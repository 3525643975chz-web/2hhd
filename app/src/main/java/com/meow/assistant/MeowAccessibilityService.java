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
    
    // 记录上一次生效的完整文本（带后缀的）
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

        // ================= 终极防死循环 & 防删除回弹 =================
        // 1. 如果当前文本等于我们上次处理完的文本，说明是我们自己触发的，直接放过
        if (currentText.equals(lastProcessedText)) {
            return; 
        }

        // 2. 如果用户正在按退格删除（当前字数比上次少，且剩下的内容是上次内容的一部分），放过
        if (currentText.length() < lastProcessedText.length() && lastProcessedText.contains(currentText)) {
            lastProcessedText = currentText; // 记录用户的删除动作
            return;
        }

        // 3. 执行替换逻辑
        String processedText = TextProcessor.process(currentText, config);

        // 如果处理后没变化，更新记录并退出
        if (currentText.equals(processedText)) {
            lastProcessedText = currentText;
            return;
        }

        // 执行文字替换
        isProcessing = true;
        lastProcessedText = processedText; // 记录预期结果

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

    // 向下寻找真正的输入框（兼容部分应用深层嵌套）
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
