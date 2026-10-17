package com.meow.assistant;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class MeowAccessibilityService extends AccessibilityService {
    private CatConfig config = new CatConfig();
    private boolean isProcessing = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private String lastProcessedText = "";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        reloadConfig();
    }

    @Override
    public int onStartCommand(android.content.Intent intent, int flags, int startId) {
        if (intent != null && "RELOAD_CONFIG".equals(intent.getAction())) {
            reloadConfig();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void reloadConfig() {
        config = CatConfig.load(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return;
        
        if (!config.enabled) return;
        if (isProcessing) return; 

        AccessibilityNodeInfo node = findEditText(event.getSource());
        if (node == null || !node.isEditable() || node.isPassword()) return;

        String pkgName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        if (!config.targets.contains(pkgName)) return;

        CharSequence text = node.getText();
        if (text == null) return;
        String currentText = text.toString();

        if (currentText.isEmpty()) return;

        // 读取修改前的文本
        CharSequence beforeSeq = event.getBeforeText();
        String beforeText = beforeSeq != null ? beforeSeq.toString() : "";

        // 拦截拼音输入阶段（纯英文字母增加时先放过）
        if (currentText.length() > beforeText.length()) {
            String addedText = currentText.substring(beforeText.length());
            if (addedText.matches("[a-zA-Z]+")) { 
                return;
            }
        }

        // 防套娃 & 防删除拦截
        if (currentText.equals(lastProcessedText)) return; 
        if (currentText.length() < lastProcessedText.length() && lastProcessedText.contains(currentText)) {
            lastProcessedText = currentText; 
            return;
        }

        String processedText = TextProcessor.process(currentText, config);

        if (currentText.equals(processedText)) {
            lastProcessedText = currentText;
            return;
        }

        // ================= 执行无痕替换 =================
        isProcessing = true;
        lastProcessedText = processedText; 
        
        // 使用标准的节点直接赋值，不需要剪贴板
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, processedText);
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        
        // 延迟解锁，时间拉长到 400ms，彻底杜绝输入法干扰
        handler.postDelayed(() -> isProcessing = false, 400);
    }

    // 深度遍历寻找真正的 EditText 节点
    private AccessibilityNodeInfo findEditText(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findEditText(child);
            if (result != null) return result;
        }
        return null;
    }

    @Override
    public void onInterrupt() {}
}
