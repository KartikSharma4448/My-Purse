package com.mypurse.vault

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mypurse.vault.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VaultState(val ready: Boolean = false,val fatal: String? = null,
    val cards: List<VaultCard> = emptyList(),val documents: List<VaultDocument> = emptyList(),
    val busy: Boolean = false,val message: String? = null,val error: String? = null,val storedBytes: Long = 0)

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(VaultState())
    val state = mutable.asStateFlow()
    var repository: VaultRepository? = null
        private set
    init {
        viewModelScope.launch {
            try {
                val repo = withContext(Dispatchers.IO) { (application as PurseApplication).repository.also { it.tidy() } }
                repository = repo
                mutable.update { it.copy(ready = true) }
                launch { repo.cards.collect { cards -> mutable.update { it.copy(cards = cards) } } }
                launch { repo.documents.collect { docs -> mutable.update { it.copy(documents = docs) } } }
                launch {repo.dao.storedBytes().collect {bytes -> mutable.update {it.copy(storedBytes=bytes)}}}
            } catch(e: Exception) { mutable.update { it.copy(fatal = e.message ?: "Cannot open the local vault.") } }
        }
    }
    fun dismissMessage() { mutable.update { it.copy(message = null,error = null) } }
    private fun operation(onSuccess: ()->Unit = {},block: suspend (VaultRepository) -> String) {
        if(state.value.busy) return
        val repo = repository ?: return
        viewModelScope.launch {
            mutable.update { it.copy(busy = true,error = null) }
            try { val message = block(repo); mutable.update { it.copy(message = message) };onSuccess() }
            catch(e: Exception) { mutable.update { it.copy(message = e.message ?: "Please try again.",error = e.message ?: "Please try again.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun save(card: VaultCard,onSaved: ()->Unit = {}) = operation(onSaved) { it.save(card); "Card saved" }
    fun saveCard(card: VaultCard,front: Uri?,back: Uri?,onSaved: ()->Unit) = operation(onSaved) {it.saveCard(card,front,back); "Card saved"}
    fun save(doc: VaultDocument,onSaved: ()->Unit = {}) = operation(onSaved) { it.save(doc); "Document updated" }
    fun import(uri: Uri,category: String) = operation { val doc = it.import(uri,category); "${doc.title} imported" }
    fun delete(card: VaultCard) = operation { it.delete(card); "Card deleted" }
    fun delete(doc: VaultDocument) = operation { it.delete(doc); "Document deleted" }
}
