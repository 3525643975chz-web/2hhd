package com.meow.assistant;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
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

        // 获取修改前的文本
        CharSequence beforeSeq = event.getBeforeText();
        String beforeText = beforeSeq != null ? beforeSeq.toString() : "";

        // 【核心修复 1】如果用户正在输入拼音（纯英文字母），直接放过，等选词了再处理
        if (currentText.length() > beforeText.length()) {
            String addedText = currentText.substring(beforeText.length());
            if (addedText.matches("[a-zA-Z]+")) { 
                return;
            }
        }

        // 【核心修复 2】防套娃拦截
        if (currentText.equals(lastProcessedText)) return; 

        // 【核心修复 3】删除动作拦截
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
        
        // 【核心修复 4】针对微信、QQ等顽固应用，直接使用剪贴板粘贴法
        boolean isHardApp = pkgName.equals("com.tencent.mm") || pkgName.equals("com.tencent.mobileqq");
        boolean success = false;

        if (!isHardApp) {
            // 普通应用：优先使用直接赋值（速度快，无弹窗）
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, processedText);
            success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        }

        if (!success) {
            // 如果普通赋值失败，或者遇到微信/QQ，强制使用剪贴板注入
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("weaitext", processedText);
            clipboard.setPrimaryClip(clip);
            
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            
            success = true;
        }

        // 【核心修复 5】智能光标回填
        if (success) {
            // 计算新的光标位置：如果是在末尾打字，光标应该在 newText 末尾；
            // 如果是在中间插入，光标应该在插入词之后。
            int newCursorPos;
            if (selectionStart == -1 || selectionStart >= currentText.length()) {
                newCursorPos = processedText.length(); // 光标跑到最后
            } else {
                // 粗略计算长度差
                int diff = processedText.length() - currentText.length();
                newCursorPos = Math.min(Math.max(0, selectionStart + diff), processedText.length());
            }

            Bundle selectionArgs = new Bundle();
            selectionArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursorPos);
            selectionArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursorPos);
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs);
        }
        
        // 延迟解锁时间延长到 500ms，给输入法和剪贴板充足的缓冲时间
        handler.postDelayed(() -> isProcessing = false, 500);
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
