package com.ca.attendance.user;

import com.ca.attendance.common.ApiException;

import java.util.regex.Pattern;

public final class UserInputPolicy {
    public static final int STUDENT_NO_MIN_LENGTH = 6;
    public static final int STUDENT_NO_MAX_LENGTH = 32;
    public static final int NAME_MAX_LENGTH = 64;
    public static final int PASSWORD_MIN_LENGTH = 6;
    public static final int PASSWORD_MAX_LENGTH = 64;
    public static final int PHONE_MAX_LENGTH = 64;
    public static final int COLLEGE_MAX_LENGTH = 128;
    public static final int GRADE_MAX_LENGTH = 16;
    public static final int QQ_MAX_LENGTH = 32;
    public static final int REASON_MAX_LENGTH = 500;

    private static final Pattern NEW_STUDENT_NO = Pattern.compile("\\d{6,32}");

    private UserInputPolicy() {
    }

    public static String newStudentNo(String value) {
        String normalized = accountReference(value);
        if (!NEW_STUDENT_NO.matcher(normalized).matches()) {
            throw ApiException.badRequest("学号必须为 6 至 32 位纯数字");
        }
        return normalized;
    }

    public static String accountReference(String value) {
        String normalized = required(value, "学号不能为空");
        if (normalized.length() > 64) {
            throw ApiException.badRequest("账号不能超过 64 个字符");
        }
        return normalized;
    }

    public static String name(String value) {
        String normalized = required(value, "姓名不能为空");
        if (normalized.length() > NAME_MAX_LENGTH) {
            throw ApiException.badRequest("姓名不能超过 64 个字符");
        }
        return normalized;
    }

    public static String password(String value) {
        int length = value == null ? 0 : value.length();
        if (value == null || value.isBlank()
                || length < PASSWORD_MIN_LENGTH || length > PASSWORD_MAX_LENGTH) {
            throw ApiException.badRequest("密码长度必须为 6 至 64 个字符");
        }
        return value;
    }

    public static String defaultPassword(String studentNo) {
        String normalized;
        try {
            normalized = newStudentNo(studentNo);
        } catch (ApiException ex) {
            throw ApiException.badRequest("该历史账号不符合当前学号规则，请手动输入 6 至 64 位新密码");
        }
        return normalized.substring(normalized.length() - 6);
    }

    public static String phone(String value) {
        return optional(value, PHONE_MAX_LENGTH, "联系方式");
    }

    public static String college(String value) {
        return optional(value, COLLEGE_MAX_LENGTH, "学院");
    }

    public static String grade(String value) {
        return optional(value, GRADE_MAX_LENGTH, "年级");
    }

    public static String qq(String value) {
        return optional(value, QQ_MAX_LENGTH, "QQ");
    }

    public static String reason(String value) {
        return optional(value, REASON_MAX_LENGTH, "操作原因");
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(message);
        }
        return value.trim();
    }

    private static String optional(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw ApiException.badRequest(label + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }
}
