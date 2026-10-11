#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁：mods 目录里**同一个 modId 只能出现在一个 jar** 里（第 104 次新增）。

## 为什么要有它

本工程第 104 次实测踩到：测试床 `mods/` 里同时躺着**两个自己构建的 jar**
（旧那份只是文件名少了 `-beta`，两边的 `modId` 与 `version` **完全一样**）。
Forge 遇到重复 modId 只会加载其中一个（哪个不确定），于是：

* 现象一：新恢复的类（如 `HolographicGlassesWorldScreenRenderer`）"明明在制品里"，游戏里却没反应；
* 现象二：排查方向被引到代码上，而真正的问题是**测试床里装的是旧构建**。

⇒ 把"装了几份我自己"变成一条**能说不**的判据：版本号相同更危险（连版本都分不出来）。

## 判据

1. 扫描 `--mods` 目录下所有 `*.jar`，从 `META-INF/mods.toml` 的 `[[mods]]` 段读 `modId`
   （只取**第一个 `[[dependencies` 之前**的 `modId`，后面的都是依赖声明）；
2. 任何 modId 出现在 **≥2 个 jar** 里 ⇒ 失败（并打印每个 jar 的路径、大小、sha256 前 12 位、版本）；
3. 目标 modId（默认 `net_music_can_play_bili`）必须**恰好出现一次**，否则失败；
4. 顺带打印目标 mod 的 `version`，便于人工核对"装的是不是刚构建的那份"。

## 退出码

* `0` 通过
* `1` 判据失败（重复 modId / 目标缺失）
* `2` 用法或环境错误（目录不存在等）
* `--self-test` 用临时目录跑"正例 + 两个负例"，**必须一个通过、两个失败**才算自检通过
"""

from __future__ import annotations

import argparse
import hashlib
import io
import os
import re
import shutil
import sys
import tempfile
import zipfile

DEFAULT_MODS = os.environ.get("NCPB_MODS_DIR", "mods")
TARGET_MODID = "net_music_can_play_bili"
MODID_RE = re.compile(r'^\s*modId\s*=\s*"([^"]+)"', re.MULTILINE)
VERSION_RE = re.compile(r'^\s*version\s*=\s*"([^"]+)"', re.MULTILINE)


def read_mods_toml(jar_path: str) -> str | None:
    try:
        with zipfile.ZipFile(jar_path) as zf:
            for name in ("META-INF/mods.toml", "META-INF/neoforge.mods.toml"):
                try:
                    return zf.read(name).decode("utf-8", "replace")
                except KeyError:
                    continue
    except (zipfile.BadZipFile, OSError):
        return None
    return None


def own_modids(toml: str) -> list[str]:
    """只取 [[mods]] 段（第一个 [[dependencies 之前）里的 modId。"""
    head = toml.split("[[dependencies", 1)[0]
    return MODID_RE.findall(head)


def own_version(toml: str) -> str | None:
    head = toml.split("[[dependencies", 1)[0]
    m = VERSION_RE.search(head)
    return m.group(1) if m else None


def sha12(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()[:12]


def scan(mods_dir: str, target: str) -> tuple[bool, list[str]]:
    lines: list[str] = []
    if not os.path.isdir(mods_dir):
        return False, [f"目录不存在：{mods_dir}"]

    jars = sorted(
        os.path.join(mods_dir, n)
        for n in os.listdir(mods_dir)
        if n.lower().endswith(".jar")
    )
    lines.append(f"扫描 {len(jars)} 个 jar：{mods_dir}")

    by_modid: dict[str, list[tuple[str, str | None, int, str]]] = {}
    for jar in jars:
        toml = read_mods_toml(jar)
        if toml is None:
            lines.append(f"  - {os.path.basename(jar)}：没有 mods.toml（跳过）")
            continue
        ids = own_modids(toml)
        version = own_version(toml)
        size = os.path.getsize(jar)
        for mid in ids:
            by_modid.setdefault(mid, []).append((jar, version, size, sha12(jar)))
        if target in ids:
            lines.append(
                f"  - 目标 mod：{os.path.basename(jar)}  version={version}  {size} B  sha256={sha12(jar)}"
            )

    ok = True
    for mid, entries in sorted(by_modid.items()):
        if len(entries) < 2:
            continue
        ok = False
        lines.append(f"[FAIL] modId 重复：{mid} 出现在 {len(entries)} 个 jar 里")
        for path, version, size, digest in entries:
            lines.append(f"         {os.path.basename(path)}  version={version}  {size} B  sha256={digest}")

    target_entries = by_modid.get(target, [])
    if not target_entries:
        ok = False
        lines.append(f"[FAIL] 没有找到目标 mod：{target}")
    elif len(target_entries) > 1:
        ok = False
        lines.append(f"[FAIL] 目标 mod 出现 {len(target_entries)} 次（必须恰好 1 次）")
    else:
        lines.append(f"[OK] 目标 mod 恰好 1 份：{os.path.basename(target_entries[0][0])}")
    return ok, lines


def _make_jar(path: str, modid: str, version: str) -> None:
    toml = (
        'modLoader="javafml"\n'
        'loaderVersion="[47,)"\n'
        'license="MIT"\n'
        "[[mods]]\n"
        f'modId="{modid}"\n'
        f'version="{version}"\n'
        'displayName="Synthetic"\n'
        "[[dependencies.x]]\n"
        '    modId="minecraft"\n'
    )
    with zipfile.ZipFile(path, "w") as zf:
        zf.writestr("META-INF/mods.toml", toml)


def self_test() -> int:
    """正例必须通过；两个负例（重复 modId、目标缺失）必须失败。"""
    results: list[tuple[str, bool, bool]] = []
    with tempfile.TemporaryDirectory() as tmp:
        # 正例：一份目标 mod + 一个别的 mod
        good = os.path.join(tmp, "good")
        os.makedirs(good)
        _make_jar(os.path.join(good, "ncpb.jar"), TARGET_MODID, "0.7.9-beta")
        _make_jar(os.path.join(good, "other.jar"), "some_other_mod", "1.0")
        passed, _ = scan(good, TARGET_MODID)
        results.append(("正例（单份）", passed, True))

        # 负例 1：两份同 modId（正是第 104 次踩到的情形）
        dup = os.path.join(tmp, "dup")
        os.makedirs(dup)
        _make_jar(os.path.join(dup, "ncpb-old.jar"), TARGET_MODID, "0.7.9-beta")
        _make_jar(os.path.join(dup, "ncpb-new.jar"), TARGET_MODID, "0.7.9-beta")
        passed, _ = scan(dup, TARGET_MODID)
        results.append(("负例（重复 modId）", passed, False))

        # 负例 2：目标 mod 缺失
        empty = os.path.join(tmp, "empty")
        os.makedirs(empty)
        _make_jar(os.path.join(empty, "other.jar"), "some_other_mod", "1.0")
        passed, _ = scan(empty, TARGET_MODID)
        results.append(("负例（目标缺失）", passed, False))

    bad = 0
    print("---- 门禁自检：mods 目录重复 modId")
    for name, got, want in results:
        mark = "PASS" if got == want else "!! FAIL"
        if got != want:
            bad += 1
        print(f"  {mark}  {name}：期望{'通过' if want else '失败'}，实际{'通过' if got else '失败'}")
    print(f"---- 自检结论：{len(results) - bad}/{len(results)} 条符合预期")
    return 0 if bad == 0 else 1


def main() -> int:
    ap = argparse.ArgumentParser(description="检查 mods 目录里是否出现重复 modId")
    ap.add_argument("--mods", default=DEFAULT_MODS, help="mods 目录（默认测试床）")
    ap.add_argument("--modid", default=TARGET_MODID, help="必须恰好出现一次的 modId")
    ap.add_argument("--self-test", action="store_true", help="跑正例/负例自检")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    ok, lines = scan(args.mods, args.modid)
    for line in lines:
        print(line)
    print("---- 门禁结果：" + ("通过" if ok else "失败"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
