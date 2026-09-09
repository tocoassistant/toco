# Building TOCO on-device (proot / M4Coding IDE)

## One-time

```
chmod +x gradlew
```

Confirm the JDK is 17 — AGP 8.2.2 rejects Java 8:

```
./gradlew -version
```

`JVM:` must read 17.x. If it reads 1.8, edit `org.gradle.java.home` in
`gradle.properties` to match the real path from `ls /opt/java`.

## Every build

```
export GRADLE_OPTS="-Xmx900m -XX:MaxMetaspaceSize=256m"
./gradlew assembleDebug --no-daemon --max-workers=1 > build.log 2>&1
tail -40 build.log
```

Always log to a file. Piping to `tail` loses everything if the process is
killed, which is the common failure here.

APK: `app/build/outputs/apk/debug/app-debug.apk`

## If it says only "Killed"

That is Android's low-memory killer taking the whole proot app, not a Gradle
error. Nothing in this project can prevent it. What helps:

- Settings -> Apps -> your IDE -> Battery -> **Unrestricted**
- Recents -> long-press the IDE -> **lock** it
- Screen on, stay inside the app for the whole build
- Close everything else first, then check `free -m` (want `used` under 4000)
- Build in stages so progress is not lost:

```
./gradlew processDebugResources --no-daemon > b1.log 2>&1
./gradlew compileDebugKotlin    --no-daemon > b2.log 2>&1
./gradlew assembleDebug         --no-daemon > b3.log 2>&1
```

If it is still killed, compile off-device (GitHub Actions builds this in about
two minutes) and copy the APK back. Editing stays on the phone.
