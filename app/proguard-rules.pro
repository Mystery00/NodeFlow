# 通用属性：泛型、内部类关系、注解、异常表和崩溃堆栈行号。
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,Exceptions,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization：保留项目已有的 serializer 查找入口。
# 不使用全包 -keep，也不关闭 R8 优化或全局忽略 missing-class 警告。
-keepclassmembers class app.mystery0.nodeflow.** {
    *** Companion;
}
-keepclasseswithmembers class app.mystery0.nodeflow.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.mystery0.nodeflow.**$$serializer { *; }

# Retrofit 3 自带 consumer rules；显式固定本项目三个反射代理接口。
# suspend 接口还需要 Continuation 与 Response 的泛型签名（R8 full mode）。
-keep,allowoptimization interface app.mystery0.nodeflow.core.network.V2exRawApi {
    @retrofit2.http.* <methods>;
}
-keep,allowoptimization interface app.mystery0.nodeflow.core.network.V2exWriteApi {
    @retrofit2.http.* <methods>;
}
-keep,allowoptimization interface app.mystery0.nodeflow.core.network.V2exThankApi {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Room.databaseBuilder 通过数据库类名定位生成实现；仅固定本项目数据库入口。
-keep,allowoptimization class app.mystery0.nodeflow.core.database.NodeFlowDatabase { *; }
-keep,allowoptimization class app.mystery0.nodeflow.core.database.NodeFlowDatabase_Impl {
    public <init>();
}

# Worker 类名会持久化到 WorkManager 数据库，跨版本不能变；保留反射构造函数。
-keep,allowoptimization class app.mystery0.nodeflow.core.notification.NotificationCheckWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# OkHttp、Coil、Media3、Koin 等其余规则由依赖和 Android 默认规则提供。
# okhttp3.internal.Util.closeQuietly 是 ZoomImage 的直接字节码调用，R8 会一起改写，
# 不是按名字反射查找，不应为了该兼容层保留整个 okhttp3 包。
