#!/usr/bin/env python3
"""Noto Serif SC woff2 可变字体子集生成脚本（T2 衬线 webfont）

用法（依赖本机 fonttools+brotli，不进项目依赖）：
    # 1) 可变字体源文件放到 /tmp/t2-fonts/NotoSerifSC-var.ttf
    #    获取：google/fonts 仓库 ofl/notoserifsc/NotoSerifSC[wght].ttf
    #    （GitHub 直连不通时可走 gh-proxy 等镜像，见 prd.md）
    # 2) python3 subset-fonts.py /tmp/t2-fonts
    #    输出 woff2 → mysite-frontend/public/fonts/，unicode-range 清单 → 本目录 unicode-ranges.txt

分片策略（unicode-range 精确不相交，浏览器按需下载）：
  可变字体 wght 200–900，一个 @font-face 覆盖全部字重（fonts.css 里 font-weight: 200 900）。
  core      : cp < U+2E80（拉丁/标点/符号）+ CJK 标点/假名/注音 + 全角 + GB2312 一级字库(3755)
  hanzi2    : GB2312 二级字库(3008)
  rest-1/2/3: 剩余字符按码点均分三片（CJK 扩展 A、非常用汉字、兼容表意等）
"""
import os
import subprocess
import sys
import tempfile

from fontTools.ttLib import TTFont

HERE = os.path.dirname(os.path.abspath(__file__))
SRC_DIR = sys.argv[1] if len(sys.argv) > 1 else "/tmp/t2-fonts"
SRC_FONT = os.path.join(SRC_DIR, "NotoSerifSC-var.ttf")
OUT_DIR = os.path.join(HERE, "..", "..", "..",
                       "mysite-frontend", "public", "fonts")


def gb2312_hanzi(q1, q2):
    out = set()
    for qu in range(q1, q2 + 1):
        for wei in range(1, 95):
            try:
                out.add(ord(bytes([0xA0 + qu, 0xA0 + wei]).decode("gb2312")))
            except Exception:
                pass
    return out


def runs(cps):
    cps = sorted(cps)
    if not cps:
        return []
    out, s, p = [], cps[0], cps[0]
    for c in cps[1:]:
        if c == p + 1:
            p = c
            continue
        out.append((s, p))
        s = p = c
    out.append((s, p))
    return out


def range_str(rs):
    return ",".join(f"U+{s:04X}" if s == e else f"U+{s:04X}-{e:04X}" for s, e in rs)


def main():
    f = TTFont(SRC_FONT, lazy=True)
    cmap = set(f.getBestCmap().keys())
    f.close()

    l1 = gb2312_hanzi(16, 55) & cmap   # GB2312 一级字库（常用 3755）
    l2 = gb2312_hanzi(56, 87) & cmap   # GB2312 二级字库（3008）

    core = {cp for cp in cmap if cp < 0x2E80}
    core |= {cp for cp in cmap if 0x3000 <= cp <= 0x303F}   # CJK 标点
    core |= {cp for cp in cmap if 0x3040 <= cp <= 0x30FF}   # 平假名/片假名
    core |= {cp for cp in cmap if 0x3100 <= cp <= 0x312F}   # 注音符号
    core |= {cp for cp in cmap if 0xFF00 <= cp <= 0xFFEF}   # 全角字符
    core |= l1

    hanzi2 = l2 - core
    rest = sorted(cmap - core - hanzi2)
    third = len(rest) // 3
    slices = {
        "core": sorted(core),
        "hanzi2": sorted(hanzi2),
        "rest-1": rest[:third],
        "rest-2": rest[third:2 * third],
        "rest-3": rest[2 * third:],
    }

    os.makedirs(OUT_DIR, exist_ok=True)
    report = []
    for name, cps in slices.items():
        out = os.path.join(OUT_DIR, f"noto-serif-sc-{name}.woff2")
        with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as tf:
            tf.write(",".join(f"U+{c:04X}" for c in cps))
            uni_file = tf.name
        try:
            subprocess.run(
                ["pyftsubset", SRC_FONT, f"--output-file={out}", "--flavor=woff2",
                 f"--unicodes-file={uni_file}", "--layout-features=*"],
                check=True)
        finally:
            os.unlink(uni_file)
        report.append((name, len(cps), os.path.getsize(out)))

    # unicode-range 清单（供 fonts.css 生成/核对）
    with open(os.path.join(HERE, "unicode-ranges.txt"), "w") as fp:
        for name, cps in slices.items():
            fp.write(f"# {name} ({len(cps)} cps, {len(runs(cps))} runs)\n")
            fp.write(range_str(runs(cps)) + "\n\n")

    for name, n, size in report:
        print(f"{name:8} {n:6} cps  {size/1024:8.1f} KiB")
    print(f"\n输出目录: {OUT_DIR}")


if __name__ == "__main__":
    main()
