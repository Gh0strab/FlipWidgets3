# Add project specific ProGuard rules here.
-keep class * extends android.appwidget.AppWidgetProvider
-keepclassmembers class * extends android.appwidget.AppWidgetProvider {
    public <methods>;
}
