package com.pricescanner.app.ui.scan

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

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).productDao()
    private val repository = ProductRepository(dao)
    private var lastScanTime = 0L

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun onBarcodeDetected(barcode: String) {
        val now = System.currentTimeMillis()
        if (now - lastScanTime < 1000) return
        if (barcode == _uiState.value.barcode) return
        lastScanTime = now

        viewModelScope.launch {
            val product = repository.getByBarcode(barcode)
            _uiState.update {
                it.copy(
                    barcode = barcode,
                    existingProduct = product,
                    isNewForm = product == null  // auto-show form if not found
                )
            }
        }
    }

    fun showNewProductForm(barcode: String) {
        _uiState.update {
            it.copy(
                barcode = barcode,
                existingProduct = null,
                isNewForm = true
            )
        }
    }

    fun saveNewProduct(barcode: String, name: String, price: String, unit: String) {
        viewModelScope.launch {
            repository.insert(
                Product(
                    barcode = barcode,
                    name = name,
                    price = String.format("%.2f", price.toDoubleOrNull() ?: 0.0),
                    unit = unit.ifBlank { "个" },
                    createdAt = System.currentTimeMillis()
                )
            )
            val product = repository.getByBarcode(barcode)
            _uiState.update {
                it.copy(
                    existingProduct = product,
                    isNewForm = false,
                    toastMessage = "已添加: $name"
                )
            }
        }
    }

    fun dismissForm() {
        _uiState.update { it.copy(isNewForm = false) }
    }

    fun onToastShown() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
