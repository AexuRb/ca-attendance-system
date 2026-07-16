package com.ca.attendance.maintenance;

import com.ca.attendance.shared.application.SafetyBackupPort;
import org.springframework.stereotype.Component;

@Component
public class BackupSafetyAdapter implements SafetyBackupPort {
    private final BackupService backups;

    public BackupSafetyAdapter(BackupService backups) {
        this.backups = backups;
    }

    @Override
    public BackupReceipt create(String reason) {
        return new BackupReceipt(backups.createSystemBackup(reason).filename());
    }
}
