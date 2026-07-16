package com.ca.attendance.term.application;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.shared.application.AuditLogPort;
import com.ca.attendance.shared.application.CurrentActor;
import com.ca.attendance.shared.application.SafetyBackupPort;
import com.ca.attendance.term.domain.AcademicTerm;
import com.ca.attendance.term.domain.TermStatus;
import com.ca.attendance.term.infrastructure.AcademicTermRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.ca.attendance.common.JdbcTime.databaseDate;

@Service
public class TermService {
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9._-]{2,32}");

    private final AcademicTermRepository terms;
    private final JdbcTemplate jdbc;
    private final CurrentActor currentActor;
    private final SafetyBackupPort safetyBackups;
    private final AuditLogPort auditLogs;
    private final ObjectMapper objectMapper;

    public TermService(AcademicTermRepository terms,
                       CurrentActor currentActor,
                       SafetyBackupPort safetyBackups,
                       AuditLogPort auditLogs,
                       ObjectMapper objectMapper) {
        this.terms = terms;
        this.jdbc = terms.jdbc();
        this.currentActor = currentActor;
        this.safetyBackups = safetyBackups;
        this.auditLogs = auditLogs;
        this.objectMapper = objectMapper;
    }

    public TermListResponse list() {
        currentActor.require();
        List<AcademicTerm> items = terms.list();
        return new TermListResponse(items, items.stream()
                .filter(item -> item.status() == TermStatus.ACTIVE)
                .findFirst().orElse(null));
    }

    public AcademicTerm create(TermRequest request) {
        CurrentActor.Actor actor = requirePresidentOrAdmin();
        TermValues values = validate(request, null);
        try {
            long id = terms.create(values.code(), values.name(), values.startDate(), values.endDate(), actor.id());
            AcademicTerm created = requireTerm(id);
            auditLogs.write("CREATE_ACADEMIC_TERM", "academic_terms", id, null, created, "创建学期");
            return created;
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("学期编码已存在");
        }
    }

    public AcademicTerm update(long id, TermRequest request) {
        CurrentActor.Actor actor = requirePresidentOrAdmin();
        AcademicTerm before = requireTerm(id);
        if (before.status() != TermStatus.DRAFT) {
            throw ApiException.conflict("只有草稿学期可以修改");
        }
        TermValues values = validate(request, id);
        try {
            terms.updateDraft(id, values.code(), values.name(), values.startDate(), values.endDate(), actor.id());
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("学期编码已存在");
        }
        AcademicTerm after = requireTerm(id);
        auditLogs.write("UPDATE_ACADEMIC_TERM", "academic_terms", id, before, after, "修改学期");
        return after;
    }

    @Transactional
    public AcademicTerm activate(long id, ActivateRequest request) {
        CurrentActor.Actor actor = requirePresidentOrAdmin();
        AcademicTerm before = requireTerm(id);
        if (before.status() != TermStatus.DRAFT) {
            throw ApiException.conflict("只有草稿学期可以激活");
        }
        if (terms.active().isPresent()) {
            throw ApiException.conflict("已有活动学期，请先进入结算并封存");
        }
        terms.activate(id, actor.id());
        int copiedSlots = 0;
        if (request != null && request.copyPreviousSchedule()) {
            copiedSlots = copySchedule(id, request.sourceTermId(), actor.id());
        }
        AcademicTerm after = requireTerm(id);
        auditLogs.write("ACTIVATE_ACADEMIC_TERM", "academic_terms", id, before, after,
                copiedSlots > 0 ? "激活学期并复制 " + copiedSlots + " 个排班时段" : "激活学期");
        return after;
    }

    public AcademicTerm beginSettling(long id) {
        CurrentActor.Actor actor = requirePresidentOrAdmin();
        AcademicTerm before = requireTerm(id);
        if (before.status() != TermStatus.ACTIVE) {
            throw ApiException.conflict("只有活动学期可以进入结算");
        }
        terms.beginSettling(id, actor.id());
        AcademicTerm after = requireTerm(id);
        auditLogs.write("BEGIN_TERM_SETTLEMENT", "academic_terms", id, before, after, "进入学期结算");
        return after;
    }

    public SettlementPreview preview(long termId) {
        requirePresidentOrAdmin();
        AcademicTerm term = requireTerm(termId);
        if (term.status() != TermStatus.SETTLING) {
            throw ApiException.conflict("学期进入结算状态后才能生成结算预览");
        }
        return buildPreview(term);
    }

    public SettlementPreflight preflight(long termId) {
        requirePresidentOrAdmin();
        return buildPreflight(requireTerm(termId));
    }

    @Transactional
    public SealResult seal(long termId) {
        CurrentActor.Actor actor = requireAdmin();
        AcademicTerm before = requireTerm(termId);
        if (before.status() != TermStatus.SETTLING) {
            throw ApiException.conflict("只有结算中的学期可以封存");
        }
        SettlementPreview preview = buildPreview(before);
        if (preview.preflight().blocked()) {
            throw ApiException.conflict("仍有未处理项目，暂时不能封存");
        }

        SafetyBackupPort.BackupReceipt backup = safetyBackups.create(
                actor.name() + "封存学期“" + before.name() + "”前自动备份");
        int version = preview.nextVersion();
        String summaryJson = json(preview.summary());
        String digest = digest(summaryJson, preview.members());
        Long settlementId = jdbc.queryForObject("""
                INSERT INTO term_settlements (
                  term_id, version, status, summary_json, source_digest,
                  prepared_by, sealed_at, sealed_by
                ) VALUES (?, ?, 'SEALED', ?, ?, ?, datetime('now', 'localtime'), ?)
                RETURNING id
                """, Long.class, termId, version, summaryJson, digest, actor.id(), actor.id());
        long id = settlementId == null ? 0 : settlementId;
        for (MemberSettlement member : preview.members()) {
            jdbc.update("""
                    INSERT INTO term_member_settlements (
                      settlement_id, user_id, student_no_snapshot, name_snapshot, role_snapshot,
                      attendance_count, attendance_minutes, training_count, training_minutes, total_minutes
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, member.userId(), member.studentNo(), member.name(), member.role(),
                    member.attendanceCount(), member.attendanceMinutes(), member.trainingCount(),
                    member.trainingMinutes(), member.totalMinutes());
        }
        terms.seal(termId, actor.id());
        AcademicTerm after = requireTerm(termId);
        auditLogs.write("SEAL_ACADEMIC_TERM", "academic_terms", termId, before, after,
                "封存版本 " + version + "；自动备份：" + backup.filename());
        return new SealResult(after, id, version, digest, backup.filename());
    }

    @Transactional
    public AcademicTerm reopen(long termId, ReopenRequest request) {
        CurrentActor.Actor actor = requireAdmin();
        AcademicTerm before = requireTerm(termId);
        if (before.status() != TermStatus.SEALED) {
            throw ApiException.conflict("只有已封存学期可以重新打开");
        }
        String reason = required(request == null ? null : request.reason(), "重新打开必须填写原因", 500);
        SafetyBackupPort.BackupReceipt backup = safetyBackups.create(
                actor.name() + "重新打开学期“" + before.name() + "”前自动备份");
        jdbc.update("""
                UPDATE term_settlements
                SET status = 'SUPERSEDED', superseded_at = datetime('now', 'localtime'), reopen_reason = ?
                WHERE term_id = ? AND status = 'SEALED'
                """, reason, termId);
        terms.reopen(termId, actor.id(), reason);
        AcademicTerm after = requireTerm(termId);
        auditLogs.write("REOPEN_ACADEMIC_TERM", "academic_terms", termId, before, after,
                reason + "；自动备份：" + backup.filename());
        return after;
    }

    public List<SettlementVersion> settlements(long termId) {
        requirePresidentOrAdmin();
        requireTerm(termId);
        return jdbc.query("""
                SELECT ts.id, ts.version, ts.status, ts.source_digest, ts.prepared_at, ts.sealed_at,
                       p.name AS prepared_by_name, s.name AS sealed_by_name, ts.superseded_at, ts.reopen_reason,
                       (SELECT COUNT(*) FROM term_member_settlements tms WHERE tms.settlement_id = ts.id) AS member_count
                FROM term_settlements ts
                LEFT JOIN users p ON p.id = ts.prepared_by
                LEFT JOIN users s ON s.id = ts.sealed_by
                WHERE ts.term_id = ?
                ORDER BY ts.version DESC
                """, (rs, rowNum) -> new SettlementVersion(
                rs.getLong("id"), rs.getInt("version"), rs.getString("status"),
                rs.getString("source_digest"), rs.getString("prepared_at"), rs.getString("sealed_at"),
                rs.getString("prepared_by_name"), rs.getString("sealed_by_name"),
                rs.getString("superseded_at"), rs.getString("reopen_reason"), rs.getInt("member_count")
        ), termId);
    }

    private SettlementPreview buildPreview(AcademicTerm term) {
        SettlementPreflight preflight = buildPreflight(term);
        Map<String, Object> summary = settlementSummary(term);
        List<MemberSettlement> members = memberSettlements(term.id());
        Integer currentVersion = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM term_settlements WHERE term_id = ?",
                Integer.class, term.id());
        return new SettlementPreview(term, preflight, currentVersion == null ? 1 : currentVersion + 1,
                summary, members);
    }

    private SettlementPreflight buildPreflight(AcademicTerm term) {
        List<PreflightIssue> issues = new ArrayList<>();
        addIssue(issues, "PENDING_ATTENDANCE", "仍有待审核签到或签退", count("""
                SELECT COUNT(*) FROM attendance_records
                WHERE term_id = ? AND (check_in_status = 'PENDING' OR check_out_status = 'PENDING')
                """, term.id()));
        addIssue(issues, "OPEN_ATTENDANCE", "仍有未签退记录", count("""
                SELECT COUNT(*) FROM attendance_records
                WHERE term_id = ? AND check_out_time IS NULL AND check_out_status = 'NOT_SUBMITTED'
                  AND check_in_status <> 'REJECTED'
                """, term.id()));
        addIssue(issues, "OPEN_REPAIRS", "仍有进行中的维修事务", count("""
                SELECT COUNT(*) FROM repair_cases
                WHERE term_id = ? AND status = 'REPAIRING' AND deleted_at IS NULL
                """, term.id()));
        int unassigned = count("""
                SELECT
                  (SELECT COUNT(*) FROM attendance_records WHERE term_id IS NULL) +
                  (SELECT COUNT(*) FROM training_sessions WHERE term_id IS NULL) +
                  (SELECT COUNT(*) FROM duty_schedule_slots WHERE term_id IS NULL) +
                  (SELECT COUNT(*) FROM repair_cases WHERE term_id IS NULL)
                """);
        addIssue(issues, "UNASSIGNED_TERM", "存在未归属学期的数据", unassigned);
        int outside = count("""
                SELECT
                  (SELECT COUNT(*) FROM attendance_records
                   WHERE term_id = ? AND duty_date NOT BETWEEN ? AND ?) +
                  (SELECT COUNT(*) FROM training_sessions
                   WHERE term_id = ? AND training_date NOT BETWEEN ? AND ?) +
                  (SELECT COUNT(*) FROM repair_cases
                   WHERE term_id = ? AND date(received_at) NOT BETWEEN ? AND ?)
                """, term.id(), databaseDate(term.startDate()), databaseDate(term.endDate()),
                term.id(), databaseDate(term.startDate()), databaseDate(term.endDate()),
                term.id(), databaseDate(term.startDate()), databaseDate(term.endDate()));
        addIssue(issues, "OUTSIDE_TERM_RANGE", "存在日期超出学期范围的数据", outside);
        return new SettlementPreflight(!issues.isEmpty(), issues);
    }

    private Map<String, Object> settlementSummary(AcademicTerm term) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("termId", term.id());
        summary.put("termCode", term.code());
        summary.put("termName", term.name());
        summary.put("startDate", term.startDate());
        summary.put("endDate", term.endDate());
        summary.put("attendanceRecords", count("SELECT COUNT(*) FROM attendance_records WHERE term_id = ?", term.id()));
        summary.put("validAttendanceRecords", count("SELECT COUNT(*) FROM attendance_records WHERE term_id = ? AND effective_status = 'VALID'", term.id()));
        summary.put("trainingSessions", count("SELECT COUNT(*) FROM training_sessions WHERE term_id = ?", term.id()));
        summary.put("scheduleSlots", count("SELECT COUNT(*) FROM duty_schedule_slots WHERE term_id = ? AND status = 'ACTIVE'", term.id()));
        summary.put("scheduleExceptions", count("SELECT COUNT(*) FROM duty_schedule_exceptions WHERE term_id = ?", term.id()));
        summary.put("shiftReassignments", count("SELECT COUNT(*) FROM duty_shift_reassignments WHERE term_id = ?", term.id()));
        summary.put("repairs", count("SELECT COUNT(*) FROM repair_cases WHERE term_id = ? AND deleted_at IS NULL", term.id()));
        summary.put("completedRepairs", count("SELECT COUNT(*) FROM repair_cases WHERE term_id = ? AND status = 'COMPLETED' AND deleted_at IS NULL", term.id()));
        summary.put("canceledRepairs", count("SELECT COUNT(*) FROM repair_cases WHERE term_id = ? AND status = 'CANCELED' AND deleted_at IS NULL", term.id()));
        return summary;
    }

    private List<MemberSettlement> memberSettlements(long termId) {
        return jdbc.query("""
                WITH attendance AS (
                  SELECT user_id, COUNT(*) AS record_count, COALESCE(SUM(duration_minutes), 0) AS minutes
                  FROM attendance_records
                  WHERE term_id = ? AND effective_status = 'VALID'
                  GROUP BY user_id
                ), training AS (
                  SELECT tp.user_id, COUNT(*) AS record_count,
                         COALESCE(CAST(ROUND(SUM(tp.duration_hours) * 60) AS INTEGER), 0) AS minutes
                  FROM training_participants tp
                  JOIN training_sessions ts ON ts.id = tp.session_id
                  WHERE ts.term_id = ? AND tp.attendance_status = 'PRESENT' AND tp.user_id IS NOT NULL
                  GROUP BY tp.user_id
                )
                SELECT u.id, u.student_no, u.name, u.role,
                       COALESCE(a.record_count, 0) AS attendance_count,
                       COALESCE(a.minutes, 0) AS attendance_minutes,
                       COALESCE(t.record_count, 0) AS training_count,
                       COALESCE(t.minutes, 0) AS training_minutes
                FROM users u
                LEFT JOIN attendance a ON a.user_id = u.id
                LEFT JOIN training t ON t.user_id = u.id
                WHERE u.status = 'ACTIVE' OR a.user_id IS NOT NULL OR t.user_id IS NOT NULL
                ORDER BY u.student_no
                """, (rs, rowNum) -> {
            int attendanceMinutes = rs.getInt("attendance_minutes");
            int trainingMinutes = rs.getInt("training_minutes");
            return new MemberSettlement(
                    rs.getLong("id"), rs.getString("student_no"), rs.getString("name"), rs.getString("role"),
                    rs.getInt("attendance_count"), attendanceMinutes,
                    rs.getInt("training_count"), trainingMinutes, attendanceMinutes + trainingMinutes
            );
        }, termId, termId);
    }

    private int copySchedule(long targetTermId, Long sourceTermId, long actorId) {
        Long source = sourceTermId;
        if (source == null) {
            List<Long> candidates = jdbc.query("""
                    SELECT id FROM academic_terms
                    WHERE id <> ? AND status IN ('SETTLING', 'SEALED')
                    ORDER BY end_date DESC, id DESC LIMIT 1
                    """, (rs, rowNum) -> rs.getLong(1), targetTermId);
            source = candidates.isEmpty() ? null : candidates.getFirst();
        }
        if (source == null) {
            throw ApiException.badRequest("没有可复制的历史排班");
        }
        requireTerm(source);
        List<Map<String, Object>> slots = jdbc.queryForList("""
                SELECT * FROM duty_schedule_slots
                WHERE term_id = ? AND status = 'ACTIVE'
                ORDER BY weekday, start_time, id
                """, source);
        int copied = 0;
        for (Map<String, Object> slot : slots) {
            Long newId = jdbc.queryForObject("""
                    INSERT INTO duty_schedule_slots (
                      term_id, weekday, start_time, end_time, title, location, note, enabled,
                      status, created_by, updated_by
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                    RETURNING id
                    """, Long.class, targetTermId, slot.get("weekday"), slot.get("start_time"), slot.get("end_time"),
                    slot.get("title"), slot.get("location"), slot.get("note"), slot.get("enabled"), actorId, actorId);
            if (newId == null) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO duty_schedule_assignees (
                      slot_id, user_id, student_no_snapshot, name_snapshot, sort_order
                    )
                    SELECT ?, user_id, student_no_snapshot, name_snapshot, sort_order
                    FROM duty_schedule_assignees WHERE slot_id = ?
                    """, newId, slot.get("id"));
            copied++;
        }
        return copied;
    }

    private TermValues validate(TermRequest request, Long excludedId) {
        if (request == null) {
            throw ApiException.badRequest("请填写学期信息");
        }
        String code = required(request.code(), "学期编码不能为空", 32);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw ApiException.badRequest("学期编码只能包含字母、数字、点、横线或下划线");
        }
        String name = required(request.name(), "学期名称不能为空", 80);
        if (request.startDate() == null || request.endDate() == null) {
            throw ApiException.badRequest("请填写学期开始和结束日期");
        }
        if (request.startDate().isAfter(request.endDate())) {
            throw ApiException.badRequest("学期开始日期不能晚于结束日期");
        }
        if (terms.overlaps(request.startDate(), request.endDate(), excludedId)) {
            throw ApiException.conflict("学期日期与已有学期重叠");
        }
        return new TermValues(code, name, request.startDate(), request.endDate());
    }

    private AcademicTerm requireTerm(long id) {
        return terms.find(id).orElseThrow(() -> ApiException.notFound("学期不存在"));
    }

    private CurrentActor.Actor requirePresidentOrAdmin() {
        CurrentActor.Actor actor = currentActor.require();
        if (actor.role() != Role.PRESIDENT && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有会长或管理员可以管理学期");
        }
        return actor;
    }

    private CurrentActor.Actor requireAdmin() {
        CurrentActor.Actor actor = currentActor.require();
        if (actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有管理员可以封存或重新打开学期");
        }
        return actor;
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private void addIssue(List<PreflightIssue> issues, String code, String message, int count) {
        if (count > 0) {
            issues.add(new PreflightIssue(code, message, count));
        }
    }

    private String required(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(message);
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法生成结算快照", ex);
        }
    }

    private String digest(String summaryJson, List<MemberSettlement> members) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(summaryJson.getBytes(StandardCharsets.UTF_8));
            digest.update(json(members).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", ex);
        }
    }

    public record TermRequest(String code, String name, LocalDate startDate, LocalDate endDate) {
    }

    public record ActivateRequest(boolean copyPreviousSchedule, Long sourceTermId) {
    }

    public record ReopenRequest(String reason) {
    }

    public record TermListResponse(List<AcademicTerm> terms, AcademicTerm currentTerm) {
    }

    public record PreflightIssue(String code, String message, int count) {
    }

    public record SettlementPreflight(boolean blocked, List<PreflightIssue> issues) {
    }

    public record MemberSettlement(
            Long userId, String studentNo, String name, String role,
            int attendanceCount, int attendanceMinutes,
            int trainingCount, int trainingMinutes, int totalMinutes
    ) {
    }

    public record SettlementPreview(
            AcademicTerm term, SettlementPreflight preflight, int nextVersion,
            Map<String, Object> summary, List<MemberSettlement> members
    ) {
    }

    public record SealResult(
            AcademicTerm term, long settlementId, int version, String sourceDigest, String backupFilename
    ) {
    }

    public record SettlementVersion(
            long id, int version, String status, String sourceDigest, String preparedAt, String sealedAt,
            String preparedByName, String sealedByName, String supersededAt, String reopenReason, int memberCount
    ) {
    }

    private record TermValues(String code, String name, LocalDate startDate, LocalDate endDate) {
    }
}
