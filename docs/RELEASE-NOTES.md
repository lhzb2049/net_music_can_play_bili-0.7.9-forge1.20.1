# Release 说明（可直接粘贴到 GitHub Release）

> 本文件是**发布用的文案底稿**，与 [CHANGELOG.md](../CHANGELOG.md) 的内容一致。
> 发布时把下面 `---` 之间的正文粘进 Release 描述即可。

---

首个公开的 **Forge 1.20.1 移植版**（基线：NeoForge 1.21.1 构建 `0.7.9-beta.3+neo1.21.1`）。

**参考 / 姊妹工程**：[NetMusicCanPlayBili-NEOforge1.21.1](https://github.com/lhzb2049/NetMusicCanPlayBili-NEOforge1.21.1)
（NeoForge 1.21.1 移植，同一作者）。

## 下载

* `net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar` —— 放进 `mods/`
* `…jar.sha256` —— 校验用

**必需前置**：NetMusic `1.5.2-forge+mc1.20.1`；
**可选前置**：Oculus（光影）、Curios（头部饰品槽，**不装也能戴**）。

> ⚠️ **同一 modId 只放一个 jar**。同时留两份（哪怕文件名不同）时，Forge 只会加载其中一个，
> 症状是"新功能没反应、改动没效果"。

## 内容

* 方块：现代唱片机、扬声器、直播机、**视频投影仪**、**歌词投影仪**、**中控台**（含场景编辑器界面）；
* 物品：MP4、Pad、媒体管理工具、隐形耳机、猫耳耳机、**全息眼镜**；
* 数据：**11 个配方 + 6 个方块掉落表 + Curios 头部标签**（按 1.20.1 的目录名与字段规范翻译）；
* 兼容：Oculus / Iris 光影、Embeddium/Sodium 系、Curios（缺失自动降级）。

## 本版移植要点

* 方块实体的渲染裁剪包围盒统一到一份公共实现，并让相关方块成为"全局方块实体"
  ⇒ 修掉**方块离开视野后投影幕 / 字幕 / 中控台面板消失**；
* 光影检测改为按 **Iris API 类是否存在**判断 ⇒ `iris` 与 `oculus` 都识别
  （此前只认 `iris`，在 1.20.1 Forge 上会误判为"没开光影"，表现为**开光影后画面消失**）；
* 中控台场景编辑器的**画中画视口**按 1.20.1 的 `BufferBuilder.RenderedBuffer` / `VertexBuffer#upload`
  路线重建；
* 附魔改为**代码注册**（1.20.1 无数据驱动附魔）；OpenAL 内部字段读取改用 **AccessTransformer**
  （不依赖 refmap）；
* 发布卫生：补齐配方 / 掉落表 / 图标与 `mods.toml` 元数据，移除构建期自检类，
  诊断探针**默认关闭**（`-Dncpb.diag.use=true` 可开）。

## 已知限制（1.20.1 能力差异，非缺陷）

* 场景编辑器地形预览的**半透明面片不做按距离重排**（1.20.1 无 `MeshData.SortState`）；
* 面向 **Sable**（子层级 / plot 坐标）的一组音频定位混入**未启用** ——
  1.20.1 上没有对应主模组，且本包 `mixins.json` 为 `required: true`，目标类缺失会直接崩。
  **"机械动力：航空学"（Create: Aeronautics）在 1.20.1 没有官方主模组**，本移植不声明也不需要该依赖；
* 上游的 `enchantable/*` 标签与数据附魔 JSON 不随包（1.20.1 无该机制）。

完整说明见 [README](../README.md)，逐条变更见 [CHANGELOG](../CHANGELOG.md)。

**MIT** —— 原模组 © [zhongbai233](https://github.com/zhongbai2333/NetMusicCanPlayBili)，
NeoForge 1.21.1 分支 © [lhzb2049](https://github.com/lhzb2049/NetMusicCanPlayBili-NEOforge1.21.1)，
Forge 1.20.1 移植 © [lhzb2049](https://github.com/lhzb2049)。非官方移植，与原作者无隶属关系。

---

## 发布前的自检清单

| 检查 | 期望 |
| --- | --- |
| 资产 | `net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar`（9,746,661 B）+ `.jar.sha256` |
| 哈希 | `sha256 = 139216919f6bc7e5cfb07390ae241c0fab85000ffff22d3206a0999a23a4a20a` |
| 标签 | 与版本一致（例如 `v0.7.9-beta+forge1.20.1`） |
| 勾选 | 这是**beta**，建议勾选 *Set as a pre-release*；确认后再设为 Latest |
| 说明 | 勾选 *Set as the latest release* 前，先确认 jar 已在测试环境装过（本版已实测通过） |
