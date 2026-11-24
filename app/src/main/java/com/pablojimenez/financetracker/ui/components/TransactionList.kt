package com.pablojimenez.financetracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.Merchant
import com.pablojimenez.financetracker.domain.Transaction
import com.pablojimenez.financetracker.domain.formatCents
import com.pablojimenez.financetracker.domain.groupedByDay
import com.pablojimenez.financetracker.ui.theme.FinanceMotion
import com.pablojimenez.financetracker.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The rows of one day in a list of Transactions (Home's, or an Account's). */
data class TransactionListDay(val date: LocalDate, val rows: List<TransactionListRow>)

/** One Transaction in a list of Transactions. */
data class TransactionListRow(
    val id: Long,
    /** The Category's name, or null when the Transaction has none. */
    val category: String?,
    /** The Merchant's name, or null when the Transaction has none. */
    val merchant: String?,
    val amountCents: Long,
    /** Set when the Transaction is a Transfer leg, which has no Category. */
    val transfer: TransferSide? = null,
)

/** Which Transfer a leg belongs to and the Account at its other end. */
data class TransferSide(
    val transferId: Long,
    /** The other leg's Account: where the money went (out leg) or came from (in leg). */
    val otherAccount: String,
    /** True on the out leg. */
    val outgoing: Boolean,
)

/**
 * [transactions] as rows by day, newest first (see [groupedByDay]), keeping only those of the
 * Account [onlyAccountId] when it is given. Category, Account and Merchant names come from
 * [categories], [accounts] and [merchants]; a Transfer leg finds its other leg among [transactions].
 */
fun transactionListDays(
    transactions: List<Transaction>,
    accounts: List<Account>,
    categories: List<Category>,
    merchants: List<Merchant>,
    onlyAccountId: Long? = null,
): List<TransactionListDay> {
    val categoryNames = categories.associate { it.id to it.name }
    val merchantNames = merchants.associate { it.id to it.name }
    val accountNames = accounts.associate { it.id to it.name }
    val legsByTransfer = transactions.filter { it.transferId != null }.groupBy { it.transferId }
    fun rowOf(transaction: Transaction): TransactionListRow {
        val transfer = transaction.transferId?.let { transferId ->
            val other = legsByTransfer[transferId].orEmpty().firstOrNull { it.id != transaction.id }
            TransferSide(
                transferId = transferId,
                otherAccount = other?.accountId?.let(accountNames::get).orEmpty(),
                outgoing = transaction.amountCents < 0,
            )
        }
        return TransactionListRow(
            id = transaction.id,
            category = transaction.categoryId?.let(categoryNames::get),
            merchant = transaction.merchantId?.let(merchantNames::get),
            amountCents = transaction.amountCents,
            transfer = transfer,
        )
    }
    val shown = if (onlyAccountId == null) transactions else transactions.filter { it.accountId == onlyAccountId }
    return shown.groupedByDay().map { day -> TransactionListDay(day.date, day.transactions.map(::rowOf)) }
}

/**
 * The [days] as a heading per day (tagged `day-<date>`) over its rows (tagged `transaction-<id>`),
 * or "No transactions yet" when there are none. Tapping a row calls [onOpen].
 */
fun LazyListScope.transactionList(
    days: List<TransactionListDay>,
    reduceMotion: Boolean,
    onOpen: (TransactionListRow) -> Unit,
) {
    if (days.isEmpty()) {
        item(key = "empty") {
            Text(
                text = stringResource(R.string.empty_transactions),
                modifier = Modifier
                    .rowMotion(this, reduceMotion)
                    .padding(top = Spacing.Large),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    days.forEach { day ->
        item(key = "day-${day.date}") {
            DayHeading(
                date = day.date,
                modifier = Modifier
                    .rowMotion(this, reduceMotion)
                    .testTag("day-${day.date}"),
            )
        }
        items(day.rows, key = { "transaction-${it.id}" }) { row ->
            TransactionRow(
                row = row,
                onClick = { onOpen(row) },
                modifier = Modifier
                    .rowMotion(this, reduceMotion)
                    .testTag("transaction-${row.id}"),
            )
        }
    }
}

/**
 * A row fades in when it is added, collapses and fades when it is deleted, and slides when
 * others come and go (docs/design.md §9); with reduce motion it only fades.
 */
private fun Modifier.rowMotion(scope: LazyItemScope, reduceMotion: Boolean): Modifier = with(scope) {
    animateItem(
        fadeInSpec = FinanceMotion.effects(),
        placementSpec = if (reduceMotion) null else FinanceMotion.spatialDefault(),
        fadeOutSpec = FinanceMotion.effects(),
    )
}

/** "Martes, 10 de marzo de 2026": the full date in the current locale, in sentence case. */
@Composable
private fun DayHeading(date: LocalDate, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    val text = remember(date, locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date)
            .replaceFirstChar { it.titlecase(locale) }
    }
    Text(
        text = text,
        modifier = modifier
            .padding(top = Spacing.XLarge, bottom = Spacing.XSmall)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The Category (and Merchant when set) with the signed amount; the sign is never a colour. A
 * Transfer leg reads "Transfer" over the Account at its other end. The whole row is one touch
 * target that opens the Transaction.
 */
@Composable
private fun TransactionRow(row: TransactionListRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.MinTouchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.Small)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        val transfer = row.transfer
        val headline = when {
            transfer != null -> stringResource(R.string.transfer)
            else -> row.category ?: stringResource(R.string.uncategorised)
        }
        val detail = when {
            transfer == null -> row.merchant
            transfer.outgoing -> stringResource(R.string.transfer_to, transfer.otherAccount)
            else -> stringResource(R.string.transfer_from, transfer.otherAccount)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = formatCents(row.amountCents, locale, explicitSign = true),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
