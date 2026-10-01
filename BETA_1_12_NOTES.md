# TOCO beta 1.12

- GitHub Actions now restores the permanent TOCO signing keystore from encrypted repository secrets.
- Release APKs are signed with the permanent TOCO certificate instead of an ephemeral debug certificate.
- CI verifies the signing SHA-1 matches the Android OAuth client certificate before building.
- The keystore is deleted from the runner after the job.
- Android version bumped to 0.1.12-beta (versionCode 12).

Required GitHub Actions secrets:
- TOCO_KEYSTORE_BASE64
- TOCO_KEYSTORE_PASSWORD
- TOCO_KEY_ALIAS
- TOCO_KEY_PASSWORD
- GOOGLE_WEB_CLIENT_ID
