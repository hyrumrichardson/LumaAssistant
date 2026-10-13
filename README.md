# Luma

[![Build](https://github.com/hyrumrichardson/LumaAssistant/actions/workflows/build.yml/badge.svg)](https://github.com/hyrumrichardson/LumaAssistant/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/hyrumrichardson/LumaAssistant)](https://github.com/hyrumrichardson/LumaAssistant/releases/latest)

A replacement for a certain company's brutal dismantling of their beloved assistant, to be replaced with crappy AI services that nobody wanted.

Luma is a small Android voice assistant. Set it as your phone's digital assistant, invoke it (long-press
power or home, corner swipe, or whatever gesture your phone uses), and a popup listens and acts:

| Say | What happens |
| --- | --- |
| "Set a timer for 10 minutes", "Set a pasta timer for 8 minutes" | Starts a timer in your Clock app |
| "Turn on the flashlight", "Flashlight off" | Switches your phone's flashlight |
| "Navigate to the Space Needle", "Walking directions to the library", "Take me home" | Starts Google Maps navigation |
| "Turn off the living room lights", "Turn on the fan for half an hour", "Turn off the lights in 10 minutes", "...at 10:30 pm" | Switches Google Home devices now or later ([build it yourself](#google-home-device-control)) |
| Anything else, like "How tall is Mount Everest?" | Searches the web in your default browser |

Commands are understood with hand-written patterns, like [Dicio](https://github.com/Stypox/dicio-android)'s
sentence templates. There's no AI service or cloud parsing. Speech-to-text uses the phone's built-in
recognizer. See [PRIVACY.md](PRIVACY.md) for what data goes where.

## Install

Requires Android 10 or newer.

- **Download:** get `luma-v*.apk` from the [latest release](https://github.com/hyrumrichardson/LumaAssistant/releases/latest)
  and open it on your phone. Android will ask you to allow installs from your browser or file manager.
- **Auto-updates:** add `https://github.com/hyrumrichardson/LumaAssistant` to
  [Obtainium](https://github.com/ImranR98/Obtainium), and it'll install new releases as they come out.

To check you've got a genuine copy, the release signing certificate's SHA-256 fingerprint is:

```
3B:FE:7B:80:2F:21:7E:77:07:0A:18:18:F0:99:86:DC:1E:D0:AC:51:00:AA:91:CE:62:51:47:B8:54:44:A8:BC
```

Release builds don't include Google Home device control; see below to add it.

## Set up on your phone

1. Open Luma.
2. Tap **Open settings** next to *Default assistant* and pick Luma as the **Digital assistant app**.
3. Allow the microphone.
4. Optionally, pick your search engine under **Preferences**. Searches open in your default browser.

Then trigger your phone's assistant gesture and try "set a timer for 1 minute".

## Google Home device control

Google's [Home APIs](https://developers.home.google.com/apis/android/overview) are in open beta, and the
SDK is only available to signed-in developers, so release builds can't include it. To control your
devices, you build Luma yourself. It's a one-time setup.

You'll need a computer with [Android Studio](https://developer.android.com/studio), and a phone with the
Google Home app and your devices already working in it.

### 1. Install the SDK on your computer

1. Sign in at [developers.home.google.com](https://developers.home.google.com/apis/android/sdk) and
   download the Home APIs Android SDK.
2. Install it into your local Maven repo (`~/.m2/repository`) following the instructions in the
   download, so that this folder exists:
   `~/.m2/repository/com/google/android/gms/play-services-home-types/17.1.0`
3. If your version isn't 17.1.0, change `homeApis` in [gradle/libs.versions.toml](gradle/libs.versions.toml).

The build detects the SDK automatically and prints `Luma: Google Home APIs enabled`. To force it on or
off, set `luma.homeApis=true` or `false` in `gradle.properties`.

### 2. Set up OAuth

The Home APIs use OAuth to get access to your home. You'll be both the developer and the test user.

1. In the [Google Cloud console](https://console.cloud.google.com/), create a project.
2. Go to **APIs & Services > OAuth consent screen**. Choose **External**, fill in the app name, skip
   scopes, and add your own Google account under **Test users**.
3. Go to **APIs & Services > Credentials > Create credentials > OAuth client ID**. Choose **Android**,
   enter package name `io.github.hyrumrichardson.luma`, and add the SHA-1 fingerprint of the key that
   signs your build. Run `./gradlew signingReport` and copy the `SHA1` for the variant you'll install
   (`debug` unless you've set up your own release key).

### 3. Build, install, and connect

1. Uninstall any downloaded copy of Luma first. Your build is signed with a different key, so it can't
   update over it.
2. Connect your phone with USB debugging on, and run the app from Android Studio, or:
   `./gradlew installDebug`
3. Open Luma, tap **Connect Google Home**, choose your account and home, and allow access. Your
   switchable devices then show up in the app.
4. Allow **Alarms & reminders** so "in 10 minutes" actions run on time, and **Notifications** if you want
   to be told when they run.

### Notes

- Luma can switch anything the Home APIs expose with an on/off control: lights, plugs, switches and fans.
  Matter devices need a [supported hub](https://developers.home.google.com/apis/android/sdk#supported_hubs).
- Devices are matched by name and room: "kitchen lights" means every light in the Kitchen, or a single
  device named "Kitchen Lights" if you have one. "Everything in the bedroom" and "all the lights" work too.
- Scheduled actions ("in 10 minutes", "at 11 pm", "for an hour") run on your phone with exact alarms, so
  the phone has to be on at the scheduled time. Pending actions are listed in the app, where you can
  cancel them.
- On the lock screen, Luma asks you to unlock before switching devices.

## Known limitations

- **Flashlight:** Android turns the flashlight off if it closes Luma in the background. Some phones do
  this aggressively; if yours does, exempt Luma from battery optimization.

## Development

Open the project in Android Studio (2026.2 or newer) and run it, or from the command line:

```sh
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest  # parser and device-matching tests
```

Every push to `main` and every pull request is built and tested by GitHub Actions, and the debug APK is
attached to each run under **Artifacts**.

Debug builds accept a typed command over adb, which skips the microphone. Only the adb shell can do
this; other apps on the phone can't.

```sh
adb shell am start -n io.github.hyrumrichardson.luma/.assist.DebugQuery --es io.github.hyrumrichardson.luma.QUERY "'set a timer for 2 minutes'"
```

### Project layout

```
app/src/main/java/io/github/hyrumrichardson/luma/
  assist/     The assistant popup: activity, Compose sheet, speech in/out, command execution
  nlu/        Command parsing: timers, flashlight, device control, navigation, search
  home/       Smart home interface and fuzzy device/room matching
  phone/      The phone's own hardware (flashlight)
  schedule/   Delayed device actions (AlarmManager + WorkManager)
app/src/homeapi/  Google Home APIs implementation (compiled when the SDK is installed)
app/src/homestub/ Stand-in used when it isn't
tools/icon/       Generates the launcher icon (python tools/icon/generate_icon.py)
```

### Releasing

Releases are built and signed by the [Release workflow](.github/workflows/release.yml) when a version
tag is pushed:

1. Bump `luma.versionName` in [gradle.properties](gradle.properties). Minor and patch numbers must stay
   below 100, since the Android version code is derived from them (0.1.0 becomes 100, 1.2.3 becomes 10203).
2. Commit, then tag and push: `git tag v0.2.0 && git push origin main v0.2.0`

The workflow checks that the tag matches the version, runs the tests, builds and signs the APK, and
publishes it to GitHub Releases. Google Home is never included in release builds.

Signing uses two repository secrets (**Settings > Secrets and variables > Actions**):

| Secret | Value |
| --- | --- |
| `LUMA_KEYSTORE_BASE64` | The release keystore, base64-encoded |
| `LUMA_KEYSTORE_PASSWORD` | Its password |

To build a signed release locally, put the same details in `~/.gradle/gradle.properties` (never in the
repo):

```properties
luma.keystore.file=/path/to/luma-release.jks
luma.keystore.password=...
```

Keep the keystore backed up somewhere safe. Every future release must be signed with it, or existing
installs can't update.

## License

Luma is licensed under the [Apache License 2.0](LICENSE). The Google Home APIs SDK it can optionally be
built with is Google's, under Google's own terms.
