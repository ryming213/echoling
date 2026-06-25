package com.echoling.app.presentation

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