# 「扫码查价」App — 架构级 Bug 分析报告

> 以资深架构工程师视角，逐行代码走查后发现的逻辑缺陷、并发问题、状态管理漏洞及安全隐患。
>
> 分析日期：2026-05-24
> 分析范围：`android/app/src/main/java/com/pricescanner/app/` 全部 Kotlin 源码

---

## 一、致命级（Critical）— 可能导致崩溃或数据丢失

### BUG-1: 数据库破坏性迁移 — 用户数据随时可能丢失

**位置**: `AppDatabase.kt:22`

```kotlin
.fallbackToDestructiveMigration()
```

**问题分析**:
- 当数据库 schema 版本变化时（如增加字段），Room 会**直接删除旧表并重建**
- 用户所有商品数据瞬间清零，且不可恢复
- 当前 `version = 2`，如果未来升级到 v3，所有用户数据丢失

**触发场景**: 发布新版本时修改了 `Product` 实体（如把 `price: String` 改成 `price: Double`）

**修复方案**:
```kotlin
// 必须实现 Migration，绝不能 fallbackToDestructiveMigration
.addMigrations(MIGRATION_1_2, MIGRATION_2_3)
```

---

### BUG-2: 相机绑定异常静默吞掉 — 扫描功能可能完全失效

**位置**: `ScanScreen.kt:290-295`

```kotlin
try {
    cameraProvider.unbindAll()
    cameraProvider.bindToLifecycle(
        lifecycleOwner, cameraSelector, preview, imageAnalysis
    )
} catch (_: Exception) {}
```

**问题分析**:
- 所有相机绑定异常被静默吞掉
- 如果 `SecurityException`、`IllegalStateException` 等发生，用户看到的是**黑屏**，没有任何提示
- 无法诊断问题，也无法给用户反馈

**触发场景**:
- 设备相机被其他应用占用
- 权限被用户手动撤销
- 某些厂商 ROM 的相机兼容性问题

**修复方案**:
```kotlin
try {
    cameraProvider.unbindAll()
    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
} catch (e: SecurityException) {
    Log.e("Camera", "权限被拒绝", e)
    // 显示权限错误提示
} catch (e: IllegalStateException) {
    Log.e("Camera", "相机状态错误", e)
    // 提示用户重启应用
} catch (e: Exception) {
    Log.e("Camera", "相机初始化失败", e)
    // 通用错误处理
}
```

---

### BUG-3: 价格字段 `String` 类型 — 数据一致性和排序灾难

**位置**: `Product.kt:11`

```kotlin
val price: String,  // 价格用 String 存储
```

**问题分析**:
- `String.format("%.2f", product.price.toDoubleOrNull() ?: 0.0)` 在多处重复
- `toDoubleOrNull()` 在非法输入时回退到 `0.0`，**静默掩盖数据错误**
- 无法做价格排序（字符串排序 `"10.00" < "2.00"` 是错的）
- 无法做价格范围查询
- 数据库层面没有数值约束

**触发场景**:
- 用户输入 `"abc"` → 显示 `¥0.00`，但数据库保存了 `"abc"`
- 排序时 `"100.00"` 排在 `"2.00"` 前面

**修复方案**:
```kotlin
// Product.kt
val price: Double,  // 改为 Double

// 展示时统一格式化
fun formatPrice(price: Double): String = String.format("¥%.2f", price)
```

---

### BUG-4: `EditProductScreen` 条码字段未真正禁用

**位置**: `EditProductScreen.kt:65-75`

```kotlin
OutlinedTextField(
    value = uiState.barcode,
    onValueChange = { viewModel.onBarcodeChanged(it) },  // ← 编辑时仍可修改！
    // ...
    colors = if (!uiState.isNew) OutlinedTextFieldDefaults.colors(
        disabledContainerColor = ...,
        disabledTextColor = Slate400
    ) else OutlinedTextFieldDefaults.colors()
)
```

**问题分析**:
- `colors` 只是视觉样式，**没有设置 `enabled = false`**
- 编辑现有商品时，用户仍可以修改条码
- 但 `EditProductViewModel.saveProduct()` 中没有校验条码唯一性
- 可能导致**两个商品拥有相同条码**，扫描时只能匹配到其中一个

**修复方案**:
```kotlin
OutlinedTextField(
    value = uiState.barcode,
    onValueChange = { },  // 空回调或保留原值
    enabled = uiState.isNew,  // ← 编辑时真正禁用
    // ...
)
```

---

## 二、高危级（High）— 并发问题、状态不一致、内存泄漏

### BUG-5: 扫描双重防抖逻辑不一致 — 可能导致漏扫或重复触发

**位置**: `ScanScreen.kt:255-276` + `ScanViewModel.kt:24-28`

```kotlin
// ScanScreen.kt — 相机层防抖
var cooldown = false
// ...
if (barcodes.isNotEmpty() && !cooldown) {
    cooldown = true
    Handler(Looper.getMainLooper()).postDelayed({ cooldown = false }, 1000)
}

// ScanViewModel.kt — ViewModel 层防抖
fun onBarcodeDetected(barcode: String) {
    val now = System.currentTimeMillis()
    if (now - lastScanTime < 1000) return   // ← 1000ms
    if (barcode == _uiState.value.barcode) return  // ← 同条码过滤
    lastScanTime = now
}
```

**问题分析**:
- 两层防抖时间不同步（相机层 1000ms Handler，ViewModel 层 1000ms 时间戳）
- `frameSkip % 3` 导致实际处理间隔不固定
- **竞态条件**: 相机层 cooldown 结束后立即扫描到同一条码，但 ViewModel 层的 `lastScanTime` 可能已经过了 1000ms，导致重复触发
- 更糟的是：ViewModel 层的 `barcode == _uiState.value.barcode` 会**永久阻止同一条码再次扫描**，除非扫描其他条码

**触发场景**:
1. 扫描条码 A → 显示结果
2. 用户点击"返回"回到扫描页
3. 再次扫描条码 A → **被 ViewModel 层永久过滤，没有任何反应**

**修复方案**:
- 统一在一层做防抖
- 提供"重新扫描"或"清除当前结果"机制
- 或者防抖只限制连续扫描同一条码，不永久阻止

---

### BUG-6: `Executors.newSingleThreadExecutor()` 永不关闭 — 内存泄漏

**位置**: `ScanScreen.kt:218`

```kotlin
val executor = remember { Executors.newSingleThreadExecutor() }
```

**问题分析**:
- `remember` 创建的 executor 在 Composable 离开组合时**不会自动关闭**
- 每次进入扫描页都会创建新的线程池
- 反复进出扫描页会导致线程数持续增长

**修复方案**:
```kotlin
val executor = remember { Executors.newSingleThreadExecutor() }

DisposableEffect(Unit) {
    onDispose {
        executor.shutdown()  // 组件销毁时关闭线程池
    }
}
```

---

### BUG-7: `ScanViewModel` 状态更新非原子性 — 并发扫描导致状态错乱

**位置**: `ScanViewModel.kt:30-38`

```kotlin
viewModelScope.launch {
    val product = repository.getProductByBarcode(barcode)  // 挂起点
    _uiState.update {                                     // 可能基于过期状态
        it.copy(
            barcode = barcode,
            existingProduct = product,
            isNewForm = product == null
        )
    }
}
```

**问题分析**:
- `repository.getProductByBarcode()` 是挂起函数，在挂起期间可能有新的扫描事件
- 如果快速连续扫描 A 和 B，可能出现：
  - 扫描 A → 挂起查询
  - 扫描 B → 挂起查询
  - B 查询完成 → 更新状态为 B
  - A 查询完成 → **覆盖状态为 A**（用户看到的是 A，但实际最后扫描的是 B）

**修复方案**:
```kotlin
fun onBarcodeDetected(barcode: String) {
    val now = System.currentTimeMillis()
    if (now - lastScanTime < 1000) return
    lastScanTime = now

    viewModelScope.launch {
        val product = repository.getProductByBarcode(barcode)
        // 检查这个扫描结果是否仍然是最新的
        if (barcode != _uiState.value.barcode) {
            _uiState.update {
                it.copy(
                    barcode = barcode,
                    existingProduct = product,
                    isNewForm = product == null
                )
            }
        }
    }
}
```

---

### BUG-8: `NewProductForm` 状态在重组时丢失

**位置**: `ScanScreen.kt:165-167`

```kotlin
var name by remember { mutableStateOf("") }
var price by remember { mutableStateOf("") }
var unit by remember { mutableStateOf("个") }
```

**问题分析**:
- 表单的 `name`、`price`、`unit` 是 Composable 级别的 `remember` 状态
- 如果屏幕旋转或配置变更，Composable 重组，`remember` 状态**不会保留**（除非使用 `rememberSaveable`）
- 用户输入一半旋转屏幕 → 输入内容全部丢失

**修复方案**:
```kotlin
var name by rememberSaveable { mutableStateOf("") }
var price by rememberSaveable { mutableStateOf("") }
var unit by rememberSaveable { mutableStateOf("个") }
```

---

### BUG-9: `importResolved` 逐条插入无事务保护

**位置**: `ProductListViewModel.kt:75-79`

```kotlin
fun importResolved(products: List<Product>) {
    viewModelScope.launch {
        products.forEach { repository.insert(it) }  // 每条都是独立事务
        showToast("已导入 ${products.size} 条数据")
    }
}
```

**问题分析**:
- `forEach { repository.insert(it) }` 每条记录都是独立的数据库事务
- 如果导入 1000 条 → 1000 次事务，性能极差
- 如果在导入过程中应用被杀死 → 部分数据导入成功，处于**不一致状态**

**修复方案**:
```kotlin
// ProductDao.kt
@Transaction
suspend fun insertAll(products: List<Product>) {
    products.forEach { insert(it) }
}

// ProductRepository.kt
suspend fun importResolved(products: List<Product>) {
    dao.insertAll(products)  // 单事务
}
```

---

## 三、中危级（Medium）— 边界条件、异常处理、逻辑缺陷

### BUG-10: 价格输入允许多个小数点

**位置**: `ScanScreen.kt:188` / `EditProductScreen.kt:95`

```kotlin
onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' } }
```

**问题分析**:
- 过滤只保留了数字和小数点，但**不限制小数点数量**
- 用户可以输入 `"3.5.7.9"`
- `toDoubleOrNull()` 会返回 `null`，然后被格式化为 `0.00`

**修复方案**:
```kotlin
onValueChange = { newValue ->
    val filtered = newValue.filter { it.isDigit() || it == '.' }
    // 只允许一个小数点
    val firstDot = filtered.indexOf('.')
    val cleaned = if (firstDot == -1) {
        filtered
    } else {
        filtered.take(firstDot + 1) + filtered.drop(firstDot + 1).filter { it.isDigit() }
    }
    price = cleaned
}
```

---

### BUG-11: `saveNewProduct` 中 `price.toDoubleOrNull() ?: 0.0` 静默失败

**位置**: `ScanViewModel.kt:58`

```kotlin
price = String.format("%.2f", price.toDoubleOrNull() ?: 0.0),
```

**问题分析**:
- 如果用户输入非法价格（如 `"abc"`），不报错，直接保存为 `"0.00"`
- 用户以为保存成功，实际价格是 0

**修复方案**:
```kotlin
val priceValue = price.toDoubleOrNull()
if (priceValue == null || priceValue <= 0) {
    _uiState.update { it.copy(toastMessage = "请输入有效价格") }
    return@launch
}
```

---

### BUG-12: `EditProductViewModel` 加载产品后未重置状态

**位置**: `EditProductViewModel.kt:37-52`

```kotlin
fun loadProduct(id: Long) {
    editingId = id
    viewModelScope.launch {
        val product = if (id > 0) repository.getProductById(id) else null
        if (product != null) {
            _uiState.update { it.copy(...) }  // 设置编辑状态
        }
        // ← 如果 id == 0（新增），没有重置状态！
    }
}
```

**问题分析**:
- 先编辑商品 A → 状态变为 A 的内容
- 再点击"手动添加"（id = 0）→ `loadProduct(0)` 不执行任何状态更新
- 表单仍然显示商品 A 的内容

**修复方案**:
```kotlin
fun loadProduct(id: Long) {
    editingId = id
    viewModelScope.launch {
        if (id > 0) {
            val product = repository.getProductById(id)
            if (product != null) {
                _uiState.update { it.copy(
                    barcode = product.barcode,
                    name = product.name,
                    price = product.price,
                    unit = product.unit,
                    isNew = false,
                    nameError = null,
                    priceError = null
                ) }
            }
        } else {
            // 新增时重置状态
            _uiState.value = EditProductUiState()  // 重置为默认值
        }
    }
}
```

---

### BUG-13: `ProductListViewModel` 搜索与数据更新竞态

**位置**: `ProductListViewModel.kt:25-47`

```kotlin
init {
    viewModelScope.launch {
        repository.allProducts.collect { products ->
            allProducts = products
            applySearch()  // ← 使用 allProducts 的最新值
        }
    }
}

fun onSearchQueryChanged(query: String) {
    _uiState.update { it.copy(...) }
    applySearch()  // ← 可能使用 allProducts 的旧值！
}
```

**问题分析**:
- `allProducts` 是普通变量，不是线程安全的
- `onSearchQueryChanged` 和 Flow collect 可能在不同线程同时执行
- 搜索时 `allProducts` 可能还没被更新

**修复方案**:
- 使用 `StateFlow` 或原子引用存储 `allProducts`
- 或者让搜索逻辑直接从 Flow 获取最新数据

---

### BUG-14: 导入冲突检测逻辑缺陷 — 空条码商品被误判为冲突

**位置**: `ProductListScreen.kt:241-243`

```kotlin
val conflicts = importData!!.filter { imported ->
    uiState.products.any { it.barcode == imported.barcode && it.barcode.isNotBlank() }
}
```

**问题分析**:
- 条件 `it.barcode.isNotBlank()` 只检查了现有商品的条码
- 如果导入的商品条码为空，且现有商品条码也为空 → 会被认为是冲突
- 实际上空条码商品不应该参与冲突检测

**修复方案**:
```kotlin
val conflicts = importData!!.filter { imported ->
    imported.barcode.isNotBlank() &&  // ← 导入商品条码也必须非空
    uiState.products.any { it.barcode == imported.barcode }
}
```

---

### BUG-15: `ConflictResolutionDialog` 中 `existing` 非空断言可能崩溃

**位置**: `ProductListScreen.kt:298`

```kotlin
val existing = existingProducts.find { it.barcode == current.barcode }!!
```

**问题分析**:
- `!!` 非空断言，如果 `existingProducts` 中没有匹配项 → **NullPointerException**
- 虽然逻辑上不应该发生，但在并发场景下（列表被清空的同时显示弹窗）可能触发

**修复方案**:
```kotlin
val existing = existingProducts.find { it.barcode == current.barcode }
if (existing == null) {
    currentIndex++  // 跳过此项
    return
}
```

---

### BUG-16: 导出文件写入缓存目录但无清理机制

**位置**: `ProductListScreen.kt:69-71`

```kotlin
val file = File(context.cacheDir, "price_backup.json")
file.writeText(json, Charsets.UTF_8)
```

**问题分析**:
- 每次导出都写入同名文件，覆盖旧文件
- 但如果导出失败（如磁盘空间不足），旧文件残留
- 没有定期清理机制，缓存目录可能膨胀

**修复方案**:
```kotlin
val file = File(context.cacheDir, "price_backup_${System.currentTimeMillis()}.json")
file.writeText(json, Charsets.UTF_8)
// 使用后删除
// 或定期清理旧的备份文件
```

---

## 四、低危级（Low）— 代码异味、潜在问题

### BUG-17: `PinyinUtil` 未初始化时返回空字符串

**位置**: `PinyinUtil.kt:25`

```kotlin
fun getInitials(text: String): String {
    if (!isLoaded) return ""  // ← 静默失败
    // ...
}
```

**问题分析**:
- 如果 `init()` 调用失败（如资源文件损坏），搜索功能中的拼音匹配完全失效
- 没有日志，没有提示

---

### BUG-18: `SoundManager` 每次播放都创建新的 `AudioTrack`

**位置**: `SoundManager.kt:15-46`

**问题分析**:
- 每次扫描都创建 `AudioTrack` 实例，播放后延迟释放
- 快速连续扫描时可能创建大量实例
- 应该复用或限制并发

---

### BUG-19: `ScanScreen` 中 `ScanOverlay` 的动画在后台仍运行

**位置**: `ScanScreen.kt:317-322`

```kotlin
val transition = rememberInfiniteTransition()
val scanLineY by transition.animateFloat(...)
```

**问题分析**:
- `rememberInfiniteTransition` 在 Composable 可见时持续运行
- 如果扫描页在后台（如用户切换到其他应用），动画仍在消耗 CPU/GPU

---

### BUG-20: `HorizontalPager` 的 `Int.MAX_VALUE` 可能导致整数溢出

**位置**: `MainActivity.kt:35-38`

```kotlin
val pagerState = rememberPagerState(
    initialPage = Int.MAX_VALUE / 2 - 1,
    pageCount = { Int.MAX_VALUE }
)
```

**问题分析**:
- `Int.MAX_VALUE / 2 - 1` 约 10 亿，用户不可能滑动到边界
- 但 `page % 2` 计算在极端情况下可能有符号问题
- 无实际影响，但属于不良实践

---

## 五、Bug 汇总表

| 编号 | 级别 | 模块 | 问题 | 影响 |
|------|------|------|------|------|
| BUG-1 | 🔴 Critical | 数据库 | 破坏性迁移 | 升级时数据全部丢失 |
| BUG-2 | 🔴 Critical | 扫描 | 相机异常静默吞掉 | 黑屏无提示 |
| BUG-3 | 🔴 Critical | 数据模型 | price 为 String | 排序错误、数据不一致 |
| BUG-4 | 🔴 Critical | 编辑页 | 条码未真正禁用 | 重复条码、扫描匹配错误 |
| BUG-5 | 🟠 High | 扫描 | 双重防抖不一致 | 漏扫或重复触发 |
| BUG-6 | 🟠 High | 扫描 | 线程池不关闭 | 内存泄漏 |
| BUG-7 | 🟠 High | 扫描 | 状态更新非原子 | 并发状态错乱 |
| BUG-8 | 🟠 High | 扫描 | 表单状态不保存 | 旋转丢失输入 |
| BUG-9 | 🟠 High | 导入 | 无事务保护 | 部分导入、数据不一致 |
| BUG-10 | 🟡 Medium | 输入 | 允许多个小数点 | 保存为 0 元 |
| BUG-11 | 🟡 Medium | 扫描 | 非法价格静默失败 | 保存为 0 元 |
| BUG-12 | 🟡 Medium | 编辑 | 新增未重置状态 | 显示旧数据 |
| BUG-13 | 🟡 Medium | 列表 | 搜索竞态 | 搜索结果不准确 |
| BUG-14 | 🟡 Medium | 导入 | 空条码冲突误判 | 导入逻辑错误 |
| BUG-15 | 🟡 Medium | 导入 | 非空断言 | 可能崩溃 |
| BUG-16 | 🟡 Medium | 导出 | 无文件清理 | 缓存膨胀 |
| BUG-17 | 🟢 Low | 工具 | 拼音未初始化 | 搜索失效 |
| BUG-18 | 🟢 Low | 音频 | 频繁创建 AudioTrack | 资源浪费 |
| BUG-19 | 🟢 Low | 扫描 | 后台动画运行 | 耗电 |
| BUG-20 | 🟢 Low | 导航 | Int.MAX_VALUE | 代码异味 |

---

## 六、修复优先级建议

```
第一阶段（立即修复）: BUG-1, BUG-3, BUG-4, BUG-6
第二阶段（本周修复）: BUG-2, BUG-5, BUG-7, BUG-8, BUG-9
第三阶段（下个迭代）: BUG-10, BUG-11, BUG-12, BUG-13, BUG-14, BUG-15
第四阶段（技术债）:   BUG-16, BUG-17, BUG-18, BUG-19, BUG-20
```

---

*报告生成时间: 2026-05-24*
*分析工具: 人工代码走查*
