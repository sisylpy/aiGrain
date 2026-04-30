# AI 添加商品 — 文案、状态与接口约定（给前端）

本文约定：**小程序「AI 添加商品」页** 的用户可见文案、**`flowState` 状态机**，以及前后端对齐的 **HTTP 接口**（路径可按项目统一前缀调整，例如与现有 `ai/chat` 并列：`/ai/goods-add/...`）。  
后端实现 AI / Prompt 时，只要遵守本文的 **请求字段** 与 **响应里的 `flowState` + `data` 结构** 即可；**首版可先 mock 固定 JSON**，待联调再接入真实模型。

---

## 1. 页面与主流程（产品）

1. 用户进入 **「AI 添加商品」** 页。  
2. **首屏**：展示引导文案（见 §3.1），可配两个必填框：**商品名称**、**商品规格**（或一行合并输入，由前端解析/整句提交，推荐两框便于校验）；**可选**再配一行 **说明**（`goodsFurtherDescription`，见 §5.1）——用途、备注、帮助对照目录、临时品备注等**写在同一字段**，由模型与落库共用。  
3. **首屏入口三选一（推荐）**：  
   - **AI 添加**（默认）：`analyzeMode=AI` 或不传 → 调用 **§5.1**，由模型匹配目录。  
   - **自己选目录**：`analyzeMode=MANUAL_CATALOG` → 同一接口返回 **一级目录** `catalogLevel1Options`，用户逐级选择后带上 `manualGreatGrandNxGoodsId` / `manualGrandNxGoodsId` / `manualFatherNxGoodsId` 再次请求（见 §5.1 与 §2 `MANUAL_CATALOG_*`）。  
   - **直接添加临时商品**：`analyzeMode=DIRECT_TEMP`（或 **`ADD_TEMP` / `TEMP_ONLY` / `TEMP_GOODS`** 等价）→ 同一 **§5.1** 接口，**不调 DeepSeek、不调目录匹配**，走与 **`saveLinshiGoodsGb`** 一致的落库逻辑，**当场**返回 **`flowState=SUCCESS`**（`persistedGoods`、`gbDistributerGoods`、`gbDepartmentDisGoods` 与 **§5.2** `confirmType=TEMP` 成功一致）。若产品需要「用户点确认再提交」，由**前端在调接口前**完成二次确认即可，**无需再调 confirm**。  
4. 用户点击 **「提交分析」**（或等价主按钮）→ 调用 **§5.1 分析接口**，展示 loading：「正在对照商品库…」（**`MANUAL_CATALOG` / `DIRECT_TEMP` 可不展示** AI 对照目录类 loading，或统一短 loading）。  
5. 根据返回的 **`flowState`** 渲染不同区块（§2 + §3）。  
6. 若需用户确认或选择 → 用户操作后调用 **§5.2 确认接口**（或再次调用分析接口并带上 `sessionId` + 用户选择，见 §5 说明）。  
7. **`SUCCESS`**：展示成功文案，提供「完成」跳转（商品列表 / 采购等由产品定）。

**未匹配时的说明（`NO_MATCH`）**：与**首屏同一字段** **`goodsFurtherDescription`**（标签「说明（选填）」、占位见 §3.1）。用户可（1）**不填**直接走临时商品；（2）**填写或修改后点「再试一次匹配」**——再次调用 **§5.1**，同一 `sessionId` 并带上最新 **`goodsFurtherDescription`**（及名称、规格），便于模型在分类与 SKU 两阶段结合补充信息（仍不做与主搜索同语义的名称全库检索）；（3）**确认临时商品**——**§5.2** 将该说明写入商品详情/备注（与现有 `detail` 类字段对齐）。**不增加新 `flowState`**，仍为 `NO_MATCH` 区块内的 UI 扩展。

**原则**：**分析**与**落库**可分两步，避免误触直接写库；若产品坚持「一键到底」，后端可在 `MATCH_SINGLE` 时同步落库，但仍建议返回 `SUCCESS` 与商品摘要供前端展示。

---

## 2. 状态枚举 `flowState`

前端以 **`flowState`** 为唯一路由开关（字符串枚举，全大写）。

| `flowState` | 含义 | 前端应展示 | 主要操作 |
|-------------|------|------------|----------|
| `INPUT` | 仅客户端首屏引导（可选） | 引导 + 输入框 | 「提交分析」 |
| `ANALYZING` | 分析中（可选：仅前端本地状态） | Loading | 禁用重复提交 |
| `MATCH_SINGLE` | 目录中**唯一**可信匹配 | 匹配摘要 + 路径 | 「确认添加」 / 「换一个说法」 |
| `MATCH_CHOICE` | **多个**候选需用户点选 | 候选列表（卡片） | 选中一项 → 「确认添加」 |
| `DIS_CATALOG_GB_ONLY` | DeepSeek **之前**：合并后列表 **全部为** 本批发商 **已入库** 的目录 SKU（无待下载）。 | `catalogHitComposition=GB_ONLY` | 偏「去下单」；`nxCatalogConfirmIntents` 常为单条 **`ADD_SIBLING_SKU`**。 |
| `DIS_CATALOG_NX_ONLY` | 预检索列表 **全部为** 农鑫目录有、批发商 **尚未下载** 的 SKU。 | **`catalogHitComposition=NX_ONLY`** | **主：**选一条 **`confirm`** + **`USE_MATCHED`** 下载；**`nxCatalogConfirmIntents`** 通常仅 **`USE_MATCHED`**。 |
| `DIS_CATALOG_MIXED` | 既有已入库条目，又有待下载条目。 | **`catalogHitComposition=MIXED`** | **并排两种按钮：**已入库走下单 / 待下载走确认；**`nxCatalogConfirmIntents`** 含 **`USE_MATCHED`** 与 **`ADD_SIBLING_SKU`** 两条。 |
| `NO_MATCH` | 目录中**无**合适匹配（且无法进入分支确认） | 说明 + **可选「说明」输入**（`goodsFurtherDescription`，与首屏同源） + **`data.catalogLevel1Options`（一级目录，便于改选手动逐级选）** + `analyzeModeHint` | 「再试一次匹配」 / 「添加为临时商品」 / **「自己从一级目录选」**（`analyzeMode=MANUAL_CATALOG`） / 「返回修改」 |
| `BRANCH_CONFIRM` | 已定位一级/二级，但**三级 SKU 未匹配到**（或该二级下无 SKU） | 展示 `data.branchOptions`（一级/二级名称与 id），请用户**点选确认正确的二级** | 选中后再次调用 **§5.1**，带同一 `sessionId` + `confirmedGrandNxGoodsId` |
| `MANUAL_CATALOG_L1` | **手动选目录**：首步 | 展示 `data.catalogLevel1Options`（一级 `nx_goods_level=0`） | 用户选一级 → 下一请求带 `manualGreatGrandNxGoodsId` |
| `MANUAL_CATALOG_L2` | 手动选目录：已选一级 | 展示 `catalogLevel2Options`（二级 level=1） | 选二级 → 带 `manualGrandNxGoodsId` |
| `MANUAL_CATALOG_L3` | 手动选目录：已选二级，且**有**三级品名 | 展示 `catalogLevel3Options`（品名父 level=2） | 选三级 → 带 `manualFatherNxGoodsId` |
| `MANUAL_CATALOG_BRANCH` | 手动下二级下**无**三级品名，或三级下**无** SKU | 与 `BRANCH_CONFIRM` 类似，展示 `branchOptions` | 确认后 **§5.1** 带 `confirmedGrandNxGoodsId` 走扩充目录 |
| `MANUAL_CATALOG_SKU_SINGLE` | 手动下某三级下**仅一条** SKU | 同 `MATCH_SINGLE`，`matchSummary` | **§5.2** `NX_CATALOG` + `nxCatalogIntent` |
| `MANUAL_CATALOG_SKU_CHOICE` | 手动下某三级下**多条** SKU | 同 `MATCH_CHOICE`，`candidates` | 选中后 **§5.2** |
| `TEMP_CONFIRM` | 用户已选择走临时商品（如自 `NO_MATCH` 进入） | 临时品摘要二次确认 | 「确认添加临时商品」 / 「返回修改」 |
| `SUCCESS` | 已落库成功 | 成功提示 + 商品关键信息 | 「完成」 |
| `ERROR` | 系统或参数错误 | 错误说明 | 「重试」 / 返回 |

说明：

- **`DIS_CATALOG_*`（三态）**：`analyzeMode=AI`（默认）、且 **`skipCatalogPrefetch`≠`true`** 时，在 DeepSeek **之前**，根据本次合并候选里 **已全部入库 /** **全部未入库 /** **两者皆有** 分别返回。**`MANUAL_CATALOG`**/**`DIRECT_TEMP`** 不出现。  

- `INPUT`、`ANALYZING` 可由**纯前端**维护，接口不必返回；若后端统一返回，也可在首次进页不请求接口。  
- **`MATCH_*` / `NO_MATCH` / `BRANCH_CONFIRM` / `TEMP_CONFIRM`** 必须由 **§5.1** 或 **§5.2** 返回，便于联调与埋点。  
- **`analyzeMode`** 为临时品直连（`DIRECT_TEMP` 及文档所列别名）时，**§5.1** 成功即 **`SUCCESS`**，**不调 DeepSeek**（不经 `TEMP_CONFIRM` / **§5.2**）。  
- **`SUCCESS`** 通常由 **§5.2** 返回；若用户走「确认二级后扩充目录」，**§5.1** 在携带 `confirmedGrandNxGoodsId` 时也可直接返回 `SUCCESS`（见 §5.1 说明）。

---

## 3. 产品文案（建议稿，可改字不改意）

### 3.1 首屏引导（`INPUT`）

| 用途 | 文案 |
|------|------|
| 页面标题 | AI 添加商品 |
| 引导主句 | 请填写要添加的**商品名称**和**规格**，我们会对照公司商品目录；对不上的可以添加为**临时商品**。 |
| 名称占位 | 例如：西红柿 |
| 规格占位 | 例如：斤、袋、箱 |
| 主按钮 | 提交分析 |
| 辅助说明（小字） | 规格请尽量与平时下单习惯一致，便于自动匹配。 |
| **说明（选填）** | 对应接口字段 **`goodsFurtherDescription`**。**占位示例（可整段展示）**：用途（用在什么菜、场景）、备注、别名或包装、帮助系统对照目录的补充、若添加临时品时希望带上的说明等——**统一写在这一项**；不填也可提交。 |

### 3.2 加载（`ANALYZING`）

| 用途 | 文案 |
|------|------|
| Loading | 正在对照商品库，请稍候… |

### 3.3 唯一匹配（`MATCH_SINGLE`）

| 用途 | 文案 |
|------|------|
| 区块标题 | 找到以下商品 |
| 路径前缀 | 分类： |
| 主按钮一 | 使用目录里的这个商品（对应 `confirm` 的 `nxCatalogIntent=USE_MATCHED`，或不传该字段） |
| 主按钮二 | 保留我输入的名称和规格，在同品类下新增一条目录 SKU（`nxCatalogIntent=ADD_SIBLING_SKU`；名称规格取自用户输入或会话快照） |
| 次按钮 | 重新填写 |

说明：当用户输入（如「素鸡卷 / 斤」）与目录匹配项（如「素鸡 / 根」）不一致时，**两个按钮**对应两种落库路径；详见 **§5.2** `nxCatalogIntent`。`analyze` 响应中的 `data.nxCatalogConfirmIntents` 提供 `intent` + `label` 供前端渲染（与上表文案可对齐）。

### 3.4 多候选（`MATCH_CHOICE`）

| 用途 | 文案 |
|------|------|
| 区块标题 | 请选择一个最符合的商品 |
| 辅助说明 | 以下结果来自公司商品目录，点选一项后确认添加。 |
| 主按钮 | 确认添加（需先选中一行） |
| 次按钮 | 都不对，改为临时商品 / 重新填写（二选一或合并为「以上都不是」→ 转 `NO_MATCH` 流程由产品定） |

### 3.5a 确认一二级后再扩目录（`BRANCH_CONFIRM`）

| 用途 | 文案 |
|------|------|
| 区块标题 | 未找到目录里的具体规格商品 |
| 主说明（与 `data.assistantMessage` 对齐） | 口语化引导：先看下面**推荐的一级/二级**是不是您想归的那一类——是就确认，系统会在该大类下帮您加上这件货；不是就**改手动选目录**重新归类，或**直接加成临时商品**。 |
| 补充（技术说明，可小字） | 确认后系统将**新增品名与规格 SKU 到农鑫目录**（业务称三级、四级，对应库 `nx_goods_level` 2 与 3），并加入您的批发商商品。 |
| 列表 | 每项展示一级名称、二级名称；用户点选一项二级。 |
| 主按钮 | 确认分类并生成目录商品（调用 **§5.1** 并传 `confirmedGrandNxGoodsId`） |
| 次按钮 | 添加为临时商品 / 返回修改 |

### 3.5 无匹配（`NO_MATCH`）

| 用途 | 文案 |
|------|------|
| 区块标题 | 未在商品目录中找到完全匹配 |
| 说明 | 您可以修改名称或规格后再试，或添加为**临时商品**（仍可在下单中使用）。 |
| 可选输入标题 | **说明（选填）**（与 §3.1 首屏**同一字段**、同一 `goodsFurtherDescription`，可回显已填内容） |
| 可选输入占位 | 与 §3.1 **说明（选填）** 占位一致（用途、备注、助匹配、临时品说明等统写在一处） |
| 主按钮（建议） | 再试一次匹配（有输入时高亮；无输入时可禁用或仍调用由产品定） |
| 次按钮一 | 添加为临时商品 |
| 次按钮二 | 返回修改 |

### 3.6 临时商品二次确认（`TEMP_CONFIRM`）

（首屏 **`analyzeMode=DIRECT_TEMP`** 不再进入本状态：产品在**调 §5.1 前**用弹窗等方式确认即可，接口一次返回 **`SUCCESS`**。）

| 用途 | 文案 |
|------|------|
| 区块标题 | 确认添加临时商品 |
| 说明 | 以下商品将加入您的商品清单，**不在**公司统一目录中，请核对名称与规格。 |
| 若用户曾填写说明 | 展示只读一行：**备注**：{说明正文}（来自 **`tempPreview.goodsFurtherDescription`**） |
| 主按钮 | 确认添加 |
| 次按钮 | 返回 |

### 3.7 成功（`SUCCESS`）

| 用途 | 文案 |
|------|------|
| 标题 | 添加成功 |
| 说明 | 商品已保存，您可以在商品列表中查看或继续添加。 |
| 主按钮 | 完成 |

### 3.8 错误（`ERROR`）

| 用途 | 文案 |
|------|------|
| 通用 | 操作失败，请稍后重试。 |
| 参数不全 | 请填写商品名称和规格。 |

---

## 4. 通用响应包装（与项目 `R` 一致时）

若沿用现有 `R`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | number | 业务码，`0` 表示成功（以项目为准）。 |
| `msg` | string | 提示信息；`flowState=ERROR` 时展示给用户。 |
| `flowState` | string | **§2** 枚举；成功体里必带。 |
| `data` | object | **§5** 中的业务载荷；`ERROR` 时可为 `null` 或含 `errorCode`。 |

**约定**：HTTP 200 仍可能 `flowState=ERROR`（业务失败）；网络层错误按小程序惯例处理。

---

## 5. 接口定义

基础路径示例：**`/ai/goods-add`**（实现类：`GbAiGoodsAddController`）。若服务配置了 `server.servlet.context-path`（如本项目的 **`/api`**），则完整路径为 **`/api/ai/goods-add/analyze`** 与 **`/api/ai/goods-add/confirm`**。

### 5.1 分析 / 匹配（首轮或「修改后再试」）

**`POST /ai/goods-add/analyze`**

**Request JSON**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `sessionId` | string | 否 | 多轮时带上；首轮不传则由后端生成并在响应返回。 |
| `analyzeMode` | string | 否 | `AI`（默认）、`MANUAL_CATALOG`，或临时品直连：**`DIRECT_TEMP`**（等价 **`ADD_TEMP`**、**`TEMP_ONLY`**、**`TEMP_GOODS`**）。手动模式不跑 L1L2/SKU 模型，仅按 `manual*` 逐级返回目录。**临时品直连**：**不请求 DeepSeek**，不跑目录匹配；**§5.1** 一次即落库并 **`flowState=SUCCESS`**（与 **§5.2** `TEMP` 成功体一致），**无需**再调 **§5.2**。若与本表 `confirmedGrandNxGoodsId` 同传，**以临时品直连为准**（忽略扩充目录）。 |
| `manualGreatGrandNxGoodsId` | number | 否 | 手动模式：已选**一级**（`nx_goods_level=0`），用于拉二级。 |
| `manualGrandNxGoodsId` | number | 否 | 手动模式：已选**二级**（`level=1`），用于拉三级品名。 |
| `manualFatherNxGoodsId` | number | 否 | 手动模式：已选**三级品名父**（`level=2`），用于拉四级 SKU。 |
| `goodsName` | string | 是 | 用户填写的商品名称，trim 后非空。 |
| `goodsSpec` | string | 是 | 用户填写的规格（斤/袋/箱等），trim 后非空；若前端合并为一行，可拆入此字段或扩展 `goodsNameRaw`。 |
| `goodsFurtherDescription` | string | 否 | **说明（选填）**：用途、备注、助对照目录、临时品希望带上的说明等**统一写在本字段**（前台标签「说明（选填）」）。参与 L1/L2 与 SKU 两阶段 Prompt，写入会话快照；**§5.2** 确认临时商品时可写入商品详情。带 **`confirmedGrandNxGoodsId`** 扩充目录时若省略则沿用会话内上次值。建议上限约 **500** 字。 |
| `departmentId` | number | 是 | 当前部门，与现有业务一致。 |
| `distributerId` | number | 是 | 批发商 `disId`，落库需要。 |
| `depId` | number | 是 | 部门商品写入侧部门 id（与现有 `saveLinshiGoodsGb` / `createDistributerGoodsFromNxGoods` 入参一致）。 |
| `depFatherId` | number | 否 | 若临时品/分销逻辑需要父部门 id。 |
| `confirmedGrandNxGoodsId` | number | 否 | **仅在**上一步 `flowState=BRANCH_CONFIRM` 且用户已点选某条二级后，与 **同一 `sessionId`** 再次调用 **§5.1** 时传入（值为 `branchOptions[].grandNxGoodsId`）。此时后端不再跑 L1L2/SKU 匹配，而是调用模型生成 **品名父节点（库 `nx_goods_level=2`）+ SKU（`level=3`）** 写入 `nx_goods`，并执行与 `confirmType=NX_CATALOG` 等价的批发商商品创建；成功时 **§5.1 直接返回 `flowState=SUCCESS`**。 |
| `skipCatalogPrefetch` | boolean | 否 | 为 **`true`** 时跳过 **`DIS_CATALOG_*`** 三态预检索，同一 **`sessionId`** 直接进入 DeepSeek。**`analyzeMode=MANUAL_CATALOG`** 或 **`DIRECT_TEMP`** 时本字段无效。 |

**临时品直连（`DIRECT_TEMP` 及其别名）**：与 `AI` / `MANUAL_CATALOG` 相同必填字段（`goodsName`、`goodsSpec`、`distributerId`、`depId`、`departmentId` 等）；`manual*`、`confirmedGrandNxGoodsId` 会被忽略。成功：`flowState=SUCCESS`，`data.persistedGoods` / `data.gbDistributerGoods` / `data.gbDepartmentDisGoods` 与 **§5.2** `confirmType=TEMP` 相同；`data.analyzeMode` 恒为 **`DIRECT_TEMP`**（便于埋点）。失败：`flowState=ERROR`。

**匹配策略（后端）**：默认 **`analyzeMode=AI`** 时，预检索**先**校验 **批发商商品：`gbDgGoodsName` + `gbDgGoodsStandardname`** 与用户 **`goodsName` + `goodsSpec`**（trim 后）**完全一致**；未命中时再校验 **农鑫四级 SKU：`nxGoodsName` + `nx_goods_standardname`** **完全一致**。若命中则 **只返回这一条**目录四级 SKU，`assistantMessage` 为完全一致提示字符串，**不再**做 LIKE / 合并同父兄弟 SKU。若均无完全一致命中，再走：**农鑫名称/别名 LIKE** + **批发商商品名称 LIKE** + **同父兄弟 SKU** → **`DIS_CATALOG_*`**（并带 **`catalogHitComposition`**）；可选 **`skipCatalogPrefetch=true`** 跳过整块预检索。再否则会进入 **两阶段** DeepSeek：**①** 加载一二级目录 → 模型选大类；**②** 拉取用该大类下的 SKU 候选表。**`goodsFurtherDescription`** 参与 Prompt、会话与裁剪。**`MANUAL_CATALOG`** 不跑前述「预检索 + 两阶段」中的模型步骤（仅逐级拉目录）。

**未找到 SKU 时**：若二级已可信但 SKU 仍无结果（含该二级下候选为空），或模型返回 NONE 且**名称与候选 SKU 的文本相近度不足**（避免把「果丹皮」与无关零食品凑成多选），接口返回 **`flowState=BRANCH_CONFIRM`**，并在 `data.branchOptions` 中给出当前归类的**一级/二级**供用户确认；用户确认二级后带 `confirmedGrandNxGoodsId` 再调 **§5.1** 走扩充目录与落库（见上表）。仅当相近度足够高时，后端才用文本回退给出 `MATCH_SINGLE` / `MATCH_CHOICE`。

**Response `data` 建议结构**

| 字段 | 类型 | 说明 |
|------|------|------|
| `sessionId` | string | 会话 id，后续确认、多轮追问必带。 |
| `flowState` | string | 与根级 **`flowState`** 一致（也可仅在根级返回，二选一由项目定，**前后端统一一处即可**）。 |
| `assistantMessage` | string | **给用户看的自然语言摘要**（可选，减轻前端拼文案压力）。 |
| `matchSummary` | object \| null | `MATCH_SINGLE` 时非空：展示用。 |
| `matchSummary.nxGoodsId` | number | 目录商品 id。 |
| `matchSummary.displayName` | string | 展示名称。 |
| `matchSummary.standardName` | string | 目录规格。 |
| `matchSummary.path` | string | 大类路径，如 `新鲜蔬菜 > 番茄瓜茄豆 > 西红柿`。 |
| `nxCatalogConfirmIntents` | array \| null | **`MATCH_SINGLE` / `MATCH_CHOICE` / `MANUAL_CATALOG_SKU_*` / `DIS_CATALOG_*`** 时按需非空。**`DIS_CATALOG_GB_ONLY`** 通常仅 **`ADD_SIBLING_SKU`**；**`DIS_CATALOG_NX_ONLY`** 通常仅 **`USE_MATCHED`**；**`DIS_CATALOG_MIXED`** 两条兼有。每项含 `intent`、`label`。 |
| `catalogLevel1Options` | array \| null | **`NO_MATCH`** 或 **`MANUAL_CATALOG_*`** 某步：一级节点列表，每项含 `nxGoodsId`、`nxGoodsName`、`nxGoodsLevel`、`nxGoodsSort`。 |
| `catalogLevel2Options` | array \| null | 手动模式 `MANUAL_CATALOG_L2` 及之后：当前上下文下的二级列表（或上一步已选二级回显）。 |
| `catalogLevel3Options` | array \| null | 手动模式 `MANUAL_CATALOG_L3` / `MANUAL_CATALOG_SKU_*` / `MANUAL_CATALOG_BRANCH`：三级品名列表等。 |
| `analyzeMode` | string \| null | 非成功体：`AI`、`MANUAL_CATALOG` 或 **`DIRECT_TEMP`**。**`DIRECT_TEMP` 且落库成功**时仅在 **`flowState=SUCCESS`** 的 `data` 中带 `DIRECT_TEMP`（其它成功路径可不含此字段）。 |
| `analyzeModeHint` | string \| null | **`NO_MATCH`** 时提示如何改选手动目录（与 `catalogLevel1Options` 配合）。 |
| `catalogHitComposition` | string \| null | **`DIS_CATALOG_*`**：`GB_ONLY` \| `NX_ONLY` \| `MIXED`，与同次 **`flowState`** 一致。 |
| `candidates` | array \| null | **`MATCH_CHOICE`** 或 **`DIS_CATALOG_*`** 时非空；**`MATCH_CHOICE`** 每项同 `matchSummary` 可加 `score`（number，可选）。**`DIS_CATALOG_*`** 行字段见下一行块。 |
| `disCatalogChoices` | array \| null | **`DIS_CATALOG_*`**：与 **`candidates`** 一致。 |
| **`DIS_CATALOG_*` 行字段** | — | 在 `nxGoodsId`、`displayName`、`standardName`、`path` 外增加：`disImportStatus`、`gbDistributerGoodsId`、`useConfirmApi`（见 §2）。 |
| `tempPreview` | object \| null | `NO_MATCH`、`TEMP_CONFIRM` 或与 **`DIS_CATALOG_*`** 同上态可带临时品摘要。 |
| `tempPreview.goodsName` | string |  |
| `tempPreview.goodsSpec` | string |  |
| `tempPreview.goodsFurtherDescription` | string \| null | 用户本轮已提交的**说明**（与请求字段同源；回显在 `TEMP_CONFIRM` 等）。 |
| `branchOptions` | array \| null | **`BRANCH_CONFIRM`** 时非空：每项含 `greatGrandNxGoodsId`、`greatGrandName`、`grandNxGoodsId`、`grandName`，供用户点选二级。 |
| `gbDistributerGoods` | object \| null | **扩充目录成功**（`§5.1` + `confirmedGrandNxGoodsId`）时非空：新建批发商商品主要字段，与 `GbDistributerGoodsController` 中 `createDistributerGoodsFromNxGoods` 落库结果一致。 |
| `persistedGoods` | object \| null | **`SUCCESS`** 时非空：在 `gbDistributerGoods` 基础上另含 `nxGoodsId`、`nxGoodsFatherNodeId`（新建目录节点），便于跳转。**§5.1** 扩充目录成功时与 `gbDistributerGoods` 同时返回。 |

**`flowState` 与 `data` 组合示例**

- `MATCH_SINGLE`：`matchSummary` 有值，`candidates` 为空。  
- `MATCH_CHOICE`：`candidates.length >= 2`。  
- **`DIS_CATALOG_*`**：`disCatalogChoices` / `candidates` 至少 1 条，`data.catalogHitComposition`（与 **`flowState`** 三合一）。若都不合适：**`skipCatalogPrefetch=true`** + 同一 **`sessionId`** 再调 **§5.1**。
- `NO_MATCH`：`matchSummary` 为空，`tempPreview` 可预填用户输入；**`catalogLevel1Options` 非空**（一级目录供自选）。  
- `MANUAL_CATALOG_L1` / `L2` / `L3` / `BRANCH` / `SKU_*`：见 §2；`analyzeMode` 为 `MANUAL_CATALOG`。  
- `BRANCH_CONFIRM`：`branchOptions` 非空，`matchSummary` / `candidates` 为空，`tempPreview` 可预填。  
- **`SUCCESS`（直连临时）**：请求 `analyzeMode=DIRECT_TEMP` 且落库成功；`persistedGoods` / `gbDistributerGoods` / `gbDepartmentDisGoods` 非空（与 **§5.2** `TEMP` 成功一致），`data.analyzeMode` 为 `DIRECT_TEMP`。  
- `ERROR`：`msg` 说明原因。

---

### 5.2 确认落库

**`POST /ai/goods-add/confirm`**

**Request JSON**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `sessionId` | string | 是 | 与 analyze 返回一致。 |
| `confirmType` | string | 是 | `NX_CATALOG`：按目录商品添加；`TEMP`：按临时商品添加。 |
| `nxCatalogIntent` | string | 否 | **仅** `confirmType=NX_CATALOG`：`USE_MATCHED`（默认）— 直接使用 `nxGoodsId` 对应目录 SKU；`ADD_SIBLING_SKU` — 在该 SKU 所属品名父（nx level=2）下**新建**一条 SKU（nx level=3），名称与规格优先取本请求 `goodsName` / `goodsSpec`，缺省用会话快照中的用户输入。 |
| `nxGoodsId` | number | 条件 | `confirmType=NX_CATALOG` 时必填，且须为本次 `session` 下曾出现过的候选 id（后端校验）。 |
| `goodsName` | string | 条件 | `confirmType=TEMP` 时可必填或与 session 内快照一致；`NX_CATALOG` 且 `nxCatalogIntent=ADD_SIBLING_SKU` 时建议传用户原始名称（可与快照一致）。 |
| `goodsSpec` | string | 条件 | 同上；`ADD_SIBLING_SKU` 时建议传用户原始规格。 |
| `goodsFurtherDescription` | string | 否 | `confirmType=TEMP` 时若有，写入商品详情/备注；缺省用会话快照中的说明。与 analyze 字段同源。 |

**Response**

- 成功：`flowState=SUCCESS`，`data.persistedGoods` 返回新建记录关键字段（如 `gbDistributerGoodsId`、`gbDgGoodsName` 等，与现有表字段对齐）。**`NX_CATALOG` 与 `TEMP`** 成功时均返回 **`data.gbDistributerGoods`**（`toGbDistributerGoodsApiMap` 摘要）与 **`data.gbDepartmentDisGoods`**（部门商品 `gb_department_dis_goods` 摘要，与目录确认一致；若部门行创建失败则为 `null` 且 `persistedGoods` 可能不含 `gbDepartmentDisGoodsId`）。若本次为 **`ADD_SIBLING_SKU`**，`persistedGoods` 另含：`addedSiblingNxSku`（boolean）、`nxGoodsFatherId`（品名父 nx id）、`refNxGoodsId`（用户点选时的参考目录 SKU id）。  
- 失败：`flowState=ERROR`，`msg` 说明原因（会话过期、重复提交、校验失败等）。

---

### 5.3 多轮追问（可选 · 第二版）

若同一 `sessionId` 下允许用户**仅发一句自然语言**补充（不重新填表），可增加：

**`POST /ai/goods-add/message`** — Body：`sessionId` + `userText`。  
响应结构与 **analyze** 相同，仍用 `flowState` 驱动 UI。

**与说明字段的关系**：`NO_MATCH` 下的「说明 + 再试一次匹配」**用同一 `analyze` 接口** 完成，不必单独 `message`；`message` 留给完全类聊天的扩展。

首版若不做 `message`，由用户「返回修改」或「带最新 **`goodsFurtherDescription`** 再次 analyze」即可。

---

## 6. 前端检查清单（实现前对齐）

- [ ] 主路径：**analyze → 根据 `flowState` 渲染 → confirm → SUCCESS**。  
- [ ] 所有请求带登录态 / `token`（与现有小程序一致）。  
- [ ] `MATCH_CHOICE` 未选中时禁用「确认添加」。  
- [ ] `MATCH_SINGLE` / `MATCH_CHOICE`：展示 `nxCatalogConfirmIntents` 两个意图；确认时传 `nxCatalogIntent` + 选中行的 `nxGoodsId`（多选时先选一行再选意图）。  
- [ ] 首屏：`analyzeMode=AI`、`MANUAL_CATALOG`、**`DIRECT_TEMP`** 三个入口；**`DIRECT_TEMP`** 一次 **analyze** 即 **`SUCCESS`**，展示落库结果，**不调 confirm**。手动路径按 `MANUAL_CATALOG_L1`→`L2`→`L3`→`SKU` 或 `MANUAL_CATALOG_BRANCH` 与 **§5.1** `confirmedGrandNxGoodsId` 扩充。  
- [ ] `NO_MATCH`：展示 `catalogLevel1Options`，可引导用户切 `MANUAL_CATALOG` 或同一屏内用返回的一级列表继续操作。  
- [ ] 首屏与 `NO_MATCH`：**同一**可选控件「说明（选填）」，统一映射 **`goodsFurtherDescription`**。  
- [ ] `NO_MATCH`：「再试一次匹配」须带同一 `sessionId` + 最新 `goodsName`/`goodsSpec`/`goodsFurtherDescription`。  
- [ ] `BRANCH_CONFIRM`：展示 `branchOptions`；用户确认二级后再次 **analyze**，带 `sessionId` + `confirmedGrandNxGoodsId`；成功则 `flowState=SUCCESS`（可能不经 **confirm**）。**`goodsFurtherDescription`** 可省略，后端沿用会话；若用户补充可传入覆盖。  
- [ ] `sessionId` 本地缓存至本页关闭或 `SUCCESS`，避免误用旧会话。  
- [ ] 埋点（可选）：`flowState` 曝光、确认点击率、临时品占比、**`goodsFurtherDescription`（说明）** 填写率、二次匹配成功率。

---

## 7. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v0.1 | 2026-04-27 | 初稿：状态、文案、analyze/confirm 字段。 |
| v0.2 | 2026-04-27 | `NO_MATCH` 补充可选用途说明；`userPurpose` 与 analyze/confirm 字段；`tempPreview.userPurpose`。 |
| v0.3 | 2026-04-27 | `nxCatalogIntent`（沿用匹配 SKU / 同品名父下新增 SKU）；`analyze` 返回 `nxCatalogConfirmIntents`。 |
| v0.4 | 2026-04-27 | `analyzeMode=MANUAL_CATALOG` 逐级目录；`NO_MATCH` 返回 `catalogLevel1Options`；`MANUAL_CATALOG_*` 状态机。 |
| v0.5 | 2026-04-27 | **`analyzeMode=DIRECT_TEMP`**：初版文档（后经 v0.6 改为一次落库 `SUCCESS`）。 |
| v0.6 | 2026-04-27 | **`DIRECT_TEMP`**：**§5.1** 当场落库并 **`SUCCESS`**，与 **§5.2** `TEMP` 成功体一致，无需 confirm。 |
| v0.7 | 2026-04-27 | 临时品直连：**优先于** `confirmedGrandNxGoodsId`；明确 **不调 DeepSeek**；`analyzeMode` 别名 `ADD_TEMP` / `TEMP_ONLY` / `TEMP_GOODS`。 |
| v0.8 | 2026-04-28 | **`goodsFurtherDescription`**：可选「商品进一步描述」，助模型选一二级；与 `userPurpose` 区分；写入会话；扩充目录时可沿用或覆盖。§3.1 / §5.1 / §6 同步。 |
| v0.9 | 2026-04-28 | 产品收束为**单一说明字段**：前台标签「说明（选填）」，接口仍用 **`goodsFurtherDescription`**；`tempPreview` 以 **`goodsFurtherDescription`** 回显；§5.2 `confirm` 增加同字段说明。 |
| v0.10 | 2026-04-28 | 移除 **`userPurpose`**：请求体、会话快照与实现仅保留 **`goodsFurtherDescription`**。 |
| v0.11 | 2026-04-28 | **`BRANCH_CONFIRM`**：`assistantMessage` 固定为口语化引导（覆盖模型 userFacingSummary）；§3.5a 与之一致。 |

---

**下一步**：与产品/前端确认文案与枚举；联调时保证模型 JSON 与 **`nxGoodsId`** 校验规则一致（无库内候选表时由模型直接给 id）。
