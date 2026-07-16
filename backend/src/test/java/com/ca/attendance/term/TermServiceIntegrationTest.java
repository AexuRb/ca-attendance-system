package com.ca.attendance.term;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.config.DatabaseMigrator;
import com.ca.attendance.config.SQLiteDataSourceConfiguration;
import com.ca.attendance.config.StoragePaths;
import com.ca.attendance.shared.application.AuditLogPort;
import com.ca.attendance.shared.application.CurrentActor;
import com.ca.attendance.shared.application.SafetyBackupPort;
import com.ca.attendance.term.application.TermService;
import com.ca.attendance.term.application.TermWritePolicy;
import com.ca.attendance.term.domain.AcademicTerm;
import com.ca.attendance.term.domain.TermStatus;
import com.ca.attendance.term.infrastructure.AcademicTermRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TermServiceIntegrationTest {
    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2027, 1, 31);

    @TempDir
    Path tempDirectory;

    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private MutableActor actor;
    private RecordingBackups backups;
    private RecordingAudit audit;
    private TermService service;
    private AcademicTermRepository repository;
    private long adminId;
    private long presidentId;
    private long memberId;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = (HikariDataSource) new SQLiteDataSourceConfiguration()
                .dataSource(new StoragePaths(tempDirectory.toString()));
        new DatabaseMigrator(dataSource).run();
        jdbc = new JdbcTemplate(dataSource);
        adminId = insertUser("admin", "管理员", "ADMIN");
        presidentId = insertUser("president", "会长", "PRESIDENT");
        memberId = insertUser("20260001", "测试成员", "MEMBER");
        actor = new MutableActor(new CurrentActor.Actor(
                presidentId, "president", "会长", Role.PRESIDENT));
        backups = new RecordingBackups();
        audit = new RecordingAudit();
        repository = new AcademicTermRepository(jdbc);
        service = new TermService(repository, actor, backups, audit,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void sealsReopensAndResealsWithoutOverwritingPreviousSnapshot() {
        AcademicTerm term = createAndActivate();
        seedCompletedBusinessData(term.id());

        service.beginSettling(term.id());
        TermService.SettlementPreview preview = service.preview(term.id());
        assertFalse(preview.preflight().blocked());
        assertEquals(1, preview.summary().get("validAttendanceRecords"));
        assertEquals(1, preview.summary().get("trainingSessions"));
        assertEquals(1, preview.summary().get("completedRepairs"));
        TermService.MemberSettlement member = preview.members().stream()
                .filter(item -> "20260001".equals(item.studentNo()))
                .findFirst().orElseThrow();
        assertEquals(120, member.attendanceMinutes());
        assertEquals(90, member.trainingMinutes());
        assertEquals(210, member.totalMinutes());

        assertThrows(ApiException.class, () -> service.seal(term.id()));
        actor.set(new CurrentActor.Actor(adminId, "admin", "管理员", Role.ADMIN));
        TermService.SealResult first = service.seal(term.id());
        assertEquals(1, first.version());
        assertEquals(TermStatus.SEALED, first.term().status());
        assertEquals(1, backups.reasons.size());
        assertEquals(preview.members().size(), jdbc.queryForObject(
                "SELECT COUNT(*) FROM term_member_settlements WHERE settlement_id = ?",
                Integer.class, first.settlementId()));

        service.reopen(term.id(), new TermService.ReopenRequest("发现一条培训时长需要更正"));
        assertEquals(TermStatus.SETTLING, repository.find(term.id()).orElseThrow().status());
        assertEquals("SUPERSEDED", jdbc.queryForObject(
                "SELECT status FROM term_settlements WHERE id = ?", String.class, first.settlementId()));

        TermService.SealResult second = service.seal(term.id());
        assertEquals(2, second.version());
        assertEquals(2, service.settlements(term.id()).size());
        assertEquals(3, backups.reasons.size());
        assertTrue(audit.actions.contains("SEAL_ACADEMIC_TERM"));
        assertTrue(audit.actions.contains("REOPEN_ACADEMIC_TERM"));
    }

    @Test
    void ongoingRepairBlocksSealingUntilResolved() {
        AcademicTerm term = createAndActivate();
        jdbc.update("""
                INSERT INTO repair_cases (
                  term_id, case_no, agreement_type, owner_name, device_type,
                  fault_description, status, received_at, created_by, updated_by
                ) VALUES (?, 'JXWX-2026-0001', 'PERSONAL_DEVICE', '送修人', '笔记本电脑',
                          '无法开机', 'REPAIRING', ?, ?, ?)
                """, term.id(), LocalDateTime.of(2026, 10, 10, 14, 0), presidentId, presidentId);
        service.beginSettling(term.id());

        TermService.SettlementPreflight preflight = service.preflight(term.id());
        assertTrue(preflight.blocked());
        assertEquals("OPEN_REPAIRS", preflight.issues().getFirst().code());

        actor.set(new CurrentActor.Actor(adminId, "admin", "管理员", Role.ADMIN));
        assertThrows(ApiException.class, () -> service.seal(term.id()));
        assertEquals(0, backups.reasons.size());
        jdbc.update("UPDATE repair_cases SET status = 'CANCELED' WHERE term_id = ?", term.id());
        assertFalse(service.preflight(term.id()).blocked());
        assertEquals(TermStatus.SEALED, service.seal(term.id()).term().status());
    }

    @Test
    void writePolicyStopsPublicAttendanceWithoutActiveTermAndLocksMinistersDuringSettlement() {
        TermWritePolicy policy = new TermWritePolicy(repository);
        assertThrows(ApiException.class, () -> policy.requirePublicAttendanceTerm(START));

        AcademicTerm term = createAndActivate();
        assertEquals(term.id(), policy.requirePublicAttendanceTerm(START.plusDays(1)).id());
        service.beginSettling(term.id());

        assertThrows(ApiException.class,
                () -> policy.requireBusinessWriteTerm(START.plusDays(1), Role.MINISTER));
        assertEquals(term.id(),
                policy.requireBusinessWriteTerm(START.plusDays(1), Role.PRESIDENT).id());
    }

    @Test
    void rejectsOverlappingTerms() {
        service.create(new TermService.TermRequest("2026-autumn", "2026-2027 第一学期", START, END));
        assertThrows(ApiException.class, () -> service.create(new TermService.TermRequest(
                "overlap", "重叠学期", END.minusDays(10), END.plusMonths(3))));
    }

    private AcademicTerm createAndActivate() {
        AcademicTerm draft = service.create(new TermService.TermRequest(
                "2026-autumn", "2026-2027 第一学期", START, END));
        return service.activate(draft.id(), new TermService.ActivateRequest(false, null));
    }

    private void seedCompletedBusinessData(long termId) {
        jdbc.update("""
                INSERT INTO attendance_records (
                  term_id, user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                  is_duty_day, within_duty_period, check_in_time, check_out_time,
                  check_in_status, check_out_status, duration_minutes, valid_hours, effective_status
                ) VALUES (?, ?, '20260001', '测试成员', '2026-10-08', 4, 1, 1,
                          '2026-10-08 14:00:00', '2026-10-08 16:00:00',
                          'AUTO_APPROVED', 'AUTO_APPROVED', 120, 2, 'VALID')
                """, termId, memberId);
        Long trainingId = jdbc.queryForObject("""
                INSERT INTO training_sessions (
                  term_id, title, training_date, start_time, end_time, status, created_by, updated_by
                ) VALUES (?, '装机培训', '2026-10-09', '14:00:00', '15:30:00', 'COMPLETED', ?, ?)
                RETURNING id
                """, Long.class, termId, presidentId, presidentId);
        jdbc.update("""
                INSERT INTO training_participants (
                  session_id, user_id, student_no_snapshot, name_snapshot, attendance_status,
                  duration_hours, source, created_by, updated_by
                ) VALUES (?, ?, '20260001', '测试成员', 'PRESENT', 1.5, 'MANUAL', ?, ?)
                """, trainingId, memberId, presidentId, presidentId);
        jdbc.update("""
                INSERT INTO repair_cases (
                  term_id, case_no, agreement_type, owner_name, device_type,
                  fault_description, status, received_at, completed_at, created_by, updated_by
                ) VALUES (?, 'JXWX-2026-0002', 'PERSONAL_DEVICE', '送修人', '台式机',
                          '系统故障', 'COMPLETED', '2026-10-10 14:00:00',
                          '2026-10-11 14:00:00', ?, ?)
                """, termId, presidentId, presidentId);
    }

    private long insertUser(String studentNo, String name, String role) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES (?, ?, 'test-hash', ?, 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name, role);
        return id == null ? 0 : id;
    }

    private static final class MutableActor implements CurrentActor {
        private Actor actor;

        private MutableActor(Actor actor) {
            this.actor = actor;
        }

        void set(Actor actor) {
            this.actor = actor;
        }

        @Override
        public Actor require() {
            return actor;
        }
    }

    private static final class RecordingBackups implements SafetyBackupPort {
        private final List<String> reasons = new ArrayList<>();

        @Override
        public BackupReceipt create(String reason) {
            reasons.add(reason);
            return new BackupReceipt("backup-" + reasons.size() + ".zip");
        }
    }

    private static final class RecordingAudit implements AuditLogPort {
        private final List<String> actions = new ArrayList<>();

        @Override
        public void write(String action, String targetType, Long targetId,
                          Object before, Object after, String reason) {
            actions.add(action);
        }
    }
}
