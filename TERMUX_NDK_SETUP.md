# Termux 上 Android NDK 开发环境完整手册

## 一、版本基准

| 组件 | 版本 |
|---|---|
| Termux | F-Droid 版（不是 Play Store 版） |
| JDK | openjdk-17 |
| Gradle | 8.x（>= 8.0） |
| AGP | 8.2.2 |
| CMake | Termux 系统版（$PREFIX） |
| Ninja | Termux 系统版（$PREFIX） |
| aapt2 | Termux 系统版（$PREFIX/bin/aapt2） |
| Android SDK | cmdline-tools latest |
| Android NDK | r30（30.0.16248370） |
| compileSdk / targetSdk | 34 |
| minSdk | 24 |
| ABI | arm64-v8a |

## 二、Termux 基础

必须从 F-Droid 安装 Termux（Play Store 版本已停更，NDK 无法使用）。

pkg update && pkg upgrade -y
pkg install -y openjdk-17 git wget unzip cmake ninja aapt gradle

验证：

java -version        # 应为 17.x
cmake --version      # 3.2x 或 4.x
ninja --version      # 1.x
gradle --version     # 必须 >= 8.0

若 gradle < 8.0，手动安装：

cd ~
wget https://services.gradle.org/distributions/gradle-8.5-bin.zip
unzip gradle-8.5-bin.zip
echo 'export PATH=$HOME/gradle-8.5/bin:$PATH' >> ~/.bashrc
source ~/.bashrc

## 三、Android SDK

mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*.zip
mv cmdline-tools latest

写入环境变量（~/.bashrc）：

cat >> ~/.bashrc << 'EOF'
export ANDROID_HOME=$HOME/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
EOF
source ~/.bashrc

安装 SDK 组件：

yes | sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

## 四、Android NDK

必须用解压式安装（sdkmanager 装的目录结构是 ndk/30.0.16248370/，官方 zip 解压出来是 android-ndk-r30/，我们用 zip 方式）：

cd ~/android-sdk
mkdir -p ndk
wget https://dl.google.com/android/repository/android-ndk-r30-linux.zip
unzip android-ndk-r30-linux.zip
mv android-ndk-r30 ndk/

验证：

grep Revision ~/android-sdk/ndk/android-ndk-r30/source.properties
# 应输出 Pkg.Revision = 30.0.16248370

## 五、关键配置（三处，一处都不能错）

### 1. aapt2 必须用 Termux 系统版

AGP 默认从 Maven 拉 x86_64 的 aapt2，在 arm64 上直接 SIGILL 崩溃。

项目 gradle.properties 必须有：

android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2

### 2. CMake 必须用 Termux 系统版

AGP 去 SDK 里找 CMake 会找到 x86_64 版，跑不了。

项目 local.properties 必须写：

sdk.dir=/data/data/com.termux/files/home/android-sdk
cmake.dir=/data/data/com.termux/files/usr

cmake.dir 指向 $PREFIX，AGP 会去 $PREFIX/bin/cmake 找可执行文件。

不要写 ndk.dir —— AGP 8.x 已弃用，用了会出一堆 WARNING，且容易和 ndkVersion 冲突。

### 3. ndkVersion 和 ndkPath 必须同时写

app/build.gradle 里：

android {
    ndkVersion "30.0.16248370"
    ndkPath "/data/data/com.termux/files/home/android-sdk/ndk/android-ndk-r30"
}

只写 ndkPath → 报 CXX1100: ndkVersion is [25.x] but ndkPath refers to [30.x]。

externalNativeBuild.cmake 里不要写 version：

externalNativeBuild {
    cmake {
        path "src/main/cpp/CMakeLists.txt"
        // 不写 version，让 AGP 自动探测 Termux 系统版 cmake
    }
}

写了 version 会和 Termux 实际 cmake 版本号冲突。

## 六、项目标准结构

MyApp/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── local.properties
├── build.sh
└── app/
    ├── build.gradle
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/myapp/MainActivity.java
        └── cpp/
            ├── CMakeLists.txt
            └── native-lib.cpp

## 七、各文件模板

### 根 build.gradle

buildscript {
    repositories { google(); mavenCentral() }
    dependencies { classpath 'com.android.tools.build:gradle:8.2.2' }
}
allprojects { repositories { google(); mavenCentral() } }

### settings.gradle

include ':app'

### gradle.properties

org.gradle.jvmargs=-Xmx2048m -XX:+UseG1GC
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
android.useAndroidX=true
org.gradle.caching=true

### local.properties

sdk.dir=/data/data/com.termux/files/home/android-sdk
cmake.dir=/data/data/com.termux/files/usr

### app/build.gradle

apply plugin: 'com.android.application'

android {
    namespace 'com.example.myapp'
    compileSdk 34
    ndkVersion "30.0.16248370"
    ndkPath "/data/data/com.termux/files/home/android-sdk/ndk/android-ndk-r30"

    defaultConfig {
        applicationId "com.example.myapp"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
        ndk { abiFilters 'arm64-v8a' }
        externalNativeBuild {
            cmake { cppFlags "-std=c++17 -O3" }
        }
    }

    externalNativeBuild {
        cmake { path "src/main/cpp/CMakeLists.txt" }
    }

    signingConfigs {
        release {
            storeFile file(System.getProperty("user.home") + "/android-release.keystore")
            storePassword "123456"
            keyAlias "android"
            keyPassword "123456"
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    lint { checkReleaseBuilds false }
}

### app/proguard-rules.pro

-keep public class * extends android.app.Activity
-keepclasseswithmembernames class * {
    native <methods>;
}

### app/src/main/cpp/CMakeLists.txt（体积优化版）

cmake_minimum_required(VERSION 3.22.1)
project("native")

add_library(native SHARED native-lib.cpp)

target_compile_options(native PRIVATE
    -O3
    -fno-exceptions
    -fno-rtti
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)

target_link_options(native PRIVATE
    -static-libstdc++
    -Wl,--gc-sections
    -Wl,--strip-all
    -s
)

### app/src/main/cpp/native-lib.cpp（最小体积版）

#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_myapp_MainActivity_helloFromCpp(JNIEnv* env, jobject) {
    return env->NewStringUTF("Hello from C++ (NDK)!");
}

## 八、JNI 命名规则

Java_ + 包名（点换下划线） + _ + 类名 + _ + 方法名

例：包名 com.example.myapp，类 MainActivity，方法 helloFromCpp
→ Java_com_example_myapp_MainActivity_helloFromCpp

包名若带下划线（com.my_app），下划线要转义为 _1（Java_com_my_1app_...）。强烈建议包名不带下划线。

## 九、体积优化（核心规则）

### C++ 代码侧

不要用 C++ 标准库容器和字符串 —— 用了会把 libc++ 静态链进 .so，体积从 3KB 涨到 30~50KB。

| 禁用 | 替代 |
|---|---|
| std::string | const char* + snprintf |
| std::vector | malloc/free 或栈数组 |
| new/delete | malloc/free |
| std::cout | __android_log_print 或不用 |

### 期望体积

| 情况 | .so | APK |
|---|---|---|
| 空壳 JNI | 2~3 KB | 15~16 KB |
| 普通算法 | 视算法 | 视算法 |
| 用了 std::string | 30~50 KB | 60~100 KB |

### 体积排查

unzip -l app/build/outputs/apk/release/app-release.apk | sort -k1 -rn | head -10

看哪一项最大：
- 若 libc++_shared.so 存在 → 缺 -static-libstdc++
- 若 .so > 10KB → C++ 代码里用了 std::string/vector
- 若 classes.dex 大 → 加 minifyEnabled true + shrinkResources true

## 十、踩坑速查表

| 错误 | 原因 | 解决 |
|---|---|---|
| CXX1100: ndkVersion is [25.x] but ndkPath refers to [30.x] | 只写 ndkPath 没写 ndkVersion | 两个都写，版本一致 |
| aapt2 崩溃 / SIGILL | AGP 拉了 x86_64 aapt2 | gradle.properties 加 aapt2FromMavenOverride |
| CMake 'x.x.x' was not found in SDK, PATH, or by cmake.dir | cmake.dir 没指对 | cmake.dir=/data/data/com.termux/files/usr |
| Task 'assembleDebug' not found | app/build.gradle 不存在 / settings 没 include | 检查这两个文件 |
| sed -i '1i ...\n...' 只插一行 | Termux sed 不支持 \n | 用两次 sed 或 printf |
| heredoc 被中断 | Termux 偶尔吞长内容 | 分段写，每段 <100 行，末尾 wc -l 验证 |
| UnsatisfiedLinkError 但 .so 已打包 | R8 把 Activity 或 native 方法改名了 | proguard-rules 加 keepclasseswithmembernames native methods |
| keystore 生成报错 | -dname 里 S=NA 不合规 | 用 ST=NA 或全称 STATE=NA |
| --offline 首次构建失败 | .gradle 缓存不全 | 首次不加 --offline，后续再加 |
| .so 30~50KB 太大 | std::string/new/vector 拉了 libc++ | 见第九节 |

## 十一、编译打包命令

# Debug
gradle assembleDebug

# Release（首次不加 --offline）
gradle assembleRelease -x lintVitalRelease

# 清理重编（仅在改 build.gradle、换 NDK、诡异报错时用）
gradle clean && gradle assembleRelease

APK 产物：
- Debug：app/build/outputs/apk/debug/app-debug.apk
- Release：app/build/outputs/apk/release/app-release.apk

首次编译慢是正常的（AGP 下载、aapt2/d8/R8 首次 JIT、AAR 解压），后续会从 1 分钟级降到 5~10 秒。

## 十二、一键脚本

两个模板：
- ~/new_java.sh —— 纯 Java 项目
- ~/new_ndk.sh —— NDK 项目

用法：

bash ~/new_java.sh MyJavaApp
bash ~/new_ndk.sh MyNdkApp
cd MyNdkApp && ./build.sh

脚本内置：
- 自动生成签名 keystore（~/android-release.keystore，密码 123456）
- 自动配置 ndkVersion + ndkPath
- 自动加 aapt2FromMavenOverride
- CMake 体积优化全套参数
- Release 打包后自动拷到 /sdcard/Download/

## 十三、参考

- Android NDK 官方文档：https://developer.android.com/ndk
- AGP 版本与 Gradle 对应：https://developer.android.com/build/releases/gradle-plugin
- Termux Wiki：https://wiki.termux.com
