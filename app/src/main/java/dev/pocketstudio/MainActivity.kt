package dev.pocketstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.pocketstudio.data.AppState
import dev.pocketstudio.data.Tab
import dev.pocketstudio.data.Workspace
import dev.pocketstudio.termux.Runner
import dev.pocketstudio.ui.PocketApp
import dev.pocketstudio.ui.PocketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null && !Workspace.hasAccess()) AppState.tab = Tab.Setup
        refreshWorkspace()
        Runner.attach()
        setContent {
            PocketTheme { PocketApp() }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshWorkspace()
        AppState.tick++
    }

    override fun onPause() {
        AppState.editor.saveAll()
        super.onPause()
    }

    private fun refreshWorkspace() {
        if (!Workspace.hasAccess()) return
        try {
            Workspace.ensure(this)
        } catch (_: Exception) {
        }
    }
}
