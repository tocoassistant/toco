# TOCO Beta 1.5

## Added
- Supabase-backed account gate before onboarding.
- Google sign-in browser flow.
- Phone OTP sign-in.
- TOCO ID (`username@toco.io`) + password sign-in/create flow.
- Encrypted local session storage.
- Profile account label and Sign out.
- Backend migration source for per-user profiles and server-side admin gating.

## Security model
- No service-role/admin database key is shipped in the APK.
- User data remains protected by Supabase Auth + Row Level Security.
- Developer/support roles are server-owned, not a local boolean or hidden tap sequence.

## Setup needed in Supabase
- Enable Google provider and allow redirect URI: `toco://auth/callback`.
- Configure SMS provider for phone OTP.
- For TOCO-ID creation before real `@toco.io` mail exists, email confirmation must not require delivery to that domain.

## Build check
XML/resource parsing passed. Full Gradle compilation could not run in this environment because the Gradle distribution host is unreachable here; use the existing GitHub Actions build after pushing.
