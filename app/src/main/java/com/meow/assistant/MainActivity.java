package com.meow.assistant;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 强制窗口背景透明，完美配合网页淡入
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // 绑定 JS 桥接
        webView.addJavascriptInterface(new WebBridge(this), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        
        // 加载 assets 里的网页
        webView.loadUrl("file:///android_asset/index.html");
        
        setContentView(webView);

        // 状态栏透明
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
    }

    // 核心桥梁（全部使用基础 Activity 传参，彻底告别找不到符号报错）
    private class WebBridge {
        private Activity mActivity;

        public WebBridge(Activity activity) {
            this.mActivity = activity;
        }

        @JavascriptInterface
        public void openAccessibilitySettings() {
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    mActivity.startActivity(intent);
                }
            });
        }

        @JavascriptInterface
        public void openBatterySettings() {
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    mActivity.startActivity(intent);
                }
            });
        }

        @JavascriptInterface
        public String getInstalledApps() {
            try {
                PackageManager pm = mActivity.getPackageManager();
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);

                JSONArray jsonArray = new JSONArray();
                Set<String> addedPkgs = new HashSet<>();

                for (ResolveInfo info : resolveInfos) {
                    String pkgName = info.activityInfo.packageName;
                    if (pkgName.equals(mActivity.getPackageName())) continue; 
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
                return "[]";
            }
        }

        @JavascriptInterface
        public void saveConfig(String json) {
            // 存到本地，供引擎读取
            mActivity.getSharedPreferences("weai_cfg", Context.MODE_PRIVATE).edit().putString("data", json).apply();
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(mActivity, "规则保存成功", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public String loadConfig() {
            return mActivity.getSharedPreferences("weai_cfg", Context.MODE_PRIVATE).getString("data", "");
        }
    }
}
