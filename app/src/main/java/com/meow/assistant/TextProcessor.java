package com.meow.assistant;

public class TextProcessor {
    public static String process(String original, CatConfig config) {
        if (original == null || original.isEmpty() || config == null) return original;
        
        // 【修复1】拦截常见的输入框占位符（如豆包的“发消息...”）
        if (original.equals("发消息...") || original.equals("发消息…") || original.equals("发送消息...")) {
            return original; // 是占位符，直接原样返回，不处理
        }
        
        String result = original;
        String suffix = config.suffix;
        boolean append = config.appendEnabled;
        
        // 【修复2】开启后缀时，把文本里所有的旧后缀全部“拔掉”
        if (append && suffix != null && !suffix.isEmpty()) {
            result = result.replace(suffix, ""); // 无论后缀在中间还是末尾，统统干掉
        }

        // 执行规则替换
        for (CatConfig.Rule rule : config.rules) {
            if (result.contains(rule.from)) {
                result = result.replace(rule.from, rule.to);
            }
        }
        
        // 【修复3】替换完成后，重新把后缀加在最末尾
        if (append && suffix != null && !suffix.isEmpty()) {
            if (!result.isEmpty()) {
                result = result + suffix;
            }
        }
        
        return result;
    }
}
