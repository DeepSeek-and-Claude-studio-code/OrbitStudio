package dev.pocketstudio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import dev.pocketstudio.data.AppState
import dev.pocketstudio.data.Tab

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PocketApp() {
    val imeVisible = WindowInsets.isImeVisible
    BackHandler(enabled = AppState.tab != Tab.Projects) { AppState.tab = Tab.Projects }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!imeVisible) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    for (t in Tab.values()) {
                        NavigationBarItem(
                            selected = AppState.tab == t,
                            onClick = { AppState.tab = t },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label, fontSize = 11.sp, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (AppState.tab) {
                Tab.Projects -> ProjectsScreen()
                Tab.Editor -> EditorScreen()
                Tab.Build -> BuildScreen()
                Tab.Keys -> KeysScreen()
                Tab.Setup -> SetupScreen()
            }
        }
    }
}
