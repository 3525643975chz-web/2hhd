package com.meow.assistant;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WebUIActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 强制透明防闪屏
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // 注入桥接接口，把当前的 Context 传进去
        webView.addJavascriptInterface(new WebBridge(this), "AndroidBridge");

        // 防止网页跳转到外部浏览器
        webView.setWebViewClient(new WebViewClient());
        
        // 加载 assets 里的前端页面
        webView.loadUrl("file:///android_asset/index.html");
        
        setContentView(webView);

        // 透明状态栏和导航栏
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
    }

    // 核心通信桥梁（使用 Context 传参，解决所有找不到符号的报错）
    private class WebBridge {
        private Context mContext;

        public WebBridge(Context context) {
            this.mContext = context;
        }

        @JavascriptInterface
        public void openAccessibilitySettings() {
            ((Activity) mContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    mContext.startActivity(intent);
                }
            });
        }

        @JavascriptInterface
        public void openBatterySettings() {
            ((Activity) mContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    mContext.startActivity(intent);
                }
            });
        }

        @JavascriptInterface
        public void openNotificationSettings() {
            ((Activity) mContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, mContext.getPackageName());
                    mContext.startActivity(intent);
                }
            });
        }

        @JavascriptInterface
        public void openAutoStartSettings() {
            ((Activity) mContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + mContext.getPackageName()));
                    mContext.startActivity(intent);
                }
            });
        }

        @JavascriptInterface
        public void setBackgroundMode(boolean enabled) {
            mContext.getSharedPreferences("meow_prefs", Context.MODE_PRIVATE).edit().putBoolean("bg_mode_hide", enabled).apply();
        }

        @JavascriptInterface
        public String getInstalledApps() {
            try {
                PackageManager pm = mContext.getPackageManager();
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);

                JSONArray jsonArray = new JSONArray();
                Set<String> addedPkgs = new HashSet<>();

                for (ResolveInfo info : resolveInfos) {
                    String pkgName = info.activityInfo.packageName;
                    if (pkgName.equals(mContext.getPackageName())) continue; // 排除自己
                    if (addedPkgs.contains(pkgName)) continue;

                    ApplicationInfo appInfo = pm.getApplicationInfo(pkgName, 0);
                    JSONObject obj = new JSONObject();
                    obj.put("name", pm.getApplicationLabel(appInfo).toString());
                    obj.put("pkg", pkgName);
                    jsonArray.put(obj);
                    addedPkgs.add(pkgName);
                }
                return jsonArray.toString();
            } catch (Exception e) {
                e.printStackTrace();
                return "[]";
            }
        }

        @JavascriptInterface
        public void saveConfig(String json) {
            mContext.getSharedPreferences("meow_prefs", Context.MODE_PRIVATE).edit().putString("weai_config", json).apply();
            
            ((Activity) mContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, "规则已保存", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public String loadConfig() {
            return mContext.getSharedPreferences("meow_prefs", Context.MODE_PRIVATE).getString("weai_config", "");
        }
    }
}
