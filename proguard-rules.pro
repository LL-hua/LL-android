# ============================================================
# 1. 基础配置
# ============================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# ============================================================
# 2. 保留我们自己的核心业务代码
# ============================================================
-keep class com.hdm.Models** { *; }
-keep class com.hdm.ProjectData { *; }
-keep class com.hdm.AppConfig { *; }

# 保留所有 Activity 和 Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application

# 保留自定义 View（如果你有自定义的 CanvasMapView 等）
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ============================================================
# 3. 保留底层算法和特殊工具类
# ============================================================
-keep class com.hdm.LL { *; }
-keep class com.hdm.Projection { *; }
-keep class com.hdm.XlsxProjectData { *; }
-keep class com.hdm.XlsxProjectData$XlsxReader { *; }

# 如果你使用了 SQLiteOpenHelper (TileDatabase)
-keep class * extends android.database.sqlite.SQLiteOpenHelper { *; }