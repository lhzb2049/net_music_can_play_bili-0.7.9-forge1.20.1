# Net Music Can Play Bili —— Forge 1.20.1 移植版

[中文](#中文) · [English](#english)

> **本仓库是 [NetMusicCanPlayBili](https://github.com/zhongbai2333/NetMusicCanPlayBili)（主线）
> 的 Forge 1.20.1 非官方移植。**“本仓库由 MC百科用户 流火之冰 维护
> 上游 / 分支 / 移植三者版权与许可见 [LICENSE](LICENSE)。

---

## 中文

给 [NetMusic](https://modrinth.com/mod/netmusic) 增加 **BiliBili 视频 / 直播音频**支持：
把 B 站视频链接（`BV`/`av`）写进音乐 CD 即可播放它的音频；在广播喇叭里输入
`live:直播间ID` 可播放直播音频；另有视频投影仪、歌词投影仪、中控台、
MP4 / Pad 手持播放器与全息眼镜等一套"媒体硬件"。

### 版本矩阵

| 项 | 值 |
|---|---|
| Minecraft | **1.20.1** |
| Forge | **1.20.1-47.4.x**（47.4.26 实测） |
| Java | **17** |
| mod 版本 | `0.7.9-beta+forge1.20.1` |
| 必需前置 | **NetMusic 1.5.2-forge+mc1.20.1** |
| 可选 | **Oculus 1.8.0+**（光影兼容；Iris 分支亦可） |
| 可选 | Curios（把耳机/眼镜放进头部饰品槽；**不装也能戴**，走原版头部装备槽） |

### 安装

1. 装好 Minecraft 1.20.1 + Forge 47.4.x；
2. 把 **NetMusic**（1.5.2-forge+mc1.20.1）与 [Releases](../../releases) 里的本模组 jar
   一起放进 `mods/`；
3. 启动游戏。写 B 站链接进音乐 CD，或给广播喇叭 `live:直播间ID`。

> ⚠️ **同一 modId 只放一个 jar**。如果你同时留着旧构建（哪怕文件名不同），
> Forge 只会加载其中一个，症状是"新功能没反应、改动没效果"。
> 本移植的发布命名固定为 `net_music_can_play_bili-<版本>+forge1.20.1.jar`，请只保留最新那份。

> 🛠️ **想自己构建、或想看移植过程？** 见 **`source` 分支**：可构建源码（Gradle 工程）
> \+ 9 条门禁脚本 + 移植台账（`docs/PORT-STATUS.md`、`docs/HANDOVER.md`），
> 说明见该分支的 [BUILDING.md](https://github.com/lhzb2049/net_music_can_play_bili-0.7.9-forge1.20.1/blob/source/BUILDING.md)。

### 包含内容

* 方块：现代唱片机、扬声器、直播机、**视频投影仪**、**歌词投影仪**、**中控台**（含场景编辑器界面）；
* 物品：MP4、Pad、媒体管理工具、隐形耳机、猫耳耳机、**全息眼镜**；
* 数据：**11 个配方 + 6 个方块掉落表**（对齐上游）、Curios 头部标签；
* 兼容：光影（Oculus/Iris，自订 YUV 着色器在光影下自动切换兼容路线）、
  现代渲染模组（Embeddium/Sodium 系）与 Curios（缺失时自动降级）。

### 已知差异与限制（1.20.1 vs 上游 1.21.1）

这些是**版本能力差异**，不是缺陷；都已在包内按 1.20.1 的实际 API 落地：

| 项 | 说明 |
|---|---|
| 附魔 | 1.20.1 没有数据驱动附魔 ⇒ `耳机` / `全息眼镜` 两个附魔改为**代码注册**（类别 `ARMOR_HEAD`，效果一致） |
| 光影检测 | 1.20.1 Forge 上光影通常是 **Oculus**（modId `oculus`），本移植按**是否提供 Iris API v0** 判断，`iris` 与 `oculus` 都识别 |
| 中控台编辑器视口 | 地形预览的**半透明面片不做按距离重排**（1.20.1 没有 `MeshData.SortState` / `uploadIndexBuffer`），极少数叠放顺序可能与上游略不同 |
| OpenAL 访问 | 读取声音引擎内部字段改用 **AccessTransformer**（等价于上游的 Mixin 访问器，且不依赖 refmap） |
| 子层级音频定位 | 面向 **Sable**（子层级/plot 坐标）的那组混入**未启用** —— 1.20.1 上没有对应的主模组，且本包 `mixins.json` 是 `required: true`，目标类缺失会直接崩。装 Sable 后可再评估 |
| 附魔数据标签 | 上游的 `enchantable/*` 标签只服务于数据驱动附魔；1.20.1 无此机制，故不随包 |

**"机械动力：航空学"（Create: Aeronautics）**：该模组在 1.20.1 上**没有官方发布的主模组**，
本移植因此**不声明任何对它的依赖**，也不需要它 —— 上表"子层级音频定位"是唯一与那套物理体系
相关的能力，缺失时只是音频定位按普通世界坐标处理。

### 排查

日志开关（默认**关**，发布包不会刷日志）：

```
-Dncpb.diag.use=true      # 打开本模组的诊断探针（投影仪/眼镜/视频链路会打印状态与心跳）
```

### 致谢与许可

**MIT**，见 [LICENSE](LICENSE)。

* 原模组与主线：© [zhongbai233](https://github.com/zhongbai2333/NetMusicCanPlayBili)
* NeoForge 1.21.1 分支：© [lhzb2049](https://github.com/lhzb2049/NetMusicCanPlayBili-NEOforge1.21.1)
* Forge 1.20.1 移植：© [lhzb2049](https://github.com/lhzb2049)

**非官方移植**，与原作者及分支作者无隶属关系；问题请提到本仓库。
本模组依赖第三方 B 站接口，请遵守 B 站服务条款；仅供学习与互操作性用途。

---

## English

An **unofficial Forge 1.20.1 port** of
[NetMusicCanPlayBili](https://github.com/zhongbai2333/NetMusicCanPlayBili) (based on the
NeoForge 1.21.1 branch by [wrdqtww](https://github.com/wrdqtww/NetMusicCanPlayBili-1.21.1)).

It adds **Bilibili video / live audio** playback to [NetMusic](https://modrinth.com/mod/netmusic):
write a Bilibili link (`BV`/`av`) into a music CD to play its audio, or type `live:<roomId>`
into a megaphone for live audio. The port also ships the mod's "media hardware":
video projector, lyric projector, control console (with scene editor), MP4/Pad handheld
players and holographic glasses.

| | |
|---|---|
| Minecraft | **1.20.1** |
| Forge | **47.4.x** (tested on 47.4.26) |
| Java | **17** |
| Version | `0.7.9-beta+forge1.20.1` |
| Required | **NetMusic 1.5.2-forge+mc1.20.1** |
| Optional | Oculus 1.8.0+ (shader support), Curios (head slot; works without it) |

**Install**: drop NetMusic and the jar from [Releases](../../releases) into `mods/`.
Keep exactly **one** jar of this mod — duplicate modIds make Forge load only one of them.

**Port-specific differences** (capability gaps of 1.20.1, all handled in code):
enchantments are code-registered (1.20.1 has no data-driven enchantments); shader mods are
detected through the Iris API class (works for both Iris and Oculus); the scene-editor terrain
preview does not distance-sort translucent quads (no `MeshData.SortState` in 1.20.1);
OpenAL internals are read through an AccessTransformer instead of Mixin accessors; and the
Sable (sub-level physics) audio-positioning mixins are intentionally disabled because there is
no corresponding main mod on 1.20.1 — **Create: Aeronautics has no official 1.20.1 release**,
so nothing here depends on it.

**License**: MIT — see [LICENSE](LICENSE). Unofficial port; issues belong in this repository.
