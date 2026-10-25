#!/bin/bash
# ==========================================
# hdm 纯本地一键打包+安装脚本 (不复制外部)
# ==========================================

echo "======================================"
echo "🚀 开始编译 hdm 测量神器 (Release 版)..."
echo "======================================"

# 执行正式版打包命令
gradle assembleRelease

# 检查是否成功
if [ $? -eq 0 ]; then
    echo ""
    echo "✅ 编译成功！正在定位内部 APK..."
    
    # 直接在当前目录的 build 文件夹里找最终生成的包
    TARGET_FILE="app/build/outputs/apk/release/app-release.apk"
    
    # 检查包是否真的存在
    if [ -f "$TARGET_FILE" ]; then
        # 计算大小
        FILE_SIZE=$(du -k "$TARGET_FILE" | cut -f1)
        
        echo "🎉 搞定！"
        echo "📦 包体积: ${FILE_SIZE} KB"
        echo "📍 当前路径: $TARGET_FILE"
        
        # 调用系统安装器直接安装该路径下的包
        echo ""
        
    else
        echo "❌ 报错：编译成功了，但没在当前目录找到 app-release.apk"
    fi
    
    echo "======================================"
else
    echo ""
    echo "❌ 编译失败！请往上翻查看红色的报错信息。"
    echo "======================================"
fi