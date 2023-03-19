package unlikely.alice.ninepm

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import unlikely.alice.ninepm.ui.theme.HandwrittenFontFamily


fun ByteArray.base64(offset: Int = 0, len: Int = 3): String {
    return Base64.encodeToString(this, offset, len, Base64.NO_WRAP)
}

fun String.base64(): ByteArray {
    return Base64.decode(this, Base64.NO_WRAP)
}

val Context.app: AndroidApp get() = this.applicationContext as AndroidApp

fun Context.findActivity(): MainActivity = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> throw RuntimeException("did not find MainActivity from this context")
}

@Composable
fun Ltr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        content()
    }
}

@Composable
fun Rtl(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        content()
    }
}

@Stable
fun Modifier.mirror(): Modifier {
    return this.scale(scaleX = -1f, scaleY = 1f)
}

inline fun Modifier.clickableNoRipple(crossinline onClick: () -> Unit): Modifier = composed {
    clickable(indication = null,
        interactionSource = remember { MutableInteractionSource() }) {
        onClick()
    }
}

fun openWeb(context: Context, url: String) {
    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(browserIntent)
}

@Composable
fun ArrowHelp(text: String, modifier: Modifier = Modifier, upward: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .alpha(0.8f)
    ) {
        if (upward) {
            Icon(imageVector = Icons.Rounded.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.size(4.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        if (!upward) {
            Spacer(modifier = Modifier.size(4.dp))
            Icon(imageVector = Icons.Rounded.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun HandwrittenMessage(messages: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    var messageIndex by remember { mutableStateOf(0) }
    var visible by remember { mutableStateOf(true) }
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f)

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(messages[messageIndex].second * 1000L)
            visible = false
            delay(500)
            messageIndex = (messageIndex + 1) % messages.size
            visible = true
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .alpha(alpha)
    ) {
        Text(
            text = messages[messageIndex].first,
            fontFamily = HandwrittenFontFamily,
            fontSize = 26.sp,
            letterSpacing = 2.6.sp,
            lineHeight = 30.sp,
            textAlign = TextAlign.Center
        )
    }
}