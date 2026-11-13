package app.recal.ui

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.concurrent.TimeUnit

/** True when the user asked the system to remove animations. */
@Composable
fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Press feedback that starts on touch-down, not on release, and springs back from wherever
 * it currently is. The click commits on touch-up inside the element.
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    scaleTo: Float = 0.96f,
    onClick: () -> Unit,
): Modifier = composed {
        var pressed by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(if (pressed) scaleTo else 1f, Motion.quick(), label = "press")
        val alpha by animateFloatAsState(if (pressed) 0.82f else 1f, Motion.quick(), label = "pressAlpha")
        this
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = if (enabled) alpha else 0.4f }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (enabled) onClick { onClick(); true }
            }
            .pointerInput(enabled, onClick) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val up = waitForUpOrCancellation()
                    pressed = false
                    if (up != null) onClick()
                }
            }
    }

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
    height: Dp = 50.dp,
    style: TextStyle = Type.headline,
    container: Color = if (filled) colors.accent else colors.fill,
    content: Color = if (filled) colors.onAccent else colors.label,
    shape: Shape = RoundedCornerShape(percent = 50),
    padding: PaddingValues = PaddingValues(horizontal = 22.dp),
) {
    Box(
        modifier
            .pressable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = height)
            .clip(shape)
            .background(container)
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Initial-in-a-circle avatar. Tint is derived from the name so a person keeps their colour. */
@Composable
fun Avatar(label: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val palette = listOf(
        Color(0xFFF2600C), Color(0xFF2F7CF6), Color(0xFF7A5AF8), Color(0xFF12A594),
        Color(0xFFE5484D), Color(0xFFD6409F), Color(0xFF3E9B4F), Color(0xFFB8860B),
    )
    val tint = palette[(label.hashCode() and 0x7fffffff) % palette.size]
    val initial = label.firstOrNull { it.isLetter() }?.uppercase()
    val ink = if (colors.isDark) tint.copy(alpha = 1f).lighten() else tint
    Box(
        modifier.size(size).clip(CircleShape).background(tint.copy(alpha = if (colors.isDark) 0.28f else 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            Text(initial, color = ink, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold)
        } else {
            // A bare number has no initial; a person glyph reads better than "#".
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Rounded.Person, null, tint = ink, modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

private fun Color.lighten(): Color = Color(
    red = red + (1f - red) * 0.35f, green = green + (1f - green) * 0.35f, blue = blue + (1f - blue) * 0.35f, alpha = 1f,
)

/** "just now", "12 min ago", "3 h ago", "Yesterday", "4 d ago". */
fun relativeTime(then: Long, now: Long = System.currentTimeMillis()): String {
    val d = (now - then).coerceAtLeast(0)
    val min = TimeUnit.MILLISECONDS.toMinutes(d)
    val h = TimeUnit.MILLISECONDS.toHours(d)
    val days = TimeUnit.MILLISECONDS.toDays(d)
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        h < 24 -> "$h h ago"
        days == 1L -> "yesterday"
        days < 30 -> "$days d ago"
        else -> "${days / 30} mo ago"
    }
}


@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = Type.caption.copy(letterSpacing = 0.6.sp), color = colors.secondary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
