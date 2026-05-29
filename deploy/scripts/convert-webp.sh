#!/bin/bash
set -euo pipefail

IMAGE_DIR="${1:-/data/mysite/uploads/images}"

if [ ! -d "$IMAGE_DIR" ]; then
    echo "目录不存在: $IMAGE_DIR"
    exit 1
fi

if ! command -v cwebp &> /dev/null; then
    echo "cwebp 未安装，正在安装..."
    if command -v apt-get &> /dev/null; then
        sudo apt-get update && sudo apt-get install -y webp
    elif command -v yum &> /dev/null; then
        sudo yum install -y libwebp-tools
    elif command -v apk &> /dev/null; then
        sudo apk add webp
    else
        echo "无法自动安装 cwebp，请手动安装: https://developers.google.com/speed/webp/docs/precompiled"
        exit 1
    fi
fi

TOTAL=0
CONVERTED=0
SKIPPED=0
FAILED=0

while IFS= read -r img; do
    TOTAL=$((TOTAL + 1))
    webp_path="${img%.*}.webp"

    if [ -f "$webp_path" ]; then
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    if cwebp -quiet -q 80 "$img" -o "$webp_path" 2>/dev/null; then
        orig_size=$(stat -c%s "$img" 2>/dev/null || stat -f%z "$img" 2>/dev/null)
        webp_size=$(stat -c%s "$webp_path" 2>/dev/null || stat -f%z "$webp_path" 2>/dev/null)
        ratio=$((100 - webp_size * 100 / orig_size))
        echo "✓ $(basename "$img") → $(basename "$webp_path") (${orig_size}B → ${webp_size}B, -${ratio}%)"
        CONVERTED=$((CONVERTED + 1))
    else
        echo "✗ 转换失败: $(basename "$img")"
        rm -f "$webp_path"
        FAILED=$((FAILED + 1))
    fi
done < <(find "$IMAGE_DIR" -type f \( -name "*.png" -o -name "*.jpg" -o -name "*.jpeg" -o -name "*.gif" \))

echo ""
echo "批量转换完成: 共 ${TOTAL} 个文件, 转换 ${CONVERTED}, 跳过 ${SKIPPED}, 失败 ${FAILED}"
