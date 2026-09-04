package com.ca.attendance.training;

import com.ca.attendance.access.RolePermissionPolicy;
import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.ExcelImportPolicy;
import com.ca.attendance.common.ExportRowLimit;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;

import static com.ca.attendance.common.JdbcTime.databaseDate;
import static com.ca.attendance.common.JdbcTime.databaseTime;
import static com.ca.attendance.common.JdbcWriteChecks.requireOne;

@Service
public class TrainingService {
    private static final Pattern STUDENT_NO_PATTERN = Pattern.compile("\\d{1,32}");
    private static final int ISSUE_LIMIT = 20;

    private final JdbcTemplate jdbc;
    private final OperationLogService logs;
    private final TrainingQueryService queries;
    private final TrainingExcelExportService excelExports;
    private final TrainingParticipantImportParser participantImports;

    public TrainingService(JdbcTemplate jdbc, OperationLogService logs, TrainingQueryService queries,
                           TrainingExcelExportService excelExports,
                           TrainingParticipantImportParser participantImports) {
        this.jdbc = jdbc;
        this.logs = logs;
        this.queries = queries;
        this.excelExports = excelExports;
        this.participantImports = participantImports;
    }

    @Transactional(readOnly = true)
    public TrainingSessionPage page(
            String keyword,
            String status,
            LocalDate from,
            LocalDate to,
            int page,
            int pageSize
    ) {
        requireViewTrainings(AuthContext.current());
        TrainingQueryService.PageResult<TrainingSessionItem> result = queries.sessionPage(
                keyword, status, from, to, page, pageSize
        );
        return new TrainingSessionPage(
                result.items(),
                result.total(),
                result.page(),
                result.pageSize(),
                result.hasMore()
        );
    }

    @Transactional
    public TrainingSessionItem create(SessionRequest request) {
        AuthUser current = AuthContext.current();
        requireManageTrainings(current);
        SessionValues values = sessionValues(request, null);
        Long id = jdbc.queryForObject("""
                INSERT INTO training_sessions (
                  title, training_date, start_time, end_time, location, speaker, description, status,
                  created_by, updated_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                values.title(),
                databaseDate(values.trainingDate()),
                databaseTime(values.startTime()),
                databaseTime(values.endTime()),
                values.location(),
                values.speaker(),
                values.description(),
                values.status(),
                current.id(),
                current.id()
        );
        TrainingSessionItem created = findSession(id == null ? 0 : id).orElseThrow();
        logs.log("CREATE_TRAINING", "training_sessions", created.id(), null, created, "新增培训");
        return created;
    }

    @Transactional
    public TrainingSessionItem update(long id, SessionRequest request) {
        AuthUser current = AuthContext.current();
        requireManageTrainings(current);
        TrainingSessionItem before = findSession(id).orElseThrow(() -> ApiException.notFound("培训不存在"));
        SessionValues values = sessionValues(request, before);
        int updated = jdbc.update("""
                UPDATE training_sessions
                SET title = ?, training_date = ?, start_time = ?, end_time = ?, location = ?,
                    speaker = ?, description = ?, status = ?, updated_by = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ?
                """,
                values.title(),
                databaseDate(values.trainingDate()),
                databaseTime(values.startTime()),
                databaseTime(values.endTime()),
                values.location(),
                values.speaker(),
                values.description(),
                values.status(),
                current.id(),
                id
        );
        requireOne(updated, "培训状态已经变化，请刷新后重试");
        TrainingSessionItem after = findSession(id).orElseThrow();
        logs.log("UPDATE_TRAINING", "training_sessions", id, before, after, "修改培训");
        return after;
    }

    @Transactional
    public void archive(long id) {
        AuthUser current = AuthContext.current();
        requireManageTrainings(current);
        TrainingSessionItem before = findSession(id).orElseThrow(() -> ApiException.notFound("培训不存在"));
        int updated = jdbc.update("""
                UPDATE training_sessions
                SET status = 'ARCHIVED', updated_by = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ?
                """, current.id(), id);
        requireOne(updated, "培训状态已经变化，请刷新后重试");
        logs.log("ARCHIVE_TRAINING", "training_sessions", id, before, Map.of("status", "ARCHIVED"), "归档培训");
    }

    @Transactional(readOnly = true)
    public TrainingParticipantPage participantPage(long sessionId, String keyword, int page, int pageSize) {
        requireViewTrainings(AuthContext.current());
        ensureSessionExists(sessionId);
        TrainingQueryService.PageResult<TrainingParticipantItem> result = queries.participantPage(
                sessionId, keyword, page, pageSize
        );
        return new TrainingParticipantPage(
                result.items(),
                result.total(),
                result.page(),
                result.pageSize(),
                result.hasMore()
        );
    }

    @Transactional
    public TrainingParticipantItem addParticipant(long sessionId, ParticipantRequest request) {
        AuthUser current = AuthContext.current();
        requireManageTrainings(current);
        ensureSessionExists(sessionId);
        TrainingSessionItem session = findSession(sessionId).orElseThrow(() -> ApiException.notFound("培训不存在"));
        ParticipantValues values = participantValues(request, "MANUAL", defaultDurationHours(session), null);
        try {
            long id = insertParticipant(sessionId, values, current.id());
            TrainingParticipantItem item = findParticipant(sessionId, id).orElseThrow();
            logs.log("CREATE_TRAINING_PARTICIPANT", "training_participants", item.id(), null, item, "新增培训参与记录");
            return item;
        } catch (DuplicateKeyException ex) {
            throw ApiException.badRequest("该学号已在本场培训名单中");
        }
    }

    @Transactional
    public TrainingParticipantItem updateParticipant(long sessionId, long participantId, ParticipantRequest request) {
        AuthUser current = AuthContext.current();
        requireManageTrainings(current);
        TrainingParticipantItem before = findParticipant(sessionId, participantId)
                .orElseThrow(() -> ApiException.notFound("参与记录不存在"));
        TrainingSessionItem session = findSession(sessionId).orElseThrow(() -> ApiException.notFound("培训不存在"));
        ParticipantValues values = participantValues(request, "MANUAL", defaultDurationHours(session), before.durationHours());
        int updated;
        try {
            updated = jdbc.update("""
                    UPDATE training_participants
                    SET user_id = ?, student_no_snapshot = ?, name_snapshot = ?, attendance_status = 'PRESENT',
                        duration_hours = ?, remark = ?, updated_by = ?, updated_at = datetime('now', 'localtime')
                    WHERE id = ? AND session_id = ?
                    """,
                    values.userId(),
                    values.studentNo(),
                    values.name(),
                    values.durationHours(),
                    values.remark(),
                    current.id(),
                    participantId,
                    sessionId
            );
        } catch (DuplicateKeyException ex) {
            throw ApiException.badRequest("该学号已在本场培训名单中");
        }
        requireOne(updated, "培训参与记录状态已经变化，请刷新后重试");
        TrainingParticipantItem after = findParticipant(sessionId, participantId).orElseThrow();
        logs.log("UPDATE_TRAINING_PARTICIPANT", "training_participants", participantId, before, after, "修改培训参与记录");
        return after;
    }

    @Transactional
    public void deleteParticipant(long sessionId, long participantId) {
        AuthUser current = AuthContext.current();
        requireManageTrainings(current);
        TrainingParticipantItem before = findParticipant(sessionId, participantId)
                .orElseThrow(() -> ApiException.notFound("参与记录不存在"));
        int deleted = jdbc.update(
                "DELETE FROM training_participants WHERE id = ? AND session_id = ?",
                participantId,
                sessionId
        );
        requireOne(deleted, "培训参与记录状态已经变化，请刷新后重试");
        logs.log("DELETE_TRAINING_PARTICIPANT", "training_participants", participantId, before, Map.of("deleted", true), "删除培训参与记录");
    }

    @Transactional
    public ImportResult importParticipants(long sessionId, MultipartFile file) {
        AuthUser current = AuthContext.current();
        requireManageTrainings(current);
        ensureSessionExists(sessionId);
        ExcelImportPolicy.validateFile(file, "培训名单");
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                throw ApiException.badRequest("Excel 文件没有工作表");
            }
            ImportResult result = importSheet(sessionId, sheet, current.id());
            if (!result.errors().isEmpty()) {
                throw ApiException.badRequest(importFailureMessage(result.errors()));
            }
            logs.log("IMPORT_TRAINING_PARTICIPANTS", "training_participants", sessionId, null, result, "导入培训参与名单");
            return result;
        } catch (ApiException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("Excel 文件读取失败，请确认文件格式正确");
        }
    }

    public ExportFile exportSessionImportTemplate(long sessionId) {
        AuthUser current = AuthContext.current();
        requireManageTrainings(current);
        TrainingSessionItem session = findSession(sessionId).orElseThrow(() -> ApiException.notFound("培训不存在"));
        TrainingExcelExportService.ExportDocument document =
                excelExports.generateImportTemplate(session, defaultDurationHours(session));
        return new ExportFile(document.filename(), document.bytes());
    }

    public ExportFile exportSession(long sessionId) {
        AuthUser current = AuthContext.current();
        requireExportTrainings(current);
        TrainingSessionItem session = findSession(sessionId).orElseThrow(() -> ApiException.notFound("培训不存在"));
        List<TrainingParticipantItem> rows = queries.participants(sessionId);
        ExportRowLimit.requireWithinLimit(rows.size());
        TrainingExcelExportService.ExportDocument document = excelExports.generateSession(session, rows);
        logs.logExport("TRAINING_SESSION", "培训名单", Map.of("sessionId", sessionId), rows.size(), document.filename());
        return new ExportFile(document.filename(), document.bytes());
    }

    public ExportFile exportSummary(String keyword, String status, LocalDate from, LocalDate to) {
        AuthUser current = AuthContext.current();
        requireExportTrainings(current);
        LocalDate start = from == null ? LocalDate.of(LocalDate.now().getYear(), 1, 1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        List<TrainingSessionItem> sessions = queries.sessions(keyword, status, start, end);
        List<Map<String, Object>> memberRows = queries.memberSummary(start, end);
        ExportRowLimit.requireWithinLimit(sessions.size() + memberRows.size());
        TrainingExcelExportService.ExportDocument document =
                excelExports.generateSummary(sessions, memberRows, start, end);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("from", start.toString());
        filters.put("to", end.toString());
        if (keyword != null && !keyword.isBlank()) {
            filters.put("keyword", keyword.trim());
        }
        logs.logExport(
                "TRAINING_SUMMARY",
                "培训统计",
                filters,
                sessions.size() + memberRows.size(),
                document.filename(),
                Map.of("sessionRows", sessions.size(), "memberRows", memberRows.size())
        );
        return new ExportFile(document.filename(), document.bytes());
    }

    @Transactional(readOnly = true)
    public List<MyTrainingRecordItem> myRecords(LocalDate from, LocalDate to) {
        AuthUser current = AuthContext.current();
        return queries.myRecords(current.id(), from, to);
    }

    private ImportResult importSheet(long sessionId, Sheet sheet, long operatorId) {
        TrainingSessionItem session = findSession(sessionId).orElseThrow(() -> ApiException.notFound("培训不存在"));
        BigDecimal defaultDuration = defaultDurationHours(session);
        TrainingParticipantImportParser.ParseResult parsed = participantImports.parse(sheet, defaultDuration);
        List<ParticipantValues> valuesToWrite = new ArrayList<>();
        List<String> errors = new ArrayList<>(parsed.errors());
        int skipped = parsed.skipped();
        for (TrainingParticipantImportParser.ParsedRow row : parsed.rows()) {
            try {
                valuesToWrite.add(importParticipantValues(row));
            } catch (ApiException ex) {
                skipped++;
                addIssue(errors, "第 " + row.rowNumber() + " 行：" + ex.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            return new ImportResult(0, 0, skipped, errors);
        }

        int created = 0;
        int updated = 0;
        Set<String> existingStudentNumbers = new HashSet<>(jdbc.queryForList(
                "SELECT student_no_snapshot FROM training_participants WHERE session_id = ?",
                String.class,
                sessionId
        ));
        for (ParticipantValues values : valuesToWrite) {
            if (existingStudentNumbers.contains(values.studentNo())) {
                updated++;
            } else {
                created++;
            }
            upsertParticipant(sessionId, values, operatorId);
        }
        return new ImportResult(created, updated, skipped, errors);
    }

    private ParticipantValues importParticipantValues(TrainingParticipantImportParser.ParsedRow row) {
        String studentNo = row.studentNo();
        UserRef user;
        if (studentNo.isBlank()) {
            user = findUniqueUserByName(row.name())
                    .orElseThrow(() -> ApiException.badRequest("请填写学号；姓名未能唯一匹配成员"));
            studentNo = user.studentNo();
        } else {
            user = findUser(studentNo).orElse(null);
        }
        return new ParticipantValues(
                user == null ? null : user.id(),
                studentNo,
                user == null ? row.name() : user.name(),
                row.durationHours(),
                row.remark(),
                "IMPORT"
        );
    }

    private String importFailureMessage(List<String> errors) {
        return "培训名单校验未通过，未写入任何记录：" + String.join("；", errors);
    }

    private Optional<TrainingSessionItem> findSession(long id) {
        return queries.findSession(id);
    }

    private void ensureSessionExists(long sessionId) {
        findSession(sessionId).orElseThrow(() -> ApiException.notFound("培训不存在"));
    }

    private Optional<TrainingParticipantItem> findParticipant(long sessionId, long participantId) {
        return queries.findParticipant(sessionId, participantId);
    }

    private long insertParticipant(long sessionId, ParticipantValues values, Long operatorId) {
        Long id = jdbc.queryForObject("""
                INSERT INTO training_participants (
                  session_id, user_id, student_no_snapshot, name_snapshot, attendance_status,
                  duration_hours, remark, source, created_by, updated_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                sessionId,
                values.userId(),
                values.studentNo(),
                values.name(),
                "PRESENT",
                values.durationHours(),
                values.remark(),
                values.source(),
                operatorId,
                operatorId
        );
        return id == null ? 0 : id;
    }

    private void upsertParticipant(long sessionId, ParticipantValues values, Long operatorId) {
        int affected = jdbc.update("""
                INSERT INTO training_participants (
                  session_id, user_id, student_no_snapshot, name_snapshot, attendance_status,
                  duration_hours, remark, source, created_by, updated_by
                )
                VALUES (?, ?, ?, ?, 'PRESENT', ?, ?, ?, ?, ?)
                ON CONFLICT(session_id, student_no_snapshot) DO UPDATE SET
                  user_id = excluded.user_id,
                  name_snapshot = excluded.name_snapshot,
                  attendance_status = 'PRESENT',
                  duration_hours = excluded.duration_hours,
                  remark = excluded.remark,
                  source = excluded.source,
                  updated_by = excluded.updated_by,
                  updated_at = datetime('now', 'localtime')
                """,
                sessionId,
                values.userId(),
                values.studentNo(),
                values.name(),
                values.durationHours(),
                values.remark(),
                values.source(),
                operatorId,
                operatorId
        );
        if (affected != 1) {
            throw new IllegalStateException("培训参与记录写入数量异常");
        }
    }

    private ParticipantValues participantValues(ParticipantRequest request, String source, BigDecimal defaultDuration, BigDecimal fallbackDuration) {
        String requestedName = required(request.name(), "姓名不能为空");
        String studentNo = request.studentNo() == null ? "" : request.studentNo().replaceAll("\\s+", "");
        UserRef user;
        if (studentNo.isBlank()) {
            user = findUniqueUserByName(requestedName)
                    .orElseThrow(() -> ApiException.badRequest("请填写学号；姓名未能唯一匹配成员"));
            studentNo = user.studentNo();
        } else if (!STUDENT_NO_PATTERN.matcher(studentNo).matches()) {
            throw ApiException.badRequest("学号格式不正确");
        } else {
            user = findUser(studentNo).orElse(null);
        }
        String name = user == null ? requestedName : user.name();
        return new ParticipantValues(
                user == null ? null : user.id(),
                studentNo,
                name,
                normalizedDuration(request.durationHours(), defaultDuration, fallbackDuration),
                trimToNull(request.remark(), 500),
                source
        );
    }

    private Optional<UserRef> findUser(String studentNo) {
        return jdbc.query("""
                SELECT id, student_no, name
                FROM users
                WHERE student_no = ?
                LIMIT 1
                """, (rs, rowNum) -> new UserRef(
                rs.getLong("id"),
                rs.getString("student_no"),
                rs.getString("name")
        ), studentNo).stream().findFirst();
    }

    private Optional<UserRef> findUniqueUserByName(String name) {
        List<UserRef> matches = jdbc.query("""
                SELECT id, student_no, name
                FROM users
                WHERE name = ?
                  AND status = 'ACTIVE'
                LIMIT 2
                """, (rs, rowNum) -> new UserRef(
                rs.getLong("id"),
                rs.getString("student_no"),
                rs.getString("name")
        ), name);
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private SessionValues sessionValues(SessionRequest request, TrainingSessionItem fallback) {
        String title = request.title() == null && fallback != null ? fallback.title() : request.title();
        LocalDate date = request.trainingDate() == null && fallback != null ? fallback.trainingDate() : request.trainingDate();
        if (date == null) {
            throw ApiException.badRequest("培训日期不能为空");
        }
        LocalTime start = request.startTime() == null && fallback != null ? fallback.startTime() : request.startTime();
        LocalTime end = request.endTime() == null && fallback != null ? fallback.endTime() : request.endTime();
        if (start != null && end != null && end.isBefore(start)) {
            throw ApiException.badRequest("结束时间不能早于开始时间");
        }
        return new SessionValues(
                required(title, "培训标题不能为空", 100, "培训标题"),
                date,
                start,
                end,
                trimToNull(request.location() == null && fallback != null ? fallback.location() : request.location(), 120),
                trimToNull(request.speaker() == null && fallback != null ? fallback.speaker() : request.speaker(), 120),
                trimToNull(request.description() == null && fallback != null ? fallback.description() : request.description(), 500),
                TrainingSessionStatus.parse(request.status() == null && fallback != null ? fallback.status() : request.status())
        );
    }

    private BigDecimal normalizedDuration(BigDecimal requested, BigDecimal defaultDuration, BigDecimal fallbackDuration) {
        BigDecimal value = requested == null ? fallbackDuration : requested;
        if (value == null) {
            value = defaultDuration;
        }
        if (value == null) {
            value = BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("培训时长不能为负数");
        }
        if (value.compareTo(new BigDecimal("999.99")) > 0) {
            throw ApiException.badRequest("培训时长不能超过 999.99 小时");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultDurationHours(TrainingSessionItem session) {
        LocalTime start = session.startTime();
        LocalTime end = session.endTime();
        if (start == null || end == null || !end.isAfter(start)) {
            return BigDecimal.ZERO;
        }
        long minutes = java.time.Duration.between(start, end).toMinutes();
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private void requireManageTrainings(AuthUser current) {
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.TRAINING_MANAGE,
                "只有会长或管理员可以管理培训");
    }

    private void requireViewTrainings(AuthUser current) {
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.TRAINING_MANAGE,
                "只有会长或管理员可以查看培训管理");
    }

    private void requireExportTrainings(AuthUser current) {
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.TRAINING_MANAGE,
                "只有会长或管理员可以导出培训 Excel");
    }

    private String sessionStatusText(String status) {
        return switch (status) {
            case "PLANNED" -> "计划中";
            case "COMPLETED" -> "已完成";
            case "CANCELED" -> "已取消";
            case "ARCHIVED" -> "已归档";
            default -> status;
        };
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(message);
        }
        return value.trim();
    }

    private String required(String value, String message, int maxLength, String label) {
        String normalized = required(value, message);
        if (normalized.length() > maxLength) {
            throw ApiException.badRequest(label + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String trimToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private void addIssue(List<String> issues, String issue) {
        if (issues.size() < ISSUE_LIMIT) {
            issues.add(issue);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionRequest(
            String title,
            LocalDate trainingDate,
            LocalTime startTime,
            LocalTime endTime,
            String location,
            String speaker,
            String description,
            String status
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParticipantRequest(String studentNo, String name, BigDecimal durationHours, String remark) {
    }

    public record ImportResult(int created, int updated, int skipped, List<String> errors) {
    }

    public record TrainingSessionPage(
            List<TrainingSessionItem> items,
            long total,
            int page,
            int pageSize,
            boolean hasMore
    ) {
    }

    public record TrainingParticipantPage(
            List<TrainingParticipantItem> items,
            long total,
            int page,
            int pageSize,
            boolean hasMore
    ) {
    }

    public record ExportFile(String filename, byte[] bytes) {
    }

    private record SessionValues(
            String title,
            LocalDate trainingDate,
            LocalTime startTime,
            LocalTime endTime,
            String location,
            String speaker,
            String description,
            String status
    ) {
    }

    private record ParticipantValues(Long userId, String studentNo, String name, BigDecimal durationHours, String remark, String source) {
    }

    private record UserRef(long id, String studentNo, String name) {
    }

}
