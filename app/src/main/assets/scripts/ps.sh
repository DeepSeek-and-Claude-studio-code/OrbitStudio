#!/data/data/com.termux/files/usr/bin/bash
# PocketStudio — вспомогательный скрипт. Выполняется внутри Termux.
#
#   ps.sh ping
#   ps.sh setup <принять_лицензии 0|1>
#   ps.sh build <проект> <режим> <keystore|__none__> <alias> <пароль_хранилища> <пароль_ключа|__none__> <пропустить_lint 0|1>
#   ps.sh debug-key
#   ps.sh stop
#
# Режимы сборки: debug | release-apk | release-aab | signing-report | clean
# Ход работы пишется в $ROOT/logs/run.log, состояние — в $ROOT/logs/run.status
# (STARTING -> RUNNING -> DONE:<код выхода>). Приложение читает эти два файла.

NONE="__none__"
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
HOME="${HOME:-/data/data/com.termux/files/home}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd)"
ROOT="${PS_ROOT:-$(dirname "$SCRIPT_DIR")}"
LOGS="$ROOT/logs"
LOG="$LOGS/run.log"
STATUS="$LOGS/run.status"
WORKBASE="$HOME/pocketstudio"
GRADLE_VERSION="8.7"
GRADLE_HOME="$WORKBASE/gradle-$GRADLE_VERSION"
SDK="$HOME/android-sdk"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"
PLATFORM="android-34"
BUILD_TOOLS="34.0.0"

cmd="${1:-}"
[ $# -gt 0 ] && shift

mkdir -p "$LOGS" 2>/dev/null

if [ "$cmd" = "stop" ]; then
  pkill -f 'org\.gradle' 2>/dev/null
  pkill -f 'aapt2' 2>/dev/null
  echo "Остановлено пользователем" >> "$LOG" 2>/dev/null
  echo "DONE:130" > "$STATUS"
  exit 0
fi

: > "$LOG" || exit 1
echo "RUNNING" > "$STATUS"
exec >>"$LOG" 2>&1

finish() {
  local rc=$?
  if [ "$rc" -ne 0 ] && [ "$cmd" = "build" ]; then hint_for_failure; fi
  command -v termux-wake-unlock >/dev/null 2>&1 && termux-wake-unlock
  echo "DONE:$rc" > "$STATUS"
}
trap finish EXIT

hint_for_failure() {
  grep -q -E 'OutOfMemoryError|Java heap space|GC overhead' "$LOG" &&
    echo "Подсказка: не хватило памяти. Закройте другие приложения или уменьшите -Xmx в org.gradle.jvmargs (gradle.properties проекта)."
  grep -q -E 'Could not resolve|Could not GET|UnknownHostException|Connection (reset|refused)|timed out' "$LOG" &&
    echo "Подсказка: не удалось скачать зависимости. Проверьте интернет и повторите — Gradle продолжит с того же места."
  grep -q -i -E 'jlink|JdkImageTransform' "$LOG" &&
    echo "Подсказка: проблема с jlink в JDK из Termux. Попробуйте в app/build.gradle поставить compileSdk 33 или переустановить JDK: pkg reinstall openjdk-17."
  grep -q -i -E 'aapt2.*(not found|cannot execute|Exec format|Permission denied)|AAPT2.*daemon' "$LOG" &&
    echo "Подсказка: проблема с aapt2. Проверьте, что он установлен: pkg install aapt2"
  return 0
}

resolve_java() {
  if [ -d "$PREFIX/lib/jvm/java-17-openjdk" ]; then
    export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
  elif command -v java >/dev/null 2>&1; then
    local real
    real="$(readlink -f "$(command -v java)" 2>/dev/null)"
    if [ -n "$real" ]; then
      JAVA_HOME="$(dirname "$(dirname "$real")")"
      export JAVA_HOME
    fi
  fi
  [ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"
  return 0
}

install_pkgs() {
  pkg install -y "$@" || { apt update -y && pkg install -y "$@"; }
}

# ───────────────────────── ping ─────────────────────────
do_ping() {
  echo "pong: Termux отвечает"
  echo "Архитектура: $(uname -m)"
  echo "HOME: $HOME"
  echo "Рабочая папка: $ROOT"
  if touch "$LOGS/.write-test" 2>/dev/null; then
    rm -f "$LOGS/.write-test"
    echo "Доступ к хранилищу: OK"
  else
    echo "НЕТ доступа к хранилищу. В Termux выполните: termux-setup-storage"
    return 1
  fi
  local t
  for t in java keytool aapt2 rsync curl unzip; do
    if command -v "$t" >/dev/null 2>&1; then echo "  [x] $t"; else echo "  [ ] $t — не установлен"; fi
  done
  if [ -f "$LOGS/toolchain.ok" ]; then
    echo "Инструменты сборки: установлены ($(cat "$LOGS/toolchain.ok"))"
  else
    echo "Инструменты сборки: не установлены (нажмите «Установить»)"
  fi
  return 0
}

# ───────────────────────── setup ─────────────────────────
do_setup() {
  local accept="${1:-0}"
  echo "== PocketStudio: установка инструментов сборки =="
  command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock

  echo "[1/6] Пакеты Termux: JDK 17, aapt2, rsync и утилиты"
  install_pkgs openjdk-17 aapt2 rsync curl unzip zip tar procps resolv-conf ||
    { echo "Не удалось установить пакеты. В Termux выполните: pkg update"; return 11; }
  pkg install -y ca-certificates-java >/dev/null 2>&1 || true
  resolve_java

  echo "[2/6] Gradle $GRADLE_VERSION"
  if [ ! -f "$GRADLE_HOME/bin/gradle" ]; then
    mkdir -p "$WORKBASE" && cd "$WORKBASE" || return 12
    local url="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    curl -fL --retry 3 -o gradle.zip "$url" || { echo "Не удалось скачать Gradle"; return 12; }
    local expected actual
    expected="$(curl -fsSL "$url.sha256" 2>/dev/null | tr -d ' \r\n')"
    actual="$(sha256sum gradle.zip | cut -d' ' -f1)"
    if [ -n "$expected" ] && [ "$expected" != "$actual" ]; then
      echo "Контрольная сумма Gradle не совпала — файл удалён"; rm -f gradle.zip; return 13
    fi
    unzip -q -o gradle.zip && rm -f gradle.zip || { echo "Не удалось распаковать Gradle"; return 12; }
  else
    echo "уже установлен"
  fi

  echo "[3/6] Android command-line tools"
  if [ ! -f "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    mkdir -p "$SDK/cmdline-tools" && cd "$SDK/cmdline-tools" || return 14
    curl -fL --retry 3 -o cmdline.zip "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP" ||
      { echo "Не удалось скачать command-line tools"; return 14; }
    unzip -q -o cmdline.zip && rm -f cmdline.zip && rm -rf latest && mv cmdline-tools latest ||
      { echo "Не удалось распаковать command-line tools"; return 14; }
  else
    echo "уже установлены"
  fi
  mkdir -p "$HOME/.android" && touch "$HOME/.android/repositories.cfg"

  echo "[4/6] Лицензии Android SDK"
  if [ "$accept" = "1" ]; then
    yes | sh "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null 2>&1
    echo "Лицензии приняты (вы согласились с ними в приложении)"
  elif [ -d "$SDK/licenses" ]; then
    echo "Лицензии были приняты ранее"
  else
    echo "Лицензии не приняты. Отметьте согласие в приложении и повторите установку."
    return 15
  fi

  echo "[5/6] Платформа $PLATFORM и build-tools $BUILD_TOOLS (скачивание может занять несколько минут)"
  sh "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platforms;$PLATFORM" "build-tools;$BUILD_TOOLS" ||
    { echo "sdkmanager завершился с ошибкой"; return 16; }

  echo "[6/6] Проверка"
  java -version 2>&1 | head -1
  aapt2 version 2>&1 | head -1
  echo "gradle $GRADLE_VERSION, $PLATFORM, build-tools $BUILD_TOOLS" > "$LOGS/toolchain.ok"
  echo "Готово: инструменты установлены."
  return 0
}

# ───────────────────────── build ─────────────────────────
collect_outputs() {
  local work="$1" out="$2" mode="$3" n=0 f
  local -a pat
  case "$mode" in
    debug)       pat=(-ipath '*/build/outputs/apk/*debug/*.apk') ;;
    release-apk) pat=(-ipath '*/build/outputs/apk/*release/*.apk') ;;
    release-aab) pat=(-ipath '*/build/outputs/bundle/*release/*.aab') ;;
    *) return 0 ;;
  esac
  while IFS= read -r f; do
    if cp -f "$f" "$out/"; then echo "Готово: $out/$(basename "$f")"; n=$((n + 1)); fi
  done < <(find "$work" -type f "${pat[@]}" ! -ipath '*/androidTest/*')
  [ "$n" -gt 0 ] || echo "Файлы .apk/.aab не найдены в build/outputs."
}

do_build() {
  local project="${1:-}" mode="${2:-}" ks_file="${3:-$NONE}" ks_alias="${4:-}" ks_pass="${5:-}" key_pass="${6:-$NONE}" skip_lint="${7:-1}"
  local src="$ROOT/projects/$project"
  [ -n "$project" ] && [ -d "$src" ] || { echo "Проект не найден: $src"; return 20; }
  [ -f "$LOGS/toolchain.ok" ] || { echo "Инструменты сборки не установлены. Откройте вкладку «Настройка» и нажмите «Установить»."; return 21; }

  local tasks
  case "$mode" in
    debug)          tasks="assembleDebug" ;;
    release-apk)    tasks="assembleRelease" ;;
    release-aab)    tasks="bundleRelease" ;;
    signing-report) tasks="signingReport" ;;
    clean)          tasks="clean" ;;
    *) echo "Неизвестный режим сборки: $mode"; return 23 ;;
  esac

  command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock
  resolve_java
  echo "== Сборка проекта $project: $tasks =="
  echo "Java: $(java -version 2>&1 | head -1)"

  local work="$WORKBASE/work/$project"
  mkdir -p "$work" || return 24
  echo "[1/3] Копирую исходники в рабочую папку Termux"
  rsync -rt --delete \
    --exclude='/.gradle' --exclude='/build' --exclude='/*/build' --exclude='/.git' --exclude='/local.properties' \
    "$src"/ "$work"/ || { echo "rsync завершился с ошибкой"; return 22; }

  if [ "$ks_file" != "$NONE" ]; then
    [ -f "$ks_file" ] || { echo "Keystore не найден: $ks_file"; return 25; }
    export PS_KS_FILE="$ks_file" PS_KS_ALIAS="$ks_alias" PS_KS_PASS="$ks_pass"
    if [ "$key_pass" = "$NONE" ]; then export PS_KEY_PASS="$ks_pass"; else export PS_KEY_PASS="$key_pass"; fi
    echo "Подпись: $(basename "$ks_file"), alias $ks_alias"
  else
    case "$mode" in release-*) echo "ВНИМАНИЕ: keystore не выбран — release-сборка будет неподписанной." ;; esac
  fi
  export PS_SKIP_LINT="$skip_lint"

  export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK" GRADLE_USER_HOME="$HOME/.gradle"
  mkdir -p "$GRADLE_USER_HOME" "$PREFIX/tmp"
  export TMPDIR="$PREFIX/tmp"
  export GRADLE_OPTS="-Dorg.gradle.native=false -Djava.io.tmpdir=$PREFIX/tmp -Xmx512m"

  local jvmargs="-Xmx1536m -Dfile.encoding=UTF-8" line
  if [ -f "$work/gradle.properties" ]; then
    line="$(grep -E '^org\.gradle\.jvmargs=' "$work/gradle.properties" | tail -1 | cut -d= -f2-)"
    [ -n "$line" ] && jvmargs="$line"
  fi
  jvmargs="$jvmargs -Dorg.gradle.native=false -Djava.io.tmpdir=$PREFIX/tmp"

  local -a launcher
  if [ -f "$work/gradlew" ]; then
    echo "Использую gradlew проекта"
    launcher=(sh "$work/gradlew")
  else
    launcher=(sh "$GRADLE_HOME/bin/gradle")
  fi

  echo "[2/3] Gradle: $tasks"
  cd "$work" || return 24
  "${launcher[@]}" --no-daemon --console=plain --build-cache \
    "-Dorg.gradle.jvmargs=$jvmargs" \
    "-Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2" \
    -I "$ROOT/.scripts/init.gradle" \
    $tasks
  local rc=$?
  if [ "$rc" -ne 0 ]; then echo "Gradle завершился с кодом $rc"; return "$rc"; fi

  local out="$ROOT/output/$project"
  mkdir -p "$out"
  echo "[3/3] Копирую результат в $out"
  collect_outputs "$work" "$out" "$mode"
  echo "Сборка завершена успешно."
  return 0
}

# ───────────────────────── debug-key ─────────────────────────
do_debug_key() {
  resolve_java
  command -v keytool >/dev/null 2>&1 || { echo "keytool не найден. Сначала установите инструменты (вкладка «Настройка»)."; return 30; }
  local ks="$HOME/.android/debug.keystore"
  mkdir -p "$HOME/.android" "$ROOT/keystores"
  if [ ! -f "$ks" ]; then
    echo "debug.keystore ещё нет — создаю такой же, как создаёт Android Gradle Plugin"
    keytool -genkey -v -keystore "$ks" -storepass android -alias androiddebugkey -keypass android \
      -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" || return 31
  fi
  cp -f "$ks" "$ROOT/keystores/debug.keystore" || return 32
  echo "Готово: $ROOT/keystores/debug.keystore (пароль: android, alias: androiddebugkey)"
  return 0
}

case "$cmd" in
  ping)      do_ping ;;
  setup)     do_setup "$@" ;;
  build)     do_build "$@" ;;
  debug-key) do_debug_key ;;
  *) echo "Неизвестная команда: $cmd"; exit 64 ;;
esac
