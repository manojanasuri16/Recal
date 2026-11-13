package app.recal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.recal.ui.MainViewModel
import app.recal.ui.RecalApp
import app.recal.ui.RecalTheme
import app.recal.ui.isDarkFor

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Permissions are granted in system screens; re-read them every time we come back.
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshPerms() }
            val dark = isDarkFor(vm.themeChoice)
            // Keep status/navigation icons legible when the app theme differs from the system's.
            SideEffect {
                val bar = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            }
            RecalTheme(dark = dark) { RecalApp(vm) }
        }
    }
}
