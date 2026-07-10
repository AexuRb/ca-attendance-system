package com.ca.attendance.repair;

import java.time.LocalDateTime;

public record RepairCaseItem(
        long id,
        String caseNo,
        String agreementType,
        String ownerName,
        String ownerPhone,
        String ownerOrg,
        String deviceType,
        String deviceBrand,
        String deviceModel,
        String deviceSerial,
        String accessories,
        String faultDescription,
        String serviceDescription,
        boolean dataBackupConfirmed,
        boolean riskAcknowledged,
        boolean privacyAcknowledged,
        String status,
        LocalDateTime receivedAt,
        LocalDateTime completedAt,
        Long handlerUserId,
        String handlerName,
        String remark,
        String createdByName,
        String updatedByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
