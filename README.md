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

**Not built yet**
AccessibilityService (back/home/scroll/tap inside other apps), wake word,
SFX/haptics, action log, boot animation, structured AI command schema.

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
| Always-on wake word | Possible, but needs a foreground service, a permanent notification, and real battery cost. Not faked. |
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
