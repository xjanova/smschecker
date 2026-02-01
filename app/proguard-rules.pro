# ============================================
# Retrofit
# ============================================
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ============================================
# Gson
# ============================================
-keep class com.thaiprompt.smschecker.data.model.** { *; }
-keep class com.thaiprompt.smschecker.data.api.** { *; }

# ============================================
# Room Database
# ============================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface * { *; }
-keep class com.thaiprompt.smschecker.data.db.Converters { *; }
-keep class com.thaiprompt.smschecker.data.db.** { *; }

# ============================================
# Hilt / Dagger
# ============================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class com.thaiprompt.smschecker.di.** { *; }

# Keep all ViewModels (Hilt needs to instantiate them)
-keep class * extends androidx.lifecycle.ViewModel { *; }

# ============================================
# Enums (Room TypeConverters use valueOf)
# ============================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================
# Firebase Cloud Messaging
# ============================================
-keep class com.google.firebase.** { *; }
-keep class com.thaiprompt.smschecker.service.FcmService { *; }

# ============================================
# Compose Navigation
# ============================================
-keep class androidx.navigation.** { *; }
-keep class androidx.compose.** { *; }

# ============================================
# Security Crypto
# ============================================
-keep class androidx.security.crypto.** { *; }
-keep class com.thaiprompt.smschecker.security.** { *; }

# ============================================
# WorkManager + Hilt Worker
# ============================================
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class com.thaiprompt.smschecker.service.** { *; }

# ============================================
# ML Kit (barcode scanning)
# ============================================
-keep class com.google.mlkit.** { *; }

# ============================================
# OkHttp
# ============================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ============================================
# Kotlin Coroutines
# ============================================
-keepclassmembers class kotlinx.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }

# ============================================
# General Android
# ============================================
-keep class * implements android.os.Parcelable { *; }
-keep class * implements java.io.Serializable { *; }
