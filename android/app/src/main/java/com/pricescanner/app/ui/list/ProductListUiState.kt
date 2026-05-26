package com.pricescanner.app.ui.list

import com.pricescanner.app.data.Product

data class ProductListUiState(
    val products: List<Product> = emptyList(),
    val searchQuery: String = "",
    val filteredProducts: List<Product> = emptyList(),
    val isSearching: Boolean = false,
    val toastMessage: String? = null
)
