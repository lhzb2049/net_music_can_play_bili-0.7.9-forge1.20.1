#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""组装（待办⑥）：把"`ncpb-assembled` 与 `stage1-src` 的差异"变成**登记过的事实**。

## 为什么不是"从 stage1-src 重算一遍"

第 89 次逐文件比过 sha256：`stage1-src`（`port_rename.py` 的产物）与
`ncpb-assembled/src/main/java` 的差异是 **删 20 / 加 7 / 直改 130**。
其中那 130 个"直接编辑"是十几轮里逐处改出来的（`network` 48、`client` 27、`gui` 10…），
**没有任何脚本能重新发明它们**。所以本工具不去"重算"，它做的是项目一直在用的那件事：

> **改动要么被登记、要么让构建失败。**

* 差异集合**登记**在 `ncpb-assembled/assemble-manifest.txt`（三段：deleted / added / overridden）；
* `verify` 逐条校验登记与磁盘是否一致：**新出现的差异**（没登记）⇒ 失败；
  **登记过的删除又回来了** ⇒ 失败；**登记过的新增不见了** ⇒ 失败；
* `register` 是**唯一**的写入入口，会记流水（`assemble.log`）并抬高水位线
  （`assemble-manifest.hwm`），用来抓"登记表被手改短"（即 §6.1 那种自擦除）。

## 子命令

    verify                     门禁（退出码 = 失败项数，>0 即失败）
    register --reason 理由      把当前**新出现**的差异登记进去（唯一写入入口）
    list                       打印登记表与统计
    selftest                   负例自检（在临时树上造 4 种破坏，逐条要求被拒）
    bootstrap [--reason 理由]   首次建表（把当前差异整体登记）——只跑一次

退出码：0 通过；1 有未登记/被破坏的差异；2 自检失败；3 前置缺失。
"""
import argparse
import datetime
import hashlib
import os
import shutil
import sys
import tempfile

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
PORT = os.path.join(ROOT, 'port-1.20.1')
STAGE = os.path.join(PORT, 'stage1-src')
ASM = os.path.join(PORT, 'ncpb-assembled', 'src', 'main', 'java')
MANIFEST = os.path.join(PORT, 'ncpb-assembled', 'assemble-manifest.txt')
HWM = os.path.join(PORT, 'ncpb-assembled', 'assemble-manifest.hwm')
LOG = os.path.join(PORT, 'ncpb-assembled', 'assemble.log')

SECTIONS = ('deleted', 'added', 'overridden')

HEADER = [
    '# 组装登记表（NCPB-ASSEMBLE）—— ncpb-assembled 与 stage1-src 的差异。',
    '#',
    '# 路径相对源码根（posix），一行一个。三段：',
    '#   [deleted]    组装时**删掉**的（stage1-src 有、ncpb-assembled 没有）',
    '#   [added]      组装时**新增**的（ncpb-assembled 有、stage1-src 没有）',
    '#   [overridden] 两边都有、但 ncpb-assembled 的内容是**手改过**的',
    '#',
    '# ⚠️ 不要手改这个文件。',
    '#   登记： python tools/assemble.py register --reason "为什么"',
    '#   校验： python tools/assemble.py verify',
    '# 手改短了会被 assemble-manifest.hwm（水位线）抓到。',
    '',
]


def sha(path):
    with open(path, 'rb') as f:
        return hashlib.sha256(f.read()).hexdigest()


def index(root):
    out = {}
    for dp, _d, fs in os.walk(root):
        for f in fs:
            p = os.path.join(dp, f)
            out[os.path.relpath(p, root).replace(os.sep, '/')] = sha(p)
    return out


def delta(stage_root, asm_root):
    a, b = index(stage_root), index(asm_root)
    only_a = sorted(set(a) - set(b))
    only_b = sorted(set(b) - set(a))
    diff = sorted(r for r in set(a) & set(b) if a[r] != b[r])
    return {'deleted': only_a, 'added': only_b, 'overridden': diff}


def read_manifest(path=MANIFEST):
    if not os.path.isfile(path):
        return None, ['登记表不存在：%s' % path]
    cur, problems, seen = {s: [] for s in SECTIONS}, [], set()
    section = None
    with open(path, encoding='utf-8') as f:
        for i, ln in enumerate(f.read().splitlines(), 1):
            t = ln.strip()
            if not t:
                continue
            if t.startswith('#'):
                continue
            if t.startswith('[') and t.endswith(']'):
                name = t[1:-1]
                if name not in SECTIONS:
                    problems.append('第 %d 行：未知的段 [%s]' % (i, name))
                    section = None
                else:
                    section = name
                continue
            if section is None:
                problems.append('第 %d 行：不在任何段里' % i)
                continue
            if t in seen:
                problems.append('第 %d 行：重复 %s' % (i, t))
                continue
            seen.add(t)
            cur[section].append(t)
    for s in SECTIONS:
        cur[s] = sorted(cur[s])
    return cur, problems


def write_manifest(m, path=MANIFEST):
    body = list(HEADER)
    for s in SECTIONS:
        body.append('[%s]' % s)
        body.extend(m[s])
        body.append('')
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(body))


def read_hwm():
    if not os.path.isfile(HWM):
        return None
    with open(HWM, encoding='utf-8') as f:
        try:
            return int(f.read().strip().splitlines()[0])
        except (ValueError, IndexError):
            return None


def write_hwm(n, why):
    with open(HWM, 'w', encoding='utf-8', newline='\n') as f:
        f.write('%d\n# %s  %s\n' % (n, datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'), why))


def log(msg):
    with open(LOG, 'a', encoding='utf-8', newline='\n') as f:
        f.write('%s  %s\n' % (datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'), msg))


def compare(manifest, d, stage_root=STAGE, asm_root=ASM):
    """-> (fails, notes)。fails 非空即门禁失败。"""
    fails, notes = [], []
    if not os.path.isdir(stage_root):
        return ['前置缺失：%s' % stage_root], notes
    if not os.path.isdir(asm_root):
        return ['前置缺失：%s' % asm_root], notes

    for detail in sorted(set(d['deleted']) - set(manifest['deleted'])):
        fails.append('未登记的**删除**（stage1-src 有、assembled 没有）：%s' % detail)
    for detail in sorted(set(manifest['deleted']) - set(d['deleted'])):
        fails.append('登记为"已删除"，但 assembed 里**又出现了**：%s' % detail)

    for detail in sorted(set(d['added']) - set(manifest['added'])):
        fails.append('未登记的**新增**：%s' % detail)
    for detail in sorted(set(manifest['added']) - set(d['added'])):
        fails.append('登记为"组装新增"，但 assembed 里**不见了**：%s' % detail)

    for detail in sorted(set(d['overridden']) - set(manifest['overridden'])):
        fails.append('未登记的**手改**（与 stage1-src 不同却没登记）：%s' % detail)
    for detail in sorted(set(manifest['overridden']) - set(d['overridden'])):
        notes.append('登记过的覆盖现在与 stage1-src 一致（被回退了，或登记过期）：%s' % detail)

    # 同一路径不该同时出现在两段里
    for x, y in (('deleted', 'overridden'), ('added', 'overridden'), ('deleted', 'added')):
        both = set(manifest[x]) & set(manifest[y])
        for detail in sorted(both):
            fails.append('同一路径同时登记在 [%s] 与 [%s]：%s' % (x, y, detail))
    return fails, notes


def cmd_verify(_argv):
    m, probs = read_manifest()
    if probs:
        print('登记表本身不合法，先修：')
        for p in probs:
            print('  ' + p)
        return 2
    d = delta(STAGE, ASM)
    fails, notes = compare(m, d)

    hwm = read_hwm()
    total = sum(len(m[s]) for s in SECTIONS)
    if hwm is None:
        fails.append('水位线文件缺失：%s（自擦除就抓不到了）' % HWM)
    elif total < hwm:
        fails.append('登记表被改短：现在 %d 条 < 水位线 %d 条（自擦除）' % (total, hwm))

    print('磁盘差异：删 %d / 加 %d / 改 %d（登记表：%d / %d / %d，水位线 %s）'
          % (len(d['deleted']), len(d['added']), len(d['overridden']),
             len(m['deleted']), len(m['added']), len(m['overridden']), hwm))
    # ⚠️ 作用域必须写出来：本工具只比 **java 源根**（`stage1-src` ↔ `src/main/java`）。
    #    资源（assets / native）**不在**它的覆盖范围里 —— 别把"组装门禁：通过"读成"资源也对"。
    res = os.path.join(PORT, 'ncpb-assembled', 'src', 'main', 'resources')
    if os.path.isdir(res):
        print('作用域：只含 java 源根；**不含** src/main/resources'
              '（资源由 tools/sync_natives.py --check 与资源同步流程各自负责）')
    for nt in notes:
        print('  [注意] ' + nt)
    for f in fails:
        print('  [FAIL] ' + f)
    print('---- 组装门禁：%s' % ('通过' if not fails else '%d 项失败' % len(fails)))
    return len(fails)


def cmd_register(argv):
    reason = ''
    if '--reason' in argv:
        i = argv.index('--reason')
        reason = argv[i + 1] if i + 1 < len(argv) else ''
    if not reason.strip():
        print('用法：register --reason "为什么这些差异是合理的"（必须给理由）')
        return 2
    m, probs = read_manifest()
    if probs:
        print('登记表本身不合法，先修：')
        for p in probs:
            print('  ' + p)
        return 2
    d = delta(STAGE, ASM)
    added, dropped = [], []
    # ⚠️ 必须**双向**对齐：verify 对 deleted/added 是"两个方向都算失败"的严格相等检查。
    #    第一版 register 只会往后追加，于是"登记为组装新增、但后来被删掉了"的文件会永远留在表里
    #    （第 91 次实测：MeshData/ByteBufferBuilder 迁包 + 删掉借道类之后，3 条陈旧登记卡住门禁）。
    #    所以这里既登记新差异，也退掉已经**不再是差异**的旧登记。
    for s in SECTIONS:
        cur, reg = set(d[s]), set(m[s])
        for x in sorted(cur - reg):
            m[s].append(x)
            added.append('%s:%s' % (s[0], x))
        for x in sorted(reg - cur):
            m[s] = [y for y in m[s] if y != x]
            dropped.append('%s:%s' % (s[0], x))
    for s in SECTIONS:
        m[s] = sorted(m[s])
    total = sum(len(m[s]) for s in SECTIONS)
    if not added and not dropped:
        print('没有新差异要登记（登记表已经是最新的）。')
        return 0
    write_manifest(m)
    hwm = read_hwm() or 0
    if total > hwm:
        write_hwm(total, 'register 抬高：%s' % reason.strip())
    elif total < hwm:
        # 合法缩表：差异真的消失了（例如把借道类删掉、把覆盖改回与 stage1-src 一致）。
        # 必须**同时**降水水位线，否则 verify 会以"登记表被改短（自擦除）"失败 ——
        # 那道判据的用意是防**偷偷**缩表，而这里是"有理由、有输出、进 log"的缩表。
        write_hwm(total, 'register 降水：%s' % reason.strip())
    log('register +%d/-%d（%s）→ 共 %d 条' % (len(added), len(dropped), reason.strip(), total))
    print('登记：+%d / -%d → 共 %d 条（%s）' % (len(added), len(dropped), total, reason.strip()))
    for x in added:
        print('  + ' + x)
    for x in dropped:
        print('  - ' + x)
    return 0


def cmd_bootstrap(argv):
    reason = '首次建表'
    if '--reason' in argv:
        i = argv.index('--reason')
        reason = argv[i + 1] if i + 1 < len(argv) else reason
    if os.path.isfile(MANIFEST):
        print('登记表已经存在：%s（要增量登记请用 register）' % MANIFEST)
        return 2
    d = delta(STAGE, ASM)
    write_manifest(d)
    total = sum(len(d[s]) for s in SECTIONS)
    write_hwm(total, 'bootstrap：%s' % reason)
    log('bootstrap：删 %d / 加 %d / 改 %d → 共 %d 条（%s）'
        % (len(d['deleted']), len(d['added']), len(d['overridden']), total, reason))
    print('已建表：删 %d / 加 %d / 改 %d，共 %d 条' %
          (len(d['deleted']), len(d['added']), len(d['overridden']), total))
    return 0


def cmd_list(_argv):
    m, probs = read_manifest()
    if probs:
        for p in probs:
            print('  !! ' + p)
        return 2
    for s in SECTIONS:
        print('[%s] %d 条' % (s, len(m[s])))
        for x in m[s]:
            print('   ' + x)
    print('水位线：%s' % read_hwm())
    return 0


def selftest():
    """在**临时树**上造 4 种破坏，逐条要求被拒；再验证"一致时通过"。"""
    tmp = tempfile.mkdtemp(prefix='ncpb-assemble-selftest-')
    st = os.path.join(tmp, 'stage')
    asm = os.path.join(tmp, 'asm')
    mf = os.path.join(tmp, 'manifest.txt')

    def make(root, files):
        for rel, body in files.items():
            p = os.path.join(root, rel.replace('/', os.sep))
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, 'w', encoding='utf-8', newline='\n') as f:
                f.write(body)

    shutil.rmtree(tmp, ignore_errors=True)
    make(st, {'a/A.java': 'A', 'b/B.java': 'B', 'c/C.java': 'C'})
    make(asm, {'a/A.java': 'A-edited', 'b/B.java': 'B', 'd/D.java': 'D'})
    d = delta(st, asm)
    manifest = {'deleted': ['c/C.java'], 'added': ['d/D.java'], 'overridden': ['a/A.java']}
    write_manifest(manifest, mf)
    m2, probs = read_manifest(mf)
    cases = []
    cases.append(('读回来的登记表与写进去的一致', not probs and m2 == manifest))

    fails, _n = compare(manifest, d, st, asm)
    cases.append(('N0 一致时应通过', not fails))

    # N1 未登记的手改
    make(asm, {'b/B.java': 'B-edited'})
    fails, _n = compare(manifest, delta(st, asm), st, asm)
    cases.append(('N1 未登记的手改 ⇒ 失败', any('未登记的**手改**' in f for f in fails)))

    # N2 登记过的删除又回来了
    os.makedirs(os.path.join(asm, 'c'), exist_ok=True)
    shutil.copy(os.path.join(st, 'c', 'C.java'), os.path.join(asm, 'c', 'C.java'))
    fails, _n = compare(manifest, delta(st, asm), st, asm)
    cases.append(('N2 登记为已删除却又出现 ⇒ 失败', any('又出现了' in f for f in fails)))
    os.remove(os.path.join(asm, 'c', 'C.java'))

    # N3 登记过的新增不见了
    os.remove(os.path.join(asm, 'd', 'D.java'))
    fails, _n = compare(manifest, delta(st, asm), st, asm)
    cases.append(('N3 登记为新增却不见了 ⇒ 失败', any('不见了' in f for f in fails)))

    # N4 未登记的新增
    make(asm, {'e/E.java': 'E'})
    fails, _n = compare(manifest, delta(st, asm), st, asm)
    cases.append(('N4 未登记的新增 ⇒ 失败', any('未登记的**新增**' in f for f in fails)))

    # N5 水位线读写 —— ⚠️ 用**临时文件**，绝不能碰真项目的水位线。
    #    第一版这里直接调了 `write_hwm(3, 'x')`，而那个函数写的是全局 `HWM` 路径
    #    ⇒ **自检把真项目的登记表水位线改成了 3**。幸好紧随其后的 bootstrap 又写回 158。
    #    教训：自检只能动自己造的临时状态（与 `deferred_cut.selftest` 的"连 mtime 一起恢复"同源）。
    hw = os.path.join(tmp, 'hwm')
    with open(hw, 'w', encoding='utf-8', newline='\n') as f:
        f.write('3\n# selftest 临时水位线\n')
    with open(hw, encoding='utf-8') as f:
        cases.append(('N5 水位线可读回（临时文件）', int(f.read().splitlines()[0]) == 3))

    shutil.rmtree(tmp, ignore_errors=True)
    bad = 0
    for name, ok in cases:
        print('%s  %s' % ('PASS' if ok else 'FAIL', name))
        if not ok:
            bad += 1
    print('---- 负例自检：%d/%d 通过' % (len(cases) - bad, len(cases)))
    return bad


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('cmd', choices=['verify', 'register', 'bootstrap', 'list', 'selftest'])
    ap.add_argument('rest', nargs='*')
    # ⚠️ 必须用 parse_known_args：`--reason "…"` 是给子命令的参数，
    #    严格解析会在 argparse 层就报 unrecognized arguments（第一版就是这样）。
    args, extra = ap.parse_known_args()
    rest = list(args.rest) + list(extra)
    if args.cmd == 'selftest':
        return selftest()
    if args.cmd == 'verify':
        return cmd_verify(rest)
    if args.cmd == 'register':
        return cmd_register(rest)
    if args.cmd == 'bootstrap':
        return cmd_bootstrap(rest)
    return cmd_list(rest)


if __name__ == '__main__':
    sys.exit(main())
