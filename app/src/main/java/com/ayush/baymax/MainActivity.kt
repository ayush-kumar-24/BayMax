package com.ayush.baymax

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.ayush.baymax.ui.home.HomeActions
import com.ayush.baymax.ui.home.HomeScreen
import com.ayush.baymax.ui.home.HomeViewModel
import com.ayush.baymax.ui.theme.BaymaxTheme
import com.ayush.baymax.ui.theme.Nunito

class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()

    /** Microphone permission is requested only when the mic is first tapped (NFR-10). */
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else toast("I need microphone permission to hear you. You can type instead.")
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            if (level >= 0 && scale > 0) {
                // FR-19: below 15% and not charging.
                vm.controller.setLowBattery(level * 100 / scale < 15 && !charging)
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateOnline()
        override fun onLost(network: Network) = updateOnline()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = updateOnline()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.dialer = ::dial

        val c = vm.controller
        val actions = HomeActions(
            onSend = c::send,
            onChip = c::chip,
            onMic = ::onMicTapped,
            onCaseTap = c::caseTap,
            onHeadTap = c::headTap,
            onPainSelected = c::selectPain,
            onCall = c::call,
            onMessageFriend = c::messageFriend,
            onImOkay = c::imOkay,
            onToggleMute = c::toggleMute,
            onOpenLog = { toast("The health log arrives in the next update.") },
            onOpenSettings = { toast("Settings arrive in the next update.") },
        )
        setContent {
            BaymaxTheme(fontFamily = Nunito) {
                HomeScreen(vm.controller.state, actions)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(networkCallback)
        updateOnline()
    }

    override fun onStop() {
        unregisterReceiver(batteryReceiver)
        runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback) }
        if (vm.speechInput.isListening) {
            vm.speechInput.stop()
            vm.controller.setListening(false)
        }
        super.onStop()
    }

    private fun updateOnline() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        runOnUiThread { vm.controller.setOnline(online) }
    }

    private fun onMicTapped() {
        when {
            !vm.speechInput.isAvailable -> toast("Speech input is not available on this phone. You can type instead.")
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> listen()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun listen() = vm.startListening(onError = ::toast)

    /** ACTION_DIAL needs no permission and works offline (FR-15, FR-16). */
    private fun dial(number: String) {
        runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
            .onFailure { toast("Please dial $number now.") }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
