package com.ca.attendance.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class SchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc;

    public SchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS training_sessions (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  title VARCHAR(200) NOT NULL COMMENT '培训标题',
                  training_date DATE NOT NULL COMMENT '培训日期',
                  start_time TIME NULL COMMENT '开始时间',
                  end_time TIME NULL COMMENT '结束时间',
                  location VARCHAR(120) NULL COMMENT '培训地点',
                  speaker VARCHAR(120) NULL COMMENT '主讲人',
                  description VARCHAR(500) NULL COMMENT '培训说明',
                  status VARCHAR(20) NOT NULL DEFAULT 'PLANNED' COMMENT 'PLANNED, COMPLETED, CANCELED, ARCHIVED',
                  created_by BIGINT UNSIGNED NULL COMMENT '创建人',
                  updated_by BIGINT UNSIGNED NULL COMMENT '最后修改人',
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_training_sessions_date_status (training_date, status),
                  KEY idx_training_sessions_created_by (created_by),
                  KEY idx_training_sessions_updated_by (updated_by),
                  CONSTRAINT fk_training_sessions_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
                  CONSTRAINT fk_training_sessions_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
                  CONSTRAINT chk_training_sessions_status CHECK (status IN ('PLANNED', 'COMPLETED', 'CANCELED', 'ARCHIVED'))
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='培训场次表'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS training_participants (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  session_id BIGINT UNSIGNED NOT NULL COMMENT '培训场次 ID',
                  user_id BIGINT UNSIGNED NULL COMMENT '关联成员 ID，外部人员可为空',
                  student_no_snapshot VARCHAR(32) NOT NULL COMMENT '学号快照',
                  name_snapshot VARCHAR(64) NOT NULL COMMENT '姓名快照',
                  attendance_status VARCHAR(20) NOT NULL DEFAULT 'PRESENT' COMMENT 'PRESENT, ABSENT, LEAVE',
                  duration_hours DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '计入值班统计的培训时长',
                  remark VARCHAR(500) NULL COMMENT '备注',
                  source VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL, IMPORT',
                  created_by BIGINT UNSIGNED NULL COMMENT '创建人',
                  updated_by BIGINT UNSIGNED NULL COMMENT '最后修改人',
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_training_participants_session_student (session_id, student_no_snapshot),
                  KEY idx_training_participants_user (user_id),
                  KEY idx_training_participants_status (attendance_status),
                  KEY idx_training_participants_created_by (created_by),
                  KEY idx_training_participants_updated_by (updated_by),
                  CONSTRAINT fk_training_participants_session FOREIGN KEY (session_id) REFERENCES training_sessions (id) ON DELETE CASCADE,
                  CONSTRAINT fk_training_participants_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
                  CONSTRAINT fk_training_participants_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
                  CONSTRAINT fk_training_participants_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
                  CONSTRAINT chk_training_participants_status CHECK (attendance_status IN ('PRESENT', 'ABSENT', 'LEAVE')),
                  CONSTRAINT chk_training_participants_source CHECK (source IN ('MANUAL', 'IMPORT'))
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='培训参与记录表'
                """);
        addColumnIfMissing(
                "training_participants",
                "duration_hours",
                "ALTER TABLE training_participants ADD COLUMN duration_hours DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '计入值班统计的培训时长' AFTER attendance_status"
        );
        jdbc.update("""
                UPDATE training_participants p
                JOIN training_sessions s ON s.id = p.session_id
                SET p.duration_hours = ROUND(TIME_TO_SEC(TIMEDIFF(s.end_time, s.start_time)) / 3600, 2)
                WHERE p.duration_hours = 0
                  AND p.attendance_status = 'PRESENT'
                  AND s.start_time IS NOT NULL
                  AND s.end_time IS NOT NULL
                  AND s.end_time > s.start_time
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS duty_schedule_slots (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  weekday TINYINT UNSIGNED NOT NULL COMMENT '星期：1=周一，2=周二，...，7=周日',
                  start_time TIME NULL COMMENT '开始时间',
                  end_time TIME NULL COMMENT '结束时间',
                  title VARCHAR(100) NOT NULL DEFAULT '值班' COMMENT '排班标题',
                  location VARCHAR(120) NULL COMMENT '值班地点',
                  note VARCHAR(500) NULL COMMENT '备注',
                  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
                  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, ARCHIVED',
                  created_by BIGINT UNSIGNED NULL COMMENT '创建人',
                  updated_by BIGINT UNSIGNED NULL COMMENT '最后修改人',
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_duty_schedule_weekday_enabled (weekday, enabled, status),
                  KEY idx_duty_schedule_created_by (created_by),
                  KEY idx_duty_schedule_updated_by (updated_by),
                  CONSTRAINT fk_duty_schedule_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
                  CONSTRAINT fk_duty_schedule_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
                  CONSTRAINT chk_duty_schedule_weekday CHECK (weekday BETWEEN 1 AND 7),
                  CONSTRAINT chk_duty_schedule_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='值班周排班时间段表'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS duty_schedule_assignees (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  slot_id BIGINT UNSIGNED NOT NULL COMMENT '排班时间段 ID',
                  user_id BIGINT UNSIGNED NULL COMMENT '关联成员 ID，手动填写外部姓名时可为空',
                  student_no_snapshot VARCHAR(32) NULL COMMENT '学号快照',
                  name_snapshot VARCHAR(64) NOT NULL COMMENT '姓名快照',
                  sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '显示顺序',
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_duty_schedule_assignee_slot (slot_id, sort_order),
                  KEY idx_duty_schedule_assignee_user (user_id),
                  CONSTRAINT fk_duty_schedule_assignee_slot FOREIGN KEY (slot_id) REFERENCES duty_schedule_slots (id) ON DELETE CASCADE,
                  CONSTRAINT fk_duty_schedule_assignee_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='值班周排班人员表'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS repair_cases (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  case_no VARCHAR(32) NOT NULL COMMENT '维修事务编号',
                  agreement_type VARCHAR(30) NOT NULL DEFAULT 'PERSONAL_DEVICE' COMMENT 'PERSONAL_DEVICE=维修协议, PUBLIC_DEVICE=免责协议',
                  owner_name VARCHAR(64) NOT NULL COMMENT '送修人姓名',
                  owner_phone VARCHAR(40) NULL COMMENT '送修人联系方式',
                  owner_org VARCHAR(120) NULL COMMENT '历史兼容字段',
                  device_type VARCHAR(80) NOT NULL COMMENT '设备类型',
                  device_brand VARCHAR(80) NULL COMMENT '品牌',
                  device_model VARCHAR(120) NULL COMMENT '型号',
                  device_serial VARCHAR(120) NULL COMMENT '历史兼容字段',
                  accessories VARCHAR(500) NULL COMMENT '随附物品',
                  fault_description VARCHAR(1000) NOT NULL COMMENT '故障描述',
                  service_description VARCHAR(1000) NULL COMMENT '处理记录',
                  data_backup_confirmed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已提醒数据备份',
                  risk_acknowledged TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否确认维修风险',
                  privacy_acknowledged TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否确认隐私提示',
                  status VARCHAR(30) NOT NULL DEFAULT 'REPAIRING' COMMENT 'REPAIRING, COMPLETED, CANCELED；兼容旧进行中状态',
                  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
                  completed_at DATETIME NULL COMMENT '完成时间',
                  handler_user_id BIGINT UNSIGNED NULL COMMENT '处理人',
                  handler_name_snapshot VARCHAR(64) NULL COMMENT '处理人姓名快照',
                  remark VARCHAR(1000) NULL COMMENT '备注',
                  created_by BIGINT UNSIGNED NULL COMMENT '创建人',
                  updated_by BIGINT UNSIGNED NULL COMMENT '最后修改人',
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_repair_cases_case_no (case_no),
                  KEY idx_repair_cases_status_received (status, received_at),
                  KEY idx_repair_cases_owner (owner_name, owner_phone),
                  KEY idx_repair_cases_handler (handler_user_id),
                  KEY idx_repair_cases_created_by (created_by),
                  KEY idx_repair_cases_updated_by (updated_by),
                  CONSTRAINT fk_repair_cases_handler FOREIGN KEY (handler_user_id) REFERENCES users (id) ON DELETE SET NULL,
                  CONSTRAINT fk_repair_cases_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
                  CONSTRAINT fk_repair_cases_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
                  CONSTRAINT chk_repair_cases_agreement_type CHECK (agreement_type IN ('PERSONAL_DEVICE', 'PUBLIC_DEVICE')),
                  CONSTRAINT chk_repair_cases_status CHECK (status IN ('RECEIVED', 'DIAGNOSING', 'REPAIRING', 'WAITING_PICKUP', 'COMPLETED', 'CANCELED'))
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='协会维修事务记录表'
                """);
        jdbc.update("""
                UPDATE repair_cases
                SET status = 'REPAIRING'
                WHERE status IN ('RECEIVED', 'DIAGNOSING', 'WAITING_PICKUP')
                """);
        jdbc.update("""
                UPDATE IGNORE repair_cases
                SET case_no = CONCAT('JX', SUBSTRING(case_no, 1, 10), '-', SUBSTRING(case_no, 11))
                WHERE case_no REGEXP '^WX[0-9]{12}$'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS app_settings (
                  setting_key VARCHAR(100) NOT NULL COMMENT '配置键',
                  setting_value VARCHAR(1000) NOT NULL COMMENT '配置值',
                  description VARCHAR(255) NULL COMMENT '配置说明',
                  updated_by BIGINT UNSIGNED NULL COMMENT '最后修改人',
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (setting_key),
                  KEY idx_app_settings_updated_by (updated_by),
                  CONSTRAINT fk_app_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表'
                """);
    }

    private void addColumnIfMissing(String table, String column, String ddl) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, table, column);
        if (count == null || count == 0) {
            jdbc.execute(ddl);
        }
    }
}
