# 变更记录

本文件记录本移植仓库的发布。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号沿用上游（`<上游版本>+forge1.20.1`）。

## [0.7.9-beta+forge1.20.1] - 2026-10-11

首个公开的 Forge 1.20.1 移植版（基线：NeoForge 1.21.1 分支 `0.7.9-beta.3`）。

### Added

- 完整的 1.20.1 移植：方块（现代唱片机 / 扬声器 / 直播机 / 视频投影仪 / 歌词投影仪 / 中控台）、
  物品（MP4 / Pad / 媒体管理工具 / 隐形耳机 / 猫耳耳机 / 全息眼镜）、
  中控台场景编辑器、B 站音视频与直播播放链路、光影（Oculus/Iris）兼容路线、Curios 头部槽软兼容。
- **11 个配方 + 6 个方块掉落表 + Curios 头部标签**（从上游 1.21.1 制品按版本差异翻译：
  目录名 `loot_table/recipe/tags/item` → `loot_tables/recipes/tags/items`，
  配方 `result.id` → `result.item`）。
- 模组图标 `logo.png` 与完整的 `mods.toml` 元数据（authors / credits / displayURL / issueTrackerURL，
  以及 Oculus / Iris 的**可选**依赖声明）。

### Changed

- 附魔改为**代码注册**（1.20.1 无数据驱动附魔）；光影检测改按 **Iris API 类是否存在**判断，
  因此 `iris` 与 `oculus` 都能识别（此前只认 `iris`，在 1.20.1 Forge 上会误判为"没开光影"）。
- 读取 OpenAL 内部字段改用 **AccessTransformer**（字段已放开为 public），
  替代上游那组依赖 refmap 的 Mixin 访问器。
- 方块实体的渲染裁剪包围盒统一到一份公共实现
  （视频投影仪 / 歌词投影仪 / 现代唱片机 / 中控台），并让其成为"全局方块实体"，
  修掉"方块离开视野后画面消失"。
- 中控台场景编辑器的**画中画视口**恢复渲染（按 1.20.1 的
  `BufferBuilder.RenderedBuffer` / `VertexBuffer#upload` 路线重建）。

### Fixed

- 视频投影仪、歌词投影仪、中控台面板在世界中"方块出视野即消失"。
- 光影开启后画面消失（Oculus 未被识别）。
- 中控台编辑器中间视口全黑（缺少画中画预览渲染器的注册与整层代码）。
- 方块被破坏后不掉落、无法合成（数据文件缺失）。
- 发布包内不再包含构建期自检类；诊断探针默认关闭。

### Known limitations

- 场景编辑器地形预览的**半透明面片不做按距离重排**（1.20.1 无 `MeshData.SortState`）。
- 面向 **Sable**（子层级/plot 坐标）的一组音频定位混入**未启用**：1.20.1 上没有对应主模组，
  且 `mixins.json` 为 `required: true`，目标类缺失会直接崩。
  **"机械动力：航空学"在 1.20.1 无官方主模组**，本移植不依赖它。
- 上游的 `enchantable/*` 标签与数据附魔 JSON 不随包（1.20.1 无该机制）。

[0.7.9-beta+forge1.20.1]: https://github.com/lhzb2049/net_music_can_play_bili-0.7.9-forge1.20.1/releases/tag/v0.7.9-beta+forge1.20.1
