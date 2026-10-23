# R8 rules for the release build (NUD-118). The libraries (Hilt, Room, WorkManager, Retrofit, OkHttp,
# kotlinx.serialization, Firebase, Coil) ship their own consumer rules; only what they miss goes here.

# Crash reports and the app's diagnostics name the exception (DiagnosticsInterceptor logs its simple name).
-keepnames class * extends java.lang.Throwable

# Readable line numbers in Crashlytics stack traces; the mapping file restores the names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Retrofit's kotlinx converter finds a response's serializer from the method's generic signature at run time. A model
# no code constructs (Ok, the reply of sendOtp) is otherwise removed and its signature rewritten to
# Response<Object>, which fails as "Unable to create converter for class java.lang.Object" (seen in the minified
# smoke run). Keeping the generated models lets kotlinx.serialization's own rules keep their serializers.
-keep,allowobfuscation class app.nuddle.api.model.**
