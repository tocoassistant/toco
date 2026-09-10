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

**Background listening — sleep mode**

Settings -> Background listening. Two states:

- **Sleeping**: a tiny AudioRecord watches raw microphone amplitude only. No
  recognition, no network, almost no work. It cannot hear words, only that a
  sound loud enough to be speech happened.
- **Awake**: sound detected, so the mic goes to SpeechRecognizer for ONE
  session to check for a wake phrase. Match -> "How can I help you?" ->
  listens once for the command. No match -> straight back to sleep.

So the recognizer runs when someone speaks nearby, not every two seconds
forever. "toco volume up" in one breath skips the greeting.

Commands run as device actions; questions go to Gemini and are spoken back.

Remaining limits: amplitude cannot tell speech from a door slam, so loud noise
still wakes it (it sleeps again immediately). A word in the first moment of
waking can clip. OEM battery managers still kill foreground services — set
TOCO to **Unrestricted**. A trained wake-word model would beat this.

Locked-screen: volume, flashlight, media and spoken answers work. Opening apps
or dialling needs **Display over other apps** (listed in Access), because
Android blocks background activity starts.

Fallbacks that cannot be killed: **Quick Settings tile** and the **Talk**
button on the notification.

**Command matching**

Skills match on the verb at the start or on whole words, never on substrings,
and each carries a `priority` so the strongest claim wins. This replaced
substring matching, where "open play store" was captured by the media skill
because the text contained "play". `CommandText` also normalises everyday
phrasing — "torch", "louder", "go to", "please", "fire up" — so plain speech
works without memorising syntax.

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
