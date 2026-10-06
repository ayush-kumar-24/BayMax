package com.ayush.baymax.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ayush.baymax.agent.AgentState
import com.ayush.baymax.ui.home.ChestContent
import com.ayush.baymax.ui.home.HealthReadings
import com.ayush.baymax.ui.home.HomeActions
import com.ayush.baymax.ui.home.HomeScreen
import com.ayush.baymax.ui.home.HomeUiState
import com.ayush.baymax.ui.home.QuickChip
import com.ayush.baymax.ui.theme.BaymaxTheme
import com.ayush.baymax.ui.theme.Nunito

// Android Studio previews of the key Home states (static: no inflate animation).

@Composable
private fun P(state: HomeUiState, dark: Boolean = false) =
    BaymaxTheme(darkTheme = dark, fontFamily = Nunito) { HomeScreen(state, HomeActions()) }

@Preview(name = "Idle", widthDp = 390, heightDp = 844)
@Composable
private fun IdlePreview() = P(HomeUiState())

@Preview(name = "Pain scale", widthDp = 390, heightDp = 844)
@Composable
private fun PainPreview() = P(
    HomeUiState(
        agentState = AgentState.PainScale,
        caption = "On a scale of one to ten, how would you rate your pain?",
        userLine = "ow",
        chest = ChestContent.PainScale(4),
        tilt = true,
    ),
)

@Preview(name = "Scan complete", widthDp = 390, heightDp = 844)
@Composable
private fun ScanPreview() = P(
    HomeUiState(
        agentState = AgentState.Scanning,
        caption = "Scan complete.",
        userLine = "4",
        chest = ChestContent.Scan(1f, HealthReadings(78, 4210, 400)),
    ),
)

@Preview(name = "Satisfaction", widthDp = 390, heightDp = 844)
@Composable
private fun SatisfactionPreview() = P(
    HomeUiState(
        agentState = AgentState.Satisfaction,
        caption = "Are you satisfied with your care?",
        tilt = true,
        chips = listOf(QuickChip("I am satisfied with my care", QuickChip.Style.Primary), QuickChip("Not yet")),
    ),
)

@Preview(name = "Emergency", widthDp = 390, heightDp = 844)
@Composable
private fun EmergencyPreview() = P(
    HomeUiState(
        agentState = AgentState.Emergency,
        caption = "I have detected signs of a possible emergency. Please call 112 now.",
        userLine = "my chest hurts",
        chest = ChestContent.Emergency("112", "Priya"),
        chips = listOf(QuickChip("Call 112", QuickChip.Style.Danger), QuickChip("I am okay now")),
    ),
)

@Preview(name = "Dark · low battery", widthDp = 390, heightDp = 844)
@Composable
private fun DarkLowBatteryPreview() = P(
    HomeUiState(
        agentState = AgentState.Listening,
        lowBattery = true,
        caption = "my baaattery is low… i am… perfectly fine…",
    ),
    dark = true,
)
