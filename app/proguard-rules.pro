# 通用属性：泛型签名、注解、异常表、源文件行号（崩溃页堆栈可读）
-keepattributes Signature, *Annotation*, Exceptions, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization
# OkHttp/Retrofit/Room/Coil 等依赖均自带 consumer rules，无需重复；
# 这里只补充项目内 @Serializable 类型经 serializer() 反射查找的路径
-keepclassmembers class app.mystery0.nodeflow.** {
    *** Companion;
}
-keepclasseswithmembers class app.mystery0.nodeflow.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.mystery0.nodeflow.**$$serializer { *; }
