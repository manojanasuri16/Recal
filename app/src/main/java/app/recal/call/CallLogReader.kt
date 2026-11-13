package app.recal.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

data class CallRow(val number: String, val type: Int, val date: Long, val durationSec: Long, val id: Long = 0) {
    val isOutgoing get() = type == CallLog.Calls.OUTGOING_TYPE
    /** Outgoing call that never connected: no answer, declined, busy or cancelled. */
    val isUnansweredOutgoing get() = isOutgoing && durationSec == 0L
    val isAnsweredOutgoing get() = isOutgoing && durationSec > 0L
    /** Incoming call that was picked up and actually lasted. Missed/rejected rows have other types. */
    val isAnsweredIncoming get() = type == CallLog.Calls.INCOMING_TYPE && durationSec > 0L

    /** Service codes and hidden numbers are not people; never prompt for them. */
    val isDialable get() = number.isNotBlank() && number.none { it == '*' || it == '#' } &&
        number.count { it.isDigit() } >= 3
}

object CallLogReader {
    /** Newer providers reject "LIMIT n" inside the sort string; the query-args bundle is the supported way. */
    private fun bundle(selection: String?, args: Array<String>?, order: String, limit: Int) = android.os.Bundle().apply {
        selection?.let { putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, it) }
        args?.let { putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, it) }
        putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, order)
        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
    }

    fun canRead(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    /** Highest call-log row id, or -1 when it cannot be read. */
    fun latestId(context: Context): Long {
        if (!canRead(context)) return -1
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID),
                bundle(null, null, "${CallLog.Calls._ID} DESC", 1), null,
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    /**
     * Rows added after [afterId], oldest first. Progress is tracked by row id (insertion order), not
     * by call start time: a row for a call that began earlier can be written after a later one.
     * Only the newest [limit] rows are returned so a long backlog can never starve the current call.
     */
    fun since(context: Context, afterId: Long, limit: Int = 200): List<CallRow> {
        if (!canRead(context) || afterId < 0) return emptyList()
        val out = ArrayList<CallRow>()
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls._ID),
                bundle("${CallLog.Calls._ID} > ?", arrayOf(afterId.toString()), "${CallLog.Calls._ID} DESC", limit), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    out += CallRow(c.getString(0).orEmpty(), c.getInt(1), c.getLong(2), c.getLong(3), c.getLong(4))
                }
            }
        } catch (_: Exception) {
            // Some OEM providers reject odd selections; treat as "nothing new".
        }
        out.reverse()
        return out
    }

    /** Most recent outgoing calls that did not connect, newest first, one per number. */
    fun recentUnanswered(context: Context, max: Int = 6): List<CallRow> {
        if (!canRead(context)) return emptyList()
        val out = ArrayList<CallRow>()
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
                "${CallLog.Calls.TYPE} = ${CallLog.Calls.OUTGOING_TYPE} AND ${CallLog.Calls.DURATION} = 0", null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                var scanned = 0
                while (c.moveToNext() && out.size < max && scanned++ < 60) {
                    val row = CallRow(c.getString(0).orEmpty(), c.getInt(1), c.getLong(2), c.getLong(3))
                    if (row.isDialable && out.none { app.recal.data.PhoneNumbers.same(it.number, row.number) }) out += row
                }
            }
        } catch (_: Exception) {
        }
        return out
    }
}
