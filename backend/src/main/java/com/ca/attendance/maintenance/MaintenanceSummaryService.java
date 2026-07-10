package com.ca.attendance.maintenance;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MaintenanceSummaryService {
    private final JdbcTemplate jdbc;
    private final BackupService backups;

    public MaintenanceSummaryService(JdbcTemplate jdbc, BackupService backups) {
        this.jdbc = jdbc;
        this.backups = backups;
    }

    public MaintenanceSummary summary() {
        Role role = AuthContext.current().role();
        if (role != Role.PRESIDENT && role != Role.ADMIN) {
            throw ApiException.forbidden("只有会长或管理员可以查看数据中心");
        }

        List<BackupService.BackupItem> backupItems = backups.list();
        long backupSize = backupItems.stream().mapToLong(BackupService.BackupItem::size).sum();
        BackupService.BackupItem latestBackup = backupItems.isEmpty() ? null : backupItems.get(0);

        List<DataMetric> datasets = List.of(
                new DataMetric(
                        "users",
                        "成员档案",
                        count("SELECT COUNT(*) FROM users"),
                        count("SELECT COUNT(*) FROM users WHERE status = 'ACTIVE'") + " 个启用账号",
                        "steady"
                ),
                new DataMetric(
                        "attendance_records",
                        "签到记录",
                        count("SELECT COUNT(*) FROM attendance_records"),
                        count("""
                                SELECT COUNT(*) FROM attendance_records
                                WHERE check_in_status = 'PENDING' OR check_out_status = 'PENDING'
                                """) + " 条待审核",
                        "attention"
                ),
                new DataMetric(
                        "training_sessions",
                        "培训数据",
                        count("SELECT COUNT(*) FROM training_sessions WHERE status <> 'ARCHIVED'"),
                        count("SELECT COUNT(*) FROM training_participants") + " 条参与记录",
                        "steady"
                ),
                new DataMetric(
                        "duty_schedule_slots",
                        "排班表",
                        count("SELECT COUNT(*) FROM duty_schedule_slots WHERE status = 'ACTIVE'"),
                        count("SELECT COUNT(*) FROM duty_schedule_slots WHERE status = 'ACTIVE' AND enabled = TRUE") + " 段公开显示",
                        "steady"
                ),
                new DataMetric(
                        "repair_cases",
                        "维修事务",
                        count("SELECT COUNT(*) FROM repair_cases WHERE status <> 'CANCELED'"),
                        count("""
                                SELECT COUNT(*) FROM repair_cases
                                WHERE status IN ('RECEIVED', 'DIAGNOSING', 'REPAIRING', 'WAITING_PICKUP')
                        """) + " 件处理中",
                        "attention"
                ),
                new DataMetric(
                        "operation_logs",
                        "操作日志",
                        count("SELECT COUNT(*) FROM operation_logs"),
                        backupItems.size() + " 个备份文件",
                        role == Role.ADMIN ? "sensitive" : "steady"
                )
        );

        BackupOverview backupOverview = new BackupOverview(
                backupItems.size(),
                backupSize,
                latestBackup == null ? null : latestBackup.filename(),
                latestBackup == null ? null : latestBackup.createdAt(),
                latestBackup == null ? 0 : latestBackup.size()
        );
        return new MaintenanceSummary(datasets, backupOverview, LocalDateTime.now());
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    public record MaintenanceSummary(List<DataMetric> datasets, BackupOverview backups, LocalDateTime generatedAt) {
    }

    public record DataMetric(String key, String label, long total, String detail, String tone) {
    }

    public record BackupOverview(int count, long totalSize, String latestFilename, Instant latestCreatedAt, long latestSize) {
    }
}
