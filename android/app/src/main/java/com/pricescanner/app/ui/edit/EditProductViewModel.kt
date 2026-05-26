package com.pricescanner.app.ui.edit

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

data class EditProductUiState(
    val barcode: String = "",
    val name: String = "",
    val price: String = "",
    val unit: String = "个",
    val isNew: Boolean = true,
    val nameError: String? = null,
    val priceError: String? = null,
    val toastMessage: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false
)

class EditProductViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).productDao()
    private val repository = ProductRepository(dao)
    private var editingBarcode: String = ""

    private val _uiState = MutableStateFlow(EditProductUiState())
    val uiState: StateFlow<EditProductUiState> = _uiState.asStateFlow()

    fun loadProduct(barcode: String) {
        editingBarcode = barcode
        // Reset form state
        val isNew = barcode.isBlank()
        _uiState.update { EditProductUiState(isNew = isNew, barcode = barcode) }
        if (!isNew) {
            viewModelScope.launch {
                val product = repository.getByBarcode(barcode)
                if (product != null) {
                    _uiState.update {
                        it.copy(
                            barcode = product.barcode,
                            name = product.name,
                            price = product.price,
                            unit = product.unit,
                            isNew = false
                        )
                    }
                }
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, nameError = null) }
    }

    fun onPriceChanged(price: String) {
        _uiState.update { it.copy(price = price, priceError = null) }
    }

    fun onUnitChanged(unit: String) {
        _uiState.update { it.copy(unit = unit) }
    }

    fun onBarcodeChanged(barcode: String) {
        _uiState.update { it.copy(barcode = barcode) }
    }

    fun saveProduct() {
        val state = _uiState.value
        var hasError = false

        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = "请输入商品名称") }
            hasError = true
        }
        val priceVal = state.price.toDoubleOrNull()
        if (state.price.isBlank() || priceVal == null || priceVal <= 0) {
            _uiState.update { it.copy(priceError = "请输入有效价格") }
            hasError = true
        }
        if (hasError) return

        viewModelScope.launch {
            val barcode = state.barcode.ifBlank {
                // Auto-generate 10-char barcode: a + 9 digits from name hash
                "a${(Math.abs(state.name.hashCode()) % 1000000000).toString().padStart(9, '0')}"
            }
            repository.insert(
                Product(
                    barcode = barcode,
                    name = state.name,
                    price = String.format("%.2f", priceVal),
                    unit = state.unit.ifBlank { "个" },
                    createdAt = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(saved = true, toastMessage = "已保存") }
        }
    }

    fun deleteProduct() {
        viewModelScope.launch {
            val product = repository.getByBarcode(editingBarcode)
            if (product != null) {
                repository.delete(product)
                _uiState.update { it.copy(deleted = true, toastMessage = "已删除: ${product.name}") }
            }
        }
    }

    fun onToastShown() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
