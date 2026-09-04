package com.ca.attendance.repair;

import com.ca.attendance.access.RolePermissionPolicy;
import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.ExportRowLimit;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.maintenance.BackupService;
import com.ca.attendance.user.UserRepository;
import com.ca.attendance.user.UserSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.ca.attendance.common.JdbcWriteChecks.requireOne;

@Service
public class RepairCaseService {
    private static final DateTimeFormatter CASE_DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> STORED_AGREEMENT_TYPES = Set.of("PERSONAL_DEVICE", "PUBLIC_DEVICE");

    private final JdbcTemplate jdbc;
    private final OperationLogService logs;
    private final BackupService backups;
    private final UserRepository users;
    private final RepairCaseQueryService queries;
    private final RepairAgreementService agreements;
    private final RepairExcelExportService excelExports;

    public RepairCaseService(JdbcTemplate jdbc, OperationLogService logs, BackupService backups,
                             UserRepository users, RepairCaseQueryService queries,
                             RepairAgreementService agreements, RepairExcelExportService excelExports) {
        this.jdbc = jdbc;
        this.logs = logs;
        this.backups = backups;
        this.users = users;
        this.queries = queries;
        this.agreements = agreements;
        this.excelExports = excelExports;
    }

    public List<RepairCaseItem> list(String keyword, String status, LocalDate from, LocalDate to) {
        requireManager(AuthContext.current());
        return queries.list(keyword, status, from, to);
    }

    @Transactional(readOnly = true)
    public RepairPage page(String keyword, String status, LocalDate from, LocalDate to, int page, int pageSize) {
        requireManager(AuthContext.current());
        RepairCaseQueryService.PageResult<RepairCaseItem> result = queries.page(
                keyword, status, from, to, page, pageSize
        );
        return new RepairPage(
                result.items(),
                result.total(),
                result.page(),
                result.pageSize(),
                result.hasMore(),
                result.statusCounts()
        );
    }

    public List<UserRepository.UserCandidate> handlerCandidates(String keyword) {
        requireManager(AuthContext.current());
        return queries.handlerCandidates(keyword);
    }

    @Transactional
    public RepairCaseItem create(RepairCaseRequest request) {
        AuthUser current = AuthContext.current();
        requireManager(current);
        RepairValues values = repairValues(request, null, current);
        String caseNo = nextCaseNo();
        Long id = jdbc.queryForObject("""
                INSERT INTO repair_cases (
                  case_no, agreement_type, owner_name, owner_phone, owner_org, device_type,
                  device_brand, device_model, device_serial, accessories, fault_description,
                  service_description, data_backup_confirmed, risk_acknowledged, privacy_acknowledged,
                  status, received_at, completed_at, handler_user_id, handler_name_snapshot,
                  remark, created_by, updated_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                caseNo,
                values.agreementType(),
                values.ownerName(),
                values.ownerPhone(),
                values.ownerOrg(),
                values.deviceType(),
                values.deviceBrand(),
                values.deviceModel(),
                values.deviceSerial(),
                values.accessories(),
                values.faultDescription(),
                values.serviceDescription(),
                values.dataBackupConfirmed(),
                values.riskAcknowledged(),
                values.privacyAcknowledged(),
                values.status(),
                Timestamp.valueOf(values.receivedAt()),
                toTimestamp(values.completedAt()),
                values.handlerUserId(),
                values.handlerName(),
                values.remark(),
                current.id(),
                current.id()
        );
        RepairCaseItem created = findCase(id == null ? 0 : id).orElseThrow();
        logs.log("CREATE_REPAIR_CASE", "repair_cases", created.id(), null, created, "新增维修事务");
        return created;
    }

    @Transactional
    public RepairCaseItem update(long id, RepairCaseRequest request) {
        AuthUser current = AuthContext.current();
        requireManager(current);
        RepairCaseItem before = findCase(id).orElseThrow(() -> ApiException.notFound("维修事务不存在"));
        RepairValues values = repairValues(request, before, current);
        int updated = jdbc.update("""
                UPDATE repair_cases
                SET agreement_type = ?, owner_name = ?, owner_phone = ?, owner_org = ?,
                    device_type = ?, device_brand = ?, device_model = ?, device_serial = ?,
                    accessories = ?, fault_description = ?, service_description = ?,
                    data_backup_confirmed = ?, risk_acknowledged = ?, privacy_acknowledged = ?,
                    status = ?, received_at = ?, completed_at = ?, handler_user_id = ?,
                    handler_name_snapshot = ?, remark = ?, updated_by = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ?
                """,
                values.agreementType(),
                values.ownerName(),
                values.ownerPhone(),
                values.ownerOrg(),
                values.deviceType(),
                values.deviceBrand(),
                values.deviceModel(),
                values.deviceSerial(),
                values.accessories(),
                values.faultDescription(),
                values.serviceDescription(),
                values.dataBackupConfirmed(),
                values.riskAcknowledged(),
                values.privacyAcknowledged(),
                values.status(),
                Timestamp.valueOf(values.receivedAt()),
                toTimestamp(values.completedAt()),
                values.handlerUserId(),
                values.handlerName(),
                values.remark(),
                current.id(),
                id
        );
        requireOne(updated, "维修事务状态已经变化，请刷新后重试");
        RepairCaseItem after = findCase(id).orElseThrow();
        logs.log("UPDATE_REPAIR_CASE", "repair_cases", id, before, after, "修改维修事务");
        return after;
    }

    public ExportFile exportCases(String keyword, String status, LocalDate from, LocalDate to) {
        AuthUser current = AuthContext.current();
        requireRepairExporter(current);
        LocalDate start = from == null ? LocalDate.of(LocalDate.now().getYear(), 1, 1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        List<RepairCaseItem> rows = list(keyword, status, start, end);
        ExportRowLimit.requireWithinLimit(rows.size());
        RepairExcelExportService.ExportDocument document = excelExports.generate(rows, start, end);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("from", start.toString());
        filters.put("to", end.toString());
        filters.put("status", status == null || status.isBlank() ? "IN_PROGRESS" : status.trim().toUpperCase(Locale.ROOT));
        if (keyword != null && !keyword.isBlank()) {
            filters.put("keyword", keyword.trim());
        }
        logs.logExport("REPAIR_CASES", "维修事务", filters, rows.size(), document.filename());
        return new ExportFile(document.filename(), document.bytes());
    }

    public AgreementFile agreement(long id) {
        requireManager(AuthContext.current());
        RepairCaseItem item = findCase(id).orElseThrow(() -> ApiException.notFound("维修事务不存在"));
        RepairAgreementService.AgreementDocument document = agreements.generate(item);
        return new AgreementFile(document.filename(), document.bytes());
    }

    public List<RepairCaseItem> recycleBin() {
        requireAdmin(AuthContext.current());
        return queries.recycleBin();
    }

    @Transactional
    public RepairCaseItem moveToRecycleBin(long id) {
        AuthUser current = AuthContext.current();
        requireRepairDeleter(current);
        RepairCaseItem before = findCase(id).orElseThrow(() -> ApiException.notFound("维修事务不存在"));
        int updated = jdbc.update("""
                UPDATE repair_cases
                SET deleted_at = datetime('now', 'localtime'), deleted_by = ?,
                    updated_by = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ? AND deleted_at IS NULL
                """, current.id(), current.id(), id);
        requireOne(updated, "维修事务状态已经变化，请刷新后重试");
        RepairCaseItem after = findDeletedCase(id).orElseThrow();
        logs.log("DELETE_REPAIR_CASE", "repair_cases", id, before, after, "维修事务移入回收站");
        return after;
    }

    @Transactional
    public RepairCaseItem restore(long id) {
        AuthUser current = AuthContext.current();
        requireAdmin(current);
        RepairCaseItem before = findDeletedCase(id).orElseThrow(() -> ApiException.notFound("回收站中不存在该维修事务"));
        int updated = jdbc.update("""
                UPDATE repair_cases
                SET deleted_at = NULL, deleted_by = NULL,
                    updated_by = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ? AND deleted_at IS NOT NULL
                """, current.id(), id);
        requireOne(updated, "维修事务状态已经变化，请刷新后重试");
        RepairCaseItem after = findCase(id).orElseThrow();
        logs.log("RESTORE_REPAIR_CASE", "repair_cases", id, before, after, "从回收站恢复维修事务");
        return after;
    }

    @Transactional
    public PurgeResult purge(long id, String confirmedCaseNo) {
        AuthUser current = AuthContext.current();
        requireAdmin(current);
        RepairCaseItem before = findDeletedCase(id).orElseThrow(() -> ApiException.notFound("回收站中不存在该维修事务"));
        if (confirmedCaseNo == null || !before.caseNo().equals(confirmedCaseNo.trim())) {
            throw ApiException.badRequest("维修编号不匹配，未执行永久删除");
        }

        BackupService.BackupItem safetyBackup = backups.createSystemBackup(
                "永久删除维修事务前自动备份：" + before.caseNo()
        );
        int deleted = jdbc.update("DELETE FROM repair_cases WHERE id = ? AND deleted_at IS NOT NULL", id);
        requireOne(deleted, "维修事务状态已经变化，请刷新后重试");
        PurgeResult result = new PurgeResult(before.caseNo(), safetyBackup);
        logs.log(
                "PURGE_REPAIR_CASE",
                "repair_cases",
                id,
                before,
                Map.of("caseNo", before.caseNo(), "safetyBackup", safetyBackup.filename()),
                "永久删除维修事务，安全备份：" + safetyBackup.filename()
        );
        return result;
    }

    private Optional<RepairCaseItem> findCase(long id) {
        return queries.findCase(id);
    }

    private Optional<RepairCaseItem> findDeletedCase(long id) {
        return queries.findDeletedCase(id);
    }

    private RepairValues repairValues(RepairCaseRequest request, RepairCaseItem fallback, AuthUser current) {
        String status = RepairStatus.parse(valueOr(
                request.status(),
                fallback == null ? "REPAIRING" : fallback.status()
        ));
        LocalDateTime receivedAt = request.receivedAt() == null
                ? fallback == null ? LocalDateTime.now() : fallback.receivedAt()
                : request.receivedAt();
        LocalDateTime completedAt = "COMPLETED".equals(status)
                ? request.completedAt() == null
                    ? fallback != null && fallback.completedAt() != null ? fallback.completedAt() : LocalDateTime.now()
                    : request.completedAt()
                : null;
        if (completedAt != null && !completedAt.isAfter(receivedAt)) {
            throw ApiException.badRequest("完成时间必须晚于受理时间");
        }
        HandlerSelection handler = resolveHandler(request, fallback, current);

        return new RepairValues(
                parseAgreementType(valueOr(request.agreementType(), fallback == null ? "PERSONAL_DEVICE" : fallback.agreementType())),
                required(valueOr(request.ownerName(), fallback == null ? null : fallback.ownerName()), "送修人姓名不能为空", 64),
                trimToNull(valueOr(request.ownerPhone(), fallback == null ? null : fallback.ownerPhone()), 40),
                null,
                required(valueOr(request.deviceType(), fallback == null ? null : fallback.deviceType()), "设备类型不能为空", 80),
                trimToNull(valueOr(request.deviceBrand(), fallback == null ? null : fallback.deviceBrand()), 80),
                trimToNull(valueOr(request.deviceModel(), fallback == null ? null : fallback.deviceModel()), 120),
                null,
                trimToNull(valueOr(request.accessories(), fallback == null ? null : fallback.accessories()), 500),
                required(valueOr(request.faultDescription(), fallback == null ? null : fallback.faultDescription()), "故障描述不能为空", 1000),
                trimToNull(valueOr(request.serviceDescription(), fallback == null ? null : fallback.serviceDescription()), 1000),
                request.dataBackupConfirmed() == null ? fallback != null && fallback.dataBackupConfirmed() : request.dataBackupConfirmed(),
                request.riskAcknowledged() == null ? fallback != null && fallback.riskAcknowledged() : request.riskAcknowledged(),
                request.privacyAcknowledged() == null ? fallback != null && fallback.privacyAcknowledged() : request.privacyAcknowledged(),
                status,
                receivedAt,
                completedAt,
                handler.userId(),
                handler.name(),
                trimToNull(valueOr(request.remark(), fallback == null ? null : fallback.remark()), 1000)
        );
    }

    private HandlerSelection resolveHandler(RepairCaseRequest request, RepairCaseItem fallback, AuthUser current) {
        Long requestedId = request.handlerUserId();
        if (requestedId != null) {
            Optional<UserRepository.UserCandidate> active = activeHandlerById(requestedId);
            if (active.isPresent()) {
                UserRepository.UserCandidate candidate = active.get();
                return new HandlerSelection(candidate.id(), candidate.name());
            }
            if (fallback != null && requestedId.equals(fallback.handlerUserId())) {
                return new HandlerSelection(fallback.handlerUserId(), fallback.handlerName());
            }
            throw ApiException.badRequest("负责人账号不存在或已停用");
        }

        String requestedName = trimToNull(request.handlerName(), 64);
        if (fallback != null
                && (requestedName == null || requestedName.equals(fallback.handlerName()))) {
            return new HandlerSelection(fallback.handlerUserId(), fallback.handlerName());
        }
        if (requestedName == null) {
            throw ApiException.badRequest("请选择负责人");
        }
        if (requestedName.equals(current.name())) {
            return new HandlerSelection(current.id(), current.name());
        }

        List<UserRepository.UserCandidate> matches = activeHandlersByName(requestedName);
        if (matches.size() == 1) {
            UserRepository.UserCandidate candidate = matches.get(0);
            return new HandlerSelection(candidate.id(), candidate.name());
        }
        throw ApiException.badRequest(matches.isEmpty()
                ? "负责人账号不存在或已停用"
                : "存在同名账号，请通过账号选择负责人");
    }

    private Optional<UserRepository.UserCandidate> activeHandlerById(long id) {
        return users.findSummaryById(id)
                .filter(user -> "ACTIVE".equals(user.status()))
                .map(this::toCandidate);
    }

    private List<UserRepository.UserCandidate> activeHandlersByName(String name) {
        return users.findActiveByName(name).stream()
                .limit(2)
                .map(this::toCandidate)
                .toList();
    }

    private UserRepository.UserCandidate toCandidate(UserSummary user) {
        return new UserRepository.UserCandidate(user.id(), user.studentNo(), user.name(), user.role());
    }

    private String nextCaseNo() {
        String sequenceDate = LocalDate.now().format(CASE_DAY);
        Integer sequence = jdbc.queryForObject("""
                INSERT INTO repair_case_sequences (sequence_date, last_value)
                VALUES (?, 1)
                ON CONFLICT(sequence_date) DO UPDATE SET
                  last_value = repair_case_sequences.last_value + 1,
                  updated_at = datetime('now', 'localtime')
                RETURNING last_value
                """, Integer.class, sequenceDate);
        if (sequence == null) {
            throw ApiException.badRequest("维修编号生成失败");
        }
        return "JXWX" + sequenceDate + "-" + String.format("%04d", sequence);
    }

    private String parseAgreementType(String value) {
        String text = value == null || value.isBlank() ? "PERSONAL_DEVICE" : value.trim().toUpperCase(Locale.ROOT);
        return switch (text) {
            case "PERSONAL_DEVICE", "REPAIR", "维修协议" -> "PERSONAL_DEVICE";
            case "PUBLIC_DEVICE", "DISCLAIMER", "免责协议" -> "PUBLIC_DEVICE";
            default -> {
                if (STORED_AGREEMENT_TYPES.contains(text)) {
                    yield text;
                }
                throw ApiException.badRequest("协议类型不合法");
            }
        };
    }

    private void requireManager(AuthUser current) {
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.REPAIR_MANAGE,
                "只有部长、会长或管理员可以管理维修事务");
    }

    private void requireRepairExporter(AuthUser current) {
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.REPAIR_EXPORT,
                "只有会长或管理员可以导出维修事务");
    }

    private void requireRepairDeleter(AuthUser current) {
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.REPAIR_DELETE,
                "只有会长或管理员可以删除维修事务");
    }

    private void requireAdmin(AuthUser current) {
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.REPAIR_RECYCLE_BIN,
                "只有管理员可以管理维修回收站");
    }

    private String valueOr(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String required(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(message);
        }
        return limit(value.trim(), maxLength);
    }

    private String trimToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return limit(value.trim(), maxLength);
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    public record RepairCaseRequest(
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
            Boolean dataBackupConfirmed,
            Boolean riskAcknowledged,
            Boolean privacyAcknowledged,
            String status,
            LocalDateTime receivedAt,
            LocalDateTime completedAt,
            Long handlerUserId,
            String handlerName,
            String remark
    ) {
        public RepairCaseRequest(
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
                Boolean dataBackupConfirmed,
                Boolean riskAcknowledged,
                Boolean privacyAcknowledged,
                String status,
                LocalDateTime receivedAt,
                LocalDateTime completedAt,
                String handlerName,
                String remark
        ) {
            this(agreementType, ownerName, ownerPhone, ownerOrg, deviceType, deviceBrand,
                    deviceModel, deviceSerial, accessories, faultDescription, serviceDescription,
                    dataBackupConfirmed, riskAcknowledged, privacyAcknowledged, status, receivedAt,
                    completedAt, null, handlerName, remark);
        }
    }

    public record ExportFile(String filename, byte[] bytes) {
    }

    public record RepairPage(List<RepairCaseItem> items, long total, int page, int pageSize, boolean hasMore,
                             Map<String, Long> statusCounts) {
    }

    public record AgreementFile(String filename, byte[] bytes) {
    }

    public record PurgeResult(String caseNo, BackupService.BackupItem safetyBackup) {
    }

    private record RepairValues(
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
            String remark
    ) {
    }

    private record HandlerSelection(Long userId, String name) {
    }

}
