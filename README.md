# Baymax Agent App

Personal Android app that turns Baymax (Big Hero 6) into a voice-and-chat healthcare companion. Personal use only, not a medical device. See the SRS and `design/UI_SPEC.md`.

## Status

| Phase | What | State |
|---|---|---|
| 1 | UI spec + interactive HTML demo (`demo/index.html`) | Done |
| 2 | Compose design system, Baymax character + animations, chest panel, Home screen | Done |
| 3 | Care-protocol state machine, safety rules, voice in/out | Done |
| 4 | Health log, settings, sheets, reminders, contact friend | Next |
| 5 | Gemini/Groq, speech, Health Connect, Room | |

## Build

Open the project in Android Studio (Ladybug or newer) and run `app`, or:

```
./gradlew :app:installDebug
```

Requires JDK 17+ and Android SDK 35. Min SDK 26.

## Try it

- Say or type **ow**: Baymax inflates out of his case, greets you and shows the pain scale on his chest
- Tap a number, or say it: he scans you, asks where it hurts, when it started and whether it is getting worse, then gives self-care advice
- He asks **"Are you satisfied with your care?"** Anything except **I am satisfied with my care** keeps him caring for you
- Say **my chest hurts**, **I can't breathe**, **heavy bleeding** or a self-harm phrase at any time: Emergency mode with a one-tap call button. Works in airplane mode
- **I feel low**: a gentle mood check
- Tap the mic to talk (on-device speech recognition); replies are spoken aloud (mute in the top bar)
- Below 15% battery and not charging, his eyes droop and his speech slurs

## Tests

```
./gradlew :app:testDebugUnitTest
```

Covers the SRS acceptance checks that do not need a device: T-1 persona lines (no contractions, under 40 words), T-3 care loop (only the exit phrase returns to Idle), T-4 red flags from every state, plus the UI controller's interruption handling.

## Code layout

```
app/src/main/java/com/ayush/baymax/
  agent/                       care protocol state machine, safety rules (pure Kotlin, unit tested)
  voice/                       text-to-speech and speech recognition
  ui/theme/                    colors, type (Nunito), BaymaxTheme
  ui/baymax/                   Vinyl shading, BaymaxFace, BaymaxStage (body, case, animations)
  ui/chest/                    ChestPanel, PainScale, ScanView, EmergencyPanel
  ui/home/                     HomeScreen, HomeController (runs the protocol), HomeViewModel
  ui/Previews.kt               Android Studio previews of each state
```

`design/phase2-screens.png` and `design/phase3-flow.png` show the Compose UI rendered in each state.

Nunito font: SIL Open Font License (`design/NUNITO_OFL.txt`).
