package com.ca.attendance.settings;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.JdbcWriteChecks;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AppearanceService {
    private static final String SETTING_KEY = "UI_APPEARANCE";
    private static final int CONTRACT_VERSION = 1;

    private final JdbcTemplate jdbc;
    private final OperationLogService logs;

    public AppearanceService(JdbcTemplate jdbc, OperationLogService logs) {
        this.jdbc = jdbc;
        this.logs = logs;
    }

    public AppearanceSetting current() {
        List<String> values = jdbc.query(
                "SELECT setting_value FROM app_settings WHERE setting_key = ?",
                (resultSet, rowNumber) -> resultSet.getString(1),
                SETTING_KEY
        );
        Appearance appearance = values.isEmpty() ? Appearance.CLASSIC : parse(values.getFirst());
        return new AppearanceSetting(appearance, CONTRACT_VERSION);
    }

    @Transactional
    public AppearanceSetting update(UpdateAppearanceRequest request) {
        AuthUser current = AuthContext.current();
        if (current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有管理员可以修改全局界面");
        }
        if (request == null || request.appearance() == null) {
            throw ApiException.badRequest("请选择界面外观");
        }

        AppearanceSetting before = current();
        AppearanceSetting after = new AppearanceSetting(request.appearance(), CONTRACT_VERSION);
        int affectedRows = jdbc.update("""
                INSERT INTO app_settings (setting_key, setting_value, description, updated_by)
                VALUES (?, ?, '全局界面外观', ?)
                ON CONFLICT (setting_key) DO UPDATE SET
                  setting_value = excluded.setting_value,
                  description = excluded.description,
                  updated_by = excluded.updated_by,
                  updated_at = datetime('now', 'localtime')
                """, SETTING_KEY, after.appearance().name(), current.id());
        JdbcWriteChecks.requireOne(affectedRows, "全局界面保存失败，请刷新后重试");
        logs.log(
                "UPDATE_UI_APPEARANCE",
                "app_settings",
                null,
                before,
                after,
                "切换全局界面"
        );
        return after;
    }

    private Appearance parse(String value) {
        if (value == null || value.isBlank()) return Appearance.CLASSIC;
        try {
            return Appearance.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Appearance.CLASSIC;
        }
    }

    public enum Appearance {
        CLASSIC,
        EDITORIAL,
        SPATIAL
    }

    public record AppearanceSetting(Appearance appearance, int version) {
    }

    public record UpdateAppearanceRequest(@NotNull Appearance appearance) {
    }
}
