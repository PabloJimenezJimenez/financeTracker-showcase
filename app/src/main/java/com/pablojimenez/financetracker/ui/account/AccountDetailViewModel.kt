package com.pablojimenez.financetracker.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pablojimenez.financetracker.domain.FinanceRepository
import com.pablojimenez.financetracker.domain.accountBalances
import com.pablojimenez.financetracker.ui.components.TransactionListDay
import com.pablojimenez.financetracker.ui.components.transactionListDays
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What an Account's detail screen shows. */
data class AccountDetailState(
    /** False until the Account has been read; until then there is no Delete or Archive action. */
    val loaded: Boolean = false,
    val name: String = "",
    val balanceCents: Long = 0,
    /** The Account's own Transactions, by day, newest first. */
    val days: List<TransactionListDay> = emptyList(),
    /** True once the Account has been deleted or archived, so the screen can close. */
    val closed: Boolean = false,
) {
    /** An Account with Transactions can only be archived; one without can only be deleted. */
    val canDelete: Boolean get() = days.isEmpty()
}

/** The detail of the Account [accountId], which can delete or archive it. */
class AccountDetailViewModel(
    private val repository: FinanceRepository,
    private val accountId: Long,
) : ViewModel() {
    private val closed = MutableStateFlow(false)

    val state: StateFlow<AccountDetailState> = combine(
        repository.accounts,
        repository.categories,
        repository.transactions,
        repository.merchants,
        closed,
    ) { accounts, categories, transactions, merchants, closed ->
        val balance = accountBalances(accounts.filter { it.id == accountId }, transactions).firstOrNull()
        AccountDetailState(
            loaded = balance != null,
            name = balance?.account?.name.orEmpty(),
            balanceCents = balance?.balanceCents ?: 0,
            days = transactionListDays(transactions, accounts, categories, merchants, onlyAccountId = accountId),
            closed = closed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AccountDetailState())

    private var busy = false

    /** Deletes the Account; the repository refuses when it has Transactions, and it stays open. */
    fun delete() = act { repository.deleteAccount(accountId) }

    /** Archives the Account: it leaves Home and the pickers, its Transactions stay. */
    fun archive() = act {
        repository.archiveAccount(accountId)
        true
    }

    private fun act(write: suspend () -> Boolean) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            closed.value = write()
            busy = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
