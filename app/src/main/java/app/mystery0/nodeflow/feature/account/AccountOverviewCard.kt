package app.mystery0.nodeflow.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.R
import app.mystery0.nodeflow.core.model.AccountOverview
import app.mystery0.nodeflow.core.model.DailyCheckIn

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountOverviewCard(overview: AccountOverview, isCheckingIn: Boolean, onCheckInClick: () -> Unit) {
    val coins = listOfNotNull(
        overview.wealth?.gold?.let { stringResource(R.string.account_gold, "%,d".format(it)) },
        overview.wealth?.silver?.let { stringResource(R.string.account_silver, "%,d".format(it)) },
        overview.wealth?.bronze?.let { stringResource(R.string.account_bronze, "%,d".format(it)) },
    )
    if (coins.isEmpty() && overview.checkIn == null) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (coins.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.account_wealth), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        coins.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
                    }
                }
            }
            overview.checkIn?.let { checkIn ->
                // 可用宽度不足时，按钮整体换行；金额与连续天数也允许随字体缩放自然换行。
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.widthIn(min = 180.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(if (checkIn.checkedIn) R.string.account_checked_in else R.string.account_check_in),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (checkIn.checkedIn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        checkIn.continuousDays?.let { days ->
                            Text(stringResource(R.string.account_check_in_days, "%,d".format(days)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (usesCheckInActionLine(checkIn)) {
                        Button(onClick = onCheckInClick, enabled = !isCheckingIn) {
                            Text(stringResource(if (isCheckingIn) R.string.account_checking_in else R.string.account_check_in_action))
                        }
                    }
                }
            }
        }
    }
}

internal fun usesCheckInActionLine(checkIn: DailyCheckIn): Boolean = checkIn.canCheckIn
