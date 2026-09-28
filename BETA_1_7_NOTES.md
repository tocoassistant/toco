# TOCO Beta 1.7 — Authentication stabilization

- Replaced browser-based Google OAuth launch with Android Credential Manager native Google sign-in.
- Google ID token is exchanged directly with Supabase Auth; no TOCO browser callback is exposed.
- Added GOOGLE_WEB_CLIENT_ID build configuration hook (local.properties or environment/GitHub secret).
- Improved TOCO ID error handling around Supabase email confirmation.
- LoginActivity is no longer exported because browser OAuth callback is no longer required.

## Required dashboard configuration

1. Google Cloud: create/configure OAuth consent + Web OAuth client ID and Android OAuth client for package `com.toco.ai` and your signing SHA-1/SHA-256.
2. Supabase Auth > Providers > Google: enable Google and enter the Web client ID + client secret.
3. Add `GOOGLE_WEB_CLIENT_ID` to local.properties for local builds and GitHub Actions secrets/environment for CI.
4. TOCO IDs currently use Supabase email/password with synthetic `@toco.io` identities. Until real TOCO Mail exists, turn OFF email confirmation for email signups or those accounts cannot receive the confirmation mail.
5. Phone OTP requires a configured SMS provider in Supabase; it is currently disabled server-side.
