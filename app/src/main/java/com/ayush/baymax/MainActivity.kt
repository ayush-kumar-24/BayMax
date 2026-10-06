package com.ayush.baymax

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.ayush.baymax.ui.home.HomeActions
import com.ayush.baymax.ui.home.HomeScreen
import com.ayush.baymax.ui.home.SandboxController
import com.ayush.baymax.ui.theme.BaymaxTheme
import com.ayush.baymax.ui.theme.Nunito

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BaymaxTheme(fontFamily = Nunito) {
                val scope = rememberCoroutineScope()
                val sandbox = remember { SandboxController(scope) }
                val dial = { number: String ->
                    // ACTION_DIAL needs no permission and works offline (FR-15, FR-16).
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                }
                sandbox.onCall = { dial(SandboxController.EMERGENCY_NUMBER) }
                val actions = remember(sandbox) {
                    HomeActions(
                        onSend = sandbox::send,
                        onChip = sandbox::chip,
                        onMic = sandbox::toggleMic,
                        onCaseTap = sandbox::caseTap,
                        onHeadTap = sandbox::headTap,
                        onPainSelected = sandbox::selectPain,
                        onCall = { dial(SandboxController.EMERGENCY_NUMBER) },
                        onImOkay = sandbox::imOkay,
                        onToggleMute = sandbox::toggleMute,
                    )
                }
                HomeScreen(sandbox.state, actions)
            }
        }
    }
}
