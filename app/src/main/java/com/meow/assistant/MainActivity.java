package com.meow.assistant;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true); 
        settings.setDomStorageEnabled(true); 
        settings.setAllowFileAccess(true);   

        webView.addJavascriptInterface(new WebAppInterface(MainActivity.this), "Android");
        webView.setWebViewClient(new WebViewClient());

        webView.loadUrl("file:///android_asset/index.html");
    }

    public static class WebAppInterface {
        private Context mContext;
        
        public WebAppInterface(Context c) { 
            mContext = c; 
        }

        @JavascriptInterface
        public void saveConfig(String jsonString) {
            mContext.getSharedPreferences("weai_config", Context.MODE_PRIVATE)
                    .edit().putString("config_json", jsonString).apply();
            
            Intent intent = new Intent(mContext, MeowAccessibilityService.class);
            intent.setAction("RELOAD_CONFIG");
            mContext.startService(intent);
        }

        @JavascriptInterface
        public String loadConfig() {
            return mContext.getSharedPreferences("weai_config", Context.MODE_PRIVATE)
                    .getString("config_json", "");
        }

        @JavascriptInterface
        public void openAccessibilitySettings() {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        }

        @JavascriptInterface
        public void openBatterySettings() {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        }
    }
}
