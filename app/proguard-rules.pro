# R8 configuration for the release build.
#
# OkHttp, Okio, Compose, Media3 and Hilt publish their own consumer rules, so
# they must not be kept wholesale here. Broad rules would cancel most of the
# size reduction.

# WorkManager recreates workers from the class name stored in its database and
# calls this constructor reflectively. Keeping the name also lets scheduled work
# survive an update from an older, non-minified APK.
-keep class app.samehadaku.dl.data.autocheck.AutoCheckWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class app.samehadaku.dl.data.smartstorage.SmartStorageWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Enum names are persisted as JSON (download state, host, notification kind,
# cleanup reason). Renaming the constants would make existing history unreadable.
-keepclassmembers enum app.samehadaku.dl.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep readable file names and line numbers in crash reports.
-keepattributes SourceFile,LineNumberTable

# Optional TLS providers referenced by OkHttp. The app does not bundle them.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
