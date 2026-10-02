# TOCO Beta 1.14 — TOCO Island Phase 1

## Added
- `IslandService`: a separate foreground service for the persistent floating Island.
- Compact top pill and animated expanded state using the existing WindowManager overlay permission.
- Existing `OrbView` now supports Island moods: idle, sleep, yawn, wink, peek, think, dizzy, proud, annoyed and love.
- `SoundEngine` uses Android `SoundPool`; silent placeholder WAV files are included in `res/raw` for later replacement.
- Settings now has a TOCO Island toggle. If overlay permission is missing, TOCO uses the existing overlay permission notice and opens Android's overlay settings.
- Island preference persists and the Island is restored when TOCO is opened again.

## Kept unchanged
- WakeWordService and TocoSession behavior.
- Existing missed-call overlay behavior.
- Existing AI providers, CommandEngine and skills.
- Signing configuration and GitHub Actions workflow.

## Phase 1 test
1. Build the release APK with the existing GitHub Actions workflow.
2. Install/open TOCO and grant Display over other apps if needed.
3. Settings -> TOCO Island -> On.
4. Confirm a small TOCO pill appears at the top over other apps.
5. Tap the orb/TOCO label: pill expands smoothly. Tap minus: it collapses.
6. Turn TOCO Island Off: overlay and foreground notification disappear.
7. Confirm wake word and missed-call features still behave as before.

Next phase: mood timers, event-triggered moods and absence greeting delay.
