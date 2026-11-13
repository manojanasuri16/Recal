package app.recal.call

import android.content.Context
import app.recal.data.Contacts
import app.recal.data.Reminder
import app.recal.data.Repo
import app.recal.data.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What to put in front of the user once a call has ended. */
sealed interface Outcome {
    /** An outgoing call did not connect: ask why they called. */
    data class AskWhy(val number: String, val name: String?, val existing: List<Reminder>) : Outcome
    /** An outgoing call connected while reminders were pending: ask whether they are covered. */
    data class AskCovered(val number: String, val name: String?, val pending: List<Reminder>) : Outcome
    /** A callback was answered and its reminders were consumed. */
    data class Cleared(val count: Int) : Outcome
    data object Nothing : Outcome
}

/**
 * Decides what a finished call means. The system call log is the source of truth: the
 * telephony state alone goes IDLE → OFFHOOK → IDLE for an outgoing call whether or not anyone
 * picked up, so only the logged duration can tell the two apart.
 */
object CallResolver {
    private val lock = Mutex()
    private const val WAIT_MS = 7_000L
    /** After the expected row appears, keep looking until the log has been quiet this long. */
    private const val STABLE_MS = 700L
    private const val POLL_MS = 350L
    /**
     * A call-log row is stamped when the call is created, which can be well before our first
     * broadcast (SIM picker, slow radio). A row belongs to the session that just ended if the
     * call it describes finished no earlier than this long before the session began.
     */
    private const val SLACK_MS = 120_000L

    private fun CallRow.endedAt() = date + durationSec * 1000

    /**
     * Pure decision logic over call-log rows (oldest first). Consumes reminders for answered
     * callbacks as a side effect on [repo] and returns the single most relevant thing to show.
     */
    suspend fun apply(rows: List<CallRow>, repo: Repo, whyEnabled: Boolean, nameOf: (String) -> String?): Outcome {
        var cleared = 0
        var ask: CallRow? = null
        var covered: CallRow? = null
        for (row in rows) {
            if (!row.isDialable) continue
            when {
                row.isAnsweredIncoming -> {
                    // Only what was pending before this call began; anything added later stays.
                    val due = repo.pendingFor(row.number).filter { it.createdAt < row.date }
                    repo.markDone(due.map { it.id })
                    cleared += due.size
                    if (ask != null && app.recal.data.PhoneNumbers.same(ask.number, row.number)) ask = null
                }
                row.isUnansweredOutgoing -> { ask = row; covered = null }
                row.isAnsweredOutgoing -> {
                    if (ask != null && app.recal.data.PhoneNumbers.same(ask.number, row.number)) ask = null
                    covered = row
                }
            }
        }
        if (!whyEnabled) return if (cleared > 0) Outcome.Cleared(cleared) else Outcome.Nothing
        ask?.let {
            return Outcome.AskWhy(it.number, nameOf(it.number), repo.pendingFor(it.number))
        }
        covered?.let { row ->
            val pending = repo.pendingFor(row.number).filter { it.createdAt < row.date }
            if (pending.isNotEmpty()) return Outcome.AskCovered(row.number, nameOf(row.number), pending)
        }
        return if (cleared > 0) Outcome.Cleared(cleared) else Outcome.Nothing
    }

    /** Wait for the just-ended call to reach the call log, then act on everything new. */
    suspend fun resolve(context: Context, sessionStart: Long): Outcome = lock.withLock {
        val state = CallState(context)
        val settings = Settings(context)
        val from = state.pinWatermark(context)
        if (from < 0) return Outcome.Nothing
        var rows: List<CallRow>
        var waited = 0L
        var lastCount = -1
        var stableSince = 0L
        while (true) {
            rows = CallLogReader.since(context, from)
            val haveCurrent = rows.any { it.endedAt() >= sessionStart - SLACK_MS }
            if (rows.size != lastCount) { lastCount = rows.size; stableSince = waited }
            // Two calls can overlap (call waiting) and their rows arrive in any order, so wait for quiet.
            if ((haveCurrent && waited - stableSince >= STABLE_MS) || waited >= WAIT_MS) break
            delay(POLL_MS)
            waited += POLL_MS
        }
        if (rows.isEmpty()) return Outcome.Nothing
        state.watermark = rows.maxOf { it.id }
        // Rows from earlier, unprocessed sessions still consume reminders, but only the
        // session that just ended may raise a prompt.
        val (stale, fresh) = rows.partition { it.endedAt() < sessionStart - SLACK_MS }
        val repo = Repo.get(context)
        val nameOf = { n: String -> Contacts.nameFor(context, n) }
        val a = apply(stale, repo, whyEnabled = false, nameOf)
        val b = apply(fresh, repo, settings.whyICalled, nameOf)
        if (b is Outcome.Nothing && a is Outcome.Cleared) a
        else if (b is Outcome.Cleared && a is Outcome.Cleared) Outcome.Cleared(a.count + b.count)
        else b
    }
}
