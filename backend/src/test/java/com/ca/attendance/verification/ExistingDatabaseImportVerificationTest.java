package com.ca.attendance.verification;

import com.ca.attendance.auth.*;
import com.ca.attendance.common.*;
import com.ca.attendance.schedule.*;
import com.ca.attendance.training.*;
import com.ca.attendance.user.*;
import com.ca.attendance.export.AttendanceLedgerService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Explicitly enabled only for a user-authorized original database verification. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.main.allow-bean-definition-overriding=true")
@Import(ExistingDatabaseImportVerificationTest.ProtectedDatabase.class)
@EnabledIfSystemProperty(named = "verify.original.imports", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExistingDatabaseImportVerificationTest {
    static final String A = "0098776611223301";
    static final String B = "0098776611223302";
    static final String EXTERNAL = "0098776611223303";
    static final String LONG_ID = "00987766112233001234567890123456";
    static final String[] MEMBER_HEADERS = {"学号", "姓名", "手机号", "学院", "年级", "QQ"};
    static final String[] TRAINING_HEADERS = {"学号", "姓名", "时长", "备注"};
    static final String[] SCHEDULE_HEADERS = {"星期", "值班时段", "学号", "姓名"};
    @Autowired JdbcTemplate jdbc;
    @Autowired ExistingDatabaseRollbackDataSource protectedDatabase;
    @Autowired UserService users;
    @Autowired MemberImportTemplateService memberTemplates;
    @Autowired TrainingService trainings;
    @Autowired DutyScheduleImportService schedules;
    @Autowired DutyScheduleService scheduleService;
    @Autowired TokenService tokens;
    @Autowired AuthInterceptor auth;
    @Autowired AttendanceLedgerService ledger;
    Map<String, String> baseline;
    long admin;

    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", () -> root().toString());
    }
    static Path root() {
        String configured = System.getProperty("import.verification.root");
        if (configured == null) throw new IllegalStateException("An explicit original workspace root is required");
        return Path.of(configured).toAbsolutePath().normalize();
    }
    @TestConfiguration static class ProtectedDatabase {
        @Bean(name = "dataSource", destroyMethod = "destroy")
        ExistingDatabaseRollbackDataSource dataSource() throws Exception {
            return new ExistingDatabaseRollbackDataSource(root().resolve("data/attendance.db"));
        }
    }
    @BeforeEach void prepare() throws Exception {
        baseline = snapshot();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE student_no IN (?, ?, ?, ?)",
                Integer.class, A, B, EXTERNAL, LONG_ID));
        var actor = jdbc.queryForMap("SELECT id, student_no, name FROM users WHERE role='ADMIN' AND status='ACTIVE' LIMIT 1");
        admin = ((Number) actor.get("id")).longValue();
        AuthContext.set(new AuthUser(admin, (String) actor.get("student_no"), (String) actor.get("name"), Role.ADMIN,
                Instant.now().plusSeconds(3600)));
    }
    @AfterEach void restore() throws Exception {
        AuthContext.clear();
        protectedDatabase.rollbackAll();
        assertEquals(baseline, snapshot(), "Every original table, sequence and schema must remain unchanged");
    }

    @Test void blankTemplatesDoNotImportFictionalExamples() throws Exception {
        var file = file(memberTemplates.generate().bytes());
        assertEquals(0, users.previewImport(file).created());
        assertEquals(0, users.importMembers(file).created());
        var schedule = schedules.preview(file(schedules.exportTemplate().bytes()));
        assertEquals(0, schedule.memberCount());
        long session = session();
        assertFalse(trainings.previewParticipants(session, file(trainings.exportSessionImportTemplate(session).bytes())).valid());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM training_participants WHERE session_id=?", Integer.class, session));
        String output = System.getProperty("import.verification.output");
        if (output != null) {
            Path directory = Path.of(output);
            Files.createDirectories(directory);
            Files.write(directory.resolve("invalid-members.xlsx"), workbook(MEMBER_HEADERS, new String[]{A,""}).getBytes());
            Files.write(directory.resolve("invalid-schedules.xlsx"), workbook(SCHEDULE_HEADERS, new String[]{"1","00:01-00:02",A,"导入验证部长甲"}).getBytes());
            Files.write(directory.resolve("invalid-training.xlsx"), workbook(TRAINING_HEADERS, new String[]{A,"导入验证成员甲","两小时"}).getBytes());
            Files.write(directory.resolve("valid-members.xlsx"), workbook(MEMBER_HEADERS,
                    new String[]{A,"导入验证成员甲","00000000000","虚构学院",LocalDate.now().getYear()+"级","000000001"}).getBytes());
            Files.write(directory.resolve("valid-training.xlsx"), workbook(TRAINING_HEADERS,
                    new String[]{A,"导入验证外部人员","1.5小时","校验预览，不确认导入"}).getBytes());
            var ministers=jdbc.queryForList("SELECT student_no,name FROM users WHERE role='MINISTER' AND status='ACTIVE' ORDER BY id LIMIT 1");
            if(!ministers.isEmpty()) {
                String[] group=scheduleGroup(); var minister=ministers.getFirst();
                Files.write(directory.resolve("valid-schedules.xlsx"),workbook(SCHEDULE_HEADERS,
                        new String[]{group[0],group[1],(String)minister.get("student_no"),(String)minister.get("name")}).getBytes());
            }
        }
    }

    @Test void generatedMemberTemplateCreatesAndUpdatesAllFieldsWithoutLosingIdsOrOptionalData() throws Exception {
        MockMultipartFile first;
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(memberTemplates.generate().bytes()))) {
            fill(workbook.getSheetAt(0), 1, A, "导入验证成员甲", "00000000000", "虚构学院", LocalDate.now().getYear()+"级", "000000001");
            fill(workbook.getSheetAt(0), 3000, LONG_ID, "导入验证成员乙", "", "", "", "");
            first = file(bytes(workbook));
        }
        var before = snapshot();
        var preview = users.previewImport(first);
        assertTrue(preview.valid()); assertEquals(2, preview.created()); assertEquals(before, snapshot(), "Preview is read only");
        assertEquals(2, users.importMembers(first, preview.token()).created());
        var created = jdbc.queryForMap("SELECT * FROM users WHERE student_no=?", A);
        assertEquals("00000000000", created.get("phone")); assertEquals("000000001", created.get("qq"));
        assertEquals("虚构学院", created.get("major")); assertEquals("MEMBER", created.get("role"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE student_no=?", Integer.class, LONG_ID));
        var changed = workbook(MEMBER_HEADERS, new String[]{A, "导入验证成员更新", "", "", "", ""});
        var result = users.importMembers(changed, users.previewImport(changed).token());
        assertEquals(0, result.created()); assertEquals(1, result.updated());
        var updated = jdbc.queryForMap("SELECT * FROM users WHERE student_no=?", A);
        assertEquals("导入验证成员更新", updated.get("name"));
        for (String key : List.of("phone", "major", "grade", "qq", "role", "status", "password_hash")) {
            assertEquals(created.get(key), updated.get(key), "Blank optional fields preserve " + key);
        }
        assertEquals(2, auditCount("IMPORT_USERS"));
    }

    @Test void memberInvalidMixedRowsAndDuplicateIdsRejectTheWholeBatch() throws Exception {
        for (var file : List.of(
                workbook(MEMBER_HEADERS, new String[]{A,"导入验证成员甲"}, new String[]{B,""}),
                workbook(MEMBER_HEADERS, new String[]{A,"导入验证成员甲"}, new String[]{A,"导入验证成员乙"}))) {
            var before = snapshot();
            assertFalse(users.previewImport(file).valid());
            assertThrows(ApiException.class, () -> users.importMembers(file));
            assertEquals(before, snapshot());
        }
    }

    @Test void memberStalePreviewRejectsBothFileAndDatabaseChanges() throws Exception {
        user(A, "导入验证成员甲", Role.MEMBER);
        var file = workbook(MEMBER_HEADERS, new String[]{A,"导入验证成员更新"});
        var preview = users.previewImport(file);
        jdbc.update("UPDATE users SET name='导入验证并发更新' WHERE student_no=?", A);
        var before = snapshot();
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> users.importMembers(file, preview.token())).status());
        assertEquals(before, snapshot());
        var current = users.previewImport(file);
        var changed = workbook(MEMBER_HEADERS, new String[]{B,"导入验证其他成员"});
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> users.importMembers(changed, current.token())).status());
        assertEquals(before, snapshot());
    }

    @Test void presidentSkipsAdministratorAndImportsOtherRowsWithWarning() throws Exception {
        long administrator = user(A, "导入验证管理员", Role.ADMIN);
        AuthContext.set(new AuthUser(admin, "verification", "导入验证会长", Role.PRESIDENT, Instant.now().plusSeconds(600)));
        var file = workbook(MEMBER_HEADERS, new String[]{A,"不能修改管理员"}, new String[]{B,"导入验证普通成员"});
        var preview = users.previewImport(file);
        assertTrue(preview.valid()); assertFalse(preview.warnings().isEmpty());
        var result = users.importMembers(file, preview.token());
        assertEquals(1, result.created()); assertEquals(0, result.updated());
        assertEquals("导入验证管理员", jdbc.queryForObject("SELECT name FROM users WHERE id=?", String.class, administrator));
    }

    @Test void trainingGeneratedTemplateImportsMembersAndExternalPeopleAndUpdatesInsteadOfAddingDuration() throws Exception {
        long member = user(A, "导入验证成员甲", Role.MEMBER);
        long session = session();
        MockMultipartFile first;
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(trainings.exportSessionImportTemplate(session).bytes()))) {
            fill(workbook.getSheetAt(0), 1, A, "文件姓名应由系统校正", "1.5小时", "原备注");
            fill(workbook.getSheetAt(0), 2, EXTERNAL, "导入验证外部人员", "", "外部备注");
            first = file(bytes(workbook));
        }
        var before = snapshot();
        var preview = trainings.previewParticipants(session, first);
        assertTrue(preview.valid()); assertEquals(before, snapshot());
        var result = trainings.importParticipants(session, first);
        assertEquals(2, result.created());
        var row = participant(session, A);
        assertEquals(member, ((Number) row.get("user_id")).longValue());
        assertEquals("导入验证成员甲", row.get("name_snapshot"));
        assertEquals(1.5, ((Number) row.get("duration_hours")).doubleValue());
        assertNull(participant(session, EXTERNAL).get("user_id"));
        assertEquals(2.0, ((Number) participant(session, EXTERNAL).get("duration_hours")).doubleValue());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE student_no=?", Integer.class, EXTERNAL));
        var updated = trainings.importParticipants(session, workbook(TRAINING_HEADERS, new String[]{A,"导入验证成员甲","2.5",""}));
        assertEquals(0, updated.created()); assertEquals(1, updated.updated());
        assertEquals(2.5, ((Number) participant(session, A).get("duration_hours")).doubleValue());
        assertNull(participant(session, A).get("remark"));
        assertEquals("外部备注", participant(session, EXTERNAL).get("remark"));
        assertEquals(2, auditCount("IMPORT_TRAINING_PARTICIPANTS"));
    }

    @Test void trainingInvalidDurationDuplicateAndAmbiguousNamesRejectWholeBatch() throws Exception {
        user(A, "导入验证同名成员", Role.MEMBER); user(B, "导入验证同名成员", Role.MEMBER);
        long session = session();
        for (var file : List.of(
                workbook(TRAINING_HEADERS, new String[]{A,"导入验证成员甲","1h"}, new String[]{B,"导入验证成员乙","两小时"}),
                workbook(TRAINING_HEADERS, new String[]{A,"导入验证成员甲","1"}, new String[]{A,"导入验证成员甲","2"}),
                workbook(TRAINING_HEADERS, new String[]{"","导入验证同名成员","1"}))) {
            var before = snapshot();
            assertFalse(trainings.previewParticipants(session, file).valid());
            assertThrows(ApiException.class, () -> trainings.importParticipants(session, file));
            assertEquals(before, snapshot());
        }
    }

    @Test void schedulesReplaceOnlyTheSelectedGroupAndUseMultipleMinisters() throws Exception {
        user(A, "导入验证部长甲", Role.MINISTER); user(B, "导入验证部长乙", Role.MINISTER);
        String[] group = scheduleGroup();
        var otherSlots = jdbc.queryForList("SELECT * FROM duty_schedule_slots WHERE weekday != ? OR substr(start_time,1,5) != ? OR substr(end_time,1,5) != ? ORDER BY id",
                Integer.parseInt(group[0]), group[1].substring(0,5), group[1].substring(6));
        var file = workbook(SCHEDULE_HEADERS, new String[]{group[0],group[1],A,"导入验证部长甲"}, new String[]{group[0],group[1],B,"导入验证部长乙"});
        var before = snapshot();
        assertTrue(schedules.preview(file).valid()); assertEquals(before, snapshot());
        var imported = schedules.importSchedules(file);
        assertEquals(1, imported.replacedGroups()); assertEquals(2, imported.assignedMembers());
        long slot = imported.groups().getFirst().slotId();
        assertEquals(List.of(A,B), jdbc.queryForList("SELECT student_no_snapshot FROM duty_schedule_assignees WHERE slot_id=? ORDER BY sort_order", String.class, slot));
        var updated = schedules.importSchedules(workbook(SCHEDULE_HEADERS, new String[]{group[0],group[1],B,"导入验证部长乙"}));
        assertEquals(1, updated.updatedGroups());
        assertEquals(List.of(B), jdbc.queryForList("SELECT student_no_snapshot FROM duty_schedule_assignees WHERE slot_id=?", String.class, slot));
        assertEquals(otherSlots, jdbc.queryForList("SELECT * FROM duty_schedule_slots WHERE weekday != ? OR substr(start_time,1,5) != ? OR substr(end_time,1,5) != ? ORDER BY id",
                Integer.parseInt(group[0]), group[1].substring(0,5), group[1].substring(6)));
        assertEquals(2, auditCount("IMPORT_DUTY_SCHEDULES"));
    }

    @Test void scheduleDuplicateMemberNonMinisterAndDisabledSettingsAreRejectedWithoutWrites() throws Exception {
        user(A, "导入验证部长甲", Role.MINISTER); user(B, "导入验证普通成员", Role.MEMBER);
        String[] group = scheduleGroup();
        for (var file : List.of(
                workbook(SCHEDULE_HEADERS, new String[]{group[0],group[1],A,"导入验证部长甲"}, new String[]{group[0],group[1],A,"导入验证部长甲"}),
                workbook(SCHEDULE_HEADERS, new String[]{group[0],group[1],A,"导入验证部长甲"}, new String[]{group[0],group[1],B,"导入验证普通成员"}),
                workbook(SCHEDULE_HEADERS, new String[]{group[0],"00:01-00:02",A,"导入验证部长甲"}))) {
            var before = snapshot();
            assertFalse(schedules.preview(file).valid());
            assertThrows(ApiException.class, () -> schedules.importSchedules(file));
            assertEquals(before, snapshot());
        }
        jdbc.update("UPDATE duty_weekday_settings SET enabled=0 WHERE weekday=?", Integer.parseInt(group[0]));
        var file = workbook(SCHEDULE_HEADERS, new String[]{group[0],group[1],A,"导入验证部长甲"});
        assertFalse(schedules.preview(file).valid());
        var before = snapshot(); assertThrows(ApiException.class, () -> schedules.importSchedules(file)); assertEquals(before, snapshot());
    }

    @ParameterizedTest @ValueSource(strings={"IMPORT_USERS","IMPORT_TRAINING_PARTICIPANTS","IMPORT_DUTY_SCHEDULES"})
    void eachImportRollsBackItsOwnWritesWhenAuditFails(String action) throws Exception {
        user(A, "导入验证部长甲", Role.MINISTER);
        long session = session(); String[] group = scheduleGroup();
        jdbc.execute("CREATE TEMP TRIGGER verify_import_audit BEFORE INSERT ON main.operation_logs WHEN NEW.action_type='"+action+"' BEGIN SELECT RAISE(ABORT, 'verification audit failure'); END");
        try {
            var before = snapshot();
            assertThrows(DataAccessException.class, () -> {
                switch (action) {
                    case "IMPORT_USERS" -> users.importMembers(workbook(MEMBER_HEADERS, new String[]{A,"导入验证已修改"},new String[]{B,"导入验证新增"}));
                    case "IMPORT_TRAINING_PARTICIPANTS" -> trainings.importParticipants(session, workbook(TRAINING_HEADERS, new String[]{A,"导入验证部长甲","1"},new String[]{B,"导入验证外部","2"}));
                    default -> schedules.importSchedules(workbook(SCHEDULE_HEADERS, new String[]{group[0],group[1],A,"导入验证部长甲"}));
                }
            });
            assertEquals(before, snapshot(), "Service rollback must preserve fixtures as well as all original rows");
        } finally { jdbc.execute("DROP TRIGGER temp.verify_import_audit"); }
    }

    @Test void allImportsRejectOversizedCorruptAndOverRowLimitFiles() throws Exception {
        long session = session(); String[] group = scheduleGroup();
        var large = file(new byte[5*1024*1024+1]); var corrupt = file("not an xlsx".getBytes(StandardCharsets.UTF_8));
        for (var file : List.of(large,corrupt)) {
            var before = snapshot();
            assertThrows(ApiException.class, () -> users.importMembers(file));
            assertThrows(ApiException.class, () -> trainings.importParticipants(session,file));
            assertThrows(ApiException.class, () -> schedules.importSchedules(file));
            assertEquals(before,snapshot());
        }
        for (int kind=0; kind<3; kind++) {
            var headers = kind==0?MEMBER_HEADERS:kind==1?TRAINING_HEADERS:SCHEDULE_HEADERS;
            try(var workbook=new XSSFWorkbook()) {
                var sheet=workbook.createSheet(); fill(sheet,0,headers);
                fill(sheet,kind==2?1001:3001, kind==2?new String[]{group[0],group[1],A,"导入验证部长甲"}:new String[]{A,"导入验证成员甲"});
                var file=file(bytes(workbook)); var before=snapshot();
                int selected=kind;
                assertThrows(ApiException.class,()-> { if(selected==0) users.importMembers(file); else if(selected==1) trainings.importParticipants(session,file); else schedules.importSchedules(file); });
                assertEquals(before,snapshot());
            }
        }
    }

    @ParameterizedTest @EnumSource(Role.class)
    void authenticatedMultipartApisEnforceRolesForPreviewAndImport(Role role) throws Exception {
        long actor = user(EXTERNAL, "导入验证操作员", role);
        user(A, "导入验证部长甲", Role.MINISTER);
        long session = session(); String[] group = scheduleGroup();
        String token = tokens.issue(actor,EXTERNAL,"导入验证操作员",role);
        MockMvc mvc = mvc();
        var paths=List.of("/api/users/import", "/api/schedules/import", "/api/trainings/"+session+"/participants/import");
        var files=List.of(workbook(MEMBER_HEADERS,new String[]{B,"导入验证成员乙"}),
                workbook(SCHEDULE_HEADERS,new String[]{group[0],group[1],A,"导入验证部长甲"}),
                workbook(TRAINING_HEADERS,new String[]{A,"导入验证部长甲","1"}));
        boolean allowed=role==Role.ADMIN||role==Role.PRESIDENT;
        for(int i=0;i<paths.size();i++) for(String suffix:List.of("/preview","")) {
            mvc.perform(multipart(paths.get(i)+suffix).file(files.get(i)).header("Authorization","Bearer "+token))
                    .andExpect(allowed?status().isOk():status().isForbidden());
        }
    }

    @Test void unauthenticatedMultipartApisRejectBeforeWriting() throws Exception {
        long session=session(); var before=snapshot(); var mvc=mvc();
        for(String path:List.of("/api/users/import","/api/schedules/import","/api/trainings/"+session+"/participants/import"))
            for(String suffix:List.of("/preview","")) mvc.perform(multipart(path+suffix).file(file(new byte[]{1}))).andExpect(status().isUnauthorized());
        assertEquals(before,snapshot());
    }

    @Test void attendanceLedgerReadsOriginalDatabaseAndAuditsDownloadOnly() throws Exception {
        var request=new AttendanceLedgerService.Request("monthly","2026-10-01","2026-10-31","");
        var before=snapshot();var preview=ledger.preview(request);assertEquals(before,snapshot());
        var file=ledger.export(new AttendanceLedgerService.Request(request.period(),request.from(),request.to(),preview.revision()));
        assertEquals(1,auditCount("EXPORT_DATA"));
        try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(file.bytes()))){assertEquals("值班记录",workbook.getSheetAt(0).getSheetName());assertEquals(2,workbook.getSheetAt(0).getPaneInformation().getVerticalSplitPosition());}
    }
    @Test void attendanceLedgerAuditFailureRollsBackOriginalDatabase() throws Exception {
        var request=new AttendanceLedgerService.Request("monthly","2026-10-01","2026-10-31","");var preview=ledger.preview(request);
        jdbc.execute("CREATE TEMP TRIGGER verify_ledger_audit BEFORE INSERT ON main.operation_logs WHEN NEW.action_type='EXPORT_DATA' BEGIN SELECT RAISE(ABORT, 'verification audit failure'); END");
        try {var before=snapshot();assertThrows(DataAccessException.class,()->ledger.export(new AttendanceLedgerService.Request(request.period(),request.from(),request.to(),preview.revision())));assertEquals(before,snapshot());}
        finally {jdbc.execute("DROP TRIGGER temp.verify_ledger_audit");}
    }

    MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new UserController(users),new DutyScheduleController(scheduleService,schedules),new TrainingController(trainings))
                .addInterceptors(auth).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    long user(String number,String name,Role role) {
        return jdbc.queryForObject("INSERT INTO users(student_no,name,password_hash,role,status,must_change_password) VALUES(?,?,'verification-only',?,'ACTIVE',0) RETURNING id",Long.class,number,name,role.name());
    }
    long session() {
        return jdbc.queryForObject("INSERT INTO training_sessions(title,training_date,start_time,end_time,status,speaker) VALUES('导入事务验证','2026-10-18','14:00','16:00','PLANNED','虚构主讲人') RETURNING id",Long.class);
    }
    String[] scheduleGroup() throws Exception {
        try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(schedules.exportTemplate().bytes()))) {
            var row=workbook.getSheetAt(0).getRow(1); var fmt=new DataFormatter();
            String weekday=fmt.formatCellValue(row.getCell(0));
            int index=List.of("星期一","星期二","星期三","星期四","星期五","星期六","星期日").indexOf(weekday);
            assertTrue(index>=0,"Template must use a configured weekday");
            return new String[]{String.valueOf(index+1),fmt.formatCellValue(row.getCell(1))};
        }
    }
    Map<String,Object> participant(long session,String number) {
        return jdbc.queryForMap("SELECT * FROM training_participants WHERE session_id=? AND student_no_snapshot=?",session,number);
    }
    int auditCount(String action) { return jdbc.queryForObject("SELECT COUNT(*) FROM operation_logs WHERE action_type=?",Integer.class,action)-originalAuditCount(action); }
    int originalAuditCount(String action) {
        // Existing audit rows are identified by their original sequence, not their contents.
        return jdbc.queryForObject("SELECT COUNT(*) FROM operation_logs WHERE action_type=? AND id<=?",Integer.class,action,originalLogId);
    }
    long originalLogId;
    Map<String,String> snapshot() throws Exception {
        var result=new TreeMap<String,String>();
        for(String table:jdbc.queryForList("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name",String.class)) {
            if(!table.matches("[A-Za-z_][A-Za-z_0-9]*")) throw new IllegalStateException("Unexpected table identifier");
            var rows=jdbc.queryForList("SELECT * FROM \""+table+"\"").stream().map(Map::toString).sorted().toList();
            result.put(table,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rows.toString().getBytes(StandardCharsets.UTF_8))));
        }
        result.put("schema",jdbc.queryForList("SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name").toString());
        if(baseline==null) originalLogId=jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM operation_logs",Long.class);
        return result;
    }
    static MockMultipartFile workbook(String[] headers,String[]... rows) throws Exception {
        try(var workbook=new XSSFWorkbook()) { var sheet=workbook.createSheet(); fill(sheet,0,headers); for(int i=0;i<rows.length;i++) fill(sheet,i+1,rows[i]); return file(bytes(workbook)); }
    }
    static void fill(Sheet sheet,int index,String... values) {
        var row=sheet.getRow(index); if(row==null) row=sheet.createRow(index);
        for(int c=0;c<values.length;c++) { var cell=row.getCell(c); if(cell==null) cell=row.createCell(c); else cell.setBlank(); cell.setCellValue(values[c]); }
    }
    static byte[] bytes(Workbook workbook) throws Exception { try(var out=new ByteArrayOutputStream()) { workbook.write(out); return out.toByteArray(); } }
    static MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file","verification.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes); }
}
