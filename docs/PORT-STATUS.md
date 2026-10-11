# 功能移植状态（阶段 1 已建立可复算基线）

> 本文件是**功能移植（Milestone A）**的活台账。外观层已完成，见 `VERIFY-2026-10-08.md`。
> 所有路径相对工作区 `D:\work`。**这里的数字就是进度**：下一步要看的是它下降。

---

## 1. 流水线（三步，都可复算）

```powershell
$env:JAVA_HOME='D:\jdk-17-toolchain\PFiles64\Microsoft\jdk-17.0.13.11-hotspot'
$env:GRADLE_USER_HOME='D:\gradle-home'
Set-Location D:\work\port-1.20.1
$py='D:\Python311\python.exe'

# ① 机械改名（登记表 + 幂等自检 + 零命中点名）
& $py tools\port_rename.py --scan            # 只看命中面
& $py tools\port_rename.py --apply           # 复制到 stage1-src 并改名
& $py tools\port_rename.py --scan --fault    # 负例：拼错一条规则必须被点名

# ② 让 javac 说话（对 1.20.1 的官方名类路径编译 stage1-src）
Set-Location ncpb-1.20.1
.\gradlew.bat dumpCompileClasspath --offline --no-daemon --console=plain
Set-Location ..
#    （命令见 §1.1；注意 javac 的 -J 参数必须**逐个加引号**，否则 PowerShell 会拆坏）

# ③ 分类台账
& $py tools\compile_ledger.py --top 20
```

### 1.1 javac 那条命令（照抄）

```powershell
$javac='D:\jdk-17-toolchain\PFiles64\Microsoft\jdk-17.0.13.11-hotspot\bin\javac.exe'
$out='D:\work\port-1.20.1\_port-compile'
$cp=(Get-Content 'D:\work\port-1.20.1\ncpb-1.20.1\build\compile-classpath.txt' -Raw) -split "`r?`n" | Where-Object { $_ }
Get-ChildItem 'D:\work\port-1.20.1\stage1-src' -Recurse -Filter '*.java' |
    ForEach-Object { $_.FullName } | Set-Content (Join-Path $out 'files.txt') -Encoding ascii
& $javac '-J-Duser.language=en' '-J-Duser.country=US' -encoding UTF-8 -nowarn -proc:none `
    -Xmaxerrs 200000 -d (Join-Path $out 'classes') -cp ($cp -join ';') "@$(Join-Path $out 'files.txt')" `
    2>&1 | Set-Content (Join-Path $out 'errors-en.txt') -Encoding UTF8
```

> **为什么强制英文 locale**：台账按英文 `error:` 解析。中文 locale 下同一批错误完全解析不出来
> （实测：中文那轮 7465 行**一条都没解析成功**）。
> 另：`-J` 参数写成 `-J-Duser.language=en` 会被 PowerShell 拆成两段（报 `invalid flag: .language=en`），
> 必须 `'-J-Duser.language=en'` 整体加引号。

---

## 2. 基线

| 指标 | 23（能力类型） | 24（物品属性） | 25（垫片回调修正） |
| --- | --- | --- | --- |
| 机械改名 | 197/1732 | 197/1734 | **197/1736** |
| **错误总数** | 453 | 447 | **425** |
| **在范围内** | 304 | 298 | **276** |
| 已登记延后 | 149 | 149 | 149 |
| **主类 `NetMusicCanPlayBili`** | **0** ✅ | 0 | 0 |
| `ModItems` | 15 | **9** | 9 |
| `PadItem` | 11 | 11 | **7** |
| `HolographicGlassesItem` | 7 | 7 | **3** |

> **在范围内 1468 → 193（−86.9%）**。
>
> ⚠️ 第 20 次**在数字上是零**：它把 44 处 1.21 专有 API 换成 1.20.1 的写法
> （迟早都要改、且没让指标变差），但那些行同时还有别的错，所以计数不动。**不记为进展。**

### 3.18 第 16 次：注册 API（A2 的入口）

NeoForge 的便捷注册 API 在 Forge 侧不存在，已改成带泛型的写法：

| NeoForge | Forge 1.20.1 |
| --- | --- |
| `DeferredRegister.Items` / `DeferredRegister.Blocks`（嵌套子类） | `DeferredRegister<Item>` / `DeferredRegister<Block>` |
| `DeferredRegister.createItems(modid)` | `DeferredRegister.create(Registries.ITEM, modid)` |
| `DeferredRegister.createBlocks(modid)` | `DeferredRegister.create(Registries.BLOCK, modid)` |
| `DeferredBlock<T>` / `DeferredItem<T>` | `RegistryObject<T>` |
| `EquipmentSlotGroup.X`（1.20.5+） | `EquipmentSlot.X` |

产出核对（ModItems / ModBlocks）：
```java
public static final DeferredRegister<Item>  ITEMS  = DeferredRegister.create(Registries.ITEM,  "net_music_can_play_bili");
public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "net_music_can_play_bili");
```

**效果**：`ModItems` 20 → 15 条，`ModBlocks` 10 → 6 条。

### 3.20 下一个靶子：5 个「包处理器作用域」Mixin（`*SubLevelScopeMixin`）——含一个**必须先想清楚**的冲突

这 5 个 mixin（`ModernTurntableControlPacket` / `LyricProjectorConfigPacket` / `VideoProjectorConfigPacket` /
`SpeakerConfigPacket` / `ControlConsoleConfigPacket` 各一）`@Inject` 进我们的包的 `handle` 方法：

```java
@Inject(method = "handle", at = @At("HEAD"))
private static void ncpb$pushNetworkScope(XxxPacket payload, IPayloadContext context, CallbackInfo callback) {
    ServerPlayer player = context.getSender();
    ...
}
```

NeoForge 的 `handle` 第二参是 `IPayloadContext`；Forge 侧已经改成
`Supplier<NetworkEvent.Context> contextSupplier`，所以注入器签名也得跟着改：

| 现在 | 目标 |
| --- | --- |
| `IPayloadContext context` | `Supplier<NetworkEvent.Context> contextSupplier` |
| `context.getSender()` | `contextSupplier.get().getSender()` |

#### ⚠️ 为什么不能直接加两条全局规则

`context.getSender()` 在**已经迁移好的包文件里也是正确写法**
（codemod 注入了 `NetworkEvent.Context context = contextSupplier.get();`，那里的 `context` 就是
`NetworkEvent.Context`）。全局把 `context.getSender()` 换成 `contextSupplier.get().getSender()`
会**把那些正确的调用改坏**。

所以需要一个「**只在 mixin 包里生效**」的作用域 —— 现有 `port_rename.py` 是逐文件正则、
**没有文件级条件**，因此：

* 要么给规则表加一个「限定路径前缀」的字段（推荐，通用）；
* 要么为这 5 个文件单独写一个小 codemod（像 `port_network_codemod.py` 那样）；
* 要么把这 5 个文件放进 `tools/port-manual/`。

#### ✅ 已解决（第 19 次）：给规则表加了**路径作用域**（第 7 项字段）

推荐方案 1 落地了：规则元组可以带第 7 项「路径限定前缀」，`apply_rules(text, rules, rel_path)`
与 `scan` 都会先过 `scope_ok(scope, rel)` 再决定要不要应用。

```python
('mixin: 注入器参数类型', r'\bIPayloadContext (\w+)', r'Supplier<NetworkEvent.Context> \1Supplier',
 'safe', '...', ['java.util.function.Supplier', 'net.minecraftforge.network.NetworkEvent'], '/mixin/')
#                                                                                        ^^^^^^^^ 作用域
```

**效果：8 个 `*SubLevelScopeMixin` 从 15 条 → 0 条；`network/` 里 context 相关错误 0 条**（没被误伤）。

> ⚠️ 排查过程中的一个**假警报**值得记：我当时在 `network/` 里看到 21 处
> `contextSupplier.get().getSender()`，怀疑作用域漏了。实际核完代码（`scope_ok` 对
> `network/` 返回 False）并用 javac 验证后确认**没有漏** —— 那 21 处本来就存在且合法。
> **"看到可疑现象"之后要先验证，别急着改**；这次的判据是"编译后 `network/` 的 context 错误为 0"。



### 3.19 ✅ 已解决：netmusic 1.20.1 的 `MusicToClientMessage` **没有任何访问器**
































































### 3.86 第 85 次：🔴🔴 **最终结论 —— "1 错误"从头到尾都是中止点，真实状态一直是 ~147**

#### 决定性的一步

我把"只去掉 `VideoPlaybackPresentation` 这一条 exclude"（**精确替换，行数 12 → 11**，
上次的过滤器误删了别的行、那才是更早那次 149 的真因）后再构建：

```
exclude 行数 12 -> 11（只减 1）
错误 149 条
block/ 报错 7 条            ← javac 跑完了整轮
ControlConsoleBlock 报错 4 条
我的桩 VideoPlaybackPresentation.java 报错 0 条   ← **桩本身是对的**
```

⇒ **我的桩没错**。它一进编译，**中止原因消失了**，javac 于是跑完整轮、报出 **149 条真实错误**。

#### 因此必须撤回整条"进度曲线"

之前报的 `147 → 78 → 71 → 37 → 20 → 13 → 2 → 1` ——
**后面那些数字全都是"javac 中止在第几个错误"的表现，不是剩余工作量**。
排除清单**确实**消掉了被排除文件自身的错误，但**只要还剩一个错误，javac 就中止**，
于是**报出来的数只反映"第一个拦路虎在第几行"，与总量无关**。

> **真实状态：不做排除 = 147；带 11 条排除 + 桩 = 149。两者是同一量级** ——
> **路径 B 并没有把我们带到"快出 jar"的地方。我把"B 快到了"这个说法彻底撤回。**

#### 这也解释了之前一堆"怪现象"

* 为什么"去掉一个 exclude 就炸到 149" —— 因为它解除了中止；
* 为什么 149 ≈ 无排除的 147 —— 因为**它们本来就是同一个数**；
* 为什么第 83 次那个桩"看起来把数字弄坏了" —— 其实它**修好了一个真错误**，只是同时揭开了后面 148 条。

#### 现在有效的门禁与口径

* `build_full.ps1` 的**金丝雀**（`ControlConsoleBlock` 必须报错）已能识别"中止 ⇒ 数不可用"；
  本次它**没有**报警 ⇒ **149 是一个有效读数**；
* **从现在起**：读数有效 ⇒ 说明 javac 跑完 ⇒ 这个数才可比；
* **真正的下一步**：不再靠"排除"制造好看的数，而是**直面 147–149 条**。
  优先处理**功能路径**（`block/` 的 `MapCodec`、`network`、`blockentity`、`item`、注册类），
  渲染/视频层继续用"叶子排除 + 桩"控制，但**报数一律用有效口径**。
### 3.85 第 84 次：🔴 **撤回"1 错误"** —— 它是 javac 提前中止造成的截断；已加**金丝雀**门禁

#### 怎么发现的

上一轮那个 149 的怪现象逼我去查，得到一个**决定性证据**：

```
ControlConsoleBlock.java 里明明还有  MapCodec / simpleCodec   （1.21 专有，1.20.1 不可能有）
但"1 错误"那次构建里，block/ 的报错 = 0 条
```

**一个含 1.21 专有 API 的文件不可能编译通过** ⇒ 它没被报错，只能说明**javac 提前中止了**。

#### 对照实验（决定性）

| 状态 | 错误 | block/ 报错 |
| --- | --- | --- |
| **无任何 exclude** | **147** | **7 条** |
| 12 条 exclude | "1" | 0 条 |

⇒ **147 才是可信的当前状态**；"1 / 2 / 13 / 20 / 37…"这些**后期数字随 javac 的中止行为而变**，
**不能当作进度**。

> 这是**第三次**踩同一族坑：
> 第 47 次（离线规则产出残缺 `}`）、第 78 次（build.gradle 改坏）、这次（排除清单让 javac 提前中止）。
> **共同点：报错数被"编译没跑完"污染。**

#### 补上的**金丝雀**判据（已进 `tools/build_full.ps1`）

选 `block/ControlConsoleBlock.java` 当金丝雀 ——
**它含 1.21 专有的 `MapCodec`/`simpleCodec`，在 1.20.1 上必然报错**。
于是：只要它在编译范围内，日志里就**必须**出现它的报错；否则 ⇒ **总数被截断，退出 2、明确说不可用**。

**负例已验证**：恢复 12 条 exclude 后重跑 → 打印
`!! 金丝雀缺失 … ⇒ 总数被截断，不可用`，**exit 2**。

#### 现在的诚实状态

* **可信口径：不做任何 exclude 时 = 147 错误**；
* 排除清单**确实能消掉被排除文件自身的错误**，但一旦剩余错误变少，
  javac 会提前中止 ⇒ **报出来的数不可用**（金丝雀现在能识别这一点）；
* 因此"路径 B 已经快到了"这个结论**我撤回**。B 的真实进度需要用别的方式度量
  （例如：**以"能否出 jar"为判据**，而不是以错误数）。
### 3.84 第 83 次：桩没成 —— **写 `VideoPlaybackPresentation` 桩反而炸到 149**，已回退

按"第三条"给 `VideoPlaybackPresentation` 写了个最小桩（签名逐条抄自原文件：
6 个 `ProjectorFrameSnapshot` 快照方法 + `submit` / `restartAllowed` / `renderYuvImmediate` / `isWithinAudioRange`），
并从排除清单里取出它。**结果：1 → 149。**

**诡异之处**：报错根本不是我的桩，而是 **`block/` 包里的 `MapCodec` / `simpleCodec`**
（那批从 184 基线就有的 1.21 缺口）—— 也就是**回到了"什么都没排除"的样子**。

我把它的排除放回去、重跑：**又回到 1 错误，可复现**。

⇒ **现象已确认、原因未查明**。可能的方向：
① 我那次改 `build.gradle` 的脚本（用 `rfind('        }')` 定位插入点）**动错了位置**，
导致 sourceSets 结构在某次构建里没生效（但文件内容看起来是好的）；
② 或者"包含该文件"会让 javac 的**编译顺序/错误归属**变化，把前面隐藏的错误全暴露出来。

**下一步必须先查清 ②**：如果"包含它"确实会暴露 `block/` 包的真实错误，那说明
**之前那个"1 错误"是假象**（错误被部分屏蔽），这一点比多消一个错误重要得多。

> 又一条纪律的味道：**"数字突然变好"和"数字突然变坏"一样，都要先怀疑测量。**
### 3.83 第 81–82 次：**只剩 1 个错误**（离出 jar 一步）+ 用户定了 NeoBackports 的用法

#### 用户给了 NeoBackports（`neobackports-1.4.2.jar`）并指示"**走第三条**"

"第三条"= **学它的思路、自己实现**；**不加依赖**、**不抄它的源码**。
（已核实：仓库 `Nikdo53/NeoBackports`，公开，**LGPL-2.1**，Forge `[47.1.3,)`、loader `[47,)`，与我们的 1.20.1/47.4.26 兼容。
抄源码会构成演绎作品 ⇒ 有 LGPL 义务；只学做法无义务。）

**它对我最有价值的校验**：它的 `io/components/CustomData` 公开面与我手写的垫片
（`of` / `EMPTY` / `itemMatcher` / `matchedBy` / 两个 `update` / `set`）**一一对应** ——
说明我的垫片签名是对的；差别在实现层（它用**真组件框架**，我用 NBT 直存）。
它的 `io/attachment/AttachmentType` + `DataAttachmentRegistry` 正是我**未完成项①**缺的那套附件 API 的范式。

#### 一路把错误数压到 **1**

| 做法 | 错误 |
| --- | --- |
| 13 个**叶子**文件排除 + 枢纽类放回 | 2 |
| **只**放回 `VideoBillboardUploadSupport` | **1** |
| 放回 `VideoPlaybackPresentation`（单独）| 149 ⇒ **证实它就是那片的根**，已回退 |

**"一次只放回一个"是关键**：上次一次放回两个 ⇒ 149，我误以为两个都拉大网；
拆开试才发现**只有 `VideoPlaybackPresentation` 是根**，`VideoBillboardUploadSupport` 放回是净赚。

#### 最后一个错误与它的修法

`VideoPlaybackInstance` 用了被排除的 `VideoPlaybackPresentation`（12 处调用）。
按"第三条"，**下一步：自己写一个 `VideoPlaybackPresentation` 的最小桩**
（同包同名、只留被调用到的成员），这正是 `ByteBufferBuilder`/`MeshData` 那招的延续。
### 3.82 第 80 次：放宽工具判据，**20 → 13**

上一轮查明 `strip_all_deferred_refs.py` 清不掉那 20 条，因为它是按
"`程序包 X 不存在`"识别的 —— **但缺 import 目标时 javac 报的是「找不到符号」**，
两种消息都有。只认前者会漏掉一大半（实测漏掉 4 个文件的全部 import）。

放宽判据（`不存在` **或** `找不到符号`）后：识别出 4 个文件、5 个类名，
**清理 3 个文件、跳过 1 个**，**20 → 13**。

#### 被跳过的那 1 个正是"设计要它拒绝"的情况

`ItemInHandRendererMixin.java` 里是：

```java
MP4ItemScreenRenderer.renderMapLike(
PadItemScreenRenderer.renderMapLike(
```

—— 不是**独立语句**（是跨行表达式的一部分），工具**拒绝写盘并把它列出来**。
**这就是我给它定的规则：宁可不改，也不乱改。** 剩下这 2 条要人工处理。

| 状态 | 全量错误 |
| --- | --- |
| 不做任何排除 | 147 |
| 14 个叶子文件 | 20 |
| 放宽判据后清掉 3 个文件的引用 | **13** |
### 3.81 第 79 次：**"多排除几个"再次让数字变差（20 → 47）** —— 规律第二次被验证

剩下 20 条全是"保留文件引用了被排除的类"。我看其中几个引用者（`VideoBillboardState`、
`VideoCandidateDecodeRunner`、`VideoPlaybackCloser`、`MP4HandheldVideoClient`、`ItemInHandRendererMixin`）
**自己也是延后视频代码**，就想"把它们也排除掉，引用关系就一并消失了"。

**实测：20 → 47，更差。**

原因和上次一样：**排除一个文件并不消灭错误，只是把错误搬给了引用它的文件** ——
而被搬过去的那批文件本身又有很多引用者。

⇒ **回退到 14 个文件的清单，回到 20。**

#### 剩下的 20 条为什么 `strip_all_deferred_refs.py` 也清不掉

工具是按"**import 行**"识别副作用的；而这 20 条是**类型用法**，例如：

```java
private final VideoPlaybackInstance owner;        // ← 没有 import 行
VideoCandidateDecodeRunner(VideoPlaybackInstance owner) { … }
```

它们要么靠同包可见、要么靠通配 import，所以**日志里没有可摘取的 import 行** ⇒ 工具选不中。
下一轮要么扩展工具（从 `符号:`/`位置:` 行反推类型名），要么直接手改这几个文件。

#### 规律（现已两次验证）

> **在"实现有缺口"的文件上，排除法是"零和"的**：
> 它消掉自己的 N 条，却给引用者添上 M 条。只有 M < N 时净收益为正 ——
> 所以**该排除的是"引用者少的叶子文件"**，而视频/渲染层恰恰是"引用者多"的枢纽。

| 状态 | 全量错误 |
| --- | --- |
| 不做任何排除 | 147 |
| 14 个文件（叶子）| **20** |
| 20 个文件（+ 视频枢纽）| 47（更差，已回退）|
### 3.80 第 78 次：⚠️ **"0 错误"假绿** —— 我改坏了 `build.gradle`，台账却报全绿

想把最后几个延后渲染器也排除掉，我用脚本**追加** exclude 行，逻辑写错：
把文件末尾的 `}` 去掉后又拼了一段，结果 `sourceSets` 块结构**被破坏**。

于是 gradle **在编译之前**就失败：

```
Could not compile build file '…\build.gradle'.
> startup failed: 186: Unexpected input: '{\n    main {\n        '
```

**但台账报的是「错误 0 条」** —— 因为日志里一条 `.java:` 都没有。
**"没有编译错误"被读成了"全绿"。**

#### 补上的硬判据（已进 `tools/build_full.ps1`）

> **构建失败 + 日志里 0 条编译诊断 ⇒ 数字不可用（exit 2）**，并打印 `What went wrong`
> 的前几行。绝不允许"编译都没跑起来"被当成"0 错误"。

这正是那条一直在强调的纪律：**抓不到错的检查不算检查** ——
而这次是**我自己的台账**抓不到错。

#### 修正后的真实状态

`build.gradle` 重写为合法结构（14 条 exclude），重跑：**20 错误**。

| 状态 | 全量错误 |
| --- | --- |
| 不做任何排除 | 147 |
| 精确排除 26 个延后文件 | 78 |
| 清掉枢纽文件的引用 | 71 |
| 把 5 个 shim 放回编译 | 37 |
| re-derive 后排除 14 个 + 修结构 | **20** |
### 3.79 第 77 次：**把 5 个"后来已被修好"的 shim 放回编译，71 → 37**

新工具 `tools/strip_all_deferred_refs.py`：从全量日志里找出"引用了被排除类"的文件。
干跑一看：**13 个文件只引用了 5 个类**，而且**全是我们自己的垫片**：

```
PortSubmitNodeCollector  PortWorldRenderEvents  PortDebugRenderTypes
PortGuiFramebufferBlitter  PortGuiTextures
```

⇒ **逐个文件"剥离引用"是笨办法** —— 这 5 个类是我们的抽象层，把它们**放回编译**才是对的。
一试（从 exclude 清单里删掉这 5 行）：**71 → 37**（−34），
它们自身的 API 缺口**已经被前几轮的规则修掉了**。

#### 这里有个和上一轮**方向相反**的教训

* 上一轮我犯的错是：**用当前日志重算排除集** ⇒ 自擦除、震荡（7/27/68 假象）；
* 这一轮的发现是：**排除清单也要定期"反算"** ——
  被排除的文件**不参与编译**，所以它"后来有没有被修好"**从日志里看不出来**。
  我第 72 次排除它们时它们确实有缺口；到第 77 次，规则已经把缺口补上了，
  **可它们还是被关着**，于是保留代码一直在报"程序包不存在"。

⇒ **正确做法**：排除清单要**从"无 exclude 的基线"重新推导**（不是从当前日志推），
推完再问一句"里面有没有其实已经能编过的"。

#### 自洽数字

| 状态 | 全量错误 |
| --- | --- |
| 不做任何排除 | 147 |
| 精确排除 26 个延后文件 | 78 |
| 清掉枢纽文件的引用 | 71 |
| **把 5 个 shim 放回编译** | **37** |
### 3.78 第 76 次：清"副作用"有了可复用的安全工具，**78 → 71**

上一轮把 B 的数字定为 **78**（精确排除 26 个延后文件后的自洽值）。
这 78 里 **17 条是"程序包 shim 不存在"**、其余是保留文件里对被排除类的引用。

先拿**功能枢纽**开刀：`ModernTurntableClientEvents`（注册入口，A1/A2 靠它）。
它那 7 条错**全是 import 被排除的渲染类**，而真正用到它们的是 6 条独立语句：

```java
modEventBus.addListener(CuriosHeadGearLayer::register);
PortPictureInPictureRenderer.registerPipRenderer(HolographicPreviewPipRenderState.class, …);
modEventBus.addListener(YuvVideoRenderTypes::registerPipelines);
event.registerBlockEntityRenderer(…CONTROL_CONSOLE.get(), ControlConsoleRenderer::new);
MP4ItemScreenRenderer.warmup();
PadItemScreenRenderer.warmup();
```

#### 新工具：`tools/strip_deferred_refs.py`

按类名删两类东西：① `import …X;` 行；② **整行**引用 X 的独立语句。
**关键设计**：如果某行引用了 X 但**不是独立语句**（是复合表达式的一部分），它**不写盘**、
把那些行列出来让人看 —— **宁可不改，也不乱改**。

用法（先 `--scan` 看，再执行）：

```
python tools/strip_deferred_refs.py --file <java> --scan --class A --class B …
```

对 `ModernTurntableClientEvents` 扫描出的 14 行（8 import + 6 语句）**全部是干净可删**，
无 risky 行 ⇒ 应用。**78 → 71。**

> 副作用清理现在有了可复用、带"拒绝"能力的做法，剩下的文件可以照这个模式逐个过。
### 3.77 第 75 次：🔴 **我自己制造了一个"不稳定测量"，已更正**

#### 我做了什么错事

上一轮"精确到文件"后拿到 82。接着我想再收紧，就反复跑
`narrow_excludes.py`（用**当前**构建日志重算要排除哪些文件）+ 重新构建。结果数字乱跳：

```
23 个 exclude -> 82
12 个 exclude -> 27
23 个（取并集）-> 55
 6 个 -> 7
 4 个 -> 11
 5 个 -> 68
```

#### 真因：**"用当前日志重算排除集"是不自洽的**

**被 exclude 的文件根本不参与编译，它的错误就不会出现在日志里。**
所以每跑一轮，上一轮被排除的文件就会"看起来没问题"而被**放回**；放回后它的错误又冒出来。
⇒ 这个迭代**不收敛**，只会来回震荡。数字 7 / 27 / 68 全是**假象**。

> 这和之前"增量编译"那次是**同一类错误**：**测量的输入依赖于被测量的东西**。
> 上次是"只编了一部分"，这次是"只看得见没被排除的那部分"。

#### 正确做法（已改用）

1. **先取"无任何 exclude"的干净基线** —— 实测 **147**；
2. 从这份基线里算出"真的有缺口"的延后文件 —— **26 个**；
3. 用这份清单**固定住**，再构建 —— 实测 **78**。

#### 自洽的数字

| 状态 | 全量错误 |
| --- | --- |
| 不做任何排除（全编译）| **147** |
| 精确排除 26 个延后文件 | **78** |

**78 才是 B 路线的可信数字**（7 / 27 / 55 / 68 都是震荡产物，作废）。

> 又一条纪律：**任何"按当前结果再筛一遍"的循环，都要先问"被筛掉的东西还会出现在输入里吗"。**
### 3.76 第 74 次：把砍的范围**精确到文件**，**114 → 82（−32）**

上一轮发现"114 里 82 条是自造副作用"之后，做法就明确了：
**问题不在"砍得不干净"，而在"砍得太宽"** —— 整包 glob 把**本来编得过**的类也砍了，
保留代码引用它们就报"程序包不存在"。

新增 `tools/narrow_excludes.py`：从全量日志里找出**真的有 1.21 API 缺口的文件**，
只 exclude 那些。结果：**23 个文件**需要移出（其余同包文件全部**留回编译**）。

**114 → 82。**

#### 移出的 23 个文件（**这就是延后层的精确清单**，可恢复）

```
client/terrain/TerrainPreviewSectionCompiler      client/renderer/gui/HolographicPreviewPipRenderer
client/renderer/video/VideoBillboardPreview       client/renderer/item/PadMapGuiLayer
port/shim/PortGuiFramebufferBlitter               port/shim/PortGuiTextures
port/shim/PortDebugRenderTypes                    port/shim/PortSubmitNodeCollector
port/shim/PortPictureInPictureRenderer            client/renderer/item/MP4OffscreenGuiRenderer
client/renderer/item/PadOffscreenGuiRenderer      client/renderer/video/YuvVideoRenderTypes
client/renderer/video/VideoBillboardQuadSupport   client/renderer/gui/TerrainPreviewGpuCache
client/debug/PlaybackRangeDebugRenderer           client/debug/VideoPlaybackDebugRenderer
client/renderer/video/VideoBillboardGeometrySupport  port/shim/PortWorldRenderEvents
client/renderer/item/CuriosHeadGearLayer          client/renderer/item/MP4ItemScreenRenderer
client/renderer/item/PadMapLayerTexture           client/renderer/item/PadItemScreenRenderer
client/terrain/TerrainHardRangeBounds
```

> **教训**：**"排除一个包"比"排除几个文件"危险得多** ——
> 包里有编得过的类时，你会同时得到"实现没了"和"引用它的代码全报错"两份损失，
> 而且后者看起来像"移植缺口"，会把人骗向错误的方向（我上一轮就差点去写 20 个桩）。
### 3.75 第 73 次：砍完之后 **114 的构成变了** —— 82 条是"砍出来的副作用"

移出编译后重新分桶，形状**完全不同**了：

| 消息种类 | 条数 |
| --- | --- |
| **`程序包 com.zhongbai233.net_music_can_play_bili.… 不存在`** | **82** |
| `找不到符号` | 19 |
| `程序包 VideoBillboardState 不存在` 等 | 6 / 4 / 2 |
| mixin 目标警告 | 1 |

⇒ **82 条不是"原来的 1.21 API 缺口"，而是"保留代码还在引用被我移出去的那些类"**（副作用）。
剩下的真缺口只有 ~20 条。

#### 副作用清单（**这就是 B 的下一步工作项**）

保留代码引用、但已被移出的类（按引用次数）：

```
15 VideoBillboardPreview      9 VideoBillboardState      8 PortSubmitNodeCollector
 4 MP4ItemScreenRenderer      4 PadItemScreenRenderer   4 VideoCloseDiagnostics
 4 TerrainPreviewManager      3 PortWorldRenderEvents   3 PortDebugRenderTypes
 3 PortGuiTextures            2 VideoPipelineProperties 2 VideoZombieCloseSupervisor
 2 VideoFallbackReason        2 IrisShaderpackCompat    2 HolographicPreviewPipRenderState
 2 HolographicPreviewPipRenderer   2 VideoSurfacePrivacyPolicy
 1 VideoVisualSyncPolicy      1 VideoPerformanceFallbackPolicy   1 VideoPerformanceMonitor
```

#### 下一步（两条，任一即可，我倾向 (a)）

* **(a) 补"桩包"**：为这 ~20 个类型各写一个最小桩（同包同名、只留被引用到的成员），
  放在**不被 exclude 的新目录**里。这样既保住砍掉的实现，又让保留代码编得过 ——
  正是 `ByteBufferBuilder`/`MeshData` 那招的延续；
* **(b) 挑几个"不那么烂"的移回来**（如 `client/renderer/video/**` 自身约 14 条错，
  却造成约 24 条副作用）—— 但净收益不高，不如 (a)。

> **必须说清**：现在这 114 里**大部分是"我自己制造的可修复副作用"**，
> 而不是"移植本身还剩这么多"。数字变好看是因为砍掉了实现，**不是因为移植完成了**。
### 3.74 第 72 次：**用户选定路线 B** —— 砍掉延后渲染层，**147 → 114**

用户回答「b」⇒ 采用"最快到可测试"：把**已登记为延后的渲染层**整棵移出编译，
再对"保留代码真正引用到的少数类型"补最小桩。

#### 🔴 本轮移出编译的清单（**逐条登记，不是静默跳过**）

写进 `ncpb-assembled/build.gradle` 的 `sourceSets.main.java.exclude`：

| 移出的子树 | 为什么 |
| --- | --- |
| `port/shim/**` | 移植垫片层，本身是渲染/窗口抽象 |
| `client/terrain/**` | 地形预览 |
| `client/renderer/gui/**` | 离屏/画中画 GUI |
| `client/renderer/video/**` | 视频广告牌与 YUV 渲染 |
| `client/renderer/item/**` | 手持物品界面渲染 |
| `client/renderer/debug/**` | 调试绘制 |

**保留**：`client/renderer/` **根下的方块实体渲染器**（方块/物品注册与显示要用）。

#### 数字（全量口径）

| 步骤 | 错误 |
| --- | --- |
| 砍之前 | 147 |
| 砍 `port/shim` + `client/terrain` | 136 |
| 再砍 `renderer/{gui,video,item,debug}` | **114** |

#### ⚠️ 恢复方式（写清楚，免得以后找不到）

把 `build.gradle` 里那段 `NCPB-DEFERRED-CUT` 的 `exclude` 行**删掉**即可让它们重新参与编译；
但**先要解决它们自身的 1.21 API 缺口**（`vertex(Pose…)`、`Tesselator`、`PortGui*` 等）。
### 3.73 第 71 次：查清了 `VertexConsumer` 的真名 —— **149 → 147（只有 −2，不如预期）**

上一轮那个"报错与代码对不上"的矛盾，`javap` 查清了。1.20.1 的
`com.mojang.blaze3d.vertex.VertexConsumer` **根本没有 `addVertex` / `setNormal`**，它的方法是：

```
vertex(float,float,float) / vertex(double,double,double) / vertex(Matrix4f,float,float,float)
normal(float,float,float) / normal(Matrix3f,float,float,float)
putBulkData(PoseStack$Pose, BakedQuad, …)
```

⇒ **1.21 把 `vertex`→`addVertex`、`normal`→`setNormal` 改了名。** 两条规则（命中 30 / 13），
`PoseStack.Pose` 那个 import 本来就已经是对的，**矛盾解释清楚了**。

#### 但**只降了 2 条**（149 → 147），与"30 处改名"不成比例

如实说明：改名本身是对的（那些方法名确实不存在），但**取 `Pose` 的那批并没有因此通过** ——
因为 1.20.1 根本没有 `vertex(PoseStack.Pose, float,float,float)` 这个重载
（只有 `putBulkData(PoseStack$Pose, BakedQuad, …)`）。所以那批从"方法名不对"
变成了"参数类型不对"，**条数没消失**。

⇒ 下一轮要处理的是：**拿 `Pose` 调 `vertex(…)` 的地方该改成什么**
（大概是 `vertex(pose.pose(), x, y, z)` —— `Pose` 里有 `pose()` 取 `Matrix4f`；
但要先 `javap` 确认 `PoseStack$Pose` 有没有 `pose()`，不再猜）。

> 这一轮的价值是**把"矛盾"查成了"确凿的 API 变更"**，并且**没有把 −2 说成大进展**。
### 3.72 第 70 次：`addVertex` 那 28 条**先不动** —— 我查到的与报错对不上

想把 `addVertex` 那批（`(Matrix4f,…)` 16 + `(Pose,…)` 12）当"改名"吃掉，先去看调用点，结果**发现矛盾**：

```java
// 调用点（两种形状）
buffer.addVertex(pose, x, y, z)                    // pose 是 Pose
builder.addVertex(matrix, left, bottom, 0.0F)      // matrix 是 Matrix4f
```

```java
// 而文件里**已经**是正确的 1.20.1 import（说明早先的规则已经改过类型名）
import com.mojang.blaze3d.vertex.PoseStack.Pose;
```

也就是说：`Pose` 这个名字在 1.20.1 就是 `PoseStack.Pose`，**已经对了**，可 javac 仍报
`找不到符号 方法 addVertex(Pose,float,float,float)`。

**这只有两种解释**：① 1.20.1 的 `VertexConsumer` 根本没有这两个重载；
② 报错来自**另一层**（某个我们自己的垫片类/接口）而不是原版 `VertexConsumer`。

我用 `javap` 去查 `com.mojang.blaze3d.vertex.VertexConsumer` 的 `addVertex` 重载，**没有输出**
（可能类名/来源不对，我没有继续猜）。

⇒ **按纪律停手**：不确认 1.20.1 到底有没有这些重载之前，不动这 28 条。
下一轮先把 `VertexConsumer` 的真实重载列出来（换 jar / 换查法），再决定是"补桩"还是"改调用点"。

> 这条正好是我这几轮反复用到的纪律：**"报错内容"和"代码现状"对不上时，先怀疑自己的理解，别急着改。**
### 3.71 第 69 次：三条低成本规则，**155 → 149**

| 规则 | 原因 | 命中 |
| --- | --- | --- |
| `new MatchException(null, null)` → `new IllegalStateException("未匹配的 case 分支")` | `MatchException` 是 **Java 21** 才有的（项目目标 17）；这些都在 `default ->` 分支里，语义就是"没匹配上" | 3 |
| `getModelViewStack().identity()` → `.setIdentity()` | 1.21 的 `getModelViewStack()` 返回 `Matrix4fStack`（有 `identity()`）；1.20.1 返回 `PoseStack`，方法是 `setIdentity()` | 4 |
| `ABGR32.` → `net.minecraft.util.FastColor.ABGR32.` | 1.21 把 `ARGB32`/`ABGR32` 从 `FastColor` 里提出来了；1.20.1 仍在 `FastColor` 下 | 4 |

**155 → 149。** 另有两条规则一次就报 **0 命中**（`ABGR32` 的 import 那条），
`--scan` 立刻拒收并列出，我核实后**删掉了它**（改用全限定名后本来就不需要 import）。

> 又一次：零命中检查在**动手之前**就把"多余的规则"挑出来了。

#### 累计轨迹（全量口径）

| 步骤 | 错误 |
| --- | --- |
| 全量基线 | 184 |
| `Mth.clamp` | 169 |
| `instanceof ServerPlayer` | 163 |
| `PoseStack` 改名 | 159 |
| `OPTIONAL_STREAM_CODEC` 判据 | 155 |
| `MatchException` / `identity` / `ABGR32` | **149** |
### 3.70 第 68 次：补上 codemod 漏掉的候选判据，**159 → 155**

`port_codec_codemod.py` 的候选判据只认 `'StreamCodec'` / `'ByteBufCodecs'`，
**漏了大写的 `OPTIONAL_STREAM_CODEC`** —— 所以 `MP4DeviceStateMirrorPacket` 与
`PadStatePacket`（各有 decode/encode 各 1 处 `ItemStack.OPTIONAL_STREAM_CODEC`）**从未进过候选**。

补上判据后两个文件都判为 OK 并改造（`readItem()` / `writeItem()`）。**159 → 155。**

> **教训**：判据里写常量字符串时要想到**大小写变体** ——
> `STREAM_CODEC`（1.21 原版字段名）与 `StreamCodec`（类名）是同一个东西的两种写法，
> 只匹配后者就会漏掉一批文件，而且**漏得很安静**（它们连候选都不是，不会报"形态不符"）。

#### 累计轨迹（全量口径）

| 步骤 | 错误 |
| --- | --- |
| 全量基线 | 184 |
| `Mth.clamp` 的 `(long)`→`(int)` | 169 |
| `instanceof ServerPlayer` | 163 |
| `PoseStack` 改名 | 159 |
| 补 `OPTIONAL_STREAM_CODEC` 判据 | **155** |
### 3.69 第 66–67 次：把 163 条的"缺失符号"抽出来分成本，并做掉纯改名那批

#### 关键诊断：剩下 163 条**成本差异极大**

从 javac 的 `符号:` / `位置:` 行抽出真实缺口：

| 缺失 | 条数 |
| --- | --- |
| `变量 context` | **20** |
| `addVertex(Matrix4f,float,float,float)` | **16** |
| `addVertex(Pose,float,float,float)` | **12** |
| `begin(Mode,VertexFormat)` / `build()` / `buildOrThrow()` | 7 / 5 / 3 |
| `OPTIONAL_STREAM_CODEC` | 4 |
| `getComponents()` / `DataComponents` | 3 / 3 |
| `pushMatrix()` / `popMatrix()` / `identity()` | 3 / 3 / 3 |
| `MatchException`（Java 21 才有） | 3 |
| `fromArgb32(int)` / `ABGR32` | 3 / 3 |

**纯改名那批**（`pushMatrix/popMatrix`）已做：两条规则，**全量 163 → 159**。

> 注意 `addVertex(Matrix4f…)/(Pose…)` **合计 28 条**，是"顶点管线签名变了"，
> 属**高成本**；`变量 context` 20 条要逐个看语义。

#### 给用户的工期判断（已当面说明）

* **继续补桩/改名**：低成本那批约 25~35 条几轮可掉；**剩下 ~130 条集中在渲染/垫片层**，
  是真移植工作量，估计**还要 15~25 轮**；
* **砍掉延后渲染层**（用户早先说过"最快到可测试"）：约 **3~5 轮**能出 jar，
  代价是地形预览/离屏 GUI/视频广告牌等**本就登记为延后**的功能彻底没有；
* **必须说清的前提：编过 ≠ 能跑。** 到 0 错误只是"能出 jar"，
  之后 Mixin 是否真应用、注册是否成功、运行期 NPE —— **A1–A6 的验证是另一个阶段**。
### 3.68 第 65 次：`instanceof ServerPlayer` 那 9 条 —— **修好了，但不是靠我新加的规则**

那 9 处形状完全一致：

```java
if (contextSupplier.get().getSender() instanceof ServerPlayer player && …)
```

`getSender()` 已返回 `ServerPlayer` ⇒ Java 17 报"模式类型是表达式类型的子类型"。

#### 有意思的地方：**我新加的规则 0 命中，而 `scan` 当场拒收**

我为此写了一条新规则，`--scan` 报 **`0` 命中**，并明确列出"零命中的规则（可能是表写错，或该规则本就不适用）"、**退出码 2**。

去查文件才发现：**这些站点早就被另一条既有规则修好了**（表里那条
`mixin: context.getSender() -> contextSupplier.get().getSender()`，本轮报 `17 → 9`）——
它把整段改成了 `ServerPlayer player = …getSender(); if (player != null && …)`。
所以轮到我的新规则时**已经没东西可匹配**。

⇒ **我把那条冗余规则删掉了**（留着会让 `--apply` 因零命中检查而拒绝，且污染规则表）。
删除后 `--scan` 干净通过（无零命中、幂等自检通过）。

#### 结果

**全量 169 → 163**（−6；另有 3 处在这轮之前就已被那条既有规则覆盖）。

> 这条**零命中检查**又一次替我挡住了错误动作 —— 它逼我去查"为什么没命中"，
> 才发现真正修好它的不是我以为的那条规则。**如果当时没有这个门禁，我会带着一条死规则继续走。**
### 3.67 第 64 次：`Mth.clamp` 的 `(long)` 强转改 `(int)`，**全量 184 → 169**

那 15 条 `float → int 可能有损失` **形状完全一致**：

```java
rangeRaceMaxCandidates = Mth.clamp((long)rangeRaceMaxCandidates, 1, 8);
int centerX = Mth.clamp((long)focus.x(), this.minX, this.maxX);
```

**真因**：早先为消歧给第一个实参加的 `(long)`，让它落到了
`clamp(float,float,float)` 重载 ⇒ 返回 `float` ⇒ 赋给 `int` 就有损失。
1.20.1 有 `clamp(int,int,int)`：把强转改成 `(int)` 即可走 int 重载、原样返回 `int`。

一条规则、改动 11 个文件。**全量 184 → 169**（正好 −15，与条数吻合）。

> 这次是**第一次用新的全量口径报进度** —— `clean compileJava`，
> 因此这 15 条是真实的净减少，不是增量假象。
### 3.66 第 63 次：把"只能报全量数"做成**脚本**（`tools/build_full.ps1`）

上一轮发现增量编译会隐藏错误，光靠"我记得跑 clean"不够 —— 做成工具：

* 固定跑 `gradlew clean compileJava`，用 `cmd` 重定向按**原始字节**落盘（避免 GBK 标签变 `????`）；
* 直接调 `gradle_ledger.py` 出**全量**数字与文件排行；
* **它从不接受外部日志** ⇒ "拿一份增量输出冒充全量"在这条路径上**不可能发生**（负例自检就是验这一点，已通过）；
* 退出码：0 编译通过 / 1 有错 / 2 结果不可用（语法类）/ 3 前置缺失。

**实跑确认：全量 = 184 错误 / 3 警告**，文件排行与上一轮一致。

#### 本轮又踩了两个老坑（各记一条）

1. **`.NET` 的相对路径不认 PowerShell 的 `Set-Location`** —— `[System.IO.File]::ReadAllBytes('tools\x.ps1')`
   直接报"找不到路径 `D:\work\tools\...`"。**凡是 `.NET` 调用一律用绝对路径。**
2. **文件开头的 `<#` 会丢 `<`** —— 这是**第二次**（上次是 `check_ingame_smoke.ps1`）：
   写完 `.ps1` 后第 1 行变成 `#`，于是注释块失效、`[CmdletBinding()]` 变成语法错误。
   ⇒ **写完 `.ps1` 必须核验第 1 行**（不能只看"语法 OK"，因为 GBK 误读也会报成别的语法错误）。
### 3.65 第 62 次：**全量口径下 184 条的真实构成**（重新定基线）

按"全量构建"重新分桶：

**按种类**

| 种类 | 条数 |
| --- | --- |
| `找不到符号` | **121** |
| `float → int 可能有损失` | 15 |
| `模式类型 ServerPlayer 是表达式类型的子类`（Java 语言级）| 9 |
| `Matrix4f 无法转换为 Quaternionf` | 5 |
| `ContainerEventHandler.mouseScr…` 覆盖冲突 | 5 |
| `ByteBufferBuilder 无法转换为 BufferBuilder` | 4 |
| `saveWithoutMetadata` | 3 |
| `NO_CULL 是 protected` | 2 |

**按文件（前 14）**

```
13  TerrainPreviewSectionCompiler        8  HolographicPreviewPipRenderer
 6  VideoBillboardPreview                6  PadMapGuiLayer
 6  PortGuiFramebufferBlitter            6  PortGuiTextures
 5  PortDebugRenderTypes                 4  PortSubmitNodeCollector
 4  PortPictureInPictureRenderer         4  MP4OffscreenGuiRenderer
 4  PadOffscreenGuiRenderer              4  YuvVideoRenderTypes
 4  SetMusicIDMessageMixin               3  ModernTurntableBlockEntity
```

#### 结论：**大头是"渲染/垫片层"**（`PortGui*`、`PortSubmitNodeCollector`、`*OffscreenGuiRenderer`、
`YuvVideoRenderTypes`、`TerrainPreview*`）—— 正是 MVP-SCOPE 登记为**延后**的那一层。
它**仍然挡着 jar**，但按用户口径**画面正确性不在验收范围**。

⇒ **最快打法（延续已经验证有效的那招）**：给这一层用到的 1.21 渲染 API **继续补编译桩**
（`ByteBufferBuilder`/`MeshData` 两个桩已经证明一次能消 14~17 条），
而不是逐个文件真移植。

#### 也在里面、但**不能靠桩**的几条（要单独处理）

* `实例of ServerPlayer` 的 9 条 —— **Java 17 语言级**：泛型 `getSender()` 已返回 `ServerPlayer`，
  再写 `instanceof ServerPlayer` 就是"模式类型是表达式类型的子类"。改法固定（直接赋值、不判 instanceof）。
* `float → int` 15 条 —— `Mth.clamp` 的 1.21 重载问题，需要显式转型或用 1.20.1 的写法。
* `Matrix4f ↔ Quaternionf` 5 条、`mouseScr…` 5 条 —— 渲染相关，属延后层。
* `ByteBufferBuilder → BufferBuilder` 4 条 —— 正是我之前预言过的硬边界
  （目标方法形参是 1.20.1 真实类型，桩不适用）。
### 3.64 第 61 次：🔴🔴 **重大更正 —— 我前若干轮报的错误数大多是"增量编译"的数，不是全项目数**

#### 怎么发现的

改完 `ClientPauseChangeEvent`（自认为消掉最后 2 条）后跑 `compileJava`，**错误数从 2 跳到 184**。
第一反应是我把文件改坏了。去查报错内容，发现是：

```
ControlConsoleBlock.java:32  找不到符号   private static final MapCodec<ControlConsoleBlock> CODEC = simpleCodec(...)
```

—— `MapCodec` / `simpleCodec` 是 **1.21 的 API**，这些文件**根本没被我的规则改过**。
它们的 mtime（23:54）比 stage（00:16）**更早**，说明组装副本是拿**更早的 stage 状态**做的。

#### 真因：**Gradle 增量编译**

Gradle 的 `compileJava` 默认**增量**：只重编"受影响"的源文件，
**没被重编的文件里的错误不会被报出来**。所以我最近这些轮看到的
`134 → … → 3 → 2`，**大多是增量口径**，**不是全项目口径**。

**验证**：跑 `clean compileJava`（全量）⇒ **184 条**。

#### 这意味着什么（必须说清）

* **那些修复本身是真的** —— 每一步都确实改掉了对应的错误，编译也确实通过了那些文件；
* **但那些"总数"不可比、也不代表项目总错误数** —— 我把增量数当成了全量数，
  于是报告了一条"134 → 2（掉了 98.5%）"的曲线，**这条曲线不成立**；
* 真实全项目口径（clean）现在是 **184**。

> 这与之前那个"离线 javac 333 vs gradle 160"的谜团**很可能是同一个原因** ——
> 离线 javac 是**全量**的，而 gradle 那些次数是**增量**的。

#### 制度修补（立刻生效）

**以后所有错误数一律来自 `gradlew clean compileJava`**；
`gradle_ledger.py` 的输入必须是全量构建的输出，
并且**报数时同时写上"全量"**。增量的数只用于"这次改动有没有让受影响的文件变好"，**不得当作进度**。
### 3.63 第 60 次：`LineStateShard` 用"同包借道"解决，**3 → 2**

1.20.1 的 `RenderStateShard.LineStateShard` 是 **`protected` 嵌套类** —— 我们别的包不能直接 `new`。

**Java 的 `protected` 嵌套类对"同一个包"可见** ⇒ 在 **Minecraft 自己的包**里放一个借道入口：

```java
// ncpb-assembled/src/main/java/net/minecraft/client/renderer/NcpbRenderStateShardAccess.java
package net.minecraft.client.renderer;
public final class NcpbRenderStateShardAccess {
    public static RenderStateShard.LineStateShard lineStateShard(OptionalDouble width) {
        return new RenderStateShard.LineStateShard(width);
    }
}
```

调用点改成 `NcpbRenderStateShardAccess.lineStateShard(OptionalDouble.of(width.floatValue()))`。

> 这和 `ByteBufferBuilder` / `MeshData` 两个桩是**同一招**：
> **当 1.20.1 的某个东西"访问不到"，就把入口放进它自己的包里。**
> 与"桩"的区别是：这里构造出来的**是真的 `LineStateShard`，不改变任何渲染语义**。

**3 → 2 错误。**

#### 只剩 2 条：`ClientPauseChangeEvent`（同一处，2 条错误）

1.20.1 **没有**这个事件。打算做成：在已有的 `ClientTickEvent` 处理器里
**比对 `Minecraft.getInstance().isPaused()` 的变化**，状态翻转时执行原来 `onClientPauseChanged` 的逻辑。
—— 这是**行为等价的改写**，不是改名，所以下一轮单独做、做完立刻编译复验。
### 3.62 第 59 次：`RegisterMenuScreensEvent` → `FMLClientSetupEvent`，**5 → 3**

`javap` 实证 1.20.1 **没有** `RegisterMenuScreensEvent` ⇒ 菜单注册要改方式。改法：

```java
// 原来（1.21）
private static void registerMenuScreens(RegisterMenuScreensEvent event) {
   event.register(ModMenus.MEDIA_TOOL_BINDING.get(), MediaToolBindingScreen::new);
   …
}
// 现在（1.20.1）：静态 MenuScreens.register，并且用 enqueueWork 包起来
private static void registerMenuScreens(FMLClientSetupEvent event) {
   event.enqueueWork(() -> {
      MenuScreens.register((MenuType)ModMenus.MEDIA_TOOL_BINDING.get(), MediaToolBindingScreen::new);
      MenuScreens.register((MenuType)ModMenus.MEDIA_TOOL_REPORT.get(), MediaToolReportScreen::new);
   });
}
```

* 挂载点**本来就在**（`modEventBus.addListener(…::registerMenuScreens)`），不用动；
* 用 `enqueueWork` 是 Forge 推荐做法（这个回调可能在别的线程上跑）。

**5 → 3 错误。**

#### ⚠️ 记账：这一处是**直接改副本**，不在规则表里

与之前那 3 处 `P0ModEntry` 同类问题 —— **重新组装会丢掉它**。
待办里已有"写 `tools/assemble.py` 把组装脚本化"，**这条也要一起进脚本**（或改成规则）。

#### 剩 3 条

| 位置 | 条数 | 处境 |
| --- | --- | --- |
| `ClientPauseChangeEvent` | 2 | **无等价物** ⇒ 在 ClientTick 里比对 `isPaused()` 变化 |
| `RenderStateShard.LineStateShard` | 1 | 1.20.1 里是 `protected` ⇒ 包一层 |
### 3.61 第 58 次：`PlayerSkin.Model` → `DefaultPlayerSkin.ModelType`，**6 → 5**

1.21 有 `net.minecraft.client.resources.PlayerSkin`（含嵌套 `Model`）。
列 jar 实证：**1.20.1 没有 `PlayerSkin` 这个类**，只有 `DefaultPlayerSkin$ModelType` / `$SkinType`。
而 `CuriosHeadGearLayer` 正是在 `EntityRenderersEvent.AddLayers` 里遍历皮肤：

```java
for (Model skin : event.getSkins()) { if (event.getSkin(skin) instanceof PlayerRenderer pr) … }
```

⇒ 两条规则：import 改成 `DefaultPlayerSkin`，用法改成 `DefaultPlayerSkin.ModelType`。**6 → 5 错误。**

#### 剩 5 条（都是"要改结构"，不是改名）

| 位置 | 条数 | 处境 |
| --- | --- | --- |
| `RegisterMenuScreensEvent` | 2 | **类在 1.20.1 不存在** ⇒ 菜单注册要挪到客户端 setup（`FMLClientSetupEvent`）里直接 `MenuScreens.register` |
| `ClientPauseChangeEvent` | 2 | **无等价物** ⇒ 需设计（在 ClientTick 里比对 `Minecraft.getInstance().isPaused()` 的变化） |
| `RenderStateShard.LineStateShard` | 1 | 1.20.1 里是 **`protected`** ⇒ 要自己包一层或在同包内取用 |
### 3.60 第 57 次：`JOrbisAudioStream` 在 1.20.1 **换了名也换了包** —— **7 → 6**

上一轮没查清的类，这轮扫遍 `fg_cache` 的 **5 个 jar** 找到了：

```
com/mojang/blaze3d/audio/OggAudioStream.class      ← 1.20.1 的位置
net/minecraft/client/sounds/AudioStream.class
```

即 1.21 的 `net.minecraft.client.sounds.JOrbisAudioStream` 在 1.20.1 是
**`com.mojang.blaze3d.audio.OggAudioStream`**。两条规则（import + 类名）。

**7 → 6 错误。**

> 这一条也说明：**"找不到符号"要先去 jar 里找真身**，别猜包名 ——
> 1.21 把 Mojang 的 OGG 解码器从 `com.mojang.blaze3d.audio` 挪进了 `net.minecraft.client.sounds`
> **并改了名**，光按包名推断会一直找不到。

#### 剩 6 条

| 位置 | 类别 |
| --- | --- |
| `RegisterMenuScreensEvent` | 类在 1.20.1 **不存在** ⇒ 改注册方式（2 条）|
| `ClientPauseChangeEvent` | **无等价物** ⇒ 需设计（2 条）|
| `PlayerSkin.Model` | 1.20.1 是别的枚举；**要先查 `event.getSkins()` 的元素类型**（1 条）|
| `RenderStateShard.LineStateShard` | 1.20.1 里是 `protected` ⇒ 要包一层（1 条）|
### 3.59 第 56 次：`DeltaTracker` 形参删掉，**9 → 7**

上一轮取证的第 3 条，它的对应物最确定，先做它。

`javap -p net.minecraft.client.gui.Gui` 实证：

```
public void renderCrosshair(net.minecraft.client.gui.GuiGraphics);
```

—— **只有一个参数**。而 `GuiRoamingCrosshairMixin` 的处理器写成
`(GuiGraphics graphics, DeltaTracker …, CallbackInfo callback)`。

> **Mixin 处理器的形参必须与目标方法逐位一致**，所以多出来的 `DeltaTracker` 参数要删掉。
> 两条规则（进 `port_rename.py`）：删该形参 + 删 `DeltaTracker` 的 import。

**9 → 7 错误。**

#### 累计轨迹（全部实测、语法类 0）

| 步骤 | 错误 |
| --- | --- |
| 起 | 134 |
| 移走 19 个"目标不可映射"的 mixin | 71 |
| `ByteBufferBuilder` 桩 | 57 |
| `MeshData` 桩 | 40 |
| 删 `collectImplicitComponents` | 34 |
| 删死掉的 `DataComponents` import | 24 |
| `RenderFrameEvent` → `RenderTickEvent` | 17 |
| `Attribute.Sentiment` | 16 |
| `port_codec_codemod` | 11 |
| 恢复被截断的 `LiveStreamerControlPacket` | 9 |
| 删 `DeltaTracker` 形参 | **7** |

**剩 7 条**：`RegisterMenuScreensEvent` 2、`ClientPauseChangeEvent` 2、
`JOrbisAudioStream` 1、`PlayerSkin.Model` 1、`LineStateShard` 1。
### 3.58 第 55 次：把剩下 9 条逐条查清（**没有硬改，先取证**）

| # | 位置 | 查到的事实 |
| --- | --- | --- |
| 1 | `CuriosHeadGearLayer:15` `import …PlayerSkin.Model;` | **`Model` 确实在用**（`:26 for (Model skin : event.getSkins())`）⇒ **不是删 import 就完**。1.20.1 的 `net.minecraft.client.resources` 里有 `DefaultPlayerSkin$ModelType` / `$SkinType`，**下一步要核对 `event.getSkins()` 在 1.20.1 的元素类型**再改 |
| 2 | `SyncedMediaSound:30,179` `JOrbisAudioStream` | **在 Forge 的 mapped jar 里搜不到这个类** ⇒ 它不在 `net.minecraft.client.sounds`（1.21 的位置）里，要去别的 jar/包找 |
| 3 | `GuiRoamingCrosshairMixin:4,20` `DeltaTracker` | 1.20.1 的 `GuiGraphics` 渲染方法收的是 **`float partialTick`**，没有 `DeltaTracker` ⇒ 形参要换成 `float`（mixin 的签名必须与目标方法一致）|
| 4 | `PortDebugRenderTypes:10,24` `RenderStateShard.LineStateShard` | 1.20.1 里它是 **`protected`** ⇒ 不能直接引用，要自己包一层或换写法 |

#### 为什么本轮**不动手**

这 4 条**每一条都要先确认 1.20.1 的确切对应物**（元素类型 / 类所在包 / 形参类型 / 访问权限），
不是改名。按既定纪律：**查不清就不改** —— 尤其第 1 条，`Model` 在用，改错会连累 `event.getSkins()` 的遍历。

> 本轮产出是**4 条已取证的事实**，不是数字。剩下 9 条里另 5 条是
> `RegisterMenuScreensEvent`(2) 与 `ClientPauseChangeEvent`(2) 加一条 —— 都属"类不存在、要改结构"。
### 3.57 第 54 次：🔴 抓到并修复一处**被我截断成 0 字节的源文件** —— **11 → 9**

#### 症状与真因（隔着两个文件）

`LiveStreamerScreen.java` 报：

```
程序包 LiveStreamerControlPacket 不存在
    private void sendControl(LiveStreamerControlPacket.Action action) {
```

顺着查下去：**`network/LiveStreamerControlPacket.java` 是 0 字节** —— 文件被清空了，
所以"这个类不存在"。

#### 怎么造成的（就是上一轮我记下的那个"半应用状态"）

上一轮 `port_codec_codemod.py` 的 apply 循环写成：

```python
open(p, 'w', ...).write(new)     # new 可能是 None
```

`open(p, 'w')` **一调用就把文件截断**，然后 `.write(None)` 才抛 `TypeError` 崩掉 ⇒
**文件被清空、循环中止**。我上一轮只记了"写了一半"，**没想到会有一个文件被清成 0 字节**。

#### 修法

* 全树扫 `0 字节的 .java`：**只有这 1 个**；
* 从手工层恢复（`tools/port-manual/.../network/LiveStreamerControlPacket.java`，**4087 B**）；
* 复核：`还有 0 字节文件吗: 0`。

**错误 11 → 9。**

#### 另一件（本轮做的，未降数字）

`ItemStack.OPTIONAL_CODEC` → `ItemStack.CODEC`（javap 实证 1.20.1 只有 `CODEC`）。
**条数没变**（那几处本来就没被报错）—— 但它把登记在册的"未完成项③"做掉了，
并把**语义差异**写进了规则注释：`CODEC` **不接受空栈**，而三处用法分别是
`optionalFieldOf(..., EMPTY)`（写时空值省略）与 `encodeStart(...).resultOrPartial(...)`
（失败本来就是"没有结果"）⇒ 行为一致。

> **教训（补一条）**：写文件不要"先 open 再判断能不能写"。
> 应**先算出内容、判空之后再 open**；再进一步就是**写临时文件 + 原子替换**。
> 我上一轮只把它当成"半应用状态"，实际后果是**丢了一个源文件**。
### 3.56 第 53 次：写 `port_codec_codemod.py`，**16 → 11**

按上一轮登记的方案：**改调用点 + 删声明块**（因为垫片在语言层面写不出来）。

| 原样 | 改成 |
| --- | --- |
| `(UUID)X.UUID_CODEC.decode(buf)` | `buf.readUUID()` |
| `X.UUID_CODEC.encode(buf, v)` | `buf.writeUUID(v)` |
| `(X.Action)ACTION_CODEC.decode(buf)` | `buf.readEnum(X.Action.class)` |
| `ACTION_CODEC.encode(buf, v)` | `buf.writeEnum(v)` |
| `(ItemStack)ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)` | `buf.readItem()` |
| `ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, v)` | `buf.writeItem(v)` |

删除声明块用的是**同名缩进反向引用**（`(?P=ind)\};`）—— 那是第 47 次踩坑换来的写法。
结果：**改造 6 个文件、跳过 5 个**，**16 → 11 错误**。

#### 跳过的 5 个是"假阳性"，而且这个失败方向**是对的**

`LiveStreamerControlPacket` / `ModernTurntableControlPacket` / `MP4DeviceIdPacket` /
`MP4EnsureDeviceIdPacket` / `PadPublishPacket` —— 它们是**手工层**的文件，
只在**注释里**提到 `StreamCodec`（我写的迁移说明），并没有真的用它。
工具按文本判断 ⇒ 误判为"形态不符" ⇒ **拒绝改动**。
**宁可不动，也不乱动** —— 这个方向我保留。

#### 本轮踩的两个自己的坑（都记下来）

1. **`--apply` 遇到形态不符会直接 return 2、什么都不写** —— 导致我第一次"应用了"却没变化。
   已改成：**只改判为 OK 的**，其余原样留着并明确列出。
2. 改完又踩一个：apply 循环对 `transform()` 返回 `None` 的文件**照样 `write(None)`**
   ⇒ `TypeError`，而且**写了一半就崩，留下半应用状态**（错误数一度从 16 变 17）。
   已加 `if new is None: continue`。**"跳过"必须显式跳过，不能指望它自己不出现在列表里。**
### 3.55 第 52 次：差点做错 —— 那些 `StreamCodec` **不是死代码**，而且**桩救不了**

`StreamCodec` / `ByteBufCodecs` 那 7 条涉及 7 个包类，3 种形态：

| 形态 | 文件 | 样子 |
| --- | --- | --- |
| A | `MP4PlaybackSyncPacket` / `MP4PlaybackTimelinePacket` / `ControlConsoleConfigResultPacket` | `StreamCodec<FriendlyByteBuf, UUID> UUID_CODEC = new StreamCodec<…>() { static decode/encode }` |
| B | `MP4PlaybackControlPacket` / `PadPlaybackControlPacket` | 同上，但泛型是包的 `Action` 枚举 |
| C | `LyricProjectorConfigPacket` / `VideoProjectorConfigPacket` | 只是 `import …ByteBufCodecs;` |

**好消息**：这些包**已经改成 Forge 1.20.1 的形态**了（都有 `decode(FriendlyByteBuf)` /
`encode(packet, buf)` / `handle(payload, Supplier<NetworkEvent.Context>)`）。看着像"删掉残留即可"。

#### ⚠️ 但**不能删** —— 它们还在被用

```java
// MP4PlaybackSyncPacket:36
private static final StreamCodec<FriendlyByteBuf, UUID> UUID_CODEC = new StreamCodec<…>() { … };
// :47  ← 这里还在调
   (UUID)MP4PlaybackSyncPacket.UUID_CODEC.decode(buffer),
```

#### ⚠️ 而且**垫片也救不了**（这条值得记）

我本想照 `ByteBufferBuilder` 的办法在 `net.minecraft.network.codec` 下补一个 `StreamCodec` 桩。
**行不通**，理由是语言层面的：

* 调用点 `UUID_CODEC.decode(buf)` 是按**字段的静态类型** `StreamCodec<B,V>` 去解析的，
  所以 `StreamCodec` 必须声明 `decode`/`encode`；
* 而匿名类里的那份是 **`static`** 的 —— 桩若把它们声明成**实例方法**，
  匿名类里的 `static` 就会**隐藏实例方法**，直接编译错；
* 若声明成 `static`，泛型参数 `V` / `B` **在静态方法里不可用** ⇒ 根本写不出来。

⇒ **只能改调用点**：`UUID_CODEC.decode(buf)` → `buf.readUUID()`、
`UUID_CODEC.encode(v, buf)` → `buf.writeUUID(v)`（枚举则是 `readEnum(Action.class)` / `writeEnum(v)`），
再把声明块整段删掉。**这要按文件处理**（调用点形态不一），与 `port_saveddata_codemod` 同一类需求。

> **本轮的价值是"没做错"**：我原本打算当死代码删掉（会立刻崩），也想用垫片（写不出来）。
> 两条都先核了才停手。
### 3.54 第 51 次：`Attribute.Sentiment`（1 条）+ 摸清剩下 16 条的**类别**

`javap` 实证：1.20.1 的 `Attribute` **没有** `Sentiment`，也没有 `setSentiment` ——
那是 1.21 加的（只影响属性提示的颜色，**纯外观**）⇒ 去掉 import 与那段链式调用。
**17 → 16 错误。**

#### 同时确认了另一件事（为下一轮省事）

`javap net.minecraftforge.client.event.RegisterMenuScreensEvent` → **1.20.1 里不存在这个类**。
即菜单界面注册在 1.20.1 得走别的口子（客户端 setup 里直接 `MenuScreens.register`），
不是改个名就完 —— **下一轮要改成事件外的注册**。

#### 剩下 16 条的类别（每类都要一个决定，不是改名）

| 类别 | 条数 | 1.20.1 的处境 |
| --- | --- | --- |
| `StreamCodec` / `ByteBufCodecs` | ~7 | `net.minecraft.network.codec` 整个包不存在 ⇒ 要改成手写 encode/decode（即 `port_network_codemod` 还有 5 处形状没覆盖）|
| `RegisterMenuScreensEvent` | 2 | **类不存在** ⇒ 改注册方式 |
| `ClientPauseChangeEvent` | 2 | **无等价物** ⇒ 需设计（如在 ClientTick 里比对 `isPaused()` 变化）|
| `JOrbisAudioStream` | 1 | 1.20.1 是别的类 |
| `PlayerSkin.Model` | 1 | 1.20.1 是 `MinecraftProfileTexture.Type` |
| `DeltaTracker` | 2 | 1.20.1 用 `float partialTick` |
| `RenderStateShard.LineStateShard` | 1 | 访问受限（`protected`）⇒ 需换写法 |

> **这 16 条基本都是"语义缺口"，不是机械改名。** 我不打算再用正则一把梭 ——
> 上面每一行都要先查清 1.20.1 的对应做法，再动手，改一条编译一次。
### 3.53 第 50 次：`RenderFrameEvent` 换成 `TickEvent.RenderTickEvent`，**24 → 17**

1.21 的 `RenderFrameEvent`（Pre/Post）在 1.20.1 **不存在**；逐帧钩子的对应物是
`TickEvent.RenderTickEvent`（同样每帧触发，带 phase）。两条规则：

* `import …RenderFrameEvent.Pre;` → `import net.minecraftforge.event.TickEvent;`（3 处）
* `onRenderFrame(Pre e)` → `onRenderFrame(TickEvent.RenderTickEvent e)`（3 处）

**24 → 17 错误。**

#### 顺手核实了一个可疑读数

规则表报 `MinecraftForge 补 import: 11`，看着像"注入了 11 行重复 import"。**去核对**：
`ModernTurntableClientEvents.java` 共 30 行 import、**去重后仍是 30** —— **没有重复**，
那个 11 是"整块 import 注入"这条规则的匹配计数，不是行数。**虚惊一场，但我核了才敢说。**

#### 累计轨迹（全部实测、语法类 0）

| 步骤 | 错误 |
| --- | --- |
| 起 | 134 |
| 移走 19 个"目标不可映射"的 mixin | 71 |
| `ByteBufferBuilder` 桩 | 57 |
| `MeshData` 桩 | 40 |
| 删 `collectImplicitComponents` | 34 |
| 删死掉的 `DataComponents` import | 24 |
| `RenderFrameEvent` → `TickEvent.RenderTickEvent` | **17** |
### 3.52 第 49 次：删掉死掉的 `DataComponents` import，**34 → 24**

`DataComponents` 在 1.20.1 不存在。`CUSTOM_DATA` 的用法被垫片替换掉之后，
多数文件只剩一个**没人用的 import**。先核对"正文是否还用到"，结果：

```
有 DataComponents import 的文件: 10 ；正文没用到的: 9 ；仍有用到的: 1（LinkHelper）
```

⇒ 删 import 是净收益（那 1 个仍有用法的文件，删掉 import 后用法改报"找不到符号"，条数不变）。

**34 → 24 错误**（语法类 0，警告 2）。

#### 累计轨迹（全部实测、且语法类为 0）

| 步骤 | 错误 |
| --- | --- |
| 起 | 134 |
| 移走 19 个"目标不可映射"的 mixin | 71 |
| `ByteBufferBuilder` 桩 | 57 |
| `MeshData` 桩 | 40 |
| 删 `collectImplicitComponents` | 34 |
| 删死掉的 `DataComponents` import | **24** |

**从 134 到 24 —— 剩下不到两成。**
### 3.51 第 48 次：把"语法错误 ⇒ 总数不可信"做成**硬判据**（不再靠我记得）

`tools/gradle_ledger.py` 新增：只要错误里出现**语法类**消息
（`需要 class/interface/enum/record`、`';' expected`、`'{' expected`…），就：

* 仍然打印条数，但**紧接着说明"这个总数不可用（会假性偏小）"**，并列出前几条语法错误；
* **退出码 2**，让调用方/门禁能据此拒绝这个数字。

**负例验证**：拿上一轮那份被截断的日志（`gradle-raw11.txt`）跑 —— 它报"错误 25 条"，
但紧接着明确写出"**有 25 条语法类错误 ⇒ 总数不可用**"并 exit 2。
**正例验证**：当前那份（语法类 0）正常出 **34 条 / 2 警告**。

> 这是第 47 次那个错误报告的**制度化修补**：一条我口头记住的教训，
> 现在变成了**工具会替我说"不"**的判据。下次再看到"数字突然大降"，台账会先拦住我。
### 3.50 第 47 次：⚠️ **更正上一轮的数字** —— 那个"25"是假的

上一轮我报了"删掉 `collectImplicitComponents` 后 **40 → 25**"。**那个 25 不成立。**

原因：我那条删除规则的正则写成

```
^[ \t]*protected void collectImplicitComponents\(Builder \w+\) \{.*?\n[ \t]*\}
```

收尾用了 `[ \t]*\}` —— 而方法体里**有嵌套的 `if`**，非贪婪的 `.*?` **先遇到内层 `if` 的 `}` 就停下**，
把方法的 `}` 孤零零剩在外面 ⇒ **整个文件语法崩**。

于是 javac 报了一堆"需要 class、interface、enum 或 record"，**并在语法错误后提前收工** ——
所以 25 条里**全部 25 条都是这个语法级联**，真实错误被截断了。**同一个坑我早先就记录过**
（codemod 产出 `), ClientMediaSyncPayload {` 那次），这次又踩。

#### 修法与当前真实数字

* 正则改成用**同名缩进反向引用**收尾：`^(?P<ind>[ \t]*)…\{.*?\n(?P=ind)\}`；
  重建 stage 后核验：3 个 BE 里 `collectImplicitComponents` 残留 0、**花括号平衡**。
* 把 3 个文件用修好的版本覆盖回去，重跑：**错误 34 条，其中语法类 0 条**。

| 步骤 | 错误（**已修正**） |
| --- | --- |
| 起 | 134 |
| 移走 19 个 mixin | 71 |
| `ByteBufferBuilder` 桩 | 57 |
| `MeshData` 桩 | 40 |
| 删 `collectImplicitComponents` | **34**（不是 25） |

> **教训（第二次）：只要报错里出现"语法类"消息，总数就不可信** —— javac 在语法错误后会提前停止，
> 后面真正的错误不会被数到。**看到数字突然掉一大截、同时出现语法类报错，先怀疑截断，别急着庆祝。**
### 3.49 第 46 次：删掉 1.21 的 `collectImplicitComponents`，**40 → 25**（警告也归零）

3 个方块实体（`LyricProjectorBlockEntity` / `SpeakerBlockEntity` / `VideoProjectorBlockEntity`）
各有一小段 1.21 专有钩子：

```java
protected void collectImplicitComponents(Builder components) {
   super.collectImplicitComponents(components);
   if (this.linkedTurntablePos != null) {
      components.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
   }
}
```

**1.20.1 没有这个钩子**，而它的唯一效果是"给方块实体的物品形态加附魔光效"（**纯外观**）⇒ **整段删掉**。
两条规则（进 `port_rename.py`）：删方法 + 删随之无用的 `DataComponentMap.Builder` import。

**40 → 25 错误，警告 2 → 0。**

#### 累计轨迹（全部实测）

| 步骤 | 错误 |
| --- | --- |
| 起 | 134 |
| 移走 19 个"目标不可映射"的 mixin | 71 |
| `ByteBufferBuilder` 桩 | 57 |
| `MeshData` 桩 | 40 |
| 删 `collectImplicitComponents` | **25** |
### 3.48 第 45 次：`MeshData` 桩写完，**57 → 40（−17）**

新增 `com/mojang/blaze3d/vertex/MeshData.java`（含嵌套 `DrawState` / `SortState`），
并给 `ByteBufferBuilder` 补上嵌套 `Result`。覆盖的表面（实测得来）：
`indexBuffer()` / `drawState().indexType()` / `drawState().indexCount()` /
字段 `vertexBuffer`（借 1.20.1 真实存在的 `VertexBuffer` 提供 `bind()`/`draw()`）/
`close()` / `sortQuads(ByteBufferBuilder, VertexSorting)` → `SortState.buildSortedIndexBuffer(...)` → `Result`。

**一个桩类消掉 17 条。**

#### 累计轨迹（全是实测）

| 步骤 | 错误 |
| --- | --- |
| 起 | 134 |
| 移走 19 个"目标不可映射"的 mixin | **71** |
| `ByteBufferBuilder` 桩 | **57** |
| `MeshData` 桩 | **40** |

> 桩的方法体都写成"不做事、不返回 null 让调用方解引用"，取向是
> **要崩也只崩在被延后的渲染路径上**，不牵连 A1–A6 的功能路径。这句话写进了两个桩的类注释。

**下一步**：清剩下的 40 条（`RenderFrameEvent` 那几处、`MultiBufferSource.immediate` 真改、以及那 36 条在范围内的）。
### 3.47 第 44 次：**`ByteBufferBuilder` 桩写完，71 → 57（−14）**

新增 `ncpb-assembled/src/main/java/com/mojang/blaze3d/vertex/ByteBufferBuilder.java`
（**移植桩，渲染延后**）：两个构造器（`int` / 拷贝构造）+ `close()` + `buffer()` / `capacity()` / `reserve()`。
内部持一个真实的 `java.nio.ByteBuffer`，所以"能把缓冲交给原版 API"的地方还有救。

**一个桩类消掉 14 条。** 剩下的（57）里还有 `MeshData` / `SortState` / `RenderFrameEvent` / `MultiBufferSource.immediate` 那几处。

> 桩的注释里**写死**了三句以免后来人误解：① 它**不工作**，只保证"能编译不崩"；
> ② 画面正确性不在本工程的验收范围内（MVP-SCOPE 登记为延后）；
> ③ 硬边界：形参是 1.20.1 真实类型（如 `ByteBuffer`）的地方，桩不适用。

**下一步**：同样的办法写 `MeshData`（含嵌套 `SortState`），再处理 `MultiBufferSource.immediate` 那几处真改。
### 3.46 第 43 次：把桩要覆盖的**确切表面**摘出来了 —— 也发现它有一个**硬边界**

实测用到的成员（去重后）：

```java
// ByteBufferBuilder
new ByteBufferBuilder(262144) / (32768) / (4096) / (initialIndexBytes)   // 构造
new ByteBufferBuilder(bytes)                                            // 拷贝构造
close()                                                                 // closeStorage 里
// MeshData
builder.build()                                                         // BufferBuilder → MeshData
mesh.sortQuads(indexStorage, VertexSorting.DISTANCE_TO_ORIGIN)          // → SortState
// SortState
sortState.buildSortedIndexBuffer(storage, sorting)                      // → Result
```

#### ⚠️ 硬边界：`MultiBufferSource.immediate(...)`

```java
this.bufferSource = MultiBufferSource.immediate(new ByteBufferBuilder(4096));
```

**1.20.1 的 `MultiBufferSource.immediate` 收的是 `java.nio.ByteBuffer`** ——
而 `ByteBuffer` 是抽象类、构造器包内私有，**我们的桩不可能"是"一个 ByteBuffer**。

⇒ 这两行**桩救不了**，必须真改（`ByteBuffer.allocate(...)` 或走 `Tesselator`）。
类似的还有几处"桩类型被当参数传给原版方法"的地方 —— 判断标准很简单：
**只要目标方法的形参是 1.20.1 的真实类型（ByteBuffer / BufferBuilder / VertexConsumer），桩就不适用。**

#### 结论（并据此修正打法）

* **桩仍然值得写**：它能让 `Map<RenderType, MeshData>` / `ByteBufferBuilder[]` 这类
  **只是出现在签名与容器里**的用法全部通过 —— 那占 35 条里的大多数；
* **但桩不是万能**，剩下那几处"桩当参数传给原版 API"的必须**逐个改成 1.20.1 的真类型**；
* 因此最终形态是 **2 个桩类 + 少数几处真改**，而不是"零改动"。
### 3.45 第 42 次：为那 35 条渲染错误定下**最快打法：给 1.21 的 blaze3d 类写"编译桩"**

实测缺的类与用到的嵌套类型（从 71 条错误的 import 行统计）：

| 缺的类 | 出现在几个文件 |
| --- | --- |
| `com.mojang.blaze3d.vertex.ByteBufferBuilder`（+ 嵌套 `ByteBufferBuilder.Result`） | 6 |
| `com.mojang.blaze3d.vertex.MeshData`（+ 嵌套 `MeshData.SortState`） | 5 |
| `RenderStateShard.LineStateShard` | 1 |

**打法**：在 `com.mojang.blaze3d.vertex` 包下**我们自己补两个类**（`ByteBufferBuilder` / `MeshData`），
只实现被用到的表面、内部不做真实工作（渲染已延后）。
这样**11 个渲染文件一行都不用改**就能编译 —— 与 `CustomData` 垫片同一招（那次一次消掉 22 条）。

⚠️ 两条必须守住：
1. **桩必须"能编译且不崩"** —— 方法体可以是空实现，但**不能返回 null 给调用方解引用**（会变成运行期 NPE，比编译错误更难查）；
2. **必须逐条登记**为"渲染延后"，并在文档里写清"画面不保证正确"。

为什么不做逐个文件打桩：**跨文件引用是双向的**（`TerrainPreviewGpuCache` 用
`TerrainPreviewSectionCompiler.compile`，而 `ClientMediaLifecycleHandler` 又用 `TerrainPreviewManager`），
逐个桩要动 11 个文件、还得保证互相的签名不变；给底层补 2 个类只动 2 个新文件。

**下一步就是写这两个桩**，然后重跑构建看 71 掉到多少。
### 3.44 第 41 次（用户要求"最快到可测试"）：**108 → 71**，一刀切掉 37 条 Mixin AP

#### 试错的先后（都是实测，不是猜）

1. **先在 `mixins.json` 里移除那 19 个 mixin** → **无效**（仍 108/22）。
   ⇒ **注解处理器是按 `@Mixin` 注解发现类的，不看配置清单。** 这条假设被实测否掉。
2. **把那 19 个 mixin 文件移出源码树**（`ncpb-assembled/_deferred-mixins/`）→ **有效**：
   **错误 108 → 71，警告 22 → 2。**

#### 🔴 移走的是这 19 个（**明确登记为延后，不是静默跳过**）

`*PacketSubLevelScopeMixin`（8 个）、`AudienceSyncLevelScopeMixin`、`AudioSubscriptionSubLevelDistanceMixin`、
`AudioUtilsSubLevelPositionMixin`、`ClientAudioDemandSubLevelDistanceMixin`、
`ClientAudioOutputRegistryLivePositionMixin`、`ControlConsoleAudioElementPositionMixin`、
`ModernTurntableSoundSubLevelSourceMixin`、`NcpbNetworkTuningMixin`、
`OffscreenGuiPassFixMixin`、`OffscreenGuiProjectionFixMixin`、
`VideoProjectorVisibilityDistanceMixin`、`VideoProjectorConfigPacketSubLevelScopeMixin`。

**共同点：它们的目标绝大多数是"我们自己的方法"**（`handle` 16 条等），
所以在 1.20.1 上 Mixin AP 找不到"官方名→SRG名"的映射、报 37 条错。
它们的用途集中在**Sable 物理化子层（sub-level）的坐标/作用域修复**与若干音频/离屏渲染的位置修正 ——
**测试床没装 Sable**，所以对 A1–A6 的首轮可测试目标**不是必需**。

代价（写清楚）：**子层场景下按钮可能无效、音频位置/离屏渲染的若干修正缺失**；
恢复它们需要先解决"自家类的 mixin 映射"问题（或改成 `@Accessor`/绕开目标校验）。

#### 下一步（按"最快到可测试"排序）

剩下的 **71 条 = 35 条延后渲染 + 36 条在范围内**，**两者都挡着 jar**（没有捷径）。

* **35 条延后渲染**（11 文件，`client/terrain` 22 条最大）：**用最小可编译桩**最快 ——
  保留公开签名、方法体 `throw new UnsupportedOperationException("渲染延后")`。
  ⚠️ 不能简单移走：实测 `TerrainPreviewManager` 被 `ClientMediaLifecycleHandler` 用、
  `VideoBillboardPreview` 被 `VideoProjectorBlockEntity` 用 —— 跨文件有引用。
* **36 条在范围内**（22 文件，最大 5 条）：逐个改。
### 3.43 第 40 次：把 `port_saveddata_codemod` 的自检查清楚了 —— **它是"看运气"的**

上一轮我加了"注入后重新读盘"，但自检仍报失败。这一轮把 `transform` 单独拎出来测：

```
注入是否生效: True
decl 正则是否匹配: False
transform 返回 None? True      ← 分类器本身是对的！
notes: ['改完仍有 FACTORY 残留']
```

**分类器没问题**，问题在**负例挑哪个文件**。实测：

| 跑法 | 结果 |
| --- | --- |
| `--dir stage1-src --scan --fault` | **通过**（`[FAIL] AudioPlaybackIndexSavedData 改完仍有 FACTORY 残留`，exit 0）|
| 把 4 个文件拷到一个平铺目录再 `--dir <tmp> --scan --fault` | **失败**（报"全部 OK"，exit 2）|

即：注入目标是 `targets[0]`，而**哪个文件排第一取决于 `os.walk` 的顺序**；
某些文件上"改坏"恰好命中不了判据（例如它本来就走另一条分支）。

> **结论（如实）**：这个 codemod 的**正例可信**（编译实测 116 → 108）；
> 但它的负例**依赖目录/顺序，等于看运气** —— **不能算"它还会说'不'"**。
> 修法已明确、下轮落实：**把负例目标按文件名写死**（而不是 `targets[0]`），
> 并且**在临时目录里跑**（别动真 stage —— 这次负例把 `stage1-src` 里一个文件改坏了，
> 我已用 `port_rename --apply` 整目录重建恢复，并确认 `FactoryXX` 残留为 0）。
### 3.42 第 39 次：`SavedData.Factory` 的按文件 codemod —— **消掉 8 条（116 → 108）**

新增 `tools/port_saveddata_codemod.py`。它**先读出类名 `X`**，再做三件事：

1. 删掉 `import …SavedData.Factory;`
2. 删掉 `public static final Factory<X> FACTORY = new Factory<>(…);`
3. `computeIfAbsent(FACTORY, "name")` → `computeIfAbsent(X::load, X::new, "name")`

产出核对（`PadDocumentSavedData`）：
```java
return (PadDocumentSavedData)level.getDataStorage()
    .computeIfAbsent(PadDocumentSavedData::load, PadDocumentSavedData::new, "pad_documents");
```

**真实构建 116 → 108（−8）**，与预估一致。

#### ⚠️ 但它的**负例自检仍然不成立**（如实记账）

`--fault` 把一个文件的 `new Factory<>(` 改坏成 `new FactoryXX<>(`，工具却报"改坏了仍全部 OK"。

我第一版犯的是**和以前同一个错**：注入后循环仍用**注入前读进内存的文本**分类
（apply 循环是从磁盘重读的）。这次我加了"注入后重新读盘"，**但它还是报 OK** ⇒ 原因**未查明**。

**结论：这个 codemod 的正例可信（编译实测 −8），但它的自检目前没有鉴别力，不能作为"它还会说'不'"的证据。**
已登记为待修 —— 在修好之前，它只能算"已知有效、但未自带负例"的工具。
### 3.41 第 38 次：`SavedData` 的一半做了，**净效果为零 —— 如实记录**

做了**不需要类名**的那一半（2 条规则，进 `port_rename.py`）：

| 规则 | 命中 |
| --- | --- |
| `save(CompoundTag t, Provider registries)` → `save(CompoundTag t)` | 4 |
| `load(CompoundTag t, Provider registries)` → `load(CompoundTag t)` | 4 |

**但错误数 116 → 116，一点没降。** 逐行比对前后：

```
（前）SavedData 相关 8 条：21,45 / 17,37 / 23,36 / 12,22
（后）完全相同
```

即那 4 个文件各报的 **2 条**是 **`Factory` 的 import 与 `FACTORY` 声明**，
而 `save/load` 的签名**在这个配置下压根没被报成错误**（离线 javac 报过
"is not abstract and does not override"，真实构建这轮没报 —— 原因未查）。

> **为什么还是收下**：1.20.1 的抽象方法确实是 `save(CompoundTag)`（javap 实证），
> 这个签名迟早都要改，且没让任何指标变差。**但它不算进展** —— 这一轮数字上是零。

#### 剩下的 `Factory` 那半：**必须要类名，得写按文件处理的 codemod**

1.21 的 `FACTORY = new Factory<>(X::new, X::load, DataFixTypes.LEVEL)` +
`computeIfAbsent(FACTORY, "name")` 在 1.20.1 要变成
`computeIfAbsent(X::load, X::new, "name")` —— **`X` 是所在类名**，
而规则表是逐条文本替换、**拿不到"当前文件是哪个类"**。

⇒ 与 `port_network_codemod.py` 同一类需求：写一个 **按文件**的小 codemod
（`tools/port_saveddata_codemod.py`），从文件里读出类名 `X` 再生成 3 参数调用。
**这是本轮真正的下一个动作**，已登记。
### 3.40 第 37 次：`TickEvent.*.Post` 那批**改掉了** —— 真实构建 134 → **116**（−18）

5 条规则（已进 `port_rename.py`，可复算），只按**方法名**精确匹配：

| 规则 | 命中 |
| --- | --- |
| `TickEvent.ServerTickEvent.Post` 的 import → `TickEvent` | 5 |
| `TickEvent.ClientTickEvent.Post` 的 import → `TickEvent` | 4 |
| `onServerTick(Post e)` → `onServerTick(TickEvent.ServerTickEvent e)` + **phase 判定** | 5 |
| `onClientTick(Post e)` → 同上（Client 版） | 4 |
| `onClientTick(<全限定>.ClientTickEvent.Post e)` → 同上 | 1 |

生成的样子（`if` 是**必须**的，否则每 tick 跑两次）：

```java
public static void onServerTick(TickEvent.ServerTickEvent event) {
   if (event.phase != TickEvent.Phase.END) {
      return;
   }
   ...
```

> ⚠️ **刻意只匹配 `onServerTick` / `onClientTick`**：这批文件里还有 `onRenderGui(Post event)`，
> 用的是 `net.minecraftforge.client.event.RenderGuiEvent.Post` —— **那个类在 1.20.1 是存在的**，
> 一起改就会把它弄坏。**"看起来同形"的东西必须逐个核对，不能一把梭。"**

**效果：真实构建 134 → 116 条错误**（警告仍 22 条）。13 个文件被改。

#### 待办（组装需要脚本化）

那 3 处 `P0ModEntry.MOD_ID` → `NetMusicCanPlayBili.MODID` 是我**手工**在副本里改的，
不在规则表里；`stage1-src` 里也没有这些引用（它们在"工程版"6 个文件里）。
⇒ 组装步骤目前**不是完全可复算**的。已记为待办：写 `tools/assemble.py` 把
「并入 → 覆盖 6 个 → 删 5 个 P0 → 改 P0ModEntry 引用」全部脚本化。
### 3.39 第 36 次：两个**跨文件的重复形态**（都已用 `javap` 取证）

62 条在范围内的代码错误里，有两个形态各占一片：

#### 形态 A：1.21 把事件拆成了嵌套的 `Pre`/`Post`，Forge 1.20.1 **没有**

`javap net.minecraftforge.event.TickEvent` 实测：**只有外层类，没有嵌套的 `Pre`/`Post`**。
1.20.1 的写法是**一个事件类 + 一个 `phase` 字段**（`TickEvent.Phase.START/END`）。

| 1.21 写法 | 处数 | 1.20.1 的对应物 |
| --- | --- | --- |
| `TickEvent.ServerTickEvent.Post` | 6 | `TickEvent.ServerTickEvent` + **判 `event.phase == Phase.END`** |
| `TickEvent.ClientTickEvent.Post` | 5 | 同上（Client 版） |
| `RenderFrameEvent.Pre` | 3 | `RenderFrameEvent` 在 1.20.1 **不存在** ⇒ 要用 `RenderLevelStageEvent` 或 `TickEvent.RenderTickEvent` |
| `ClientPauseChangeEvent.Post` | 1 | 1.20.1 **没有等价物** ⇒ 需要设计（例如在 ClientTick 里比对 `Minecraft.getInstance().isPaused()` 的变化） |

⚠️ 注意：`TickEvent.*.Post` 那 11 处**不是纯改名** —— 加上 `phase` 判定才是语义等价的（否则代码会在每 tick 跑两次）。

#### 形态 B：`SavedData.Factory` 在 1.20.1 **不存在**

`javap net.minecraft.world.level.saveddata.SavedData` 实测，它只有
`save(CompoundTag)` / `setDirty()` / `isDirty()` / `save(File)` —— **没有 `Factory` 嵌套类**
（而且 `save` 是**抽象方法**、返回 `CompoundTag`）。

受影响 **4 个文件**：`MP4PlaybackSavedData` / `PadDocumentSavedData` / `PadMapScopeSavedData` /
`AudioPlaybackIndexSavedData`。1.20.1 的对应写法是
`ServerLevel.getDataStorage().computeIfAbsent(loader, supplier, name)`。

> 这两条都要**改结构**（加 phase 判定 / 换注册方式），不是规则表能套的 —— 已按"想清楚了再动"的规矩登记，
> 下一轮逐个改，每改完一个立刻 `gradlew compileJava` 复验。
### 3.38 第 35 次：**到"能出 jar"还差什么** —— 精确分解

把真实构建的 134 条按"是不是代码错误""在不在延后名单里"切开：

| 类别 | 条数 | 说明 |
| --- | --- | --- |
| **Mixin AP 诊断** | **37** | `Unable to locate obfuscation mapping …`，目标几乎全是**我们自己的方法**；不是类型错误，但会让构建失败 |
| **代码错误 · 在范围内** | **62** | 功能相关（A1–A6 的主战场） |
| **代码错误 · 已登记延后（渲染）** | **35** | 地形预览 14 / 投影仪·中控台渲染 8 / 视频 6 / `port/shim` 4 / 离屏 GUI 3 |

> 注意：**延后 ≠ 可以不编**。这 35 条同样挡着 jar —— 只是"画面对不对"不在本轮验收范围。

#### 因此"出 jar"需要三件事同时收掉

1. **62 条在范围内的代码错误** —— 按用户指定的优先级：先 mixin + client，再 network + blockentity；
2. **35 条延后渲染错误** —— 两条路：
   (a) 真正移植到 1.20.1 的旧渲染 API（`BufferBuilder`/`Tesselator`/`VertexBuffer`）；
   (b) **最小可编译改写**（保留公开签名、内部 `throw new UnsupportedOperationException("渲染延后")`），
       并**逐条登记**为"已延后"—— 这符合 MVP-SCOPE 的口径，但必须在文档里写死、不能静默；
3. **37 条 Mixin AP 诊断** —— 需要决定是"理解后接受"还是"换掉那几个 mixin 的注入方式"。
   我倾向先留着，用 A1 的运行期日志（`Mixing … into …` / `Mixin apply failed`）来判它到底有没有害。

#### 已查过错题集（按用户提醒）

针对本轮的 11 类 API（`DataComponents` / `RenderFrameEvent` / `MeshData` / `ByteBufferBuilder` /
`PlayerSkin` / `RenderStateShard` / `LineStateShard` / `ItemAttributeModifier` / `SavedData` / …）
逐条搜了 `misakanet-pending`：**只有 L21**（`SimpleUnbakedGeometry`/`UnbakedGeometryHelper`）命中，
而那条对应的文件（`ncpbmodel/*`）**已经用工程移植版覆盖，不再报错**。其余 10 类**没有现成条目** ——
也就是说这部分要现做，做完值得**补成新 lesson**。

（本轮还确认：L21 那条正是 `EulerElementsGeometry` 上游版 6543 B 编不过、工程版 11334 B 能编过的原因。）
### 3.37 第 34 次（续）：查错题集 → 发现**组装把运行期门禁弄坏了**

用户提醒去看 `port-1.21.1/misakanet-pending` 的错题集。查到 **L17**（`system-out-does-not-reach-latest-log`）
正好命中当前处境 —— 它记的正是"怎么验证 1.20.1 的 Mixin 接线在运行期真的成立"，
而且给了两条我现在就用得上的结论：

1. **优先采用框架自己打出的证据**，而不是自己打印的行：
   `Found valid mod file …` / `Registering mixin config: …` / `Mixing <X> … into <Y>`；
2. 运行期门禁要**同时读 `latest.log` 与 `debug.log`**，并把已知噪声显式声明为不判失败
   （其中一条噪声就包括"`JAVA_17` 超过 Mixin 0.8.5 的 `JAVA_13` 上限会告警但照常生效"）。

#### 🔴 照出来的真问题：我第 31 次删探针时，把门禁断言 C 一起废了

`tools/check_ingame_smoke.ps1` 的断言 C 原本**死盯** `Mixing P0RefmapProbeMixin … into …ItemStack`
—— 而那个阶段 0 的探针**正是我组装时删掉的**（换成了上游真实的 39 个 mixin）。
结果：这条断言以后**永远失败**，而且它证明的也只是"那一个探针注入了"。

**改法**（同时符合 L17 的建议②，也更强）：

```powershell
$mixed = Find-First $debug 'Mixing \S+ from net_music_can_play_bili\.mixins\.json into \S+'
```

* 匹配**我们自己 mixin 配置里的任意一个**真正被注入 ⇒ 覆盖真实的 39 个，而不只是一个探针；
* 不随探针去留而失效；
* **向后兼容**：实测旧探针行与真实 mixin 行**都能被命中**。

同时把负例自检的合成日志里那行也换成真实 mixin 名（`TileEntityMusicPlayerMixin`）。

#### ⚠️ 我自己又踩了 L14 那条坑

用编辑工具改 `.ps1` 时**把 BOM 弄丢了** ⇒ PowerShell 按 GBK 读中文 ⇒ **语法解析错误**。
（正是错题集 L14 说的：**`.ps1` 必须带 BOM，`.java`/`.gradle` 绝不能带**。）
已修：BOM 补回、并把同时丢掉的 `<`（注释式帮助的开头）也补回来。

**验证**：`check_tools` 通过（61 个文件，BOM 规则全对）；冒烟门禁 `-SelfTest` 报 **6 条 FAIL 且 exit 0**
（说明新断言确有鉴别力）；新正则对旧探针行/真实 mixin 行均命中。
### 3.36 第 34 次：**134 条里有 37 条不是代码错误** —— 并新增 `tools/gradle_ledger.py`

#### 新工具：把"错误"和"警告"真正分开

gradle 在 Windows 控制台输出的是 **GBK**，经 PowerShell 管道会变成 `????`（标签丢失 ——
这正是我第 32 次把"22 个警告"读成"22 个错误"的原因）。
`tools/gradle_ledger.py` 的做法：先用 `cmd /c "… > file 2>&1"` **按原始字节落盘**，
再按 GBK 解码，然后**按标签分类**。它现在是真实构建的唯一台账入口。

#### 134 条错误的真实构成

| 种类 | 条数 |
| --- | --- |
| javac：找不到符号 | **73** |
| **Mixin AP：`Unable to locate obfuscation mapping for @Inject/@Redirect target`** | **37** |
| javac：程序包不存在 | 23 |
| 其它 | 1 |

⇒ **真正需要改代码的是 97 条；另外 37 条是 Mixin 注解处理器的诊断**（不是类型错误）。
另有 **22 条警告**，也全部是 mixin 相关。

#### 那 37 条 Mixin 诊断是什么

按目标去重：`handle` **16** 条，其余 `syncNearbyPlayers` / `syncNearbySpectators` /
`isProjectorWithinRenderDistance` / `render` / `update` 等 —— **全是我们自己的方法**。

Mixin AP 要往 refmap 写"官方名 → SRG 名"；而我们自己的类在运行期**不被重映射**，
所以它找不到映射就报错。

#### ❌ 我试了两种抑制办法，都没用（负结果，如实记录）

| 办法 | 结果 |
| --- | --- |
| MixinGradle DSL：`disableTargetValidator = true` / `disableTargetExport = true` | 仍是 134 / 22 |
| 注解处理器参数：`-AdisableTargetValidator=true -AdisableTargetExport=true` | 仍是 134 / 22 |

#### ⚠️ A1 风险（**未验证的假设**，不当作结论）

若这 37 条意味着"这些 mixin 的 refmap 没有对应条目"，那么运行期它们能否生效就取决于
Mixin 对**自家类**是用字面名还是用映射名去查找。**我的假设是字面名（因此仍能生效）**，
因为 mod 类不参与混淆 —— **但这必须在运行期验证**，不能靠推断。
A1 要求的"日志无 Mixin 应用失败"正好会照出真相。
### 3.35 第 33 次：把"错误"和"警告"分开了 —— 并修正上一轮的读法

上一轮我把构建输出里的两个计数都读成了"错误"，**读错了**。把 gradle 输出**按原始字节存盘、再按 GBK 解码**之后，
标签就分开了：

| 标签 | 条数 |
| --- | --- |
| **错误** | **134** |
| **警告** | **22** |

（上一轮那行 `138 ??????` / `22 ??????`，其实是"138 个错误" + "**22 个警告**" —— 我把后者也读成了错误。）

#### ⚠️ 那 22 条警告**全部落在 mixin 包**，而且内容高度一致

```
警告: Unable to locate obfuscation mapping for @Inject target …
```

按目标方法去重：`handle` **16** 条，其余是 `isProjectorWithinRenderDistance` / `render` /
`syncNearbyPlayers` / `hasPreparationDemand` / `update` 等 —— **绝大多数是"我们自己的方法"**
（那些 `*PacketSubLevelScopeMixin` 就是 `@Inject` 进我们自己包的 `handle`）。

**为什么会有这条警告**：Mixin 注解处理器要往 refmap 里写"官方名 → SRG 名"的映射；
而**我们自己的类在运行期不会被重映射**（mod 类不混淆），所以它找不到映射、只能警告。
**这意味着这几个 mixin 的 refmap 里没有对应条目** —— 对"目标是自家类"的 mixin 通常仍能工作，
但**必须在运行期验证**（这正是 A1 里"日志无 Mixin 应用失败"要盯的东西）。

#### ❓ 134（真实构建）vs 333（离线 javac，同一份源码）—— **仍未解释**

又排除了一个：我在离线那套里也是**逐文件同一份组装源码**（757 个），不是 stage 版。
剩下的可疑点仍是**注解处理器的分轮行为**（真实构建开着 Mixin AP，离线用 `-proc:none`）。
**在两套数字对齐前，我继续两个都报、不拿任何一个宣称进展。**

#### 附：本轮实际修掉的

删掉 5 个 P0 脚手架后，**3 个工程版文件还在引用 `P0ModEntry.MOD_ID`**
（`init/ModBlocks`、`init/ModItems`、`ncpbmodel/NcpbModelGeometry`）—— 它们正是我从工程覆盖回去的那几个。
已改成 `NetMusicCanPlayBili.MODID`（真值 `net_music_can_play_bili`）。
真实构建 **160 → 134**，P0 痕迹归零。
### 3.34 第 32 次：真实构建的错误分布，以及**一个我没能解释的数字差异**

#### 真实构建（`gradlew compileJava`）报 **160** 个错误

```
138 errors
 22 errors
```

按包分布（这是**新的权威口径**）：

| 包 | 条数 |
| --- | --- |
| `mixin` | **61** |
| `client` | **49** |
| `network` | 15 |
| `blockentity` | 11 |
| `server` | 6 |
| `port` / `link` / `item` | 各 4 |
| `init` | 3 |
| `ncpbmodel` | 2 |

**mixin + client 占了 110/160** —— 下一轮的靶子很清楚。

#### ❓ 160 vs 离线 333：**我没能解释，如实记录**

我测了两个假设：

1. **假设一（已否定）**：差异来自"离线编译的是上游版 6 个文件"。
   实测：把**组装副本的 757 个文件**喂给同一套离线 javac ⇒ **333**（stage 版是 341）。
   ⇒ 我那 6 个移植版**只省了 8 条**，不是 203 条。**假设不成立。**
2. **假设二（已部分排除）**：`-Xmaxerrs` 截断。
   我把构建的 `-Xmaxerrs` 调到 100000 后重跑，仍然是 138+22=160 ⇒ **不是截断问题**。

**剩余可疑点（下一轮查）**：真实构建里**注解处理器（Mixin AP）是开着的**，
而离线那套用的是 `-proc:none`。带 AP 时 javac 会分轮处理，**报错行为可能不同**
（例如某些轮次里不再重复报同类错误）。另一个可能是 `--release 17` 与直接 `-source/-target` 的差异。

> **在两套数字对齐之前，我不会用其中任何一个去宣称"进展了多少"。**
> 现阶段两个数字都记录在案：离线 **333/185**（组装副本口径）、真实构建 **160**。

#### 另一个待查项

构建日志里出现过一行提到 `P0ModEntry` 的提示，但我在整个副本的
`*.toml / *.json / *.cfg / *.gradle / *.properties` 里**搜不到任何 P0ModEntry 引用** ——
**疑似 gradle 增量构建的陈旧状态**（第一次跑构建时 `mixins.json` 还是旧的）。
下一轮先 `gradlew clean` 再跑一次确认。
### 3.33 ✅ 第 31 次：**组装完成，真实 ForgeGradle 构建跑起来了**

按 §3.32 的四步，在**工程副本** `ncpb-assembled/`（不碰在用的 `ncpb-1.20.1/`）里完成组装：

```
① stage1-src 757 个文件并入            → 762
② 6 个文件用工程移植版覆盖回去          → 仍是 762（内容替换）
③ 删掉 5 个 P0 脚手架文件              → 757
④ gradlew compileJava --offline
```

**结果：构建配置本身是通的** —— 4 个 task 执行到了 `:compileJava`，而且：

* **refmap 真的生成了**：`Writing refmap to …/compileJava-refmap.json`、
  `Writing searge composite mappings to …compileJava-mappings.tsrg`
  ⇒ **Mixin Annotation Processor 工作正常**（这是 A1"日志无 Mixin 应用失败"的前置条件之一）；
* `mixins.json` 没有被拒绝（我把上游那份真实清单搬了过来，并补上 Forge 必需的
  `"refmap"` 字段、把 `compatibilityLevel` 从 `JAVA_21` 降到 **`JAVA_17`**）；
* 编译失败在 `:compileJava`，报 **138 个错误**（前几条是
  `ByteBufferBuilder` / `SortState` / `MeshData` / `PlayerSkin` —— 正是**已登记延后的渲染侧**）。

#### 🎉 顺带发现并修掉一个**会让 A1 直接失败**的问题

工程原来的 `mixins.json` 里**只登记了 `P0RefmapProbeMixin`**（阶段 0 的探针），
**39 个真实 mixin 一个都没登记**！照那样打出来的 jar，所有 mixin 都不会生效
（而且 `required: true` + 找不到探针类 ⇒ 可能直接崩）。已改成上游那份真实清单
（12 个 `mixins` + 26 个 `client`）。

#### ❓ 一个需要下一轮确认的**数字不一致**

离线 javac 报 **341 / 在范围内 193**，真实构建报 **138**。差 **203** 条。
最可能的解释：离线那套编译的是 `stage1-src`（**上游版**的 6 个文件），
而真实构建编译的是组装后的副本（**我移植版**的 6 个文件）——
上游那 3 个 `ncpbmodel/*` + `ModItems`/`ModBlocks` 在 1.20.1 上本来就编不过。
**但这只是假设，下一轮要实测确认**，确认之后离线台账的口径也要跟着对齐
（否则两套数字会一直打架）。
### 3.32 🔴 **组装计划**：stage 与工程有 6 个文件"各有一版"，必须以工程为准

第 30 次对比了 `ncpb-1.20.1/src/main/java`（11 个 java 文件）与 `stage1-src`，
发现一个**组装阶段必须处理**的冲突：

| 类别 | 数量 | 文件 | 处理 |
| --- | --- | --- | --- |
| **仅工程有**（P0 脚手架） | 5 | `P0ModEntry` / `P0ReobfProbe` / `P0RefmapProbeMixin` / `debug/ModelDump` / `init/ModCreativeTabs` | **组装时删除** —— 它们是阶段 0 的探针/夹具，不属于 mod 本体 |
| **两边都有但不同** | 6 | `init/ModBlocks` / `init/ModItems` / `ncpbmodel/EulerElement` / `ncpbmodel/EulerElementsGeometry` / `ncpbmodel/EulerElementsLoader` / `ncpbmodel/NcpbModelGeometry` | **以工程为准**（工程里是**已经手工移植好、能编译能跑**的版本） |

**证据**（不是推断）：`ncpbmodel/EulerElementsGeometry.java`
—— stage 里是**上游 1.21.1 的版本**（6543 B，用 NeoForge 的
`UnbakedGeometryHelper.resolveDirtyMaterial` / `bakeElementFace` 旧签名 / `face.cullForDirection()`，
在 1.20.1 上编不过）；工程里是**我移植后的版本**（11334 B，对着 1.20.1 的
`SimpleUnbakedGeometry.addQuads(...)` 签名写的）。**两者不是同一个东西。**

#### 结论：**不能简单地把 stage1-src 整个覆盖进 `src/main/java`**

正确的组装步骤（下一步）：
1. 把 `stage1-src` 的**其余 744 个文件**拷进 `ncpb-1.20.1/src/main/java`；
2. 上面那 **6 个文件用工程现有版本覆盖回去**；
3. **删掉 5 个 P0 脚手架文件**（并清理 `mods.toml` / `mixins.json` / build.gradle 里对它们的引用）；
4. 重新 `gradlew build`，看**真实构建**（而不是离线 javac）的第一批错误。

> ⚠️ 这一步会**首次动到工程本体**（在此之前 `stage1-src` 一直只是测量台，
> 外观层产物与门禁完全没受影响）。动之前要先把外观层的门禁跑一遍并留档，
> 之后每次都要能回到"外观层仍全绿"这个基线。
### 3.31 第 29 次：方块 API 的三处平台差异（−25 条）

| 1.21 | 1.20.1 | 处数 |
| --- | --- | --- |
| 覆写方法用 `protected`（BlockBehaviour 里也是 protected） | 必须用 **`public`**（1.20.1 的 BlockBehaviour 声明为 public），否则报 "cannot override … weaker access privileges" | 14 / 6 文件 |
| `InteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION` | `InteractionResult.PASS` | 5 |
| `BLOCKS.register("name", id -> new X(Properties.of()))`（NeoForge 的 `Function<ResourceLocation,Block>`） | `BLOCKS.register("name", () -> new X(Properties.of()))`（Forge 收 `Supplier<Block>`） | 6 |

> 第二条很典型：**枚举常量的改名不会报"找不到符号在别处"，只报 `cannot find symbol` + 那一行**，
> 所以要靠**看报错文本**把同类项归并 —— 5 处分布在 3 个文件里，一次规则就全清了。

**效果：366 → 341，在范围内 218 → 193。**
### 3.30 第 28 次：BlockEntity 的 NBT 方法名与泛型注册（−20 条）

| 1.21 | 1.20.1 |
| --- | --- |
| `super.saveAdditional(output, registries)` | `super.saveAdditional(output)` |
| **`super.loadAdditional(input, registries)`** | **`super.load(input)`** —— 不只是少个参数，**方法名都不一样** |
| `RegistryObject<BlockEntityType<?>, BlockEntityType<X>>` | `RegistryObject<BlockEntityType<X>>` |

第三行是**上一版双参规则漏掉的形态**：`DeferredHolder<A,B>` → `RegistryObject<B>` 那条只写了
`\w+` 型参数，遇到 `BlockEntityType<?>` 这种**带泛型的参数**就匹配不上。
补了一条 `RegistryObject<[^,]+,\s*(\w+(?:<[^>]*>)?)>`。**8 处 / 2 个文件。**

> 教训与第 6 条同一类：**"通用规则"往往只覆盖了当时想到的那一种形态**。
> 漏掉的形态不会报"规则没生效"，而是留下一批"看起来不像同一类"的错误
> （这里是 `wrong number of type arguments`）—— 只有去看**具体报错文本**才发现它们同源。

**效果：386 → 366，在范围内 238 → 218。**
### 3.29 第 26 次：`TooltipContext` 与**清理死 import**（本轮 −38 条）

| 1.21 | 1.20.1 |
| --- | --- |
| `Item.TooltipContext`（嵌套类） | `net.minecraft.world.level.Level`（`appendHoverText(ItemStack, @Nullable Level, List<Component>, TooltipFlag)`） |

**更值钱的是后半段：清理"没人用的 import"。** 包迁到 `SimpleChannel` 之后，
大量文件**只剩一个没人引用的 `import …CustomPacketPayload;`** —— 每个都报一条
`package … does not exist`。先核对"该字符串出现次数 ≤ import 次数"（即正文没用到），
再批量删除 import；实测 **31 个文件**受影响（上游扫描里两条规则共命中 115 处）。

> ⚠️ 这里有个**顺序陷阱**要留意：`port_rename` 跑在 codemod **之前**，
> 而 codemod 靠 `'CustomPacketPayload' not in text` 判断"这个包是否已迁移"。
> 我删的只是 **import 行**，正文里的 `implements CustomPacketPayload` 仍在，
> 所以分类不受影响 —— 但**如果哪天顺手把正文也清了，codemod 就会把未迁移的包误判成"已迁移"并跳过**。
> 这一点已写进注释。

**效果：417 → 386，在范围内 268 → 238。**
### 3.27 第 25 次：**编译器抓出了我自己垫片里的 bug**（本轮最大一笔，−22 条）

`CustomData.updateOn` 第一版把回调参数写成了 `CompoundTag`：

```java
public static void updateOn(ItemStack stack, Consumer<CompoundTag> action)   // ← 错
```

但原代码里的 lambda 长这样：`customData -> customData.update(tag -> …)` —— 它调的是
**`CustomData.update`**。所以参数类型必须是 **`CustomData`**：
```java
public static void updateOn(ItemStack stack, Consumer<CustomData> action) { … action.accept(new CustomData(t)); … }
```

改对之后：**`PadItem` 11 → 7、`HolographicGlassesItem` 7 → 3、全工程 447 → 425。**

> **教训：垫片"能编译"≠"接口形状对"。** 第一版之所以过编译，是因为 javac 只看到
> `updateOn(stack, lambda)` 的**外部**调用点，lambda 体里的 `customData.update(...)`
> 只有在参数类型定下来之后才会被检查 —— 而我当时把它写成了 `CompoundTag`，
> 于是那几处调用点报"找不到符号"，**位置在调用点、根因在垫片**。
> 遇到"一批调用点同时报同一个符号找不到"时，先怀疑**共同依赖的接口**，而不是逐个改调用点。

### 3.28 第 25 次：另外两处

| 1.21 | 1.20.1 |
| --- | --- |
| `public Holder<SoundEvent> getEquipSound()` | `public SoundEvent getEquipSound()`（1.20.1 的 `Equipable` 返回 `SoundEvent`） |

#### 🔴 仍未做（累计三项）

1. 物品处理器的 **BE 挂载**（§3.25）；
2. 属性修饰的 **事件化**（§3.26）；
3. `Item.TooltipContext` → `appendHoverText(ItemStack, Level, List, TooltipFlag)`（1.21 的嵌套
   `TooltipContext` 在 1.20.1 不存在；涉及改动方法签名，**且要看方法体怎么用 context**）；
   以及 `ItemStack.OPTIONAL_CODEC` 这类 1.21 codec 的替换。
### 3.26 第 24 次：物品属性常量与附魔书（`ModItems` 15 → 9）

| 1.21 写法 | 1.20.1 写法（均已 `javap` 核实） |
| --- | --- |
| `Mutable stored = new Mutable(ItemEnchantments.EMPTY); stored.set(e, lv); book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());` | `book.enchant(enchantment.value(), level);`（三行并一行） |
| `Operation.ADD_VALUE` | `Operation.ADDITION` |
| `new AttributeModifier(ResourceLocation.tryBuild("ns","path"), 1.0, Operation.ADDITION)` | `new AttributeModifier("path", 1.0, Operation.ADDITION)`（1.20.1 三参构造器吃的是 **String 名字**，不是 ResourceLocation） |

**效果：全工程 453 → 447，在范围内 304 → 298，`ModItems` 15 → 9。**

#### 🔴 `ModItems` 剩下的 9 条：**属性修饰的挂载方式要改结构**（未做）

`ItemAttributeModifiers.builder()…build()` + `Properties.attributes(...)` 在 1.20.1 **都不存在**。
已核实的替代是 **`net.minecraftforge.event.ItemAttributeModifierEvent`**
（`addModifier(Attribute, AttributeModifier)` / `getItemStack()` / `getSlotType()`），
但那意味着：

1. 把两个 `*AttributeModifiers()` 方法改写成**静态事件处理器**；
2. 在**主类**上把处理器注册到 `MinecraftForge.EVENT_BUS`。

**这会动两个文件、且主类刚刚才编译通过（0 错误）** —— 所以我没有顺手动它，
而是明确登记为未完成。下一轮做的时候要**先备份主类的 0 错误状态**，改完立刻复验。
### 3.25 第 23 次：主类编译通过 —— 但能力挂载**只做了一半**，必须记账

`javap` 实测：**Forge 1.20.1 的 `RegisterCapabilitiesEvent` 只有 `register(Class<T>)`**，
没有 NeoForge 的 `registerBlockEntity(...)`。所以：

| 事项 | 状态 |
| --- | --- |
| 在 `RegisterCapabilitiesEvent` 里**登记能力类型**（`event.register(IItemHandler.class)`） | ✅ 已做 |
| 把物品处理器**挂到 `ModernTurntableBlockEntity` 上**（在 BE 里覆写 `getCapability`，或用 `AttachCapabilitiesEvent<BlockEntity>`） | 🔴 **未做** |

后果：**漏斗之类的自动化取放会失效**；方块自己的 UI 路径不受影响。
这一条**属于 A2/A3 之外的能力兼容**，且**没有静默跳过** —— 明确记在这里。

> 之所以先只做一半：这两半的修法完全不同（前者是一行 API 替换，后者要在 BE 里写
> `getCapability` 覆写并处理 `LazyOptional` 的生命周期/invalidate）。
> 混在一起做容易把 BE 改坏，且难以验证。

**效果：主类 1 → 0 条（编译通过），全工程 454 → 453。**
### 3.24 第 22 次：事件总线与配置（NeoForge → Forge）

| NeoForge | Forge 1.20.1 | 处数 |
| --- | --- | --- |
| `addListener(X.class, this::m)` | `addListener(this::m)` | 1 |
| `addListener(X.class, e -> …)` | `addListener((X e) -> …)`（**lambda 参数必须显式写类型**，否则泛型推不出来） | 2 |
| `modContainer.registerConfig(Type.COMMON, SPEC)` | `ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC)` | 1 |

**效果：主类 5 → 1 条，全工程 458 → 454。**

> ⚠️ 又踩了一次**幂等性**（第 3 次）：`Type.COMMON → ModConfig.Type.COMMON` 这条规则的产出
> **仍然匹配自己**（`ModConfig.Type.COMMON` 里还含 `Type.COMMON`），第二趟会变成
> `ModConfig.ModConfig.Type.COMMON`。修法是加 `(?<!ModConfig\.)` 负向后顾。
> **自检当场就把它抓出来了** —— 这条自检到今天已经抓出 3 次真 bug，价值远超"洁癖"。
### 3.23 第 21 次：主类的 import / 能力常量

| 问题 | 修法 |
| --- | --- |
| `import net.minecraftforge.common.NeoForge;`（该类在 Forge 不存在） | 删掉 —— `NeoForge.EVENT_BUS` 已换成 `MinecraftForge.EVENT_BUS`，import 成了多余的（2 处） |
| `import ...capabilities.Capabilities.ItemHandler;` | 1.20.1 是常量 **`ForgeCapabilities.ITEM_HANDLER`**，没有嵌套的 `ItemHandler` 类型 |
| 正文里的 `ItemHandler.BLOCK` | → `ForgeCapabilities.ITEM_HANDLER` |

**效果：`NetMusicCanPlayBili` 6 → 5 条，全工程 462 → 458。**

> ⚠️ 中间踩了一次"**假状态**"：`Capabilities -> ForgeCapabilities` 那条规则变成零命中后，
> 触发了零命中自检 ⇒ **`--apply` 直接退出、根本没写盘**，于是我看到的 462/7 是**半应用状态**，
> 一度以为改动让主类错误从 6 涨到 7。把它标进 `OPTIONAL_RULES` 后重跑，才得到真实的 458/309。
> **教训：`--apply` 的退出码必须看** —— 它非 0 就说明没写盘，此时的编译数字没有意义。
### 3.21 第 20 次：`ResourceLocation.fromNamespaceAndPath` → `tryBuild`（**净效果为零**，但要如实说明）

44 处 1.21 专有静态工厂改成了 1.20.1 的 `ResourceLocation.tryBuild(ns, path)`
（残留 0、产出 45）。**但错误总数 462 → 462，一点没降。**

原因：这些行**本身还有别的错**（例如同一行的
`new AttributeModifier(ResourceLocation…, 1.0, Operation.ADD_VALUE)` —— 1.20.1 的
`AttributeModifier` 根本没有 `(ResourceLocation, double, Operation)` 这个构造器），
所以把符号改对了，那一行照样报错。

**为什么还是收下了**：这不是"看起来更广但实测有害"（第 12 次那种），
而是"**1.21 的 API 在 1.20.1 根本不存在，迟早都要改**"，且没有让任何指标变差。
**但我不把它记成"进展"** —— 这一轮在数字上是零。

### 3.22 为下一个语义改写准备好的**已核实 API**（`javap` 实测，下轮直接用）

`ModItems.java` 剩下 15 条，根因是三处 1.21 独有 API。已核实的对应关系：

| 1.21 | Forge 1.20.1（**已核实存在**） |
| --- | --- |
| `AttributeModifier(ResourceLocation, double, Operation.ADD_VALUE)` | `AttributeModifier(String name, double amount, Operation.ADDITION)` 或 `AttributeModifier(UUID, String, double, Operation)` |
| `Operation.ADD_VALUE` | `Operation.ADDITION` |
| `ItemAttributeModifiers.builder()…build()` + `Properties.attributes(...)` | **`net.minecraftforge.event.ItemAttributeModifierEvent`**：`addModifier(Attribute, AttributeModifier)` / `getItemStack()`（事件总线注册） |
| `Mutable stored = new Mutable(ItemEnchantments.EMPTY); stored.set(e, lv); book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());` | `book.enchant(enchantment.value(), level);`（`ItemStack.enchant(Enchantment, int)` 已核实） |

> ⚠️ 这个改写会**动结构**（属性修饰要挪到 Forge 事件里注册），不是正则能套的 ——
> 所以适合走 `tools/port-manual/` 或手工，别硬塞进规则表。


```
public class ...network.message.MusicToClientMessage {
  private final BlockPos pos;        // ← 全是 private final
  private final String url;
  private final String rawUrl;
  private final int timeSecond;
  private final String songName;
  public MusicToClientMessage(BlockPos, String, String, int, String);
  public static MusicToClientMessage decode(FriendlyByteBuf);
  public static void encode(MusicToClientMessage, FriendlyByteBuf);
  public static void handle(MusicToClientMessage, Supplier<NetworkEvent$Context>);
  // 没有任何 public 访问器
}
```

而 **1.21.1 的 netmusic 有** `pos()` / `url()` / `rawUrl()` / `songName()` / `timeSecond()`
（见 `netmusic-api-diff.md`，那 5 条都标在"仅 1.21.1 有"）。
本模组的 `mixin/MusicToClientMessageClientMixin.java` 正是按 1.21.1 的访问器写的，
所以在 1.20.1 上**根本读不到数据** —— 这是该文件 15 条错误的根因。

#### 为什么不能靠 AccessTransformer

工程的 `META-INF/accesstransformer.cfg` 目前是空骨架，很容易想到用它把字段放开。
但 **AT 的作用对象是 Minecraft 的类**：对第三方 mod 依赖 jar，
ForgeGradle **不会**在编译期把 AT 应用到依赖上（javac 看到的仍是 `private`），
所以"改 AT"解决不了编译错误。**要读依赖的私有字段，得用 Mixin 访问器或反射。**

#### ✅ 已解决（第 17 次）：反射桥

`tools/port-manual/.../port/bridge/NetmusicMessageAccess.java` —— 用反射读那 5 个私有字段
（字段查找 `static` 一次性缓存；只服务这 6 个调用点，热路径开销可忽略）。
配一条规则把 `message.url()` 这类调用改成 `NetmusicMessageAccess.url(message)`。

**效果：`MusicToClientMessageClientMixin` 15 条 → 0 条，桥自身 0 错误。**

> **为什么不用 AccessTransformer**：AT 的作用对象是 Minecraft 的类；对第三方 mod 依赖 jar，
> ForgeGradle 不会在编译期把 AT 应用到依赖上，javac 看到的仍是 `private`，编译错误照旧。
>
> **为什么不用 Mixin 访问器**：那要在 mixin 配置里登记、还要保证运行期真的应用得上；
> 反射零配置、当场可编译可验证。**留作后续优化**（运行期热路径若要更快，可换成 `@Accessor`）。


> 这条也解释了 `netmusic-api-diff.md` 的价值：它由反射生成、以"仅 1.21.1 有 / 仅 1.20.1 有"
> 列出差异，**正好把这类"名字对不上"的问题提前摆在台面上** —— 没有它，这次又是靠 javap 现场追。

> ⚠️ 第 16 次里**规则顺序问题出现了三次**（import 被"类型"规则改坏、声明规则与工厂规则互相吃掉），
> 全部靠"零命中会点名"这条自检抓出来。这已是同一类问题第 4 次出现
> （前面还有 `DeferredHolder<A,B>`、`sendToPlayer` 前缀命中 `sendToPlayers*`）。
> 缓冲办法：**能写成不依赖顺序的正则（`(?:A|B)`）就不要依赖顺序**；
> 必须排序的，就在注释里写明"必须排在 X 之后"及其原因。


> **在范围内 1468 → 350（−76.2%）**。
>
> ✅ **网络层（阶段 2）结构性完成**：42 个 `*Packet` 全部有静态 `encode`/`decode`/`handle`，
> `NcpbNetwork`（42 条注册）**0 错误**，全工程 `CustomPacketPayload` 引用**清零**。
> 对应 MVP-SCOPE 的 **A4**（双向包）—— 但**只完成了"结构"，"真的到达并生效"仍需运行期验证**。

### 3.17 ✅ 已修：规则表失去幂等性（第 14 次引入，第 15 次修掉）

**症状**：`--apply` 的幂等自检失败 —— 再跑一遍仍有 37 个文件会变。

**根因**：上游源码里的手写 codec 声明写的是 `decode(RegistryFriendlyByteBuf buf)`，
而我新加的 `decode 转 static` / `encode 转 static 且换序` 两条规则**只匹配 `FriendlyByteBuf`**。
于是它们的生效时机取决于"类型重命名"那一步跑在前面还是后面 ——
第一趟匹配不上（或只匹配到一部分），第二趟才生效 ⇒ 不幂等。

**修法**：把缓冲类型写成 `(?:Registry)?FriendlyByteBuf`，与执行顺序无关。

**意外收获**：修完之后错误总数**又降了一截（536 → 503）**，而且
`NcpbNetwork` 从 31 条降到 **0 条**。说明这个不幂等**本身就在掩盖一个覆盖缺口** ——
有一部分包的方法没被转成静态，注册器自然引用不到。

> **教训：幂等自检不是"洁癖"，它抓的是真 bug。**
> 一条规则"最终会生效"和"第一趟就生效"差别很大：前者会让依赖它的规则看到旧形态，
> 产出就取决于规则顺序 —— 而这种偏差只在第二次应用时才显形。

### 3.16 第 6 种形态：手写的 instance `decode`/`encode` 对

`LyricProjectorConfigPacket` / `VideoProjectorConfigPacket` 用的是**逐字段手写**的一对方法：

```java
public LyricProjectorConfigPacket decode(FriendlyByteBuf buf) {   // 注意：不是 static
   return new LyricProjectorConfigPacket((BlockPos)BlockPos.STREAM_CODEC.decode(buf),
                                         (Float)ByteBufCodecs.FLOAT.decode(buf), ...);
}
public void encode(FriendlyByteBuf buf, LyricProjectorConfigPacket packet) { ... }  // 形参顺序也不对
```

修法（4 类规则，实测把这两个包从各 **19 条降到各 1 条**）：

| 规则 | 映射 |
| --- | --- |
| 原版 codec 解码 | `ByteBufCodecs.FLOAT/INT/BOOL/VAR_INT/…`.decode(buf) → `buf.readFloat()/readInt()/…` |
| 原版 codec 编码 | 同上 `.encode(buf, v)` → `buf.writeX(v)` |
| `stringUtf8` | `ByteBufCodecs.stringUtf8(n)…` → `buf.readUtf(n)` / `buf.writeUtf(v, n)` |
| `BlockPos` | `BlockPos.STREAM_CODEC.decode/encode` → `buf.readBlockPos()` / `buf.writeBlockPos(p)` |
| instance → static | `public X decode(...)` → `public static X decode(...)`；`encode(buf, packet)` → `static encode(packet, buf)` |

#### ⚠️ 同时修掉一个**误伤**（这条很有代表性）

原来的委托规则写的是 `\b(\w+)\.STREAM_CODEC\.decode\(`，**没有限定是"我们的包"**，
于是它把原版也改了：`BlockPos.STREAM_CODEC.decode(buf)` → 不存在的 `BlockPos.decode(buf)`。
**修法：限定成 `\w*Packet`。** 上游扫描里那 14 处命中，其实 **13 处是原版 `BlockPos`、只有 1 处是真正的委托包** ——
说明这条规则原来的"覆盖面"绝大部分是**错的**。

### 3.17 ~~🔴 未解决：规则表失去了幂等性~~ → ✅ 已在第 15 次修掉（见上）

### 3.15 阶段 3 的打法：**垫片优先，而不是逐行改写**

1.21 用 `DataComponents.CUSTOM_DATA` 把自定义 NBT 包成 `CustomData` 对象；
1.20.1 的物品数据**就是** `ItemStack.getTag()` 返回的裸 `CompoundTag` ——
**同一份数据，只是外面多了一层壳**。

所以没有去逐行改写 5 个大文件（`PadItem` / `MP4Item` / `LinkHelper` / `AudioLinkData` /
`HolographicGlassesItem`），而是写了一个同 API 的**垫片**
`tools/port-manual/.../port/nbt/CustomData.java`，再加 6 条规则把 `ItemStack` 上的
`get/set/update/remove(DataComponents.CUSTOM_DATA, …)` 换成垫片的静态方法。

**结果：消掉 74 条在范围内的错误（515 → 441），垫片自身 0 错误。**

为什么这样更对：
* 逻辑**一行不改**，所以不存在"改写时把语义改错"的风险；
* 垫片读写的是 `ItemStack` 的**真实 NBT**，存档往返是同一份数据（A5 需要的正是这个）；
* 真正需要人判断的只剩 **3 处 `CUSTOM_DATA` + 6 处其它 `DataComponents`**
  （`ENCHANTMENT_GLINT_OVERRIDE` / `STORED_ENCHANTMENTS`），一眼数得清。

> ⚠️ **故意不实现** `getAsJsonObject()` / `getAsJsonArray()`：1.20.1 的 `CompoundTag` 没有它们，
> 硬造一个语义不对的实现会**静默**出错；不提供 ⇒ 编译器把用到的地方点出来，人工处理。

> 垫片放在 `port/nbt/` 而**不是** `port/shim/` —— 因为 `port/shim/` 在台账里被登记为
> **延后**，放进去等于把错误藏起来，那就成了"静默跳过"。
>
> ⚠️ 第 4 次同时修正了"延后"分类的口径（见 §5），第 1–3 次的数字是**按新口径重算**的。
>
> 第 5 次错误数只降 25 条，但**结构上的进展更大**：注册器重写后，
> `NcpbNetwork.java` 报出的 47 条错误**精确地等于剩下 8 个未迁移的包**
> （每个 6 条 = encoder/decoder/consumer 各 2 条），说明生成物对已迁移的 34 个包全对。

> **在范围内 1468 → 768（−47.7%）**。
>
> ⚠️ **第 4 次同时修正了"延后"分类的口径**（见 §5），所以第 1–3 次的数字是**按新口径重算**的 ——
> 旧口径把 `client/terrain/`（13 个文件，全是地形预览渲染）和 `port/shim/`
> （6 个文件，全是渲染垫片）算进了"在范围内"，**虚高了在范围内的负担**。
> 比较进度时一定要用同一套口径重算，否则"数字下降"里会混进分类变化。

> **这个数字就是进度。** 每改完一段就重跑 §1 的三步并把这里更新一行。
> 注意：`--top` 之外的错误种类与"最烂文件"榜也会随之下移，别只看总数。

### 2.1 在范围内的错误种类（第 2 次）

| 种类 | 条数 |
| --- | --- |
| `cannot-find-symbol` | 1030 |
| `package-missing` | 303 |
| `other` | 33 |
| `args-mismatch` | 16 |
| `incompatible-types` | 15 |
| `not-abstract` | 5 |
| `no-suitable-method` | 2 |
| `bad-override` | 1 |

### 2.2 缺失的类型（**这就是"要补哪套 API"的清单**）

| 类型 | 次数 | 1.20.1 的对策 |
| --- | --- | --- |
| `RegistryFriendlyByteBuf` | 258 | 用 `FriendlyByteBuf` + 手工取注册表（1.20.1 没有这个类） |
| `CustomPacketPayload` | 173 | 换成 `SimpleChannel` + 自定义消息（阶段 2 主体） |
| `Type` | 126 | `CustomPacketPayload.Type` 的残留，随上一条一起消失 |
| `StreamCodec` | 105 | 手写 `encode`/`decode(FriendlyByteBuf)` |
| `clamp` | 67 | `Mth.clamp`（1.20.1 用 `Mth`，不是新版的 `MathHelper`/实例方法） |
| `IPayloadContext` | 60 | `NetworkEvent.Context`（`Supplier<Context>`） |
| `CustomData` | 49 | 回到 NBT（`CompoundTag`） |
| `DataComponents` | 43 | 回到 NBT（阶段 3） |
| `ItemInteractionResult` | 33 | 1.20.1 用 `InteractionResult` |
| `ByteBufferBuilder` | 31 | 1.21+ 的类，**属渲染**，登记延后 |
| `addVertex` | 30 | 顶点写入 API 变化（渲染，延后） |
| `STREAM_CODEC` | 29 | 随 `StreamCodec` 一起消失（`SimpleChannel` 用 id 注册） |
| `EventBusSubscriber` | 26 | 1.20.1 是**嵌套注解** `@Mod.EventBusSubscriber` |
| `DeferredItem` | 13 | `RegistryObject<Item>` / `ItemLike` |
| `MeshData` | 17 | 渲染，延后 |
| `EquipmentSlotGroup` | 9 | 1.20.1 没有，需按目标改写 |

### 2.3 最"烂"的文件（在范围内）

`network/*Packet.java` 霸榜（每个 22–36 条），其次是
`init/ModItems.java`(34)、`item/PadItem.java`(27)、`block/ModernTurntableBlock.java`(22)、
`block/VideoProjectorBlock.java`(21)。
**与 HANDOVER §7.2 的判断完全一致：网络是最大的一块。**

---

> 说明：§3.x 是按轮次**追加**的，编号顺序可能与正文顺序不一致；以内容为准。

## 3. 阶段 2（网络）的目标样板 —— **已编译验证**

NeoForge 的 payload 体系在 Forge 1.20.1 上**整体不存在**，不是改名能解决的。所以先写了一份
**目标样板并让它对着真实 1.20.1 类路径编译通过**（`javac exit=0`，无 removal 警告）：

**样板文件**：`tools/_net-probe/com/zhongbai233/net_music_can_play_bili/network/NcpbNetwork.java`
（含一个最小的 `MP4InventoryDeviceIdPacket`，int + UUID）
**验证**：`javac -cp <build/compile-classpath.txt> …` → **exit 0**

### 3.1 三处映射（批量改造照这个来）

| NeoForge（1.21.1） | Forge 1.20.1 | 备注 |
| --- | --- | --- |
| `implements CustomPacketPayload` + `static Type<T> TYPE` | `SimpleChannel.messageBuilder(Class, int id, NetworkDirection)` | id 集中登记，**必须稳定** |
| 匿名 `StreamCodec<RegistryFriendlyByteBuf, T>` 的 `encode/decode` | 静态 `encode(T, FriendlyByteBuf)` / `T decode(FriendlyByteBuf)` | 结构一一对应，可 codemod |
| `handle(T, IPayloadContext)` + `context.enqueueWork(...)` | `handle(T, Supplier<NetworkEvent.Context>)` + `ctx.enqueueWork(...)` + **`ctx.setPacketHandled(true)`** | 最后一句**不能漏**：Forge 不写它包会被丢 |
| `RegistryFriendlyByteBuf` | `FriendlyByteBuf` | 需要注册表时手工取（47 文件 / 258 处） |
| `new ResourceLocation(ns, path)` | `ResourceLocation.tryBuild(ns, path)` | 构造器已标记"计划删除"；`tryBuild` 返回 `@Nullable` |

### 3.2 网络包规模

`network/` 共 **73 个 `.java`**，其中 **42 个 `*Packet.java`**，按形态分三类：

| 形态 | 个数 | 处置 |
| --- | --- | --- |
| 匿名 `StreamCodec` | **31** | 已由 `tools/port_network_codemod.py` 机械改造完成 |
| `StreamCodec.composite(...)` | **5** | **未处理**，需手工展开组合语义（逐条列出，不静默跳过） |
| 其它形态 | **6** | **未处理**，逐条列出 |

其余 31 个是基础设施（`MP4PlaybackSyncManager.java` 单文件 56 KB、`MP4DeviceLocationIndex.java` 25 KB 等），
需要按逻辑逐个过。

### 3.3 codemod 的产出**语法自检**（补上的缺口）

`tools/port_network_codemod.py` 在产出前会检查：括号平衡、以及 `, {` / `implements {` /
`implements ,` / `, ,` / `(,` 这类断掉的片段 —— 命中就**撤下该文件并列入"形态不符"**。

为什么必须加这一道：实测有的 record 同时 `implements CustomPacketPayload` 和别的接口，
早先直接 `replace(' implements CustomPacketPayload','')` 会留下孤零零的逗号 ⇒ 产出
`), ClientMediaSyncPayload {`。**而 javac 遇到解析错误会提前停下**（整棵树只报 2 条），
看起来像"几乎全好了" —— 这是最危险的一种假信号。

---

## 3.5 网络注册器：**已完成**（从原文读出方向，没有推断）

本 mod 的**唯一**注册点是 `network/ModernTurntableNetwork.java`，里面逐条写了
`registrar.playToServer(...)` / `playToClient(...)` —— **共 42 条（18 个 TO_CLIENT / 24 个 TO_SERVER）**。
所以方向是**读出来的**。

`tools/port_network_registrar.py`（幂等、带负例）做的三件事：

1. 生成 `network/NcpbNetwork.java`：42 个包按 `.messageBuilder(cls, id++, NetworkDirection.X)`
   注册，写法照**编译验证过的**样板；
2. 删掉旧的 `ModernTurntableNetwork.java`；
3. 把主类 `modEventBus.addListener(ModernTurntableNetwork::register);`
   改成直接 `NcpbNetwork.register();`（Forge 侧在 mod 构造期注册，不走 mod 事件总线）。

> ⚠️ **包 id 按原文出现顺序从 0 递增 —— 顺序不能改**：改了就是客户端/服务端包号错位。
> 工具算出的 42 条与原文 `registrar.*(` 的出现次数一致；**不一致会直接报错退出**（负例已验证）。

### 3.6 流水线顺序（很重要）

```powershell
& $py tools\port_rename.py --apply            # ① 从来源重建 stage1-src（会整目录重建！）
& $py tools\port_network_codemod.py --apply   # ② 包类改造
& $py tools\port_network_registrar.py --apply # ③ 注册器重写
& $py tools\port_manual.py --apply            # ④ 覆盖手写改写件（tools/port-manual/）
# ⑤ 重新生成文件清单（**每次增删文件都要做**）
Get-ChildItem stage1-src -Recurse -Filter *.java | ForEach-Object { $_.FullName } |
    Set-Content _port-compile\files.txt -Encoding ascii
# ⑥ javac + 台账
```

**①会整目录重建**，所以 ②③④ 必须在 ① 之后按序重跑；顺序错了就会丢改动。
**④ 不能省**：`files.txt` 是快照，增删文件后不重生成，javac 会报
`error: file not found: …ModernTurntableNetwork.java`（退出码 2，**不是**编译错误）——
第 5 次就踩了这个，台账显示"0 条错误"，看着像全好了。

### 3.7 剩余的网络工作

42 个包里：**25 个已迁移**、5 个 `StreamCodec.composite`、12 个仍引用
`CustomPacketPayload`（多是被它们实现的标记接口 `ClientMediaSyncPayload` /
`ClientMediaTimelinePayload` 带出来的，要一并改）。

---

### 3.8 发送点迁移：**已完成**（89 处，0 残留）

**事实（javap 真实 Forge jar 核实）**：Forge 1.20.1 的 `PacketDistributor` **只有目标点字段**
（`PLAYER` / `NEAR` / `ALL` / `SERVER` / `TRACKING_ENTITY` / `TRACKING_ENTITY_AND_SELF` /
`TRACKING_CHUNK` / `DIMENSION` / `NMLIST`），**没有任何 `sendToXxx` 静态方法** ——
那批静态方法是 NeoForge 加的。发送必须走 `SimpleChannel`：

| NeoForge 写法 | Forge 1.20.1 写法 | 处数 |
| --- | --- | --- |
| `PacketDistributor.sendToServer(msg, new CustomPacketPayload[0])` | `CHANNEL.sendToServer(msg)` | 46 |
| `sendToPlayer(player, msg, new CustomPacketPayload[0])` | `CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg)` | 39 |
| `sendToPlayersNear(level, null, x, y, z, r, msg)` | `CHANNEL.send(PacketDistributor.NEAR.with(TargetPoint.p(x, y, z, r, level.dimension())), msg)` | 3 |
| `sendToPlayersTrackingEntityAndSelf(e, msg)` | `CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), msg)` | 1 |
| （尾参）`, new CustomPacketPayload[0]` | 删掉（Forge 的 `send` 不需要变参） | 89 |

**验证**：改造后 `PacketDistributor.sendTo` 残留 **0**；错误台账里提到
`TargetPoint` / `TRACKING_ENTITY_AND_SELF` / `NcpbNetwork.CHANNEL` / `PacketDistributor` 的条目 **全为 0**
（即这些转换本身没有引入任何编译错误）。

> ⚠️ 规则顺序有语义：`sendToPlayer\(` 的正则会**前缀命中** `sendToPlayersNear` /
> `sendToPlayersTrackingEntityAndSelf`，所以两条 `sendToPlayers*` 必须排在它**前面**。
> 排在后面的话，前者会先命中 43 处、把后者的形态破坏掉。

---

### 3.9 手写改写层 `tools/port-manual/`（有些形态不能机械改）

`StreamCodec.composite(...)`（5 个包）**没有**做 codemod：它把"字段顺序"分散在
codec/getter 交替的参数表里，还要逐个翻译 `ByteBufCodecs.*`，
机械替换很容易把**顺序**改错 —— 而顺序错了是"**能编译、但字段错位**"的静默 bug。

手写件**不能**直接写进 `stage1-src`（`port_rename.py --apply` 会整目录重建 ⇒ 丢改动）。
所以放在 `tools/port-manual/<相对路径>`，由 `tools/port_manual.py` 在自动改造**之后**覆盖进去。
流水线因此仍然完全可复算。

**已完成的样板**：`network/SpeakerConfigPacket.java` —— 编译后该文件 **0 错误**。

#### 1.21 codec → 1.20.1 `FriendlyByteBuf` 方法对照（写这 5 个包时照抄）

| 1.21 | 1.20.1 |
| --- | --- |
| `BlockPos.STREAM_CODEC` | `writeBlockPos` / `readBlockPos` |
| `ByteBufCodecs.INT` | `writeInt` / `readInt` |
| `ByteBufCodecs.FLOAT` | `writeFloat` / `readFloat` |
| `ByteBufCodecs.BOOL` | `writeBoolean` / `readBoolean` |
| `ByteBufCodecs.VAR_INT` | `writeVarInt` / `readVarInt` |
| `ByteBufCodecs.stringUtf8(n)` | `writeUtf(s, n)` / `readUtf(n)` |

#### 2. 两个必须记住的 API 差异

* **`IPayloadContext.player()` → `NetworkEvent.Context.getSender()`**。
* ⚠️ **`getSender()` 的声明返回类型就是 `ServerPlayer`**，所以
  **不能**再写 `if (context.getSender() instanceof ServerPlayer player ...)` ——
  javac 会报 `expression type ServerPlayer is a subtype of pattern type ServerPlayer`
  （Java 17 起这是错误，不是警告）。正确写法是先取出来判空：
  ```java
  ServerPlayer player = context.getSender();
  if (player != null && player.level() instanceof ServerLevel level) { ... }
  ```
  客户端侧它会返回 null，所以判空是**必要的**，不只是为了过编译。

---

### 3.10 网络的**第三种形态：委托包**（第 8 次发现的）

有些类自己不是 payload，而是把编解码**委托**给别人：

```java
MP4StatePacket state = (MP4StatePacket) MP4StatePacket.STREAM_CODEC.decode(buffer);
MP4StatePacket.STREAM_CODEC.encode(buffer, packet.state());
```

已加规则机械改（`X.STREAM_CODEC.decode(buffer)` → `X.decode(buffer)`；
`encode(buffer, v)` → `X.encode(v, buffer)`，**形参顺序要对调**），共 **28 处 / 12 个文件**。

> ⚠️ 留意 `ItemStack.OPTIONAL_STREAM_CODEC` 这类**两个点**的原版 codec：
> 上面的正则只匹配 `单标识符.STREAM_CODEC`，所以**不会**误伤它 ——
> 但这些要手工翻成 `readItem()` / `writeItemStack(...)`，**已登记为剩余工作**。

### 3.11 已完成的手写包（6 个，全部 0 错误）

| 包 | 形态 | 字段顺序 |
| --- | --- | --- |
| `SpeakerConfigPacket` | composite | pos → channelIndex → volume → autoMixJoc |
| `MP4DeviceIdPacket` | composite | hand(bool) → deviceId(UUID) |
| `MP4EnsureDeviceIdPacket` | composite | hand(bool) |
| `LiveStreamerControlPacket` | composite | pos → action(VarInt) → roomInput(Utf256) → value(VarInt) |
| `ModernTurntableControlPacket` | composite（含**内联** Long codec） | pos → action(VarInt) → targetMillis(VarLong) |
| `PadPublishPacket` | **`StreamCodec.of`**（两个 lambda） | deviceId(UUID) |

> 网络的**第 4 种形态：`StreamCodec.of((buffer, packet) -> …, buffer -> …)`** ——
> 不是 composite 的交替参数表，而是一对 lambda。
> `(buffer, packet) -> …` 展开成 `encode(packet, buffer)` 时**形参顺序要对调**。

### 3.12 网络剩余（最后 2 个，形态已确认）

| 包 | 行数 | 形态 |
| --- | --- | --- |
| `MP4PlaybackControlPacket` | 497 | `StreamCodec.of`，编码体 5 个字段（含可空 deviceId 的条件写） |
| `PadPlaybackControlPacket` | 386 | `StreamCodec.of`，编码体 4 个字段（含可空 pointId 的条件写） |

两者的 `STREAM_CODEC = StreamCodec.of((buffer, packet) -> { … }, buffer -> { … })` 都是
**块语句 lambda**，适合再写一条 codemod（要按花括号配对切出两段 body）。
`.STREAM_CODEC` 的真实代码引用已从 37 降到 **4**。

---

### 3.13 第 11 次修掉的两个真缺口

**① `StreamCodec.of(...)` 形态已可机械展开**（`expand_streamcodec_of`）：
按括号/花括号配对切出两个 lambda 的 body，**原样搬运**（不改语义），
只把 encoder 的形参 `(buffer, packet)` 对调成 `(packet, buffer)`。已覆盖 2 个包。

**② `IPayloadContext.player()` → `NetworkEvent.Context.getSender()`**
⚠️ **不能只做纯文本替换**：`getSender()` 的声明返回类型**就是** `ServerPlayer`，
于是原来的 `if (ctx.player() instanceof ServerPlayer player)` 会变成
`expression type ServerPlayer is a subtype of pattern type ServerPlayer`（Java 17 里是**错误**）。
所以先处理两种结构性写法（换成"取出后判空"：**7 处**含 `&& level()` 的 + **18 处**单条件），
再做兜底纯替换（**18 处**）。残留 2 处待查。

### 3.14 已确认但**尚未修**的缺口（下一轮）

**匿名 codec 的字段名不叫 `STREAM_CODEC` 时，codemod 不会处理。**
实测还有 **5 个文件**（不是原来的 2 个）：

| 文件 | 行 | 说明 |
| --- | --- | --- |
| `ControlConsoleConfigResultPacket` | 17 | 辅助 codec |
| `MP4PlaybackSyncPacket` | 35 | 委托包 |
| `MP4PlaybackTimelinePacket` | 14 | 委托包 |
| `MP4PlaybackControlPacket` | 30 | `ACTION_CODEC` |
| `PadPlaybackControlPacket` | 32 | `ACTION_CODEC` |

**配方**：把 `X_CODEC = new StreamCodec<…>() { decode/encode }` 的**壳去掉**，
两个方法改成唯一名字（`ACTION_CODEC` → `decodeAction`/`encodeAction`），
再把用法 `X_CODEC.decode(b)` → `decodeX(b)`、`X_CODEC.encode(b, v)` → `encodeX(v, b)`（**形参对调**）。

#### 实测结果：**做了，但净效果是负的 ⇒ 默认关闭**

第 12 次把这个展开实现了（`expand_helper_codecs`），实测 **668 → 677 条（在范围内 515 → 524）**，
所以**默认关闭**（`ENABLE_HELPER_CODECS = False`，可用 `--with-helper-codecs` 打开）。

过程中修掉了两个真 bug，都是有价值的记录：

1. **限定名被误伤**：用法正则 `\b(\w+)_CODEC\.` 会命中 `ItemStack.OPTIONAL_STREAM_CODEC`
   的后半截，改写成 `encodeOptionalstream(...)`。修法是加 `(?<![\w.])` 排除限定名。
2. **跨文件引用**：有些辅助 codec 定义在**别的文件**里，本工具按文件处理，
   改名后那个引用就悬空了 —— 这**正是**净效果为负（+9）的原因。
   现在只改"本文件里确实生成过方法"的名字，剩下的**报出来但不动**。

> **教训**：一条覆盖率更广的规则**不等于**更好的规则。
> 必须先跑同一套台账比较**净效果**，负了就不进默认流水线 ——
> 否则"多覆盖了 5 个文件"听起来像进步，实际是把已知最好的状态弄差。
> 这也说明**跨文件分析**是下一个真正的门槛（本工具目前是逐文件处理的）。

---

## 4. 下一步（按"先让 javac 少说话"排序）

1. **补"安全的符号级改名"**（一次能消掉几百条，且没有设计决策）：
   - `ModConfigSpec` → `ForgeConfigSpec`（`net.minecraftforge.common` 下）
   - `@EventBusSubscriber` → `@Mod.EventBusSubscriber`（嵌套）
   - `DeferredItem<T>` / `DeferredHolder<A,B>` → `RegistryObject<T>`（**要删掉第一个类型参数**）
   - `ItemInteractionResult` → `InteractionResult`
   - `clamp` → `Mth.clamp`
   做法：**加进 `tools/port_rename.py` 的登记表**（不要手改 stage1-src），
   然后 `--apply` + 重新编译 + 看台账下降。
2. **阶段 2 网络重写**（约 73 文件 / 288 处的 `CustomPacketPayload`）：
   换成 `SimpleChannel` + `FriendlyByteBuf`；这是 A4（双向包）的载体，
   也是"基本功能"里最大的一件。
3. **阶段 3 物品数据**（`DataComponents`→NBT，43+49 处）→ 对应 A5（存档往返等价）。
4. 每完成一段就重跑 §1 的三步，把 §2 的数字更新到本文件。

> ⚠️ **不要**为了让数字好看而去注释掉/删除代码。MVP-SCOPE §5 要求：
> 任何"先注掉/先 stub"的改动都必须在延后表里登记（类名 + 原因 + 计划）。

---

## 5. 已知的口径陷阱（本轮踩到的）

* **中文 locale 的 javac 输出解析不了** —— 台账必须按英文消息解析，所以强制
  `-J-Duser.language=en`（且 `-J` 参数要**整体加引号**，否则 PowerShell 拆坏成 `invalid flag: .language=en`）。
* **`net.neoforged.neoforge.network` 这类"体系不同"的包**：机械改名**照样改**，
  目的是把报错从"整套包不存在"变成"**逐个找不到符号**" ——
  后者才是能逐条重写的清单。留着不改，报错会退化成一句没有信息量的话。
* **⚠️ "扫描"必须模拟"应用"的真实顺序**（这是本轮最有价值的自检抓到的坑）：
  `apply` 是按顺序在**不断演进的文本**上逐条替换的，而第一版 `scan` 是拿**原始文本**
  逐条独立匹配。于是"依赖前一条产出"的规则（如
  `neoforged.fml` 先把 `…fml.common.EventBusSubscriber` 改成 `net.minecraftforge.fml.common.…`，
  后面那条才匹配得上）在扫描里永远命中 0 ⇒ 触发零命中检查 ⇒ **`apply` 根本没执行**
  ⇒ 错误数一条没降，看起来像"改了没用"。
  **教训：一个只预测、不执行的检查，必须跑在与被预测操作完全相同的状态序列上**，
  否则它报的是另一件事。
* **表内规则顺序是有语义的**：`DeferredHolder<A,B>` 的双参规则必须排在单参规则**前面**，
  否则先被单参规则吃掉。这类错误同样只有"按真实顺序扫描"才能发现。
* **零命中检查要留白名单**：兜底规则（`neoforged(兜底)`）在具体规则补齐后命中 0 是正常的，
  要在 `OPTIONAL_RULES` 里显式列出来，否则会把"表已经完备"误报成"表写错了"。
* **`Math.clamp` 是 Java 21 的 API**，本工程 `--release 17` ⇒ 不存在；1.20.1 的等价物是
  `net.minecraft.util.Mth.clamp`（改名的同时要补 import，所以规则系统支持"附带 import"）。
* **⚠️ "延后"分类必须逐文件核，不能只看包名印象**：最早只列了
  `client/renderer/*` / `media/*` / `compat/*` 等，漏了 `client/terrain/`
  （13 个文件，`TerrainPreview*` 全是渲染）与 `port/shim/`（6 个文件，全是渲染垫片）。
  漏掉它们的后果是**把已登记延后的渲染活算进了"基本功能必须修"**，
  在范围内的负担被虚高约 100 条。
  **改分类规则之后，历史各次的数字必须用同一套口径重算**，否则进度里会混进口径变化。
* **负例注入必须落到磁盘**：codemod 的 apply 循环是**从磁盘重读**文件的，
  只在分类用的内存副本上做手脚，负例就是假的（本次踩过：注入了破坏却报"全部成功"）。
* **⚠️ 负例有"有效期"，而且过期后失败方式很隐蔽**：codemod 与 registrar 的负例都依赖
  "文件还是改造前/重写前的形态"。一旦工具已经跑过，破坏点就命中不了 ——
  此时工具会**明确拒绝**（`负例无法注入…` / `前置缺失…说明已经被重写过`）而不是假装通过。
  **规矩：负例自检必须在 §3.6 的干净 stage 上、按步骤顺序跑。**
* **规则顺序有语义（两处都踩过）**：① `DeferredHolder<A,B>` 的双参规则必须排在单参前；
  ② `sendToPlayer\(` 的正则会前缀命中 `sendToPlayers*`，所以后者必须排前面。
  这类错误只有"按真实顺序模拟扫描"或"看残留"才能发现。
* **`files.txt` 会过期，且失败方式像"全好了"**：增删文件后不重生成，javac 报
  `error: file not found`（退出码 2，**不是**编译错误），台账显示"0 条错误"。
* **对整份文本用带 `^$` 锚点的正则时必须逐行匹配**（或加 `re.M`），
  否则 `^`/`$` 只匹配整个字符串首尾 ⇒ 所有文件被误判（本次踩过）。

---

## 6. 第 87 次：未完成层清单**外置**（交予下轮）

> 这一轮**没有推进编译进度**，只修掉了"反复返工"的病根，并把下一轮的起跑线固定下来。
> 工程 `ncpb-1.20.1`（外观层）本轮**仍未改动**。

### 6.1 病根：清单会被重算它的那个动作吃掉

第 86 次我从 `build.gradle` 里读出那 11 条 `exclude`，发现
`TerrainPreview*`、`HolographicPreviewPipRenderer`、`PortGuiFramebufferBlitter`、
`PortGuiTextures` 这些**我明明排除过**的文件又在报错 —— 清单**被侵蚀**了。

原因不是看漏，是**那个动作本身**：重算脚本（`narrow_excludes.py`）干的是
"把 `build.gradle` 里 `NCPB-DEFERRED-CUT` 段**整体重写**"。
而**被 exclude 的文件不会出现在构建日志里** ⇒ 下一轮"拿当前日志重算"就**永远选不上它们**
⇒ 清单必然一路缩水。更糟的是缩了**看不出来**：构建照样跑、照样出数字、照样可能"绿"。

> **教训（值得单独记一条）**：清单的"读写入口"如果和"被重写的产物"是同一个文件，
> 那么每次重写都是一次**无证据的删除**。删除必须留下理由，否则它和事故无法区分。

### 6.2 现在的结构（清单在构建脚本之外）

| 文件 | 角色 | 读写的唯一入口 |
| --- | --- | --- |
| `ncpb-assembled/deferred-excludes.txt` | **清单本体**：一行一个路径（相对 `src/main/java`），`#` 注释 | `tools/deferred_cut.py add` / `remove` |
| `ncpb-assembled/deferred-excludes.hwm` | **水位线**：记录"上一次显式操作后的条数" | `add` 抬 / `remove` 重写 |
| `ncpb-assembled/deferred-excludes.log` | **流水**：每次增删带时间戳与理由 | 只能追加 |
| `ncpb-assembled/build.gradle` | 配置期**读**清单；缺失即失败；打印生效条数 | 不存条目 |

- `build.gradle` 里不再有任何 inline `exclude '…'`，重写它也碰不到清单；
- 清单缺失 ⇒ **配置期直接抛异常**（不允许"清单没了所以什么都不排除"这种静默降级）；
- `narrow_excludes.py` 降级为**转发器**，语义从"替换"改成"**追加**"。

### 6.3 五道门禁，每道都能说"不"（`python tools/deferred_cut.py check`）

| 门 | 判据 | 它能拒绝的情形 |
| --- | --- | --- |
| G1 | 清单存在且每行可解析、在允许的延后树内、无重复 | 文件被删 / 写进了越界路径 |
| G2 | 每条在磁盘上**真有**这个文件 | exclude **空转**（静默失效） |
| G3 | `build.gradle` 读清单，且**没有** inline exclude | 外置没接上 / 又写死了条目 |
| G4 | 条数 **>= 水位线** | **自擦除**（清单被手改短） |
| G5 | **上次构建日志**里 `active excludes = N` 且 `N == 清单条数`，且日志比清单**新** | 清单没到达编译器 / 拿旧日志冒充 |

> **G5 的已知残余假失败**：它判"新旧"用的是文件 mtime。所以**内容没变、只是被重写一遍**
> （如自检恢复、格式无变化的编辑）也会让它报"日志比清单旧"，叫你重建一次。
> 这是**失败朝安全一侧**（宁可叫你重建，也不放行一份对不上的日志）。
> `selftest` 现在**连 mtime 一起恢复**，所以它自己不会再制造这种假失败。
> 若以后嫌烦，更彻底的做法是把清单内容的哈希打进那行 `logger.lifecycle`，
> 让门禁比**内容**而不是比时间 —— 本轮没做，登记在此。

`python tools/deferred_cut.py selftest` —— **9 条负例**，逐条故意破坏再要求对应门拒绝：

```
PASS  [G1] 清单被删掉                      PASS  [G4] 清单被手改短（自擦除）
PASS  [G2] 清单里有不存在的文件（空转）      PASS  [G4] 水位线文件被删
PASS  [G3] build.gradle 不再读清单          PASS  [G5] 日志条数与清单不一致
PASS  [G3] build.gradle 里又出现 inline     PASS  [G5] 日志里没有生效条数那行
                                          PASS  [G5] 日志条数与清单一致（正例）
---- 负例自检：9/9 道门拒绝了伪造的破坏
```

`add` / `remove` 的两条路径也实测过：`add` 拒收不存在的文件（会空转）、`remove` 拒收
无 `--reason` 的删除；`remove` + `add` 的闭环后清单回到 11 条、水位线 11。

### 6.4 本轮实测（全量口径，`tools/build_full.ps1`）

* **完整构建跑了 3 次，每次都是 143 错误 / 3 警告 / 66 个文件**，gradle 退出码 1，
  "错误最多的文件"列表三次完全一致 ⇒ 这个数字是**可重复**的（同时也是外置前后
  的等价比对：清单从 build.gradle 搬到独立文件，编译结果一字未变）。
  证据：`_port-compile/gradle-full.txt`（最后一份）。
* 构建日志里有证据行：`NCPB-DEFERRED-CUT: active excludes = 11 (deferred-excludes.txt)`。
* `check` 5/5 通过；`selftest` 9/9 拒绝。
  **顺序也验过**：先 `selftest` 再 `check`，`check` **仍为 0** ——
  自检不留痕迹（这是把"恢复内容但没恢复 mtime"这个自造的假失败修掉之后的结果）。
* `check_tools.ps1` 仍绿（62 个文件：`.ps1` 带 BOM 且语法干净；工程文件无 BOM），
  说明本轮改 `build.gradle` 没有引入 BOM。
* **"清单缺失 ⇒ 构建失败"是实测的，不是承诺**：把清单改名后跑
  `gradlew help`，**9 秒**就 `BUILD FAILED`，原文是
  `NCPB-DEFERRED-CUT: missing …\deferred-excludes.txt. … the build must not silently turn green.`
  —— 不存在"清单没了所以什么都不排除"的静默降级路径。
  （证据：`_port-compile/cut-missing-list.txt`）
* ⚠️ **143 不是进度**，理由见 §3 / 第 86 次：javac 只要还剩一个错误就停在**第一个中止点**，
  同一方向的动作会先暴跌再暴涨（实测 149 → 5 → 143）。
  **唯一可用的进度判据是"`compileJava` 是否为 0 错误 / 能否出 jar"。**

### 6.5 下轮按这个顺序做

1. **先跑门禁**：`python tools/deferred_cut.py check` 必须 5/5 绿
   （G5 要求日志比清单新 ⇒ 先跑一次 `tools/build_full.ps1`）。
2. **补回被侵蚀的条目**（这是本轮唯一没做的事）：用**无 exclude 的基线日志**跑
   `python tools/deferred_cut.py from-baseline <基线日志>`
   —— 只追加、不替换。⚠️ 基线日志必须是**真的关掉 exclude 时**产出的那份，
   拿带 exclude 的日志当基线是自相矛盾（第 78 次踩过）。
   本轮核对过 `_port-compile/` 里那两份旧"无 exclude"日志（判据：它是否包含**当前正被
   exclude** 的文件的报错）：

   | 日志 | 诊断条数 | 含 `ModernTurntableRenderer.java` 报错 | 能不能当基线 |
   | --- | --- | --- | --- |
   | `gradle-noexclude.txt` | 150 | **否** | ✗ 它不含已知会报错的排除文件 ⇒ 不是干净基线 |
   | `gradle-noexclude2.txt` | 39 | 是（1 条） | △ 可以用作**候选起点**，但**不完整** |

   ⚠️ **不要期待"一次拿到完整基线"**：即使真的关掉 exclude，javac 仍然会**在第一个
   中止点停下**（39 与 150 这两个数就是两个不同的中止点）。所以这一条本来就是
   **迭代**：`from-baseline` → 构建 → `from-baseline` → …，每轮只补进"这轮日志里真的
   报错"的文件。**别因为"基线看着不全"就怀疑基线选错了。**
3. **只盯"能不能编译"**：排**叶子**、不排**枢纽**（排一条枢纽会把它自己的报错变成
   引用它的所有文件的报错 —— 实测 20 → 47）。每加一条都要留"它自己的报错消失了"的证据。
4. 到 0 错误后 `gradlew build` 出 jar，再往下谈 Milestone A 的 A1–A6。
5. 需要放回某条时：`remove <路径> --reason "它已经能编译了 / 为什么"`。

### 6.6 本轮新踩的坑（已写进 `tools/README.md` §18）

1. **自擦除**（§6.1）—— 清单别写死在会被重写的文件里。
2. **PowerShell 的 `>` 写的是 UTF-16LE**：`python … > out.txt` 落盘首两字节是 `ff fe`，
   下游按 UTF-8 读会当成二进制（本轮就因此读不出自检输出）。要落盘就走
   `cmd /c "… > out.txt 2>&1"`，并给 Python 加 `$env:PYTHONIOENCODING='utf-8'`。
3. **本机有执行策略**：`& .\tools\build_full.ps1` 直接被拒（"未数字签名"）。
   先 `Set-ExecutionPolicy -Scope Process -Force Bypass`，或用
   `powershell -NoProfile -ExecutionPolicy Bypass -File …`。
4. **构建脚本里打印的字符串必须只有 ASCII**：`build.gradle` 在中文 Windows 上可能按
   GBK 解码，`logger.lifecycle("…中文…")` 会变乱码，G5 的正则就再也匹配不到。
   注释中文没关系，**打印的内容**不行。
5. **门禁自己也会崩**：G5 里 `os.path.getmtime(清单)` 在"清单已被删"（正是 G1 要报的情形）
   时抛 `FileNotFoundError` —— 门禁必须能**报出**问题而不是自己炸掉。
   这条正是负例 N1 抓出来的（**只写正例的门禁不会暴露自己的异常路径**）。

### 6.7 仍然挂着的账（未变，逐项登记）

| # | 事项 | 状态 |
| --- | --- | --- |
| ① | 物品能力 **attachment**（`ModernTurntableBlockEntity` 等） | 未做；三条路：NeoBackports 的 `AttachmentType` 范式 / 依赖它 / 自己实现 |
| ② | 属性修饰符**事件化**（`ItemAttributeModifierEvent`） | 未做 |
| ③ | `ItemStack.OPTIONAL_CODEC` | **已做**（→ `CODEC`） |
| ④ | **19 个 Mixin** 移出（`ncpb-assembled/_deferred-mixins/`） | 未回填；原因见 §3（目标是自家方法，AP 找不到映射，37 条） |
| ⑤ | 渲染层切割（本轮清单里的 11 条 + 报错里的 `TerrainPreview*` / `PortGui*` / `port/shim` 等） | **未完成，清单已外置**；恢复方式 = `remove --reason` |
| ⑥ | `tools/assemble.py`（把"6 个项目版本文件覆盖 + 删 5 个 P0 文件 + 改 `P0ModEntry.MOD_ID`"变成可复算） | **仍未写**（那几处是直接编辑，不是规则） |
| ⑦ | 游戏内验证（A1 主菜单 / A3 音频 / A4 双向包 / A5 存档往返 / A6 命令） | **需要你**：给测试实例路径、确认测试台有没有 Sable 与 netmusic/Curios |
| ⑧ | **绝对路径硬编码**（`tools/build_full.ps1` 里的 `D:\Python311\python.exe` 与 JDK、`ncpb-assembled/gradle.properties` 里的 `org.gradle.java.installations.paths`） | 已知；本轮**故意没动**（交接前不碰构建管线），登记为待改：改成从环境变量/PATH 取，缺了要**明确报错**而不是静默换个 JDK |
| ⑨ | 方块实体渲染器被切掉 ⇒ 相关方块**可能渲染成空** | 需你确认可接受，否则 Milestone A 的 A2 只能算"注册与放置"，不含外观 |

---

## 6.8 第 88 次：范围内的 1.21→1.20.1 API 缺口（−53 条）+ 一类**静默**存档 bug

起点：门禁 5/5 绿（清单 11 条），全量 **143 错误**。

**新增工具（都带 `--fault` 负例自检）**

| 文件 | 作用 |
| --- | --- |
| `tools/cut_analysis.py` | **排除决策表**：每个候选给出 `own`（自己的错）/ `refs_in`（引用者也在延后树里）/ `refs_out`（引用者在范围内）—— §6.5 第 3 条"排叶子不排枢纽"的判据；另有 `--closure`（引用的传递闭包） |
| `tools/port_context_codemod.py` | 补 `NetworkEvent.Context context = contextSupplier.get();`（17 文件）；**6/6** 负例 |
| `tools/port_manual_round88.py` | 33 文件 / 70 处，**全有或全无**（一条对不上就一个文件都不写盘） |

**本轮最重要的发现：一类"编得过但什么都不做"的静默 bug**

7 个方块实体用了 1.21 的 `saveAdditional(CompoundTag, HolderLookup.Provider)` /
`loadAdditional(..., Provider)` / `getUpdateTag(Provider)`。1.20.1 的签名是 **1 参数 / 0 参数**
⇒ 这些方法**不构成覆盖**，`@Override` 又没写 ⇒ **javac 不报错，游戏永远调不到 ⇒ 存档静默不写**。
已按 1.20.1 签名改正，并**补上 `@Override`**（以后再犯会变成硬错误，而不是静默失效）。

**平台取证落盘**：`_javap-1201/`（25 个 1.20.1 类的 `javap -p` 输出），每条改法都对着字节码写。

**结果**：143 → 90（范围内 55 → 5）。

## 6.9 第 89 次：顶点/缓冲管线改写 → **出 jar**（就绪任务 ③④）

* 先排掉 **9 个叶子**（`refs_out == 0`，排它们不会把错误搬进范围内代码）；
* 撞到边界后**回填 5 条**：`VideoBillboard*` 是**继承链的根**，排下去会走到
  `VideoBillboardPreview` —— 它被 **13 个范围内文件**引用（`remove --reason` 留了理由）；
* `tools/port_manual_round89.py`：15 文件 / 32 处，含
  `MeshData` 桩新增 `of(BufferBuilder)` 工厂（把"1.20.1 怎么结束一个 render type"**收在一处**）、
  同包借道补 6 个 `protected` 的 `RenderStateShard` 常量；
* 最后一处错误**不在本模组包内**：`com/zhongbai233/scene_editor/core/session/EditorSession.java`
  （见 §6.12）。

**结果（已验）**

```
build_full.ps1            : 错误 0 条 / 警告 3 条，gradle exit 0
gradlew build --offline   : BUILD SUCCESSFUL in 15s
产物                      : ncpb-assembled/build/libs/net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar
                            3,380,150 B；**1580 个 zip 条目 / 1484 个文件条目 / 1411 个 class**；
                            `mods.toml` + `mixins.json` 在；**0 个 native**（6 平台 FFmpeg JNI 没打进 jar）
```

⚠️ **`sha256` 不是这个制品的可复现不变量**（第 89 次实测）：同一份源码、同一条命令，
三次构建得到三个不同的 sha256 —— `97232031…E11492` / `784A0247…C617D` / `D1F54FB1…79F0A`，
**长度完全相同（3,380,150 B）**，差的是 jar 条目里的时间戳。

⇒ 能用的最强不变量是**内容指纹**（条目集合 + 每个条目的内容 sha256）：

```
tools/jar_fingerprint.py <jar>
  entries=1484  fingerprint=57a73d5caf45918325b5a336f82cd513259c3e51b9cb99339cc7f73e7f6746de
两次独立 clean build 内容一致（--compare 退出码 0）；工具带 4/4 负例自检。
```

⚠️ **操作提醒**：`build_full.ps1` 跑的是 `clean compileJava` ⇒ **它会删掉已经 build 出来的 jar**。
要制品就在跑完台账之后再 `gradlew build` 一次（本文件 §3.1 的那条链本来就是这么串的）。

> ⚠️ **这个 jar 不是"功能移植完成"**：组装层的注册是**外观版**（`ModItems` 用 `simple()` 造裸 `Item`），
> 功能物品 `MP4Item`/`PadItem`/`MediaManagementToolItem`/耳机/眼镜**没有被注册**；
> 渲染层 19 个文件移出编译；**从未在游戏里启动过**。
> 它是 §6.5 第 4 步说的"**再往下谈 A1–A6**"的入场券。真实长杆是待办⑥ + 功能注册层（§6.14）。

## 6.10 第 88/89 次：**门禁自己**的三个缺陷（都是"抓不到错"）

1. **截断金丝雀把"警告"算进了文件数** —— `build_full.ps1` 用 PowerShell 的
   `Select-String '\.java:\d+:'` 数"报错文件数"，而那个模式**连警告一起数**：
   别的文件里有 2 条警告，就能把"只有 1 个文件报错"伪装成"3 个文件报错"
   ⇒ **截断探测形同虚设**。
   修法：所有按中文标签的判断收进 `tools/gradle_ledger.py`（它按 GBK 解码），输出
   **ASCII 机器行** `LEDGER errors=N warnings=M error_files=K`，PowerShell 只读机器行。
   台账新增 `--canary <必须报错的文件>`（缺席 ⇒ **exit 2**，硬判据）与 `--selftest`
   （**8/8** 负例；其中 N2b 专门证明"**警告不再抬高错误文件数**"）。
   注意：本工具**不**把"错误只落在 1 个文件"当判决 —— 本工程真的出现过"最后一处错误就在 1 个文件里"
   （`VideoBillboardQuadSupport`、`EditorSession`），那时它是**真实读数**；假失败的门禁会被忽略，比没有更糟。
2. **`check_tools.ps1` 的作用域只覆盖一个工程** —— 它的默认 `-ProjectDir` 是
   `ncpb-1.20.1`（外观层），于是 `ncpb-assembled`（功能移植主战场，**780 个** .java/.json 文件）
   **从来没被查过**，而输出照旧说"检查了 62 个文件"。
   修法：默认扫**两个**工程 + `tools/*.py`（必须无 BOM）+ **`.md`**（交接文档的规矩写了它，
   原门禁同样没查），并把**每个作用域各查了多少打印出来**；自检新增两条负例
   （"**第二个工程根**下的 .java 多 BOM"与"**根目录**的 .md 多 BOM 必须被抓到"）。
   自检还当场抓出我**自己新写的一个 bug**：仓库根递归本来就覆盖 `tools/`，
   根不去重会把同一个文件数两遍、报两遍（已修）。
   现在：**890 个文件**（.ps1 10 / .py 34 / ncpb-1.20.1 52 / ncpb-assembled 783 / .md 11），全绿。
3. **（教训，不是脚本缺陷）我的分析仪器盲区** —— `cut_analysis.py` 把"世界"定义成
   `com/zhongbai233/net_music_can_play_bili/**`，于是 `com/zhongbai233/scene_editor/**`
   不在它视野里。我因此**反复报告"范围内错误 = 0"**，而项目口径一直是 **1 条**。
   更难看的是：台账说 1、我的工具说 0，**这个差我看到了却当成解析器噪声挥手带过**。
   ⇒ 规矩：**报"范围内"必须同时写清 range 是哪棵树**；跨树的任务一律用项目的全量口径
   （`build_full.ps1`），**不要拿子集工具的数当全局**。

## 6.11 第 88/89 次：**登记的行为差异**（未验，需游戏内确认）

| # | 位置 | 差异 | 原因（有证据） | 状态 |
| --- | --- | --- | --- | --- |
| ① | `link/LinkHelper` | 链接物品**不再强制附魔光效** | 1.20.1 没有 `DataComponents.ENCHANTMENT_GLINT_OVERRIDE`（1.20.5 才有）；1.20.1 的 `isFoil` 是 `Item` 的方法，无法按 stack 强制 | 未验；可在自家物品上重写 `isFoil` 补回 |
| ② | `gui/HolographicEditorRenderScreen` | 全息编辑器的**画中画预览不渲染**（自包含 3 行已删） | `HolographicPreviewPipRenderer` / `PortPictureInPictureRenderer` / `TerrainPreviewGpuCache` 都在清单里 | 未验 |
| ③ | `port/shim/PortWorldRenderEvents` | `BufferBuilder.discard()` 去掉 | 1.20.1 的 `BufferBuilder` 没有 `discard()`（1.21 才有） | 未验 |
| ④ | `VideoBillboardQuadSupport.drawWithEventModelView` | model view 改用事件 `poseStack` 的最后一个矩阵；改用 `RenderType.end(builder, sorting)` | 1.20.1 的 `RenderLevelStageEvent` 没有 `getModelViewMatrix()`；`RenderType` 没有 `draw(MeshData)` | 未验（渲染层延后） |
| ⑤ | `init/ModCreativeTabs`（**本轮新增**） | 页签里**没有**那两本附魔书 | 1.21.1 的页签会放 `HEADPHONES_KEY` / `HOLOGRAPHIC_GLASSES_KEY` 的附魔书；1.20.1 附魔要**代码注册**，本工程还没有注册类 | 未做（需 `ModEnchantments`） |
| ⑥ | `MeshData` 桩 | 新增 `of(BufferBuilder)`；不产生真实几何 | 1.20.1 没有 `MeshData` | 桩 |

## 6.12 第 89 次：`port_rename.py` 规则表里一条**会生成编不过的代码**的规则

* 规则：`('List.getLast -> get(size()-1)', r'\.getLast\(\)', '.get(size() - 1)', ...)`
* 缺陷：改写**把接收者丢了** —— `X.getLast()` 变成 `X.get(size() - 1)`，而 `size()` 没有接收者
  ⇒ **对任何接收者都编不过**。这次落点是 `EditorSession.selectedElementIds`，
  而它是 **`LinkedHashSet`**（Set 连 `get(int)` 都没有）。
* 现状：站点已用 Guava `Iterables.getLast`（对 List / Set / Deque 都成立）修好。
* **规则表本身故意没动**：改它要重跑 `--apply`，而 `--apply` 会 `shutil.rmtree(dst)` 重建
  `stage1-src`；`tools/assemble.py` 还不存在（待办⑥）⇒ 现在重跑会丢掉"直接改副本"的那批改动。
  **登记为待办⑩**：写 `assemble.py` 的同时把这条规则改成
  `com.google.common.collect.Iterables.getLast(<接收者>)`，并补零命中/负例自检。
* 教训：每条规则都要问"**它对所有接收者都成立吗**"，以及"**零命中检查能不能抓到它写错**"。

## 6.13 就绪任务（HANDOVER §6.5）的收尾账

| 步 | 状态 | 证据 |
| --- | --- | --- |
| ① 门禁 5/5 | ✅ | 清单 19 条；G5 日志里 `active excludes = 19` == 清单 |
| ② 清单修复 | ✅（**方法有偏离，如实记**） | 11 → 19 条；全部走 `add`（存在性 + 树 + 抬水位线）；5 条 `remove --reason` 是显式回填。**偏离**：没用 `from-baseline` —— §6.5 自己核过的那两份"无 exclude"候选日志都不完整（150 / 39 是两个不同的中止点），而**只追加本来就不会自擦除**，所以用当前日志 + 叶子/枢纽仪器推候选 |
| ③ 0 错误 | ✅ | `build_full.ps1`：错误 0 / 警告 3，gradle exit 0 |
| ④ 出 jar | ✅ | `gradlew build --offline` → BUILD SUCCESSFUL in 15s；产物见 §6.9 |

**唯一可信判据（本轮再次验证）**：`compileJava` 是否 0 错误 / 能否出 jar。
javac 的错误数在本工程**不单调** —— 本轮实测 `143 → 90 → 4 → 1 → 48 → 47 → 9 → 1 → 0`，
它随"文件进出"和"**修好一处反而揭开后面**"而跳。**不得当进度曲线**（这正是 §3.85 的教训）。

## 6.14 第 89 次：待办更新（**§6.18 是第 90 次的最新版**）

| # | 事项 | 状态 |
| --- | --- | --- |
| ⑤ | 渲染层切割 | **进行中**：清单 19 条（外置 + 水位线 + 5 道门禁）。恢复方式 = `remove --reason` |
| ⑥ | `tools/assemble.py`（可复算组装） | **✅ 第 90 次完成**（§6.17：登记表 + 水位线 + 流水 + `verify`/`register` + 7/7 负例） |
| ⑩ | `port_rename.py` 的 `getLast()` 规则（§6.12） | **新增**：与 ⑥ 一起改，改完补零命中/负例自检 |
| ⑪ | `build_full.ps1` / `check_tools.ps1` 的绝对路径（原 ⑧） | **部分已修**：`check_tools.ps1` 改成从 `$PSScriptRoot` 推；`build_full.ps1` 仍写死 `D:\Python311\python.exe` 与 JDK 路径（与 `gradle.properties` 同一笔账） |
| ⑫ | 功能注册层（`ModItems`/`ModBlocks`/`ModEnchantments` 接功能版） | **✅ 第 90 次完成**（§6.16：6 个物品改注册功能类 + 两件穿戴物补属性钩子；**按制品**的注册门禁 11/11 通过。附魔确认**不需要**：那两个 KEY 全树无功能用途） |
| ⑦ | 游戏内验证（A1–A6） | 未做；**现在可以谈**了（注册层已接上）—— 需要你给测试实例路径、确认测试床里的 mods/ 只放我们的 jar + netmusic |

## 6.18 第 90 次：待办表（最新）

| # | 事项 | 状态 |
| --- | --- | --- |
| ⑩ | `port_rename.py` 的 `getLast()` 规则（§6.12） | **仍未做**：改它要重跑 `--apply`（会 rmtree `stage1-src`）⇒ 必须在**组装已经可复算**（⑥ 已完成）之后单独做，做完立刻 `assemble.py verify` 复验 |
| ⑪ | `build_full.ps1` 里写死的 `D:\Python311\python.exe` 与 JDK 路径 | 未做（与 `ncpb-assembled/gradle.properties` 的 `org.gradle.java.installations.paths` 同一笔账）：改成从环境变量/PATH 取，缺了要**明确报错**，不要静默换个 JDK |
| ⑬ | `ModAttributes` 里那几个**永不生效**的附魔声明（`HEADPHONES_KEY` / `*_ENCHANTMENT_TAG` 等，§6.16） | 新增：要么补 `ModEnchantments`（1.20.1 代码注册）、要么删掉；现状是"声明了但永远不会匹配任何东西" |
| ⑭ | 渲染层（清单 19 条 + 桩） | 未动；用户口径：延后 |
| ⑦ | 游戏内验证 A1–A6 | **下一件大事**：注册层已接上，jar 可以拿去跑了（但渲染层是桩/砍掉的，画面别当验收项） |

## 6.15 第 89 次：**组装的真实差异**（待办⑥ 的规格）+ 功能注册层到底缺什么

把 `stage1-src`（`port_rename.py` 的产物）与 `ncpb-assembled/src/main/java` **逐文件比 sha256**
（脚本 `_port-compile/_asm_diff.py`，输出留在 `_port-compile/asm-diff.txt`）：

| 类别 | 数量 | 内容 |
| --- | --- | --- |
| 只在 `stage1-src`（**组装时被删**） | 20 | **19 个 Mixin** + `network/ModernTurntableNetwork.java`（与 §6.7 ④ 的"19 个 Mixin"对得上） |
| 只在 `ncpb-assembled`（**组装时新增**） | 7 | `ByteBufferBuilder`/`MeshData` 两个桩、`init/ModCreativeTabs`（第 89 次新增）、`network/NcpbNetwork`（Forge `SimpleChannel` 重写）、`port/bridge/NetmusicMessageAccess`、`port/nbt/CustomData`、`net/minecraft/client/renderer/NcpbRenderStateShardAccess` |
| 两边都有但**内容不同**（组装后的直接编辑） | **130** | `network` 48、`client` 27、`gui` 10、`blockentity` 8、`block` 6、`port` 6、`link` 5、`item` 4、`ncpbmodel` 4、`init` 3、`util` 2、`mixin` 2、其余各 1 |

> ⇒ 所谓"组装"**不是**"6 个文件覆盖 + 删 5 个 P0 文件"那么小：它是
> **删 20 / 加 7 / 直改 130**。所以 `assemble.py` 不该假装能"从 stage1-src 全部重算"
> （那等于把 130 个文件的直接编辑重新发明一遍）。可行的形态是**把差异变成登记数据 + 可校验**：
> `--verify` 断言"该删的还不在、该加的在、该改的在登记表里"，`--apply` 只负责能表达的那部分。

**功能注册层缺什么（本轮查清，下一轮照这个做）**

组装用了**外观版**的 `ModItems`/`ModBlocks`/`ModAttributes`（3 个文件），而 `stage1-src` 里的
**功能版是原始改名产物、从来没被移植过**。证据（`stage1-src/init/ModItems.java`）它现在还在用：

* `ITEMS.registerSimpleBlockItem(...)` / `ITEMS.registerItem(...)` —— **NeoForge 专有**（Forge 的
  `DeferredRegister` 只有 `register(name, supplier)`）；
* `ItemAttributeModifiers.builder()` + `new AttributeModifier(String, double, Operation)` —— 1.20.1 是
  `AttributeModifier(UUID, String, double, Operation)`，而且**没有 `ItemAttributeModifiers`**（1.20.5+）；
* `parameters.holders().lookupOrThrow(Registries.ENCHANTMENT)` —— 1.21 的注册表访问；
* `ModAttributes` 还在 `.setSentiment(Sentiment.POSITIVE)`（1.20.1 无 `Sentiment`）；
* 重复 import（`Registries` ×2、`RegistryObject` ×2）。

**属性怎么给（1.20.1 的对应物）**：1.21 走 `Properties.attributes(...)`；1.20.1 要**重写
`Item#getDefaultAttributeModifiers(EquipmentSlot)`** 返回 `Multimap<Attribute, AttributeModifier>`。
而 `InvisibleHeadphonesItem` / `HolographicGlassesItem` 在组装树里**都没有这个重写**
⇒ 即使把物品注册上，耳机/眼镜也**不会真的带属性**（`HeadphoneAbility.has()` 靠的就是属性）。

**因此"⑫ 功能注册层"的工作包 =**（建议下一轮按序做）
1. 移植 `ModAttributes`（去掉 `Sentiment`，与第 88 次对外观版做的一致）；
2. 移植 `ModItems` 到 Forge 写法（`register` + 物品属性），页签沿用已新增的 `ModCreativeTabs`；
3. 给 `InvisibleHeadphonesItem` / `HolographicGlassesItem` 补 `getDefaultAttributeModifiers`；
4. **附魔**：1.20.1 的附魔要**代码注册**（1.21 是数据驱动）⇒ 要么补一个 `ModEnchantments`，
   要么把页签里那两本附魔书删掉并登记（§6.11 ⑤）；**不要**留"永远取不到"的代码；
5. 复跑到 0 错误 → 出 jar → 再谈 A1–A6。

## 6.16 第 90 次：**功能注册层接上了** + 一条按**制品**判的注册门禁

### 做了什么（`compileJava` 0 错误 → 出 jar）

| 改动 | 内容 |
| --- | --- |
| `init/ModItems` | 6 个独立物品从 `simple(name)`（造**裸 `Item`**）改成 `functional(name, XxxItem::new)`：`MP4Item` / `PadItem` / `MediaManagementToolItem` / `InvisibleHeadphonesItem`×2 / `HolographicGlassesItem`。字段类型**保持** `RegistryObject<Item>`（换成具体类型会让 `RegistryObject<T>` 的不变性在别处炸，且没有收益） |
| `item/InvisibleHeadphonesItem` | 补 `getDefaultAttributeModifiers(EquipmentSlot)`，HEAD 槽给 `ModAttributes.HEADPHONES` +1.0 |
| `item/HolographicGlassesItem` | 同上，给 `ModAttributes.HOLOGRAPHIC_GLASSES` +1.0 |

**附魔的结论（把 §6.15 的问题定下来）**：`HEADPHONES_KEY` / `HOLOGRAPHIC_GLASSES_KEY` /
两个 `*_ENCHANTMENT_TAG` 在整棵树里**只被声明、没有任何功能用途**（grep 实证；唯一提到它们的
是我自己写的那段注释）⇒ **不需要 `ModEnchantments`**，页签里那两本附魔书按 §6.11 ⑤ 登记为"未移植"。
（那几个常量仍是**永不生效的声明**，登记在此，将来要么补注册要么删。）

### 两个"编得过但会炸/没行为"的坑（都当场拦住了）

1. **`static` 初始化里取注册表项 ⇒ 启动时 NPE（编译照样通过）**：
   `ModItems` 的 `INVISIBLE_HEADPHONES = functional(..., InvisibleHeadphonesItem::new)`
   会让 `InvisibleHeadphonesItem` 在 **`ModItems` 类初始化时**就被加载；那一刻
   `DeferredRegister` 还没收到注册事件 ⇒ `ModAttributes.HEADPHONES.get()` 抛
   "Registry Object not present"。**第一版就是写成 `static final Multimap = buildModifier()` 的**，
   已改成调用时构造（两个物品类都是）。
2. **1.20.1 的三参 `AttributeModifier(String, double, Operation)` 每次生成随机 UUID**：
   `javap -c` 实证它走 `Mth.createInsecureUUID(RandomSource)` ⇒ 修饰符身份不稳定。
   已改用**带显式 UUID** 的四参构造器，UUID 写成常量（并注明"换掉它 = 换掉修饰符身份"）。
   （1.21 的三参是确定性的，所以这条是"换版本才出现"的差异。）
3. `Item.getDefaultAttributeModifiers` 基类返回 **`ImmutableMultimap.of()`**（`javap -c` 实证）
   ⇒ 必须先 `HashMultimap.create(...)` 拷贝再放。

### 新增门禁 `tools/check_registration.py`（按**制品**判，不按源码）

"0 错误 + 能出 jar"**证明不了**物品被注册上了 —— 第 89 次那个 jar 就是裸 `Item`。
所以新增门禁用 `javap` 反汇编**jar 里的 class**，判 3 件事（共 11 条判据）：

1. `init/ModItems` 的常量池里有**每个功能物品类的 Class 条目**（按完整内部名 `…/item/MP4Item` 判，
   避免"名字碰巧出现在别处"的假通过）；
2. 两件穿戴物**重写了属性钩子**（判据见下）；
3. `init/ModCreativeTabs` 引用了 `init/ModItems`。

**它自己的两个 bug（都由自检抓出来，记在这里免得重犯）**：

* 第一版用 `javap -p -c` —— 那个输出**不含常量池/`BootstrapMethods`**，而方法引用
  （`MP4Item::new`）只在那里出现 ⇒ 这条判据**永远失败**（连正例都判失败），
  是一条"只会说 No 的假门禁"。改 `-p -c -v`。
* 第一版按**方法名** `getDefaultAttributeModifiers` 查 jar —— **重混淆会把我们重写的原版方法
  改成 SRG 名**（dev class 是 `getDefaultAttributeModifiers(EquipmentSlot)`，
  jar 里是 `m_7167_(EquipmentSlot)`；类名不会被改）。所以判据改用**描述符**
  `(Lnet/minecraft/world/entity/EquipmentSlot;)Lcom/google/common/collect/Multimap;`，
  它跨"dev / 制品"都成立；dev class 还在时**额外**再确认一次方法名。

**负例用的是"外观层的 jar"**（`ncpb-1.20.1/build/libs/*.jar`）—— 那是一件**独立制品**，
它**应当**被判失败（实测 7 条失败，理由全是"还是裸 Item"）。这比"自己造一份假 jar"更硬。

**结果**：功能 jar **11/11 通过**；外观层 jar 被判失败且理由对得上。

```powershell
D:\Python311\python.exe tools\check_registration.py --selftest    # 正例 + 负例，exit 0
```

## 6.17 第 90 次：`tools/assemble.py` —— 组装差异第一次被**登记成事实**（待办⑥ 完成）

**为什么不做"从 `stage1-src` 重算一遍"**：§6.15 已经量过，差异是**删 20 / 加 7 / 直改 131**，
那 131 处是十几轮逐处改出来的、**没有任何脚本能重新发明**。所以本工具不去重算，它做的是
项目一直在用的那条规矩：**改动要么被登记、要么让构建失败**。

| 文件 | 角色 |
| --- | --- |
| `ncpb-assembled/assemble-manifest.txt` | **登记表**：三段 `[deleted]` / `[added]` / `[overridden]` |
| `ncpb-assembled/assemble-manifest.hwm` | **水位线**（抓"登记表被手改短"⇒ 自擦除） |
| `ncpb-assembled/assemble.log` | **流水**（只追加，带时间戳与理由） |

子命令：`verify`（门禁）/ `register --reason "…"`（**唯一**写入入口）/ `list` / `bootstrap` / `selftest`。

**首次建表（bootstrap）实测**：删 **20** / 加 **7** / 改 **131**，共 **158** 条，水位线 158；
`verify` 通过；`register` 在无新差异时是空操作（试过）。

**门禁能说"不"（7/7 负例，都在临时树上造，不碰真项目）**：
N1 未登记的手改 / N2 登记为已删除却又出现 / N3 登记为新增却不见了 / N4 未登记的新增 /
N5 水位线可读回 / N0 一致时应通过 / 读回来与写进去一致。

### 它自己的两个 bug（都由自检/实测抓出来，记在这里）

1. 🔴 **自检改到了真项目的状态**：第一版 `selftest` 里直接调了 `write_hwm(3, 'x')`，
   而那个函数写的是**全局 `HWM` 路径**（真项目的 `assemble-manifest.hwm`）
   ⇒ **自检把真项目的水位线改成了 3**（幸好紧随其后的 `bootstrap` 又写回 158）。
   ⇒ 规矩：**自检只能动自己造的临时状态**。这与 `deferred_cut.selftest` 当初
   "把清单/mtime 一起恢复"是同一族的教训。
2. `bootstrap --reason "…"` 被 argparse 挡下（`unrecognized arguments`）——
   子命令参数要用 `parse_known_args`，否则参数在到达自己的解析代码之前就被拒了。

### 组装门禁怎么用

```powershell
D:\Python311\python.exe tools\assemble.py verify                      # 必须 exit 0
D:\Python311\python.exe tools\assemble.py selftest                    # 7/7
D:\Python311\python.exe tools\assemble.py register --reason "为什么"   # 改完 ncpb-assembled 后登记
```

## 6.19 第 91 次：补齐**内嵌原生库** + 修掉一个**启动即崩**的 mixin 配置缺陷

### 先说结论：为什么"0 错误 + 出了 jar"仍然不等于"能在游戏里跑"

用户指出"文件大小不对"。查下去发现 **两个各自都能让制品不能用** 的缺陷 ——
而它们**都能通过** `compileJava 0 错误`、`gradlew build`、注册门禁、排除清单门禁。

#### 缺陷 A：jar 里**完全没有**内嵌的 FFmpeg/JNI 原生库（少了 11.76 MB）

* `Eac3NativeDecoder` 是**从 classpath 读**原生库的（实测行号）：
  `/native/<platform>/<file>`（:207）、`/native/SHA256SUMS`（:404）、
  失败时 `throw new IOException("内嵌 JNI native 库缺失: …")`（:360）；
* 而 `ncpb-assembled/src/main/resources/` 里**只有 assets、没有 `native/`**
  —— 根源是 `port_rename.py --apply` **只处理 `.java`**（`stage1-src` 里一个非 `.java` 文件都没有），
  资源根本不在这条流水线上；
* 上游是 6 个平台的一整包（`media-min-v48`，<https://github.com/zhongbai2333/FFmpeg>，LGPL-2.1，
  每个平台带未改动的许可文本；`native/SHA256SUMS` 是权威逐文件清单）。

**处理**：新增 `tools/sync_natives.py`（`--apply` 复制 / `--check` 门禁用）。
**它不放松判据**：发现 4 个 `FFmpeg-LGPL-2.1.txt` 在源工作区里是 **CRLF**（502 个 CRLF、0 个裸 LF），
`CRLF → LF` 之后哈希**正好等于清单**；于是工具**把字节规范成清单的字节**，再按逐字节验。
结果：**6 个平台齐全、38 个文件逐个与上游一致（11,751,327 B）**，
jar 从 **3,381,731 B → 9,200,700 B**。

#### 缺陷 B：`mixins.json` 列了 3 个**不在 jar 里**的 mixin ⇒ **启动崩溃**

`mixins.json` 是 `"required": true`，里面 19 个名字里有 3 个被我排除出了编译
（`SetMusicIDMessageMixin` / `CDBurnerMenuScreenMixin` / `ItemInHandRendererMixin`）
⇒ Mixin 加载配置时找不到类，直接失败。**这条只有"拿制品去跑"才会暴露。**

**处理**：

* `SetMusicIDMessageMixin`（**功能性**：拦 B 站直链/白名单）修回可编译，三处 1.21→1.20.1 差异：
  `message.song()`（1.20.1 的 netmusic 字段是 `private final` 且**无访问器**）
  → 走 `port/bridge/NetmusicMessageAccess` 反射读（新增 `setMusicIdSong`）；
  `context.flow().isServerbound()` → `context.getDirection() == NetworkDirection.PLAY_TO_SERVER`；
  `handle(msg, context)` → `handle(msg, () -> context)`（1.20.1 第二参是 `Supplier`）；
* `CDBurnerMenuScreenMixin`（UI）：1.20.1 的 netmusic `NetworkHandler` **没有** `sendToServer(...)`
  （`javap -p` 实证只有 `sendToNearby`/`sendToClientPlayer` + 公开字段 `CHANNEL`）
  → 改 `NetworkHandler.CHANNEL.sendToServer(...)`；
* `ItemInHandRendererMixin`：它依赖 `MP4ItemScreenRenderer`/`PadItemScreenRenderer`（都在延后层）
  ⇒ **保持排除**，并从 `mixins.json` 里**移除**（登记在 `deferred-excludes.txt` 的 17 条里）。
  1.21.1 上游的配置里确实列了它 —— 所以这是一处**为"能启动"而做的显式取舍**，不是遗漏。

### 新增门禁 `tools/check_in_game_ready.py`（只看制品，52 条判据）

1. jar 里的 `*.mixins.json`：`mixins`+`client`+`server` 里**每个名字**都要能在 jar 里找到 class
   （`required: true` 缺一个就崩）；
2. 反向：编译出来的 `mixin/*.class` 应当都登记了（不登记 ⇒ **静默不生效**，只警告）；
3. `MANIFEST.MF` 必须有 `MixinConfigs:` 并列出该配置（Forge 1.20.1 靠它接线）；
4. `refmap` 必须存在、合法且 `mappings` **非空**（空 ⇒ 运行期静默不生效）；
5. `native/` 的 **6 个平台目录**必须在 jar 里；jar 里有 `native/SHA256SUMS` 时**逐文件核对 sha256**；
6. `META-INF/mods.toml` 与 `pack.mcmeta` 必须在。

**结果**：最终制品 **52/52 通过**（含"38 个原生库在 jar 里逐个核对一致"），1 条警告。
负例用**外观层的 jar**（独立制品）→ 6 条失败，理由全是"缺 native/"。

**它自己的一个 bug**：第一版用"按后缀预读"的 dict 读 jar，
于是**没有扩展名的 `native/SHA256SUMS` 读不到** ⇒ 报了一次**假警告**（"jar 里没有 SHA256SUMS"）。
已改成按需 `read(name)`。教训：读制品别用"猜哪些文件重要"的过滤。

### 已知残留（登记）

| 事项 | 说明 |
| --- | --- |
| `MinecartRevolutionProjectorLinkCleanupMixin` | `@Pseudo` 的第三方联动物（`ml.mypals.minecartrevolution`），**编译进 jar 但没登记在任何 mixin 配置里** ⇒ 永不生效。**1.21.1 上游也没有登记**（全 `.json` grep 实证）⇒ 与上游一致，不是本次引入；门禁会一直以"警告"提醒 |
| `assemble.py` 的作用域 | 只比 **java 源根**；`src/main/resources`（assets + native）**不在**覆盖范围。已在 `verify` 输出里**显式打印**作用域，免得把"组装门禁：通过"读成"资源也对" |
| 从未在游戏里启动 | 本次交付的是**制品**与**制品级门禁**；A1–A6 一条都没跑 |

### 最终制品

```
ncpb-assembled/build/libs/net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar
  9,200,700 B（9.2 MB）；1527 个 zip 条目；内容指纹 128a89324f7f78ae54e167bdd2f02a7c4098189b64376fc9adf39e4b4b1ad873
  native/：6 个平台，38 个文件与上游 SHA256SUMS 逐个一致
  门禁：check_registration 11/11、check_in_game_ready 52/52
```

## 6.20 第 91 次（续）：**第一次真正启动游戏**，挖出 7 个"编译全绿但跑不起来"的缺陷

前面所有轮次都止步于"编译 0 错误 + 制品级门禁全绿"。这次真去启动客户端，
**连撞 7 个缺陷、每次都是新的一层**，而且**每一个都不是编译器能发现的**。
共同形状：**编译期看不见的"版本错配 + 加载器契约"**。

| # | 游戏里的症状 | 真因 | 关键排查手段 |
| --- | --- | --- | --- |
| 1 | 日志**戛然而止**、无 crash-report、无 hs_err、进程悄悄消失 | jar 里有 class 落在**游戏自己的包**（`com.mojang.blaze3d.vertex` 的 MeshData/ByteBufferBuilder 桩 + `net.minecraft.client.renderer.NcpbRenderStateShardAccess`）⇒ JPMS 分裂包，ModLauncher 模块解析直接拒绝 | `javaw` 把 stderr 丢进虚空 ⇒ 给 `run_testbed.ps1` 加 `-OutLog/-WaitSeconds` 落盘，才看到 `java.lang.module.ResolutionException` |
| 2 | 同上 | `@Inject(..., remap=false)` 用在 **Minecraft 目标**上：1.20.1 生产环境 MC 成员是 **SRG 名**（`turn`→`m_19884_`），`remap=false` 就去找字面量 `turn` | 读 refmap：原来只有 2 条，改完 **10 条**，且有 `{"turn":"…Entity;m_19884_(DD)V"}` 实证 |
| 3 | 同上 | 4 个 Mixin 访问器的 `@Shadow` 字段 / `@Invoker` 方法（`loaded`/`library`/`soundEngine`/`context`/`currentDevice`/`addRenderableWidget`）**refmap 里没有映射** | 改用 **AccessTransformer**（6 条 SRG 名 + 1 条嵌套类），删掉 4 个 mixin 与 3 个桥接口 |
| 4 | 同上 | `NetMusicCanPlayBili(IEventBus, ModContainer)` 是 **NeoForge 1.21** 的构造器签名；1.20.1 FML 要 `()` / `(FMLJavaModLoadingContext)` ⇒ `NoSuchMethodException` | 本地可跑的参考：外观层 `P0ModEntry(FMLJavaModLoadingContext)` |
| 5 | 模组变 **broken mod state**、刷 "Cowardly refusing to send event…"、出 crash report | `event.register(IItemHandler.class)` —— 1.21 的 `RegisterCapabilitiesEvent` 用法退化成丢参数伪影，而 1.20.1 **Forge 自己已注册 IItemHandler** ⇒ `Cannot register a capability implementation multiple times` | crash-report 里的 `Caused by` 一行 |
| 6 | 三个 netmusic 界面 mixin 注入失败 | `@Mixin(remap=false)` 是**类级**的，把 `init` 也罩住了；而 netmusic 生产 jar 里 `init()` **不存在**（它重写了 MC 方法 ⇒ 被重混淆成 `m_7856_`）| **javap 生产 jar**：`CDBurnerMenuScreen` 只剩 `handleCraftButton`。改后 refmap 出现 `{"init":"…CDBurnerMenuScreen;m_7856_()V"}` |
| 7 | **右键方块毫无反应**（用户实测报告） | 6 个方块类写的是 `useItemOn`/`useWithoutItem` —— **1.21 的方法名**；1.20.1 `BlockBehaviour` **只有** `use(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)` ⇒ 那些方法**不是重写 ⇒ 永远不被调用**（逻辑其实都移植好了）| `javap` 1.20.1 `BlockBehaviour` 只有 1 个 `use`。已按 1.21 语义接起来（有物品那条先走，返回 PASS 时落回空手那条）|

### 新增门禁：`tools/check_dead_overrides.py`（**死重写**）

第 1 次（NBT `saveAdditional(..., HolderLookup.Provider)`）与第 7 次（`useItemOn/useWithoutItem`）
是**同一个坑**：方法名或签名是**别的版本的** ⇒ 不报错、不是重写、静默失效。
门禁：源码里出现"1.21 有、1.20.1 没有"的方法名/签名就直接失败。
自带负例（塞假源码必须被抓到）⇒ **它会说"不"**。

### 实测通过的部分（游戏内日志实证）

* 模组正常构造并注册：`BiliAudioResolver registered with NetMusic`、
  `HttpAudioStreamHandler and BiliLiveAudioStreamHandler…`、
  `Registered server commands /netmusicbiliserver`（= 已进世界）；
* **内嵌原生库真的加载了**：`Eac3NativeDecoder: FFmpeg native 库提取成功` +
  `FFmpeg media native 库加载成功: 8.0.git` ⇒ §6.19"补 native"闭环；
* `Setting user` / `Sound engine` / `Reloading ResourceManager` 齐全，
  `FATAL 0`、`Cowardly refusing 0`、`Mixin apply failed 0`。

### 登记的新缺口

| 事项 | 说明 |
| --- | --- |
| ⑭ **两本附魔书没有** | 用户实测指出。上游是 **1.21 数据驱动附魔**（`data/…/enchantment/headphones.json`，效果＝给 HEAD 槽 +1.0 的 `net_music_can_play_bili:headphones` 属性）。1.20.1 附魔是**代码注册**且**不支持数据驱动属性加成** ⇒ 需注册两个 `EnchantmentCategory.ARMOR_HEAD` 的 1 级附魔 + 页签里放 `EnchantedBookItem.createForEnchantment(...)` + `has()` 改成"有该附魔 或 物品自带该属性" + lang 两条 |
| ⑮ 方块实体的物品能力 | 第 5 条删掉致命注册后，`BlockEntity#getCapability` 还没接 ⇒ 物品进不出转盘/唱片机 |
| ⑯ 方块 GUI 的"手势" | 转盘是**潜行右键**才开界面（普通右键是放唱片），控制台要 `canEdit` 权限。测试口径要按这个来，否则会误判成"没修好" |
| ⑰ `assemble.py` 作用域 | 只比 java 源根；资源不在覆盖范围（已在 verify 输出里显式打印） |
| ⑱ `assemble.py register` 只会追加 | **已修**：verify 对 `deleted`/`added` 是双向严格相等，而第一版 register 只往后追加 ⇒ "登记为组装新增、后来被删掉"的文件永远卡住门禁（本轮 3 条：迁包的 MeshData/ByteBufferBuilder + 删掉的借道类）。现在 register **双向对齐**并退掉陈旧登记；缩表时**同时降水水位线并记理由**（水位线的判据 `total < hwm ⇒ 自擦除` 保持不变，7/7 自检仍绿）|

## 6.21 第 91 次（再续）：**"右键方块没反应"的最终根因＝方块注册成了占位类**

### 症状与排查过程（值得记的是**怎么把范围缩小的**）

用户报告"右键方块还是没反应"。静态证据先说"修复在制品里"：jar 里的
`ModernTurntableBlock` 有重混淆后的 `m_6227_`（= `use`，1.20.1 的方块交互方法）。
于是装了两层诊断去分：

1. **方块自己的 `use()` 里插日志**（`port/diag/UseDiag`，默认开）——
   但只插方块时，"物品把交互吃掉"和"点到的不是我们的方块"在日志里**长得一模一样**（都是什么都没有）；
2. **Forge `PlayerInteractEvent.RightClickBlock` 探针**（在所有物品/方块逻辑**之前**触发，
   还能拿到**被点方块的 id 和类名**）—— 这一层立刻给出答案：

```
方块=net_music_can_play_bili:modern_turntable  方块类=FacingHasDiscBlock   ← 占位类！
```

### 根因

`init/ModBlocks` 的 6 个注册用的是**外观层自造的占位类**（`ModBlocks` 里的嵌套类
`ActivatedBlock` / `FacingActivatedBlock` / `FacingPlayingBlock` / `FacingHasDiscBlock`），
它们**全部 `extends Block`** —— 纯原版方块，**没有 `use()`、没有方块实体**。
⇒ 功能方块类（`ModernTurntableBlock` 等）**从未被实例化**，世界里放的是空壳，
**空手右键也毫无反应**（连 GUI 都不会尝试打开）。
上游**没有**这些占位类（全树 grep 实证），它们纯粹是"外观层先要模型"的产物。

这是**第三次**同一形状的缺陷 —— **外观层占位类型在组装时没换成功能实现**：

| 次 | 位置 | 表现 |
| --- | --- | --- |
| 1 | `ModItems` 用 `simple(name)`（裸 `Item`） | 物品没有行为（已修：`functional(name, XxxItem::new)`）|
| 2 | `NcpbRenderStateShardAccess` / 桥接口（同包借道） | JPMS 分裂包，启动即崩（已改成 AT）|
| 3 | `ModBlocks` 用嵌套占位类 | **右键方块毫无反应**（本次）|

### 修复前先做的三项核对（改注册＝动外观，不能瞎换）

1. **功能类与上游逐字一致**：基类 + 属性集合 6/6 一致；
2. **blockstate JSON 与上游逐键一致**：6/6 一致 ⇒ "这些 JSON 配这些类"上游已验证；
3. 功能类多出来的状态（转盘 `playing`、歌词投影仪 `linked`）JSON 里没列 ——
   这在 1.20.1 **允许**（未列属性是通配的），上游同样如此。**不需要动资源**。

> 排查中还踩了自己一个坑：第一遍用正则 `public static final \w+Property\s+(\w+)` 找属性，
> **漏了泛型写法** `EnumProperty<Direction> FACING`，于是误判"`SpeakerBlock` 没有 `facing`，
> 而 JSON 用了 `facing`"。复核后：它有。**判据写错会得出反向结论**，所以换类前必须复核。

### 游戏内实证（换类后的 `use()` 记录，两侧都走通）

```
方块=net_music_can_play_bili:modern_turntable  方块类=ModernTurntableBlock   ← 已是功能类
modern_turntable 进来了：side=CLIENT 空手=true → 返回 SUCCESS
modern_turntable 进来了：side=SERVER 空手=true → 返回 FAIL
```
另：`control_console` / `lyric_projector` / `video_projector` / `speaker` 空手普通右键均返回
`SUCCESS`（即走到了开界面那一步）；客户端钩子链 `…ClientHooks → …Client.openScreen →
setScreen(new …Screen(pos))` 逐环核对无断点。

**测试口径（重要）**：转盘的界面是**潜行 + 右键**才开（普通右键是"放唱片"）。
本轮日志里 39 次点击 **`潜行=true` 为 0 条** —— 所以"没反应"里有相当一部分是**手势不对**，
不是没修好。

### 门禁加固：`check_registration.py` 现在也查方块

判据同物品那条：`init/ModBlocks` 的常量池里必须出现 6 个功能方块类的**完整内部名**。
**结果**：功能 jar **18/18 通过**；负例用**外观层的 jar** → **13 条失败**（它注册的正是占位类）。





## 6.22 第 91 次（三续）：**补两本附魔书** + **解禁 MP4/Pad 画面层**

### ① 附魔书（用户实测指出"少了两本"）

**根因**：上游（1.21）的附魔是**数据驱动**的
（`data/net_music_can_play_bili/enchantment/headphones.json`，效果＝给 HEAD 槽 +1.0 的
`net_music_can_play_bili:headphones` 属性），页签里那两本书由 `ModItems` 用
`holders().lookupOrThrow(Registries.ENCHANTMENT).get(KEY)` 现查现造。
而本工程**连附魔注册都没有**（`ForgeRegistries.ENCHANTMENTS` / `DeferredRegister<Enchantment>`
全树为零）⇒ 那两个 `ResourceKey` 指向虚空 ⇒ 页签里自然没有书。

**1.20.1 的落地口径（差异必须写清楚）**：1.20.1 的附魔**不能给任意属性加成**
（`minecraft:attributes` 效果是 1.21 才有的；1.20.1 只有 `getDamageBonus`/`getDamageProtection`
这类固定钩子）。所以做成**等价语义**：

* 新增 `init/ModEnchantments`：`DeferredRegister<Enchantment>` + 两个 1 级附魔，
  类别 `EnchantmentCategory.ARMOR_HEAD`（对应上游 JSON 的 `"slots": ["head"]`），
  费用沿用上游的 `min_cost=12 / max_cost=35`；
* `HeadphoneAbility.has()` / `HolographicGlassesAbility.has()` 改成
  **`戴着头部装备 且（物品自带 HEAD 属性 或 物品带该附魔）`**
  —— 即"把耳机附魔打在任意头盔上，那头盔就是耳机"，与上游"附魔给属性"效果一致，
  只是实现从"属性加成"换成"直接查附魔等级"；
* 页签里放两本 `EnchantedBookItem.createForEnchantment(new EnchantmentInstance(..., 1))`；
* `enchantment.net_music_can_play_bili.{headphones,holographic_glasses}` 的 lang
  **外观层早就写好了**，不用补。

**门禁**：`check_registration.py` 增加第 ⑤ 组判据 —— `init/ModEnchantments` 必须存在且含两条附魔 id，
页签必须引用 `EnchantedBookItem` 与 `init/ModEnchantments`。功能 jar **23/23 通过**。
（踩了一个自己的坑：常量池里的字符串**不带引号**，第一版按 `"headphones"` 找 ⇒ 正例假失败。
**判据写错会得出反向结论** —— 与 §6.21 那个漏泛型的正则同类。）

### ② MP4/Pad 画面显示：解禁 10 个文件，做**一张 API 映射表**

上一轮评估：延后清单 17 个文件 / 6,053 行；其中"用 1.21 GPU API"的 6 个约 3,550 行。
本轮把 **MP4/Pad 整簇 + 手持钩子** 一起解禁（成组解禁，因为它们是互相引用的）：

```
client/renderer/item/ MP4GuiTexture · MP4ItemScreenRenderer · MP4OffscreenGuiRenderer
                      PadGuiTexture · PadItemScreenRenderer · PadMapGuiLayer
                      PadMapRenderContext · PadOffscreenGuiRenderer
port/shim/            PortPictureInPictureRenderer
mixin/                ItemInHandRendererMixin（并重新登记进 mixins.json）
```

**11 处编译错误全部属于同一族**（1.21 顶点/缓冲 API → 1.20.1 对应物），映射表如下
（**表本身是可复用的资产**，后续解禁剩下的文件直接照用）：

| 1.21 | 1.20.1 | 命中处 |
| --- | --- | --- |
| `MultiBufferSource.immediate(new ByteBufferBuilder(n))` | `MultiBufferSource.immediate(Tesselator.getInstance().getBuilder())` | 3 |
| `VertexConsumer.setColor(int)` | `.color(int)` | 6 |
| `.setUv(float,float)` | `.uv(float,float)` | 6 |
| `.setUv1(int,int)` | `.overlayCoords(int,int)` | 1 |
| `.setUv2(int,int)` / `.setLight(int)` | `.uv2(int,int)` / `.uv2(int)` | 3 |
| `.setOverlay(int)` | `.overlayCoords(int)` | 2 |
| `.setNormal(f,f,f)` | `.normal(f,f,f)` | — |
| `Tesselator.getInstance().begin(Mode,Format)` | `Tesselator.getInstance().getBuilder()` + `builder.begin(...)` | 1 |
| `BufferBuilder.buildOrThrow()` | `builder.end()` | 1 |

**又一个运行时地雷（已提前拆掉）**：`ItemInHandRendererMixin` 目标是 **Minecraft 的
`ItemInHandRenderer`**，却带着 3 处 `remap = false` ⇒ 生产环境里方法叫 `m_109371_`，
用字面量找不到 ⇒ **启动即崩**（与 §6.20 第 2/6 条同一个坑）。删掉后 refmap 出现
`{"renderArmWithItem": "...ItemInHandRenderer;m_109371_(...)"}` —— **制品级实证**，
而不是"我觉得应该行"。

**启动实证**：`Mixin apply failed 0`、`FATAL 0`、`Cowardly refusing 0`，到主菜单，
原生库照常加载；jar 从 9,199,271 B → **9,275,562 B**（新解禁的类进包）。

### 还没做的（如实登记）

| 事项 | 说明 |
| --- | --- |
| 画面层还剩 7 个文件延后 | `ModernTurntableRenderer` / `VideoProjectorRenderer` / `NetMusicDiscModelAdapter` / `HolographicGlassesWorldScreenRenderer` / `HolographicPreviewPipRenderer` / `TerrainPreviewGpuCache` / `TerrainPreviewSectionCompiler`（后三个是 hologram/地形预览，与 MP4/Pad 无关）|
| MP4/Pad 画面**尚未由我目视确认** | 编译通了、启动干净了，但"画面在手上显示对不对"要看游戏；部分旁路仍走 `port/shim` 的桩（那些桩是空转）|


## 6.23 第 91 次（四续）：**"交互存在但完全没有渲染"的根因 = 顶点收尾协议**

### 症状

用户实测：Pad/MP4 的**交互都正常**（能开、能用），但**画面完全没有**；连"白色机身四边形"都看不见。
探针计数显示：离屏那遍每帧在跑（`离屏帧开始` 568 次）、提交也在跑（`提交屏幕` 568 次），
用的还是真 `RenderType.entityCutout/entityTranslucent` ⇒ **提交了、但屏幕上什么都没有**。

### 根因（结构性证据）

**1.21 的 `VertexConsumer` 没有 `endVertex()`** —— 顶点在缓冲里隐式收尾（下一次 `vertex(...)`
或 `build()` 时结算）。**1.20.1 的 `BufferBuilder` 要求每个顶点显式 `endVertex()`** ——
少一次那个顶点就不算数，`end()` 时**顶点数为 0**，于是"批次刷了、屏幕空白"。

实测：本工程全树 `.vertex(` **30 处**，`.endVertex()` **0 处**（上游 1.21 源码里根本没这个方法）。
凡是从 1.21 移植过来的自绘几何（Pad/MP4 屏幕、投影仪、调试线框）**全中**。

### 修法（改一处，覆盖全部，而不是改 30 条链）

新增 `port/shim/AutoEndVertexConsumer`：**把 1.21 的隐式语义补回 1.20.1 的显式协议**。

* 所有链式方法都**返回垫片自己** ⇒ 整条链都走垫片；
* 每次 `vertex(...)` 之前先把**上一个未收尾的顶点** `endVertex()` 掉（= 1.21 的语义）；
* 采集器在发射器返回后**再收尾一次**，把最后一个顶点结掉；
* 接口里的 `default` 方法（`color(int)` / `uv2(int)` / `vertex(Matrix4f,...)` / `putBulkData(...)`）
  **不用覆写** —— 它们内部都会回调到本类实现的抽象方法，自动获得同样语义。

`PortSubmitNodeCollector.submitCustomGeometry` 改用该垫片 ⇒ **12 个文件的 30 处链一次修好**。

另外 **3 个直接写 `BufferBuilder` 的文件**（不经采集器：`PadMapGuiLayer`、`PortGuiTextures`、
`PortGuiFramebufferBlitter`，共 12 条顶点）显式补 `.endVertex()`。

> 自己踩的坑：第一版补 `endVertex()` 时把行尾的 `);` 当成 `;` 删掉了，写出
> `.color(-1.endVertex();` —— **20 个编译错误**。判据是"改完必须重编译"，不是"看起来对"。

### 待补门禁（下轮做）

"每个发射顶点的文件必须收尾"：含 `.vertex(` 的文件，要么走采集器垫片，
要么 `.endVertex()` 数 ≥ `.vertex(` 数。否则**静默什么都不画**（编译器完全不管）。


### 6.23 续：`endVertex` 修完之后的实测结果（用户报告）

**好消息**：手持 Pad/MP4 **有屏幕了** —— 证明"画到手上"整条链（mixin → `renderMapLike` →
采集器 → `BufferSource` 批次）已经通了，`AutoEndVertexConsumer` 那个根因修对了。

**坏消息**：**只有黑色画面**（与之前一致）。已排除的（逐项核过，不是猜）：

| 层 | 状态 |
| --- | --- |
| 手上那层的亮度 | `PadItemScreenRenderer` / `MP4ItemScreenRenderer` 都有 `FULL_BRIGHT = 15728880` ✔ |
| GUI 层的着色器 | `PadMapGuiLayer` / `PortGuiTextures` / `PortGuiFramebufferBlitter` 都用**无光照**的 `GameRenderer::getPositionTexColorShader` ✔ |
| 纹理绑定 | 三处都显式 `RenderSystem.setShaderTexture(0, tex)` ✔（1.20.1 必须显式，1.21 跟 RenderPipeline 走）|
| 顶点协议 | 三处都是 `begin(...)` + 4×`endVertex()` + `BufferUploader.drawWithShader(builder.end())` ✔（本轮刚补）|
| 离屏目标 | `PadOffscreenGuiRenderer` 建 `TextureTarget` → 包 `RenderTargetTexture` → `TextureManager.register` ✔（第 135-139 行）|

**因此黑色只剩两个可能**，判据是**回读 FBO 像素**（下一轮）：

1. **FBO 全 0** ⇒ 离屏那遍没画出内容（要看 `shouldRender/shouldRenderNow` 节流与 GUI 子树的绘制顺序）；
2. **FBO 有内容但采样为黑** ⇒ 画到的目标与 `RenderTargetTexture` 暴露的纹理不是同一张（或 UV/翻转不对）。

用"回读到的像素值"当判据，而不是继续推理 —— 这条纪律在本轮已经被证明有效三次
（模块层 stderr、refmap 映射、顶点收尾）。


### 6.23 续 2：**黑屏的根因 = "API 名一样、语义不一样"**（字节码实证）

用户实测：`endVertex` 修完之后**屏幕出来了但内容全黑**。

排查中把外层逐项排除（亮度 `FULL_BRIGHT` ✔、GUI 着色器无光照 ✔、显式
`setShaderTexture` ✔、顶点协议 ✔、离屏目标与注册 ✔），最后落到**缓冲区源**上：

**1.20.1 的 `MultiBufferSource.BufferSource.getBuilderRaw` 反汇编出来是**
```
getBuilderRaw(RenderType): getfield builder → Map.getOrDefault(type, builder) → checkcast BufferBuilder
```
即 `fixedBuffers.getOrDefault(type, this.builder)`；而 `MultiBufferSource.immediate(builder)`
传进去的 `fixedBuffers` 是**空表** ⇒ **所有 RenderType 共用同一个 `BufferBuilder`**。

偏偏 GUI 会混用三种顶点格式（`gui` = positionColor / `text` = 字体图集 /
`guiTextured` = positionTexColor）⇒ 同一缓冲里格式互相误读 ⇒ **整块屏全黑**。

**1.21 的 `MultiBufferSource.immediate(ByteBufferBuilder)` 是按类型分配的**，
上游那样写没问题。本工程当初做 1.21→1.20.1 API 映射时把它直译成
`immediate(Tesselator.getBuilder())` —— **换了 API 没换语义**，才留下这个黑屏。
> 教训：**API 名一样不代表语义一样**；映射表要连同"语义/契约"一起核对。

**修法**：新增 `port/shim/PortGuiBufferSource`（继承 `MultiBufferSource.BufferSource`
—— 因为 1.20.1 的 `GuiGraphics` 构造器要的是这个**具体类型**），
自己接管 `getBuffer`：**每个 RenderType 各一个 `BufferBuilder`**（用该类型自己的
`mode()`/`format()` 开始），`endBatch()` 按类型逐个 `end(builder, ORTHOGRAPHIC_Z)`
—— 每种类型都用自己的着色器与纹理状态绘制。两个离屏渲染器（Pad/MP4）的 `guiBuffer`
换成它。


### 6.23 续 3：黑屏收窄到**最后一环**（含两次仪器纠错）

**编码坑（先记，避免重复踩）**：用户用 PCL 启动时日志是 **UTF-8**，我用 `-JvmArg` 启动时是 **GBK**。
按错编码解码会让中文关键字全部搜不到（表现为"探针一条都没出"）。
⇒ 读日志必须**两种编码都试**，不要假定。

**仪器纠错（两次都是我的探针错，不是现象）**：

1. 反射代理计数 **漏掉 `vertex(Matrix4f,…)` 这个 default 方法**（它在真对象上执行，绕过代理）
   ⇒ 报出的 `合计发出顶点=0` 是**假象**，据此下的"GUI 没画"结论**已撤回**；
2. 回读只打 `非零像素/maxRGB`，**分不出"只有清屏色"与"清屏色+内容"**（有红底时两者都是非零）
   ⇒ 改成统计**不同颜色数**（`=1` 才是"只有清屏"）。

**修好仪器后的实测**（`AutoEndVertexConsumer` 当计数器 —— 它**显式覆写了** `vertex(Matrix4f,…)`）：

```
pad 各类型顶点数={gui=0}
pad 回读FBO 64x64: 不同颜色数=1  最常见色=#FFFF0000  非零=4096
```

⇒ **FBO 里只有清屏色**；`getBuffer(RenderType.gui())` 被调用过，但该缓冲里**顶点数 0**。
`drawGui` 的助手们**不是桩**（`fill` 就一行 `g.fill(...)`，23 个 `draw*` 都有实体代码）。
但 **MP4 未出现在 `离屏帧开始` 之前就被拦**（`提前返回` 探针 0 条，说明它连 `renderFrameStart` 都没进 →
拦它的是更外层，而不是我加灯的那两处）。

**最后一环（已由文末 §6.24 查清）**：确认 `g.fill(...)` 这条 vanilla 调用**到底把顶点写进了哪个 consumer**——
在 `PadOffscreenGuiRenderer.fill` 里加一行自增计数（证明助手真的跑了），
再在 `PortGuiBufferSource.getBuffer` 里打印**返回出去的对象身份**，两侧对不上就说明
`GuiGraphics` 拿到的是**别的**消费者的（1.20.1 的 `GuiGraphics` 可能对某些类型走自己的缓冲）。

### 调试构建状态（交付前必须撤）

| 项 | 位置 |
| --- | --- |
| 离屏清屏=**红色** | `PadOffscreenGuiRenderer` / `MP4OffscreenGuiRenderer` 的 `RenderSystem.clearColor` |
| 探针**默认开** | `UseDiag.ON` 的默认值（现为 `"true"`） |
| 回读/各类型顶点数/提前返回/离屏帧开始/提交屏幕/开始画GUI | `UseDiag.readbackFbo`、`PortGuiBufferSource`、两个 `*OffscreenGuiRenderer` |
| 其它探针 | 六个方块 `use()`、`MediaManagementToolItem`、`NetMusicCanPlayBili` 构造期的 `UseDiag.register()` |

**保留**：`AutoEndVertexConsumer`（那是**真修复**，不是探针）；`PortGuiBufferSource`（真修复）。

---

## 6.24 第 92 次：「`g.fill` 的顶点写给了谁」查清 —— 断点不在消费者身上，而在**收尾**那一步

### 6.24.1 这一问的答案（字节码定死）

`g.fill(...)` 的顶点写给了 **`GuiGraphics` 自己持有的那个 `BufferSource`**：
本工程里就是 `PadOffscreenGuiRenderer` / `MP4OffscreenGuiRenderer` 各自 new 出来的
`PortGuiBufferSource`（两处都是 `new GuiGraphics(minecraft, this.guiBuffer)`），
落点是它 `started` 表里 `RenderType.gui()` 对应的那个 `BufferBuilder`。
**不存在“另一个消费者”** —— 想当场对身份也不必猜：1.20.1 的 `GuiGraphics` 有
`public MultiBufferSource.BufferSource bufferSource()`，比一下引用即可。

`javap -p -c net.minecraft.client.gui.GuiGraphics`（mapped_official jar）实测的调用链：

```
fill(IIIII)             → fill(IIIIII)
fill(IIIIII)            → invokestatic RenderType.gui()   → fill(RenderType,IIIIII)
fill(RenderType,IIIIII) → getfield     bufferSource
                          invokevirtual MultiBufferSource$BufferSource.getBuffer(RenderType)
                          4 × ( vertex(Matrix4f,FFF) + color(FFFF) + endVertex() )
                          flushIfUnmanaged()
```

顺带两条确定的事实（后面都用得上）：

* `flushIfUnmanaged()` = `if (!managed) flush()`，而 `managed` 只在 `drawManaged(...)` 期间为 true
  ⇒ **每调一次 `g.fill(...)` 都会走一次 `bufferSource.endBatch()`**；
* 1.20.1 的 `fill` 自带 **4 次显式 `endVertex()`**（与 §6.23 四续记的“1.21 隐式收尾”差异一致）。

### 6.24.2 真根因：`endBatch()` 当时只打印、从不上传

`PortGuiBufferSource.endBatch()` 原来只有 `UseDiag.r(...)` + `counters.clear()`：
既不 `end(...)`、也不 `drawWithShader`，`started` 还从不清理。而 `g.fill` 每一次都经
`flushIfUnmanaged → flush → endBatch` 打到这里 ⇒ **顶点写进 builder 之后从未被提交**，
FBO 里只剩清屏色 —— 与 `回读FBO … 不同颜色数=1 最常见色=#FFFF0000` 完全吻合。
也就是说：**“写给了谁”一直是对的，缺的是“写完送出去”这一步。**

**修法**（照 vanilla `BufferSource.endBatch(RenderType)` 的同一招，字节码实证过）：
交给 `RenderType.end(builder, RenderSystem.getVertexSorting())` —— 它内部按顺序做
`setupRenderState()` → `BufferUploader.drawWithShader(builder.end())` → `clearRenderState()`，
于是每种类型都用自己的着色器/纹理/混合状态绘制（GUI 是二维 ⇒ 正交排序）。
空批次（只 `getBuffer` 过、一个顶点都没写）走 `builder.discard()`，不去碰空批次的 `end()`：

```java
for (TypeBatch batch : this.started.values()) {
    if (batch.builder.isCurrentBatchEmpty()) batch.builder.discard();
    else batch.type.end(batch.builder, RenderSystem.getVertexSorting());
}
this.started.clear();     // 结批后重置；下一次 getBuffer 重新 begin(...)
```

### 6.24.3 顺带查出的两处仪器缺陷（所以上一轮那条读数要打折看）

1. **`各类型顶点数` 恒为 0**：`counters` 只在 `getBuffer` 里 `put(type, new int[]{0})`，
   **全类没有任何地方自增**；而 `getBuffer` 返回的是**裸 `BufferBuilder`**，并没有包那个带计数口的
   `AutoEndVertexConsumer`。⇒ 上一轮“`getBuffer(RenderType.gui())` 被调用过，但该缓冲里顶点数 0”
   里，**“顶点数 0”这半句撤回**；它能证明的只有“`gui` 这一项被建立过”
   （= `fill` 确实跑过、确实来要过消费者）—— 这一点反而印证了 §6.24.1。
   本轮把计数做成**真数**：每种类型一个 `AutoEndVertexConsumer(builder, counter)`
   （它显式覆写了 `vertex(Matrix4f,…)`），`endBatch` 汇总进“上一帧”报表、`diagTag` 打一行。
   这层包装同时把 1.21 的隐式收尾语义带给 GUI 路径（vanilla 自己显式收尾，不受影响）。
2. **打印用的 `Collectors.toMap` 遇到重复键会抛异常**：短名归一化后，多个
   `guiTextured(<不同纹理>)` 会撞成同一个键 ⇒ `IllegalStateException("Duplicate key")`
   会把**正在被测量的那一帧**打死。已改成 `LinkedHashMap.merge` 手工汇总。

### 6.24.4 另一处同族缺陷：MP4 的绘制顺序与 Pad 相反

Pad 是**先 `bindWrite` + 设好正交投影，再 `drawGui`**（注释里写明了原因）；
MP4 原来是**先 `drawGui`、再绑 FBO** ⇒ 里面“立即绘制”的那部分
（`PortGuiTextures` / `PortGuiFramebufferBlitter` 的 `drawWithShader`）画到了主画面，
FBO 里只剩清屏色。已按 Pad 的顺序改正。

### 6.24.5 口径与判据

| 项 | 值 / 判据 |
| --- | --- |
| 全量编译 `tools\build_full.ps1`（`clean compileJava`） | **0 错误 / 7 警告**；**口径注意**：`deferred-excludes.txt` 仍有 **7 条**生效 ⇒ 这是“排除这 7 个文件”的口径（那 7 个是 Milestone B 的待办） |
| 本轮改动的两个文件确实参与编译 | ✔（`PortGuiBufferSource.class` / `MP4OffscreenGuiRenderer.class` 时间戳 = 本次构建） |
| 逐类型收尾 | `RenderType.end(...)`（`setupRenderState → drawWithShader → clearRenderState`）✔ |
| **画面** | **要实测**：日志里先看 `{} 上一帧：getBuffer=… 结批=… 次 各类型顶点数={…}`（应是真数、千级），再看 `回读FBO …: 不同颜色数≥2`，最后手持 Pad/MP4 看内容 |

> 交付前仍要按上一节的表撤探针（红底清屏、`UseDiag.ON` 默认 `true`、各处探针）；
> `AutoEndVertexConsumer`、`PortGuiBufferSource`、`RenderType.end` 这三处是**真修复**，要留。

> 这一轮的价值：把“消费者的身份”用字节码定死，并发现**断点不在消费者身上，而在收尾那一步**。

### 6.24.6 收尾时又抓出四处“门禁在查错对象 / 自己在报假绿”（同一族）

1. **两个门禁的默认工程目录是外观层**：`check_reobf.ps1` / `check_mixin_wiring.ps1` 的
   `-ProjectDir` 默认值都是 `ncpb-1.20.1`（127 KB 的外观 jar），而**交付 jar 自第 88 次起由
   `ncpb-assembled` 产出** ⇒ 按默认值跑，它们“通过”的是**没人部署的那个 jar**。
   实测（把 `-ProjectDir` 指到交付工程）：`check_reobf.ps1` 以**未登记退出码 1 + javap 报错**
   收场（探针类不存在）；`check_mixin_wiring.ps1` 的“探针 Mixin 痕迹”那条 FAIL（交付件没有
   阶段 0 的探针 Mixin）。修法四条：
   * 两者的默认 `-ProjectDir` 改成 `ncpb-assembled`；
   * **把同名同签名的 `P0ReobfProbe` 也放进交付工程**（已 `assemble.py register`，见下）；
     “探针缺失”改成显式的 **exit 3 前置缺失**，不再以 javap 报错收场；
   * 接线门禁那条断言放宽成“探针痕迹 **或** 任意一条真实 SRG 映射 `m_<n>_`”——
     只认探针会让它在**真交付件上永远 FAIL**，而“永远失败的断言”等于没查；
   * `audit_deployed_jar.py` 的 `DEFAULT_JAR` 同样改成交付工程。

   修后实测：`check_reobf.ps1` 三条全 PASS（dev=官方名 / reobf=SRG / 可部署 jar=SRG），
   `check_mixin_wiring.ps1` 七条全 PASS（refmap 11 条真实映射，如
   `GuiRoamingCrosshairMixin.renderCrosshair → Gui.m_280130_`），
   两者的 `-SelfTest` 负例**仍然如实报 FAIL**（鉴别力没被放宽吃掉）。

2. **组装门禁早就红着**：`python tools/assemble.py verify` 因上一轮新增的
   `AutoEndVertexConsumer.java` / `PortGuiBufferSource.java` **未登记**而报 2 项失败
   （“红着的门禁 = 没有门禁”）。按它的规矩（`register` 是唯一写入口）登记后：
   **178 条、verify 通过**。⇒ 记一条：**往 `ncpb-assembled` 加新文件必须 `assemble.py register`**。

3. **HANDOVER §3.1 的构建路径是旧工程**：照抄那段 `Set-Location ncpb-1.20.1` +
   `Copy-Item build\libs\*.jar`，会把**外观层的 127 KB jar 覆盖到测试床的 mods/**
   （移植 jar 就没了）。已改成 `ncpb-assembled` 并写了警告。

4. **改 `.ps1` 会掉 BOM（本轮我自己踩的）**：`tools/` 下的脚本都是 **UTF-8 带 BOM**，
   用不带 BOM 的方式重写后，PowerShell 5.1 会按 GBK 解码 ⇒ **整段中文字符串被读成乱码、
   脚本直接语法错**（实测两处 `Unexpected token`）。判据：`头3字节 == EF BB BF`。
   两条脚本已补回 BOM 并复跑通过。这与 §6.23 记的“日志必须两种编码都试”是同一族坑。

### 6.24.7 本轮交付件与验证证据

| 项 | 值 |
| --- | --- |
| 全量编译 | `tools\build_full.ps1`（`clean compileJava`）⇒ **0 错误 / 7 警告**（口径：`deferred-excludes.txt` 的 7 条仍生效） |
| jar | `ncpb-assembled/build/libs/net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar`（**9,284,598 B**，sha256 `A19B4AAF…E70B0DE3`） |
| 部署 | 已复制到 `D:\航空学\.minecraft\versions\1.20.1-Forge_47.4.26\mods\`，**两侧 sha256 一致**；`mods/` 里只有我们的 jar + netmusic（符合 §3.1 的归因要求） |
| 重混淆 | 门禁 3/3 PASS（**默认参数已指向交付工程**，并给交付工程补了同名 `P0ReobfProbe`）；另有人工证据：jar 内 `PortGuiBufferSource` 调的是 `RenderType.m_276775_`（= `end`）、`BufferBuilder.m_231164_`（= `isCurrentBatchEmpty`）/`m_85730_`（= `discard`） |
| Mixin 接线 | 7/7 PASS（refmap 11 条真实映射；两档判据里命中的是“真实 SRG 映射”那档） |
| 模型 | `audit_deployed_jar.py` PASS（24 个模型、11 个自订 loader）—— `build/libs` 与 **`mods/` 里那一份**都查过 |
| 注册 | `check_registration.py` PASS（判据 23 条、失败 0）；它本来就已经默认查交付工程 ✔ |
| 组装登记 | `assemble.py verify` PASS（**178 条**；新增的 `P0ReobfProbe` 与两个 shim 都已登记） |
| 教训 | 本轮写进 `D:\work\port-1.21.1\misakanet-pending\`：**L34**（写对了地方≠送出去了 + 恒 0 探针）、**L35**（门禁默认查旧产物 / 永远失败的断言）、**L36**（`.ps1` 掉 BOM ⇒ GBK 解码语法错）；`refresh_all_lessons.ps1` 已收录（L1–L36） |

> 探针仍在（红底清屏、`UseDiag.ON=true`、FBO 回读、各类型顶点数）—— **这一轮就是要它们**；
> 交付前按 §6.23 那张表撤。**画面结论要用户实测**：日志先看
> `{} 上一帧：getBuffer=… 结批=… 各类型顶点数={…}`，再看 `回读FBO …: 不同颜色数≥2`。

---

## 6.25 第 93 次：用户把日志带回来了 —— 又两处根因，且**上一轮的修复确实生效了**

### 6.25.1 先立住事实：收尾/上传这一环已经修对

用户实测后 `latest.log`（UTF-8）里的三个关键读数：

```
pad 上一帧：getBuffer=241 次 结批=143 次 各类型顶点数={gui=508, forge_text=456}
pad 回读FBO 64x64: 不同颜色数=1 最常见色=#FFFF0000 非零=4096
mp4 提交屏幕：…（1242 条）        mp4 离屏帧开始：…（0 条）      mp4 开始画GUI：…（0 条）
```

* `各类型顶点数` 从 §6.24.3 的“**恒 0**”变成了 **508 + 456 的真数**、每帧结批 143 次
  ⇒ §6.24 的“逐类型 `RenderType.end` 收尾 + 真计数”**修对了**；
* 但回读仍是 **不同颜色数=1（只剩清屏色）**，用户的观感是 **Pad 红、MP4 黑**。

⇒ **顶点确实被提交了，只是没落进可见画面** —— 于是问题从“有没有送出去”变成“送到哪儿去了”。

### 6.25.2 根因 A：离屏那遍少了 GUI 的 **z 平移**（`setIdentity()` 之后必须 translate）

1.20.1 vanilla `GameRenderer.render` 的 GUI 段（`javap -p -c` 实证）：

```
setOrtho(0, guiW, guiH, 0, 1000.0f, ForgeHooksClient.getGuiFarPlane())
setProjectionMatrix(ortho, ORTHOGRAPHIC_Z)
modelViewStack.pushPose(); modelViewStack.setIdentity()
modelViewStack.translate(0.0, 0.0, 1000.0f - getGuiFarPlane())    // ← 我们缺的就是这一句
applyModelViewMatrix()
```

`GuiGraphics.fill` 写出的顶点 **z = 0**，而正交投影的可见 z 区间（相机朝 −z）是 **[−far, −1000]**
⇒ 只 `setIdentity()` 时，**每个 GUI 顶点都在近/远平面之外被裁掉**：批次照画、GL 照收，
屏幕上什么都不出现，FBO 里只剩清屏色。这就是“红/黑”的直接原因
（`PortGuiTextures` / `PadMapGuiLayer` / `PortGuiFramebufferBlitter` 的“立即绘制”同样是 z=0，一起被裁）。

**修法**：两个离屏渲染器都照 vanilla 原样平移，远平面改用 `ForgeHooksClient.getGuiFarPlane()`
（别的模组（光影/优化模组）调过远平面时也不会把我们裁掉）。

### 6.25.3 根因 B：MP4 的离屏那一遍**从来没被触发**

判据是两个探针的条数对不上（一个数就够定罪）：

| 探针 | 次数 |
| --- | --- |
| `mp4 提交屏幕`（把纹理贴到手上那张） | **1242** |
| `mp4 离屏帧开始` / `mp4 开始画GUI`（真正渲染纹理那遍） | **0** |

Pad 是**在自己的渲染路径里**驱动离屏帧的（`PadItemScreenRenderer#renderMapLikeInternal` 里的
`textureFor(deviceId).renderFrameStart(deviceId, partialTick)`），所以它有 442 次；
MP4 只有静态钩子 `renderHeldOffscreenGuiFrameStart()`，而**全工程没有任何调用者**
⇒ 离屏纹理从没被写过 ⇒ 手上贴的是一张空纹理（黑）。
**修法**：在 MP4 的渲染路径里按 Pad 的做法补触发点（`deviceId != null` 时 `renderFrameStart`）。

> 这两个都是“**读代码完全合理、只有拿运行时数字对照才暴露**”的缺陷：
> A 要拿“提交顶点数 vs 回读颜色数”对照，B 要拿“两个探针的条数”对照。

### 6.25.4 补一条金丝雀（同类缺陷以后自己喊）

`UseDiag.readbackFbo` 改成**返回不同颜色数**；两个离屏渲染器回读后加判据：
**顶点数 > 0 且颜色数 == 1 ⇒ 打印 `!! … 只有清屏色 ⇒ 查正交投影 z 区间 / 模型视图平移`**。
于是“画了但被裁掉”不会再伪装成“没画”。

### 6.25.5 本轮产物与待验

| 项 | 值 |
| --- | --- |
| jar | `net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar`，**9,285,346 B**，sha256 `6358C7AD…7534D376` |
| 部署 | 已复制到测试床 `mods/`，**两侧哈希一致** |
| 门禁 | 重混淆 3/3、Mixin 接线 7/7、注册 23 条 0 失败、组装登记 178 条 —— 全通过 |
| **待验（用户再测一次）** | `回读FBO … 不同颜色数 ≥ 2`（红底之上真的出现内容）；MP4 也应开始出现 `mp4 离屏帧开始` |

---

## 6.26 第 94 次：画面出来了 —— **但只填左上角四分之一**（投影用了设备像素，GUI 是逻辑像素）

### 症状（用户截图 + 日志）

两张截图里 Pad/MP4 的 UI **已经渲染出来了**（§6.25 的两处修复都生效：面板、文字、地图、按钮、
时间 3:10 都在），但内容只占**左上角约 1/4**，其余全是清屏色（红）。
这正是"**FBO 是 `SCALE×` 的设备像素，而 GUI 画的是逻辑像素**"的图形：

| 项 | 值 |
| --- | --- |
| FBO / target | `TARGET_WIDTH×TARGET_HEIGHT` = 448×256 **× SCALE(2)** = **896×512** |
| GUI 绘制坐标 | 逻辑像素 448×256（`fill(0,0,WIDTH,HEIGHT,…)`、`emitLandscapeGuiRect(…, 448, 256)`） |
| 第 93 次那版投影 | `setOrtho(0, TARGET_WIDTH, TARGET_HEIGHT, …)` = **896×512** ⇒ 逻辑坐标只铺满 **1/SCALE²** |

⇒ 投影（逻辑→NDC）与视口（FBO 的设备像素）**量纲不一致**，UI 就只填了左上角。
（第 93 次之前它"完全看不见"，是因为顶点被 z 裁剪；把 z 的问题修掉之后，**这个量纲问题才显形** ——
两个缺陷叠在一起，先修哪个都只能看见一部分真相。）

### 上游怎么办的（取证）

* `0.7.9-beta+neo1.21`（移植来源）：`setOrtho(0, TARGET_WIDTH, TARGET_HEIGHT, …)` + `drawGui` 在 `bindWrite` 之前
  —— 与移植版一样的写法；
* `0.7.10-beta`（**权威版**，MC 26.x）多了一句**关键**：

  ```java
  int oldGuiScale = windowState.guiScale;
  windowState.guiScale = Math.max(1, SCALE);      // ← 临时把 GUI 缩放设成 SCALE
  ... setOrtho(0, TARGET_WIDTH, TARGET_HEIGHT, ...) ...
  windowState.guiScale = oldGuiScale;             // ← 事后还原
  ```

  即上游靠"**把 GUI 缩放设成 SCALE**"让逻辑坐标在设备像素里铺满；1.20.1 没有那层
  “GUI 缩放进模型视图”的机制，等价做法是把**投影写成逻辑尺寸**。

### 修法（两处，各一行）

```java
.setOrtho(0.0F, WIDTH, HEIGHT, 0.0F, 1000.0F, ForgeHooksClient.getGuiFarPlane());
```

投影用**逻辑像素**（Pad 448×256 / MP4 `WIDTH×HEIGHT`），视口仍是 FBO 的 896×512
⇒ 逻辑坐标铺满整张纹理，**顺带得到 SCALE× 超采样**（这正是 `SCALE` 存在的意义）。
scissor 保持设备像素（`0,0,TARGET_WIDTH,TARGET_HEIGHT`）不变 —— scissor 本来就按帧缓冲像素算。

### 补第二条金丝雀（"内容只填一部分"）

`UseDiag.readbackFbo` 加了带采样原点的重载；两个离屏渲染器现在采**两处**：
左下角 + **右上角**（`TARGET_WIDTH-64, TARGET_HEIGHT-64`）。判据：

* 左下角 = 清屏色 且 顶点 > 0 ⇒ `!! … 只有清屏色 ⇒ 查正交投影 z 区间 / 模型视图平移`（§6.25 那族）；
* 左下角有内容 而 右上角只有清屏色 ⇒ `!! … 内容只填了一部分（投影/缩放与 FBO 尺寸不匹配）`（**本轮这族**）。

### 本轮产物

| 项 | 值 |
| --- | --- |
| jar | `net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar`，**9,285,786 B**，sha256 `4E3BA32C…6C2909D7` |
| 部署 | 已复制到测试床 `mods/`，两侧哈希一致 |
| jar 内证据 | `setOrtho` 实参是 **448.0f / 256.0f**（Pad 字面量、MP4 用 `WIDTH/HEIGHT` 字段）+ `1000.0f` / `getGuiFarPlane()` ✔ |
| 门禁 | 重混淆 / Mixin 接线 / 注册 23 条 / 组装 178 条 / 模型 24 个 —— 全通过 |
| **待验** | 再测一次：UI 应**铺满整张屏幕**，且日志里**不出现**两条 `!!` 金丝雀 |

---

## 6.27 第 95 次：视频投影仪 —— 排除清单里那条**过期了**（放回编译 0 错误）+ 补回被摘掉的注册

### 6.27.1 第一步：把 `VideoProjectorRenderer` 放回编译，javac 说"0 错误"

```
python tools\deferred_cut.py remove com/.../client/renderer/VideoProjectorRenderer.java --reason "…"
python tools\build_full.ps1        ⇒ 0 错误 / 7 警告
```

⇒ **排除清单 7 → 6**（`deferred_cut.py check` 5 项全过，水位线跟着降）。
这是 §3.79 那条纪律（**排除清单要定期从"无 exclude 基线"反算**）的**第四次收益**：
它当初被排除时确实有缺口，而规则表后来把这些缺口都补上了 —— **清单却没人回头看**。

### 6.27.2 第二步：把 `strip_deferred_refs.py` 连语句一起摘掉的**注册**补回来

`ModernTurntableClientEvents.registerRenderers` 里只剩 2 个渲染器，上游是 5 个：

| 上游语句 | 移植版（改前） | 现状 |
| --- | --- | --- |
| `MODERN_TURNTABLE → ModernTurntableRenderer::new` | 缺 | ⛔ 该类仍在排除清单（6 条之一） |
| `LYRIC_PROJECTOR → LyricProjectorRenderer::new` | 有 | ✔ |
| **`VIDEO_PROJECTOR → VideoProjectorRenderer::new`** | **缺** | ✅ **本轮补回** |
| `SPEAKER → SpeakerRenderer::new` | 有 | ✔ |
| `CONTROL_CONSOLE → ControlConsoleRenderer::new` | 缺 | ⚠️ **类编得过、无阻塞**（下一轮可直接补） |

没有这一行时的症状很好认：方块放下去**只有静态模型**（`getRenderShape = MODEL`），投影幕永不出现，
而日志里**不会有任何异常**。资产侧是齐的：`blockstates/video_projector.json` 有 `activated=false/true`
两个变体、模型带 `ncpb_euler_elements`（含 3 片百叶 x=90）。

### 6.27.3 顺带查清的两件事（都**不需要**改）

* `MediaEquipmentBindingService` 里 `case VIDEO_PROJECTOR -> unhandled()` **是上游本来的行为**
  （逐行比对上游同文件），不是移植缺口。投影仪的绑定走另一条路：
  **手持投影仪方块物品先右键唱片机**（`LinkHelper` 把目标坐标写进物品）→ 放置时
  `VideoProjectorBlock.setPlacedBy → applyLinkedPosition` 自动 `linkTo`。
* `_deferred-mixins/OffscreenGuiPassFixMixin.java` / `OffscreenGuiProjectionFixMixin.java` **不要启用**：
  它们是 1.21.1 移植版（`D:\work\port-1.21.1\PAD-UI-FINDINGS.md`）用来修
  “先画后绑目标 / z 裁剪 / 投影口径”的补丁，而本工程的**源码里已经把这三处直接改对了**（§6.25 / §6.26）
  ⇒ 启用会重复施加。（那份文档独立复现了同样的两个根因，是本轮修法的旁证。）

### 6.27.4 玩家侧怎么验（给用户的步骤）

1. 创造模式取「视频投影仪」；**手持它先右键现代唱片机**（物品记住目标），再放到地上/天花板
   ⇒ 放置即绑定（投影仪界面里会显示 `已连接 (x,y,z)`）。
2. 右键投影仪 → 打开配置界面（偏航 / 俯仰 / 高度 / 水平·纵深偏移 / 尺寸 / 亮度 + 画质）。
3. 唱片机播放视频源 → 应当出现一张按这些参数摆在世界里的**投影幕面片**。

### 6.27.5 仍未接回来的“被摘语句”清单（逐条登记，免得再被静默漏掉）

| 上游语句 | 阻塞 |
| --- | --- |
| `MODERN_TURNTABLE → ModernTurntableRenderer::new` | 该类仍在排除清单（6 条之一） |
| `CONTROL_CONSOLE → ControlConsoleRenderer::new` | **无阻塞**（类编得过） |
| `modEventBus.addListener(CuriosHeadGearLayer::register)` | 待确认该类是否在编译范围 |
| `PortPictureInPictureRenderer.registerPipRenderer(HolographicPreviewPipRenderState.class, HolographicPreviewPipRenderer::new)` | `HolographicPreviewPipRenderer` 仍在排除清单 |
| `modEventBus.addListener(YuvVideoRenderTypes::registerPipelines)` | 待确认 |
| `MP4ItemScreenRenderer.warmup() / PadItemScreenRenderer.warmup()`（`warmupClientResources` 内） | 无阻塞（现靠懒初始化，Pad/MP4 已实测可用） |

> **通用教训**：`strip_deferred_refs.py` 这类“摘引用”的工具**只保证编译过，不保证接线还在**。
> 每把一份文件从排除清单放回来，都要同时问一句：“它当初被摘掉的那几行**语句**（注册/监听/入口）在哪？”

### 6.27.6 本轮产物

| 项 | 值 |
| --- | --- |
| 全量编译 | `clean compileJava` **0 错误 / 7 警告**（口径：排除 **6** 条） |
| jar | `net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar`，**9,293,227 B**，sha256 `94298DBD…94D1DE8` |
| 部署 | 已复制到测试床 `mods/`，两侧哈希一致 |
| jar 内证据 | 制品里有 `VideoProjectorRenderer`（`BlockEntityRenderer<VideoProjectorBlockEntity>`），`registerRenderers` 存在 |
| 门禁 | 重混淆 3/3、Mixin 接线 7/7、注册 23 条 0 失败、组装 178 条、**排除清单 5 项**、模型 24 个 —— 全通过 |

---

## 6.28 第 96 次：用户实测回报三条 —— 光亮 / 红滤镜 / **视频渲染的真根因（1.21 专有着色器 include）**

### 6.28.1 “投影仪启动时应产生光亮” ⇒ 移植时漏了 `lightLevel`

逐树比对（`lightLevel` 三处）：

| 树 | 有 `lightLevel` 的方块 |
| --- | --- |
| 权威版 0.7.10 | `LyricProjectorBlock` 15、`SpeakerBlock` 10、**`VideoProjectorBlock` 15** |
| 移植来源 0.7.9+neo1.21 | `LyricProjectorBlock` 15、`SpeakerBlock` 10（**视频投影仪本来就没有**） |
| **本移植版（改前）** | `LyricProjectorBlock` 15、`SpeakerBlock` 10 |

⇒ **视频投影仪的 15 级光照在搬运时整条丢了**（来源版自己就缺，权威版有）。
修法（1.20.1 的写法就是 `Properties#lightLevel`）：

```java
public VideoProjectorBlock(Properties properties) {
   super(properties.lightLevel(state -> state.getValue(ACTIVATED) ? 15 : 0));
   ...
}
```

### 6.28.2 “MP4 与 Pad 视频蒙了红色滤镜” ⇒ **是我自己留的红底探针**

`RenderSystem.clearColor(1,0,0,1)` 是第 91 次为对照“FBO 里是不是只有清屏色”设的探针，
而 `clearColor` 的**alpha 也是 1** ⇒ UI 的透明处变成**不透明红**。Pad/MP4 在“视频 + 周边 UI”
模式（`splitGuiAroundVideo`）下正是靠这些透明处把**后面的视频层**透出来 ⇒ 视频被蒙上一层红。
已把两个离屏渲染器恢复成上游的**透明**清屏色 `clearColor(0,0,0,0)`。

### 6.28.3 ★ 视频渲染的真根因：**1.20.1 没有 `dynamictransforms.glsl`**（1.21 才有的 include）

用户日志（原文）：

```
ERROR [YuvVideoRenderTypes]: YUV core shader 'bili_yuv420p_entity' failed to compile.
      YUV/NVN surfaces fall back to a single-plane display; restart with
      video.iris.disable_yuv_shader=true to force CPU RGBA conversion instead.
```

`client.jar`（1.20.1 原版）实证：

| 事实 | 证据 |
| --- | --- |
| `assets/minecraft/shaders/include/` **只有** `fog / projection / matrix / light` 四个 | 列 jar 条目 |
| **没有 `dynamictransforms.glsl`** | 同上（它是 1.21 才加进来的） |
| 1.20.1 的 `ColorModulator` 是**各着色器自己声明**的 | `position_tex_color.fsh`：`uniform vec4 ColorModulator;` |
| 1.20.1 的 import 是**无命名空间**写法 | 原版 `#moj_import <fog.glsl>` / `<light.glsl>` |
| 1.20.1 的 `fog.glsl` 只有 `linear_fog / linear_fog_fade / fog_distance` | 读 include 原文（**没有** 1.21 的 `apply_fog` 与 `FogEnvironmental*`） |

而本工程三个核心着色器照抄了 1.21 的写法 ⇒ 在 1.20.1 上**永远编不过**（日志里那串
`Could not open GLSL import minecraft:shaders/include/dynamictransforms.glsl` 就是同一件事）
⇒ YUV 视频退化成“单平面显示” ⇒ 投影仪/手持设备的视频画面全废。

**修法（三个 fsh）**：把 include 换成**直接声明**要用到的 uniform，并把 1.21 的雾函数换回 1.20.1 的：

| 文件 | 改法 |
| --- | --- |
| `yuv420p_entity.fsh` | 删 `#moj_import <minecraft:dynamictransforms.glsl>` → `uniform vec4 ColorModulator;` |
| `nv12_entity.fsh` | 同上 |
| `yuv420p_textured_probe_entity.fsh` | `#moj_import <fog.glsl>`（去命名空间）+ 声明 `ColorModulator`/`FogStart`/`FogEnd`/`FogColor`，`apply_fog(...)` → `linear_fog(color, dist, FogStart, FogEnd, FogColor)`；并把三个雾 uniform 补进 `bili_yuv420p_textured_probe_entity.json`（写法与**原版 `rendertype_solid.json` 逐字一致**） |

### 6.28.4 给投影仪装上“状态探针”（下一轮日志就能定位“没有画面”）

`VideoProjectorRenderer.render` 里加一行（`MediaLogThrottle` 节流：**载荷变化才打**，不刷屏）：

```
[NCPB/diag] video_projector {x,y,z} 状态：visible=… linked=… session=… frame=WxH|无 privacy=…
```

这条把“投影仪有没有画面”拆成**五环**：`visible`（绑定 + 唱片机在播 / 本地图片）→ `linked`
→ `session`（视频会话）→ `frame`（拿到帧）→ 绘制。断在哪一环一眼可见。

> 顺带说明一个**尚未证实**的怀疑：用户那次测试右键时手持的是 `music_cd`（音乐 CD = **音频**源），
> 而 `state.visible` 只看“唱片机在播”，**不看源是不是视频** ⇒ 方块会进激活态（看起来“启动了”），
> 却因为没有视频会话而**没有帧、也就没有投影幕**。这一条下一轮用状态探针判。
> （要不要给“音频源 + 激活”也画一个占位幕，是**产品决定**，不是缺陷；先取证再定。）

### 6.28.5 顺带发现一处**门禁盲区**（记下来，避免重复踩）

* `assemble.py verify` 的作用域是“**只含 java 源根，不含 `src/main/resources`**”；
* `sync_assets.py` 的 `--check` 目标是 **`ncpb-1.20.1`**（外观层工程），不是交付工程 `ncpb-assembled`；
* ⇒ **交付工程的资源（含本次这三个着色器）没有任何门禁**。这正是“一份 1.21 专有着色器
  能一路活到运行期、直到日志报 compile error”的原因。
  建议（下一轮）：把 assembled 的资源也纳入登记/校验（复用 `assemble.py` 那套水位线机制），
  或把 `sync_assets.py` 的 DST 指到 assembled。

### 6.28.6 本轮产物与判据

| 项 | 值 |
| --- | --- |
| jar | `net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar`，**9,294,740 B**，sha256 `C329FB6F…B02B64E23` |
| 部署 | 已复制到测试床 `mods/`，两侧哈希一致 |
| jar 内证据 | 三个 fsh 的**代码行**里已无 `dynamictransforms` / `apply_fog` / `FogEnvironmental` / 带命名空间的 import ✔（注释里保留了“为什么”，那是刻意的） |
| 门禁 | 重混淆 3/3、Mixin 接线 7/7、注册 23 条、组装 **179 条**、排除清单 5 项、模型 24 个 —— 全通过 |
| **待验（用户）** | ① 方块激活后**发光**；② MP4/Pad 视频**不再蒙红**；③ 日志里**不再出现** `YUV core shader … failed to compile`；④ 投影仪状态探针那一行告诉我们“没有画面”断在哪一环 |

---

## 6.29 第 97 次：投影仪“没画面”的真身 —— `VideoPlaybackPresentation` 是一个 **62 行的手写桩**

### 6.29.1 状态探针把前四环全点亮了，可是仍然没画面

第 96 次部署的探针在日志里打出：

```
video_projector BlockPos{x=-2,y=-60,z=2} 状态：visible=true linked=BlockPos{x=-3,y=-60,z=2}
                                            session=-824633708604-771800 frame=320x180 privacy=false
```

⇒ 绑定 ✔、会话 ✔、帧 ✔ …… 但用户依旧看不到画面。

### 6.29.2 那个 `frame=320x180` 是 **LOADING 占位图**，不是视频帧

两条**独立**证据：

| 证据 | 内容 |
| --- | --- |
| 占位图尺寸 | `VideoPlaceholderFrames` 的 `loading_base_phase0.png` 实测 **320×180** —— 与探针里那个尺寸**一模一样** |
| 桩 vs 上游 | 本工程 `VideoPlaybackPresentation.java` = **62 行**，每个方法 `return ProjectorFrameSnapshot.empty()`、`submit(...)` 空实现；上游同文件 = **271 行 / 14.5 KB** 真实现 |

即：`currentProjectorDisplayFrame` 只能走到 `VideoPlaceholderFrames` 那一支，**真帧路径整条被桩吃掉**。
投影仪因此“能激活、能建会话、能拿占位图，却永远拿不到真帧、也永远不会提交投影幕”。

### 6.29.3 修法：把上游真实现**逐行搬回**（只改一处包名）

搬之前先逐项核对“能不能搬”，结论是**一个依赖都不缺**：

* `VideoPlaybackInstance` 侧 27 个成员（`hasFrame` / `networkFailure` / `textures` / `consumers` / `anchor` /
  `grantDecodeAdmission` / `pumpUploadOnRenderThread` / `firstYuvImmediateLogged` / `decoderGenerationStartedNanoTime`…）全在；
* `VideoBillboardPreview`（继承链 `… → VideoBillboardQuadSupport → VideoBillboardState`）需要的 15 个静态方法全在；
* `VideoBerConsumerVisibilityPolicy` / `VideoRestartSuppressionPolicy` / `ControlConsoleRenderer.isPredictivePrewarmActive` /
  `VideoPipelineProperties.loadingPlaceholderEnabled` / `IrisShaderpackCompat.shouldApplyIrisYuvCompatibility` 也都在。

**唯一改动**：`net.neoforged.neoforge.client.event.RenderLevelStageEvent` →
`net.minecraftforge.client.event.RenderLevelStageEvent`（1.20.1/Forge 的同一个事件）。
**结果：一次编过（0 错误）**；调用链与上游逐行一致
（`VideoPlaybackInstance:687 submit` / `:691 renderYuvImmediate` / `VideoBillboardPreview:866 renderYuvImmediate`）。

> 这一步同时把“视频渲染有问题”的另一半补上了：真实现里的 `markVisibility(...)` 才是
> **授予解码准入**（`grantDecodeAdmission`）的地方 —— 桩在的时候，“投影仪看得见”这件事
> 永远传不到解码器那边。

### 6.29.4 字幕投影仪也装了状态探针

它的激活门槛是四道门：**唱片机在播 → 有歌词记录 → 当前/翻译行非空 → visible**（`LyricProjectorRenderer` 第 95–133 行，
与上游逐行一致）。探针一行打全：

```
lyric_projector {pos} 状态：visible=… linked=… playing=… lyricRecord=… line=…/… allowAi=…
```

⇒ 下一轮就能区分“这首歌本来就没有歌词（设计如此）”与“真缺陷”。

### 6.29.5 一条通用教训（已进 L37）

> **为了把编译错误降到 0 而写的“最小桩”，会在功能阶段以“能激活、能建会话、但永远没有数据”的形式复发。**
> 桩必须带**债务标记**（哪个类、为什么、什么时候该回收），并且在“要验证功能”之前先回收掉；
> 本次就是靠**一个尺寸**（320×180 = 占位图尺寸）把桩揪出来的。

### 6.29.6 本轮产物

| 项 | 值 |
| --- | --- |
| jar | `net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar`，**9,301,337 B**，sha256 `B08E7606…F6A8053D` |
| 部署 | 已复制到测试床 `mods/`，两侧哈希一致 |
| 门禁 | 重混淆 3/3、Mixin 接线 7/7、注册 23 条 0 失败、组装 **180 条**、模型 24 个 —— 全通过 |
| **待验** | ① 投影仪**出真画面**（探针里 `frame=` 应是**真分辨率**、且 `yuv=`/`rgbaTex=` 有纹理、`drawn=true`）；② 字幕投影仪探针行指出四道门里哪一道没过 |

---

## 6.30 第 98 次：中控台 = 和投影仪**同一个缺陷**；顺带把唱片机渲染器也救回来了

### 6.30.1 中控台的情况：`ControlConsoleRenderer` 编得过、消费者也一直在注册，**只差那行 BER 注册**

上游 `registerRenderers` 有 **5** 行，移植版在第 96 次之前只有 **2** 行（歌词投影仪、音箱）。
症状与投影仪同款：方块放下去**只有静态模型**，中控台的屏幕**永远不画**；
而它的 renderer 还兼着**两条每帧视频同步的调用点**（`ModernTurntableVideoClient` 的注释里写着），
⇒ 中控台的视频也一并受影响。

**现在上游那 5 行全部到位**：`MODERN_TURNTABLE` / `LYRIC_PROJECTOR` / `VIDEO_PROJECTOR` / `SPEAKER` / `CONTROL_CONSOLE`。

### 6.30.2 顺带：唱片机渲染器（`ModernTurntableRenderer`）是又一个“过期排除条目”

按 §3.79 的纪律**成对体检**（它 + 它依赖的 `NetMusicDiscModelAdapter`），唯一缺口是一处
1.21→1.20.1 的 API 差异（javac 原话）：

```
需要: PoseStack,VertexConsumer,int,int,float,float,float,float
找到: PoseStack,VertexConsumer,int,int,int
```

即 `Model#renderToBuffer` 在 1.21 收**一个打包 int**（`-1` = 不透明白），
1.20.1 收**四个 float**（r,g,b,a）。补成 `1.0F,1.0F,1.0F,1.0F`（与 `-1` 语义相同）后
**一次编过**，两个文件一起放回编译 ⇒ **排除清单 6 → 4**。

### 6.30.3 剩下 4 个条目的**逐个体检结论**（让 javac 说话，不再靠猜）

| 文件 | 结论 | 证据 |
| --- | --- | --- |
| `HolographicPreviewPipRenderer` | ❌ 仍缺 | `:53 找不到符号` |
| `TerrainPreviewGpuCache` | ❌ 仍缺 | `:18 找不到符号`；`:589 程序包 TerrainPreviewSectionCompiler 不存在`（**依赖同样被排除的那个**） |
| `TerrainPreviewSectionCompiler` | ❌ 仍缺 | `:150 找不到符号`；`:300 对于 vertex(float,float,float,int,float,float,int,int,float,float,float) 找不到合适的方法`（§3.71/§3.72 记过的顶点重载差异） |
| `HolographicGlassesWorldScreenRenderer` | ⚪ **可放回但无意义** | 0 错误 —— 但**上游也没有任何调用方**（死代码），放回行为不变，故留在清单里 |

### 6.30.4 同族“被摘语句”：本轮又补回 3 条，§6.27.5 清单**只剩 1 条**

| 上游语句 | 现状 |
| --- | --- |
| `MODERN_TURNTABLE → ModernTurntableRenderer::new` | ✅ 本轮补回（并把它从排除清单救回） |
| `CONTROL_CONSOLE → ControlConsoleRenderer::new` | ✅ 本轮补回 |
| `modEventBus.addListener(CuriosHeadGearLayer::register)` | ✅ 本轮补回（类一直在编译范围内，纯漏接线） |
| `MP4ItemScreenRenderer.warmup() / PadItemScreenRenderer.warmup()` | ✅ 本轮补回（懒初始化也能跑，属“补回上游行为”） |
| `YuvVideoRenderTypes::registerPipelines` | ✅ 第 96 次补回 |
| `PortPictureInPictureRenderer.registerPipRenderer(HolographicPreviewPipRenderState.class, HolographicPreviewPipRenderer::new)` | ⛔ **仍缺**（`HolographicPreviewPipRenderer` 还在排除清单里 ⇒ 等它修好再补） |

### 6.30.5 本轮产物

| 项 | 值 |
| --- | --- |
| 全量编译 | `clean compileJava` **0 错误 / 7 警告**（口径：排除 **4** 条） |
| jar | `net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar`，**9,309,966 B**，sha256 `C8B060A9…D3DBF15CA` |
| 部署 | 已复制到测试床 `mods/`，两侧哈希一致 |
| jar 内证据 | 常量池里有 5 个渲染器 + `CuriosHeadGearLayer`（判据要用 `javap -v`，`-c` 不打常量池 —— 我第一遍就用错了判据） |
| 门禁 | 重混淆 3/3、Mixin 接线 7/7、注册 23 条、组装 **181 条**、排除清单 **5/5**、模型 24 个 —— 全通过 |
| **待验** | 中控台屏幕（含它的视频）、唱片机渲染（碟片/正面显示） |

---

## 6.31 第 99 次：中控台通过 ✔；两条新回报各装一个探针（碟片 / 视频提前消失）

用户实测：**中控台屏幕 + 视频正常** ✔。新报两条：

### 6.31.1 「唱片机碟片未显示」—— 先把三种可能分清，再动手

静态侧**已排除**的（都不必再猜）：

* 碟片**存档 + 同步链完整**：`saveAdditional` 写 `"Disc"`、`load` 读回、
  `getUpdateTag() = saveWithoutMetadata()` ⇒ 客户端应当拿得到碟；
* `NetMusicDiscModelAdapter` / `DiscPlacementPolicy` / `DiscRotationPolicy`
  **与上游逐行一致**（唯一差异是我这次补的 `renderToBuffer` 颜色参数，以及
  `Math.clamp → Mth.clamp` 的 1.20.1 适配）；
* 方块模型**三树一致**：`modern_turntable.json` 28 个元素、`modern_turntable_playing.json` 20 个元素，
  移植 / 来源 0.7.9 / 权威 0.7.10 **完全相同** ⇒ 不是摊平出错；
* blockstate 的 `has_disc` 变体齐全（false → `modern_turntable`、true → `modern_turntable_playing`）。

⇒ 在 `ModernTurntableRenderer` 装**状态探针**（节流：载荷变化才打），三种情形一次分清：

```
[NCPB/diag] turntable {x,y,z} 渲染状态：hasDisc=… playing=… facing=… 方块态hasDisc=… 碟物品=… 歌词行=…
```

① 探针**不出现** ⇒ 渲染器没被调用；② `hasDisc=false` ⇒ 客户端同步问题；
③ `hasDisc=true` 却看不见 ⇒ 摆放 / 贴图 / 被方块模型盖住。

### 6.31.2 「投影视频在视线未完全移开时就消失」—— 先分清是哪一道门翻脸

**已排除**：可见性阈值与上游**逐字一致**（`view_dot_threshold=0.12`、`occlusion_check=true`、
`visibility_sample_edge_scale=0.86`、`max_render_distance=64`）。

判定链本身是“**5 个采样点里只要有一个通过就算可见**”：
`isScreenInView(点积 > 阈值)` **且** `!isOccluded(射线不被挡)`。两条最可能的路线：

* **BER 路线**（`route=ber`）：可见性等于“BER 最近有没有提交过”（`submittedByBer`）。
  BER 只在**它的渲染包围盒进视锥**时被调用 ⇒ 盒一旦先于画面离开视锥，
  `renderable` 翻 false ⇒ `markVisibility(false)` ⇒ 暂停解码 ⇒ **视频消失**；
* **视线路线**（`route=view`）：`dotOk=false`（采样点先被判看不见）或 `occluded=true`（射线被挡）。

⇒ 在 `VideoPlaybackPresentation.submit` 里装**可见性探针**，每道子门都打出来：

```
[NCPB/diag] video_projector {x,y,z} 可见性：route=ber|view berManaged=… submittedByBer=…
            renderable=… prewarm=… distOk=… dotOk=… occluded=… dotThreshold=…
```

> 参考：1.21.1 那次移植也遇到过同款症状（“仍在看屏幕却被判看不见 → 停画 + 计离屏”），
> 他们的因是**采样点在 plot 坐标**（结构场景）。本工程是地面场景，
> 所以要**用探针重新定因**，不要照搬那条结论。

### 6.31.3 本轮产物

| 项 | 值 |
| --- | --- |
| jar | `net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar`，**9,311,884 B**，sha256 `8D84F33E…87BAD75B` |
| 部署 | 已复制到测试床 `mods/`，两侧哈希一致 |
| 门禁 | 重混淆 3/3、Mixin 接线 7/7、注册 23 条、组装 **182 条**、排除清单 5/5、模型 24 个 —— 全通过 |
| **待验** | 上面两行探针日志（各自“发生的那一刻”） |

### 6.31.4 ★ 视频提前消失的根因（已在同一轮查出并修掉）：**解码存活被绑在"BER 这一帧提交了吗"上**

把那条链读透之后，机制是这样的（三处合起来才是因）：

| 环节 | 事实 |
| --- | --- |
| ① 谁是 BER 托管 | `attachProjectorToTurntable(...)` 会把**任何已绑定唱片机的投影仪**记进 `berManagedProjectorPositions` ⇒ 投影仪一绑定，`isProjectorRenderedByBer(pos)` 就为 **true** |
| ② 会话的存活判据 | `VideoBerConsumerVisibilityPolicy.usesBerSubmission(berManaged, submittedByBer)` 为 true 时，`renderable` **只**等于 `submittedByBer` |
| ③ 那个"最近"有多近 | `BER_SUBMITTED_PROJECTORS = new RecentFrameVisibility<>(1L)` —— 容忍窗口 **1 帧** |

而 BER 只在**它自己的渲染包围盒进视锥**时才会被调用（而且它的标记还要求
`isProjectorScreenPotentiallyVisible` 成立）⇒ **盒一旦先于画面离开视锥，②就在 1 帧内翻 false**
⇒ `markVisibility(false)` ⇒ 暂停解码（grace 500ms 后）⇒ 用户看到的是
“**视线还没完全移开，视频就没了**”，转回来还要重定位 + 加载图。

> 这条也解释了**为什么偏偏在注册 BER 之后才出现**：注册之前 `extract` 从不运行 ⇒
> 没有投影仪是 BER 托管的 ⇒ 走的是 `view` 路线（5 个采样点 + 点积 0.12，宽松得多）。
> **我注册 BER 把路线从 `view` 切到了 `ber`**，于是把这条脆弱的链暴露出来了。

**修法**（只改"要不要继续解码"，不改变"谁来画"）：

```java
boolean viewFallback = !submittedByBer && projectorOrNull != null
   && VideoBillboardPreview.isProjectorScreenRenderable(minecraft, camera, projectorOrNull, viewDotThreshold());
boolean live = submittedByBer || viewFallback;   // 兜底：BER 这一帧没提交 ≠ 屏幕不可见
renderable |= live;
// 注意：**不**把 projectorOrNull 加进 renderableProjectors —— 该投影仪仍由 BER 提交，避免重复绘制
```

配套的**可见性探针**保留（`route/berManaged/submittedByBer/renderable/distOk/dotOk/occluded/dotThreshold`），
下一轮若还有异常，一眼能看出是哪一道门。

| 项 | 值 |
| --- | --- |
| 本轮第二个 jar | `…-0.7.9-beta+forge1.20.1.jar`，**9,311,954 B**，sha256 `5B58F3EA…C6B16DFD`，已部署（哈希一致） |
| 门禁 | 重混淆 3/3、Mixin 7/7、注册 23 条、组装 182 条、排除清单 5/5 —— 全通过 |

### 6.31.5 ★★ 用户第二条线索把真因锁死：**1.20.1 的"截面剔除"**

用户补了一句关键观察：**“只要视野中没有视频投影仪这个方块，视频就会消失”** ——
不是"屏幕不在视野"，而是"**方块**不在视野"。这一句直接指向 BE 的调度方式。

`javap -c net.minecraft.client.renderer.LevelRenderer`（偏移 1551–1598，`renderLevel` 内）实证：

```
getfield globalBlockEntities → Set.iterator() → hasNext/next
  → checkcast BlockEntity
  → BlockEntity.getRenderBoundingBox()      ← 用的是 **BE 自己的**包围盒
  → Frustum.isVisible(AABB)
  → getBlockPos() → pushPose → translate → ……渲染
```

也就是说：

| BE 类型 | 1.20.1 的可见性口径 |
| --- | --- |
| **全局**（`shouldRenderOffScreen()==true`，进 `globalBlockEntities`） | 每帧都被遍历，再用 `BE.getRenderBoundingBox()` 做视锥判定 |
| **非全局**（默认） | **按"它所在的 16³ 截面有没有进视锥"被遍历** —— BE 自己的包围盒**根本不参与** |

⇒ 投影幕虽然画在方块**外面几格**、此时明明还看得见，但只要**方块所在截面**离开视锥，
BER 就**不会被调用**，投影幕随之消失（用户看到的正是这个）。
上游（1.21 / 26.x）没有这个问题：那边的 BE 路径用 AABB 判可见性，所以它只写了一个大包围盒就够了。

**修法**（与 6.31.4 互补，两处合起来才是完整答案）：

```java
boolean viewFallback = !submittedByBer && projectorOrNull != null
   && isProjectorScreenRenderable(minecraft, camera, projectorOrNull, viewDotThreshold());
boolean live = submittedByBer || viewFallback;   // ① 存活：不再被 1 帧的 BER 心跳绑死
renderable |= live;
if (viewFallback) {
   renderableProjectors.add(projectorOrNull);    // ② 绘制：BER 这一帧没画 ⇒ 会话补画一次
}
```

② 是**互补**的：只在 `!submittedByBer` 时才补 ⇒ **不会与 BER 重复绘制**（同一帧二者必居其一），
而且会话那条路径本来就是上游支持的正常路线（`submitProjectorGeometry(...)` 自己按投影参数算世界坐标姿态，
不依赖 BER 的 pose）。

| 项 | 值 |
| --- | --- |
| 本轮第三个 jar | `…-0.7.9-beta+forge1.20.1.jar`，**9,311,962 B**，sha256 `84B40820…0FFC05A98`，已部署（哈希一致） |
| 门禁 | 重混淆 3/3、Mixin 7/7、注册 23 条、组装 182 条、排除清单 5/5、模型 24 个 —— 全通过 |
| **待验** | 转动视角让**方块离开视野但屏幕仍在画面内**：画面应保持，且不再有加载图；探针会打 `route=ber … submittedByBer=false … renderable=true` |

### 6.31.6 ★ 用户实测"还是消失" ⇒ 探针把锅交给了**第三个**原因：会话那条路的批次垫片

用户回报：**碟片成功渲染 ✔**，但"方块离开画面、屏幕仍在画面里"时**画面仍然消失**。
再读探针（这次探针正是为它准备的）：

```
可见性：route=ber berManaged=true submittedByBer=false renderable=true prewarm=true distOk=true dotOk=true occluded=false
绘制：yuv=true yuvTex=true/true/true yuvShader=true drawn=true      ← 这是 **BER** 的探针
```

⇒ ① 我的补画兜底**确实在生效**（`renderable=true`、`dotOk=true`、`occluded=false`
⇒ `viewFallback=true` ⇒ 投影仪被加进 `renderableProjectors`）；
② 会话那条路的几何门也只有"世界锚 + 64 格"，**不含视线** ⇒ 它应当提交几何。

**于是只剩一个嫌疑**：会话用的**批次垫片** `PortWorldRenderEvents`。它的 `Scratch` 写的是

```java
final BufferSource source = MultiBufferSource.immediate(this.builder);   // ← 1.21 写法的直译
```

而 1.20.1 的 `immediate(...)` 传进去的 `fixedBuffers` 是**空表** ⇒ **所有 RenderType 共用同一个 `BufferBuilder`**
—— 这正是 **§6.23 续 2** 在 GUI 上抓到过的同一个坑（当时的症状是"整块屏全黑"）。
**BER 用的是原版按类型分配的 `bufferSource`，所以 BER 画得出来；会话这条世界阶段的路用它 ⇒ 提交了也画不出来。**
这也解释了为什么"投影仪的画面一直是 BER 独家提供" —— 会话那条路从第 23 次起就是**静默死**的。

**修法**：`PortWorldRenderEvents.Scratch` 改用**按 RenderType 分配缓冲**的 `PortGuiBufferSource(false)`
（它内部就是"每类型一个 `BufferBuilder` + `endBatch()` 时逐个 `RenderType.end(...)`"），
并给 `PortGuiBufferSource` 加一个 `diag` 开关（世界侧不开诊断，免得每帧刷日志）。

> 教训：**修好一个坑之后要 grep 同一模式的所有出现**（见 L40）—— 同一段 `immediate(builder)`
> 在工程里有**两处**，第 23 次只修了 GUI 那处。

**L40 当场又抓到第三处**：全工程 grep `MultiBufferSource.immediate(` 得到
`port/shim/PortPictureInPictureRenderer.java:27` 也是同一写法
（上游写的是 `immediate(new ByteBufferBuilder(4096))` —— 1.21 那边天然按类型分配，所以上游无罪）。
它服务于"全息眼镜的 PiP 预览"，当前那条路还没启用（`HolographicPreviewPipRenderer` 仍在排除清单里），
属于**潜伏**的同类缺陷 ⇒ 一并改成本工程的按类型实现。
改完复查：**真代码里的 `MultiBufferSource.immediate(` 出现次数 = 0** ✔

| 项 | 值 |
| --- | --- |
| 本轮第四个 jar | `…-0.7.9-beta+forge1.20.1.jar`，**9,311,890 B**，sha256 `EC9D5BD9…6830F9FC4`，已部署（哈希一致） |
| 门禁 | 重混淆 3/3、Mixin 7/7、注册 23 条、组装 182 条 —— 全通过 |
| **待验** | 同上：方块离开视野而屏幕仍在画面内时，画面应保持（这次会话那条路真的能画了） |

**本轮最终 jar**（含 PiP 那处一起修）：`…-0.7.9-beta+forge1.20.1.jar`，**9,311,818 B**，
sha256 `E6C82A13…BE174494F`，已部署（哈希一致）；门禁：重混淆 3/3、Mixin 7/7、注册 23 条、
组装 182 条、排除清单 5/5、模型 24 个 —— 全通过。

### 6.31.7 ★ 采纳用户建议（"把视锥外限制取消"）—— 让投影仪成为**全局方块实体**

会话那条路为什么还是没画出来，暂未查明；但用户提的方向是**官方机制**，而且比继续追会话更直接：

`javap -c ChunkRenderDispatcher$RenderChunk$RebuildTask` 实证（1.20.1 的 BE 列表构建处）：

```
renderer = dispatcher.getRenderer(be)
if (renderer != null && renderer.shouldRenderOffScreen(be)) → CompileResults.globalBlockEntities
else                                                        → CompileResults.blockEntities（随 16³ 截面走）
```

而 `LevelRenderer.renderLevel` 里全局那类是：

```
getfield globalBlockEntities → iterator → BlockEntity.getRenderBoundingBox()
  → Frustum.isVisible(AABB) → 渲染
```

`getRenderBoundingBox` 是 **Forge 的 `IForgeBlockEntity` 默认方法**（默认"1 格"；
`SignBlockEntity` 就是靠覆盖它来支持超出方块的渲染），所以两处覆盖即可：

| 位置 | 改动 |
| --- | --- |
| `VideoProjectorRenderer.shouldRenderOffScreen(be)` | → `true`（进全局列表） |
| `VideoProjectorBlockEntity.getRenderBoundingBox()` | → **紧贴投影幕**的保守 AABB（屏幕中心 ± 右/上方向 ×半宽/半高 的各轴绝对值投影 + 0.5 格余量；纯数学、不引用客户端类） |
| `VideoProjectorRenderer.render(...)` | 补回**距离门**（全局 BE 引擎不再按距离筛 ⇒ 自己保留 64 格，免得远处也画） |

⇒ 投影幕的绘制**不再依赖"方块所在截面是否在视锥里"**，而只依赖**投影幕自己的包围盒** ——
这正是 1.21 那边天然的语义（那边 BE 用 AABB 判可见），也是"照搬上游写法在 1.20.1 不够"的最后一处补齐。
jar 内已确认两个覆盖都在（`javap` 可见 `shouldRenderOffScreen` 与 BE 的 `getRenderBoundingBox()`）。

**同类待办（未做，等确认）**：歌词投影仪的字幕面片同样浮在方块外，理论上同一个缺陷；
它现在能用只是因为还没被"方块离开视野"测到。若需要，照这两处一起改即可。

| 项 | 值 |
| --- | --- |
| 本轮第五个 jar | `…-0.7.9-beta+forge1.20.1.jar`，**9,312,702 B**，sha256 `A10AA716…ADE8F305`，已部署（哈希一致） |
| 门禁 | 重混淆 3/3、Mixin 7/7、注册 23 条、组装 182 条 —— 全通过 |
| **待验** | 方块离开视野而投影幕仍在画面内 ⇒ 画面应保持（这次连"BER 被截面带走"这条路都断了） |

---

## 6.32 第 100 次：对照**另一份已实机验证的 1.20.1 移植**（GitHub）—— 印证 + 采纳

用户给了 <https://github.com/ChihayaAnonQWQ/NetMusicCanPlayBili-1.20.1>：
MIT、**fork 自同一条 1.21.1 基线**（`wrdqtww/NetMusicCanPlayBili-1.21.1`），今天刚推过，
README 是一份完整的移植报告，声称**全链路实机验证通过**（含"视频投影仪画面 ✅""中控台界面 ✅"）。

### 6.32.1 它**独立印证**了我们最近几轮的结论（三处，都是他们写进代码/注释的）

| 我们这轮的修法 | 他们的对应物 |
| --- | --- |
| §6.28 着色器：删掉 `#moj_import <minecraft:dynamictransforms.glsl>`，改为**直接声明** `uniform vec4 ColorModulator;` | 他们的 `yuv420p_entity.fsh` **一个 `#moj_import` 都没有**，直接在文件里 `uniform vec4 ColorModulator;` ⇒ 与我们修好的版本**逐行同类** |
| §6.31.7 投影幕裁剪盒挂在 **BE** 上 | 他们的 `VideoProjectorBlockEntity#getRenderBoundingBox()` → `BlockEntityRenderBounds.videoProjector(...)`，注释明写"1.20.1 的钩子在 `IForgeBlockEntity#getRenderBoundingBox()`——挂在 BlockEntity 上；BER 里那份已不是覆写" |
| 顶点收尾 `endVertex()`、`MeshData`/`ByteBufferBuilder`→`BufferBuilder.RenderedBuffer`、`VertexBuffer.upload` 自释放等 | README 第 4 节逐条列出，与我们已经落地的映射一致 |

### 6.32.2 采纳其一：把"渲染裁剪包围盒"抽成**公共侧唯一实现**（并给 BE 接线）

他们的 `BlockEntityRenderBounds` 放在 `blockentity` 包（**服务端安全、无 client 依赖**），
BE 用默认宽高比、BER 传**真实帧宽高比**，且**四个方块实体都接上了**。本轮照做：

* 新增 `blockentity/BlockEntityRenderBounds.java`：`videoProjector(...)`（含带 `aspect` 的重载）、
  `projectorScreen(...)`（与 `ProjectorScreenGeometry.aroundCenter` 逐行等价）、`turntable(...)`、
  `lyricProjector(...)`、`distanceToSqr(...)`、`clampVideoAspect(...)`、`videoMargin/videoMaxAspect()`；
* `VideoProjectorBlockEntity` / `LyricProjectorBlockEntity` / `ModernTurntableBlockEntity` 三处**BE 侧接线**；
* `VideoProjectorRenderer` 改用同一份实现并传入**真实帧宽高比**（`shouldRender` 的距离口径也共用）；
* ⇒ 我上一轮在 BE 里**复制**的那段几何删掉了（L40："一份实现"），
  并且**顺手关掉了上轮登记的"歌词投影仪同一缺陷"**（字幕面片也浮在方块外）。

### 6.32.3 一条他们提到、我们**已经有**的东西

他们的 README 警告：MinecartRevolution 的模拟 level 会在 `setBlock` 时重建 BE ⇒ 若在渲染器里同步
`ACTIVATED` 会每帧重建 BE、打断视频会话。他们的做法是 `level != Minecraft.getInstance().level` 直接返回。
**我们的 `VideoProjectorRenderer#syncActivatedState` 本来就有这条守卫** ✔（`level == Minecraft.getInstance().level` 才继续），无需改。

### 6.32.4 登记下来、暂未做的（来自他们的报告，逐条对我们有用）

| 项 | 价值 |
| --- | --- |
| 用 **`@Accessor` 混入**替代 `@Shadow` 访问**原版成员**（他们有 `ScreenAccessor` / `LibraryAccessor` / `SoundManagerAccessor` / …） | 我们的 5 条 `@Shadow` 警告都在 **netmusic 的类**上（debug.log 实证这些混入**已成功应用**：`Mixing CDBurnerMenuScreenMixin …`），所以目前不是活缺陷；但作为健壮性升级值得做 |
| 加一条 **mixin 配置完整性 / refmap 覆盖门禁**（他们的 `check_refmap_coverage.py`） | 我们的 `check_mixin_wiring.ps1` 只查"配置文件在 + refmap 非空"，**查不出"某个 mixin 类没被登记"**（他们的战绩：15+ 个混入被静默禁用）。我们的清单经复核是 **15/16**，唯一未登记的 `MinecartRevolutionProjectorLinkCleanupMixin` 是**必需前置缺失时不该登记**的兼容类 ⇒ 目前无缺陷，但门禁缺口是真的 |
| 资产保留**父链**（他们的 `models/block/video_projector.json` 只有 208 B） | 我们的流水线会**摊平**父链，台账里已记过"投影仪 24 个重复元素""父节点 `ncpb_euler_elements` 被摊平丢掉"；长期应改 |
| `OffscreenGuiRenderTargetContext`（线程局部 RenderTarget 覆盖） | 他们离屏 GUI 的另一种做法：让嵌套助手能查"当前活动目标"，而不是靠我们那种**显式排序**。可作后续选项 |

| 项 | 值 |
| --- | --- |
| 本轮第六个 jar | `…-0.7.9-beta+forge1.20.1.jar`，**9,315,076 B**，sha256 `EF2F5478…CBBF5EDBF6`，已部署（哈希一致） |
| jar 内证据 | 三个 BE 都有 `getRenderBoundingBox` ✔；`BlockEntityRenderBounds` 6 个方法/重载在制品里 |
| 门禁 | 重混淆 3/3、Mixin 7/7、注册 23 条、组装 **183 条**、排除清单 5/5 —— 全通过 |

---

## 6.33 第 101 次：与**官方上游 1.21.1 构建**逐项对照（`0.7.9-beta.3+neo1.21.1.jar`）

用户给了本机路径 `C:\Users\Administrator\Downloads\net_music_can_play_bili-0.7.9-beta.3+neo1.21.1.jar`
（vendor `zhongbai233`，1626 条目 / 1378 个类）。它是**我们移植来源那条线的正式构建**，所以既做保真度标尺，也当"上游怎么做"的答案。

### 6.33.1 三处**印证**（都是字节码级证据）

| 我们的修法 | 上游 1.21.1 实际 |
| --- | --- |
| §6.28 删 `#moj_import <minecraft:dynamictransforms.glsl>`，改直接声明 `uniform vec4 ColorModulator;` | 上游 `yuv420p_entity.fsh` **仍然**是 `#moj_import <minecraft:dynamictransforms.glsl>`（1.21 里存在）⇒ 我们那处是**正确的 1.20.1 翻译**，不是"上游写错" |
| §6.25/§6.26 离屏 GUI：先绑后画 + 逻辑尺寸正交投影（+ z 平移）；§6.28.2 撤红底 | 上游顺序：`backupProjectionMatrix → modelView.identity() → enableBlend → target.bindWrite(true) → enableScissor → clearColor(0,0,0,0) → clear → setOrtho(0, 448, 256, **-21000**, **21000**) → setProjectionMatrix → new GuiGraphics → drawGui → flush/endBatch`；**MP4 同样是先 bindWrite 再 drawGui** ⇒ 先绑后画 ✔、清屏透明 ✔、逻辑尺寸 ✔ 三处全部对得上；差别只在：上游用**对称 z 区间**，所以**不需要**那句平移 |
| §6.31.7/§6.32 裁剪盒挂到 **BE** | 上游把 `getRenderBoundingBox(T)` 覆写在**渲染器**上（1.21 Forge 钩子在 `IForgeBlockEntityRenderer`），且**没有** `shouldRenderOffScreen` ⇒ 1.20.1 钩子挪到 `IForgeBlockEntity`（BE）上，我们的迁移就是忠实翻译 |

### 6.33.2 真缺口只有 **12 个顶层类**（其余 50 个是 NeoForge StreamCodec 的 `$N` 匿名类，我们手写 `SimpleChannel` 天然没有）

| 组 | 类 | 性质 |
| --- | --- | --- |
| **音频内部桥 + 对应 Accessor 混入** | `bridge/{Library,SoundManager,SoundEngine}Bridge` + `mixin/{Library,SoundManager,SoundEngine}Accessor` | ⚠️ **真功能缺口**：上游用 Mixin `@Implements` 的鸭子接口深入 OpenAL/声音引擎（`net_music_can_play_bili$currentDevice/context`、`$soundEngine`、`$loaded/library`）⇒ 支撑"按设备/位置的音频输出"（1.21.1 的 R9/R10/R11）。**我们的移植把三个 bridge 删了**（组装清单 `[deleted]` 里就有），对应 `*LivePositionMixin`/`*AudioElementPositionMixin` 仍在 `_deferred-mixins` ⇒ **音频定位精度降级** |
| **全息/地形预览子系统** | `HolographicPreviewPipRenderer`、`TerrainPreviewGpuCache`、`TerrainPreviewSectionCompiler`、`HolographicGlassesWorldScreenRenderer` | 就是 `deferred-excludes.txt` 里那 4 条（3 条编不过；第 4 条能编但上游的调用方在我们树里也被摘了）⇒ 已登记 |
| 混入模式 | `mixin/ScreenAccessor` | 上游用 Accessor 取 `Screen` 字段；我们用 `@Shadow`（都在 netmusic 类上，debug.log 实证能应用）⇒ 健壮性升级项 |
| 架构差异（**非缺口**） | `network/ModernTurntableNetwork` | 上游它是 `register(RegisterPayloadHandlersEvent)` 入口；我们由 `NcpbNetwork`/`NcpbNetworkDispatch` 承担 |

> 我们这边**多出 115 个顶层类**（`Port*` 垫片、`BlockEntityRenderBounds`、`ncpbmodel/*` 自订欧拉 loader、
> `compat/sable/*`、`media/local/*` 等）⇒ 路线差异：**我们"自建垫片 + 手写网络 + 摊平资产"，上游"NeoForge 原语直用"**。

### 6.33.3 可采纳但**不动**的一条

把离屏 GUI 改成上游那种 `setOrtho(0, W, H, -21000, 21000)` 并去掉模型视图平移 —— 行为等价、少两处魔法数字。
但 Pad/MP4 已被用户实测通过 ⇒ **不碰已验证能用的东西**，仅登记备选。

### 6.33.4 给"验证"装上**心跳**（本轮代码改动）

已有探针只在**载荷变化**时打印，而"方块离开视野后画面消失"恰好**不改变载荷** ⇒ 加 `UseDiag.heartbeat(tag, 5000ms, payload)`：
每 5 秒打一行"**累计调用 / 本周期调用**"。两个投影仪的 `render(...)` **第一行**（连距离门之前）各调一次：

```
[NCPB/diag] video_projector BER 心跳：累计调用=… 次，本周期=… 次 pos=(…)
[NCPB/diag] lyric_projector BER 心跳：累计调用=… 次，本周期=… 次 pos=(…)
```

判据：**方块不在视野时这两行仍必须每 5 秒出现，且"本周期次数 ≈ 帧率 × 5"** —— 那就证明引擎每帧仍在调用
投影仪渲染器（这正是全局 BE 那条改动的目的）。

| 项 | 值 |
| --- | --- |
| 本轮 jar | `…-0.7.9-beta+forge1.20.1.jar`，**9,315,860 B**，sha256 `18A37C27…FC5B02DD`，已部署（哈希一致） |
| jar 内证据 | `UseDiag.heartbeat(String,long,String)` 在制品里 ✔ |
| 门禁 | 重混淆 3/3、Mixin 7/7、注册 23 条、组装 183 条、排除清单 5/5 —— 全通过 |

---

## 6.34 第 102 次：用户报的两个新问题（中控台编辑界面消失 / 光影不兼容）

### 6.34.1 中控台"右键进入后编辑界面消失" —— **同一个裁剪机制的第四个实例**

`ControlConsoleRenderer#getRenderBoundingBox(console)` 一直都在（方块 ∪ 所有已启用元素），
但 **`ControlConsoleBlockEntity` 没有覆写** `IForgeBlockEntity#getRenderBoundingBox()`
⇒ 1.20.1 里它按"方块所在 16³ 截面是否在视锥内"被遍历；**进入编辑/漫游模式时相机一变，
方块先出视锥，面板整块消失**（`ControlConsoleRenderer#shouldRender` 恰好是 `return true`，
所以它原本完全没有距离/视线门）。

修法与视频投影仪同构（见 §6.31.7、L41）：

| 位置 | 改动 |
| --- | --- |
| `BlockEntityRenderBounds` | 新增 `controlConsole(BlockPos, ControlConsoleDocument)`（与 `ControlConsoleRenderer#getRenderBoundingBox` **逐行等价**） |
| `ControlConsoleBlockEntity` | 覆写 `getRenderBoundingBox()` → 上面的助手 |
| `ControlConsoleRenderer` | `shouldRenderOffScreen(be) → true` |

> 不需要补距离门：它的 `shouldRender` 本来恒 `true`，而全局列表仍受**视锥**约束
> （`LevelRenderer` 用 BE 自己的包围盒测），且全局列表里的 BE 只来自**已加载区块**。

### 6.34.2 光影不兼容（"开启光影后画面消失"）—— **生态位差异：光影是 Oculus，不是 Iris**

**先取证**：测试床装的是 `oculus-mc1.20.1-1.8.0.jar`（+ `embeddium-0.3.31`），
日志里满是 `rule 'mixin.features...' (added by mods [oculus])`。而代码写的是：

```java
if (!ModList.get().isLoaded("iris")) { available = false; return; }   // ← 只认 iris
```

⇒ `isShaderPackInUse()` **恒为 false** ⇒ 开着光影还以为没开 ⇒ 继续走"自订 YUV 核心着色器"
⇒ **画面消失**（不报错、编译期也看不出）。

**Oculus 提供的是同一套 API**（`javap` 直接读测试床里的 Oculus 制品实证）：

```
net/irisshaders/iris/api/v0/IrisApi.class
  public static IrisApi getInstance();
  public abstract boolean isShaderPackInUse();      ← 注意：Oculus 1.8.0 **没有** isShaderPackActive
```

**修法**：判据从"mod id 是不是 `iris`"改成"**能不能拿到 API 类**"（真正决定"能不能问"），
mod id 只用于日志说明，已知提供者列全 `{"iris","oculus"}`；启动时打一行
`已启用光影（shaderpack）兼容检测：提供者=oculus（可用属性：ncpb.video.iris.disable_yuv_shader / ncpb.video.iris.yuv_mode）`。
（`detectShaderPackInUse()` 的 `isShaderPackActive → isShaderPackInUse` 回退顺序本来就兜住了 Oculus ✔）

| 项 | 值 |
| --- | --- |
| 本轮 jar | `…-0.7.9-beta+forge1.20.1.jar`，**9,317,819 B**，sha256 `57A1C788…F844F55792`，已部署（哈希一致） |
| jar 内证据 | `ControlConsoleBlockEntity.getRenderBoundingBox()` ✔、`ControlConsoleRenderer.shouldRenderOffScreen(…)` ✔、`IrisShaderpackCompat` 里含 `oculus` 字面量 ✔ |
| 门禁 | 重混淆 3/3、Mixin 7/7、注册 23 条、组装 **184 条**、排除清单 5/5 —— 全通过 |
| 教训 | **L41**（1.20.1 的裁剪钩子在 BE 上）+ **L42**（光影 mod id 是 `oculus`）已收录，索引刷至 L42 |
| **待验** | ① 右键进中控台编辑模式：面板应保持可见（日志有 `中控台 …` 相关行）；② **开光影**（Oculus + 任一致力包）：日志必须先出现 `已启用光影（shaderpack）兼容检测：提供者=oculus`，随后画面应恢复 |

### 6.35 ★ 第 103 次：截图定位"编辑器视口全黑" —— 把**排除清单里的画中画预览整层**接回来

用户截图（中控台编辑器：左侧元素层级、右侧检查器都在，**中间视口全黑**）说明缺的不是方块裁剪，
而是**视口渲染层**。取证链：

1. `HolographicEditorRenderScreen` 里那段自带的注释写着"**画面渲染层（画中画预览）整层延后** …
   `pipState` 因此暂时不再被消费，这是本次登记的**行为差异**（预览不渲染）" ⇒ 症状吻合；
2. 全工程 grep：**`registerPipRenderer` 没有任何调用点** ⇒ `PortPictureInPictureRenderer` 的工厂表为空
   ⇒ `createPipRenderer(...)` **恒返回 null**；而上游在 `ModernTurntableClientEvents:42` 就有这一行；
3. 排除清单里正是这一层：`HolographicPreviewPipRenderer`、`TerrainPreviewGpuCache`、
   `TerrainPreviewSectionCompiler`（第 4 个 `HolographicGlassesWorldScreenRenderer` 与视口无关）。

**做法**：`deferred_cut.py remove` 三个类 → 全量构建取第一手错误 → **13 条全部是 1.21→1.20.1 的
网格/缓冲 API 差异**，逐条按 1.20.1 的真实签名改（对照表见 **L43**）：

| 1.21 | 1.20.1 落地 |
| --- | --- |
| `builder.build()` → `MeshData` | 垫片 `MeshData.of(builder)`（只记 builder，**不** build） |
| `MeshData` → `VertexBuffer.upload(mesh)` | `BufferBuilder#endOrDiscardIfEmpty()` → `RenderedBuffer` → `VertexBuffer#upload(RenderedBuffer)`（空层返 null 并跳过该层） |
| `MeshData.SortState` / `uploadIndexBuffer` | 1.20.1 **不存在** ⇒ `resort(...)` 改为只记 `lastSortModelView`（半透明按缓冲顺序绘制，**登记为行为差异**） |
| `new BufferBuilder(bytes, Mode.QUADS, layer.format)` | `new BufferBuilder(262144)` + `begin(Mode.QUADS, DefaultVertexFormat.BLOCK)`（`RenderType#format` 在 1.20.1 是 **private**） |
| `vertex(11 参)` | 链式 `vertex().color().uv().overlayCoords().uv2().normal().endVertex()` |
| `poseStack.mulPose(Matrix4f)` ×4 | `mulPoseMatrix(Matrix4f)`（1.20.1 的等价物） |
| `Lighting.setupLevel()` | `Lighting.setupLevel(poseStack.last().pose())`（照原版 `LevelRenderer`） |
| `shader.setDefaultUniforms(Mode, ModelViewMat, ProjMat, Window)` | `safeGetUniform("ModelViewMat"/"ProjMat"/"ColorModulator").set(...)` + `apply()` |
| 1.21 命名的 `TranslatedVertexConsumer`（`addVertex/setColor/…`） | 补齐 **1.20.1 接口面**（含 Forge 的 `unsetDefaultColor`） |

然后照上游回填两处接线：`ModernTurntableClientEvents` 里的
`PortPictureInPictureRenderer.registerPipRenderer(HolographicPreviewPipRenderState.class, HolographicPreviewPipRenderer::new)`，
以及 `HolographicEditorRenderScreen` 里那三行（`acquire() → render(pipState) → PortGuiFramebufferBlitter.blit(...)`）。

| 项 | 值 |
| --- | --- |
| 全量口径 | `clean compileJava` **错误 13 → 0**、警告 7；**排除清单 4 → 1 条**（只剩 `HolographicGlassesWorldScreenRenderer`，与编辑器视口无关） |
| 本轮 jar | `…-0.7.9-beta+forge1.20.1.jar`，**9,370,282 B**，sha256 `6263C32C…CB4275913`，已部署（哈希一致） |
| jar 内证据 | `HolographicPreviewPipRenderer` / `TerrainPreviewGpuCache` / `TerrainPreviewSectionCompiler` 三个类都在制品里 ✔ |
| 门禁 | 重混淆 3/3、Mixin 7/7、注册 23 条、组装 184 条、排除清单 **G5 PASS（生效 1 条 == 清单 1 条）** —— 全通过 |
| 教训 | **L43**（1.21→1.20.1 网格管道对照表 + "整层延后要连同症状登记"）已收录，索引刷至 L43 |
| **待验** | 打开中控台编辑器：**中间视口应出现画面**（画中画预览）；若仍全黑，请看日志里有没有 `TerrainPreview` 相关的失败行（该类自带诊断） |

### 6.36 ★★ 第 104 次：全息眼镜收尾 —— **排除清单归零**，并修正一条我自己的错误结论

**排除清单从 1 条降到 0 条**：最后一条是 `client/renderer/item/HolographicGlassesWorldScreenRenderer.java`。

#### 6.36.1 我之前的判断是错的：它**不是**死代码

上一轮（§6.33.2）我把它记成"编译过但上游是死代码"，依据是**扫描官方制品的常量池**——
结果只有它自己的内部类（`$ScreenQuad`）引用它，grep 上游源码也只有自引用。**这个判据不成立**：

```java
@EventBusSubscriber(modid = "net_music_can_play_bili", value = {Dist.CLIENT})   // ← 注册全靠注解
public final class HolographicGlassesWorldScreenRenderer {
   private static final boolean ENABLED = ClientDisplayProperties.holographicWorldScreenEnabled();
   public static void onSubmitCustomGeometry(RenderLevelStageEvent event) {
      if (ENABLED && PortWorldRenderEvents.isStage(event, Stage.AFTER_TRANSLUCENT_BLOCKS)) { ... }
   }
}
```

* **注解注册不产生字节码引用** ⇒ 常量池扫描/调用点 grep **都看不见它的"调用者"**（调用者是事件总线）；
* 它的开关 `ncpb.holographic_glasses.screen.enabled` **默认 true** ⇒ 摘掉它等于**静默关掉一个默认开启的功能**；
* 我们这份的注解已正确移植为 Forge 版（`@EventBusSubscriber(modid=…, value={Dist.CLIENT})`，
  `RenderLevelStageEvent` 属 **FORGE** 总线，Forge 的 `@EventBusSubscriber` 默认就是 FORGE 总线 ✔）。

⇒ 因此这条恢复是**功能性恢复**（全息眼镜的**世界屏**绘制），不是"补制品完整性"。

#### 6.36.2 全息眼镜整块的接线核对（全部在位）

| 环节 | 状态 |
| --- | --- |
| 物品 `HolographicGlassesItem`（`ModItems.HOLOGRAPHIC_GLASSES`） | ✔ 已注册 |
| 头饰层 `CuriosHeadGearLayer::register` | ✔ `ModernTurntableClientEvents:41` |
| 按键 `HolographicGlassesKeyHandler::register` | ✔ 同文件 `:52` |
| **画中画预览工厂** `PortPictureInPictureRenderer.registerPipRenderer(...)` | ✔ `:45`（第 102 次补回） |
| 配置包 `HolographicGlassesConfigPacket` → 服务端 | ✔ `NcpbNetwork:78`（PLAY_TO_SERVER），发送点 `HolographicEditorLifecycleScreen:458` |
| **世界屏** `HolographicGlassesWorldScreenRenderer`（本轮） | ✔ 回到编译范围，注解即注册 |
| 隐私遮罩 `HolographicPrivacyOverlay` + `HolographicGlassesClient.shouldHideProjectorVideos()` | ✔ 投影仪/中控台/会话三处都在用 |
| 会话侧"眼镜消费者" `hasHolographicTurntableConsumer` / `handlesTurntable` | ✔ 在 `VideoBillboardSessionSupport` / `ModernTurntableVideoClient` / `LiveStreamerVideoClient` 里在用 |

#### 6.36.3 与官方制品的类集对齐（收尾后的账）

仍缺 **8 个顶层类**，其中只有 **1 组是真功能缺口**：

| 类别 | 类 | 说明 |
| --- | --- | --- |
| **音频内部桥 + Accessor 混入**（真缺口） | `bridge/{Library,SoundEngine,SoundManager}Bridge` + `mixin/{Library,SoundEngine,SoundManager}Accessor` | 支撑"按设备/位置的音频输出"（上游用 Mixin `@Implements` 的鸭子接口深入 OpenAL）。我们这三对是删掉的，对应的 `*LivePositionMixin` 等仍在 `_deferred-mixins` ⇒ **下一个可做的里程碑** |
| 混入模式差异 | `mixin/ScreenAccessor` | 上游用 Accessor 取 `Screen` 字段；我们用 `@Shadow`（都在 netmusic 类上，实测能应用） |
| 架构差异（**非缺口**） | `network/ModernTurntableNetwork` | 上游的 payload 注册入口；我们由 `NcpbNetwork`/`NcpbNetworkDispatch` 承担 |

| 项 | 值 |
| --- | --- |
| 全量口径 | `clean compileJava` **错误 0 / 警告 7**；**排除清单 4 → 1 → 0 条** |
| 本轮 jar | `…-0.7.9-beta+forge1.20.1.jar`，**9,378,060 B**，sha256 `E092FC9F…29EEFF0DC2`，已部署（哈希一致） |
| 门禁 | 重混淆 3/3、Mixin 7/7、注册 23 条、组装 184 条、**排除清单 G5 PASS（生效 0 条 == 清单 0 条）** —— 全通过 |
| 教训 | **L44**（注解注册的处理器在字节码里没有引用者 ⇒ "没人引用"≠"没人调用"）已收录，索引刷至 L44 |

---

## 6.37 ★★ 第 105 次：**测试床里躺着两个自己的 jar** —— "眼镜没画面"的真凶，以及"音频桥"这条缺口的更正

用户报"全息眼镜没画面"，并要求接着做"音频内部桥 + 3 个 Accessor 混入"。两件事查下去，
**两件都不是我原先以为的那样**。

### 6.37.1 眼镜没画面：`mods/` 里有两个同 modId 的 jar，游戏加载的是**旧的那个**

```
net_music_can_play_bili-0.7.9+forge1.20.1.jar        9,370,282 B  sha256 6263C32C…  ← §6.35 那次构建（旧）
net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar   9,378,060 B  sha256 E092FC9F…  ← 本轮部署（新）
```

* 两份的 **modId 与 version 完全相同**（都是 `net_music_can_play_bili` / `0.7.9-beta+forge1.20.1`），
  只有**文件名**差一个 `-beta`；旧那份**不含** `HolographicGlassesWorldScreenRenderer`
  （那个类是第 104 次才恢复的）⇒ 眼镜世界屏当然不画；
* 名字排序上旧的那份在前（`0.7.9+` 的 `+` < `0.7.9-` 的 `-`）⇒ 极可能就是被加载的那份；
* **这解释了症状，也解释了前几轮偶发的"改了没效果"**：排查方向被引到代码上，而问题在测试床。

**处置**：按哈希确认后删除旧 jar（`mods/` 恢复为：我们最新 + Embeddium + Oculus + NetMusic）。

### 6.37.2 新增门禁 `tools/check_mods_dir.py`：**同一 modId 只能出现一次**

"装了几份我自己"必须是一条**能说不**的判据：

* 扫 `mods/` 下所有 jar，从 `META-INF/mods.toml` 的 `[[mods]]` 段读 modId
  （**只取第一个 `[[dependencies` 之前**的，后面都是依赖声明）；
* 任何 modId 出现在 ≥2 个 jar ⇒ 失败（打印各自的版本/大小/sha256 前 12 位）；目标 mod 必须**恰好 1 份**；
* 顺带打印目标 mod 的 version 与 sha256 ⇒ 人工也能核对"装的是不是刚构建的那份"；
* `--self-test`：**正例通过 + 两个负例（重复 modId、目标缺失）必须失败** ⇒ 3/3 符合预期 ✔；
* 部署例程已改为：**先清掉 `net_music_can_play_bili-*.jar` 里所有非本次产物，再拷贝**。

### 6.37.3 "音频内部桥 + 3 个 Accessor 混入"：**不是缺口，是被更好的机制取代了**

查下去发现 §91 早有记录（`MinecraftOpenAlContext` 里那段注释）：

> 这里原来走 3 个 Mixin 访问器（`SoundManagerAccessor`/`SoundEngineAccessor`/`LibraryAccessor` + 对应桥接口），
> 而那几个 `@Shadow` 字段**在 refmap 里没有映射** ⇒ 运行期在 SRG 环境下找不到
> （`loaded`/`library`/`soundEngine`/`context`/`currentDevice` 实际叫 `f_120219_`/`f_120220_`/`f_120349_`/`f_83687_`/`f_193465_`），
> **Mixin 应用直接失败**。现在改用 **AccessTransformer** 把 5 个字段放开成 public，直接读 ——
> AT 在编译期与运行期都生效，**不依赖 refmap**，也就不需要 mixin 了（4 个 mixin 与 3 个桥接口一并删除）。

**实证**：`accesstransformer.cfg:27-31` 确实放开了那 5 个字段，而 `MinecraftOpenAlContext` 直接读：

```java
SoundEngine soundEngine = soundManager.soundEngine;   // AT 放开
if (!soundEngine.loaded) { ... }                      // AT 放开
Library library = soundEngine.library;                // AT 放开
long context = library.context;                       // AT 放开
long device = library.currentDevice;                  // AT 放开
```

⇒ **把桥与 Accessor 加回来等于把当年运行期失败的那条路重新接上**，属于**回归**，不做。
类集差异表的这一行据此更正为"**机制差异（AT 取代），非缺口**"。

**那 7 个停放混入的真实阻塞点**也不是桥，而是 **`SableGeometryCompat`**（Sable 子层级坐标）：

| 混入 | 依赖 |
| --- | --- |
| `ClientAudioOutputRegistryLivePositionMixin` / `ControlConsoleAudioElementPositionMixin` / `ModernTurntableSoundSubLevelSourceMixin` / `AudioUtilsSubLevelPositionMixin` / `AudioSubscriptionSubLevelDistanceMixin` / `ClientAudioDemandSubLevelDistanceMixin` / `LiveStreamerControlPacketSubLevelScopeMixin` | 全部 `import compat.sable.SableGeometryCompat` |

它们要的是 **Sable 模组在场**（子层级/plot 坐标 → 世界坐标）；而本工程 `mixins.json` 是
`"required": true`，目标类缺失时 Mixin 会**直接崩**，所以只能停放 ⇒
**要做这块的前提是测试床先装 Sable**（或在单独的可选配置里登记它们）。

### 6.37.4 本轮的代码改动：给眼镜装**三道门**的心跳诊断

`HolographicGlassesWorldScreenRenderer` 的心跳现在一次打出：

```
[NCPB/diag] holographic_glasses 世界屏 心跳：累计调用=… 次，本周期=… 次
            enabled=true 绑定数=… 本帧已提交=… 种类=<戴上与否/绑定摘要>
```

* `绑定数=0` ⇒ **没戴**或**没绑定**，摘要会直接说明（"头部没戴眼镜"/"戴上了但没有任何绑定"/"#0=投影仪BlockPos{…}"）；
* `绑定数>0` 但 `本帧已提交=0` ⇒ 绑定在、**找不到帧**（对应来源没在放画面）；
* `SUBMITTED_QUADS` 是**真数**（在真正 `submitCustomGeometry` 之后自增），并在心跳里取走清零。

| 项 | 值 |
| --- | --- |
| 全量口径 | `clean compileJava` **错误 0 / 警告 7**；排除清单 **0 条** |
| 本轮 jar | `…-0.7.9-beta+forge1.20.1.jar`，**9,379,579 B**，sha256 `5CE87EEA…536C6DCF`，已部署（哈希一致） |
| 门禁 | 重混淆 3/3、Mixin 7/7、注册 23 条、组装 **186 条**、排除清单 G5 PASS、**mods 目录门禁 PASS（目标 mod 恰好 1 份）** —— 全通过 |
| 教训 | 本轮暂无新 lesson 编号；**mods 目录重复 modId** 这条已用门禁固化（见 `tools/check_mods_dir.py` 头部注释），L44 已覆盖"没人引用≠没人调用" |
| **待验** | 戴上全息眼镜（HEAD 槽，无需 Curios）→ 配置界面**绑定一个正在放画面的来源**（投影仪/唱片机/MP4 设备）⇒ 眼镜里应出现画面；若仍无，把那行 `holographic_glasses 世界屏 心跳` 发我（`绑定数`/`本帧已提交`/种类 三者直接定位断在哪一环） |

---

## 6.38 ★★ 第 106 次：发布前完整性审计 + **发布到 GitHub**（`lhzb2049/net_music_can_play_bili-0.7.9-forge1.20.1`）

用户要求："对 1.20.1 而言 **Create: Aeronautics（机械动力：航空学）没有官方主模组**，
检查内容完整性，没问题后发布。"审计过程抓出**一批必须补的东西**，全部处理完才发布。

### 6.38.1 审计抓出的真缺口（都已修）

| # | 发现 | 证据 | 处置 |
| --- | --- | --- | --- |
| 1 | **发布包里一条 `data/` 都没有** ⇒ 方块**打掉不掉落**、**无法合成** | `src/main/resources/data` 是空目录（`npcb-1.20.1` 那个资源同步脚本的 DST 从来不是交付工程） | 新增 `tools/port_data_files.py`：从上游制品翻译 **19 个**数据文件（11 配方 + 6 掉落表 + Curios 头部标签 + 1 entities），逐条规则见脚本头部（目录改名 `loot_table→loot_tables`/`recipe→recipes`/`tags/item→tags/items`、配方 `result.id→result.item`；1.21 的数据附魔与 `enchantable/*` 标签**跳过**，因为 1.20.1 在代码里注册附魔） |
| 2 | `mods.toml` 缺发布元数据、也无图标 | 上游 `neoforge.mods.toml` 有 `logoFile/authors`；上游 jar 根有 `logo.png` | 补齐 `logoFile`/`authors`/`credits`/`displayURL`/`issueTrackerURL`，并抽出上游 `logo.png`（MIT）放进包根 |
| 3 | 包里带着**构建期自检类** `P0ReobfProbe` | `jar tf` 可见 | 打包排除（见 6.38.2 的纪律问题），**dev 产物保留** |
| 4 | 诊断探针**默认开启**（会刷日志） | `UseDiag.ON` 默认 `true` | 改为默认 `false`（`-Dncpb.diag.use=true` 显式打开），代码全保留 |
| 5 | 缺 Curios 数据 ⇒ 装了 Curios 也没有头部槽归类 | 上游有 `data/curios/tags/item/head.json` + `.../curios/entities/players.json` | 已翻译为 1.20.1 的 `tags/items/` 路径随包 |
| 6 | `assets/minecraft/shaders/include/dynamictransforms.glsl` 上游有、我们没有 | 官方制品条目 | **不需要**：那是 1.21 的着色器 include，1.20.1 没有这个文件，我们的 fsh 已改成就地声明 `ColorModulator`（§6.28） |

**与 Create: Aeronautics 的关系**（用户的问题）：`mods.toml` 里**没有任何对它的依赖**
（`forge`/`minecraft`/`netmusic` 必需，`oculus`/`iris` 可选）；唯一与那套物理体系相关的能力是
**面向 Sable 的 7 个子层级音频定位混入**，它们仍停放（1.20.1 无对应主模组 + `required:true` 会崩）
⇒ **缺它不影响游玩，也不需要它**。这条已写进 README 与 Release 说明。

### 6.38.2 改动过程中**门禁又抓到我自己**（两次，都按纪律修）

1. 我一开始把打包排除写成 `build.gradle` 里的字面量 `exclude '…P0ReobfProbe*.class'`
   ⇒ `deferred_cut.py check` 的 **G3 FAIL**（"build.gradle 里还有 inline exclude，会被整体重写时丢掉"）。
   **修法**：新增 `ncpb-assembled/jar-excludes.txt`（文件驱动，与编译期清单同一套纪律），
   build.gradle 只负责读 ⇒ **G1–G5 全 PASS**。
2. 探针从发布包移除后，`check_reobf.ps1` 的两条断言**失去被检对象**（它用探针类在
   dev / reobfJar / build/libs 三处验证名字空间）。
   **修法**：被检类换成**随包发布**的 `blockentity/BlockEntityRenderBounds`（它调用 `BlockPos.getX()`），
   证据完全等价且更强（同时证明**发布包本体**被重混淆过）；`-SelfTest` 仍如实报 FAIL ⇒ 鉴别力没丢。
   （顺带：`edit` 又把这个 `.ps1` 的 UTF-8 BOM 弄丢了 —— 本工程 L29 的老坑，已按字节检查补回 `EF BB BF`。）

### 6.38.3 发布

| 项 | 值 |
| --- | --- |
| 仓库 | <https://github.com/lhzb2049/net_music_can_play_bili-0.7.9-forge1.20.1>（public，已有 LICENSE 提交） |
| 提交 | `README.md` + `LICENSE`（保留作者署名行 + 追加上游/分支署名）+ `CHANGELOG.md` + `dist/`（jar + `.sha256`） |
| Release | `v0.7.9-beta+forge1.20.1`：<https://github.com/lhzb2049/net_music_can_play_bili-0.7.9-forge1.20.1/releases/tag/v0.7.9-beta%2Bforge1.20.1>（**非草稿、非预发布**，标题与说明齐） |
| 资产 | `net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar`（**9,746,661 B**）+ `.jar.sha256` |
| **回下载校验** | Release 资产与本地构建**同尺寸、同 sha256** `139216919F6B…23A4A20A` ✔ |
| 交付件 | `…-0.7.9-beta+forge1.20.1.jar`，**9,746,661 B**，sha256 `139216919F6B…23A4A20A`（已部署到测试床，哈希一致） |
| 门禁 | 重混淆 3/3（改用随包类后仍 PASS，自检仍 FAIL 得出来）、Mixin 通过、注册 23/0、组装 186 条、排除清单 **G1–G5 全 PASS（0 条）**、mods 目录门禁 PASS、数据文件 19/19 可解析、部署审计通过 |
| 未发布 | **源码与移植台账未随仓库公开**（只发了成品 + 使用文档）；需要的话可另开分支补上 |
