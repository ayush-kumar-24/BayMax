# Baymax Agent App

Personal Android app that turns Baymax (Big Hero 6) into a voice-and-chat healthcare companion. Personal use only, not a medical device. See the SRS and `design/UI_SPEC.md`.

## Status

| Phase | What | State |
|---|---|---|
| 1 | UI spec + interactive HTML demo (`demo/index.html`) | Done |
| 2 | Compose design system, Baymax character + animations, chest panel, Home screen | Done |
| 3 | Care-protocol state machine wired to the UI | Next |
| 4 | Health log, settings, sheets, reminders, contact friend, low battery | |
| 5 | Gemini/Groq, speech, Health Connect, Room | |

## Build

Open the project in Android Studio (Ladybug or newer) and run `app`, or:

```
./gradlew :app:installDebug
```

Requires JDK 17+ and Android SDK 35. Min SDK 26.

## Phase 2 sandbox

Until Phase 3 lands, `ui/home/SandboxController.kt` scripts Baymax so the UI can be tried on a phone:

- type **ow** → he inflates out of the case, greets you, shows the pain scale
- tap a number → scan → advice → "Are you satisfied with your care?"
- **I am satisfied with my care** → he deflates back into the case
- **my chest hurts** → emergency mode with the call button (opens the dialer)
- tap the case to wake him, tap his head to make him blink and tilt

## Code layout

```
app/src/main/java/com/ayush/baymax/
  agent/AgentState.kt          care-protocol states
  ui/theme/                    colors, type (Nunito), BaymaxTheme
  ui/baymax/                   Vinyl shading, BaymaxFace, BaymaxStage (body, case, animations)
  ui/chest/                    ChestPanel, PainScale, ScanView, EmergencyPanel
  ui/home/                     HomeScreen, top bar, captions, chips, input, sandbox
  ui/Previews.kt               Android Studio previews of each state
```

`design/phase2-screens.png` shows the Compose UI rendered in each state.

Nunito font: SIL Open Font License (`design/NUNITO_OFL.txt`).
