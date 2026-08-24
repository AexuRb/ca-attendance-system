package com.ca.attendance.log;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.user.UserRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OperationLogService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OperationLogService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void log(String actionType, String targetType, Long targetId, Object beforeData, Object afterData, String reason) {
        AuthUser operator = AuthContext.current();
        jdbc.update("""
                INSERT INTO operation_logs (
                  operator_user_id, operator_student_no, operator_name, action_type, target_type,
                  target_id, before_data, after_data, reason
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                operator.id(),
                operator.studentNo(),
                operator.name(),
                actionType,
                targetType,
                targetId,
                toJson(beforeData),
                toJson(afterData),
                reason
        );
    }

    public void logExport(String exportType, String exportLabel, Map<String, ?> filters,
                          int rowCount, String filename) {
        logExport(exportType, exportLabel, filters, rowCount, filename, Map.of());
    }

    public void logExport(String exportType, String exportLabel, Map<String, ?> filters,
                          int rowCount, String filename, Map<String, ?> details) {
        AuthUser operator = AuthContext.current();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exportType", exportType);
        payload.put("exportLabel", exportLabel);
        payload.put("operatorRole", operator.role().name());
        payload.put("filters", filters == null ? Map.of() : filters);
        payload.put("rows", Math.max(0, rowCount));
        payload.put("filename", filename);
        if (details != null && !details.isEmpty()) {
            payload.put("details", details);
        }
        log("EXPORT_DATA", "data_exports", null, null, payload, "导出" + exportLabel);
    }

    public void logAuthentication(AuthenticationOutcome outcome,
                                  boolean remote,
                                  UserRepository.UserLoginRow user,
                                  String attemptedAccount,
                                  String ipAddress,
                                  String userAgent,
                                  String reason) {
        int inserted = jdbc.update("""
                INSERT INTO operation_logs (
                  operator_user_id, operator_student_no, operator_name, action_type, target_type,
                  target_id, reason, ip_address, user_agent
                )
                VALUES (?, ?, ?, ?, 'authentication', ?, ?, ?, ?)
                """,
                user == null ? null : user.id(),
                user == null ? limited(attemptedAccount, 64) : user.studentNo(),
                user == null ? null : user.name(),
                (remote ? "REMOTE_LOGIN_" : "LOCAL_LOGIN_") + outcome.name(),
                user == null ? null : user.id(),
                limited(reason, 255),
                limited(ipAddress, 255),
                limited(userAgent, 255)
        );
        if (inserted != 1) {
            throw new IllegalStateException("认证审计日志写入失败");
        }
    }

    private String limited(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String clean = value.replace("\r", "").replace("\n", "").trim();
        return clean.substring(0, Math.min(clean.length(), maxLength));
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            return "{\"error\":\"json_encode_failed\"}";
        }
    }

    public enum AuthenticationOutcome {
        SUCCESS,
        FAILURE,
        LOCKED
    }
}
