# Speed Launcher proguard
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}
-keep class org.cheipstudio.speedlauncher.** { *; }
