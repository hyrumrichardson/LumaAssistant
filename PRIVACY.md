# Luma privacy policy

_Last updated: October 13, 2026_

Luma is a voice assistant that sets timers, switches Google Home devices, and starts Google Maps
navigation. It has no accounts, ads, analytics, or crash reporting, and it doesn't collect, sell, or
share your personal data. Luma's own code doesn't connect to the internet.

## Your voice

Luma uses the microphone only while its popup is listening, after you invoke it. It doesn't record,
store, or upload audio or transcripts.

Speech is turned into text by your phone's speech recognition service, not by Luma. On most Android
phones that's Google's, which may process audio on Google's servers under
[Google's privacy policy](https://policies.google.com/privacy). Spoken replies use your phone's
text-to-speech engine.

## Google Home

If you connect Google Home, Luma reads the names and rooms of your switchable devices and sends on/off
commands to them through Google Play services. That exchange is covered by Google's privacy policy.
Luma only gets access to the home you choose. You can revoke it any time in your Google Account under
**Data & privacy > Third-party apps & services**.

## Maps and timers

When you ask for directions, Luma passes the destination to Google Maps. When you set a timer, it
passes the length and name to your Clock app. Those apps handle the data under their own policies.

## What Luma stores on your phone

- Your preferences, such as whether replies are spoken.
- Scheduled device actions ("turn off the lights in 10 minutes"): the device IDs, a description, and
  the time. Each one is deleted once it runs or you cancel it.

This data never leaves your phone. It's excluded from cloud backups and device-to-device transfers,
and uninstalling Luma deletes it.

## Lock screen

Luma can open over the lock screen, but it asks you to unlock before switching devices, setting a
timer, or starting navigation. Someone holding your locked phone can't control your home.

## Permissions

| Permission | Why |
| --- | --- |
| Microphone | To hear your command while the popup is listening |
| Set alarms | To start timers in your Clock app |
| Alarms & reminders | To run scheduled device actions on time |
| Notifications | To tell you when a scheduled action has run (optional) |
| Run at startup | To restore scheduled actions after the phone restarts |
| Internet | Declared for the Google Home APIs. Luma's own code makes no network requests |

## Contact

Questions or concerns: [open an issue](https://github.com/hyrumrichardson/LumaAssistant/issues).
