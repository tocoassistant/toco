# TOCO Beta 1.13

- Removed the machine-specific `org.gradle.java.home=/opt/java/jdk-17.0.20+8` setting that broke GitHub Actions.
- GitHub Actions now uses the Java 17 installation supplied by `actions/setup-java`.
- Updated checkout/setup-java actions to v5.
- Version bumped to 0.1.13-beta (versionCode 13).
- Signing keystore is not included in this source package; CI restores it from GitHub Actions secrets.
