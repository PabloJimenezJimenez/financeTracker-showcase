package com.pablojimenez.financetracker.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.AmountInput
import com.pablojimenez.financetracker.domain.FinanceRepository
import kotlinx.coroutines.launch

/** What the Account form shows. The errors appear only after a Save that failed them. */
data class AccountFormState(
    val name: String = "",
    val type: AccountType = AccountType.Current,
    val startingBalance: String = "",
    val nameError: Boolean = false,
    val startingBalanceError: Boolean = false,
    val saved: Boolean = false,
)

class AccountFormViewModel(private val repository: FinanceRepository) : ViewModel() {
    var state by mutableStateOf(AccountFormState())
        private set

    private var saving = false

    fun onNameChange(name: String) {
        state = state.copy(name = name, nameError = false)
    }

    fun onTypeChange(type: AccountType) {
        state = state.copy(type = type)
    }

    /** Takes [text] only while it can still become an amount; a leading "−" is allowed. */
    fun onStartingBalanceChange(text: String) {
        if (AmountInput.accepts(text, allowNegative = true)) {
            state = state.copy(startingBalance = text, startingBalanceError = false)
        }
    }

    /** Saves the Account when the name is set and the starting balance is blank (0) or valid. */
    fun save() {
        if (saving) return
        val name = state.name.trim()
        val startingBalance =
            if (state.startingBalance.isBlank()) 0L else AmountInput.parseCents(state.startingBalance)
        state = state.copy(nameError = name.isEmpty(), startingBalanceError = startingBalance == null)
        if (name.isEmpty() || startingBalance == null) return
        saving = true
        val account = Account(name = name, type = state.type, startingBalanceCents = startingBalance)
        viewModelScope.launch {
            repository.addAccount(account)
            state = state.copy(saved = true)
        }
    }
}
