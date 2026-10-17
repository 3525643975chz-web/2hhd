package com.meow.assistant;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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

        // 读取系统给的“修改前”和“修改后”的文本
        CharSequence beforeSeq = event.getBeforeText();
        String beforeText = beforeSeq != null ? beforeSeq.toString() : "";

        // 【修复4】如果用户正在输入拼音（英文字母增加），先放过，等选字了（变成中文）再处理
        if (currentText.length() > beforeText.length()) {
            String addedText = currentText.substring(beforeText.length());
            if (addedText.matches("[a-zA-Z]+")) { // 如果新增的是纯英文字母（拼音阶段）
                return;
            }
        }

        // 防套娃拦截
        if (currentText.equals(lastProcessedText)) return; 

        // 删除动作拦截
        if (currentText.length() < lastProcessedText.length() && lastProcessedText.contains(currentText)) {
            lastProcessedText = currentText; 
            return;
        }

        String processedText = TextProcessor.process(currentText, config);

        if (currentText.equals(processedText)) {
            lastProcessedText = currentText;
            return;
        }

        // 执行替换
        isProcessing = true;
        lastProcessedText = processedText; 
        
        int selectionStart = node.getTextSelectionStart();
        int selectionEnd = node.getTextSelectionEnd();

        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, processedText);
        boolean success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        
        // 兼容微信
        if (!success) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("weaitext", processedText);
            clipboard.setPrimaryClip(clip);
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            
            Bundle selectionArgs = new Bundle();
            selectionArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, processedText.length());
            selectionArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, processedText.length());
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs);
        } else {
            // 恢复光标
            if (selectionStart >= 0 && selectionEnd >= 0) {
                Bundle selectionArgs = new Bundle();
                int newStart = Math.min(selectionStart + (processedText.length() - currentText.length()), processedText.length());
                int newEnd = Math.min(selectionEnd + (processedText.length() - currentText.length()), processedText.length());
                selectionArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, Math.max(0, newStart));
                selectionArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, Math.max(0, newEnd));
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs);
            }
        }
        
        handler.postDelayed(() -> isProcessing = false, 400);
    }

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
