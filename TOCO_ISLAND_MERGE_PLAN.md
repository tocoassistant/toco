# TOCO Island merge plan

## Phase 1 — Island foundation (implemented in Beta 1.14)
Changed:
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/toco/ai/TocoApp.kt
- app/src/main/java/com/toco/ai/core/Prefs.kt
- app/src/main/java/com/toco/ai/ui/settings/SettingsFragment.kt
- app/src/main/java/com/toco/ai/ui/widget/OrbView.kt
- app/src/main/res/values/strings.xml
- app/build.gradle (version only; signing untouched)

Added:
- app/src/main/java/com/toco/ai/island/IslandService.kt
- app/src/main/java/com/toco/ai/island/SoundEngine.kt
- app/src/main/res/layout/overlay_toco_island.xml
- app/src/main/res/drawable/bg_toco_island.xml
- app/src/main/res/raw/island_idle.wav
- app/src/main/res/raw/island_sleep.wav
- app/src/main/res/raw/island_yawn.wav
- app/src/main/res/raw/island_wink.wav
- app/src/main/res/raw/island_peek.wav
- app/src/main/res/raw/island_think.wav
- app/src/main/res/raw/island_dizzy.wav
- app/src/main/res/raw/island_proud.wav
- app/src/main/res/raw/island_annoyed.wav
- app/src/main/res/raw/island_love.wav
- BETA_1_14_NOTES.md

Manifest additions:
- android.permission.FOREGROUND_SERVICE_SPECIAL_USE
- .island.IslandService with foregroundServiceType=specialUse
- PROPERTY_SPECIAL_USE_FGS_SUBTYPE describing the user-enabled overlay

Gradle dependencies added: none.

## Phase 2 — Mood engine (planned)
Add:
- app/src/main/java/com/toco/ai/island/MoodEngine.kt
Change:
- app/src/main/java/com/toco/ai/island/IslandService.kt
- app/src/main/java/com/toco/ai/core/Prefs.kt
- app/src/main/java/com/toco/ai/ui/settings/SettingsFragment.kt
- app/src/main/res/values/strings.xml
- app/build.gradle (version only)
Add beta note for next version.

## Phase 3 — Island chat + providers (planned)
Add:
- app/src/main/java/com/toco/ai/ai/ClaudeProvider.kt
- app/src/main/java/com/toco/ai/ai/OpenAIProvider.kt
Change:
- app/src/main/java/com/toco/ai/island/IslandService.kt
- app/src/main/java/com/toco/ai/ai/Ai.kt
- app/src/main/java/com/toco/ai/core/Prefs.kt
- app/src/main/java/com/toco/ai/ui/models/ModelsFragment.kt
- app/src/main/res/layout/overlay_toco_island.xml
- app/src/main/res/layout/fragment_models.xml (only if current dynamic model UI needs a host change)
- app/src/main/res/values/strings.xml
- app/build.gradle (version and BuildConfig inputs only if required by existing provider pattern)
Reuse without restructuring:
- engine/CommandEngine.kt
- skill/SkillRegistry.kt

## Phase 4 — Local agent bridge (planned)
Add:
- app/src/main/java/com/toco/ai/island/agent/LocalAgentBridge.kt
- app/src/main/java/com/toco/ai/island/agent/AgentEvent.kt
- tools/toco-termux-hook.sh
Change:
- app/src/main/java/com/toco/ai/island/IslandService.kt
- app/src/main/res/layout/overlay_toco_island.xml
- app/src/main/res/values/strings.xml
- app/build.gradle (version; only add a dependency if the JDK server APIs prove unsuitable on Android)
The server binds only to 127.0.0.1.

## Phase 5 — Status card plugins (planned)
Add:
- app/src/main/java/com/toco/ai/island/card/StatusCardPlugin.kt
- app/src/main/java/com/toco/ai/island/card/GitHubStatusCard.kt
Change:
- app/src/main/java/com/toco/ai/island/IslandService.kt
- app/src/main/res/layout/overlay_toco_island.xml
- app/src/main/res/values/strings.xml
- app/build.gradle (version only unless API implementation requires otherwise)

## Phase 6 — Share to TOCO (planned)
Add:
- app/src/main/java/com/toco/ai/island/ShareToTocoActivity.kt
Change:
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/toco/ai/island/IslandService.kt
- app/src/main/res/values/strings.xml
- app/build.gradle (version only)
Intent filters will accept ACTION_SEND text, images and generic files, then hand context to the Island.
