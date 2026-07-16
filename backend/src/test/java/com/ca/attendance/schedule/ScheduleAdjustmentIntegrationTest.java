package com.ca.attendance.schedule;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.config.DatabaseMigrator;
import com.ca.attendance.config.SQLiteDataSourceConfiguration;
import com.ca.attendance.config.StoragePaths;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.schedule.application.EffectiveScheduleService;
import com.ca.attendance.schedule.application.ScheduleAdjustmentService;
import com.ca.attendance.schedule.domain.EffectiveScheduleDay;
import com.ca.attendance.schedule.domain.ScheduleAdjustmentType;
import com.ca.attendance.schedule.domain.ScheduleException;
import com.ca.attendance.schedule.domain.ShiftReassignment;
import com.ca.attendance.schedule.infrastructure.ScheduleAdjustmentRepository;
import com.ca.attendance.settings.DutyPeriodService;
import com.ca.attendance.shared.application.AuditLogPort;
import com.ca.attendance.shared.application.CurrentActor;
import com.ca.attendance.term.application.TermWritePolicy;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleAdjustmentIntegrationTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);
    private static final LocalTime FIRST_START = LocalTime.of(14, 0);
    private static final LocalTime FIRST_END = LocalTime.of(16, 0);
    private static final LocalTime SECOND_START = LocalTime.of(16, 0);
    private static final LocalTime SECOND_END = LocalTime.of(18, 0);

    @TempDir
    Path tempDirectory;

    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private MutableActor actor;
    private RecordingAudit audit;
    private ScheduleAdjustmentService adjustments;
    private EffectiveScheduleService effective;
    private long termId;
    private long slotId;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = (HikariDataSource) new SQLiteDataSourceConfiguration()
                .dataSource(new StoragePaths(tempDirectory.toString()));
        new DatabaseMigrator(dataSource).run();
        jdbc = new JdbcTemplate(dataSource);
        long presidentId = insertUser("president", "会长", "PRESIDENT");
        insertUser("m001", "部长甲", "MINISTER");
        insertUser("m002", "部长乙", "MINISTER");
        insertUser("m003", "部长丙", "MINISTER");
        termId = requiredId(jdbc.queryForObject("""
                INSERT INTO academic_terms (
                  term_code, term_name, start_date, end_date, status, created_by, updated_by
                ) VALUES ('2026-autumn', '2026-2027 第一学期', '2026-09-01', '2027-01-31', 'ACTIVE', ?, ?)
                RETURNING id
                """, Long.class, presidentId, presidentId));
        slotId = insertSlot(presidentId);
        jdbc.update("""
                INSERT INTO app_settings (setting_key, setting_value, description, updated_by)
                VALUES ('DUTY_TIME_PERIODS',
                        '[{"sortOrder":0,"startTime":"14:00","endTime":"16:00"},' ||
                        '{"sortOrder":1,"startTime":"16:00","endTime":"18:00"}]',
                        '测试时段', ?)
                """, presidentId);

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        AcademicTermRepository terms = new AcademicTermRepository(jdbc);
        ScheduleAdjustmentRepository repository = new ScheduleAdjustmentRepository(jdbc);
        effective = new EffectiveScheduleService(terms, repository);
        actor = new MutableActor(new CurrentActor.Actor(
                presidentId, "president", "会长", Role.PRESIDENT));
        audit = new RecordingAudit();
        DutyPeriodService periods = new DutyPeriodService(
                jdbc, objectMapper, new OperationLogService(jdbc, objectMapper));
        adjustments = new ScheduleAdjustmentService(
                repository, effective, periods, new TermWritePolicy(terms), actor, audit);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void resolvesPeriodCancellationTemporaryAdditionAndAssigneeOverrideInOrder() {
        assertEquals(List.of("部长甲", "部长乙"), names(effective.day(MONDAY, termId).slots().getFirst()));

        ScheduleException canceled = adjustments.createException(new ScheduleAdjustmentService.ExceptionRequest(
                termId, MONDAY, ScheduleAdjustmentType.PERIOD_CANCELLED, slotId,
                null, null, null, null, "协会临时会议", List.of()
        ));
        assertTrue(effective.day(MONDAY, termId).slots().isEmpty());
        adjustments.deleteException(canceled.id(), new ScheduleAdjustmentService.ReasonRequest("会议取消"));

        adjustments.createException(new ScheduleAdjustmentService.ExceptionRequest(
                termId, MONDAY, ScheduleAdjustmentType.TEMPORARY_ADDITION, null,
                SECOND_START, SECOND_END, "临时值班", "协会办公室", "活动前准备",
                List.of(new ScheduleAdjustmentService.AssigneeRequest("m003"))
        ));
        EffectiveScheduleDay withAddition = effective.day(MONDAY, termId);
        assertEquals(2, withAddition.slots().size());
        assertEquals("TEMPORARY_ADDITION", withAddition.slots().get(1).origin());
        assertEquals(List.of("部长丙"), names(withAddition.slots().get(1)));

        adjustments.createException(new ScheduleAdjustmentService.ExceptionRequest(
                termId, MONDAY, ScheduleAdjustmentType.ASSIGNEE_OVERRIDE, slotId,
                null, null, null, null, "本周统一调整",
                List.of(new ScheduleAdjustmentService.AssigneeRequest("m003"))
        ));
        assertEquals(List.of("部长丙"), names(effective.day(MONDAY, termId).slots().getFirst()));
        assertTrue(audit.actions.contains("CREATE_SCHEDULE_EXCEPTION"));
    }

    @Test
    void directReassignmentReplacesOnlyTheSelectedPersonAndKeepsSnapshots() {
        ShiftReassignment reassignment = adjustments.createReassignment(
                new ScheduleAdjustmentService.ReassignmentRequest(
                        termId, MONDAY, slotId, null, null,
                        "m001", "m003", "部长甲本周请假"
                ));

        EffectiveScheduleDay.EffectiveSlot slot = effective.day(MONDAY, termId).slots().getFirst();
        assertEquals(List.of("部长丙", "部长乙"), names(slot));
        assertTrue(slot.assignees().getFirst().reassigned());
        assertEquals("部长甲", slot.assignees().getFirst().originalName());
        assertEquals("部长甲", reassignment.original().name());
        assertEquals("部长丙", reassignment.replacement().name());

        assertThrows(ApiException.class, () -> adjustments.createReassignment(
                new ScheduleAdjustmentService.ReassignmentRequest(
                        termId, MONDAY, slotId, null, null,
                        "m001", "m002", "重复调班"
                )));
    }

    @Test
    void wholeDayCancellationIsExclusiveAndMinisterCannotManageAdjustments() {
        adjustments.createException(new ScheduleAdjustmentService.ExceptionRequest(
                termId, MONDAY, ScheduleAdjustmentType.DAY_CANCELLED, null,
                null, null, null, null, "法定节假日", List.of()
        ));
        EffectiveScheduleDay day = effective.day(MONDAY, termId);
        assertTrue(day.cancelled());
        assertTrue(day.slots().isEmpty());
        assertThrows(ApiException.class, () -> adjustments.createException(
                new ScheduleAdjustmentService.ExceptionRequest(
                        termId, MONDAY, ScheduleAdjustmentType.TEMPORARY_ADDITION, null,
                        SECOND_START, SECOND_END, "临时值班", null, "冲突测试",
                        List.of(new ScheduleAdjustmentService.AssigneeRequest("m003"))
                )));

        long ministerId = jdbc.queryForObject("SELECT id FROM users WHERE student_no = 'm001'", Long.class);
        actor.set(new CurrentActor.Actor(ministerId, "m001", "部长甲", Role.MINISTER));
        assertThrows(ApiException.class, () -> adjustments.exceptions(termId, MONDAY, MONDAY));
        assertFalse(audit.actions.isEmpty());
    }

    @Test
    void settlingTermIsWritableByPresidentButNotMinister() {
        jdbc.update("UPDATE academic_terms SET status = 'SETTLING' WHERE id = ?", termId);
        adjustments.createException(new ScheduleAdjustmentService.ExceptionRequest(
                termId, MONDAY, ScheduleAdjustmentType.PERIOD_CANCELLED, slotId,
                null, null, null, null, "结算修正", List.of()
        ));

        long ministerId = jdbc.queryForObject("SELECT id FROM users WHERE student_no = 'm001'", Long.class);
        actor.set(new CurrentActor.Actor(ministerId, "m001", "部长甲", Role.MINISTER));
        assertThrows(ApiException.class, () -> adjustments.createException(
                new ScheduleAdjustmentService.ExceptionRequest(
                        termId, MONDAY.plusDays(1), ScheduleAdjustmentType.DAY_CANCELLED, null,
                        null, null, null, null, "无权限", List.of()
                )));
    }

    private long insertSlot(long actorId) {
        long id = requiredId(jdbc.queryForObject("""
                INSERT INTO duty_schedule_slots (
                  term_id, weekday, start_time, end_time, title, location, enabled, status, created_by, updated_by
                ) VALUES (?, 1, '14:00:00', '16:00:00', '部长值班', '协会办公室', 1, 'ACTIVE', ?, ?)
                RETURNING id
                """, Long.class, termId, actorId, actorId));
        jdbc.update("""
                INSERT INTO duty_schedule_assignees (
                  slot_id, user_id, student_no_snapshot, name_snapshot, sort_order
                )
                SELECT ?, id, student_no, name, 0 FROM users WHERE student_no = 'm001'
                """, id);
        jdbc.update("""
                INSERT INTO duty_schedule_assignees (
                  slot_id, user_id, student_no_snapshot, name_snapshot, sort_order
                )
                SELECT ?, id, student_no, name, 1 FROM users WHERE student_no = 'm002'
                """, id);
        return id;
    }

    private long insertUser(String studentNo, String name, String role) {
        return requiredId(jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES (?, ?, 'test-hash', ?, 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name, role));
    }

    private List<String> names(EffectiveScheduleDay.EffectiveSlot slot) {
        return slot.assignees().stream().map(item -> item.name()).toList();
    }

    private long requiredId(Long value) {
        return value == null ? 0 : value;
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

    private static final class RecordingAudit implements AuditLogPort {
        private final List<String> actions = new ArrayList<>();

        @Override
        public void write(String action, String targetType, Long targetId,
                          Object before, Object after, String reason) {
            actions.add(action);
        }
    }
}
