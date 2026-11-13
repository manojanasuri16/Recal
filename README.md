# Recal

**Remember why you called.**

Recal is a small, offline Android utility. When you call someone and they don't pick up, Recal asks
*why you called* in a compact card. When that person calls you back, the reason is on screen before
you answer. Once you've actually talked, it clears itself.

- **No account, no cloud, no ads.** The app has no `INTERNET` permission, so nothing can leave the phone.
- **Quiet UI.** A top banner after a call and a small bubble during one. No full-screen takeovers.
- **Works on the lock screen.** Most calls are answered with the phone locked; Recal is built for that.

## How it works

| Moment | What Recal does |
| --- | --- |
| You call, nobody answers (no answer, declined, busy, cancelled) | A banner asks **"Why did you call?"**. Type a few words and press send. |
| You call again later, still unanswered | A fresh reminder. Anything already waiting is listed under the field. |
| They call you back | A bubble shows your reason(s), over the incoming-call screen and during the call. |
| You answer and the call ends | Every reminder that was pending *before* the call is cleared (the bubble turns green: "Cleared"). |
| You miss or reject the callback | The reminder stays. |
| You reach them on an outgoing call while reminders are pending | A banner asks **"Did you cover this?"**. Nothing is cleared without your say-so. |

**In-call Notes** (off by default, Settings): a small notes field in the same bubble, per contact, during any call.

The **+** button on the home screen adds a reason by hand, for the cases Android cannot classify (see below).

## Install

Download the APK from the [Releases](https://github.com/manojanasuri16/Recal/releases) page and open it
on your phone (allow installs from your browser or file manager when asked). Then follow the first-run
screen:

1. **Phone & call log**: lets Recal notice calls and read whether a call connected.
2. **Display over other apps**: the bubble.
3. **Notifications**: fallback when the bubble or banner cannot appear.
4. **Contacts** (optional): shows names instead of numbers.

On Android 13+ a sideloaded app may show *"Restricted setting"* for some of these. Open **Settings →
Apps → Recal**, tap the **⋮** menu and choose **Allow restricted settings**, then grant the permission again.

Xiaomi / Samsung / OnePlus and similar phones also need background pop-ups and battery restrictions
relaxed. See [docs/LIMITATIONS.md](docs/LIMITATIONS.md). Settings shows the exact hint for your phone.

## Build

Requires JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew testDebugUnitTest      # unit tests (lifecycle, number matching)
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # signed if RECAL_STORE_* properties are set, else debug-signed
```

Release signing reads `RECAL_STORE_FILE`, `RECAL_STORE_PASSWORD`, `RECAL_KEY_ALIAS` and
`RECAL_KEY_PASSWORD` from `~/.gradle/gradle.properties`. Keys never live in the repo.

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): how detection, the lifecycle and the windows work, and why.
- [docs/LIMITATIONS.md](docs/LIMITATIONS.md): what Android does and doesn't allow, and what that means for Recal.
- [docs/RESEARCH.md](docs/RESEARCH.md): competitor and platform research behind the decisions.

## Privacy

Everything is stored in a local SQLite database (`recal.db`) and a preferences file in the app's private
storage. Backups are disabled. Uninstalling removes it all. Notification text on the lock screen is
redacted (private), and **Settings → Show reasons on lock screen** keeps reasons off a locked screen entirely.
