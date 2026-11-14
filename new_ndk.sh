#!/bin/bash

PROJ_NAME=${1:-"MyNdkApp"}
PKG="com.$(echo $PROJ_NAME | tr '[:upper:]' '[:lower:]')"
JNI_FUNC="Java_$(echo $PKG | tr '.' '_')_MainActivity_helloFromCpp"

SDK_DIR="$HOME/android-sdk"
NDK_DIR="$SDK_DIR/ndk/android-ndk-r30"
NDK_VER="30.0.16248370"
PREFIX="/data/data/com.termux/files/usr"
KEYSTORE_PATH="$HOME/android-release.keystore"

if [ ! -f "$KEYSTORE_PATH" ]; then
    echo "🔑 生成签名文件..."
    keytool -genkey -v -keystore "$KEYSTORE_PATH" -alias android -keyalg RSA \
        -keysize 2048 -validity 10000 -storepass 123456 -keypass 123456 \
        -dname "CN=Android,O=Android,C=US" > /dev/null 2>&1
    echo "✅ $KEYSTORE_PATH"
else
    echo "✅ 签名文件已存在"
fi

echo "🚀 创建 NDK 项目: $PROJ_NAME (包名: $PKG)"

if [ -d "$PROJ_NAME" ]; then
    echo "⚠️ 目录 $PROJ_NAME 已存在"
    exit 1
fi

mkdir -p $PROJ_NAME
cd $PROJ_NAME
mkdir -p app/src/main/java/${PKG//./\/}
mkdir -p app/src/main/cpp

cat > build.gradle << 'EOF_INNER'
buildscript {
    repositories { google(); mavenCentral() }
    dependencies { classpath 'com.android.tools.build:gradle:8.2.2' }
}
allprojects { repositories { google(); mavenCentral() } }
EOF_INNER

echo "include ':app'" > settings.gradle

cat > gradle.properties << EOF_INNER
org.gradle.jvmargs=-Xmx2048m -XX:+UseG1GC
android.aapt2FromMavenOverride=$PREFIX/bin/aapt2
android.useAndroidX=true
org.gradle.caching=true
EOF_INNER

cat > local.properties << EOF_INNER
sdk.dir=$SDK_DIR
cmake.dir=$PREFIX
EOF_INNER

cat > app/build.gradle << EOF_INNER
apply plugin: 'com.android.application'

android {
    namespace '$PKG'
    compileSdk 34
    ndkVersion "$NDK_VER"
    ndkPath "$NDK_DIR"

    defaultConfig {
        applicationId "$PKG"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
        ndk { abiFilters 'arm64-v8a' }

        externalNativeBuild {
            cmake {
                cppFlags "-std=c++17 -O3"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
        }
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
EOF_INNER

cat > app/proguard-rules.pro << 'EOF_INNER'
-keep public class * extends android.app.Activity
-keepclasseswithmembernames class * {
    native <methods>;
}
EOF_INNER

cat > app/src/main/AndroidManifest.xml << 'EOF_INNER'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:allowBackup="true" android:label="App">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF_INNER

cat > app/src/main/java/${PKG//./\/}/MainActivity.java << EOF_INNER
package $PKG;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    static { System.loadLibrary("native"); }

    public native String helloFromCpp();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setTextSize(20);
        tv.setPadding(60, 120, 60, 60);
        tv.setText(helloFromCpp());
        setContentView(tv);
    }
}
EOF_INNER

cat > app/src/main/cpp/CMakeLists.txt << 'EOF_INNER'
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
EOF_INNER

cat > app/src/main/cpp/native-lib.cpp << EOF_INNER
#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
${JNI_FUNC}(JNIEnv* env, jobject) {
    return env->NewStringUTF("Hello from C++ (NDK)!");
}
EOF_INNER

cat > build.sh << 'EOF_INNER'
#!/bin/bash
echo "🚀 编译 Release..."
gradle assembleRelease
if [ $? -eq 0 ]; then
    APK="app/build/outputs/apk/release/app-release.apk"
    echo "✅ 成功"
    echo "📦 体积: $(du -h $APK | cut -f1)"
    echo "📍 路径: $(pwd)/$APK"
    cp $APK /sdcard/Download/ 2>/dev/null && echo "📲 已拷贝到 /sdcard/Download/"
else
    echo "❌ 失败"
fi
EOF_INNER

chmod +x build.sh

echo "=========================================="
echo "✅ 项目 $PROJ_NAME 创建完毕！"
echo "👉 包名: $PKG"
echo "👉 NDK: $NDK_VER"
echo "👉 JNI: $JNI_FUNC"
echo "👉 接下来: cd $PROJ_NAME && ./build.sh"
echo "=========================================="
