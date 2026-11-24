package com.pablojimenez.financetracker.ui.transfer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AmountInput
import com.pablojimenez.financetracker.domain.FinanceRepository
import com.pablojimenez.financetracker.domain.Transfer
import com.pablojimenez.financetracker.domain.pickable
import com.pablojimenez.financetracker.domain.transferOf
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the Transfer form shows. A null [fromAccountId] means the first Account offered and a null
 * [toAccountId] the second (see [from] and [to]). The errors appear only after a Save that failed
 * them. While [editing] it shows a saved Transfer.
 */
data class TransferFormState(
    val date: LocalDate,
    val editing: Boolean = false,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val amount: String = "",
    val note: String = "",
    val amountError: Boolean = false,
    val sameAccountError: Boolean = false,
    val saved: Boolean = false,
    /** True once the edited Transfer has been deleted. */
    val deleted: Boolean = false,
) {
    /** The from-Account among [accounts]: the one picked, or else the first. */
    fun from(accounts: List<Account>): Long? = fromAccountId ?: accounts.getOrNull(0)?.id

    /** The to-Account among [accounts]: the one picked, or else the second. */
    fun to(accounts: List<Account>): Long? = toAccountId ?: accounts.getOrNull(1)?.id
}

/** What the owner can do on the Transfer form. */
interface TransferFormActions {
    fun onFromChange(accountId: Long)
    fun onToChange(accountId: Long)
    fun onAmountChange(text: String)
    fun onDateChange(date: LocalDate)
    fun onNoteChange(note: String)
    fun onSave()
    fun onDelete()
}

/**
 * Adds a Transfer, or with [transferId] edits that saved one. [decimalSeparator] is the one the
 * owner types, used to show a saved amount.
 */
class TransferFormViewModel(
    private val repository: FinanceRepository,
    clock: Clock,
    private val transferId: Long? = null,
    private val decimalSeparator: Char = '.',
) : ViewModel(), TransferFormActions {
    /** The Transfer being edited and the id of one of its legs, once they have been read. */
    private val original = MutableStateFlow<Pair<Transfer, Long>?>(null)

    /** The Accounts the pickers offer: no archived one, unless the edited Transfer uses it. */
    val accounts: StateFlow<List<Account>> =
        combine(repository.accounts, original) { accounts, original ->
            val transfer = original?.first
            accounts.pickable(keep = listOfNotNull(transfer?.fromAccountId, transfer?.toAccountId))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var state by mutableStateOf(TransferFormState(date = LocalDate.now(clock), editing = transferId != null))
        private set

    private var saving = false

    init {
        if (transferId != null) {
            viewModelScope.launch {
                val transactions = repository.transactions.first()
                val transfer = transferOf(transferId, transactions) ?: return@launch
                original.value = transfer to transactions.first { it.transferId == transferId }.id
                state = state.copy(
                    fromAccountId = transfer.fromAccountId,
                    toAccountId = transfer.toAccountId,
                    amount = AmountInput.text(transfer.amountCents, decimalSeparator),
                    date = transfer.date,
                    note = transfer.note.orEmpty(),
                )
            }
        }
    }

    override fun onFromChange(accountId: Long) {
        state = state.copy(fromAccountId = accountId, sameAccountError = false)
    }

    override fun onToChange(accountId: Long) {
        state = state.copy(toAccountId = accountId, sameAccountError = false)
    }

    /** Takes [text] only while it can still become a positive amount. */
    override fun onAmountChange(text: String) {
        if (AmountInput.accepts(text)) state = state.copy(amount = text, amountError = false)
    }

    override fun onDateChange(date: LocalDate) {
        state = state.copy(date = date)
    }

    override fun onNoteChange(note: String) {
        state = state.copy(note = note)
    }

    /**
     * Saves the Transfer when the amount is above zero and the two Accounts differ; otherwise
     * shows the errors and saves nothing. An edited Transfer keeps its id and its legs' ids.
     */
    override fun onSave() {
        if (saving) return
        val original = original.value
        if (state.editing && original == null) return
        val form = state
        val cents = AmountInput.parseCents(form.amount)?.takeIf { it > 0 }
        val from = form.from(accounts.value)
        val to = form.to(accounts.value)
        val sameAccount = from == null || to == null || from == to
        state = form.copy(amountError = cents == null, sameAccountError = sameAccount)
        if (cents == null || from == null || to == null || sameAccount) return
        saving = true
        val transfer = Transfer(
            id = original?.first?.id ?: 0,
            fromAccountId = from,
            toAccountId = to,
            amountCents = cents,
            date = form.date,
            note = form.note.trim().ifEmpty { null },
        )
        viewModelScope.launch {
            repository.saveTransfer(transfer)
            state = state.copy(saved = true)
        }
    }

    /** Deletes the edited Transfer, both legs. */
    override fun onDelete() {
        val legId = original.value?.second ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            repository.deleteTransaction(legId)
            state = state.copy(deleted = true)
        }
    }
}
