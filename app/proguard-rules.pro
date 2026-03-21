# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK directory.

# Keep application classes
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.view.View

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
