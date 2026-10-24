# LL-android (hdm 断面计算)

这是一个专为**工程测量员**打造的纯原生安卓测量工具。没有广告，没有第三方臃肿依赖，**APK 包体仅有 51KB**，秒开秒用。

## 🌟 核心功能
*   **土方计算**：支持横断面土方、清表面积、填挖面积计算。
*   **边坡放样**：计算并绘制坡脚、坡顶放样线。
*   **正反算**：坐标(XY)与桩号(K)+偏距(B)的高精度互算。
*   **线路图**：实时加载线路中线、桩号刻度，并接入手机 GPS，实时显示当前所在桩号、偏距及定位精度。
*   **底层算法**：包含高斯投影、UTM投影、四参数转换、平曲线高斯积分、竖曲线计算等核心算法（详见 `LL.java` 与 `Projection.java`）。

## 🚀 如何克隆与编译
本项目支持在电脑或手机端的 Termux 中直接编译。

**环境准备：**
*   JDK 17 或 21
*   Android SDK (需包含 Platform 34, Build-tools 34)
*   Gradle 8.4+

**手机端（Termux）极简编译：**
1. 克隆项目：`git clone https://github.com/LL-hua/LL-android.git`
2. 进入目录：`cd LL-android`
3. 执行打包：`gradle assembleDebug` （或 `gradle assembleRelease`）
4. 生成的 APK 位于：`app/build/outputs/apk/debug/app-debug.apk`

## 📂 数据读取说明
App 通过读取手机根目录下的 `1hdm` 文件夹内的 `.xlsx` 文件来加载项目数据。请确保配置好平曲线、竖曲线、高程、结构层等对应表格。

## 📝 说明
本项目采用纯 Java 动态代码生成 UI，无任何第三方依赖。代码结构清晰，非常适合工程测量同行进行二次开发。欢迎提 Issue 或 PR！
