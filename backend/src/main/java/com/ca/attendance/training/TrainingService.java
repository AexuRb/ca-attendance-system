package com.ca.attendance.training;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class TrainingService {
    private static final Pattern STUDENT_NO_PATTERN = Pattern.compile("\\d{1,32}");
    private static final int ISSUE_LIMIT = 20;
    private static final int MAX_EXCEL_ROWS = 3000;

    private final JdbcTemplate jdbc;
    private final OperationLogService logs;

    private final RowMapper<TrainingSessionItem> sessionMapper = (rs, rowNum) -> new TrainingSessionItem(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getDate("training_date").toLocalDate(),
            toLocalTime(rs.getTime("start_time")),
            toLocalTime(rs.getTime("end_time")),
            rs.getString("location"),
            rs.getString("speaker"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getLong("participant_count"),
            rs.getBigDecimal("total_duration_hours"),
            rs.getLong("present_count"),
            rs.getLong("absent_count"),
            rs.getLong("leave_count"),
            rs.getString("created_by_name"),
            rs.getString("updated_by_name"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private final RowMapper<TrainingParticipantItem> participantMapper = (rs, rowNum) -> new TrainingParticipantItem(
            rs.getLong("id"),
            rs.getLong("session_id"),
            nullableLong(rs, "user_id"),
            rs.getString("student_no_snapshot"),
            rs.getString("name_snapshot"),
            rs.getString("attendance_status"),
            rs.getBigDecimal("duration_hours"),
            rs.getString("remark"),
            rs.getString("source"),
            rs.getString("created_by_name"),
            rs.getString("updated_by_name"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    public TrainingService(JdbcTemplate jdbc, OperationLogService logs) {
        this.jdbc = jdbc;
        this.logs = logs;
    }

    public List<TrainingSessionItem> list(String keyword, String status, LocalDate from, LocalDate to) {
        requireViewTrainings(AuthContext.current());
        LocalDate start = from == null ? LocalDate.of(LocalDate.now().getYear(), 1, 1) : from;
        LocalDate end = to == null ? LocalDate.now().plusYears(1) : to;
        if (start.isAfter(end)) {
            throw ApiException.badRequest("开始日期不能晚于结束日期");
        }
        List<Object> args = new ArrayList<>();
        args.add(start);
        args.add(end);
        StringBuilder where = new StringBuilder("""
                WHERE s.status <> 'ARCHIVED'
                  AND s.training_date BETWEEN ? AND ?
                """);
        if (keyword != null && !keyword.isBlank()) {
            where.append("""
                    AND (
                      s.title LIKE ?
                      OR s.location LIKE ?
                      OR s.speaker LIKE ?
                      OR s.description LIKE ?
                    )
                    """);
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return querySessions(where.toString(), args.toArray());
    }

    public TrainingSessionItem create(SessionRequest request) {
        AuthUser current = AuthContext.current();
        requireManageTrainings(current);
        SessionValues values = sessionValues(request, null);
        jdbc.update("""
                INSERT INTO training_sessions (
                  title, training_date, start_time, end_time, location, speaker, description, status,
                  created_by, updated_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                values.title(),
                Date.valueOf(values.trainingDate()),
                toSqlTime(values.startTime()),
                toSqlTime(values.endTime()),
                values.location(),
                values.speaker(),
                values.description(),
                values.status(),
                current.id(),
                current.id()
        );
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        TrainingSessionItem created = findSession(id == null ? 0 : id).orElseThrow();
        logs.log("CREATE_TRAINING", "training_sessions", created.id(), null, created, "新增培训");
        return created;
    }

    public TrainingSessionItem update(long id, SessionRequest request) {
        AuthUser current = AuthContext.current();
        requireManageTrainings(current);
        TrainingSessionItem before = findSession(id).orElseThrow(() -> ApiException.notFound("培训不存在"));
        SessionValues values = sessionValues(request, before);
        jdbc.update("""
                UPDATE training_sessions
                SET title = ?, training_date = ?, start_time = ?, end_time = ?, location = ?,
                    speaker = ?, description = ?, status = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                values.title(),
                Date.valueOf(values.trainingDate()),
                toSqlTime(values.startTime()),
                toSqlTime(values.endTime()),
                values.location(),
                values.speaker(),
                values.description(),
                values.status(),
                current.id(),
                id
        );
        TrainingSessionItem after = findSession(id).orElseThrow();
        logs.log("UPDATE_TRAINING", "training_sessions", id, before, after, "修改培训");
        return after;
    }

    public void archive(long id) {
        AuthUser current = AuthContext.current();
        requireManageTrainings(current);
        TrainingSessionItem before = findSession(id).orElseThrow(() -> ApiException.notFound("培训不存在"));
        jdbc.update("""
                UPDATE training_sessions
                SET status = 'ARCHIVED', updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, current.id(), id);
        logs.log("ARCHIVE_TRAINING", "training_sessions", id, before, Map.of("status", "ARCHIVED"), "归档培训");
    }

    public List<TrainingParticipantItem> participants(long sessionId) {
        requireViewTrainings(AuthContext.current());
        ensureSessionExists(sessionId);
        return queryParticipants(sessionId);
    }

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

    public TrainingParticipantItem updateParticipant(long sessionId, long participantId, ParticipantRequest request) {
        AuthUser current = AuthContext.current();
        requireManageTrainings(current);
        TrainingParticipantItem before = findParticipant(sessionId, participantId)
                .orElseThrow(() -> ApiException.notFound("参与记录不存在"));
        TrainingSessionItem session = findSession(sessionId).orElseThrow(() -> ApiException.notFound("培训不存在"));
        ParticipantValues values = participantValues(request, "MANUAL", defaultDurationHours(session), before.durationHours());
        try {
            jdbc.update("""
                    UPDATE training_participants
                    SET user_id = ?, student_no_snapshot = ?, name_snapshot = ?, attendance_status = ?,
                        duration_hours = ?, remark = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND session_id = ?
                    """,
                    values.userId(),
                    values.studentNo(),
                    values.name(),
                    values.status(),
                    values.durationHours(),
                    values.remark(),
                    current.id(),
                    participantId,
                    sessionId
            );
        } catch (DuplicateKeyException ex) {
            throw ApiException.badRequest("该学号已在本场培训名单中");
        }
        TrainingParticipantItem after = findParticipant(sessionId, participantId).orElseThrow();
        logs.log("UPDATE_TRAINING_PARTICIPANT", "training_participants", participantId, before, after, "修改培训参与记录");
        return after;
    }

    public void deleteParticipant(long sessionId, long participantId) {
        AuthUser current = AuthContext.current();
        requireManageTrainings(current);
        TrainingParticipantItem before = findParticipant(sessionId, participantId)
                .orElseThrow(() -> ApiException.notFound("参与记录不存在"));
        jdbc.update("DELETE FROM training_participants WHERE id = ? AND session_id = ?", participantId, sessionId);
        logs.log("DELETE_TRAINING_PARTICIPANT", "training_participants", participantId, before, Map.of("deleted", true), "删除培训参与记录");
    }

    public ImportResult importParticipants(long sessionId, MultipartFile file) {
        AuthUser current = AuthContext.current();
        requireManageTrainings(current);
        ensureSessionExists(sessionId);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择 Excel 文件");
        }
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                throw ApiException.badRequest("Excel 文件没有工作表");
            }
            ImportResult result = importSheet(sessionId, sheet, current.id());
            logs.log("IMPORT_TRAINING_PARTICIPANTS", "training_participants", sessionId, null, result, "导入培训参与名单");
            return result;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("Excel 文件读取失败，请确认文件格式正确");
        }
    }

    public ExportFile exportImportTemplate() {
        AuthUser current = AuthContext.current();
        requireManageTrainings(current);
        return new ExportFile("培训名单导入模板.xlsx", workbookBytes(wb -> writeImportTemplateWorkbook(wb, null)));
    }

    public ExportFile exportSessionImportTemplate(long sessionId) {
        AuthUser current = AuthContext.current();
        requireManageTrainings(current);
        TrainingSessionItem session = findSession(sessionId).orElseThrow(() -> ApiException.notFound("培训不存在"));
        String filename = "培训名单导入模板_" + cleanFilename(session.title()) + "_" + session.trainingDate() + ".xlsx";
        return new ExportFile(filename, workbookBytes(wb -> writeImportTemplateWorkbook(wb, session)));
    }

    public ExportFile exportSession(long sessionId) {
        AuthUser current = AuthContext.current();
        requireExportTrainings(current);
        TrainingSessionItem session = findSession(sessionId).orElseThrow(() -> ApiException.notFound("培训不存在"));
        List<TrainingParticipantItem> rows = queryParticipants(sessionId);
        String filename = "培训名单_" + cleanFilename(session.title()) + "_" + session.trainingDate() + ".xlsx";
        return new ExportFile(filename, workbookBytes(wb -> writeSessionWorkbook(wb, session, rows)));
    }

    public ExportFile exportSummary(String keyword, String status, LocalDate from, LocalDate to) {
        AuthUser current = AuthContext.current();
        requireExportTrainings(current);
        List<TrainingSessionItem> sessions = list(keyword, status, from, to);
        LocalDate start = from == null ? LocalDate.of(LocalDate.now().getYear(), 1, 1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        String filename = "培训统计_" + start + "_" + end + ".xlsx";
        return new ExportFile(filename, workbookBytes(wb -> writeSummaryWorkbook(wb, sessions, start, end)));
    }

    public Map<String, Object> myHours(LocalDate from, LocalDate to) {
        AuthUser current = AuthContext.current();
        LocalDate start = from == null ? LocalDate.of(LocalDate.now().getYear(), 1, 1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        if (start.isAfter(end)) {
            throw ApiException.badRequest("开始日期不能晚于结束日期");
        }
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT COUNT(*) AS trainingCount,
                       COALESCE(SUM(p.duration_hours), 0) AS trainingHours
                FROM training_participants p
                JOIN training_sessions s ON s.id = p.session_id
                WHERE s.status <> 'ARCHIVED'
                  AND s.training_date BETWEEN ? AND ?
                  AND p.user_id = ?
                  AND p.duration_hours > 0
                """, start, end, current.id());
        return Map.of(
                "trainingCount", number(row.get("trainingCount")),
                "trainingHours", decimal(row.get("trainingHours"))
        );
    }

    private ImportResult importSheet(long sessionId, Sheet sheet, long operatorId) {
        TrainingSessionItem session = findSession(sessionId).orElseThrow(() -> ApiException.notFound("培训不存在"));
        BigDecimal defaultDuration = defaultDurationHours(session);
        DataFormatter formatter = new DataFormatter();
        int headerIndex = findHeaderRow(sheet, formatter);
        Map<String, Integer> columns = headerIndex >= 0
                ? headerColumns(sheet.getRow(headerIndex), formatter)
                : fallbackColumns();
        int startRow = headerIndex >= 0 ? headerIndex + 1 : 0;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int rowLimit = Math.min(sheet.getLastRowNum(), startRow + MAX_EXCEL_ROWS);

        for (int i = startRow; i <= rowLimit; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            String studentNo = cell(row, columns.get("studentNo"), formatter).replaceAll("\\s+", "");
            String name = cell(row, columns.get("name"), formatter).trim();
            String duration = cell(row, columns.get("duration"), formatter);
            String remark = cell(row, columns.get("remark"), formatter);
            if (studentNo.isBlank() && name.isBlank() && duration.isBlank() && remark.isBlank()) {
                continue;
            }
            if (name.isBlank()) {
                skipped++;
                addIssue(errors, "第 " + (i + 1) + " 行：缺少姓名");
                continue;
            }
            String seenKey = studentNo.isBlank() ? "name:" + name : "student:" + studentNo;
            if (!seen.add(seenKey)) {
                skipped++;
                addIssue(errors, "第 " + (i + 1) + " 行：名单在本次文件中重复");
                continue;
            }
            ParticipantValues values;
            try {
                values = participantValues(new ParticipantRequest(studentNo, name, parseDuration(duration, defaultDuration), "PRESENT", remark), "IMPORT", defaultDuration, null);
            } catch (ApiException ex) {
                skipped++;
                addIssue(errors, "第 " + (i + 1) + " 行：" + ex.getMessage());
                continue;
            }
            if (participantExists(sessionId, values.studentNo())) {
                updateParticipantByStudent(sessionId, values, operatorId);
                updated++;
            } else {
                insertParticipant(sessionId, values, operatorId);
                created++;
            }
        }
        if (sheet.getLastRowNum() > rowLimit) {
            addIssue(errors, "文件超过 " + MAX_EXCEL_ROWS + " 行，后续行已跳过");
        }
        return new ImportResult(created, updated, skipped, errors);
    }

    private void writeImportTemplateWorkbook(Workbook wb, TrainingSessionItem session) {
        CellStyle titleStyle = titleStyle(wb);
        CellStyle headerStyle = headerStyle(wb);
        CellStyle textStyle = textStyle(wb);

        Sheet dataSheet = wb.createSheet("参与名单");
        String[] headers = {"学号", "姓名", "时长", "备注"};
        Row header = dataSheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        if (session != null && session.speaker() != null && !session.speaker().isBlank()) {
            Row speaker = dataSheet.createRow(1);
            speaker.createCell(0).setCellValue("");
            speaker.createCell(1).setCellValue(session.speaker());
            speaker.createCell(2).setCellValue(defaultDurationHours(session).doubleValue());
            speaker.createCell(3).setCellValue("主讲人");
            for (int col = 0; col < headers.length; col++) {
                speaker.getCell(col).setCellStyle(textStyle);
            }
        }
        for (int i = 0; i < headers.length; i++) {
            dataSheet.autoSizeColumn(i);
            dataSheet.setColumnWidth(i, Math.min(Math.max(dataSheet.getColumnWidth(i) + 1024, 14 * 256), 24 * 256));
        }
        dataSheet.createFreezePane(0, 1);

        Sheet noteSheet = wb.createSheet("填写说明");
        Row title = noteSheet.createRow(0);
        title.createCell(0).setCellValue("培训参与名单导入模板");
        title.getCell(0).setCellStyle(titleStyle);
        List<String> notes = new ArrayList<>();
        if (session == null) {
            notes.add("通用模板：请在培训管理中选择具体培训后导入。");
        } else {
            notes.add("培训：" + session.title() + "（" + session.trainingDate() + "）");
            notes.add("主讲人：" + nullToDash(session.speaker()));
            notes.add("默认时长：" + defaultDurationHours(session).stripTrailingZeros().toPlainString() + " 小时");
        }
        notes.add("参与名单工作表第一行为表头，请从第二行开始填写。");
        notes.add("必填列：姓名。建议同时填写学号，避免同名成员无法匹配。");
        notes.add("时长可不填；不填时导入会使用该培训的开始/结束时间。");
        notes.add("时长会计入值班时长，可填写 1、1.5、2 或 2小时。");
        notes.add("第一条数据建议填写主讲人；当前培训已填写主讲人时会自动预填。");
        for (int i = 0; i < notes.size(); i++) {
            Row row = noteSheet.createRow(i + 2);
            Cell cell = row.createCell(0);
            cell.setCellValue(notes.get(i));
            cell.setCellStyle(textStyle);
        }
        noteSheet.setColumnWidth(0, 58 * 256);
        wb.setActiveSheet(0);
    }

    private void writeSessionWorkbook(Workbook wb, TrainingSessionItem session, List<TrainingParticipantItem> rows) {
        Sheet sheet = wb.createSheet("培训名单");
        CellStyle titleStyle = titleStyle(wb);
        CellStyle headerStyle = headerStyle(wb);
        CellStyle textStyle = textStyle(wb);

        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue(session.title());
        title.getCell(0).setCellStyle(titleStyle);
        Row meta = sheet.createRow(1);
        meta.createCell(0).setCellValue("日期");
        meta.createCell(1).setCellValue(String.valueOf(session.trainingDate()));
        meta.createCell(2).setCellValue("地点");
        meta.createCell(3).setCellValue(nullToDash(session.location()));
        meta.createCell(4).setCellValue("主讲人");
        meta.createCell(5).setCellValue(nullToDash(session.speaker()));

        String[] headers = {"序号", "学号", "姓名", "时长", "备注"};
        Row header = sheet.createRow(3);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (int i = 0; i < rows.size(); i++) {
            TrainingParticipantItem item = rows.get(i);
            Row row = sheet.createRow(i + 4);
            row.createCell(0).setCellValue(i + 1);
            row.createCell(1).setCellValue(item.studentNo());
            row.createCell(2).setCellValue(item.name());
            row.createCell(3).setCellValue(item.durationHours().doubleValue());
            row.createCell(4).setCellValue(nullToDash(item.remark()));
            for (int col = 0; col < headers.length; col++) {
                row.getCell(col).setCellStyle(textStyle);
            }
        }
        autosize(sheet, headers.length);
    }

    private void writeSummaryWorkbook(Workbook wb, List<TrainingSessionItem> sessions, LocalDate from, LocalDate to) {
        CellStyle titleStyle = titleStyle(wb);
        CellStyle headerStyle = headerStyle(wb);
        CellStyle textStyle = textStyle(wb);

        Sheet sessionSheet = wb.createSheet("培训场次");
        Row title = sessionSheet.createRow(0);
        title.createCell(0).setCellValue("培训统计 " + from + " 至 " + to);
        title.getCell(0).setCellStyle(titleStyle);
        String[] sessionHeaders = {"日期", "标题", "地点", "主讲人", "参与", "培训时长"};
        Row header = sessionSheet.createRow(2);
        for (int i = 0; i < sessionHeaders.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(sessionHeaders[i]);
            cell.setCellStyle(headerStyle);
        }
        for (int i = 0; i < sessions.size(); i++) {
            TrainingSessionItem item = sessions.get(i);
            Row row = sessionSheet.createRow(i + 3);
            row.createCell(0).setCellValue(String.valueOf(item.trainingDate()));
            row.createCell(1).setCellValue(item.title());
            row.createCell(2).setCellValue(nullToDash(item.location()));
            row.createCell(3).setCellValue(nullToDash(item.speaker()));
            row.createCell(4).setCellValue(item.participantCount());
            row.createCell(5).setCellValue(item.totalDurationHours().doubleValue());
            for (int col = 0; col < sessionHeaders.length; col++) {
                row.getCell(col).setCellStyle(textStyle);
            }
        }
        autosize(sessionSheet, sessionHeaders.length);

        Sheet memberSheet = wb.createSheet("成员统计");
        String[] memberHeaders = {"学号", "姓名", "参加次数", "培训时长"};
        Row memberHeader = memberSheet.createRow(0);
        for (int i = 0; i < memberHeaders.length; i++) {
            Cell cell = memberHeader.createCell(i);
            cell.setCellValue(memberHeaders[i]);
            cell.setCellStyle(headerStyle);
        }
        List<Map<String, Object>> memberRows = jdbc.queryForList("""
                SELECT p.student_no_snapshot AS studentNo,
                       p.name_snapshot AS name,
                       COUNT(*) AS trainingCount,
                       COALESCE(SUM(p.duration_hours), 0) AS durationHours
                FROM training_participants p
                JOIN training_sessions s ON s.id = p.session_id
                WHERE s.status <> 'ARCHIVED'
                  AND s.training_date BETWEEN ? AND ?
                GROUP BY p.student_no_snapshot, p.name_snapshot
                ORDER BY durationHours DESC, trainingCount DESC, p.student_no_snapshot
                """, from, to);
        for (int i = 0; i < memberRows.size(); i++) {
            Map<String, Object> item = memberRows.get(i);
            Row row = memberSheet.createRow(i + 1);
            row.createCell(0).setCellValue(String.valueOf(item.get("studentNo")));
            row.createCell(1).setCellValue(String.valueOf(item.get("name")));
            row.createCell(2).setCellValue(number(item.get("trainingCount")));
            row.createCell(3).setCellValue(decimal(item.get("durationHours")).doubleValue());
            for (int col = 0; col < memberHeaders.length; col++) {
                row.getCell(col).setCellStyle(textStyle);
            }
        }
        autosize(memberSheet, memberHeaders.length);
    }

    private List<TrainingSessionItem> querySessions(String where, Object... args) {
        return jdbc.query("""
                SELECT s.*,
                       cb.name AS created_by_name,
                       ub.name AS updated_by_name,
                       (SELECT COUNT(*) FROM training_participants p WHERE p.session_id = s.id) AS participant_count,
                       (SELECT COALESCE(SUM(p.duration_hours), 0) FROM training_participants p WHERE p.session_id = s.id) AS total_duration_hours,
                       (SELECT COUNT(*) FROM training_participants p WHERE p.session_id = s.id AND p.attendance_status = 'PRESENT') AS present_count,
                       (SELECT COUNT(*) FROM training_participants p WHERE p.session_id = s.id AND p.attendance_status = 'ABSENT') AS absent_count,
                       (SELECT COUNT(*) FROM training_participants p WHERE p.session_id = s.id AND p.attendance_status = 'LEAVE') AS leave_count
                FROM training_sessions s
                LEFT JOIN users cb ON cb.id = s.created_by
                LEFT JOIN users ub ON ub.id = s.updated_by
                """ + where + """

                ORDER BY s.training_date DESC, s.id DESC
                """, sessionMapper, args);
    }

    private Optional<TrainingSessionItem> findSession(long id) {
        return querySessions("WHERE s.id = ?", id).stream().findFirst();
    }

    private void ensureSessionExists(long sessionId) {
        findSession(sessionId).orElseThrow(() -> ApiException.notFound("培训不存在"));
    }

    private List<TrainingParticipantItem> queryParticipants(long sessionId) {
        return jdbc.query("""
                SELECT p.*,
                       cb.name AS created_by_name,
                       ub.name AS updated_by_name
                FROM training_participants p
                JOIN training_sessions s ON s.id = p.session_id
                LEFT JOIN users cb ON cb.id = p.created_by
                LEFT JOIN users ub ON ub.id = p.updated_by
                WHERE p.session_id = ?
                ORDER BY
                  CASE WHEN s.speaker IS NOT NULL AND s.speaker <> '' AND p.name_snapshot = s.speaker THEN 0 ELSE 1 END,
                  p.student_no_snapshot
                """, participantMapper, sessionId);
    }

    private Optional<TrainingParticipantItem> findParticipant(long sessionId, long participantId) {
        return jdbc.query("""
                SELECT p.*,
                       cb.name AS created_by_name,
                       ub.name AS updated_by_name
                FROM training_participants p
                LEFT JOIN users cb ON cb.id = p.created_by
                LEFT JOIN users ub ON ub.id = p.updated_by
                WHERE p.session_id = ? AND p.id = ?
                """, participantMapper, sessionId, participantId).stream().findFirst();
    }

    private long insertParticipant(long sessionId, ParticipantValues values, Long operatorId) {
        jdbc.update("""
                INSERT INTO training_participants (
                  session_id, user_id, student_no_snapshot, name_snapshot, attendance_status,
                  duration_hours, remark, source, created_by, updated_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                sessionId,
                values.userId(),
                values.studentNo(),
                values.name(),
                values.status(),
                values.durationHours(),
                values.remark(),
                values.source(),
                operatorId,
                operatorId
        );
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0 : id;
    }

    private void updateParticipantByStudent(long sessionId, ParticipantValues values, Long operatorId) {
        jdbc.update("""
                UPDATE training_participants
                SET user_id = ?, name_snapshot = ?, attendance_status = ?, duration_hours = ?, remark = ?,
                    source = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE session_id = ? AND student_no_snapshot = ?
                """,
                values.userId(),
                values.name(),
                values.status(),
                values.durationHours(),
                values.remark(),
                values.source(),
                operatorId,
                sessionId,
                values.studentNo()
        );
    }

    private boolean participantExists(long sessionId, String studentNo) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM training_participants
                WHERE session_id = ? AND student_no_snapshot = ?
                """, Integer.class, sessionId, studentNo);
        return count != null && count > 0;
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
                parseParticipantStatus(request.attendanceStatus()),
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
                required(title, "培训标题不能为空"),
                date,
                start,
                end,
                trimToNull(request.location() == null && fallback != null ? fallback.location() : request.location(), 120),
                trimToNull(request.speaker() == null && fallback != null ? fallback.speaker() : request.speaker(), 120),
                trimToNull(request.description() == null && fallback != null ? fallback.description() : request.description(), 500),
                parseSessionStatus(request.status() == null && fallback != null ? fallback.status() : request.status())
        );
    }

    private int findHeaderRow(Sheet sheet, DataFormatter formatter) {
        int last = Math.min(sheet.getLastRowNum(), 8);
        for (int rowIndex = 0; rowIndex <= last; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            boolean hasStudentNo = false;
            boolean hasName = false;
            for (int col = Math.max(row.getFirstCellNum(), 0); col < row.getLastCellNum(); col++) {
                String header = clean(formatter.formatCellValue(row.getCell(col)));
                if (header.contains("学号") || header.equalsIgnoreCase("studentNo")) {
                    hasStudentNo = true;
                }
                if (header.contains("姓名") || header.equalsIgnoreCase("name")) {
                    hasName = true;
                }
            }
            if (hasStudentNo && hasName) {
                return rowIndex;
            }
        }
        return -1;
    }

    private Map<String, Integer> headerColumns(Row row, DataFormatter formatter) {
        Map<String, Integer> columns = new HashMap<>();
        for (int col = Math.max(row.getFirstCellNum(), 0); col < row.getLastCellNum(); col++) {
            String header = clean(formatter.formatCellValue(row.getCell(col))).toLowerCase(Locale.ROOT);
            if (header.contains("学号") || header.contains("student")) {
                columns.putIfAbsent("studentNo", col);
            }
            if (header.contains("姓名") || header.equals("name")) {
                columns.putIfAbsent("name", col);
            }
            if (header.contains("时长") || header.contains("小时") || header.contains("duration") || header.contains("hours")) {
                columns.putIfAbsent("duration", col);
            }
            if (header.contains("备注") || header.contains("说明") || header.contains("remark")) {
                columns.putIfAbsent("remark", col);
            }
        }
        if (!columns.containsKey("studentNo") || !columns.containsKey("name")) {
            return fallbackColumns();
        }
        columns.putIfAbsent("duration", -1);
        columns.putIfAbsent("remark", -1);
        return columns;
    }

    private Map<String, Integer> fallbackColumns() {
        return Map.of("studentNo", 0, "name", 1, "duration", 2, "remark", 3);
    }

    private String cell(Row row, Integer index, DataFormatter formatter) {
        if (row == null || index == null || index < 0) {
            return "";
        }
        return clean(formatter.formatCellValue(row.getCell(index)));
    }

    private String parseParticipantStatus(String value) {
        if (value == null || value.isBlank()) {
            return "PRESENT";
        }
        String text = value.trim().toUpperCase(Locale.ROOT);
        if (text.equals("PRESENT") || text.contains("出席") || text.contains("已到") || text.contains("签到")
                || text.contains("参加") || text.equals("到")) {
            return "PRESENT";
        }
        if (text.equals("ABSENT") || text.contains("缺席") || text.contains("未到") || text.contains("未参加")) {
            return "ABSENT";
        }
        if (text.equals("LEAVE") || text.contains("请假")) {
            return "LEAVE";
        }
        throw ApiException.badRequest("参与状态只能是出席、缺席或请假");
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

    private BigDecimal parseDuration(String value, BigDecimal defaultDuration) {
        if (value == null || value.isBlank()) {
            return defaultDuration;
        }
        String text = value.trim().replace("小时", "").replace("时", "").replace("h", "").replace("H", "");
        try {
            return normalizedDuration(new BigDecimal(text), defaultDuration, null);
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("培训时长应填写数字");
        }
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

    private String parseSessionStatus(String value) {
        if (value == null || value.isBlank()) {
            return "PLANNED";
        }
        String text = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("PLANNED", "COMPLETED", "CANCELED", "ARCHIVED").contains(text)) {
            throw ApiException.badRequest("培训状态不合法");
        }
        return text;
    }

    private void requireManageTrainings(AuthUser current) {
        if (current.role() != Role.PRESIDENT && current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有会长或管理员可以管理培训");
        }
    }

    private void requireViewTrainings(AuthUser current) {
        if (current.role() != Role.PRESIDENT && current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有会长或管理员可以查看培训管理");
        }
    }

    private void requireExportTrainings(AuthUser current) {
        if (current.role() != Role.PRESIDENT && current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有会长或管理员可以导出培训 Excel");
        }
    }

    private byte[] workbookBytes(WorkbookWriter writer) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writer.write(wb);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw ApiException.badRequest("生成 Excel 失败");
        }
    }

    private CellStyle titleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontName("Microsoft YaHei");
        font.setFontHeightInPoints((short) 14);
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = borderedStyle(wb);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = wb.createFont();
        font.setFontName("Microsoft YaHei");
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle textStyle(Workbook wb) {
        CellStyle style = borderedStyle(wb);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = wb.createFont();
        font.setFontName("Microsoft YaHei");
        style.setFont(font);
        return style;
    }

    private CellStyle borderedStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    private void autosize(Sheet sheet, int columns) {
        sheet.createFreezePane(0, Math.min(3, sheet.getLastRowNum()));
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.min(Math.max(sheet.getColumnWidth(i) + 512, 10 * 256), 28 * 256));
        }
    }

    private String participantStatusText(String status) {
        return switch (status) {
            case "PRESENT" -> "出席";
            case "ABSENT" -> "缺席";
            case "LEAVE" -> "请假";
            default -> status;
        };
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

    private String sourceText(String source) {
        return "IMPORT".equals(source) ? "导入" : "手动";
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        if (value != null) {
            try {
                return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(message);
        }
        return value.trim();
    }

    private String trimToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String cleanFilename(String value) {
        String text = value == null ? "training" : value.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return text.isBlank() ? "training" : text;
    }

    private LocalTime toLocalTime(Time time) {
        return time == null ? null : time.toLocalTime();
    }

    private Time toSqlTime(LocalTime time) {
        return time == null ? null : Time.valueOf(time);
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
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
    public record ParticipantRequest(String studentNo, String name, BigDecimal durationHours, String attendanceStatus, String remark) {
    }

    public record ImportResult(int created, int updated, int skipped, List<String> errors) {
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

    private record ParticipantValues(Long userId, String studentNo, String name, String status, BigDecimal durationHours, String remark, String source) {
    }

    private record UserRef(long id, String studentNo, String name) {
    }

    private interface WorkbookWriter {
        void write(Workbook wb);
    }
}
