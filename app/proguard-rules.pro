# Add project specific ProGuard rules here.
# Keep JavaScript interface methods
-keepclassmembers class com.maverde.crunchybadges.JavaScriptBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebView
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}

# Keep Activity classes
-keep public class * extends android.app.Activity
-keep public class * extends androidx.appcompat.app.AppCompatActivity
