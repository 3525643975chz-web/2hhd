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
        
        // 直接使用代码创建 WebView，不需要去改 xml 布局文件了
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true); // 开启 JS
        settings.setDomStorageEnabled(true); // 开启 LocalStorage
        settings.setAllowFileAccess(true);   // 允许读取本地文件

        // 注入 JS 接口，名字叫 Android，前端用 window.Android 调用
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
        webView.setWebViewClient(new WebViewClient());

        // 加载你放在 assets 里的精美页面
        webView.loadUrl("file:///android_asset/index.html");
    }

    // ================= 内部类：JS 桥接器 =================
    public class WebAppInterface {
        private Context mContext;
        WebAppInterface(Context c) { mContext = c; }

        @JavascriptInterface
        public void saveConfig(String jsonString) {
            // 保存前端传来的 JSON 配置
            mContext.getSharedPreferences("weai_config", Context.MODE_PRIVATE)
                    .edit().putString("config_json", jsonString).apply();
            
            // 发送广播或启动服务，通知无障碍服务重新加载配置（如果需要热更新）
            Intent intent = new Intent(mContext, MeowAccessibilityService.class);
            intent.setAction("RELOAD_CONFIG");
            mContext.startService(intent);
        }

        @JavascriptInterface
        public String loadConfig() {
            // 给前端返回保存的配置
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
