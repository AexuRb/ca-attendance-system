USE ca_attendance;

INSERT INTO duty_weekday_settings (weekday, weekday_name, enabled)
VALUES
  (1, '星期一', 1),
  (2, '星期二', 1),
  (3, '星期三', 1),
  (4, '星期四', 1),
  (5, '星期五', 1),
  (6, '星期六', 1),
  (7, '星期日', 1)
ON DUPLICATE KEY UPDATE
  weekday_name = VALUES(weekday_name);

INSERT INTO app_settings (setting_key, setting_value, description)
VALUES
  ('INITIAL_ADMIN_ACCOUNT', 'cugbcacyh', '初始管理员账号。初始密码由后端初始化程序加密写入，不在 SQL 中保存明文。'),
  ('VALID_HOURS_ROUNDING', 'ROUND_HALF_UP_BY_HOUR', '有效时长按小时四舍五入：1h15m=1h，2h34m=3h。'),
  ('BACKUP_RETENTION_DAYS', '7', '数据库备份保留天数。')
ON DUPLICATE KEY UPDATE
  setting_value = VALUES(setting_value),
  description = VALUES(description);
