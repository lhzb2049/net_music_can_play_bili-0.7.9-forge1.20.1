# 从源码构建（Forge 1.20.1 移植）

本分支（`source`）是**可构建的完整源码**；`main` 分支是面向使用者的发布视图
（README / 下载 / 变更记录）。

## 目录

| 路径 | 内容 |
|---|---|
| `src/main/java` | 移植后的模组源码（740 个 java 文件） |
| `src/main/resources` | 资源：`assets/`、`data/`（配方/掉落表/标签）、`META-INF/mods.toml`、`pack.mcmeta`、`logo.png`、`native/`（FFmpeg 动态库，5 个平台） |
| `build.gradle` / `settings.gradle` / `gradle.properties` | ForgeGradle 6 构建脚本（基线 = 官方 MDK `forge-1.20.1-47.4.26`） |
| `gradlew` / `gradle/wrapper` | Gradle 8.8 wrapper |
| `deferred-excludes.txt` | 编译期排除清单（**唯一权威**，由 `tools/deferred_cut.py` 读写；目前 **0 条**） |
| `jar-excludes.txt` | 打包期排除清单（同上纪律，目前排除构建期自检类） |
| `assemble-manifest.txt` | 组装台账：每个 java 文件是"上游原样 / 移植改写 / 新增"以及理由 |
| `_deferred-mixins/` | 暂未启用的 Mixin 源码（保留以便日后接线） |
| `tools/` | 门禁与构建脚本（见下） |
| `docs/` | 移植台账（`PORT-STATUS.md`、`HANDOVER.md`）：逐轮问题、根因、验证方式 |

## 构建

前置：**JDK 17**、可访问 `maven.minecraftforge.net`（首次构建要下依赖）。

```bash
export JAVA_HOME=/path/to/jdk-17          # Windows: $env:JAVA_HOME = 'C:\path\to\jdk-17'
./gradlew build
```

产物：`build/libs/net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar`
（ForgeGradle 会把 `build/libs` 的 jar 一并重混淆回 SRG —— 这正是能被游戏加载的那份）。

> `gradle.properties` 里**故意不提交**机器相关的 `org.gradle.java.installations.paths`。
> 如果你的 Gradle 找不到 JDK 17，把它写进**你自己的** `<GRADLE_USER_HOME>/gradle.properties`
> （或直接设 `JAVA_HOME`）。

## 门禁（本移植的验收手段）

这些脚本不是"跑着好看"：每条都会**说不**，并且带 `--SelfTest` / `-SelfTest` 自检
（故意制造错误输入时**必须**失败）。

| 脚本 | 断言 |
|---|---|
| `tools/build_full.ps1` | 全量口径（`clean compileJava`）的错误/警告计数 —— 增量编译会掩盖错误，所以错误数只认这个口径 |
| `tools/check_reobf.ps1` | dev 产物=官方名、重混淆产物与**可部署 jar**=SRG 名（1.20.1 必须重混淆，否则进游戏 `NoSuchMethodError` / Mixin 静默失效） |
| `tools/check_mixin_wiring.ps1` | Mixin 配置在 jar manifest 里可见、refmap 已生成且非空 |
| `tools/check_registration.py` | 方块/物品/实体/菜单等的注册条目齐全（判据逐条列出） |
| `tools/audit_deployed_jar.py` | 部署 jar 的模型角度等硬性检查 |
| `tools/deferred_cut.py check` | 排除清单的 G1–G5（含"build.gradle 不得有 inline exclude"与"构建日志必须比清单新"） |
| `tools/check_mods_dir.py` | 测试床 `mods/` 里**同一 modId 只能出现一次**（本工程实测踩过：两份同名不同文件的 jar 会让游戏加载旧的那份） |
| `tools/port_data_files.py` | 从上游制品翻译 `data/`（配方/掉落表/标签）到 1.20.1 规范；`--check` 校验落盘结果可解析 |
| `tools/assemble.py verify` | 每个 java 文件都已登记在 `assemble-manifest.txt`（防止"手改了没记账"） |

用法示例（`--ProjectDir` 默认取脚本上一级，即本工程根）：

```powershell
.\tools\check_reobf.ps1
python .\tools\check_registration.py
python .\tools\deferred_cut.py check
python .\tools\assemble.py verify
```

## 本分支**未**包含的东西（以及为什么）

| 未包含 | 说明 |
|---|---|
| 上游 1.21.1 制品（`upstream/*.jar`） | `tools/port_data_files.py` 需要它做数据文件翻译。自行从上游 Release 取，放到 `upstream/` 下，或用 `NCPB_UPSTREAM_JAR` 指定路径 |
| `stage1-src/` 组装基线 | 本工程的"阶段 0 基线"快照（移植前状态）。`tools/assemble.py` 的比较依赖它；只构建模组不需要 |
| 一次性排错探针 | 移植过程中用过的一次性脚本（几何/反编译/资产对比等）未随包，避免噪音 |
| 机器相关设置 | 见上文 `org.gradle.java.installations.paths` |

## 已验证

本分支的源码在作者机器上**离线构建通过**（`./gradlew build --offline`，`BUILD SUCCESSFUL`），
产物与 `main` 分支发布的 jar **字节数完全一致**（9,746,661 B；哈希不同仅因为 jar 内含时间戳）。

## 许可

MIT，见 [LICENSE](LICENSE)。原模组 © zhongbai233，NeoForge 1.21.1 分支 © wrdqtww，
Forge 1.20.1 移植 © 流火之冰 (lhzb2049)。**非官方移植**，问题请提到本仓库。
