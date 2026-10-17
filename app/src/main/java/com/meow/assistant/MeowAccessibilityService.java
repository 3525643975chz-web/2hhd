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
    
    // 核心防御：记录最后一次替换后的文本
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
        if (isProcessing) return; 

        AccessibilityNodeInfo node = findEditText(event.getSource());
        if (node == null || !node.isEditable() || node.isPassword()) return;

        String pkgName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        if (!config.targets.contains(pkgName)) return;

        CharSequence text = node.getText();
        if (text == null) return;
        String currentText = text.toString();

        // ================= 终极防死循环 =================
        // 1. 如果当前文本等于我们刚刚替换完的文本，说明是我们自己触发的，直接放过
        if (currentText.equals(lastProcessedText)) {
            return; 
        }

        // 2. 如果用户正在按退格删除（当前长度比上次短，且包含关系），立刻放过，绝不自动补后缀
        if (currentText.length() < lastProcessedText.length() && lastProcessedText.contains(currentText)) {
            lastProcessedText = currentText; // 更新记录
            return;
        }

        String processedText = TextProcessor.process(currentText, config);

        // 3. 如果不需要替换，记录当前文本并退出
        if (currentText.equals(processedText)) {
            lastProcessedText = currentText;
            return;
        }

        // ================= 执行替换 =================
        isProcessing = true;
        lastProcessedText = processedText; // 提前记录预期结果

        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, processedText);
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        
        // 恢复光标到末尾
        Bundle selectionArgs = new Bundle();
        selectionArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, processedText.length());
        selectionArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, processedText.length());
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs);

        // 延迟解锁
        handler.postDelayed(() -> isProcessing = false, 300);
    }

    // 向下寻找真正的输入框
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
