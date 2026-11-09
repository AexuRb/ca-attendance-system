package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.user.UserInputPolicy;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

final class BackupContactPolicy {
    private static final TypeReference<Map<String, Object>> ROW = new TypeReference<>() {};
    private static final int MAX_ISSUES = 100;

    static BackupService.ContactReview review(MultipartFile file, Path users, ObjectMapper mapper) {
        List<BackupService.ContactIssue> issues = new ArrayList<>();
        int phones = 0, qqs = 0, row = 0;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(file.getInputStream(), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            if (users != null) {
                try (JsonParser parser = mapper.createParser(users)) {
                    if (parser.nextToken() != JsonToken.START_ARRAY) throw ApiException.badRequest("成员备份格式不正确");
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        Map<String, Object> entry = parser.readValueAs(ROW); row++;
                        for (String field : List.of("phone", "qq")) {
                            if (invalid(field, entry.get(field))) {
                                if (field.equals("phone")) phones++; else qqs++;
                                if (issues.size() < MAX_ISSUES) issues.add(new BackupService.ContactIssue(row,
                                        field.equals("phone") ? "电话" : "QQ",
                                        field.equals("phone") ? "须为11位大陆手机号" : "须为5至20位数字，首位不能为0"));
                            }
                        }
                    }
                }
            }
            return new BackupService.ContactReview(HexFormat.of().formatHex(digest.digest()), phones, qqs,
                    List.copyOf(issues), phones + qqs > MAX_ISSUES);
        } catch (ApiException ex) { throw ex; }
        catch (Exception ex) { throw ApiException.badRequest("备份联系方式校验失败，请重新选择完整备份文件"); }
    }

    static Object restoreValue(String table, String field, Object value, boolean clearInvalid) {
        if (!"users".equals(table) || !(field.equals("phone") || field.equals("qq"))) return value;
        if (invalid(field, value)) {
            if (clearInvalid) return null;
            throw ApiException.badRequest("备份包含格式错误的联系方式，请先校验并确认清空后恢复");
        }
        return value;
    }

    private static boolean invalid(String field, Object value) {
        if (value == null) return false;
        if (!(value instanceof String text)) return true;
        try { normalize(field, text); return false; }
        catch (ApiException ex) { return true; }
    }

    private static String normalize(String field, String value) {
        return field.equals("phone") ? UserInputPolicy.phone(value) : UserInputPolicy.qq(value);
    }
}
