# Emulator test artifacts (local)

`mxplayer-emulator-signing.jks` is created on first `buildMxPlayerEmulatorApk` run (alias/password: `morphe`) and reused so `adb install -r` works across builds.

Do not commit the `.jks` file; back it up locally if you rely on incremental installs.