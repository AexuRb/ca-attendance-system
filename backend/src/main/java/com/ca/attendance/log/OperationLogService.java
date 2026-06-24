package com.ca.attendance.log;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"json_encode_failed\"}";
        }
    }
}
