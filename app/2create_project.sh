#!/bin/bash

PROJ_NAME=${1:-"MyApp"}
PKG="com.example.myapp"

mkdir -p $PROJ_NAME
cd $PROJ_NAME
mkdir -p app/src/main/java/${PKG//./\/}
mkdir -p app/src/main/res/values

# 根 build.gradle (已改为海外最快源)
cat > build.gradle << 'EOF'
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.1.0'
    }
}
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
EOF

echo "include ':app'" > settings.gradle

# app/build.gradle
cat > app/build.gradle << 'EOF'
apply plugin: 'com.android.application'

android {
    namespace 'com.example.myapp'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.myapp"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
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
}
EOF

# proguard
echo "-keep public class * extends android.app.Activity" > app/proguard-rules.pro

# AndroidManifest.xml
cat > app/src/main/AndroidManifest.xml << 'EOF'
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
EOF

# MainActivity.java
cat > app/src/main/java/${PKG//./\/}/MainActivity.java << 'EOF'
package com.example.myapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Hello Termux!");
        tv.setTextSize(24);
        setContentView(tv);
    }
}
EOF

# 打包脚本
cat > build.sh << 'EOF'
#!/bin/bash
echo "🚀 开始编译..."
gradle assembleRelease
if [ $? -eq 0 ]; then
    echo "✅ 编译成功！"
    echo "📦 包体积: $(du -h app/build/outputs/apk/release/app-release.apk | cut -f1)"
    echo "📍 路径: $(pwd)/app/build/outputs/apk/release/app-release.apk"
else
    echo "❌ 编译失败！"
fi
EOF

chmod +x build.sh

echo "✅ 项目 $PROJ_NAME 已创建！"
echo "👉 运行: cd $PROJ_NAME && ./build.sh"