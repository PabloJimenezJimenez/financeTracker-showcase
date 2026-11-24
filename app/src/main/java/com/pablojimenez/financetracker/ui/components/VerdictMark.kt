package com.pablojimenez.financetracker.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.MetricResult
import com.pablojimenez.financetracker.domain.Verdict
import com.pablojimenez.financetracker.ui.theme.Spacing

/** Which Verdict icon a KPI shows, as a drawable resource; tests read it from the KPI. */
internal val VerdictIconKey = SemanticsPropertyKey<Int>("VerdictIcon")
private var SemanticsPropertyReceiver.verdictIcon by VerdictIconKey

/** What a KPI's Verdict row shows: an icon and a word. */
internal data class VerdictMark(@param:DrawableRes val icon: Int, @param:StringRes val word: Int)

/**
 * The icon and word for a KPI whose current value is [value] and whose Verdict is [verdict]
 * (brief kpi-dashboard, Decision 4), or null when it has none and shows its value alone. No value
 * wins over any Verdict.
 */
internal fun verdictMark(value: MetricResult, verdict: Verdict?): VerdictMark? = when {
    value is MetricResult.NoValue -> VerdictMark(R.drawable.ic_remove, R.string.kpi_no_value)
    verdict is Verdict.OnPace -> onTrackMark(verdict.onTrack)
    verdict is Verdict.AgainstTarget -> onTrackMark(verdict.onTrack)
    // The arrow is which way the value moved; the word is whether that was good.
    verdict is Verdict.AgainstPrevious -> VerdictMark(
        icon = if (verdict.rose) R.drawable.ic_trending_up else R.drawable.ic_trending_down,
        word = if (verdict.better) R.string.kpi_better else R.string.kpi_worse,
    )
    else -> null
}

private fun onTrackMark(onTrack: Boolean) = if (onTrack) {
    VerdictMark(R.drawable.ic_check_circle, R.string.kpi_on_track)
} else {
    VerdictMark(R.drawable.ic_error, R.string.kpi_off_track)
}

/** The Verdict as an 18 dp icon and a [word], both in `onSurfaceVariant`. */
@Composable
internal fun VerdictRow(@DrawableRes icon: Int, word: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.XSmall),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .size(VerdictIconSize)
                .semantics { verdictIcon = icon },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = word,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val VerdictIconSize = 18.dp
