# Research behind Recal

Recal is an offline Android app that asks why you called after an unanswered outgoing call, and shows that reason when the person calls back.

## Competitor landscape

Method: web research. Google Play pages were not scrapable, so user reviews were not read. Negative results come from a limited search.

- **Call Notes Pro**: floating window with your note and contact details.
- **Call Note: Save Note After Call**: floating button after incoming, outgoing and missed calls; works offline. Closest UI analogue, but has no reason/callback loop.
- **Many "call notes" and "call reminder" apps on Play**: reminder apps mostly schedule a time-based alarm to call someone, which is a different thing.
- **Truecaller**: after-call screen reportedly cannot be fully disabled.
- **Google Phone "Call Reason"**: the caller flags a call as urgent. Both parties need Google's dialer. Not stored for your own memory.
- **Call.app**: contact reminders with a custom note.
- **RefreshMe** (old): notes attached to calls.
- **F-Droid**: no call-notes app found.

No app found that asks after an unanswered outgoing call, stores the reason on the contact, shows it on the callback, and auto-clears it after the conversation. This is a negative result from a limited search, not proof.

Pain points seen:

- After-call ad abuse of overlay permissions makes users wary (Malwarebytes, 2026).
- Truecaller's popup cannot be dismissed.
- Xiaomi needs several manual settings (pop-ups in background, lock-screen permission, autostart, battery) before popups show.

Differentiators chosen:

- One narrow loop: ask once, show once, clear itself.
- No ads, no account, no internet permission.
- Compact, dismissible UI.
- First-run guidance for OEM settings.

## Android platform findings

- **PHONE_STATE broadcast**: needs READ_PHONE_STATE. The number is only delivered with READ_CALL_LOG. With both permissions, each state arrives twice (once with the number, once without). The manifest receiver is exempt from implicit-broadcast limits.
- **Outgoing calls** go IDLE -> OFFHOOK -> IDLE whether answered or not. The call log (OUTGOING row; duration 0 means never connected) is the reliable signal. Verified on an Android 14 emulator.
- **Call APIs**: CallScreeningService and CallRedirectionService need roles and never learn the outcome. InCallService is for default dialers. ACTION_NEW_OUTGOING_CALL is deprecated and needs the restricted PROCESS_OUTGOING_CALLS permission.
- **Distribution**: Google Play restricts READ_CALL_LOG to default handlers and listed exceptions. Call reminders/notes are not listed, so the app is distributed as a sideloaded APK.
- **Foreground services**: Android 12+ limits background foreground-service starts. SYSTEM_ALERT_WINDOW exempts background activity starts. Android 15 requires a visible overlay for the FGS exemption. The specialUse FGS type was chosen; the phoneCall type is for ConnectionService apps.
- **Lock screen** (tested; contradicted an early assumption):
  - Application-overlay windows are hidden whenever the keyguard shows, even over the dialer's call screen.
  - An Activity-based card did show, but broke tapping Answer on the dialer, so it was rejected.
  - Setting the legacy FLAG_SHOW_WHEN_LOCKED flag on the overlay window keeps it visible, and Answer still works.
  - A show-when-locked Activity works for the post-call banner, including the keyboard on a PIN lock screen.

## Models used

- Competitor research: Sonnet-class model.
- Platform/telephony research and lock-screen research: Opus-class model.
- Independent code review: Opus-class model.
- Doc condensation: Haiku-class model.
- Implementation, debugging and device testing: the main session model.
