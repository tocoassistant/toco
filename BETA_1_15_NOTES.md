# TOCO Beta 1.15

## Stability fix
- Removed mandatory login from startup. TOCO now opens onboarding/home without requiring Google, phone, or TOCO ID.
- Existing authentication code is kept for later optional account/cloud features.
- TOCO Island Phase 1 remains available from Settings and is no longer blocked by login.
- Existing WakeWordService, TocoSession, CommandEngine, skills, signing config, and GitHub Actions workflow are unchanged.

## Abuse / spam protection
- Existing local AI limiter remains active: 1.5s minimum spacing, 12 requests/minute, 120/hour, 4,000-character prompt cap.
- Supabase backend hardened with authenticated-only user data access, ownership RLS, parent-session ownership checks, payload size constraints, and server-side write throttling.
- Anonymous users can only read public app configuration; user records require authentication.

## Test
1. Fresh install: app must not show LoginActivity.
2. Complete onboarding (or existing install goes directly Home).
3. Open Settings and enable TOCO Island.
4. Confirm overlay pill appears and expands/collapses.
5. Check a basic command such as “open YouTube”.
6. Check AI rapid-tap spam returns a rate-limit message rather than sending every request.
