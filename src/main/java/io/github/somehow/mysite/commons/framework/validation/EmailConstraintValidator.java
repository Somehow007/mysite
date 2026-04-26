package io.github.somehow.mysite.commons.framework.validation;

import cn.hutool.core.util.StrUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class EmailConstraintValidator implements ConstraintValidator<ValidEmail, String> {

    private static final Pattern LOCAL_PART_PATTERN = Pattern.compile("^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+$");
    private static final Pattern DOMAIN_LABEL_PATTERN = Pattern.compile("^[a-zA-Z0-9-]+$");
    private static final Pattern TLD_PATTERN = Pattern.compile("^[a-zA-Z]+$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (StrUtil.isBlank(value)) {
            return true;
        }

        String email = value.trim();

        long atCount = email.chars().filter(ch -> ch == '@').count();
        if (atCount != 1) {
            disableDefaultConstraintAndBuildMessage(context, "邮箱地址必须包含且仅包含一个 @ 符号");
            return false;
        }

        int atIndex = email.indexOf('@');
        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex + 1);

        if (StrUtil.isBlank(localPart)) {
            disableDefaultConstraintAndBuildMessage(context, "@ 符号前不能为空");
            return false;
        }

        if (localPart.length() > 64) {
            disableDefaultConstraintAndBuildMessage(context, "邮箱本地部分长度不能超过 64 个字符");
            return false;
        }

        if (localPart.startsWith(".")) {
            disableDefaultConstraintAndBuildMessage(context, "邮箱本地部分不能以点号开头");
            return false;
        }

        if (localPart.endsWith(".")) {
            disableDefaultConstraintAndBuildMessage(context, "邮箱本地部分不能以点号结尾");
            return false;
        }

        if (localPart.contains("..")) {
            disableDefaultConstraintAndBuildMessage(context, "邮箱本地部分不能包含连续的点号");
            return false;
        }

        if (!LOCAL_PART_PATTERN.matcher(localPart).matches()) {
            disableDefaultConstraintAndBuildMessage(context, "邮箱本地部分包含不允许的字符");
            return false;
        }

        if (StrUtil.isBlank(domainPart)) {
            disableDefaultConstraintAndBuildMessage(context, "请输入有效的邮箱域名");
            return false;
        }

        if (domainPart.length() > 255) {
            disableDefaultConstraintAndBuildMessage(context, "域名部分长度不能超过 255 个字符");
            return false;
        }

        if (domainPart.startsWith("-") || domainPart.endsWith("-")) {
            disableDefaultConstraintAndBuildMessage(context, "域名部分不能以连字符开头或结尾");
            return false;
        }

        if (domainPart.startsWith(".") || domainPart.endsWith(".")) {
            disableDefaultConstraintAndBuildMessage(context, "域名部分不能以点号开头或结尾");
            return false;
        }

        if (domainPart.contains("..")) {
            disableDefaultConstraintAndBuildMessage(context, "域名部分不能包含连续的点号");
            return false;
        }

        String[] labels = domainPart.split("\\.");
        if (labels.length < 2) {
            disableDefaultConstraintAndBuildMessage(context, "域名格式不正确，请包含完整的域名（如 example.com）");
            return false;
        }

        for (String label : labels) {
            if (StrUtil.isBlank(label)) {
                disableDefaultConstraintAndBuildMessage(context, "域名部分格式不正确");
                return false;
            }
            if (label.startsWith("-") || label.endsWith("-")) {
                disableDefaultConstraintAndBuildMessage(context, "域名标签不能以连字符开头或结尾");
                return false;
            }
            if (!DOMAIN_LABEL_PATTERN.matcher(label).matches()) {
                disableDefaultConstraintAndBuildMessage(context, "域名部分包含不允许的字符");
                return false;
            }
        }

        String tld = labels[labels.length - 1];
        if (tld.length() < 2) {
            disableDefaultConstraintAndBuildMessage(context, "顶级域名至少需要 2 个字符");
            return false;
        }

        if (!TLD_PATTERN.matcher(tld).matches()) {
            disableDefaultConstraintAndBuildMessage(context, "顶级域名只能包含字母");
            return false;
        }

        return true;
    }

    private void disableDefaultConstraintAndBuildMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();
    }
}
