package com.echoling.app.presentation

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.echoling.app.R
import com.echoling.app.presentation.ui.navigation.MainScaffold
import com.echoling.app.presentation.ui.theme.EchoLingTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The launcher activity is declared with Theme.EchoLing.Splash in
        // AndroidManifest so the system paints the splash image as the
        // window background the moment the activity is created — this
        // eliminates the pre-Compose white flash. Once onCreate starts
        // we swap to the regular Compose theme so window decorations,
        // status bar styling, etc. match the rest of the app.
        setTheme(R.style.Theme_EchoLing)
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        // §12.32b: explicitly disable the navigation-bar contrast scrim
        // (Android 10+). `enableEdgeToEdge()` already makes the nav bar
        // transparent, but on Android 10+ the system can still enforce a
        // translucent scrim on top of the app's content in the nav-bar
        // zone to guarantee the gesture handle is readable. On a
        // gesture-nav Xiaomi Mi 11 CN that scrim paints a near-white
        // wash (253, 253, 255) over the bottom 16dp of our bar's
        // surface, creating the "很浅的颜色" (very light color) the
        // user reported at the bottom of the bar. Setting
        // `isNavigationBarContrastEnforced = false` removes the scrim
        // so the bar's surface color shows through cleanly behind the
        // gesture handle.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // Belt-and-suspenders: also force the nav bar color to fully
        // transparent. `enableEdgeToEdge()` already does this on
        // API 29+, but some OEM skins (MIUI) honor
        // `window.navigationBarColor` more strictly than the contrast-
        // enforced flag.
        window.navigationBarColor = Color.TRANSPARENT
        setContent {
            EchoLingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScaffold()
                }
            }
        }
    }
}
