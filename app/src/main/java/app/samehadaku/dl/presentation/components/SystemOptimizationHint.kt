package app.samehadaku.dl.presentation.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.samehadaku.dl.core.BackgroundAccess
import app.samehadaku.dl.presentation.theme.TextMuted
import app.samehadaku.dl.presentation.theme.TextPrimary

private val HintAmber = Color(0xFFF59E0B)
private val HintBlue = Color(0xFF2563EB)

/**
 * Contextual nudge shown only where background work is actually used, for
 * example when Background play is switched on. It disappears as soon as the app
 * is exempt from battery optimization, so it never nags on every launch and is
 * not shown on first start.
 */
@Composable
fun SystemOptimizationHint(
    title: String,
    reason: String,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val access = remember(context) { BackgroundAccess(context) }
    var unrestricted by remember {
        mutableStateOf(access.status().batteryOptimizationIgnored)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        unrestricted = access.status().batteryOptimizationIgnored
    }

    if (unrestricted) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HintAmber.copy(alpha = 0.09f))
            .border(1.dp, HintAmber.copy(alpha = 0.32f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.BatterySaver,
                contentDescription = null,
                tint = HintAmber,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = reason,
            color = TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(HintBlue)
                .clickable {
                    val started = runCatching {
                        context.startActivity(
                            Intent(access.appDetailsIntent())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.isSuccess
                    onMessage(
                        if (started) {
                            "Buka Info aplikasi → Baterai → Tidak dibatasi."
                        } else {
                            "Info aplikasi tidak bisa dibuka di perangkat ini."
                        },
                    )
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Atur →",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
