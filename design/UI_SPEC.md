# Baymax Agent App — UI & Feature Spec (Phase 1)

Goal: the app should **feel like being in the movie**. Baymax is not a chat app with an avatar. Baymax *is* the screen: a big, soft, white vinyl robot who wakes up from his red case when you say "ow", speaks slowly, blinks, tilts his head, and will not leave until you say *"I am satisfied with my care."*

## 1. Design principles

| Principle | What it means on screen |
|---|---|
| Baymax fills the frame | The top ~60% of Home is a close-up of Baymax (head + torso). No cards, no clutter. |
| Calm and slow | Easing is soft and bouncy like an inflating balloon. Text appears word by word at Baymax's pace. Nothing flashes, except Emergency. |
| Literal and minimal | Captions, not chat bubbles. Short sentences. Large type. |
| Chest is the screen | Pain scale, scan, readings, emergency call, photos all appear on a glowing panel on Baymax's chest (as in the film). |
| Safety is local | Red-flag detection and the emergency panel never wait for the network. |

## 2. Visual language

- **Palette**
  - Vinyl white `#FFFFFF` → shade `#E6E8EC` → edge `#D4D8DE` (radial gradients give volume)
  - Face ink `#111317`
  - Baymax red (case, emergency, listening) `#D7262E` / deep `#A8161D`
  - Chest-screen glass `#0E1822` with cyan UI `#5FD8FF`
  - Light background: warm paper `#F3F1ED`. Dark background: `#0D1015` with a soft glow behind Baymax.
- **Type**: Nunito (rounded, friendly). Caption 20–22sp, body 16sp. Respects system font size.
- **Shape**: everything rounded (24dp cards, full pills), tap targets ≥ 48dp.
- **Sound**: soft inflation whoosh on wake, two-tone "boop" on activation, slow low TTS voice.

## 3. Character states → visuals

| Agent state | Baymax visual | Status pill |
|---|---|---|
| Idle | Red charging case at the bottom, dim; hint "Say *ow* or tap" | grey · Idle |
| Waking | Case drops, body inflates with overshoot, head pops up, eyes open, blink | green · Waking |
| Greeting / Chat | Breathing loop, random blinks (3–6 s), eyes glance to the input when typing | green · Listening |
| Pain scale | Head tilt, chest panel shows 10 faces (green → red) | green · Pain scale |
| Scan | Chest panel: body outline with sweeping scan line, then readings (Health Connect) | green · Scanning |
| Interview / Care | Head tilt on each question; quick-reply chips | green · Care |
| Satisfaction check | Head tilt, chips: *I am satisfied with my care* / *Not yet* | green · Care |
| Emergency | Screen tints red, top banner, chest shows huge **Call 112** + Contact friend, works offline | red · Emergency |
| Low battery (<15%) | Droopy squinting eyes, slow sway, slurred captions ("hairy babyyy") | amber · Low battery |
| Offline LLM | Normal look; says "I cannot think clearly right now" and offers offline tools | amber · Offline |
| Deactivate | Only on exit phrase: eyes close, body deflates back into the case | grey · Idle |

## 4. Screens

1. **Home** (single main screen, everything within 2 taps)
   - Top bar: status pill · mute · health log · settings
   - Stage: Baymax (or the case when idle)
   - Caption block: Baymax's current line (word by word) + your last line; pull handle opens full conversation
   - Quick-reply chips (contextual)
   - Input bar: text field + large round mic button (red pulse while listening)
2. **Pain scale** — on the chest panel, 10 face buttons 1–10.
3. **Emergency** — banner + chest panel call button + contact friend; overrides everything.
4. **Conversation sheet** — full scrollable chat history (FR-5).
5. **Health log** — filter (Today / 7 days / 30 days / All), entries with pain badge, mood, symptoms, delete; tap to expand.
6. **Settings** — voice on/off, speech rate, pitch, distress words, emergency number, trusted contacts, mode chips (Care always on + Fitness/Sleep/Study), wake word, theme, forget everything, not-medical-advice notice.
7. **First-launch notice** — "I am not a doctor" disclaimer with Baymax.
8. **Contact-friend confirm sheet** — drafted message, send via SMS / WhatsApp, user confirms.
9. **Reminder confirmation** — in-persona confirmation + notification preview.

## 5. Feature → FR map

| Feature | FRs |
|---|---|
| Text + voice input, spoken replies, mute | FR-1, 2, 3 |
| Persona captions, chat history | FR-4, 5 |
| Distress wake, greeting, pain scale, interview, advice | FR-6–10 |
| Satisfaction loop, exit phrase only | FR-11–13 |
| Local red flags, emergency panel, offline | FR-14–16 |
| Face (dots + line), blink per reply, sequenced anims, low battery | FR-17–19 |
| Health log, memory, forget everything | FR-20–23 |
| Health Connect readings, reminders, contact friend, chest photo | FR-24–27 |
| LLM fallback/offline message | FR-28–29 |
| Mode chips, wake word, widget | FR-30–32 |

## 6. Phases

1. **Phase 1 (done)** — UI spec + interactive HTML demo (`demo/index.html`). Approve the feel.
2. **Phase 2 (done)** — Compose design system: theme tokens, Baymax face/body composables + animations, Home screen, chest panel.
3. **Phase 3 (done)** — Care protocol state machine UI wiring, pain scale, scan, emergency, satisfaction loop.
4. **Phase 4** — Health log, settings, sheets, reminders, contact friend, low-battery, dark theme polish.
5. **Phase 5** — Hook up LLM, speech, Health Connect, Room (backend layers).
