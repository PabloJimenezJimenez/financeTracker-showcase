package com.pablojimenez.financetracker.ui.transaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AmountInput
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.Direction
import com.pablojimenez.financetracker.domain.FinanceRepository
import com.pablojimenez.financetracker.domain.Merchant
import com.pablojimenez.financetracker.domain.Transaction
import com.pablojimenez.financetracker.domain.TransactionSource
import com.pablojimenez.financetracker.domain.matching
import com.pablojimenez.financetracker.domain.pickable
import com.pablojimenez.financetracker.ui.categories.CategoryEditor
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the Transaction form shows. [accountId] null means the first Account. The errors appear
 * only after a Save that failed them. While [editing] it shows a saved Transaction.
 */
data class TransactionFormState(
    val date: LocalDate,
    val editing: Boolean = false,
    val direction: Direction = Direction.Expense,
    val amount: String = "",
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val time: LocalTime? = null,
    /** The Merchant field's text: a Merchant's name, or a new name to save as a Merchant. */
    val merchant: String = "",
    /** The Merchant picked (or the edited Transaction's), until the field's text changes. */
    val merchantId: Long? = null,
    /** Whether Merchants matching [merchant] are offered: after typing, until one is picked or dismissed. */
    val suggestingMerchants: Boolean = false,
    val note: String = "",
    val amountError: Boolean = false,
    val categoryError: Boolean = false,
    val saved: Boolean = false,
    /** True once the edited Transaction has been deleted. */
    val deleted: Boolean = false,
)

/** A Merchant offered under the Merchant field, with the name of its default Category, if any. */
data class MerchantSuggestion(val id: Long, val name: String, val category: String?)

/**
 * What the Merchant field offers in [state]: the [merchants] matching its text (see [matching]),
 * while [TransactionFormState.suggestingMerchants], with Category names from [categories].
 */
fun merchantSuggestions(
    state: TransactionFormState,
    merchants: List<Merchant>,
    categories: List<Category>,
): List<MerchantSuggestion> {
    if (!state.suggestingMerchants) return emptyList()
    val categoryNames = categories.associate { it.id to it.name }
    return merchants.matching(state.merchant).map { merchant ->
        MerchantSuggestion(merchant.id, merchant.name, merchant.defaultCategoryId?.let(categoryNames::get))
    }
}

/**
 * Adds a Transaction, or with [transactionId] edits that saved one. [decimalSeparator] is the
 * one the owner types, used to show a saved amount.
 */
class TransactionFormViewModel(
    private val repository: FinanceRepository,
    clock: Clock,
    private val transactionId: Long? = null,
    private val decimalSeparator: Char = '.',
) : ViewModel(), TransactionFormActions {
    /** The Transaction being edited, once it has been read. */
    private val original = MutableStateFlow<Transaction?>(null)

    /** The Accounts the picker offers: no archived one, unless it is the edited Transaction's. */
    val accounts: StateFlow<List<Account>> =
        combine(repository.accounts, original) { accounts, original ->
            accounts.pickable(keep = listOfNotNull(original?.accountId))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val categories: StateFlow<List<Category>> =
        repository.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val merchants: StateFlow<List<Merchant>> =
        repository.merchants.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var state by mutableStateOf(TransactionFormState(date = LocalDate.now(clock), editing = transactionId != null))
        private set

    /** The picker's "New category" dialog: a Category it saves becomes the form's Category. */
    val categoryEditor =
        CategoryEditor(repository, viewModelScope, categories = { categories.value }, onSaved = ::onCategoryChange)

    private var saving = false

    init {
        // A Merchant deleted while the form is open (on the Merchants screen) leaves the field.
        viewModelScope.launch {
            repository.merchants.collect { merchants -> clearMerchantUnlessIn(merchants) }
        }
        if (transactionId != null) {
            viewModelScope.launch {
                val saved = repository.transactions.first().firstOrNull { it.id == transactionId }
                    ?: return@launch
                val merchant = saved.merchantId?.let { id -> repository.merchants.first().firstOrNull { it.id == id } }
                original.value = saved
                state = state.copy(
                    direction = if (saved.amountCents < 0) Direction.Expense else Direction.Income,
                    amount = AmountInput.text(abs(saved.amountCents), decimalSeparator),
                    accountId = saved.accountId,
                    categoryId = saved.categoryId,
                    date = saved.date,
                    time = saved.time,
                    merchant = merchant?.name.orEmpty(),
                    merchantId = merchant?.id,
                    note = saved.note.orEmpty(),
                )
            }
        }
    }

    /**
     * Empties the Merchant field when the Merchant it holds (picked, or the edited Transaction's)
     * is not among [merchants]. Typed text that names no Merchant stays, and so does the Category.
     */
    private fun clearMerchantUnlessIn(merchants: List<Merchant>) {
        val id = state.merchantId ?: return
        if (merchants.none { it.id == id }) {
            state = state.copy(merchant = "", merchantId = null, suggestingMerchants = false)
        }
    }

    override fun onDirectionChange(direction: Direction) {
        state = state.copy(direction = direction)
    }

    /** Takes [text] only while it can still become a positive amount. */
    override fun onAmountChange(text: String) {
        if (AmountInput.accepts(text)) state = state.copy(amount = text, amountError = false)
    }

    override fun onAccountChange(accountId: Long) {
        state = state.copy(accountId = accountId)
    }

    override fun onCategoryChange(categoryId: Long) {
        state = state.copy(categoryId = categoryId, categoryError = false)
    }

    override fun onNewCategory() {
        categoryEditor.add()
    }

    override fun onDateChange(date: LocalDate) {
        state = state.copy(date = date)
    }

    override fun onTimeChange(time: LocalTime?) {
        state = state.copy(time = time)
    }

    override fun onMerchantChange(merchant: String) {
        state = state.copy(merchant = merchant, merchantId = null, suggestingMerchants = true)
    }

    /**
     * Picks the Merchant [merchantId]: its name fills the field and its default Category, when it
     * has one, replaces the Category picked so far.
     */
    override fun onMerchantPick(merchantId: Long) {
        val merchant = merchants.value.firstOrNull { it.id == merchantId } ?: return
        val categoryId = merchant.defaultCategoryId ?: state.categoryId
        state = state.copy(
            merchant = merchant.name,
            merchantId = merchant.id,
            suggestingMerchants = false,
            categoryId = categoryId,
            categoryError = state.categoryError && categoryId == null,
        )
    }

    override fun onMerchantSuggestionsDismiss() {
        state = state.copy(suggestingMerchants = false)
    }

    override fun onNoteChange(note: String) {
        state = state.copy(note = note)
    }

    /**
     * Saves a manual Transaction when the amount is above zero and a Category is picked;
     * otherwise shows the errors and saves nothing. The direction gives the amount its sign.
     * An edited Transaction keeps its id. It links the picked Merchant; a typed name links the
     * Merchant of that name, which is created with the picked Category as its default when there
     * is none. An existing Merchant is never changed. An empty Merchant field links none.
     */
    override fun onSave() {
        if (saving) return
        val original = original.value
        if (state.editing && original == null) return
        val form = state
        val cents = AmountInput.parseCents(form.amount)?.takeIf { it > 0 }
        val categoryId = form.categoryId
        val accountId = form.accountId ?: accounts.value.firstOrNull()?.id
        state = form.copy(amountError = cents == null, categoryError = categoryId == null)
        if (cents == null || categoryId == null || accountId == null) return
        saving = true
        val draft = Transaction(
            accountId = accountId,
            categoryId = categoryId,
            amountCents = form.direction.signed(cents),
            date = form.date,
            time = form.time,
            note = form.note.trim().ifEmpty { null },
            source = TransactionSource.Manual,
        )
        val merchantName = form.merchant.trim()
        viewModelScope.launch {
            val merchantId = form.merchantId ?: merchantName.takeIf { it.isNotEmpty() }?.let { name ->
                repository.addMerchant(Merchant(name = name, defaultCategoryId = categoryId))
            }
            val transaction = draft.copy(merchantId = merchantId)
            if (original == null) {
                repository.addTransaction(transaction)
            } else {
                repository.updateTransaction(transaction.copy(id = original.id))
            }
            state = state.copy(saved = true)
        }
    }

    /** Deletes the edited Transaction. */
    override fun onDelete() {
        val original = original.value ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            repository.deleteTransaction(original.id)
            state = state.copy(deleted = true)
        }
    }
}
