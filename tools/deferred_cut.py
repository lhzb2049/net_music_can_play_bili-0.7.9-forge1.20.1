#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""未完成层清单（deferred cut）的**唯一**读写入口。

## 为什么要外置

第 86 次发现：`build.gradle` 里那段 `NCPB-DEFERRED-CUT` 被脚本反复**整体重写**，
每轮"拿当前日志重算一遍清单"就会把上一轮已经写进去的条目悄悄丢掉（自擦除）。
更糟的是**丢掉了看不出来**：清单变短，构建照样往下跑，甚至可能照样"绿"。

所以清单搬进 `ncpb-assembled/deferred-excludes.txt`（一行一个路径，相对
`src/main/java`，`#` 开头是注释），并且定下三条规矩：

  * 增条目 = `add` —— 自动抬高**水位线**（hwm 文件）；
  * 删条目 = `remove` —— **必须给理由**，会重写水位线。删是"决定"，不是"事故"；
  * `check` —— 用水位线抓"清单被手改短了"，并逐条验证 exclude 不是空转。

## 子命令

    list                          打印当前清单
    add <路径...>                 追加（去重、校验存在、抬水位线）
    remove <路径...> --reason 理由  删除（重写水位线，记流水）
    check                         门禁（退出码 = 失败的门数）
    selftest                      负例自检：故意破坏清单，每道门都必须说"不"
    import-gradle                 把 build.gradle 里现存的 inline exclude 迁进清单
    from-baseline <原始日志>       从"无 exclude 基线"日志里挑候选文件并追加

`from-baseline` 只在**无 exclude 的基线日志**上才有意义：被 exclude 的文件不会再
出现在构建日志里，用**当前**日志重算清单是自相矛盾的（第 78 次踩过）。
"""
import os
import re
import sys
import datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJ = os.path.join(ROOT, 'ncpb-assembled')
SRC = os.path.join(PROJ, 'src', 'main', 'java')
LIST = os.path.join(PROJ, 'deferred-excludes.txt')
HWM = os.path.join(PROJ, 'deferred-excludes.hwm')
CHLOG = os.path.join(PROJ, 'deferred-excludes.log')
GRADLE = os.path.join(PROJ, 'build.gradle')
PREFIX = 'com/zhongbai233/net_music_can_play_bili/'
JAVA_SUFFIX = '.java'

# 只有这些树里的文件才允许进清单。加别的树要先改这里，逼一次显式决定
# （否则清单会慢慢变成"什么都排掉"，而排掉本身是零和的）。
#   port/shim/ client/terrain/ client/renderer/ client/debug/ ：延后到 Milestone B 的渲染/视频层
#   mixin/ ：延后的 Mixin（另外 19 个已整体移到 ncpb-assembled/_deferred-mixins/）
TREES = ('port/shim/', 'client/terrain/', 'client/renderer/', 'client/debug/', 'mixin/')

HEADER = [
    '# 未完成层清单（NCPB-DEFERRED-CUT）—— 生效中的 exclude 条目，一行一个。',
    '#',
    '# 路径相对 ncpb-assembled/src/main/java。`#` 开头是注释，空行忽略。',
    '#',
    '# ⚠️ 不要手改这个文件。',
    '#   增： python tools/deferred_cut.py add <路径...>',
    '#   删： python tools/deferred_cut.py remove <路径...> --reason "为什么可以放回来"',
    '#   查： python tools/deferred_cut.py check',
    '# 手改短了会被 deferred-excludes.hwm（水位线）抓到。',
    '#',
    '# 这些文件不是"不存在"，是"还没移植完"：删掉对应行即可让它们重新参与编译，',
    '# 但各自的 1.21→1.20.1 API 缺口要先解决。清单本身也是 Milestone B 的待办表。',
    '',
]


# ---------- 读写 ----------

def _norm(p):
    """把任意写法的路径规范化成相对 src/main/java 的 posix 相对路径。"""
    p = p.strip().strip('"').strip("'").replace('\\', '/')
    if not p:
        return ''
    if 'src/main/java/' in p:
        p = p.split('src/main/java/', 1)[1]
    if p.startswith('./'):
        p = p[2:]
    if p.startswith('/'):
        p = p.lstrip('/')
    return p


def read_entries():
    """返回 (entries, problems)。problems 非空表示清单本身不合法。"""
    if not os.path.isfile(LIST):
        return [], ['清单文件不存在：%s' % os.path.relpath(LIST, ROOT)]
    with open(LIST, encoding='utf-8') as f:
        raw = f.read().splitlines()
    entries, problems, seen = [], [], set()
    for i, ln in enumerate(raw, 1):
        t = ln.strip()
        if not t or t.startswith('#'):
            continue
        p = _norm(t)
        if p != t:
            problems.append('第 %d 行写法不规范（应为相对 src/main/java 的 posix 路径）：%s' % (i, t))
        if p.startswith('..') or re.match(r'^[A-Za-z]:', p):
            problems.append('第 %d 行越出了源码根：%s' % (i, t))
            continue
        if not any(p.startswith(PREFIX + t2) for t2 in TREES):
            problems.append('第 %d 行不在允许的延后树里（TREES）：%s' % (i, p))
        if p in seen:
            problems.append('第 %d 行重复：%s' % (i, p))
            continue
        seen.add(p)
        entries.append(p)
    return entries, problems


def write_entries(entries):
    body = sorted(set(entries))
    with open(LIST, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(HEADER + body) + '\n')
    return body


def read_hwm():
    if not os.path.isfile(HWM):
        return None
    with open(HWM, encoding='utf-8') as f:
        t = f.read().strip()
    try:
        return int(t.splitlines()[0])
    except (ValueError, IndexError):
        return None


def write_hwm(n, why):
    with open(HWM, 'w', encoding='utf-8', newline='\n') as f:
        f.write('%d\n' % n)
        f.write('# %s  %s\n' % (datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'), why))


def log_line(msg):
    with open(CHLOG, 'a', encoding='utf-8', newline='\n') as f:
        f.write('%s  %s\n' % (datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'), msg))


# ---------- 子命令 ----------

def cmd_list(_argv):
    entries, problems = read_entries()
    hwm = read_hwm()
    print('清单：%s' % os.path.relpath(LIST, ROOT))
    print('条目：%d 个   水位线：%s' % (len(entries), hwm if hwm is not None else '(缺)'))
    for e in entries:
        print('  ' + e)
    for p in problems:
        print('  !! ' + p)
    return 0


def cmd_add(argv):
    if not argv:
        print('用法：add <路径...>')
        return 2
    entries, problems = read_entries()
    # 首次迁移时清单还不存在，这不算"不合法"
    problems = [p for p in problems if not p.startswith('清单文件不存在')]
    if problems:
        print('清单本身不合法，先修：')
        for p in problems:
            print('  ' + p)
        return 2
    added, skipped = [], []
    for a in argv:
        p = _norm(a)
        if not p:
            continue
        if not p.startswith(PREFIX) or not p.endswith(JAVA_SUFFIX):
            print('拒收（不是本模组的 .java 源码路径）：%s' % a)
            return 2
        if not any(p.startswith(PREFIX + t) for t in TREES):
            print('拒收（不在允许的延后树里 %s）：%s' % (', '.join(TREES), p))
            return 2
        if not os.path.isfile(os.path.join(SRC, p.replace('/', os.sep))):
            print('拒收（磁盘上没有这个文件，exclude 会变成空转）：%s' % p)
            return 2
        if p in entries:
            skipped.append(p)
            continue
        entries.append(p)
        added.append(p)
    body = write_entries(entries)
    hwm = read_hwm() or 0
    if len(body) > hwm:
        write_hwm(len(body), 'add 抬高')
    log_line('add %d 条（新增 %s）→ 共 %d 条' % (len(argv), ', '.join(added) or '无', len(body)))
    print('新增 %d 条，已在清单 %d 条，跳过重复 %d 条。' % (len(added), len(body), len(skipped)))
    for p in added:
        print('  + ' + p)
    return 0


def cmd_remove(argv):
    reason = ''
    args = []
    i = 0
    while i < len(argv):
        if argv[i] == '--reason':
            i += 1
            reason = argv[i] if i < len(argv) else ''
        else:
            args.append(argv[i])
        i += 1
    if not args or not reason.strip():
        print('用法：remove <路径...> --reason "为什么可以放回来"')
        print('（必须给理由：删除是决定，不是事故。理由会记进 deferred-excludes.log）')
        return 2
    entries, problems = read_entries()
    if problems:
        print('清单本身不合法，先修：')
        for p in problems:
            print('  ' + p)
        return 2
    gone = []
    for a in args:
        p = _norm(a)
        if p not in entries:
            print('清单里没有这一条：%s' % p)
            return 2
        entries.remove(p)
        gone.append(p)
    body = write_entries(entries)
    write_hwm(len(body), 'remove 重写：%s' % reason.strip())
    log_line('remove %d 条（理由：%s）→ 共 %d 条' % (len(gone), reason.strip(), len(body)))
    print('已移除 %d 条，清单剩 %d 条，水位线已重写。' % (len(gone), len(body)))
    for p in gone:
        print('  - ' + p)
    return 0


def cmd_import_gradle(_argv):
    """把 build.gradle 里现存的 inline exclude 迁进清单（一次性迁移用）。"""
    if not os.path.isfile(GRADLE):
        print('找不到 %s' % GRADLE)
        return 2
    text = open(GRADLE, encoding='utf-8').read()
    # 先取配置期读清单的那段已经加过没？没加也不影响迁移。
    found = re.findall(r"exclude\s+'([^']+)'", text)
    found = [f for f in found if 'net_music_can_play_bili' in f]
    if not found:
        print('build.gradle 里没有 inline exclude，无需迁移。')
        return 0
    print('从 build.gradle 迁出 %d 条：' % len(found))
    for f in found:
        print('  ' + f)
    rc = cmd_add(found)
    if rc == 0:
        log_line('import-gradle 从 build.gradle 迁入 %d 条' % len(found))
    return rc


def cmd_from_baseline(argv):
    if len(argv) != 1:
        print('用法：from-baseline <无 exclude 的原始构建日志>')
        return 2
    raw = argv[0]
    if not os.path.isabs(raw):
        raw = os.path.join(ROOT, raw)
    if not os.path.isfile(raw):
        print('前置缺失：%s' % raw)
        return 3
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    try:
        import gradle_ledger as G
    except ImportError:
        print('需要 tools/gradle_ledger.py 一起用')
        return 2
    text, _enc = G.load(raw)
    bs = chr(92)
    bad = set()
    for ln in text.splitlines():
        m = G.DIAG_RE.match(ln.strip())
        if not m or m.group('label') != G.ERR_LABEL:
            continue
        p = m.group('path').replace(bs, '/')
        if PREFIX not in p:
            continue
        rel = p.split('net_music_can_play_bili/')[-1]
        if any(rel.startswith(t) for t in TREES):
            bad.add(rel)
    if not bad:
        print('这个日志里没有落在延后树里的报错文件（前置日志可能不是"无 exclude 基线"）')
        return 2
    print('从基线日志挑出 %d 个候选：' % len(bad))
    return cmd_add(sorted(bad))


def cmd_check(_argv):
    """门禁。退出码 = 失败的门数。每道门都必须能说"不"。"""
    fails = []
    warns = []
    entries, problems = read_entries()

    # G1 清单存在且合法
    if not os.path.isfile(LIST):
        fails.append('G1 清单文件不存在：%s' % os.path.relpath(LIST, ROOT))
    elif problems:
        fails.append('G1 清单内容不合法：%s' % '; '.join(problems))
    else:
        print('G1 PASS 清单存在且 %d 条都能解析' % len(entries))

    # G2 每条都在磁盘上有对应文件（否则这条 exclude 是空转，静默失效）
    stale = [e for e in entries if not os.path.isfile(os.path.join(SRC, e.replace('/', os.sep)))]
    if stale:
        fails.append('G2 有 %d 条指向不存在的文件（exclude 空转）：%s' % (len(stale), ', '.join(stale[:5])))
    else:
        print('G2 PASS %d 条 exclude 都命中真实文件' % len(entries))

    # G3 build.gradle 只通过清单文件排除，不再有 inline exclude
    gtext = open(GRADLE, encoding='utf-8').read() if os.path.isfile(GRADLE) else ''
    if 'deferred-excludes.txt' not in gtext:
        fails.append('G3 build.gradle 没有读 deferred-excludes.txt —— 清单外置没接上')
    inline = re.findall(r"exclude\s+'([^']*net_music_can_play_bili[^']*)'", gtext)
    if inline:
        fails.append("G3 build.gradle 里还有 %d 条 inline exclude（会被整体重写时丢掉）：%s"
                     % (len(inline), inline[0]))
    if 'deferred-excludes.txt' in gtext and not inline:
        print('G3 PASS build.gradle 只从清单文件取排除项，无 inline 条目')

    # G4 水位线：清单不能比上次记录时更短
    hwm = read_hwm()
    if hwm is None:
        fails.append('G4 水位线文件缺失：%s' % os.path.relpath(HWM, ROOT))
    elif len(entries) < hwm:
        fails.append('G4 清单被改短了：现在 %d 条 < 水位线 %d 条（自擦除）。'
                     '要么恢复条目，要么用 remove --reason 显式承认' % (len(entries), hwm))
    else:
        print('G4 PASS 清单 %d 条 >= 水位线 %d 条' % (len(entries), hwm))

    # G5 上一次构建日志里"生效条数"是否与清单一致（证据：清单真的到了编译器）
    # NCPB_CUT_LOG：显式指定用哪份日志（自检和多工程串用时要）。指定时不做新旧比对。
    envlog = os.environ.get('NCPB_CUT_LOG')
    agecheck = not envlog
    if envlog:
        cand = [envlog if os.path.isabs(envlog) else os.path.join(ROOT, envlog)]
    else:
        cand = [os.path.join(ROOT, '_port-compile', n)
                for n in ('gradle-full.txt', 'gradle-clean.txt', 'gradle-clean2.txt')]
    logcand = [p for p in cand if os.path.isfile(p)]
    if not logcand:
        fails.append('G5 找不到构建日志（_port-compile/gradle-{full,clean,clean2}.txt）—— '
                     '无法证明清单到达了编译器。跑：powershell -File tools\\build_full.ps1')
    else:
        newest = max(logcand, key=os.path.getmtime)
        # 清单文件可能刚好不存在（G1 已经报过了），这里不能因为 getmtime 崩掉 —— 门禁
        # 必须能**报出**问题，而不是自己抛异常（自检的 N1 就是拿这个抓出来的）。
        ref = max([os.path.getmtime(p) for p in (LIST, GRADLE) if os.path.isfile(p)] or [0])
        if agecheck and os.path.getmtime(newest) < ref:
            fails.append('G5 日志 %s 比清单/build.gradle 还旧 —— 这份日志不能证明**当前**清单生效。'
                         '跑：powershell -File tools\\build_full.ps1' % os.path.basename(newest))
        else:
            try:
                sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
                import gradle_ledger as G
                ltext, _e = G.load(newest)
            except Exception:
                ltext = open(newest, encoding='utf-8', errors='replace').read()
            m = re.findall(r'NCPB-DEFERRED-CUT: active excludes = (\d+)', ltext)
            if not m:
                fails.append('G5 日志 %s 里没有 "NCPB-DEFERRED-CUT: active excludes = N" —— '
                             '无法证明清单到了编译器（构建可能没跑成）' % os.path.basename(newest))
            elif int(m[-1]) != len(entries):
                fails.append('G5 日志说生效 %s 条，清单是 %d 条 —— 不一致' % (m[-1], len(entries)))
            else:
                print('G5 PASS 日志 %s 里生效 %s 条 == 清单 %d 条'
                      % (os.path.basename(newest), m[-1], len(entries)))

    for w in warns:
        print(w)
    for f in fails:
        print('FAIL ' + f)
    print('---- 门禁结果：%d 通过 / %d 失败' % (5 - len(fails) - len(warns), len(fails)))
    return len(fails)


def cmd_selftest(_argv):
    """负例自检：故意把清单/脚本弄坏，**每一道门都必须说"不"**。

    门的价值在于它能拒绝，不在于它平时是绿的。这个子命令就是用来证明前者的：
    每做一次破坏，就跑一遍 cmd_check，要求 (a) 退出码 > 0，(b) 输出里出现**那道门**
    的编号。破坏完一律从内存备份恢复（含异常路径），最后确认恢复正常。
    """
    import io
    import tempfile
    import contextlib

    if not os.path.isfile(LIST) or not os.path.isfile(HWM) or not os.path.isfile(GRADLE):
        print('前置缺失：清单/水位线/build.gradle 不齐，先别自检')
        return 3

    def snapshot():
        out = {}
        for p in (LIST, HWM, GRADLE):
            st = os.stat(p)
            with open(p, 'rb') as f:
                out[p] = (f.read(), st.st_atime, st.st_mtime)
        return out

    backup = snapshot()
    entries, _ = read_entries()
    n = len(entries)
    tmpdir = tempfile.mkdtemp(prefix='ncpb-cut-selftest-')
    results = []

    def run_check(env=None):
        buf = io.StringIO()
        old = {k: os.environ.get(k) for k in ('NCPB_CUT_LOG',)}
        if env:
            os.environ.update(env)
        try:
            with contextlib.redirect_stdout(buf):
                rc = cmd_check([])
        finally:
            for k, v in old.items():
                if v is None:
                    os.environ.pop(k, None)
                else:
                    os.environ[k] = v
        return rc, buf.getvalue()

    def case(name, gate, expect_fail=True):
        rc, out = run_check()
        ok = (rc > 0 and gate in out) if expect_fail else (rc == 0)
        results.append((name, ok, gate, rc, out))
        return ok

    def restore():
        # ⚠️ **连 mtime 一起恢复**：G5 判的是"构建日志是否比清单新"。自检会把清单/脚本
        # 写一遍，如果只还内容不还时间戳，自检跑完 check 就会因为"日志变旧了"而假失败
        # —— 那是自检自己制造的假象，不是真问题。
        for p, (b, at, mt) in backup.items():
            with open(p, 'wb') as f:
                f.write(b)
            os.utime(p, (at, mt))

    try:
        # N1 / G1：清单文件不存在
        os.remove(LIST)
        case('清单被删掉', 'G1', True)
        restore()

        # N2 / G2：清单里有一条磁盘上没有的路径（exclude 会静默空转）
        body = [l for l in backup[LIST][0].decode('utf-8').splitlines() if l.strip() and not l.startswith('#')]
        body.append(PREFIX + 'client/renderer/DoesNotExist.java')
        with open(LIST, 'w', encoding='utf-8', newline='\n') as f:
            f.write('\n'.join(body) + '\n')
        with open(HWM, 'w', encoding='utf-8', newline='\n') as f:
            f.write('%d\n' % (n + 1))   # 让 G4 闭嘴，单看 G2
        case('清单里有不存在的文件（空转 exclude）', 'G2', True)
        restore()

        # N3 / G3：build.gradle 不再读清单文件（外置没接上）
        g = backup[GRADLE][0].decode('utf-8').replace('deferred-excludes.txt', 'not-the-list.txt')
        with open(GRADLE, 'w', encoding='utf-8', newline='\n') as f:
            f.write(g)
        case('build.gradle 不再读清单', 'G3', True)
        restore()

        # N3b / G3：inline exclude 又回来了
        g = backup[GRADLE][0].decode('utf-8').rstrip() + \
            "\nsourceSets { main { java { exclude '%sclient/renderer/Old.java' } } }\n" % PREFIX
        with open(GRADLE, 'w', encoding='utf-8', newline='\n') as f:
            f.write(g)
        case('build.gradle 里又出现 inline exclude', 'G3', True)
        restore()

        # N4 / G4：**自擦除** —— 清单被手改短了两条，水位线没动
        body = [l for l in backup[LIST][0].decode('utf-8').splitlines() if l.strip() and not l.startswith('#')]
        with open(LIST, 'w', encoding='utf-8', newline='\n') as f:
            f.write('\n'.join(body[:-2]) + '\n')
        case('清单被手改短（自擦除）', 'G4', True)
        restore()

        # N5 / G4：水位线文件被删
        os.remove(HWM)
        case('水位线文件被删', 'G4', True)
        restore()

        # N6 / G5：日志说生效 999 条，与清单不符
        p = os.path.join(tmpdir, 'fake-log.txt')
        with open(p, 'w', encoding='utf-8') as f:
            f.write('> Task :compileJava\nNCPB-DEFERRED-CUT: active excludes = 999 (deferred-excludes.txt)\n')
        rc, out = run_check({'NCPB_CUT_LOG': p})
        ok = rc > 0 and 'G5' in out
        results.append(('日志条数与清单不一致', ok, 'G5', rc, out))

        # N7 / G5：日志里根本没有那行（构建没跑成）
        with open(p, 'w', encoding='utf-8') as f:
            f.write('> Task :compileJava FAILED\nBUILD FAILED\n')
        rc, out = run_check({'NCPB_CUT_LOG': p})
        ok = rc > 0 and 'G5' in out
        results.append(('日志里没有生效条数那行', ok, 'G5', rc, out))

        # N8 / G5 正例：日志条数与清单一致 ⇒ 这几道门都该闭嘴
        with open(p, 'w', encoding='utf-8') as f:
            f.write('> Task :compileJava\nNCPB-DEFERRED-CUT: active excludes = %d (deferred-excludes.txt)\n' % n)
        rc, out = run_check({'NCPB_CUT_LOG': p})
        results.append(('日志条数与清单一致（正例）', rc == 0, 'G5', rc, out))
    finally:
        restore()
        for f2 in os.listdir(tmpdir):
            os.remove(os.path.join(tmpdir, f2))
        os.rmdir(tmpdir)

    for name, ok, gate, rc, out in results:
        print('%s  [%s] %s（退出码 %d）' % ('PASS' if ok else 'FAIL', gate, name, rc))
        if not ok:
            for ln in out.splitlines():
                print('      | ' + ln)
    # 收尾：破坏全部恢复后必须干净（mtime 也恢复了，所以 G5 不该因为自检而假失败）
    rc, out = run_check()
    print('恢复后复跑 check：退出码 %d（应为 0；不为 0 说明自检没把自己恢复干净）' % rc)
    if rc != 0:
        for ln in out.splitlines():
            print('      | ' + ln)
    bad = [r for r in results if not r[1]]
    print('---- 负例自检：%d/%d 道门拒绝了伪造的破坏' % (len(results) - len(bad), len(results)))
    return len(bad)


USAGE = __doc__


def main(argv):
    if len(argv) < 2:
        print(USAGE)
        return 2
    cmd, rest = argv[1], argv[2:]
    table = {'list': cmd_list, 'add': cmd_add, 'remove': cmd_remove, 'check': cmd_check,
             'import-gradle': cmd_import_gradle, 'from-baseline': cmd_from_baseline,
             'selftest': cmd_selftest}
    if cmd not in table:
        print('未知子命令：%s' % cmd)
        print(USAGE)
        return 2
    return table[cmd](rest)


if __name__ == '__main__':
    sys.exit(main(sys.argv))
