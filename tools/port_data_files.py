#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把上游（1.21.1）制品里的 data/ 资源翻译成 1.20.1 可用的一套，写进组装树。

## 为什么需要它（第 105 次审计发现）

发布前审计发现：ncpb-assembled/src/main/resources/data **是空的** ——
也就是说我们打出来的 jar **没有任何配方、掉落表与标签**：

* 方块**打掉不掉落**（1.20.1 里没有 loot table 的方块就是不掉落）；
* 方块**无法合成**（配方缺失）。

上游 1.21.1 制品里有 6 个 loot_table + 11 个 recipe + 4 个 tags + Curios 数据。
本脚本按版本差异**逐项翻译**后落盘（每一条都有对应规则，不做"尽量像"的猜测）。

## 1.21 -> 1.20.1 的差异（本脚本处理的全部规则）

| 1.21.1 | 1.20.1 | 处理 |
| --- | --- | --- |
| data/<ns>/loot_table/blocks/*.json | data/<ns>/loot_tables/blocks/*.json | **目录改名**（1.21 起单数） |
| data/<ns>/recipe/*.json | data/<ns>/recipes/*.json | **目录改名** |
| 配方 "result": {"id": "...", "count": n} | "result": {"item": "...", "count": n} | **字段改名**（1.21 把 item 改成 id） |
| data/<ns>/tags/item/** | data/<ns>/tags/items/** | **目录改名**（1.20.1 是复数） |
| data/curios/tags/item/head.json | data/curios/tags/items/head.json | **目录改名** |
| data/net_music_can_play_bili/curios/entities/players.json | 同名 | 原样 |
| data/<ns>/enchantment/*.json、data/minecraft/tags/enchantment/*.json | **不存在** | **跳过**：1.20.1 没有数据驱动附魔，本工程在代码里注册（ModEnchantments，类别 ARMOR_HEAD） |
| data/<ns>/tags/item/enchantable/*.json | **跳过** | 它们只服务于上面那两个数据附魔；1.20.1 里没有代码引用，且其唯一取值 #minecraft:enchantable/head_armor 在 1.20.1 也不存在，留着只会刷"未知标签"警告 |

## 退出码

* 0 写出成功（或 --check 判定一致）
* 1 校验失败（写出的 JSON 解析不了 / 缺文件）
* 2 用法或环境错误（源 jar 不存在等）
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import zipfile

# 控制台默认是 GBK（本工程的老坑）：显式把标准输出切到 UTF-8，
# 并且正文尽量只用 GBK 也覆盖得到的字符（箭头一律写成 ASCII 的 ->）。
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except Exception:  # noqa: BLE001 - 老 Python / 非常规流都直接跳过
        pass

DEFAULT_SRC = os.environ.get(
    "NCPB_UPSTREAM_JAR",
    os.path.join("upstream", "net_music_can_play_bili-0.7.9-beta.3+neo1.21.1.jar"))
DEFAULT_OUT = os.environ.get("NCPB_RESOURCES_DIR", os.path.join("src", "main", "resources"))
NS = "net_music_can_play_bili"

MOVE_DIRS = (
    # (上游前缀, 本地前缀)
    (f"data/{NS}/loot_table/", f"data/{NS}/loot_tables/"),
    (f"data/{NS}/recipe/", f"data/{NS}/recipes/"),
    (f"data/{NS}/tags/item/", f"data/{NS}/tags/items/"),
    ("data/curios/tags/item/", "data/curios/tags/items/"),
)

# 1.21 专属、1.20.1 不适用，不移植
SKIP_PREFIXES = (
    f"data/{NS}/enchantment/",
    "data/minecraft/tags/enchantment/",
    f"data/{NS}/tags/item/enchantable/",
)


def translate_path(path: str) -> str | None:
    """上游路径 -> 本地路径；返回 None 表示该文件不移植。"""
    for skip in SKIP_PREFIXES:
        if path.startswith(skip):
            return None
    for src, dst in MOVE_DIRS:
        if path.startswith(src):
            return dst + path[len(src):]
    return path          # 原样（例如 curios/entities/players.json）


def translate_recipe(text: str) -> tuple[str, bool]:
    """配方：result.id -> result.item（1.21 的改名）。返回 (新文本, 是否改动)。"""
    data = json.loads(text)
    changed = False
    result = data.get("result")
    if isinstance(result, dict) and "id" in result:
        result["item"] = result.pop("id")
        changed = True
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n", changed


def main() -> int:
    ap = argparse.ArgumentParser(description="把上游 data/ 资源翻译成 1.20.1 并写入组装树")
    ap.add_argument("--src", default=DEFAULT_SRC, help="上游 jar（1.21.1 制品）")
    ap.add_argument("--out", default=DEFAULT_OUT, help="目标 resources/data 目录")
    ap.add_argument("--check", action="store_true", help="只校验：目标文件存在且 JSON 可解析")
    ap.add_argument("--dry-run", action="store_true", help="只打印将要写出的路径")
    args = ap.parse_args()

    if not os.path.isfile(args.src):
        print("源 jar 不存在：" + args.src)
        return 2

    with zipfile.ZipFile(args.src) as zf:
        entries = [e for e in zf.namelist() if e.startswith("data/") and not e.endswith("/")]

    planned: list[tuple[str, str]] = []
    skipped: list[str] = []
    for path in sorted(entries):
        local = translate_path(path)
        if local is None:
            skipped.append(path)
        else:
            planned.append((path, local))

    print(f"上游 data 文件 {len(entries)} 个 -> 移植 {len(planned)} 个、跳过 {len(skipped)} 个（1.21 专属）")
    for s in skipped:
        print("  跳过：" + s)

    if args.dry_run:
        for path, local in planned:
            print("  " + path + "\n    -> " + local)
        return 0

    if not args.check:
        os.makedirs(args.out, exist_ok=True)

    written = 0
    changed_recipes = 0
    with zipfile.ZipFile(args.src) as zf:
        for path, local in planned:
            raw = zf.read(path).decode("utf-8")
            text = raw
            if path.startswith(f"data/{NS}/recipe/"):
                text, changed = translate_recipe(raw)
                if changed:
                    changed_recipes += 1
            target = os.path.join(args.out, *local.split("/"))
            if args.check:
                if not os.path.isfile(target):
                    print("  [FAIL] 缺失：" + local)
                    return 1
                try:
                    with open(target, encoding="utf-8") as fh:
                        json.loads(fh.read())
                except json.JSONDecodeError as exc:
                    print(f"  [FAIL] JSON 解析失败：{local}（{exc}）")
                    return 1
                continue
            os.makedirs(os.path.dirname(target), exist_ok=True)
            with open(target, "w", encoding="utf-8", newline="\n") as fh:
                fh.write(text)
            written += 1

    if args.check:
        print(f"[OK] {len(planned)} 个文件都已存在且 JSON 可解析")
        return 0

    print(f"[OK] 写出 {written} 个文件到 {args.out}（其中配方字段改写 {changed_recipes} 个）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
