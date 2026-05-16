# ============================================================
# Zob Screen Recorder - ProGuard / R8 Rules
# ============================================================

# ---- Compose ----
-keep class androidx.compose.** { *; }
-keepclassmembers class * implements androidx.compose.runtime.Composable { <methods>; }
-keep,allowobfuscation class * extends androidx.compose.ui.node.LayoutNode
-dontwarn androidx.compose.**
-keep class * implements androidx.compose.runtime.Composer { *; }
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep Compose applier and synthetic properties
-keep class * extends androidx.compose.runtime.ComposerImpl { *; }
-keepclassmembers class * {
    @kotlin.Metadata <fields>;
}

# ---- Kotlin Serialization ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.zob.recorder.**$$serializer { *; }
-keepclassmembers class com.zob.recorder.** {
    *** Companion;
}
-keepclasseswithmembers class com.zob.recorder.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Hilt / Dagger ----
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$ActivityContextWrapper { *; }
-keepclassmembers class * {
    @dagger.hilt.android.internal.lifecycle.HiltViewModelMap <fields>;
}
-keep class * extends dagger.hilt.android.internal.managers.HiltWrapper_ActivityRetainedComponentManager_ActivityRetainedComponentBuilder { *; }
-dontwarn dagger.**
-keep class * extends dagger.hilt.android.components.ViewModelComponent { *; }

# ---- Retrofit / OkHttp ----
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ---- RootEncoder / RTMP- RTSP Stream Client ----
-keep class com.pedro.rtmp.** { *; }
-keep class com.pedro.rtsp.** { *; }
-keep class com.pedro.common.** { *; }
-keep class net.ossrs.rtmp.** { *; }
-dontwarn com.pedro.**
-dontwarn net.ossrs.**

# ---- Media3 / ExoPlayer ----
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keepclassmembers class * extends androidx.media3.exoplayer.ExoPlayer { *; }

# ---- Coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# ---- MediaProjection ----
-keep class android.media.projection.** { *; }
-keepclassmembers class * extends android.media.projection.MediaProjection { *; }
-keepclassmembers class * extends android.media.projection.MediaProjectionManager { *; }

# ---- General Android ----
-keep class * extends android.app.Service { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Application { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.app.Fragment { *; }

# ---- JavaScript / JNI ----
-keep class * {
    native <methods>;
}

# ---- Enum ----
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Parcelable ----
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ---- Serializable ----
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ---- R8 full mode ----
# Keep annotations used by Compose and serialization
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-keepattributes *Annotation*
