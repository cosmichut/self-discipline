#!/bin/bash
# 构建 Flutter+Android 编译镜像（只需执行一次，约 10-20 分钟）
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 当前用户不在 docker 组时，通过 sg 切换组执行
if ! docker info >/dev/null 2>&1; then
    echo "当前用户无 docker 权限，尝试 sg docker..."
    exec sg docker -c "bash '$SCRIPT_DIR/docker_image_build.sh'"
fi

docker build -t flutter-env:ok "$SCRIPT_DIR"
echo "=== 镜像构建完成: flutter-env:ok ==="
