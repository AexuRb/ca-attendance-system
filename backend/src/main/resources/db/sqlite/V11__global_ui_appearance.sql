INSERT INTO app_settings (setting_key, setting_value, description)
VALUES ('UI_APPEARANCE', 'CLASSIC', '全局界面外观')
ON CONFLICT (setting_key) DO NOTHING;
