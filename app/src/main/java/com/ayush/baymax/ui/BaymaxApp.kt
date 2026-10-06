package com.ayush.baymax.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.ayush.baymax.data.ReminderScheduler
import com.ayush.baymax.data.ThemeMode
import com.ayush.baymax.ui.home.HomeActions
import com.ayush.baymax.ui.home.HomeController
import com.ayush.baymax.ui.home.HomeScreen
import com.ayush.baymax.ui.log.HealthLogScreen
import com.ayush.baymax.ui.settings.HealthConnectStatus
import com.ayush.baymax.ui.settings.SettingsActions
import com.ayush.baymax.ui.settings.SettingsScreen
import com.ayush.baymax.ui.sheets.ConversationSheet
import com.ayush.baymax.ui.sheets.DraftSheet
import com.ayush.baymax.ui.sheets.NoticeSheet
import com.ayush.baymax.ui.theme.BaymaxTheme
import kotlinx.coroutines.launch

enum class Screen { Home, HealthLog, Settings }

/** Things only the platform can do. Android fills these in MainActivity. */
@Immutable
data class PlatformHooks(
    val onMic: () -> Unit = {},
    val healthStatus: HealthConnectStatus = HealthConnectStatus.Unavailable,
    val connectHealth: () -> Unit = {},
    val brainStatus: String = "No language model configured. Baymax works offline.",
    val reminders: ReminderScheduler = ReminderScheduler.None,
    /** System back. Android passes BackHandler; desktop previews ignore it. */
    val backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
)

/**
 * The whole app: Home with Baymax, the health log, settings, and the sheets on top
 * (conversation, message draft, first-launch notice). Every core action is within two
 * taps of Home (NFR-11).
 */
@Composable
fun BaymaxApp(
    controller: HomeController,
    platform: PlatformHooks,
    fontFamily: FontFamily = FontFamily.Default,
    initialScreen: Screen = Screen.Home,
) {
    val repo = controller.repository
    val settings by repo.settings.collectAsState()
    val loaded by repo.settingsLoaded.collectAsState()
    val entries by repo.healthEntries.collectAsState(initial = emptyList())
    val chat by repo.chat.collectAsState(initial = emptyList())
    val contacts by repo.contacts.collectAsState(initial = emptyList())
    val reminders by repo.reminders.collectAsState(initial = emptyList())
    val memories by repo.memories.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var screen by rememberSaveable { mutableStateOf(initialScreen) }
    var conversationOpen by rememberSaveable { mutableStateOf(false) }
    var noticeForced by remember { mutableStateOf(false) }
    val state = controller.state

    val dark = when (settings.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val homeActions = remember(controller, platform.onMic) {
        HomeActions(
            onSend = controller::send,
            onChip = controller::chip,
            onMic = platform.onMic,
            onCaseTap = controller::caseTap,
            onHeadTap = controller::headTap,
            onPainSelected = controller::selectPain,
            onCall = controller::call,
            onMessageFriend = controller::messageFriend,
            onImOkay = controller::imOkay,
            onToggleMute = controller::toggleMute,
            onOpenLog = { screen = Screen.HealthLog },
            onOpenSettings = { screen = Screen.Settings },
            onOpenConversation = { conversationOpen = true },
        )
    }

    val settingsActions = remember(repo, platform) {
        SettingsActions(
            update = { change -> scope.launch { repo.updateSettings(change) } },
            addContact = { c -> scope.launch { repo.addContact(c) } },
            deleteContact = { id -> scope.launch { repo.deleteContact(id) } },
            deleteReminder = { id -> platform.reminders.cancel(id); scope.launch { repo.deleteReminder(id) } },
            deleteMemory = { id -> scope.launch { repo.deleteMemory(id) } },
            connectHealth = platform.connectHealth,
            forgetEverything = {
                platform.reminders.cancelAll()
                scope.launch { repo.forgetEverything() }
                screen = Screen.Home
            },
            showNotice = { noticeForced = true },
        )
    }

    val showNotice = noticeForced || (loaded && !settings.disclaimerAccepted)

    platform.backHandler(conversationOpen || state.draft != null || screen != Screen.Home) {
        when {
            state.draft != null -> controller.cancelDraft()
            conversationOpen -> conversationOpen = false
            else -> screen = Screen.Home
        }
    }

    BaymaxTheme(darkTheme = dark, fontFamily = fontFamily) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    if (targetState != Screen.Home) {
                        (slideInHorizontally(tween(320)) { it } + fadeIn()) togetherWith fadeOut(tween(200))
                    } else {
                        fadeIn(tween(250)) togetherWith (slideOutHorizontally(tween(320)) { it } + fadeOut())
                    }
                },
                label = "screen",
            ) { s ->
                when (s) {
                    Screen.Home -> HomeScreen(state, homeActions)
                    Screen.HealthLog -> HealthLogScreen(
                        entries = entries,
                        onDelete = { id -> scope.launch { repo.deleteHealthEntry(id) } },
                        onBack = { screen = Screen.Home },
                    )
                    Screen.Settings -> SettingsScreen(
                        settings = settings,
                        contacts = contacts,
                        reminders = reminders,
                        memories = memories,
                        healthStatus = platform.healthStatus,
                        brainStatus = platform.brainStatus,
                        actions = settingsActions,
                        onBack = { screen = Screen.Home },
                    )
                }
            }
            ConversationSheet(conversationOpen, chat) { conversationOpen = false }
            DraftSheet(state.draft, onSend = controller::sendDraft, onCancel = controller::cancelDraft)
            NoticeSheet(showNotice) {
                noticeForced = false
                scope.launch { repo.updateSettings { it.copy(disclaimerAccepted = true) } }
            }
        }
    }
}
