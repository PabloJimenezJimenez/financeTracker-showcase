package com.pablojimenez.financetracker.ui.categories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.CategoryKind
import com.pablojimenez.financetracker.domain.CategoryNameError
import com.pablojimenez.financetracker.domain.CategoryPlace
import com.pablojimenez.financetracker.domain.FinanceRepository
import com.pablojimenez.financetracker.domain.nameError
import com.pablojimenez.financetracker.domain.place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the Category dialog shows: the [name] and [place] of a new Category, or the new [name] of
 * the saved Category it is [editing]. [error] appears after a Save that failed it.
 */
data class CategoryDialogState(
    /** The saved Category being renamed; null when adding one. */
    val editing: Category? = null,
    val name: String = "",
    val place: CategoryPlace = CategoryPlace(CategoryKind.Spending),
    val error: CategoryNameError? = null,
)

/** What the owner can do in the Category dialog. */
interface CategoryDialogActions {
    fun onNameChange(name: String)
    fun onPlaceChange(place: CategoryPlace)

    /**
     * Saves the dialog's Category and closes it, returning true; when the name is blank or a
     * sibling has it, shows the error instead, saves nothing and returns false.
     */
    fun onSave(): Boolean
    fun onDismiss()
}

/**
 * The Category dialog behind the Categories screen and the Category picker's "New category": it
 * holds the open dialog's [state], checks the name against [categories] and saves through
 * [repository] in [scope]. Once saved, [onSaved] gets the Category's id.
 */
class CategoryEditor(
    private val repository: FinanceRepository,
    private val scope: CoroutineScope,
    private val categories: () -> List<Category>,
    private val onSaved: (Long) -> Unit = {},
) : CategoryDialogActions {
    /** The open dialog, or null when it is closed. */
    var state by mutableStateOf<CategoryDialogState?>(null)
        private set

    /** Opens the dialog for a new Category, at the top level of Spending. */
    fun add() {
        state = CategoryDialogState()
    }

    /** Opens the dialog to rename [category]. */
    fun edit(category: Category) {
        state = CategoryDialogState(editing = category, name = category.name, place = category.place)
    }

    override fun onNameChange(name: String) {
        state = state?.copy(name = name, error = null)
    }

    override fun onPlaceChange(place: CategoryPlace) {
        state = state?.copy(place = place, error = null)
    }

    override fun onSave(): Boolean {
        val dialog = state ?: return false
        val error = categories().nameError(dialog.name, dialog.place, renaming = dialog.editing?.id ?: 0)
        if (error != null) {
            state = dialog.copy(error = error)
            return false
        }
        state = null
        val category = dialog.editing?.copy(name = dialog.name)
            ?: Category(0, dialog.name, dialog.place.kind, dialog.place.parentId)
        scope.launch { onSaved(repository.saveCategory(category)) }
        return true
    }

    override fun onDismiss() {
        state = null
    }
}
