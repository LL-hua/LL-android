#!/bin/bash
# ==========================================
# hdm 正式版一键打包脚本
# ==========================================

echo "======================================"
echo "🚀 开始编译 hdm 测量神器 (Release 版)..."
echo "======================================"

# 执行正式版打包命令
gradle assembleRelease

# 检查上一条命令是否成功 ($? == 0 表示成功)
if [ $? -eq 0 ]; then
    echo ""
    echo "✅ 编译成功！正在导出 APK..."
    
    # 提取出来的文件名（带时间戳，防止覆盖旧版）
    TARGET_FILE=~/storage/downloads/hdm_Release_$(date +%m%d_%H%M).apk
    
    # 拷贝文件
    cp app/build/outputs/apk/release/app-release.apk $TARGET_FILE
    
    # 计算文件大小 (单位 KB)
    FILE_SIZE=$(du -k $TARGET_FILE | cut -f1)
    
    echo "🎉 搞定！"
    echo "📦 包体积: ${FILE_SIZE} KB"
    echo "📍 路径: 手机【下载】目录 / $(basename $TARGET_FILE)"
    echo "======================================"
else
    echo ""
    echo "❌ 编译失败！请往上翻查看红色的报错信息。"
    echo "======================================"
fi

