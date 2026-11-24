package com.pablojimenez.financetracker.ui.categories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.FinanceRepository
import com.pablojimenez.financetracker.domain.moveTargets
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What happens once the owner taps Delete on a Category. */
sealed interface CategoryDeletion {
    val category: Category

    /** It has children, so it can't be deleted until they are gone. */
    data class HasChildren(override val category: Category) : CategoryDeletion

    /** It has no Transactions: a plain confirmation. */
    data class Confirm(override val category: Category) : CategoryDeletion

    /**
     * Its [count] Transactions move to [moveTo], one of [targets] (see [moveTargets]); [moveTo]
     * starts as its parent, or null for a top-level Category until the owner picks one.
     */
    data class MoveTransactions(
        override val category: Category,
        val count: Int,
        val targets: List<Category>,
        val moveTo: Long?,
    ) : CategoryDeletion
}

/** The Categories screen: the tree in [repository], and the dialogs that edit it. */
class CategoriesViewModel(private val repository: FinanceRepository) : ViewModel(), CategoriesActions {
    val categories: StateFlow<List<Category>> =
        repository.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The dialog that adds or renames a Category. */
    val editor = CategoryEditor(repository, viewModelScope, categories = { categories.value })

    /** The delete step under way, or null. */
    var deletion by mutableStateOf<CategoryDeletion?>(null)
        private set

    override fun onAdd() {
        editor.add()
    }

    override fun onOpen(category: Category) {
        editor.edit(category)
    }

    override fun onMove(id: Long, up: Boolean) {
        viewModelScope.launch { repository.moveCategory(id, up) }
    }

    /** Closes [category]'s dialog and asks how to delete it. */
    override fun onDelete(category: Category) {
        editor.onDismiss()
        val all = categories.value
        if (all.any { it.parentId == category.id }) {
            deletion = CategoryDeletion.HasChildren(category)
            return
        }
        viewModelScope.launch {
            val count = repository.transactions.first().count { it.categoryId == category.id }
            deletion = if (count == 0) {
                CategoryDeletion.Confirm(category)
            } else {
                CategoryDeletion.MoveTransactions(category, count, all.moveTargets(category.id), category.parentId)
            }
        }
    }

    override fun onDeleteTargetChange(id: Long) {
        deletion = (deletion as? CategoryDeletion.MoveTransactions)?.copy(moveTo = id) ?: deletion
    }

    /** Deletes the Category, moving its Transactions when it has any; nothing happens without a target. */
    override fun onConfirmDelete() {
        val confirmed = deletion ?: return
        val moveTo = when (confirmed) {
            is CategoryDeletion.HasChildren -> return
            is CategoryDeletion.Confirm -> null
            is CategoryDeletion.MoveTransactions -> confirmed.moveTo ?: return
        }
        deletion = null
        viewModelScope.launch { repository.deleteCategory(confirmed.category.id, moveTo) }
    }

    override fun onDeleteDismiss() {
        deletion = null
    }
}
