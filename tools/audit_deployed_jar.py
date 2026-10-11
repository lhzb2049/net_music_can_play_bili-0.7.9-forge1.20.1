#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""对**已部署 jar 里的模型**逐元素断言：1.20.1 的原版关节能不能吃下它们。

为什么必须单独查这个（本次差点漏掉的关键一环）：
  `tools/sync_assets.py` 会把父链摊平进叶子。它与上游 loader 的**收集口径**有一处不同：

      上游 loader  : loadedModel.getElements()  +  loadedModel.getData().get("ncpb_euler_elements")
      sync_assets  : nodes[].elements           +  leaf.ncpb_euler_elements

  也就是说 **父节点自己的 `ncpb_euler_elements` 会被摊平丢掉**。
  丢掉的是"元素"还是"合法元素"看不出来 —— 但 1.20.1 的原版
  `BlockElement$Deserializer` 只放行 0/±22.5/±45 这些角度，**其余直接抛 JsonParseException**。
  所以一个带自订 loader 的模型，其 `elements` 里**不允许**出现非法角度；
  一旦出现，整个模型会加载失败（比"元素丢了"更糟）。

用法：
    python audit_deployed_jar.py                    # 查 build/libs 的 jar
    python audit_deployed_jar.py --jar <path>

退出码：0 = 每个模型都合法；1 = 有非法角度（模型会加载失败）；2 = jar 或结构缺失。
"""
import argparse
import io
import json
import os
import sys
import zipfile

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
NS = 'net_music_can_play_bili'
DEFAULT_JAR = os.path.join(ROOT, 'port-1.20.1', 'ncpb-assembled', 'build', 'libs')

LEGAL = (0.0, 22.5, -22.5, 45.0, -45.0)
LOADER = NS + ':euler_elements'
EULER_KEY = 'ncpb_euler_elements'


def find_jar(path):
    if os.path.isfile(path):
        return path
    if os.path.isdir(path):
        cands = [os.path.join(path, n) for n in os.listdir(path) if n.endswith('.jar')]
        if cands:
            cands.sort(key=os.path.getmtime, reverse=True)
            return cands[0]
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--jar', default=DEFAULT_JAR)
    args = ap.parse_args()

    jar = find_jar(args.jar)
    if not jar:
        print('前置缺失：找不到 jar（%s）' % args.jar)
        return 2
    print('检查 jar：%s' % jar)

    prefix = 'assets/%s/models/' % NS
    bad_total = 0
    n_models = 0
    n_loader = 0
    n_flat_no_loader = 0

    with zipfile.ZipFile(jar) as z:
        names = [n for n in z.namelist() if n.startswith(prefix) and n.endswith('.json')]
        if not names:
            print('前置缺失：jar 里没有 %s*.json' % prefix)
            return 2
        for name in sorted(names):
            doc = json.loads(z.read(name).decode('utf-8-sig'))
            rel = name[len(prefix):]
            n_models += 1
            has_loader = doc.get('loader') == LOADER
            if has_loader:
                n_loader += 1
            els = doc.get('elements') or []
            eul = doc.get(EULER_KEY) or []
            bad = []
            for i, el in enumerate(els):
                rot = el.get('rotation')
                if not rot:
                    continue                    # 没有 rotation 完全合法（多数元素如此）
                if 'axis' not in rot:
                    bad.append((i, '三轴写法出现在 elements 里（会被原版反序列化拒绝）', rot))
                    continue
                a = float(rot.get('angle', 0.0))
                if not any(abs(a - ok) < 1e-6 for ok in LEGAL):
                    bad.append((i, 'angle=%s 不在 %s' % (a, LEGAL), rot))
            if bad:
                bad_total += len(bad)
                print('\n[FAIL] %s  loader=%s  elements=%d  euler=%d'
                      % (rel, has_loader, len(els), len(eul)))
                for i, why, rot in bad[:6]:
                    print('        el[%d] %s  %s' % (i, why, json.dumps(rot, ensure_ascii=False)))
                if len(bad) > 6:
                    print('        ... 共 %d 条' % len(bad))
            elif not has_loader and len(els) == 0 and len(eul) == 0:
                n_flat_no_loader += 1

    print('\n模型 %d 个（带自订 loader %d 个）' % (n_models, n_loader))
    if bad_total:
        print('结论：失败 —— %d 处非法元素会让对应模型**加载失败**' % bad_total)
        return 1
    print('结论：通过 —— 每个模型的 elements 里都没有原版装不下的角度')
    return 0


if __name__ == '__main__':
    sys.exit(main())
