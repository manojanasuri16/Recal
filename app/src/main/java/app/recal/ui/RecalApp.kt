package app.recal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

private enum class Screen { Welcome, Setup, Home, Settings }

@Composable
fun RecalApp(vm: MainViewModel) {
    var screen by rememberSaveable { mutableStateOf(if (vm.onboarded) Screen.Home else Screen.Welcome) }
    val reduce = reduceMotion()

    BackHandler(enabled = screen == Screen.Settings) { screen = Screen.Home }
    BackHandler(enabled = screen == Screen.Setup && !vm.onboarded) { screen = Screen.Welcome }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                if (reduce) fadeIn() togetherWith fadeOut()
                else {
                    // Push in from the trailing edge, and leave the same way it came.
                    val spec = Motion.smooth<IntOffset>()
                    if (forward) (slideInHorizontally(spec) { it } + fadeIn(Motion.smooth())) togetherWith
                        (slideOutHorizontally(spec) { -it / 4 } + fadeOut(Motion.smooth()))
                    else (slideInHorizontally(spec) { -it / 4 } + fadeIn(Motion.smooth())) togetherWith
                        (slideOutHorizontally(spec) { it } + fadeOut(Motion.smooth()))
                }
            },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Welcome -> Welcome(onContinue = { screen = Screen.Setup })
                Screen.Setup -> SetupScreen(vm, onDone = { vm.finishOnboarding(); screen = Screen.Home })
                Screen.Home -> Home(vm, onSettings = { screen = Screen.Settings })
                Screen.Settings -> SettingsScreen(vm, onBack = { screen = Screen.Home })
            }
        }
    }
}
