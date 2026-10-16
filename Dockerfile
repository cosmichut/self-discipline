# Flutter + Android 构建镜像（自律助手）
#
# 构建：bash docker_image_build.sh
# 包含：Flutter SDK 3.27.1 + Android SDK (platforms 34/35) + JDK 17 + NDK

FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive \
    FLUTTER_HOME=/opt/flutter \
    ANDROID_HOME=/opt/android-sdk
ENV PATH="$FLUTTER_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

RUN apt-get update && apt-get install -y \
    curl git unzip xz-utils zip libglu1-mesa openjdk-17-jdk wget \
    && rm -rf /var/lib/apt/lists/*

# Android SDK 命令行工具
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# SDK 平台、构建工具、NDK（版本与 android/app/build.gradle 对应）
RUN yes | sdkmanager --licenses && \
    yes | sdkmanager \
    "platforms;android-34" "platforms;android-35" \
    "build-tools;34.0.0" "build-tools;35.0.0" \
    "ndk;25.1.8937393"

# Flutter SDK（3.27.1 stable）
RUN git config --global --add safe.directory '*' && \
    curl -fsSL https://storage.googleapis.com/flutter_infra_release/releases/stable/linux/flutter_linux_3.27.1-stable.tar.xz | tar -xJ -C /opt && \
    flutter precache --android && flutter doctor

WORKDIR /app
CMD ["/bin/bash"]
