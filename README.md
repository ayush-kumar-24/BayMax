# Baymax Agent App

Personal Android app that turns Baymax (Big Hero 6) into a voice-and-chat healthcare companion, built on free tools. Personal use only, not published, **not a medical device**. Requirements: the SRS (v1.0). Design: `design/UI_SPEC.md`.

## Status

| Phase | What | State |
|---|---|---|
| 1 | UI spec + interactive HTML demo (`demo/index.html`) | Done |
| 2 | Compose design system, Baymax character + animations, chest panel, Home | Done |
| 3 | Care protocol state machine, on-device safety rules, voice in/out | Done |
| 4 | Health log, memory, settings, conversation, reminders, contact a friend, notice | Done |
| 5 | Gemini (Firebase AI Logic) with Groq fallback, tool calling, Health Connect, widget | Done |

Not built (both "could" items in the SRS): background wake word (FR-31) and photos on the chest panel (FR-27).

## Setup

1. Open in Android Studio (Ladybug or newer), JDK 17+, Android SDK 35. Min SDK 26.
2. Language model. All optional; without any, Baymax runs fully offline (care loop, safety, reminders, log).
   - **Gemini via Firebase AI Logic (primary):** create a Firebase project, add an Android app with package `com.ayush.baymax`, enable *Firebase AI Logic* with the Gemini Developer API, download `google-services.json` into `app/`. The build picks it up automatically.
   - **Groq (fallback):** copy `secrets.properties.example` to `secrets.properties` and set `GROQ_API_KEY` (free key at console.groq.com).
   - **Gemini without Firebase:** set `GEMINI_API_KEY` in `secrets.properties` (AI Studio key).
   - Order tried: Firebase Gemini → Gemini key → Groq. Each gets one retry on rate limits or server errors (FR-28).
3. Run `app`, or `./gradlew :app:installDebug`.
4. In the app: Settings → add a trusted contact, connect Health Connect, set your emergency number (default 112).

## What Baymax does

- **Care loop:** say or type *ow*. He inflates out of his case, asks for a 1–10 pain rating on his chest screen, scans you (with Health Connect readings), asks where it hurts, when it started and whether it is getting worse, gives self-care advice, logs the session, then asks *"Are you satisfied with your care?"* Only **I am satisfied with my care** puts him back in his case.
- **Emergency:** chest pain, breathing trouble, heavy bleeding, self-harm or collapse phrases, in any state, detected on the phone before any LLM call. Red screen, one-tap call button (opens the dialer), contact-a-friend. Works in airplane mode.
- **Conversation:** anything else goes to the LLM in Baymax's persona (literal, no contractions, under 40 words), with the last 10 turns and up to 5 memories. He can set reminders, read your steps/sleep/heart rate, remember facts and draft messages to a trusted contact.
- **Reminders:** "Remind me to drink water every 2 hours", which also works offline. Delivered as notifications.
- **Contact a friend:** Baymax drafts; you edit and choose WhatsApp or SMS; that app opens with the text. Nothing is sent without you.
- **Health log:** every session saved on the phone; filter Today / 7 days / 30 days / All; tap to expand; delete.
- **Settings:** voice on/off, rate, pitch, distress words, emergency number, contacts, Health Connect, reminders, memories, modes (Fitness, Sleep, Study), theme, forget everything.
- **Character:** blinks every few seconds and once per reply, tilts his head at questions, glances at the text box, breathes; droops and slurs below 15% battery.
- **Widget:** "Tap if something hurts" opens straight into a care session.

## Privacy

Health log, memories, contacts, reminders and chat (kept 30 days) are stored only on the phone (Room + DataStore). Only the LLM providers are contacted, over HTTPS, and only with the persona, current state, last 10 turns and up to 5 memories. **Forget everything** in Settings erases all of it.

## Tests

```
./gradlew :app:testDebugUnitTest
```

44 JVM unit tests cover the SRS acceptance checks that do not need a device: T-1 persona, T-3 care loop, T-4 red flags from every state, T-5 LLM fallback and offline message, T-7 log filtering and forget-everything, T-10 privacy payload, plus reminders, drafts, tools and interruption handling. T-2, T-6, T-8 and T-9 need a phone.

## Code layout

```
app/src/main/java/com/ayush/baymax/
  agent/      CareProtocol state machine, SafetyRules, ReminderParser, Brain + tool actions (pure Kotlin)
  llm/        LlmClient, LlmRouter (retry + fallback), PromptBuilder, Groq/Gemini REST, Firebase Gemini
  data/       Models, BaymaxRepository (in-memory) and room/ (Room + DataStore)
  voice/      TextToSpeech and SpeechRecognizer
  platform/   WorkManager reminders, Health Connect, messaging intents, widget
  ui/         BaymaxApp (navigation), home/, baymax/ (character), chest/, log/, settings/, sheets/, theme/
```

Screens: `design/phase2-screens.png`, `design/phase3-flow.png`, `design/phase4-screens.png`, `design/phase4-settings.png`.

Nunito font: SIL Open Font License (`design/NUNITO_OFL.txt`).
