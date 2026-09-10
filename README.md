# TOCO

Android AI assistant. Drop-in project — see `BUILD.md` to build.

## Status

**Working now**
- Splash -> shell with 5-tab bottom nav; the raised ring animates to whichever
  tab is selected
- Home: orb (idle/listening/working), greeting, typed + voice command bar
- Voice input via `SpeechRecognizer` (in-app, never leaves TOCO)
- Voice output via `TextToSpeech`
- Models page: lists every registered action, read live from the registry
- **TOCO Access**: real permission status per capability, with Allowed /
  Limited / Not allowed / Not connected / Not supported, and the correct
  system settings deep link for the ones Android will not let an app grant
- **Commands**: call/whatsapp (by number OR contact name), open app, volume,
  flashlight, media play/pause/next/prev, battery, storage, device info, and
  every Settings screen by name
- **Gemini fallback**: anything no module claims becomes a conversation with
  the model instead of a dead end
- **Chained commands**: "open youtube and search for lofi then turn volume up"
  splits into steps and runs them in order, showing progress. Message bodies
  are protected, so "call mom and tell her I'm late" stays one command
- **In-app search** by deep link: YouTube, Google, Maps, Play Store, Amazon,
  Spotify. Opens the app already showing results
- **Voice picker** in Models: four options, each a pitch/rate pairing over
  whatever voices your phone's speech engine actually has

**Background listening (built, read this)**

Settings -> Background listening turns on a foreground service that loops
`SpeechRecognizer` to catch a wake phrase. This is not a trained wake-word
model, and the difference is real:

- Noticeable battery drain; the mic never rests
- Wake words can be missed during the gap between recognizer restarts
- TOCO holds the mic, so other apps (Google Assistant included) may not record
- Some recognizers need network, so detection can stop when offline
- Infinix and similar will kill the service unless TOCO is set to
  **Unrestricted** in battery settings

While locked it can change volume, toggle the flashlight, control media and
answer out loud. Opening apps or dialling needs **Display over other apps**
(now listed in Access), because Android blocks background apps from starting
screens.

The reliable alternatives ship alongside it: a **Quick Settings tile** and a
**Talk** button on the notification, both one tap, neither costing battery.

Phrases are editable in Settings. Default: hey toco, toco, hi toco, assistant,
ok toco. Shorter phrases false-trigger more.

**Not built yet**
AccessibilityService (back/home/scroll/tap inside other apps), SFX/haptics,
action log, boot animation, structured AI command schema.

## Layout

```
com.toco.ai
├── TocoApp.kt              startup: crash handler, registry, TTS
├── access/                 capability model + live permission state
│   ├── Capability.kt        (Gate: RUNTIME | SYSTEM_SETTINGS | ALWAYS)
│   ├── CapabilityStatus.kt
│   └── PermissionManager.kt
├── core/                   CrashHandler · Prefs · Voice (TTS) · VoiceInput (STT)
├── skill/                  Skill · SkillResult · SkillRegistry + builtin/
├── engine/                 CommandEngine · ModuleProvider (AI seam)
├── ui/                     MainActivity + access/ home/ models/ common/
│                           nav/ splash/ widget/ debug/
└── util/                   AppFinder · Permissions · PhoneNumbers
```

## Android limits, stated plainly

TOCO will not claim to do things Android forbids third-party apps:

| Asked for | Reality |
|---|---|
| Toggle Wi-Fi | Removed for apps in Android 10. Only a settings panel the user taps. |
| Toggle Bluetooth | `BluetoothAdapter.enable()` is a no-op from Android 13. System dialog only. |
| Power off phone | Impossible without root or Device Owner. Not supported. |
| Force-stop an app | No legitimate API. Not supported. |
| Always-on wake word | Built, with real caveats — see below. |
| Tapping a result inside another app ("play the first video") | Needs AccessibilityService, and is per-app fragile. Deep-link search gets you to the results; the tap does not exist yet. |
| Named assistant voices | Android exposes only the voices the device's TTS engine installed. The picker tunes pitch and rate over those. |
| Accessibility actions | Real — after the user enables the service themselves. |

Works with no compromise: flashlight (`setTorchMode`), volume (`AudioManager`),
media keys, every Settings screen by Intent, app launch, device info, vibration.

## Adding an action

```kotlin
class FlashlightSkill : Skill {
    override val id = "core.flashlight"
    override val name = "Flashlight"
    override fun canHandle(command: String) =
        command.lowercase().startsWith("flashlight")
    override fun execute(context: Context, command: String): SkillResult {
        return SkillResult.Ok("Flashlight on")
    }
}
```

One line in `SkillRegistry.bootstrap()`. It appears in Models automatically.

## API keys

Never in source. Put them in `local.properties` (git-ignored) and read via
`BuildConfig`. Note this is still extractable from a built APK — fine for your
own phone, not for a build you hand out.
