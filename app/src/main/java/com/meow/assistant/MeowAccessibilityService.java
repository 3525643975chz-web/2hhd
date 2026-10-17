package com.meow.assistant;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;

public class MeowAccessibilityService extends AccessibilityService {
    private CatConfig config = new CatConfig();
    private boolean isProcessing = false; // 防止死循环的关键锁
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        loadConfig();
    }

    @Override
    public int onStartCommand(android.content.Intent intent, int flags, int startId) {
        if (intent != null && "RELOAD_CONFIG".equals(intent.getAction())) {
            loadConfig();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    // 解析前端存过来的 JSON 数据
    private void loadConfig() {
        SharedPreferences prefs = getSharedPreferences("weai_config", MODE_PRIVATE);
        String jsonStr = prefs.getString("config_json", "");
        if (jsonStr.isEmpty()) return;

        try {
            JSONObject json = new JSONObject(jsonStr);
            config.appendEnabled = json.optBoolean("appendEnabled", true);
            config.suffix = json.optString("suffix", "喵~");

            config.targets.clear();
            JSONArray targetsArr = json.optJSONArray("targets");
            if (targetsArr != null) {
                for (int i = 0; i < targetsArr.length(); i++) {
                    config.targets.add(targetsArr.getString(i));
                }
            }

            config.rules.clear();
            JSONArray rulesArr = json.optJSONArray("rules");
            if (rulesArr != null) {
                for (int i = 0; i < rulesArr.length(); i++) {
                    JSONObject r = rulesArr.getJSONObject(i);
                    CatConfig.Rule rule = new CatConfig.Rule();
                    rule.from = r.optString("from");
                    rule.to = r.optString("to");
                    config.rules.add(rule);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 只监听文本改变事件
        if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return;
        
        // 如果正在处理，直接拦截，防止自己触发自己的死循环
        if (isProcessing) return; 

        AccessibilityNodeInfo node = event.getSource();
        // 如果节点为空、不可编辑、或者是密码框，直接返回
        if (node == null || !node.isEditable() || node.isPassword()) return;

        // 检查当前 App 是否在目标领地内
        String pkgName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        if (!config.targets.contains(pkgName)) return;

        CharSequence text = node.getText();
        if (text == null) return;
        String originalText = text.toString();

        // 调用 TextProcessor 进行处理
        String processedText = TextProcessor.process(originalText, config);

        // 如果文本改变了，写回输入框
        if (!originalText.equals(processedText)) {
            isProcessing = true; // 上锁
            
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, processedText);
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            
            // 延迟 200 毫秒解锁，确保系统事件队列清空
            handler.postDelayed(() -> isProcessing = false, 200);
        }
    }

    @Override
    public void onInterrupt() {}
}
