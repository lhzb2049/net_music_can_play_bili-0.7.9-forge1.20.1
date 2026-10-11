#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""**注册层门禁**：确认 jar 里真的把"功能物品"注册上了，而不是裸 `Item`。

## 为什么必须有这条

第 89 次出 jar 时，`init/ModItems.java` 用的是**外观层**写法
（`simple("mp4")` 造裸 `Item`）⇒ **`compileJava` 0 错误、jar 也出得来**，
但物品没有任何行为（Pad 打不开界面、MP4 不认设备、耳机不带属性）。
"能编译 / 能出 jar"**证明不了**这件事 —— 所以要有这条按**制品**判的门禁。

判据（全部来自 `javap` 反汇编 jar 里的 class，不依赖源码）：

1. `init/ModItems` 的反汇编里必须出现**每个功能物品类名**（`MP4Item`/`PadItem`/
   `MediaManagementToolItem`/`InvisibleHeadphonesItem`/`HolographicGlassesItem`）；
2. `item/InvisibleHeadphonesItem` 与 `item/HolographicGlassesItem` 必须**重写了属性钩子**
   （按**描述符**判：`(EquipmentSlot) → Multimap`）。
   ⚠️ 不能按方法名判 —— 重混淆会把重写的原版方法改成 SRG 名（实测 `m_7167_`），
   类名不会被改，所以描述符才是跨"dev / 制品"都成立的判据（见 PORT-STATUS §6.16）；
3. `init/ModCreativeTabs` 必须引用 `init/ModItems`（页签真的把它们摆出来）。

负例（`--selftest`）用的是**外观层的 jar**（`ncpb-1.20.1/build/libs/*.jar`）——
那是一份独立制品，它**应当**被判失败。这比"自己造一份假 jar"更硬。

用法：
    python tools/check_registration.py --jar <jar>
    python tools/check_registration.py --selftest
退出码：0 = 通过；1 = 有判据不成立；2 = 自检失败；3 = 前置缺失。
"""
import argparse
import glob
import os
import re
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
PORT = os.path.join(ROOT, 'port-1.20.1')
JAVAP = os.environ.get('JAVAP') or (
    os.path.join(os.environ['JAVA_HOME'], 'bin', 'javap.exe') if os.environ.get('JAVA_HOME') else 'javap')
DEFAULT_JAR = os.path.join(PORT, 'ncpb-assembled', 'build', 'libs',
                           'net_music_can_play_bili-0.7.9-beta+forge1.20.1.jar')
APPEARANCE_JAR_GLOB = os.path.join(PORT, 'ncpb-1.20.1', 'build', 'libs', '*.jar')
PKG = 'com.zhongbai233.net_music_can_play_bili.'

BLOCK_CLASSES = ('ModernTurntableBlock', 'LyricProjectorBlock', 'VideoProjectorBlock',
                 'SpeakerBlock', 'LiveStreamerBlock', 'ControlConsoleBlock')
ITEM_CLASSES = ['MP4Item', 'PadItem', 'MediaManagementToolItem',
                'InvisibleHeadphonesItem', 'HolographicGlassesItem']


def disassemble(jar, fqcn):
    """退回 (文本, 错误)。javap 失败时错误里带原因（不静默当成"通过"）。

    ⚠️ 必须带 `-v`：`javap -p -c` **不打印常量池/`BootstrapMethods`**，
    而"`ModItems` 引用了 `MP4Item`"这个事实**只**出现在那里（方法引用是 invokedynamic）。
    第一版漏了 `-v` ⇒ 这条判据**永远失败**（连正例都判失败），
    也就是一条"只会说 No 的假门禁"。见 PORT-STATUS §6.16。
    """
    if not os.path.isfile(JAVAP):
        return None, '找不到 javap：%s' % JAVAP
    p = subprocess.run([JAVAP, '-p', '-c', '-v', '-classpath', jar, fqcn],
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out = p.stdout.decode('utf-8', 'replace')
    err = p.stderr.decode('utf-8', 'replace')
    if p.returncode != 0:
        return None, (err.strip() or out.strip() or 'javap 退出码 %d' % p.returncode)
    return out, None


def check(jar, verbose=True):
    """-> (失败列表, 检查项数)。失败列表为空 = 通过。"""
    fails = []
    n = 0

    if not os.path.isfile(jar):
        return ['前置缺失：jar 不存在 %s' % jar], 0

    # ① init/ModItems 的常量池里必须出现每个功能物品类的 Class 条目
    #    （判据用完整内部名 `…/item/MP4Item`，避免"名字碰巧出现在别处"的假通过）
    text, err = disassemble(jar, PKG + 'init.ModItems')
    n += 1
    if err:
        fails.append('反汇编 init/ModItems 失败：%s' % err)
    else:
        for c in ITEM_CLASSES:
            n += 1
            if ('net_music_can_play_bili/item/%s' % c) not in text:
                fails.append('init/ModItems 的常量池里**没有** %s ⇒ 这个物品还是裸 Item（无行为）' % c)

    # ② 两件穿戴物必须**真的重写了属性钩子**。
    #    ⚠️ 不能按方法名查 jar：**重混淆会把我们自己重写的原版方法改成 SRG 名** ——
    #    dev class 里是 `getDefaultAttributeModifiers(EquipmentSlot)`，
    #    jar 里变成了 `m_7167_(EquipmentSlot)`（实测，见 §6.16）。
    #    类名不会被重混淆 ⇒ 判据用**描述符**（对两种状态都成立）：
    #      (Lnet/minecraft/world/entity/EquipmentSlot;)Lcom/google/common/collect/Multimap;
    DESCRIPTOR = 'descriptor: (Lnet/minecraft/world/entity/EquipmentSlot;)Lcom/google/common/collect/Multimap;'
    devdir = os.path.join(PORT, 'ncpb-assembled', 'build', 'classes', 'java', 'main')
    for c in ('InvisibleHeadphonesItem', 'HolographicGlassesItem'):
        t, e = disassemble(jar, PKG + 'item.' + c)
        n += 1
        if e:
            fails.append('反汇编 item/%s 失败：%s' % (c, e))
        elif DESCRIPTOR not in t:
            fails.append('item/%s **没有**重写 (EquipmentSlot)→Multimap 的钩子 '
                         '⇒ 1.20.1 上不会带属性' % c)
        else:
            # 额外（可读性证据）：如果 dev class 还在，顺手确认名字是对的
            devp = os.path.join(devdir, *((PKG + 'item.' + c).split('.'))) + '.class'
            if os.path.isfile(devp):
                td, ed = disassemble(devdir, PKG + 'item.' + c)
                n += 1
                if ed or 'getDefaultAttributeModifiers' not in td:
                    fails.append('dev class item/%s 里方法名不是 getDefaultAttributeModifiers（%s）'
                                 % (c, ed or '找不到该方法名'))

    # ③ 页签必须引用 ModItems
    t, e = disassemble(jar, PKG + 'init.ModCreativeTabs')
    n += 1
    if e:
        fails.append('反汇编 init/ModCreativeTabs 失败：%s' % e)
    elif not re.search(r'init[/.]ModItems', t):
        fails.append('init/ModCreativeTabs 没有引用 init/ModItems（页签没把物品摆出来？）')

    # ④ 方块必须注册成**功能类**，不能是"占位方块"
    #    【第 91 次续的实测缺陷】外观层为了"先有模型"造了一批占位类
    #    （ActivatedBlock/FacingActivatedBlock/FacingPlayingBlock/FacingHasDiscBlock，全是 `extends Block`），
    #    组装时没换成功能类 ⇒ 方块**没有 use()、没有方块实体** ⇒ 空手右键也毫无反应，
    #    连 GUI 都不会尝试打开。而编译、打 jar、注册门禁（当时只查物品）**全是绿的**。
    #    判据同 ①：常量池里必须出现功能方块类的完整内部名。
    #    负例用**外观层的 jar**（它注册的正是占位类）⇒ 必须被判失败。
    text, err = disassemble(jar, PKG + 'init.ModBlocks')
    n += 1
    if err:
        fails.append('反汇编 init/ModBlocks 失败：%s' % err)
    else:
        for c in BLOCK_CLASSES:
            n += 1
            if ('net_music_can_play_bili/block/%s' % c) not in text:
                fails.append('init/ModBlocks 的常量池里**没有** %s ⇒ 这个方块还是占位类'
                             '（`extends Block`，没有 use()、没有方块实体，右键不会有任何反应）' % c)

    # ⑤ 附魔与附魔书必须先真的注册出来
    #    【第 91 次续的实测缺陷】上游页签里有两本附魔书，本工程**连附魔注册都没有**
    #    （ForgeRegistries.ENCHANTMENTS 全树为零）⇒ 书无处可来，页签里少两样。
    #    判据：init/ModEnchantments 必须在 jar 里，且常量池含两条附魔 id；
    #          页签必须引用 EnchantedBookItem 与 init/ModEnchantments。
    text, err = disassemble(jar, PKG + 'init.ModEnchantments')
    n += 1
    if err:
        fails.append('反汇编 init/ModEnchantments 失败：%s ⇒ 附魔没注册，页签里不会有那两本书' % err)
    else:
        for eid in ('headphones', 'holographic_glasses'):
            n += 1
            # ⚠️ 常量池里的字符串**不带引号**（`#12 = Utf8   headphones`）。
            # 第一版按 `"headphones"` 找 ⇒ 正例被判假失败（判据写错会得出反向结论）。
            if eid not in text:
                fails.append('init/ModEnchantments 里找不到附魔 id %s' % eid)
    t, e = disassemble(jar, PKG + 'init.ModCreativeTabs')
    n += 1
    if e:
        fails.append('反汇编 init/ModCreativeTabs 失败：%s' % e)
    elif 'EnchantedBookItem' not in t:
        fails.append('init/ModCreativeTabs 没有引用 EnchantedBookItem ⇒ 页签里没有那两本附魔书')
    n += 1
    if not e and 'init/ModEnchantments' not in t and 'init.ModEnchantments' not in t:
        fails.append('init/ModCreativeTabs 没有引用 init/ModEnchantments ⇒ 附魔书不是用本模组的附魔造的')

    if verbose:
        print('检查 jar：%s' % jar)
        print('  判据 %d 条，失败 %d 条' % (n, len(fails)))
        for f in fails:
            print('  !! ' + f)
    return fails, n


def selftest():
    bad = 0
    print('=== 正例：当前功能 jar 必须通过 ===')
    if not os.path.isfile(DEFAULT_JAR):
        print('!! 前置缺失：%s（先 gradlew build）' % DEFAULT_JAR)
        return 3
    fails, n = check(DEFAULT_JAR)
    if fails:
        bad += 1
    else:
        print('  正例通过（%d 条判据）' % n)

    print()
    print('=== 负例：外观层的 jar 必须被判失败 ===')
    cands = sorted(glob.glob(APPEARANCE_JAR_GLOB))
    if not cands:
        print('!! 前置缺失：找不到外观层 jar（%s）—— 没有负例就没有鉴别力，判失败' % APPEARANCE_JAR_GLOB)
        return 3
    fails, n = check(cands[0])
    if not fails:
        print('!! 外观层 jar 竟然"通过"了 —— 说明判据没有鉴别力')
        bad += 1
    else:
        missing = [f for f in fails if '还是裸 Item' in f or '不会带属性' in f]
        if not missing:
            print('!! 被判失败，但失败理由不是"裸 Item / 不带属性" —— 可能是别的原因：')
            for f in fails:
                print('     ' + f)
            bad += 1
        else:
            print('  负例通过：外观层 jar 被判失败，且理由对得上（%d 条）' % len(missing))
            for f in missing[:3]:
                print('     ' + f)

    print()
    print('---- 负例自检：%s' % ('通过' if bad == 0 else '失败 %d 项' % bad))
    return 0 if bad == 0 else 2


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--jar', default=DEFAULT_JAR)
    ap.add_argument('--selftest', action='store_true')
    args = ap.parse_args()
    if args.selftest:
        return selftest()
    fails, n = check(args.jar)
    if n == 0:
        return 3
    return 0 if not fails else 1


if __name__ == '__main__':
    sys.exit(main())
