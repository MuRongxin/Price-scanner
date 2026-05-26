package com.pricescanner.app.ui.list

import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.reflect.TypeToken
import com.pricescanner.app.data.Product
import com.pricescanner.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    viewModel: ProductListViewModel = viewModel(),
    onEditProduct: (String) -> Unit,
    onManualAdd: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Import conflict state
    var conflictItem by remember { mutableStateOf<Product?>(null) }
    var conflictExisting by remember { mutableStateOf<Product?>(null) }
    var editName by remember { mutableStateOf("") }
    var editPrice by remember { mutableStateOf("") }
    var showConflictDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onToastShown()
        }
    }

    fun doExport() {
        if (uiState.products.isEmpty()) {
            viewModel.showToast("没有数据可导出")
            return
        }
        try {
            val json = Gson().toJson(uiState.products)
            val file = File(context.filesDir, "price_backup.json")
            file.writeText(json, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, "分享备份"))
        } catch (_: Exception) {
            viewModel.showToast("分享失败")
        }
    }

    var pendingImport by remember { mutableStateOf<List<Product>?>(null) }
    var importIndex by remember { mutableStateOf(0) }

    // Process next item in pending import
    fun processNextImport() {
        val importList = pendingImport ?: run {
            viewModel.showToast("导入完成")
            return
        }
        if (importIndex >= importList.size) {
            viewModel.showToast("导入完成")
            pendingImport = null
            return
        }
        val existingMap = uiState.products.associateBy { it.barcode }
        val item = importList[importIndex]
        importIndex++

        // Fix empty barcode from old data
        val barcode = item.barcode.ifBlank {
            "a${(Math.abs(item.name.hashCode()) % 1000000000).toString().padStart(9, '0')}"
        }
        val fixedItem = item.copy(barcode = barcode)

        val existing = existingMap[fixedItem.barcode]
        when {
            existing == null -> {
                viewModel.insertProduct(fixedItem)
                scope.launch { processNextImport() }
            }
            existing.name == fixedItem.name && existing.price == fixedItem.price -> {
                scope.launch { processNextImport() }
            }
            else -> {
                conflictItem = fixedItem
                conflictExisting = existing
                editName = fixedItem.name
                editPrice = fixedItem.price
                showConflictDialog = true
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: ""
                    }
                    val type = object : TypeToken<List<Product>>() {}.type
                    val products = withContext(Dispatchers.Default) { Gson().fromJson<List<Product>>(json, type) }
                    if (products.isNotEmpty()) {
                        pendingImport = products
                        importIndex = 0
                        processNextImport()
                    }
                } catch (_: Exception) {
                    viewModel.showToast("文件格式错误")
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("扫码查价", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "菜单")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("导出备份") },
                                onClick = { showMenu = false; doExport() }
                            )
                            DropdownMenuItem(
                                text = { Text("导入备份") },
                                onClick = { showMenu = false; importLauncher.launch("application/json") }
                            )
                            DropdownMenuItem(
                                text = { Text("清空数据", color = Red500) },
                                onClick = { showMenu = false; showClearDialog = true }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = androidx.compose.ui.graphics.Color.White,
                shadowElevation = 2.dp
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("搜索（支持拼音首字母）") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("共 ${uiState.products.size} 件商品", fontSize = 13.sp, color = Slate500)
                TextButton(onClick = onManualAdd) {
                    Text("+ 手动添加", fontSize = 12.sp, color = Slate400)
                }
            }

            val displayList = if (uiState.isSearching) uiState.filteredProducts else uiState.products

            if (displayList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (uiState.products.isEmpty()) "还没有商品\n滑动扫码录入商品"
                            else "没有匹配的商品",
                            fontSize = 15.sp,
                            color = Slate400,
                            style = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayList, key = { it.barcode }) { product ->
                        ProductCard(product = product, onClick = { onEditProduct(product.barcode) })
                    }
                }
            }
        }
    }

    // Clear data dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("确认清空") },
            text = { Text("确定清空全部 ${uiState.products.size} 条商品数据？此操作不可恢复！") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false; viewModel.deleteAll(); viewModel.showToast("已清空全部数据")
                }) { Text("确定", color = Red500) }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消") } }
        )
    }

    // Import conflict dialog (per-item)
    if (showConflictDialog && conflictItem != null && conflictExisting != null) {
        AlertDialog(
            onDismissRequest = { /* must choose an action */ },
            title = { Text("条码冲突: ${conflictItem!!.barcode}") },
            text = {
                Column {
                    Text("本地: ${conflictExisting!!.name}  ¥${conflictExisting!!.price}/${conflictExisting!!.unit}",
                        fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("导入: ${conflictItem!!.name}  ¥${conflictItem!!.price}/${conflictItem!!.unit}",
                        color = Slate500)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("修改导入数据后保存:", fontSize = 12.sp, color = Slate500)
                    OutlinedTextField(
                        value = editName, onValueChange = { editName = it },
                        label = { Text("商品名称") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = editPrice, onValueChange = { editPrice = it },
                        label = { Text("单价") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        // Skip → don't import
                        showConflictDialog = false
                        scope.launch { processNextImport() }
                    }) { Text("跳过", color = Slate500) }
                    TextButton(onClick = {
                        // Overwrite → replace local with imported
                        viewModel.insertProduct(conflictItem!!)
                        showConflictDialog = false
                        scope.launch { processNextImport() }
                    }) { Text("覆盖", color = Red500) }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    // Modify → save edited version with renamed barcode
                    val modified = conflictItem!!.copy(
                        name = editName,
                        price = if (editPrice.toDoubleOrNull() != null)
                            String.format("%.2f", editPrice.toDouble()) else conflictItem!!.price,
                        barcode = conflictItem!!.barcode + "(导入)"
                    )
                    viewModel.insertProduct(modified)
                    showConflictDialog = false
                    scope.launch { processNextImport() }
                }) { Text("修改并保留") }
            }
        )
    }
}

@Composable
fun ProductCard(product: Product, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        color = androidx.compose.ui.graphics.Color.White
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(product.barcode, fontSize = 12.sp, color = Slate400, modifier = Modifier.padding(top = 2.dp))
            }
            Text(
                "¥${String.format("%.2f", product.price.toDoubleOrNull() ?: 0.0)}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Red500
            )
            Text(
                "/${product.unit}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = Slate800
            )
        }
    }
}
