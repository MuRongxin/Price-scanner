package com.pricescanner.app.ui.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pricescanner.app.data.AppDatabase
import com.pricescanner.app.data.Product
import com.pricescanner.app.data.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProductListViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).productDao()
    private val repository = ProductRepository(dao)

    private val _uiState = MutableStateFlow(ProductListUiState())
    val uiState: StateFlow<ProductListUiState> = _uiState.asStateFlow()

    private var allProducts: List<Product> = emptyList()

    init {
        viewModelScope.launch {
            repository.allProducts.collect { products ->
                allProducts = products
                applySearch()
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearching = query.isNotBlank()) }
        applySearch()
    }

    private fun applySearch() {
        val query = _uiState.value.searchQuery
        val filtered = if (query.isBlank()) {
            allProducts
        } else {
            repository.search(allProducts, query)
        }
        _uiState.update { it.copy(products = allProducts, filteredProducts = filtered) }
    }

    fun onToastShown() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    fun insertProduct(product: Product) {
        viewModelScope.launch {
            repository.insert(product)
        }
    }
}
