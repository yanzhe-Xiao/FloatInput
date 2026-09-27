# FloatInput Proguard Rules for R8 Full Mode

-repackageclasses 'com.yxiao.floatinput.internal'
-allowaccessmodification

# Optimization settings
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Keep ViewBinding generated classes
-keep class com.yxiao.floatinput.databinding.** { *; }

# Keep Material & AndroidX components used in reflections or layouts
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

-keepclassmembers class * extends android.app.Service {
    public <init>();
}

-keepclassmembers class * extends android.app.Activity {
    public <init>();
}
