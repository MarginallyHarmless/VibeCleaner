# Add project specific ProGuard rules here.

# Room database entities — prevent obfuscation of table/column names
-keep class com.example.photocleanup.data.** { *; }

# ViewModels
-keep class * extends androidx.lifecycle.ViewModel { *; }

# WorkManager workers
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Keep enums (used in Room and data classes)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Kotlin serialization / metadata
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# Compose — keep runtime stability metadata
-keep class androidx.compose.runtime.** { *; }
