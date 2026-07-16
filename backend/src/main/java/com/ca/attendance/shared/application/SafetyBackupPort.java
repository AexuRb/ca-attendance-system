package com.ca.attendance.shared.application;

public interface SafetyBackupPort {
    BackupReceipt create(String reason);

    record BackupReceipt(String filename) {
    }
}
