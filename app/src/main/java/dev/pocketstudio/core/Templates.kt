package dev.pocketstudio.core

class TemplateFile(val path: String, val content: String)

enum class TemplateKind(val title: String) {
    JAVA("Java — пустое приложение"),
    KOTLIN("Kotlin — пустое приложение"),
}

/** Шаблоны новых проектов. Без внешних зависимостей, чтобы первая сборка скачивала как можно меньше. */
object Templates {
    const val AGP_VERSION = "8.5.2"
    const val KOTLIN_VERSION = "2.0.21"
    const val COMPILE_SDK = 34

    private val nameRegex = Regex("^[A-Za-z][A-Za-z0-9_-]{0,39}$")
    private val packageRegex = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    private val javaKeywords = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
        "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
        "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null",
    )

    /** Возвращает текст ошибки либо null, если всё в порядке. */
    fun validate(name: String, pkg: String): String? = when {
        !nameRegex.matches(name) -> "Название: латиница, цифры, _ и - (начинается с буквы, до 40 символов)"
        !packageRegex.matches(pkg) -> "Пакет должен выглядеть как com.example.app (строчные латинские буквы, цифры, _)"
        pkg.split('.').any { it in javaKeywords } -> "Часть пакета не может быть ключевым словом Java"
        else -> null
    }

    fun files(kind: TemplateKind, name: String, pkg: String): List<TemplateFile> {
        val kotlin = kind == TemplateKind.KOTLIN
        val pkgPath = pkg.replace('.', '/')
        val list = ArrayList<TemplateFile>()

        list += TemplateFile(
            "settings.gradle",
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = '$name'
            include ':app'
            """.trimIndent() + "\n"
        )

        list += TemplateFile(
            "build.gradle",
            buildString {
                appendLine("plugins {")
                appendLine("    id 'com.android.application' version '$AGP_VERSION' apply false")
                if (kotlin) appendLine("    id 'org.jetbrains.kotlin.android' version '$KOTLIN_VERSION' apply false")
                appendLine("}")
            }
        )

        list += TemplateFile(
            "gradle.properties",
            """
            org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
            android.useAndroidX=true
            android.nonTransitiveRClass=true
            kotlin.compiler.execution.strategy=in-process
            """.trimIndent() + "\n"
        )

        list += TemplateFile(".gitignore", "*.iml\n.gradle/\nbuild/\nlocal.properties\n.idea/\n*.keystore\n*.jks\n*.p12\n")

        list += TemplateFile(
            "app/build.gradle",
            buildString {
                appendLine("plugins {")
                appendLine("    id 'com.android.application'")
                if (kotlin) appendLine("    id 'org.jetbrains.kotlin.android'")
                appendLine("}")
                appendLine()
                appendLine("android {")
                appendLine("    namespace '$pkg'")
                appendLine("    compileSdk $COMPILE_SDK")
                appendLine()
                appendLine("    defaultConfig {")
                appendLine("        applicationId '$pkg'")
                appendLine("        minSdk 24")
                appendLine("        targetSdk $COMPILE_SDK")
                appendLine("        versionCode 1")
                appendLine("        versionName '1.0'")
                appendLine("    }")
                appendLine()
                appendLine("    buildTypes {")
                appendLine("        release {")
                appendLine("            minifyEnabled false")
                appendLine("        }")
                appendLine("    }")
                appendLine()
                appendLine("    compileOptions {")
                appendLine("        sourceCompatibility JavaVersion.VERSION_17")
                appendLine("        targetCompatibility JavaVersion.VERSION_17")
                appendLine("    }")
                if (kotlin) {
                    appendLine("    kotlinOptions {")
                    appendLine("        jvmTarget = '17'")
                    appendLine("    }")
                }
                appendLine()
                appendLine("    lint {")
                appendLine("        checkReleaseBuilds false")
                appendLine("    }")
                appendLine("}")
            }
        )

        list += TemplateFile("app/proguard-rules.pro", "# Правила R8/ProGuard для release-сборки (если включите minifyEnabled).\n")

        list += TemplateFile(
            "app/src/main/AndroidManifest.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">

                <application
                    android:allowBackup="true"
                    android:label="@string/app_name"
                    android:theme="@android:style/Theme.Material.Light.DarkActionBar">

                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>

                </application>

            </manifest>
            """.trimIndent() + "\n"
        )

        list += TemplateFile(
            "app/src/main/res/values/strings.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">$name</string>
            </resources>
            """.trimIndent() + "\n"
        )

        if (kotlin) {
            list += TemplateFile(
                "app/src/main/java/$pkgPath/MainActivity.kt",
                """
                package $pkg

                import android.app.Activity
                import android.os.Bundle
                import android.view.Gravity
                import android.widget.Button
                import android.widget.LinearLayout
                import android.widget.TextView

                class MainActivity : Activity() {
                    private var taps = 0

                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)

                        val label = TextView(this).apply {
                            textSize = 24f
                            text = "Привет из PocketStudio!"
                        }
                        val button = Button(this).apply {
                            text = "Нажми меня"
                            setOnClickListener {
                                taps++
                                label.text = "Нажатий: " + taps
                            }
                        }
                        val root = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            addView(label)
                            addView(button)
                        }
                        setContentView(root)
                    }
                }
                """.trimIndent() + "\n"
            )
        } else {
            list += TemplateFile(
                "app/src/main/java/$pkgPath/MainActivity.java",
                """
                package $pkg;

                import android.app.Activity;
                import android.os.Bundle;
                import android.view.Gravity;
                import android.widget.Button;
                import android.widget.LinearLayout;
                import android.widget.TextView;

                public class MainActivity extends Activity {
                    private int taps = 0;

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);

                        LinearLayout root = new LinearLayout(this);
                        root.setOrientation(LinearLayout.VERTICAL);
                        root.setGravity(Gravity.CENTER);

                        TextView label = new TextView(this);
                        label.setTextSize(24);
                        label.setText("Привет из PocketStudio!");

                        Button button = new Button(this);
                        button.setText("Нажми меня");
                        button.setOnClickListener(v -> {
                            taps++;
                            label.setText("Нажатий: " + taps);
                        });

                        root.addView(label);
                        root.addView(button);
                        setContentView(root);
                    }
                }
                """.trimIndent() + "\n"
            )
        }
        return list
    }
}
