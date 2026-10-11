#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""真实 ForgeGradle 构建的**错误台账**（把"错误"和"警告"分开）。

## 为什么需要它

gradle 的输出在 Windows 控制台上是 **GBK**，直接经 PowerShell 管道读会变成 `????`，
于是**标签丢失** —— 我第 32 次就把"22 个警告"读成了"22 个错误"，凭空多算了 22 条。
所以：**所有按中文标签做的判断都收在本文件里**（它按 GBK 解码），
PowerShell 那一侧只读本文件输出的**ASCII 机器行**：

    LEDGER errors=<N> warnings=<M> error_files=<K>

## 截断（"javac 提前中止"）怎么判 —— 以及为什么不能靠猜

第 84/85/86 次反复栽在这上面：**只要还剩错误，javac 有时会少报**
（实测把 149 条报成 1 条、只落在 1 个文件里）。那种情况下"错误 1 条"不是工作量，
是中止点的位置。

⚠️ 但"错误只出现在 1 个文件"**不等于**截断：本工程真的出现过
"最后一处错误就在 1 个文件里"（`VideoBillboardQuadSupport`、`EditorSession`）——
那时它是**真实读数**。所以本工具**不把这个启发式当判决**：

* 有 `--canary <必须报错的文件>`：缺席即 **exit 2**（这是硬判据，判据不足时用它）；
* 没给 canary：只打印 `[可疑]` 提示，并明说"判据不足、这个数不能当进度"，
  **不**伪造一个"不可用"的退出码（假失败的门禁会被忽略，比没有更糟）。

退出码：0 正常；1 = 有错误但读数可用；2 = **总数不可用**（语法类错误 / canary 缺席 / 账本没给出机器行）；
3 前置缺失。

用法：
    python tools/gradle_ledger.py                      # 汇总
    python tools/gradle_ledger.py --top 15             # 最"烂"的文件
    python tools/gradle_ledger.py --msgs               # 错误消息归并（找同源）
    python tools/gradle_ledger.py --warnings           # 只看警告
    python tools/gradle_ledger.py --canary block/ControlConsoleBlock.java
    python tools/gradle_ledger.py --selftest           # 负例：合成日志，验证各判据会说"不"
"""
import argparse
import collections
import os
import re
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
DEFAULT_RAW = os.path.join(ROOT, 'port-1.20.1', '_port-compile', 'gradle-raw.txt')

ERR_LABEL = '错误'
WARN_LABEL = '警告'
DIAG_RE = re.compile(r'^(?P<path>[A-Za-z]:\\[^:]+\.java):(?P<line>\d+):\s*(?P<label>[^:]{1,8}):\s*(?P<msg>.*)$')

SYNTAX_MARKERS = ('需要 class', '需要 interface', '需要 enum', '需要 record',
                  'class, interface, enum, or record expected', "';' expected", "'{' expected")


def load(path):
    raw = open(path, 'rb').read()
    for enc in ('gbk', 'utf-8'):
        try:
            return raw.decode(enc), enc
        except UnicodeDecodeError:
            continue
    return raw.decode('utf-8', 'replace'), 'utf-8/replace'


def parse(text):
    errors, warnings, other = [], [], collections.Counter()
    for ln in text.splitlines():
        m = DIAG_RE.match(ln.strip())
        if not m:
            continue
        item = (m.group('path'), int(m.group('line')), m.group('msg').strip())
        if m.group('label') == ERR_LABEL:
            errors.append(item)
        elif m.group('label') == WARN_LABEL:
            warnings.append(item)
        else:
            other[m.group('label')] += 1
    return errors, warnings, other


def machine_line(errors, warnings):
    efiles = len(set(e[0] for e in errors))
    return 'LEDGER errors=%d warnings=%d error_files=%d' % (len(errors), len(warnings), efiles)


def judge(errors, warnings, canaries):
    """-> (退出码, 要打印的判据段落)。judge 与 main 分开，--selftest 直接用。"""
    out = []
    efiles = sorted(set(e[0] for e in errors))
    syntax = [e for e in errors if any(s in e[2] for s in SYNTAX_MARKERS)]
    rc = 1 if errors else 0

    if syntax:
        out.append('!! 有 %d 条**语法类**错误 —— javac 会在语法错误后提前停止，' % len(syntax))
        out.append('!! 因此上面的总数**不可用**（会假性偏小）。先修语法，再看总数。')
        for f, l, msg in syntax[:5]:
            out.append('   %s:%s  %s' % (os.path.basename(f), l, msg[:70]))
        rc = 2

    if canaries:
        miss = [c for c in canaries
                if not any(c.replace('/', os.sep) in e[0] or c in e[0] for e in errors)]
        if miss:
            out.append('!! 金丝雀缺失：%s —— 它在编译范围内却**没有**报错，' % ', '.join(miss))
            out.append('!! 说明 javac 提前中止了：**总数被截断、不可用**。')
            rc = 2
        else:
            out.append('金丝雀齐了（%s）⇒ 这次读数不是截断。' % ', '.join(canaries))

    if rc != 2 and errors and len(efiles) <= 1:
        out.append('!! [可疑] %d 条错误只出现在 1 个文件（%s）—— 这**可能**是 javac 提前中止。'
                   % (len(errors), os.path.basename(efiles[0])))
        out.append('!! 判据不足：这**也可能**是真实的最后一处错误（本工程两种情况都出现过）。')
        out.append('!! 想把它判成硬失败就加 `--canary <必须报错的文件>`；'
                   '或者改一处代码再跑一遍，看数字是否响应。')
        out.append('!! 在此之前，请**不要**把这个数当进度。')
    return rc, out


def analysis(text, stage, top, msgs_flag, warnings_flag):
    errors, warnings, other = parse(text)
    print('错误 %d 条' % len(errors))
    print('警告 %d 条' % len(warnings))
    for k, v in other.most_common():
        print('  其它标签 %r %d 条' % (k, v))

    if warnings_flag:
        print('\n--- 警告按文件 ---')
        for f, n in collections.Counter(w[0] for w in warnings).most_common(top):
            print('  %-4d %s' % (n, f.replace(stage, '').lstrip('\\')))
        return errors, warnings

    per_file = collections.Counter(e[0] for e in errors)
    per_pkg = collections.Counter()
    for f in per_file:
        rel = f.replace(stage, '').lstrip('\\')
        parts = rel.split('\\')
        per_pkg[parts[0] if len(parts) > 1 else '(root)'] += per_file[f]

    print('\n--- 错误按包 ---')
    for k, v in per_pkg.most_common(top):
        print('  %-4d %s' % (v, k))
    print('\n--- 错误最多的文件 ---')
    for f, n in per_file.most_common(top):
        print('  %-4d %s' % (n, f.replace(stage, '').lstrip('\\')))

    if msgs_flag:
        print('\n--- 错误消息归并（找同源）---')
        norm = collections.Counter()
        for _f, _l, msg in errors:
            norm[re.sub(r'[\w.$]+', 'X', msg)] += 1
        for k, v in norm.most_common(top):
            print('  %-4d %s' % (v, k[:110]))
    return errors, warnings


def selftest():
    """负例：合成 GBK 日志，逐条要求对应判据说"不"。"""
    import tempfile
    tmp = tempfile.mkdtemp(prefix='ncpb-ledger-selftest-')
    cases = []

    def w(name, lines):
        p = os.path.join(tmp, name)
        open(p, 'wb').write(('\n'.join(lines) + '\n').encode('gbk'))
        return p

    def diag(path, line, label, msg):
        return r'D:\x\src\main\java\%s:%d: %s: %s' % (path, line, label, msg)

    # N1 语法类 ⇒ 不可用（exit 2）
    p = w('syntax.txt', [diag('a\\A.java', 3, '错误', "需要 class, interface 或 enum")])
    e, wn, _ = parse(load(p)[0])
    rc, out = judge(e, wn, [])
    cases.append(('N1 语法类错误 ⇒ exit 2 且点名', rc == 2 and any('语法类' in x for x in out)))

    # N2 一条错误落在一个文件（无 canary）⇒ 提示"可疑"，但**不**伪造 exit 2
    p = w('one.txt', [diag('a\\A.java', 3, '错误', '找不到符号')])
    e, wn, _ = parse(load(p)[0])
    rc, out = judge(e, wn, [])
    cases.append(('N2 单文件单错误 ⇒ 只提示可疑（exit 仍 1）',
                  rc == 1 and any('可疑' in x for x in out)))

    # N2b 同一条，但另外两个文件只有**警告** ⇒ 判据不能因为警告而改变
    p = w('one_plus_warnings.txt', [diag('a\\A.java', 3, '错误', '找不到符号'),
                                    diag('b\\B.java', 9, '警告', 'Mixin target ...'),
                                    diag('c\\C.java', 11, '警告', 'Unable to locate obfuscation mapping')])
    e, wn, _ = parse(load(p)[0])
    rc, out = judge(e, wn, [])
    cases.append(('N2b 警告不能抬高"错误文件数"（本轮修掉的那个 bug）',
                  rc == 1 and any('可疑' in x for x in out)))

    # N3 声明了 canary 但它缺席 ⇒ 硬失败
    p = w('canary_missing.txt', [diag('a\\A.java', 3, '错误', '找不到符号')])
    e, wn, _ = parse(load(p)[0])
    rc, out = judge(e, wn, ['block\\ControlConsoleBlock.java'])
    cases.append(('N3 canary 缺席 ⇒ exit 2', rc == 2 and any('金丝雀缺失' in x for x in out)))

    # N4 canary 在场 ⇒ 不报截断
    p = w('canary_ok.txt', [diag('block\\ControlConsoleBlock.java', 32, '错误', '找不到符号'),
                            diag('a\\A.java', 3, '错误', '找不到符号')])
    e, wn, _ = parse(load(p)[0])
    rc, out = judge(e, wn, ['block\\ControlConsoleBlock.java'])
    cases.append(('N4 canary 在场 ⇒ 不报截断且 exit 1', rc == 1 and any('金丝雀齐了' in x for x in out)))

    # N5 多文件错误 ⇒ 不该有任何截断提示
    p = w('multi.txt', [diag('a\\A.java', 3, '错误', '找不到符号'),
                        diag('b\\B.java', 4, '错误', '找不到符号'),
                        diag('b\\B.java', 5, '错误', '找不到符号')])
    e, wn, _ = parse(load(p)[0])
    rc, out = judge(e, wn, [])
    cases.append(('N5 多文件 ⇒ 无截断提示', rc == 1 and not any('可疑' in x or '金丝雀' in x for x in out)))

    # N6 机器行可被 ASCII 解析（build_full.ps1 靠它）
    p = w('multi.txt', [diag('a\\A.java', 3, '错误', '找不到符号'), diag('b\\B.java', 4, '警告', 'x')])
    e, wn, _ = parse(load(p)[0])
    ml = machine_line(e, wn)
    cases.append(('N6 机器行 ASCII 且数字正确',
                  ml == 'LEDGER errors=1 warnings=1 error_files=1'))

    # N7 GBK 解码（中文标签本身要能读出来）
    p = w('gbk.txt', [diag('a\\A.java', 3, '错误', '找不到符号：中文消息')])
    txt, enc = load(p)
    e, wn, _ = parse(txt)
    cases.append(('N7 GBK 中文标签与消息都能解析', enc == 'gbk' and len(e) == 1 and '中文消息' in e[0][2]))

    for f in os.listdir(tmp):
        os.remove(os.path.join(tmp, f))
    os.rmdir(tmp)

    bad = 0
    for name, ok in cases:
        print('%s  %s' % ('PASS' if ok else 'FAIL', name))
        if not ok:
            bad += 1
    print('---- 负例自检：%d/%d 通过' % (len(cases) - bad, len(cases)))
    return bad


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--raw', default=DEFAULT_RAW)
    ap.add_argument('--top', type=int, default=15)
    ap.add_argument('--msgs', action='store_true')
    ap.add_argument('--warnings', action='store_true')
    ap.add_argument('--canary', action='append', default=[],
                    help='必须出现在报错里的文件（可多次）；给了它就能把"截断"判成硬失败')
    ap.add_argument('--selftest', action='store_true')
    args = ap.parse_args()

    if args.selftest:
        return selftest()

    if not os.path.isfile(args.raw):
        print('前置缺失：' + args.raw)
        print('  先按文件头注释里的 cmd 重定向把输出落盘。')
        return 3

    text, enc = load(args.raw)
    print('来源：%s（按 %s 解码）' % (args.raw, enc))
    print('=' * 74)
    stage = os.path.join(ROOT, 'port-1.20.1', 'ncpb-assembled', 'src', 'main', 'java')
    errors, warnings = analysis(text, stage, args.top, args.msgs, args.warnings)

    rc, out = judge(errors, warnings, args.canary)
    print()
    for ln in out:
        print(ln)
    print(machine_line(errors, warnings))
    return rc


if __name__ == '__main__':
    sys.exit(main())
