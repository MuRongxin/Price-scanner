package com.pricescanner.app.ui.scan

import com.pricescanner.app.data.Product

data class ScanUiState(
    val barcode: String? = null,
    val existingProduct: Product? = null,
    val isNewForm: Boolean = false,
    val toastMessage: String? = null
)
