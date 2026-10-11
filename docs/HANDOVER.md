# 工作交接：`net_music_can_play_bili` → Minecraft 1.20.1（Forge）

> **面向下一个会话（无上下文）**。所有路径相对工作区 `D:\work`（另有绝对路径的地方会写全）。
> 本文件**已覆盖**上一版"开工前计划型"HANDOVER（那份的九条实测前提仍然有效，已并入 §6/§7）。
> 现在的状态：**外观层（方块/物品的模型与资源）已落地并进游戏跑通；UI 与全部玩法尚未开始。**
>
> 阅读顺序建议：§1（用户要求，**不得擅自变更**）→ §2（现状与已验/未验台账）→ §3（怎么继续）→ 其余按需。
>
> **2026-10-08 更新**：外观层抓出并修掉了**一个真凶**——自订 loader 用 `ModelState`
> 施加欧拉旋转，数学等价但**引擎里烤出来是错的**（"扭箱"）。判据来自**游戏自己**
> （新增 `debug/ModelDump.java` 钩子 dump 真顶点 + `tools/compare_game_dump.py` 比对），
> 修后 10 个模型全部偏差 `0.000001`。另修了 `control_console` 角度被就近取整、
> `item/control_console` 静默缺 3 个元素。改动说明与**逐层验证方法**见
> **`VERIFY-2026-10-08.md`**；新增工具见 **§12**。教训 L27–L32。
>
> **2026-10-09 更新（功能移植的起跑线）**：功能移植（Milestone A）在
> **`PORT-STATUS.md`** 里记流水，**先读它的 §6（最新一轮）**。一句话现状：
> **还没有出过功能移植的 jar**；`ncpb-1.20.1`（外观层）全程未被改动；
> 未完成层的排除清单这一轮**外置**成了
> `ncpb-assembled/deferred-excludes.txt`（水位线 + 5 道门禁 + 9 条负例自检，
> 入口 `tools/deferred_cut.py`），目的是止住"清单被重算脚本整体重写时静默变短"。
> ⚠️ **别再拿"错误数下降"当进度**：javac 只要还剩一个错误就停在第一个中止点，
> 唯一的进度判据是"`compileJava` 是否为 0 错误 / 能否出 jar"。
>
> **2026-10-09 第二轮更新（第 88–89 次）—— 上面那句"还没有出过 jar"已经过期**：
>
> * **`compileJava` 已经 0 错误，`gradlew build` 出 jar 了**（BUILD SUCCESSFUL in 15s）：
>   `ncpb-assembled/build/libs/net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar`
>   （3,380,150 B，sha256 `97232031…E11492`）。明细见 `PORT-STATUS.md` §6.9。
> * **但那个 jar 不是"功能移植完成"**：组装层的注册是**外观版**
>   （`ModItems` 用 `simple()` 造裸 `Item`），功能物品 `MP4Item`/`PadItem`/媒体管理工具/
>   耳机/全息眼镜**没有被注册**；渲染层 19 个文件移出编译；**从未在游戏里启动过**。
>   ⇒ 它是"可以开始谈 A1–A6"的入场券。**当前头号长杆 = 待办⑥ `tools/assemble.py` + 功能注册层**（§6.14）。
> * 本轮的三个大发现（都写进了 `PORT-STATUS.md`）：
>   ① **静默存档 bug**：7 个方块实体用了 1.21 的 `saveAdditional(tag, HolderLookup.Provider)`
>      签名 ⇒ 在 1.20.1 **不构成覆盖** ⇒ 编得过但**永远不存档**。已改 + 补 `@Override`（§6.8）。
>   ② **两个门禁自己"抓不到错"**：`build_full.ps1` 的截断探测把"警告"算进了文件数；
>      `check_tools.ps1` 的作用域只覆盖一个工程 ⇒ `ncpb-assembled` 的 **780 个文件从来没被查过**，
>      而它照旧打印"检查了 62 个文件"。两个都已修 + 补负例（现在 **879 个文件**全绿，§6.10）。
>   ③ **规则表会生成编不过的代码**：`port_rename.py` 的 `getLast() → get(size() - 1)`
>      **把接收者丢了**（§6.12，待办⑩）。
> * **教训（比上面三条都重要）**：我自己的分析工具把"世界"定义成**本模组包**，
>   于是 `com/zhongbai233/scene_editor/**` 不在视野里 —— 我因此反复报告"范围内错误 = 0"，
>   而项目口径一直是 **1 条**；台账说 1、我的工具说 0，**这个差我看到了却当成解析器噪声带过**。
>   ⇒ **报"范围内"必须同时写清 range 是哪棵树**；跨树任务一律用项目的全量口径。
>
> **2026-10-09 第三轮更新（第 90 次）—— 功能注册层接上了**：
>
> * `init/ModItems` 的 6 个独立物品从 `simple(name)`（造**裸 `Item`**）改成注册**功能类**
>   （`MP4Item`/`PadItem`/`MediaManagementToolItem`/`InvisibleHeadphonesItem`×2/`HolographicGlassesItem`）；
>   两件穿戴物补 `getDefaultAttributeModifiers`（1.20.1 给属性的唯一正道）。**0 错误 → 出 jar**。
> * **新增两道门禁**（都带负例）：
>   `tools/check_registration.py` —— 按**制品**（`javap` 反汇编 jar）判"物品真的注册上了"，
>   **11/11 通过**；负例用**外观层的 jar**（独立制品）而不是自己造的假 jar。
>   `tools/assemble.py` —— 把"组装差异"登记成事实（`verify` / `register --reason` / 水位线 / 流水），
>   首次建表：**删 20 / 加 7 / 改 131 = 158 条**，负例 **7/7**。
> * **两个"编得过但会炸/没行为"的坑当场拦住**：① 在 `static` 初始化里取注册表项 ⇒ 启动 NPE
>   （`ModAttributes.X.get()` 时注册事件还没到）；② 1.20.1 的三参 `AttributeModifier` **每次生成随机 UUID**
>   （`javap -c` 实证走 `Mth.createInsecureUUID`）⇒ 必须用带显式 UUID 的四参构造器。
> * **一条会重犯的判据错误（已记 §6.16）**：**重混淆会把我们重写的原版方法改成 SRG 名**
>   （dev 是 `getDefaultAttributeModifiers`，jar 里是 `m_7167_`）⇒ 按**方法名**查 jar 的判据是错的，
>   要用**描述符**。类名不会被重混淆，所以"常量池里有 `…/item/MP4Item`"这类判据是成立的。
>
> **2026-10-10 更新（第 91–92 次）—— “交互有、画面全黑”查到根，并修掉（交付 jar 已换新）**：
>
> * **黑屏的真根因是两个（`PORT-STATUS.md` §6.23 / §6.24）**：
>   ① **1.21 没有 `endVertex()`**（顶点隐式收尾），1.20.1 的 `BufferBuilder` 要求**显式**收尾
>      ⇒ 全树 30 处 `.vertex(` / 0 处 `.endVertex()`，“提交了很多次、屏幕空白”；
>      修法 = `port/shim/AutoEndVertexConsumer`（30 处链一次修好）。
>   ② **`PortGuiBufferSource.endBatch()` 只打印、从不上传**：`g.fill` 每调一次都会经
>      `flushIfUnmanaged → flush → endBatch`（字节码实证），而那里既不 `end(...)` 也不 `draw`，
>      `started` 还从不清理 ⇒ **顶点写对了地方，但从没被送出去**，FBO 里只剩清屏色。
>      修法 = 逐类型 `RenderType.end(builder, RenderSystem.getVertexSorting())`
>      （就是 vanilla `BufferSource.endBatch(RenderType)` 那一招），空批次 `discard()`。
> * **上一轮悬着的问题“`g.fill` 的顶点写给了谁”已用字节码定死**：写给了 `GuiGraphics`
>   自己持有的那个 `BufferSource` ⇒ 本工程 = `PortGuiBufferSource` ⇒ `started` 里
>   `RenderType.gui()` 的那个 `BufferBuilder`。**不存在“另一个消费者”**；
>   想当场对身份用 `GuiGraphics.bufferSource()` 比引用即可。
> * **四处门禁在查错对象 / 自己在报假绿**（§6.24.6）：`check_reobf.ps1` 与
>   `check_mixin_wiring.ps1` 的默认 `-ProjectDir` 是外观层那个 **127 KB 的 jar**
>   （已改默认为 `ncpb-assembled`，并给交付工程补了同名 `P0ReobfProbe`，
>   “探针缺失”改为显式 exit 3）；`assemble.py verify` 因上一轮新增的两个 shim 文件
>   **未登记而一直红着**（已 `register`，178 条通过）；**§3.1 的
>   `Set-Location ncpb-1.20.1` 会把外观 jar 覆盖进 mods/**（已改成 `ncpb-assembled`）；
>   **改 `tools/*.ps1` 会掉 BOM** ⇒ PS 5.1 按 GBK 解码、整段中文变乱码、脚本语法错
>   （判据：头 3 字节 `EF BB BF`）。
> * **交付件**：`ncpb-assembled/build/libs/net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar`；
>   全量 `clean compileJava` **0 错误 / 7 警告**，重混淆 3/3、Mixin 接线 7/7、
>   已部署模型 24/24、组装登记 178 条 —— 全通过。
> * **未验（下一步要用户实测）**：手持 Pad/MP4 的**画面内容**。探针仍开着（红底清屏、
>   `UseDiag.ON=true`、FBO 回读、各类型顶点数）；判据 = 日志里 `各类型顶点数` 是**真数**
>   （不再是恒 0）+ `回读FBO … 不同颜色数≥2`，然后看屏幕有没有内容。
>   交付前按 §6.23 那张表撤探针。
>
> **2026-10-10 第二轮更新（第 93 次，用户带回日志）—— 又两处根因，都在“提交之后”**：
> 日志证明上一轮修对了（`各类型顶点数` 从**恒 0** 变成 `{gui=508, forge_text=456}`、每帧结批 143 次），
> 但回读仍是 `不同颜色数=1`。于是：
> * **根因 A：离屏那遍少了 GUI 的 z 平移。** vanilla `GameRenderer` 在 GUI 段是
>   `setIdentity()` **之后** `translate(0,0,1000−ForgeHooksClient.getGuiFarPlane())`（`javap` 实证），
>   而 `GuiGraphics.fill` 的顶点 z=0、正交投影的可见 z 区间是 `[−far,−1000]`
>   ⇒ 只 `setIdentity()` 时**所有 GUI 顶点被近/远平面裁掉**：批次照画、FBO 只剩清屏色。
>   两个离屏渲染器已按 vanilla 原样补上平移（远平面也改用 `getGuiFarPlane()`）。
> * **根因 B：MP4 的离屏一遍从来没被触发。** 判据是两个探针的条数：
>   `mp4 提交屏幕` **1242** 次 vs `mp4 离屏帧开始` **0** 次 —— MP4 只有那个
>   **全工程无调用者**的静态钩子 `renderHeldOffscreenGuiFrameStart()`，而 Pad 是在自己的渲染路径里
>   驱动离屏帧的。已在 MP4 的渲染路径补上同一处触发点。
> * 新增**金丝雀**：`readbackFbo` 返回不同颜色数，**顶点数>0 且颜色数==1** 时直接打印
>   “只有清屏色 ⇒ 查正交投影 z 区间 / 模型视图平移”，这类“画了但被裁掉”不会再伪装成“没画”。
> * 交付件：`…-0.7.9-beta+forge1.20.1.jar`（9,285,346 B，sha256 `6358C7AD…7534D376`），已部署，
>   门禁全通过。**待用户再测一次**：期望 `回读FBO … 不同颜色数 ≥ 2`。
>
> **2026-10-10 第三轮更新（第 93 次续 + 第 95 次）**：
> * **Pad/MP4 已通过用户实测**（画面铺满整张纹理）。第 93 次之后又抓到第三个根因：
>   **投影用了设备像素而不是逻辑像素**（FBO 是 `SCALE×`）⇒ UI 只填左上角 1/4；
>   修法 = `setOrtho(0, WIDTH, HEIGHT, …)`（顺带得到 SCALE× 超采样），见 §6.26。
>   旁证：`D:\work\port-1.21.1\PAD-UI-FINDINGS.md` 记录了上次 1.21.1 移植的同样两个根因
>   （z 裁剪 + 投影口径），以及一条**探针坑**：`glReadPixels` 与 `glClear` 一样受 scissor 影响，
>   回读离屏目标必须在“目标仍绑定 + scissor 是自己设的那块”的时刻做。
> * **视频投影仪（第 95 次）**：① 它在 `deferred-excludes.txt` 里，放回编译**一次就 0 错误**
>   （排除清单过期，7 → 6）；② `strip_deferred_refs.py` 当初连**注册语句**一起摘掉了
>   （`registerRenderers` 里 5 个渲染器只剩 2 个）⇒ 已补 `VIDEO_PROJECTOR → VideoProjectorRenderer::new`。
>   见 §6.27，其中包括**仍未接回来的 6 条被摘语句**清单（`CONTROL_CONSOLE` 那条无阻塞可直接补）。
> * `_deferred-mixins/OffscreenGui*FixMixin` **不要启用**：源码里已直接修对，启用会重复施加。
>
> **2026-10-10 第四轮更新（第 96–97 次）—— 视频这条链基本打通**：
> * 用户实测回报三条，逐条定因并修掉：① **投影仪激活不发光** = 搬运时漏了
>   `VideoProjectorBlock` 的 `.lightLevel(ACTIVATED ? 15 : 0)`（权威版 0.7.10 有、来源版自己就没有）；
>   ② **MP4/Pad 视频蒙红滤镜** = 我第 91 次留下的**红底清屏探针**（alpha=1 ⇒ UI 透明处变不透明红，
>   把后面的视频层盖住），已恢复透明；③ **视频画面全废** = 三个核心着色器照抄了 1.21 的
>   `#moj_import <minecraft:dynamictransforms.glsl>`，而 **1.20.1 没有这个 include**（原版只有
>   fog/projection/matrix/light 四个，且 `ColorModulator` 是各着色器自己声明）⇒ 运行期着色器编译失败
>   ⇒ YUV 退化成单平面。已按 1.20.1 原版写法修好三个 `.fsh`（详见 §6.28 与教训 L38）。
> * **投影仪“没画面”的真身**：`VideoPlaybackPresentation` 是移植早期为了 0 错误手写的 **62 行桩**
>   （上游 271 行）。桩把真帧路径与 `grantDecodeAdmission()` 一起吃掉 ⇒ 投影仪“能激活、能建会话、
>   却只有 LOADING 占位图”（探针里那个 `frame=320x180` 正是占位图尺寸）。已把上游真实现**逐行搬回**
>   （只把 NeoForge 的 `RenderLevelStageEvent` 换成 Forge 的），**一次编过**，调用链与上游同位置。
>   见 §6.29 与教训 L37。
> * 两个投影仪都装了**状态探针**（`video_projector … 状态：visible/linked/session/frame/privacy`、
>   `lyric_projector … 状态：visible/playing/lyricRecord/line/allowAi`），节流打（载荷变化才出）。
>
> **2026-10-10 第五轮更新（第 98 次）—— 中控台与唱片机的渲染器接回来了**：
> * **中控台**与视频投影仪是**同一个缺陷**：`ControlConsoleRenderer` 一直在编译范围内、
>   `ControlConsoleBlockEntity` 也一直在把自己注册成消费者，**唯独那行 BER 注册被“摘引用”删掉了**
>   ⇒ 屏幕永远不画（它的 renderer 还兼着两条每帧视频同步调用点，所以中控台视频一并受影响）。已补。
> * **唱片机渲染器**（`ModernTurntableRenderer`）是又一个**过期排除条目**：唯一缺口是
>   `Model#renderToBuffer` 的颜色参数（1.21 一个 int `-1` → 1.20.1 四个 float），补一行就回来了
>   ⇒ 排除清单 **6 → 4**。剩下 4 个已逐个“体检”并记进 §6.30.3（3 个真缺 + 1 个上游就是死代码）。
> * §6.27.5 那份“被摘语句”清单**只剩 1 条**（PiP 渲染器，等 `HolographicPreviewPipRenderer` 修好）。
> * ⚠️ 判据修正：查“某类是否进了制品”要用 `javap -v`（常量池），**`javap -c` 不打常量池** ——
>   我第一次用 `-c` 查，5 个渲染器全被判成“缺失”，其实都在。


---

## 1. 用户要求（**逐条保留，来自本会话的直接指令**）

| # | 要求（原话要点） | 落地方式 |
| --- | --- | --- |
| 1 | **用 `D:\jdk-21` 里已有的 java**；不要系统安装 JDK 17 | 跑 Gradle 自己的 daemon 用 `D:\jdk-21`；ForgeGradle 的 MCP 管线需要 JDK 17 ⇒ 用**便携解包**的 `D:\jdk-17-toolchain\PFiles64\Microsoft\jdk-17.0.13.11-hotspot`（`gradle.properties` 的 `org.gradle.java.installations.paths`），**未做任何系统级安装** |
| 2 | **测试床固定**为 `D:\航空学\.minecraft\versions\1.20.1-Forge_47.4.26` | 干净实例（Forge **47.4.26**）；`mods/` 只放**我们的 jar** + `[网络音乐机] netmusic-1.5.2-forge+mc1.20.1.jar`；回滚 = 删掉我们的 jar |
| 3 | **先完成基本功能的移植，渲染等问题延后** | 光影（Oculus/Embeddium）、视频管线、自定义着色器全部记为"延后"，不阻塞当前路线 |
| 4 | 当前优先：**方块物品的外观与 UI**（"功能暂缓"） | 模型/贴图/名字/页签/放置已经做完；**UI（屏幕）未开始**；玩法逻辑一律不做 |
| 5 | **以后每个出错都要写入 `D:\work\port-1.21.1\misakanet-pending\ALL-LESSONS.md`** | 做法：写 `L<n>-<slug>.md`（含"字段区"：title/domain/problem/root_cause/fix/verification/tags）→ 跑 `refresh_all_lessons.ps1` **自动收录**。**不要手改 ALL-LESSONS.md 的课节**（它是派生视图，幂等性已验证） |
| 6 | 参考一个**可行的实例**（`[网络音乐机] netmusic-1.5.2-forge+mc1.20.1.jar`）怎么实现 | 该 jar 已解包对照：`pack.mcmeta`、`META-INF/mods.toml`、Mixin 接线、复数数据目录（`recipes/`、`loot_tables/`…）都按它对齐 |
| 7 | 外观有疑问时**"对比原模型"** | **权威参考 = `D:\work\mc\NetMusicCanPlayBili-0.7.10-beta`（MC 26.1.2）**；`0.7.9-beta+neo1.21` 只是**移植来源**，两者数据有差异（已抓到 6 处 `render_type`，见 L26） |
| 8 | 本次交接：**直接覆盖 `D:\work\port-1.20.1\HANDOVER.md`** | 即本文件 |

附加的工作方式（本会话形成的、请沿用）：
* 每一步都要有**能说"不"的门禁**，新门禁必须带负例自检（`-SelfTest`）；
* 文档里**"已验"与"未验"必须分开写**；
* 错误**先取证再动手**（`javap` 看字节码 / 读日志 / `jstack`），不要凭印象（这条本身就是 L22 的教训）。

---

## 2. 现状（一句话 + 台账）

**一句话**：`D:\work\port-1.20.1\ncpb-1.20.1` 是一个**能构建、能重混淆、能进游戏**的 1.20.1 Forge 工程，
已把 6 个方块 + 6 个物品 + 1 个创造页签的**外观**接上（含本模组自订的欧拉角模型 loader），
产物 `net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar`（**118,953 B**，sha256 前缀 `2F061C919105C334`）已在测试床跑过一轮且**零外观错误**。

### 2.1 已验（有证据，可复算）

| 项 | 证据 |
| --- | --- |
| **能构建** | `gradlew build --offline --no-daemon --console=plain` → `BUILD SUCCESSFUL`，**12–31 秒**（`--offline` 可用，见 §9 的坑） |
| **能重混淆** | 门禁 `check_reobf.ps1`：dev 产物是 `getX`、`build/libs/*.jar` 是 `m_123341_`（1.20.1 生产环境只认 SRG） |
| **Mixin 接线** | 门禁 `check_mixin_wiring.ps1`：7 条断言（jar 内 `mixins.json`、MANIFEST `MixinConfigs`、`JAVA_17`、refmap 键/文件、映射非空、探针轨迹） |
| **离线 javac** | 门禁 `offline_javac_probe.ps1`：对 114 条**官方名**类路径 `--release 17` 编译通过 |
| **资源一致性** | 门禁 `check_assets.ps1`：67 个条目与"上游 + 登记规则"**逐字节**一致；负例（删一个 element）报**1 处**漂移并**点名被改文件** |
| **运行期真的注入** | 23:14 那次启动的 `debug.log`：`Found valid mod file … net_music_can_play_bili`、`Registering mixin config …`、`Mixing P0RefmapProbeMixin … into net.minecraft.world.item.ItemStack` |
| **注册层生效** | 日志：`[NCPB/P0] registered 6 blocks / 12 items / 1 creative tab (appearance-only stage)` |
| **外观层零错误** | 23:14 启动的 `latest.log` 里，六类计数**全 0**：`Exception loading blockstate definition: 'net_music_can_play_bili`、`Unknown blockstate property:`、`missing model for variant: 'net_music_can_play_bili`、`Unable to bake model: 'net_music_can_play_bili`、`Failed to load model net_music_can_play_bili`、`Using missing texture`（另有 `Model loader` 0） |
| **能进世界** | 同一次日志：进入 `ServerLevel[新的世界]`、正常保存并 `Stopping!` |
| **游戏内冒烟门禁** | `check_ingame_smoke.ps1` 对该次启动 **exit 0** |

### 2.2 未验（下一步要做的，不要当成已完成）

| 项 | 为什么还没验 / 怎么验 |
| --- | --- |
| **cutout 修正的"视觉"判定** | 日志侧已零错误，但"看起来对不对"要**眼睛**：转盘罩子现在应是**不透明单块**（见 §6 第 7 条、L26）。需要一次截图或口头确认 |
| **图 1 里那两块"平躺的浅色板（金色/青色方块）"是哪个方块** | 未知；**这是下一步第一件事**。方法：与该方块的权威版（0.7.10）模型对比 |
| **UI / 屏幕** | **未开始**。规模见 §7.3（10 个 `Screen` / 2 个 `AbstractContainerScreen` / 2 个 `AbstractContainerMenu`） |
| **全部玩法** | 按用户口径**延后**：网络（73 文件）、物品数据（DataComponents→NBT）、音频/视频、光影 |

---

## 3. 怎么继续（可复制的命令）

### 3.1 构建 + 部署（一条链）

```powershell
$env:JAVA_HOME='D:\jdk-17-toolchain\PFiles64\Microsoft\jdk-17.0.13.11-hotspot'
$env:GRADLE_USER_HOME='D:\gradle-home'
$env:GRADLE_OPTS='-Djava.net.preferIPv4Stack=true'
# ⚠️ 交付 jar 由 **ncpb-assembled** 产出（自第 88 次起）。若照旧写 `ncpb-1.20.1`（外观层），
#    构建出来的是 127 KB 的外观 jar，`Copy-Item` 下去就会**覆盖测试床里的移植 jar**。
Set-Location "D:\work\port-1.20.1\ncpb-assembled"

# 若构建"静默静置"超过 ~1 分钟（见 §9 的四种挂死），先刷新 FG 元数据缓存时间戳：
$now = Get-Date
foreach ($b in @('D:\gradle-home\caches\forge_gradle\minecraft_repo\versions','D:\gradle-home\caches\forge_gradle\mcp_repo\versions')) {
  foreach ($p in @((Join-Path $b 'manifest.json'), (Join-Path $b '1.20.1\version.json'))) { if (Test-Path $p) { (Get-Item $p).LastWriteTime = $now } }
}

& .\gradlew.bat build --offline --no-daemon --console=plain
$jar = (Get-ChildItem .\build\libs\*.jar | Select-Object -First 1)
Copy-Item -LiteralPath $jar.FullName -Destination ('D:\航空学\.minecraft\versions\1.20.1-Forge_47.4.26\mods\' + $jar.Name) -Force
```

**注意**：测试床的 `mods/` 里**只能有**我们的 jar + netmusic 的 jar（多一个都会让归因变难）。

### 3.2 改资源（**不要手改 JSON**）

```powershell
D:\Python311\python.exe D:\work\port-1.20.1\tools\sync_assets.py            # 从上游同步 + 应用登记规则
D:\Python311\python.exe D:\work\port-1.20.1\tools\sync_assets.py --check    # 只校验（门禁用的就是这条）
```

### 3.3 跑门禁（每个都带负例）

```powershell
foreach ($s in 'check_tools.ps1','check_reobf.ps1','check_mixin_wiring.ps1','offline_javac_probe.ps1','check_assets.ps1','check_ingame_smoke.ps1') {
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File ("D:\work\port-1.20.1\tools\" + $s);        "正常 exit=$LASTEXITCODE"
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File ("D:\work\port-1.20.1\tools\" + $s) -SelfTest; "负例 exit=$LASTEXITCODE"
}
```

`check_ingame_smoke.ps1` 会**拒绝**用旧日志判定（产物比日志新 ⇒ **exit 3**，这是刻意的），所以要先启动一次游戏再跑它。

### 3.4 记一条教训（用户要求 #5）

1. 写 `D:\work\port-1.21.1\misakanet-pending\L<下一个号>-<slug>.md`（照现有文件的"字段区"格式）；
2. 跑 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File D:\work\port-1.21.1\misakanet-pending\refresh_all_lessons.ps1`；
3. **不要**去手改 `ALL-LESSONS.md` 的课节（它是生成的）。

---

## 4. 工程结构与产物

```
D:\work\port-1.20.1\
├─ HANDOVER.md                  ← 本文件
├─ MVP-SCOPE.md                 范围（A1–A6 验收判据；in-scope / deferred 两张表）
├─ RESOURCE-RENAMES.md          1.21.1→1.20.1 的数据目录改名（复数目录等）
├─ mixin-targets.md             41 个 Mixin 目标分类台账（自己的/netmusic 的/原版/外部）
├─ netmusic-api-diff.md         30 个 netmusic 类型在 1.20.1 的存在性台账
├─ _mdk\                        forge-1.20.1-47.4.26-mdk.zip 解包（官方基线）
├─ _fg-src\                     ForgeGradle 6.0.54 源码（查行为用，如 EnvironmentChecks）
├─ libs\netmusic-1.5.2-forge+mc1.20.1.jar   （sha1 82c9677d1c63660c5f93d24f55c22db3df76bbf3）
├─ tools\                       门禁与探针（见 §5）
└─ ncpb-1.20.1\                 工程本体
   ├─ build.gradle              MDK 基线 + ForgeGradle 6 + MixinGradle 0.7.38 + Mixin AP 0.8.5
   ├─ gradle.properties         minecraft_version=1.20.1 / forge_version=47.4.26 / official 映射 / 四个 -D（见 §9）
   ├─ gradle\wrapper\…          Gradle **8.8**（+ 官方 sha256；8.14 会崩 FG6）
   ├─ src\main\java\…\          **10 个类**（见下）
   └─ src\main\resources\…      mods.toml / mixins.json（JAVA_17+refmap）/ pack.mcmeta(15) / accesstransformer.cfg / assets（同步生成）
```

**Java 侧 10 个类**（包 `com.zhongbai233.net_music_can_play_bili`）：

| 类 | 作用 | 备注 |
| --- | --- | --- |
| `P0ModEntry` | `@Mod` 入口 | 构造函数注入 `FMLJavaModLoadingContext`（`get()` 已标记删除）；注册 3 个 DeferredRegister |
| `P0ReobfProbe` | 重混淆门禁的被检对象 | 调 `pos.getX()`，用来断言 jar 里变成 `m_123341_` |
| `mixin/P0RefmapProbeMixin` | refmap 活性探针 | `@Inject` 到 `ItemStack.getHoverName()`，运行期打 log4j |
| `init/ModBlocks` | 6 个方块 | **按 blockstate 精确提供状态属性**（`activated`/`playing`/`has_disc`/`facing`）——见 L23 |
| `init/ModItems` | 6 BlockItem + 6 物品 | BlockItem 用 `block.getId().getPath()` 取名 |
| `init/ModCreativeTabs` | 1 个创造页签 | 标题 `itemGroup.net_music_can_play_bili` |
| `ncpbmodel/EulerElement` | 任意角度三轴旋转元素 | 把欧拉旋转塞进 `ModelState` 并**抵消引擎固定支点共轭** |
| `ncpbmodel/EulerElementsLoader` | 解析 `ncpb_euler_elements` | `IGeometryLoader`（Forge 版） |
| `ncpbmodel/EulerElementsGeometry` | 烘焙 | **不再手工继承父链**（父链已在同步阶段摊平，见 L24） |
| `ncpbmodel/NcpbModelGeometry` | 注册 loader | `ModelEvent.RegisterGeometryLoaders`，`@Mod.EventBusSubscriber` |

> 这批 `P0*` 类与探针是**阶段 0/1 的脚手架**：等真正的主类与 Mixin 迁进来后要**替换掉**（别留着两套入口）。

---

## 5. 门禁清单（这是本项目的"验证文化"，别丢）

| 脚本 | 断言什么 | 负例怎么自检 |
| --- | --- | --- |
| `tools/check_tools.ps1` | 所有 `.ps1` 带 BOM 且语法干净；工程文件**无** BOM（现在检查 **59 个文件**） | 篡改一个文件的 BOM |
| `tools/check_reobf.ps1` | dev 名 vs `build/libs/*.jar` 的 SRG 名 | 把 dev 产物放进混淆槽位 ⇒ 必须 FAIL |
| `tools/check_mixin_wiring.ps1` | 7 条 Mixin 接线断言 | 用 `libs\netmusic-…jar` 当负例 |
| `tools/offline_javac_probe.ps1` | 官方名类路径 `--release 17` 编译 | 传坏类路径 |
| `tools/check_assets.ps1` | assets == "上游 + 登记规则"（67 条目） | 临时目录里删一个 element ⇒ 必须报差异**且点名被改文件** |
| `tools/check_ingame_smoke.ps1` | 运行期证据 + 11 条失败标记 + 外观专项 7 条 | 合成日志 |
| `tools/mixin_targets.ps1` | 41 个 Mixin 目标在 1.20.1 是否存在 | —— |
| `tools/netmusic_api_ledger.ps1` | 30 个 netmusic 类型是否存在 | —— |
| `tools/sync_assets.py` | 资源同步/校验（**登记规则**的载体） | `--check` 与工程逐字节比对 |
| `tools/NetProbe.java` / `MetaProbe.java` | Java 侧 HTTPS / FG 式 ETag 探针 | —— |

---

## 6. 已完成的关键技术结论（**都带实测证据，可直接复用**）

1. **映射口径**：1.20.1 生产 jar 是 **SRG**（`m_123341_`），dev 是官方名 ⇒ **必须 reobf**（门禁已在）。
2. **Mixin 接线（Forge 1.20.1 与 NeoForge 不同）**：配置靠 **MANIFEST 的 `MixinConfigs`**（不是 `mods.toml [[mixins]]`）；
   `mixins.json` 需 `compatibilityLevel: JAVA_17` + `refmap`；refmap 由 **Mixin AP**（`org.spongepowered:mixin:0.8.5:processor`）+ **MixinGradle 0.7.38** 生成；
   运行期目标是 SRG ⇒ **没有 refmap 就静默不生效**（L4 的做法：先证明这条路径是活的）。
   已知无害噪声：`Compatibility level JAVA_17 … higher than the maximum level supported by this version of mixin (JAVA_13)`。
3. **自订模型 loader 必须移植并注册**：Forge 侧 API 差异 = `addQuads`/`bakeElementFace` **多一个 `ResourceLocation`**、
   `BlockElementFace` 是**公有字段**（不是 record 访问器）、注解是 **`@Mod.EventBusSubscriber`（嵌套）**、
   `ResourceLocation.fromNamespaceAndPath` 不存在（用构造器，**但它已标记删除** ⇒ 本项目改成只留 String path）。
4. **`render_type` 在 Forge 1.20.1 是受支持的**（不是"NeoForge 独有"）：`ExtendedBlockModelDeserializer` 读它并调
   `BlockGeometryBakingContext.setRenderTypeHint`；内建具名层 `solid / cutout / cutout_mipped / cutout_mipped_all / translucent / tripwire`。
   （这条我一开始判错并写进了 L21，已就地更正 + 记成 L22：**平台能力必须查字节码**。）
5. **`BlockModel.getElements()` 在 Forge 上对"挂了自定义 loader 的模型"返回空列表** ⇒ 父链元素在运行期取不到
   （`hasCustomGeometry()` 短路）⇒ 本工程改为**在资源同步阶段摊平**父链（L24）。
6. **方块状态属性必须与 blockstate JSON 精确匹配**：只注册光板 `Block` ⇒ 变体全失败 ⇒ **放下变紫黑缺失模型**，
   而物品栏正常（两条路径不同）。多给属性同样会坏（L23）。
7. **外观权威版与移植来源不是同一版**：`0.7.9`(1.21.1) 给 6 个方块模型写了 `render_type: minecraft:translucent`，
   `0.7.10`(26.1.2) **完全没有**；`modern_turntable.png` 有 **846 个半透明像素** ⇒ 在 `translucent` 下会透出罩子内壁
   （看起来像"两片竖直大板"），在 `cutout` 下才是参考版那个**不透明单块**（L26）。**已登记规则：`translucent → cutout`**。
8. **引擎口径在 1.20.1 与 1.21.1 一致**（已用 `javap` 逐条核）：`FaceBakery.setupShape` **除以 16**、
   `bakeVertex` 的 **`applyElementRotation` → `applyModelRotation`** 顺序、`applyModelRotation` 绕**固定支点 (0.5,0.5,0.5)**，
   且 `Transformation.getMatrix()` 返回原矩阵 ⇒ 上游那套"ModelState 共轭"数学在 1.20.1 上**成立**。

---

## 7. 迁移面实测数字（来自对 1.21.1 来源工程的静态统计）

### 7.1 机械改名（大而无脑）

`net.neoforged.*` 共 **132 文件 / 268 处**。主要对应：`neoforge.network`(96) → `net.minecraftforge.network`（**体系不同**）、
`neoforge.client`(51) → `net.minecraftforge.client`、`neoforge.event`(25)、`api.distmarker`(17)、`bus.api`(16)、
`fml.common`(14)、`neoforge.registries`(13)、`fml.loading`(10)、`neoforge.server`(8)、`neoforge.common`(6)。
配套：`NeoForge.EVENT_BUS`（2 文件/21 处）→ `MinecraftForge.EVENT_BUS`；`DeferredHolder`（4/15）→ `RegistryObject`。

### 7.2 需要**重写**的子系统

| 子系统 | 1.21.1 现状（规模） | 1.20.1 目标 |
| --- | --- | --- |
| 网络 | `CustomPacketPayload` 73 文件/288 处、`IPayloadContext` 51/111、`StreamCodec` 42/147 | `SimpleChannel` + `FriendlyByteBuf`（**最大的一块**） |
| 包缓冲 | `RegistryFriendlyByteBuf` 47/258 | 1.20.1 **没有**这个类 ⇒ `FriendlyByteBuf` + 手工取注册表 |
| 物品数据 | `DataComponents` 11/54、`CustomData` 7/56 | 回到 NBT（`CompoundTag`） |
| 能力 | `RegisterCapabilitiesEvent` 1/3 | `Capability` + `LazyOptional` |
| 配置 | `ModConfigSpec` 1/5 | 同名（几乎不用改） |

### 7.3 UI / 渲染 / 光影

| 面 | 规模 | 判断 |
| --- | --- | --- |
| **UI（外观层的下一段）** | `extends Screen` **10 文件**、`AbstractContainerScreen` **2**、`AbstractContainerMenu` **2**、`MenuType<` 1 文件/5 处、`GuiGraphics` 38 文件/253 处 | 多数是**代码绘制**（`assets/…/textures/gui` 只有 **1 张 png**，另有 `control_console_video` 3 张、`video_loading` 9 张） |
| 通用渲染 | `RenderType` 15 文件、`MultiBufferSource` 17、`PoseStack` 24、`VertexConsumer` 10 | 1.20.1 都有，面比网络小 |
| 自定义着色器 | `ShaderInstance` 2/14、`RenderStateShard` 2/15 | 需逐行对照 1.20.1（**延后**） |
| 光影兼容 | Iris/Shaderpack 17 文件/107 处 | 1.20.1 是 **Oculus + Embeddium**，包名全不同 ⇒ **延后** |
| 原生库 | 6 平台 FFmpeg JNI | **与 MC 版本无关**，可直接复用 |
| Mixin | 38 条（12 common + 26 client） | 逐个核目标（台账 `mixin-targets.md`：41 个目标已分类） |

---

## 8. 阶段计划与状态

| 阶段 | 状态 |
| --- | --- |
| **阶段 0 — 环境与骨架** | ✅ **完成**：JDK 17（便携）+ Gradle 8.8 + ForgeGradle 6 + official 映射；空工程 `build` 通过；离线 javac 通过 |
| **阶段 A — 外观层（用户插入的优先段）** | ✅ 方块/物品/页签 + 资源 + 自订模型 loader + 摊平 + 渲染层修正；进游戏零外观错误。**⏳ 待视觉确认** |
| **阶段 A2 — UI（外观层的下一段）** | ⬜ 未开始（规模见 §7.3） |
| 阶段 1 — 机械改名 | 🔶 **编译层面完成**（第 88–89 次）：`ncpb-assembled` 的 `compileJava` **0 错误**、`gradlew build` **出 jar**；`port_rename.py` 规则表 + 两个补丁脚本（`port_manual_round88/89.py`）承载。⚠️ 注册层仍是**外观版** ⇒ 见阶段 1b |
| **阶段 1b — 功能注册层**（第 89 次新增 → **第 90 次完成**） | ✅ `ModItems` 的 6 个独立物品改注册**功能类**（`MP4Item`/`PadItem`/…），两件穿戴物补 `getDefaultAttributeModifiers`；`tools/assemble.py` 把组装差异登记成事实。**按制品**的注册门禁 `tools/check_registration.py` **11/11 通过**（正例=功能 jar，负例=外观层 jar） |
| 阶段 2 — 网络重写 | 🔶 **编译层面完成**（网络包已能编过；`SimpleChannel` + `FriendlyByteBuf` 的改造在 88 次之前已完成）。**运行期未验** |
| 阶段 3 — 物品数据（DataComponents→NBT） | 🔶 **编译层面完成**（`port/nbt/CustomData` 垫片 + 本轮把 `stack.update(DataComponents…)` 等改完）。**存档往返未验** |
| 阶段 4 — 渲染/视频/光影 | 🔶 **用户口径：延后**；19 个文件移出编译（`deferred-excludes.txt`），另有一批 1.21 顶点/缓冲管线已按 1.20.1 改写（§6.9） |
| 阶段 5 — 本地媒体 | ⬜ 未开始（与 MC 版本无关，可整块搬） |
| 阶段 6 — 门禁迁移 | 🔶 部分完成（构建期门禁 6 道 + 2 本台账；第 89 次修掉两道门禁自身的缺陷，见 §6.10） |
| 阶段 7 — 进游戏回归与发布 | ⬜ 未开始 —— **现在可以开始了**（注册层已接上）。注意：渲染层 19 个文件移出编译 + 若干桩，**画面不是验收项**；要你给测试实例路径 |

**下一步优先级（建议）**：
1. **拿到外观的"眼睛判定"**：cutout 后转盘罩子是否与参考图一致；并回答"图 1 里平躺的浅色板是哪个方块"。
2. **把"对比原模型"工具化**（强烈建议）：写一个**离线几何对比器**，把我们的 assets 与 **`0.7.10` 权威版**逐元素/逐顶点比对
   —— 权威版用**原生欧拉旋转**（`elements[].rotation` 里直接是 `{x,y,z,origin}`），我们用自己的 ModelState 数学算一遍，
   断言两者顶点一致。这样"外观对不对"就**不需要启动游戏**。可直接复用 `D:\work\port-1.21.1\t5\` 里的
   `EulerMathProbe.java`（离线数学证明）、`model_diff.py`（语义对比）、`gen_euler_models.py`（模型生成）、`preview_models.py`（预览）。
3. 按比对结果修模型（**一律走 `sync_assets.py` 的登记规则**，不手改 JSON）。
4. UI 层（10 个 `Screen` + 2 个容器屏 + 2 个菜单）。
5. 玩法（网络 → 物品数据 → 音频 → 视频 → 光影）。

> **⚠️ 上面这份清单是"外观层轨"的**（阶段 A/A2），仍然有效。
> **功能移植轨的下一步不在这里** —— 已挪到 `PORT-STATUS.md` **§6.14 待办表**，
> 当前头号长杆是 **⑥ `tools/assemble.py` + ⑫ 功能注册层**（不接上，jar 就"编得过但物品没行为"）。
> 两份文档的分工：`HANDOVER.md` = 外观层轨的交接；`PORT-STATUS.md` = 功能移植轨的活台账。

---

## 9. 环境陷阱（**读 `tools/README.md` §1–§17**；这里只放最要命的）

* **只有 Windows PowerShell 5.1**（没有 `pwsh`）；脚本要 `-ExecutionPolicy Bypass -File`。
* **BOM 不对称**：`.ps1` 必须带 BOM；`.java`/`.gradle`/`.json`/`.md` **必须无 BOM**（门禁 `check_tools.ps1` 在管）。
* **文件名含 `[ ]` 要用 `-LiteralPath`**；读 BOM-less 文件要 `-Encoding UTF8`。
* **不要用 `JAVA_TOOL_OPTIONS`**（会污染退出码），用 `GRADLE_OPTS`。
* 本机 **IPv6 不通** ⇒ `-Djava.net.preferIPv4Stack=true`（已在 `gradle.properties`）。
* **"构建挂住"有四种，判据都是 `jstack`**（详见 `tools/README.md` §4/§15/§16）：
  1. Gradle 发行包 PKIX/证书链 → 先用 `curl --ssl-revoke-best-effort` 下好并**预置**到 wrapper dist 目录（校验官方 sha256）；
  2. 插件依赖解析卡在 `SSLSocketInputRecord.readFully` → 用 `--offline`；
  3. **ForgeGradle 的 ETag 元数据刷新**（1 小时窗口过期后发条件请求，慢速滴流下永久挂住）→ 刷新那 4 个文件的 mtime；
  4. **ForgeGradle 的启动证书检查**（`EnvironmentChecks.testServerConnection`，每次配置阶段都跑，**`--offline` 管不到**）→
     已在 `gradle.properties` 加 `-Dnet.minecraftforge.gradle.check.certs=false`。
* **`--no-daemon` 更可观测**（`--no-daemon` 下的 12–31 秒是正常值）。
* **复数数据目录**：1.20.1 是 `recipes/`、`loot_tables/`、`tags/items/`、`tags/blocks/`；1.21 才有 `enchantment/`、`tags/item/…`。
* **`latest.log`/`debug.log` 是 log4j appender**：`System.out.println` **不会**进去（L17）—— 要让日志成为证据，就用 log4j。

---

## 10. 铁律（沿用 1.21.1 那轮，**不要因为换了 MC 版本就丢**）

1. **离线优先**：能离线就不联网；Gradle 只在需要产出制品时用。
2. **每条检查都要能说"不"**：新增/修改门禁都要有 `-SelfTest` 故障注入，且**失败理由必须对得上**（L2）。
3. **改动要么被登记、要么让构建失败**：有意分歧逐条登记（本工程的载体是 `sync_assets.py` 的登记规则 + `check_assets.ps1`）。
4. **探针类路径里不能有产品的旧副本**（`mods/` 里最危险）；让探针先打印"我加载的类来自哪里"（L11）。
5. **独立实现只保证不共享代码，不保证正确**（L12）；关键不变量用最强证据（逐样本 sha 相等）。
6. **"没有报错"不等于"没发生"**：静默 return、默认值、静默 no-op 都很常见（L10/L4）。
7. **不写死绝对路径**；含中文的目录名用码点拼（照 `port-1.21.1/tools/` 的做法）。
8. **记账**：文档里"已验/未验"分开写（本文件 §2 就是模板）。
9. **教训落盘**：按用户要求写进 `port-1.21.1/misakanet-pending/`，再跑刷新脚本（见 §3.4）。
10. **平台能力必须取证**：`javap` 看字节码 / 读真实产物 / `jstack` 看栈 —— 不要凭印象（L22）。

---

## 11. 参考入口（按需读，别一次全读）

| 想知道 | 读 |
| --- | --- |
| **本次改动 + 你怎么自己验（先读这个）** | `D:\work\port-1.20.1\VERIFY-2026-10-08.md` |
| 环境事实全集（17 节，含四种挂死的判据） | `D:\work\port-1.20.1\tools\README.md` |
| 失误教训 **L1–L28**（本会话新增 L13–L26，2026-10-08 新增 L27–L28） | `D:\work\port-1.21.1\misakanet-pending\ALL-LESSONS.md`（派生视图）/ `L*.md`（源） |
| 集成范围与验收判据 | `D:\work\port-1.20.1\MVP-SCOPE.md` |
| 资源目录/字段的 1.21.1→1.20.1 改名 | `D:\work\port-1.20.1\RESOURCE-RENAMES.md` |
| Mixin 目标台账（41 个） | `D:\work\port-1.20.1\mixin-targets.md` |
| netmusic 依赖的类型台账（30 个） | `D:\work\port-1.20.1\netmusic-api-diff.md` |
| **权威外观参考**（MC 26.1.2） | `D:\work\mc\NetMusicCanPlayBili-0.7.10-beta\`（`assets/` + `src/`） |
| 移植来源（1.21.1） | `D:\work\mc\NetMusicCanPlayBili-0.7.9-beta+neo1.21\`（755 个 `.java`） |
| 1.20.1 Forge 工程模板 | `D:\work\mc\NetMusic-1.20\`（`build.gradle`、`mods.toml`） |
| **模型工具链（上一轮留下的，可直接复用）** | `D:\work\port-1.21.1\t5\`（`EulerMathProbe.java`、`euler_math_probe.py`、`gen_euler_models.py`、`model_diff.py`、`preview_models.py`、`inventory/*.tsv`、`26.1.2-CuboidRotation.javap.txt`） |
| 1.21.1 那轮怎么从 jar 造源码 | `D:\work\mc\NetMusicCanPlayBili-0.7.9-beta+neo1.21\docs\HANDOVER-NCPB-1.21.1-src.md` |
| 功能清单（迁移范围的事实来源） | `…\docs\play-guide.md` |

---

## 12. 2026-10-08 新增：离线几何对比器（"外观对不对"不再需要启动游戏）

### 12.1 为什么做

原来的处境：`check_ingame_smoke.ps1` 只能证明"**没有报错**"，
而外观错误最常见的形态恰恰是**不报错**（L27：角度被原版 schema 就近取整成合法值）。
所以需要一条**比原版格式更宽**的判据：越过 schema，直接与权威版 0.7.10 逐元素、逐顶点比。

### 12.2 新工具

| 文件 | 作用 | 退出码 |
| --- | --- | --- |
| `tools/ncpbgeom.py` | 烘焙数学（1.20.1 `FaceBakery` 口径）+ assets 读取；口径对着字节码写，注释带证据路径 | 模块 |
| `tools/compare_geometry.py` | **门禁**：逐元素逐顶点与权威版比对；`--fault` 负例自检 | 0 通过 / 1 不一致 / **2 对比器自己失效** |
| `tools/compare_models_report.py` | 诊断报告：元素数、父链、角度集合、包围盒、贴图解析 | 报告 |
| `tools/gen_previews.py` | 摊平两侧模型 + 出渲染图（`--render`，含"修正前"对照） | 0 / 2 / 3 |
| `tools/find_bakery_jar.py` | 在 ForgeGradle 缓存里找**真正可用**的 jar（防截断/错名） | 0 / 2 |
| `tools/run_testbed.ps1` | 命令行启动干净测试床（不依赖 PCL 的 GUI） | 0 / 2 |
| `tools/diag_projector_overlap.py` | 一次性诊断（父子元素重叠 / 非法角度） | 0 |

**注意**：`compare_geometry.py` 里的 `KNOWN_DIFF` 是一张**登记表** ——
两侧元素数本来就不一样的模型必须逐条写明原因，否则会以 **exit 2**（对比器自己失效）报出，
**不会静默跳过**。"跳过"与"通过"被刻意分成两种结果。

### 12.3 字节码证据落盘

`_bakery-javap/`（由 `tools/find_bakery_jar.py` 选出的 jar + `javap -p -c` 反汇编）：

* `…FaceBakery.txt` —— `setupShape` 除以 16、`applyElementRotation`（四元数、单轴）、
  `applyModelRotation`（**固定支点 (0.5,0.5,0.5)**）、`rotateVertexBy` 逐字；
* `…BlockElement_Deserializer.txt` —— `m_111342_`：角度只放行 **0 / ±22.5 / ±45**，否则 `JsonParseException`；
* `…BlockElementRotation.txt`、`…BlockElement.txt`、`…BlockFaceUV.txt`；
* `com_mojang_math_Transformation.txt`。

⚠️ **一个环境坑**：`…\joined\mergeMappings\output.jar` **不是 zip**（文件头 `tsrg`，
是映射文本被误命名）。`javap` 对它只说 `zip END header not found`，极易误判成"类名写错"。
本机可用的是 `…\joined\rename\output.jar`（**SRG 名**；读算法够用，字节码一样）。

### 12.4 本次抓到并修掉的 bug

`block/control_console.json` 3 个元素角度错（`-20 → -22.5`、`10 → 0`、`7.5 → 0`）。
来源 0.7.9 里就已是取整值；原版 schema 只收 5 个角度 ⇒ 一写就合法、永远不报错。
修法 = `sync_assets.py` 新增登记规则（`SNAP_RESTORE_MODELS`），还原的元素放进
`ncpb_euler_elements` 并补 `loader`。**完整说明见 `VERIFY-2026-10-08.md`。**

### 12.5 仍未处理（已登记）

1. **4 个投影仪模型有 24/28 个重复元素**（来源的叶子与父携带同一批几何，摊平装了两遍）；
   渲染结果不变，但几何量翻倍。**该修**。
2. **`block/live_streamer` 两侧都没有自己的 elements**（纯 cube 父模型）⇒ 离线逐顶点**无对象可比**。
3. **权威版 `block/projector` 比我们多 4 个元素**（28 vs 24）——来源版本差异，需决定要不要补。

---

## 13. 2026-10-08 关键结论：外观真凶在 loader 的烘焙路径（**必读**）

**症状**：日志零错误、离线渲染与权威版逐像素相同，但**游戏里形状不对**
（各面单独轴对齐、互相对不上，整体像被拧过）。

**根因**：自订 loader 曾把「绕 origin 的欧拉旋转」共轭塞进 `ModelState`，
交给引擎 `applyModelRotation` 施加。**数学等价（JOML 逐顶点差 < 1e-7），引擎里却不对。**

**分界线**（游戏自己 dump 的顶点 vs 离线预测）：

| 模型 | 走哪条路 | 偏差 |
| --- | --- | --- |
| `speaker` | 原版单轴旋转 | **0.000001** ✅ |
| `control_console` / 投影仪 | 自订欧拉路径 | **0.19** ❌ |

**修法**：`EulerElement` 只负责算，`EulerElementsGeometry` **逐顶点复刻**
`FaceBakery` 的 `applyElementRotation` → `applyModelRotation` 两步，自己写 `BakedQuad`。
修后 **10 个模型全部 0.000001**。

**怎么自己复现这条判据**（这是本项目**唯一不依赖自己复现**的检查）：

```powershell
powershell -File tools\run_testbed.ps1 -JvmArg '-Dncpb.dumpModels=true'
D:\Python311\python.exe tools\compare_game_dump.py        # 期望全部 ok / 0.000001
D:\Python311\python.exe tools\compare_game_dump.py --fault # 必须 FAIL
```

**教训**（`misakanet-pending/L31`、`L32`）：

* L31：**"数学等价" ≠ "引擎按我以为的方式施加"** —— 判据必须来自引擎自身；
* L32：**只用"自己的工具"互相验证，永远发现不了自己的 bug** ——
  本次一个角点下标 bug（`k & 2` 恒为 0，8 个角退化成 4 个）同时用在两侧，
  误差精确抵消，于是"包围盒差 0.0e+00"这种最硬的证据恰恰最不可信。


