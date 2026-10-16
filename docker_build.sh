#!/bin/bash
# Docker 构建 Flutter APK 脚本
#
# 前置：构建镜像（只需一次）：bash docker_image_build.sh
# 产物：flutter_app/build/app/outputs/flutter-apk/app-release.apk
#       并复制到项目根目录 self-discipline.apk
#
# 缓存策略（挂载宿主机目录，避免每次重新下载）：
#   ~/.gradle          Gradle 依赖与编译缓存
#   ~/.android         debug 签名密钥（持久化保证每次构建签名一致，可覆盖安装）
#   ./pub_cache        Dart 包缓存（项目内独立）

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FLUTTER_APP_DIR="$SCRIPT_DIR/flutter_app"
IMAGE_NAME="flutter-env:ok"

# 当前用户不在 docker 组时，通过 sg 切换组重新执行本脚本
if ! docker info >/dev/null 2>&1; then
    echo "当前用户无 docker 权限，尝试 sg docker..."
    exec sg docker -c "bash '$SCRIPT_DIR/docker_build.sh'"
fi

if ! docker images "$IMAGE_NAME" | grep -q flutter-env; then
    echo "错误: 未找到镜像 $IMAGE_NAME"
    echo "请先构建镜像: bash docker_image_build.sh"
    exit 1
fi

mkdir -p "$SCRIPT_DIR/pub_cache" "$HOME/.gradle" "$HOME/.android"

echo "=== 自律助手 Docker 构建 ==="

docker run --rm \
  -v "$FLUTTER_APP_DIR:/app" \
  -v "$HOME/.gradle:/root/.gradle" \
  -v "$HOME/.android:/root/.android" \
  -v "$SCRIPT_DIR/pub_cache:/root/.pub-cache" \
  -w /app "$IMAGE_NAME" sh -c \
  'git -C /opt/flutter remote remove origin 2>/dev/null; flutter pub get && flutter build apk --release'
# 注：移除 flutter 仓库 remote 是为了跳过启动时的版本检查（git fetch 在网络差时会挂起构建）

echo ""
echo "=== 构建成功 ==="
echo "APK 位置: $FLUTTER_APP_DIR/build/app/outputs/flutter-apk/app-release.apk"
cp "$FLUTTER_APP_DIR/build/app/outputs/flutter-apk/app-release.apk" "$SCRIPT_DIR/self-discipline.apk"
echo "已复制到: $SCRIPT_DIR/self-discipline.apk"
