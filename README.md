# Luma

[![Build](https://github.com/hyrumrichardson/LumaAssistant/actions/workflows/build.yml/badge.svg)](https://github.com/hyrumrichardson/LumaAssistant/actions/workflows/build.yml)

A replacement for a certain company's brutal dismantling of their useful assistant, replaced with crappy AI services.

Luma is a small Android voice assistant. Set it as your phone's digital assistant, invoke it (long-press
power or home, corner swipe, or whatever gesture your phone uses), and a popup listens and does one of
three things:

| Say | What happens |
| --- | --- |
| "Set a timer for 10 minutes", "Set a pasta timer for 8 minutes" | Starts a timer in your Clock app |
| "Turn off the living room lights", "Turn on the fan for half an hour", "Turn off the lights in 10 minutes", "...at 10:30 pm" | Switches Google Home devices now or later |
| "Navigate to the Space Needle", "Walking directions to the library", "Take me home" | Starts Google Maps navigation |

Commands are understood with hand-written patterns, like [Dicio](https://github.com/Stypox/dicio-android)'s
sentence templates. There's no AI service or cloud parsing. Speech-to-text uses the phone's built-in
recognizer. See [PRIVACY.md](PRIVACY.md) for what data goes where.

Every push to `main` and every pull request is built and tested by GitHub Actions. The debug APK is
attached to each run under **Artifacts**. CI builds without the Google Home SDK, so those APKs can't
control devices.

## Build

Open the project in Android Studio (2026.2 or newer) and run it, or from the command line:

```sh
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest  # parser and device-matching tests
```

Without the Google Home APIs SDK (below), Luma builds with device control turned off. Timers and
navigation still work.

## Set up on your phone

1. Install Luma and open it.
2. Tap **Open settings** next to *Default assistant* and pick Luma as the **Digital assistant app**.
3. Allow the microphone. Also allow **Alarms & reminders** so "in 10 minutes" actions run on time, and
   **Notifications** if you want to be told when they run.
4. Connect Google Home (once the SDK is set up, below).

## Google Home device control

Google's [Home APIs](https://developers.home.google.com/apis/android/overview) are in open beta. They
aren't on Google Maven yet, so there's some one-time setup.

### 1. Install the SDK

1. Sign in at [developers.home.google.com](https://developers.home.google.com/apis/android/sdk) and
   download the Home APIs Android SDK.
2. Install it into your local Maven repo (`~/.m2/repository`) following the instructions in the
   download, so that this folder exists:
   `~/.m2/repository/com/google/android/gms/play-services-home-types/17.1.0`
3. If your version isn't 17.1.0, change `homeApis` in [gradle/libs.versions.toml](gradle/libs.versions.toml).

The build detects the SDK automatically and prints `Luma: Google Home APIs enabled`. To force it on or
off, set `luma.homeApis=true` or `false` in `gradle.properties`.

### 2. Set up OAuth

The Home APIs use OAuth to get access to your home. As the developer, you're also the test user.

1. In the [Google Cloud console](https://console.cloud.google.com/), create or pick a project.
2. Go to **APIs & Services > OAuth consent screen**. Choose **External**, fill in the app name, skip
   scopes, and add your own Google account under **Test users**.
3. Go to **APIs & Services > Credentials > Create credentials > OAuth client ID**. Choose **Android**,
   enter package name `io.github.hyrumrichardson.luma`, and add your signing certificate's SHA-1. For debug builds,
   run `./gradlew signingReport` and copy the `SHA1` for the `debug` variant.

### 3. Connect

Open Luma, tap **Connect Google Home**, choose your account and home, and allow access. Your switchable
devices then show up in the app.

### Notes

- Requires a phone with Google Play services and the Google Home app, with your devices already working
  in Google Home.
- Luma can switch anything the Home APIs expose with an on/off control: lights, plugs, switches and fans.
  Matter devices need a [supported hub](https://developers.home.google.com/apis/android/sdk#supported_hubs).
- Devices are matched by name and room: "kitchen lights" means every light in the Kitchen, or a single
  device named "Kitchen Lights" if you have one. "Everything in the bedroom" and "all the lights" work too.
- Scheduled actions ("in 10 minutes", "at 11 pm", "for an hour") run on your phone with exact alarms, so
  the phone has to be on at the scheduled time. Pending actions are listed in the app, where you can
  cancel them.

## Project layout

```
app/src/main/java/io/github/hyrumrichardson/luma/
  assist/     The assistant popup: activity, Compose sheet, speech in/out, command execution
  nlu/        Command parsing: timers, device control, navigation, durations, number words
  home/       Smart home interface and fuzzy device/room matching
  schedule/   Delayed device actions (AlarmManager + WorkManager)
app/src/homeapi/  Google Home APIs implementation (compiled when the SDK is installed)
app/src/homestub/ Stand-in used when it isn't
tools/icon/       Generates the launcher icon (python tools/icon/generate_icon.py)
```

## Debugging

Debug builds accept a typed command over adb, which skips the microphone. Only the adb shell can do
this; other apps on the phone can't.

```sh
adb shell am start -n io.github.hyrumrichardson.luma/.assist.DebugQuery --es io.github.hyrumrichardson.luma.QUERY "'set a timer for 2 minutes'"
```
