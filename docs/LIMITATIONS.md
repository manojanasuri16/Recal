# Android platform limitations

Recal is an ordinary app. It is **not** the default dialer, so it sees only what Android gives every app.
These are the consequences, verified on an Android 14 emulator unless noted.

## Detecting calls

1. **An unanswered outgoing call looks identical to an answered one while it happens.** The telephony
   state goes `IDLE → OFFHOOK → IDLE` either way, and the public API has no "ringing at the other end" or
   "connected" signal (`READ_PRECISE_PHONE_STATE` is privileged). Recal therefore decides **after hang-up**
   by reading the call log: an `OUTGOING` row with `duration == 0` means it never connected. The banner
   appears a second or two after you hang up, not while it rings.
2. **Voicemail, carrier announcements and IVRs count as "answered".** The network sends a connect, the
   call log records a duration, and Recal stays quiet. Use **+** on the home screen to add the reason.
3. **A call answered and dropped within one second** is logged with duration 0 and is treated as unanswered.
4. **Call-log timing.** The row is written asynchronously after the call ends. Recal waits up to 8 seconds
   for it. If the row never shows (slow OEM providers), nothing is prompted; the call is picked up on the
   next call because Recal remembers how far into the log it has read.
5. **Not covered:** WhatsApp, Signal, Meet and other internet calls (they bypass the phone call log), and
   hidden / private numbers (nothing to match against). Dual-SIM works since the broadcast fires for both.
6. **Number matching** compares digits and treats two numbers as the same person when one is a suffix of
   the other with at least 7 digits in common. It never matches short codes against longer numbers.
   Same-last-7-digits numbers from different countries could collide in theory.

## Showing things

7. **Lock screen.** Android hides every application-overlay window while the keyguard is showing, even when
   the dialer's call screen is drawn over it. Recal sets the legacy `FLAG_SHOW_WHEN_LOCKED` window flag on
   the bubble, which does keep it visible (verified on API 34). It is deprecated, so a future Android
   release could stop honouring it; the notification fallback would then take over. Android 13+ lets users
   hide reasons from the lock screen; Recal offers its own switch too.
8. **Typing on a locked phone.** The post-call banner is an Activity declared show-when-locked and takes the
   keyboard on a PIN-locked screen (verified). The *bubble* is non-focusable so it can never steal the
   call screen's buttons; typing a note into it may require unlocking on some phones.
9. **Over secure system screens** (permission dialogs, Settings in some builds) Android deliberately hides
   overlays. The bubble will not appear there.
10. **Background starts.** The banner launches from a broadcast receiver, allowed because the user granted
    "Display over other apps". Without that permission Recal falls back to a notification with an inline
    reply field (which on a locked phone usually needs an unlock first).
11. **Foreground service.** During a call Recal runs a short `specialUse` foreground service so the bubble
    survives. Android 15 only permits starting it while an overlay is visible, which Recal arranges; if the
    start is refused, the visible overlay itself keeps the process alive.

## Manufacturers

12. **Xiaomi / Redmi / POCO (MIUI, HyperOS):** enable *Autostart*, *Display pop-up windows while running in
    the background* and *Show on lock screen* in the app's permissions, and set battery to *No restrictions*.
13. **Samsung:** Battery → *Unrestricted*; remove Recal from *Sleeping apps*.
14. **OnePlus / Oppo / Realme / Vivo / Huawei:** allow auto-launch and background activity; lock the app in recents.
    See <https://dontkillmyapp.com>. Settings → Reliability shows the right hint for the detected brand.
15. Dialers differ in how they log call duration and in how they handle overlays. Recal has been exercised
    with the Google dialer; other dialers should be tested.

## Distribution

16. **Google Play** restricts `READ_CALL_LOG` to default dialer/SMS apps and a short list of exceptions
    that does not include call notes or reminders. Recal needs the call log to know whether a call
    connected, so it is distributed as a **sideloaded APK** (GitHub Releases / F-Droid style), not on Play.
17. **Android 13+ "Restricted settings"** may block the call-log and overlay grants for sideloaded APKs until the
    user allows them in App info. The first-run screen explains this.
