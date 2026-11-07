#!/bin/bash

# 获取项目名，默认为 MyApp
PROJ_NAME=${1:-"MyApp"}
# 包名自动跟随项目名（强制转小写，符合 Java 规范）
PKG="com.$(echo $PROJ_NAME | tr '[:upper:]' '[:lower:]')"

# ================= 1. 自动检测并生成签名文件 =================
KEYSTORE_PATH="$HOME/android-release.keystore"
if [ ! -f "$KEYSTORE_PATH" ]; then
    echo "🔑 未找到签名文件，正在自动生成..."
    keytool -genkey -v -keystore "$KEYSTORE_PATH" -alias android -keyalg RSA -keysize 2048 -validity 10000 -storepass 123456 -keypass 123456 -dname "CN=Android,O=Android,C=US" > /dev/null 2>&1
    echo "✅ 签名文件已生成：$KEYSTORE_PATH"
else
    echo "✅ 签名文件已存在，跳过生成。"
fi

echo "🚀 开始创建项目: $PROJ_NAME (包名: $PKG) ..."

# 如果目录已存在，提示并退出
if [ -d "$PROJ_NAME" ]; then
    echo "⚠️ 目录 $PROJ_NAME 已存在！请换个名字或手动删除。"
    return 1 2>/dev/null || exit 1
fi

mkdir -p $PROJ_NAME
cd $PROJ_NAME
mkdir -p app/src/main/java/${PKG//./\/}
mkdir -p app/src/main/res/values

# 2. 根 build.gradle
cat > build.gradle << 'EOF'
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.2.0'
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

# 3. app/build.gradle (使用动态包名)
cat > app/build.gradle << EOF
apply plugin: 'com.android.application'

android {
    namespace '$PKG'
    compileSdk 34
    
    lint {
        checkReleaseBuilds false
    }

    defaultConfig {
        applicationId "$PKG"
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

# 4. 其它基础文件
echo "-keep public class * extends android.app.Activity" > app/proguard-rules.pro

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

# 5. MainActivity.java (使用动态包名)
cat > app/src/main/java/${PKG//./\/}/MainActivity.java << EOF
package $PKG;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Hello from $PROJ_NAME!");
        tv.setTextSize(24);
        setContentView(tv);
    }
}
EOF

# 6. 打包脚本
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

echo "=========================================="
echo "✅ 项目 $PROJ_NAME 创建完毕！"
echo "👉 包名: $PKG"
echo "👉 接下来可以直接运行: ./build.sh"
echo "=========================================="