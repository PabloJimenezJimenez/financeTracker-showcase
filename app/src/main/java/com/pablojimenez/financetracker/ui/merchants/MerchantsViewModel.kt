package com.pablojimenez.financetracker.ui.merchants

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.FinanceRepository
import com.pablojimenez.financetracker.domain.Merchant
import com.pablojimenez.financetracker.domain.MerchantNameError
import com.pablojimenez.financetracker.domain.foldMerchantText
import com.pablojimenez.financetracker.domain.merchantKeywords
import com.pablojimenez.financetracker.domain.merchantNameError
import com.pablojimenez.financetracker.ui.categories.CategoryEditor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A Merchant as its row shows it: [category] is the name of its default Category, or null without one. */
data class MerchantListItem(val merchant: Merchant, val category: String?)

/**
 * [merchants] as the Merchants screen lists them: by name ignoring case and accents ("ábaco"
 * before "Ahorramas", "Bar Pepe" before "BP"), each with its default Category's name from
 * [categories].
 */
fun merchantList(merchants: List<Merchant>, categories: List<Category>): List<MerchantListItem> {
    val categoryNames = categories.associate { it.id to it.name }
    return merchants
        .sortedWith(compareBy<Merchant> { foldMerchantText(it.name) }.thenBy { it.id })
        .map { MerchantListItem(it, it.defaultCategoryId?.let(categoryNames::get)) }
}

/**
 * What the Merchant dialog shows for the saved [merchant]: the [name], the [keywords] as typed
 * (comma-separated) and the [defaultCategoryId] so far. [error] appears after a Save that failed it.
 */
data class MerchantDialogState(
    val merchant: Merchant,
    val name: String = merchant.name,
    val keywords: String = merchant.keywords.joinToString(", "),
    val defaultCategoryId: Long? = merchant.defaultCategoryId,
    val error: MerchantNameError? = null,
)

/** Deleting [merchant], which [transactionCount] Transactions use: the question before it goes. */
data class MerchantDeletion(val merchant: Merchant, val transactionCount: Int)

/** What the owner can do on the Merchants screen and in its dialog; each call maps to [MerchantsViewModel]. */
interface MerchantsActions {
    /** Opens the dialog for [merchant]. */
    fun onOpen(merchant: Merchant)

    fun onNameChange(name: String)
    fun onKeywordsChange(keywords: String)
    fun onDefaultCategoryChange(categoryId: Long)

    /** Opens the "New category" dialog; the Category it saves becomes the default. */
    fun onNewCategory()

    /**
     * Saves the dialog's Merchant and closes the dialog; when the name is blank or another
     * Merchant has it, shows the error instead and saves nothing.
     */
    fun onSave()

    fun onDismiss()

    /** Closes the dialog and asks whether to delete its Merchant: see [MerchantDeletion]. */
    fun onDelete()

    fun onConfirmDelete()
    fun onDeleteDismiss()
}

/** The Merchants screen: every Merchant in [repository], and the dialog that edits one. */
class MerchantsViewModel(private val repository: FinanceRepository) : ViewModel(), MerchantsActions {
    val categories: StateFlow<List<Category>> =
        repository.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Every Merchant, in the screen's order (see [merchantList]); null until they have been read. */
    val merchants: StateFlow<List<MerchantListItem>?> =
        combine(repository.merchants, repository.categories, ::merchantList)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The open Merchant dialog, or null. */
    var dialog by mutableStateOf<MerchantDialogState?>(null)
        private set

    /** The delete question being asked, or null. */
    var deletion by mutableStateOf<MerchantDeletion?>(null)
        private set

    /** The default Category picker's "New category" dialog. */
    val categoryEditor =
        CategoryEditor(repository, viewModelScope, categories = { categories.value }, onSaved = ::onDefaultCategoryChange)

    override fun onOpen(merchant: Merchant) {
        dialog = MerchantDialogState(merchant)
    }

    override fun onNameChange(name: String) {
        dialog = dialog?.copy(name = name, error = null)
    }

    override fun onKeywordsChange(keywords: String) {
        dialog = dialog?.copy(keywords = keywords)
    }

    override fun onDefaultCategoryChange(categoryId: Long) {
        dialog = dialog?.copy(defaultCategoryId = categoryId)
    }

    override fun onNewCategory() {
        categoryEditor.add()
    }

    override fun onSave() {
        val open = dialog ?: return
        val error = merchants.value.orEmpty().map { it.merchant }.merchantNameError(open.name, renaming = open.merchant.id)
        if (error != null) {
            dialog = open.copy(error = error)
            return
        }
        dialog = null
        val merchant = open.merchant.copy(
            name = open.name.trim(),
            keywords = merchantKeywords(open.keywords),
            defaultCategoryId = open.defaultCategoryId,
        )
        viewModelScope.launch { repository.updateMerchant(merchant) }
    }

    override fun onDismiss() {
        dialog = null
    }

    override fun onDelete() {
        val merchant = dialog?.merchant ?: return
        dialog = null
        viewModelScope.launch {
            val count = repository.transactions.first().count { it.merchantId == merchant.id }
            deletion = MerchantDeletion(merchant, count)
        }
    }

    /** Deletes the Merchant; its Transactions stay, without a Merchant. */
    override fun onConfirmDelete() {
        val confirmed = deletion ?: return
        deletion = null
        viewModelScope.launch { repository.deleteMerchant(confirmed.merchant.id) }
    }

    override fun onDeleteDismiss() {
        deletion = null
    }
}
