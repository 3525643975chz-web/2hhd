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
    
    // 记录上一次的文本，用于判断用户是不是在删除
    private String lastText = "";

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

        AccessibilityNodeInfo node = event.getSource();
        if (node == null || !node.isEditable() || node.isPassword()) return;

        String pkgName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        if (!config.targets.contains(pkgName)) return;

        CharSequence text = node.getText();
        if (text == null) return;
        String currentText = text.toString();

        // ================= 核心修复1：防止“删除自动回弹” =================
        // 如果当前文本比上一次文本短，且它是上一次文本的子集，说明用户正在按退格键删除
        if (currentText.length() < lastText.length() && lastText.contains(currentText)) {
            lastText = currentText; // 更新记录
            return; // 直接跳过，不进行替换和追加
        }

        // 如果文本没变，也跳过
        if (currentText.equals(lastText)) return;

        String processedText = TextProcessor.process(currentText, config);

        if (!currentText.equals(processedText)) {
            isProcessing = true;
            
            // 记录我们要替换成什么，防止死循环
            lastText = processedText;

            // ================= 核心修复2：微信/特殊应用粘贴大法 =================
            if (pkgName.equals("com.tencent.mm") || pkgName.equals("com.tencent.mobileqq")) {
                // 微信、QQ这种自绘输入框，必须用剪贴板
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("weaitext", processedText);
                clipboard.setPrimaryClip(clip);
                
                // 聚焦并粘贴
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                
                // 粘贴后，将光标移动到文本末尾
                Bundle selectionArgs = new Bundle();
                selectionArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, processedText.length());
                selectionArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, processedText.length());
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs);
                
            } else {
                // 普通应用使用直接设置
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, processedText);
                boolean success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                
                // 如果普通应用也失败了，再退回剪贴板
                if (!success) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("weaitext", processedText);
                    clipboard.setPrimaryClip(clip);
                    node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                }
            }
            
            // 延迟解除锁
            handler.postDelayed(() -> isProcessing = false, 500);
        } else {
            // 如果不需要替换，也要记录当前的文本
            lastText = currentText;
        }
    }

    @Override
    public void onInterrupt() {}
}
