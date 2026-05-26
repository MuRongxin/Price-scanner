# Price Scanner（扫码查价）

Android 扫码查价应用 — 扫描商品条码自动识别商品名称和价格，支持手动录入、搜索管理和数据导入导出。

## 技术栈

| 维度 | 方案 |
|------|------|
| UI | Jetpack Compose + Material 3 |
| 相机 | CameraX + PreviewView (COMPATIBLE 模式) |
| 扫码 | ML Kit Barcode Scanning |
| OCR | ML Kit Chinese Text Recognition |
| 数据库 | Room v3，`barcode` 为主键 |
| 条码查询 | RollToolsApi → OpenFoodFacts 双源回退 |
| 动画 | Compose Animation API（圆形光圈式展开/收缩） |

## 功能

- **扫码识别** — 支持 EAN-13/8、UPC-A/E、CODE-128/39、Codabar 等条码格式
- **OCR 自动填名** — 扫描到未录入商品时，相机自动识别包装文字填入名称（1.5s 间隔）
- **条码联网查询** — 优先查询 RollToolsApi，失败回退 OpenFoodFacts；匹配后一键填入
- **商品管理** — 按条码增删改查，手动录入自动生成虚拟条码
- **数据迁移** — Room v2→v3 自动备份恢复，旧数据无条码自动补全
- **导入导出** — JSON 格式，通过 SAF（Storage Access Framework）选择文件；导入含冲突解决（跳过/覆盖/编辑）
- **过渡动画** — 扫描界面从按钮位置圆形展开/收缩，编辑页底部滑入

## 构建

```bash
cd android
./gradlew assembleRelease
```

APK 输出：`android/app/build/outputs/apk/release/app-release.apk`

签名用仓库内的 `price-scanner.keystore`（仅开发用途）。

## 项目结构

```
android/app/src/main/java/com/pricescanner/app/
├── MainActivity.kt          # 导航 + 动画控制
├── PriceScannerApp.kt       # Application（PinyinUtil 初始化）
├── data/
│   ├── Product.kt            # Room Entity（barcode 为主键）
│   ├── ProductDao.kt         # DAO
│   ├── ProductRepository.kt  # 仓库层
│   └── AppDatabase.kt        # Room DB（v3 + 自动迁移）
├── ui/
│   ├── scan/
│   │   ├── ScanScreen.kt     # 相机预览 + 扫描 + OCR + 表单
│   │   ├── ScanViewModel.kt
│   │   ├── ScanUiState.kt
│   │   └── ScanHomePage.kt   # 首页（扫描按钮 + 提示）
│   ├── edit/
│   │   ├── EditProductScreen.kt
│   │   └── EditProductViewModel.kt
│   ├── list/
│   │   ├── ProductListScreen.kt   # 商品列表 + 搜索 + 导入导出
│   │   ├── ProductListViewModel.kt
│   │   └── ProductListUiState.kt
│   └── theme/
│       ├── Color.kt, Theme.kt, Type.kt
└── util/
    ├── BarcodeLookup.kt      # 条码→名称 多源查询
    ├── PinyinUtil.kt         # 拼音搜索分词
    └── SoundManager.kt       # 扫码成功提示音（880Hz）
```

## 动画设计

扫描界面的进入/离开采用**圆形光圈式过渡**：

- **进入**：扫描界面从首页扫描按钮位置以圆形展开至全屏（400ms）
- **离开**：扫描界面从全屏以圆形收缩回按钮位置（400ms）

通过 `drawWithContent` + `clipPath` 实现圆形裁剪，`PreviewView` 设为 `COMPATIBLE` 模式（TextureView）确保遵守 Compose 绘制层级。

## 数据库

- **版本**：v3
- **主键**：`barcode` (String)
- **迁移策略**：`fallbackToDestructiveMigration()` + 自动备份恢复
  - 启动时用原生 SQLite 读取旧数据库，保存为 JSON
  - Room 建新库后重新插入，空条码自动分配 `a` 前缀虚拟条码
