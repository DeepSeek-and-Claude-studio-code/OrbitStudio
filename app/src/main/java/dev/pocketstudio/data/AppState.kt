package dev.pocketstudio.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File

enum class Tab(val label: String, val icon: ImageVector) {
    Projects("Проекты", Icons.Filled.Home),
    Editor("Код", Icons.Filled.Edit),
    Build("Сборка", Icons.Filled.Build),
    Keys("Ключи", Icons.Filled.Lock),
    Setup("Настройка", Icons.Filled.Settings),
}

/** Данные подписи, введённые на экране «Сборка». Живут только в памяти и никуда не сохраняются. */
class SigningInput {
    var keystore by mutableStateOf<File?>(null)
    var alias by mutableStateOf("")
    var storePass by mutableStateOf("")
    var keyPass by mutableStateOf("")
}

/** Состояние приложения на уровне процесса: переживает пересоздание Activity (поворот, смена темы). */
object AppState {
    var tab by mutableStateOf(Tab.Projects)
    var project by mutableStateOf<File?>(null)
    val editor = EditorState()
    val signing = SigningInput()

    /** Увеличивается в onResume — экраны перепроверяют разрешения. */
    var tick by mutableIntStateOf(0)

    fun openProject(dir: File) {
        editor.saveAll()
        editor.closeAll()
        project = dir
        tab = Tab.Editor
    }
}
