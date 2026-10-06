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
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.ayush.baymax.platform.Messaging
import com.ayush.baymax.ui.BaymaxApp
import com.ayush.baymax.ui.PlatformHooks
import com.ayush.baymax.ui.home.HomeViewModel
import com.ayush.baymax.ui.theme.Nunito

class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()

    /** Every permission is asked only when first needed (NFR-10); denying never breaks the app (NFR-5). */
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else toast("I need microphone permission to hear you. You can type instead.")
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast("Reminders are saved, but notifications are off. You can turn them on in system settings.")
    }

    private val healthPermissions = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        vm.refreshHealthStatus()
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
        vm.messenger = { contact, text, app ->
            if (!Messaging.open(this, contact, text, app)) toast("I could not open ${app.label}. Please message ${contact.name} directly.")
        }
        vm.requestNotificationPermission = ::askNotificationPermission

        setContent {
            BaymaxApp(
                controller = vm.controller,
                platform = PlatformHooks(
                    onMic = ::onMicTapped,
                    healthStatus = vm.healthStatus,
                    connectHealth = ::connectHealth,
                    brainStatus = vm.brainStatus,
                    reminders = vm.reminders,
                    backHandler = { enabled, onBack -> BackHandler(enabled, onBack) },
                ),
                fontFamily = Nunito,
            )
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_START_CARE) {
            vm.controller.startCareFromShortcut()
            intent.action = null
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(networkCallback)
        updateOnline()
        vm.refreshHealthStatus()
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
            vm.speechInput.isListening -> listen()
            !vm.speechInput.isAvailable -> toast("Speech input is not available on this phone. You can type instead.")
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> listen()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun listen() = vm.startListening(onError = ::toast)

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runOnUiThread { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    private fun connectHealth() {
        when (vm.healthReader.sdkStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> healthPermissions.launch(vm.healthReader.permissions)
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
            }.onFailure { toast("Please update Health Connect from the Play Store.") }
            else -> toast("Health Connect is not available on this phone.")
        }
    }

    /** ACTION_DIAL needs no permission and works offline (FR-15, FR-16). */
    private fun dial(number: String) {
        runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
            .onFailure { toast("Please dial $number now.") }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        const val ACTION_START_CARE = "com.ayush.baymax.START_CARE"
    }
}
