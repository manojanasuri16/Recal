# Architecture

```
PHONE_STATE broadcast ──► CallReceiver ──► CallState (persisted phase / session start / call-log watermark)
                               │
        RINGING / OFFHOOK + number ──► Repo.pendingFor(number) ──► CallOverlay (bubble) + OverlayService
                               │
                         IDLE ──► CallResolver.resolve ──► read CallLog since watermark
                                                          ├─ unanswered OUTGOING ─► PromptActivity (ask why)
                                                          ├─ answered OUTGOING + pending ─► PromptActivity (covered?)
                                                          └─ answered INCOMING ─► Repo.markDone ─► bubble "Cleared"
```

## Decisions

- **Why a manifest `PHONE_STATE` receiver and no always-on service.** It is exempt from implicit-broadcast limits, costs
  nothing when idle, and wakes the process per call. The only service is a short foreground service while a bubble exists.
- **The call log is the source of truth** for answered vs. not (see LIMITATIONS 1). `CallResolver.apply` is a pure function
  over call-log rows, so the whole lifecycle is unit-tested without a phone (`LifecycleTest`).
- **Watermark.** The newest call-log timestamp already handled is persisted. First use pins it to "now" so history from
  before install is never acted on, and a missed broadcast (process killed) is caught up on the next call.
- **Consume only what was pending before the call** (`createdAt < row.date`). A reminder written during the callback
  survives it.
- **Reasons are grouped per person** by a canonical digit string; `+91 98765 43210` and `098765 43210` share one list.
- **Two UI surfaces, chosen by what each needs.** The bubble is an overlay window (never focusable, so it cannot take
  the Answer/End buttons from the call screen). The post-call banner is an Activity (needs a real keyboard and focus).
- **Notification fallback** carries the same content when overlay permission is missing or the phone is locked with
  reasons hidden.
- **Design.** Light/dark/system themes, system font with size-dependent tracking, critically damped springs with bounce
  only after a flick, drag with velocity hand-off and projected landing, rubber-banding at screen edges, press feedback on
  touch-down, reduced-motion respected. The Apple design principles in the `apple-design` skill drove these choices.

## Layout

```
app/src/main/java/app/recal/
  call/      CallReceiver, CallResolver, CallLogReader, CallState, Notifications
  data/      Room entities/DAO, Repo, PhoneNumbers, Settings, Contacts
  overlay/   CallOverlay (bubble window), CallBubble (Compose), OverlayService, PromptActivity
  ui/        Theme, Components, Home, Sheets, SettingsScreen, Onboarding
```
