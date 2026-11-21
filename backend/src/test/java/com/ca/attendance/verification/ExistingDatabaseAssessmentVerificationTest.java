package com.ca.attendance.verification;

import com.ca.attendance.attendance.AttendanceRepository;
import com.ca.attendance.attendance.KioskPresenceService;
import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthInterceptor;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.GlobalExceptionHandler;
import com.ca.attendance.common.Role;
import com.ca.attendance.config.DatabaseMigrator;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.schedule.application.FixedScheduleCalendarService;
import com.ca.attendance.user.AssessmentStatus;
import com.ca.attendance.access.RemoteAccessPolicy;
import com.ca.attendance.user.UserController;
import com.ca.attendance.user.MinisterSkillAssessmentController;
import com.ca.attendance.user.UserRepository;
import com.ca.attendance.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfSystemProperty(named = "verify.original.assessments", matches = "true")
class ExistingDatabaseAssessmentVerificationTest {
    @Test
    void ministerEligibilityAndReversibleSkillRegistrationOnOriginalDatabase() throws Exception {
        Path path = Path.of(System.getProperty("assessment.verification.db"));
        ExistingDatabaseRollbackDataSource database = new ExistingDatabaseRollbackDataSource(path);
        try {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            new DatabaseMigrator(database).run();
            var date = LocalDate.of(2026, 11, 21);
            var clock = Clock.fixed(Instant.parse("2026-11-21T06:00:00Z"), ZoneId.of("Asia/Shanghai"));
            var repository = new UserRepository(jdbc);
            var service = transactionalService(database, jdbc, repository);
            var minister = addAssessmentUser(jdbc, 1, Role.MINISTER, "ACTIVE");
            var eligible = addAssessmentUser(jdbc, 2, Role.MEMBER, "ACTIVE");
            var manual = addAssessmentUser(jdbc, 3, Role.MEMBER, "ACTIVE");
            var rejected = addAssessmentUser(jdbc, 4, Role.MEMBER, "ACTIVE");
            var yesterday = addAssessmentUser(jdbc, 5, Role.MEMBER, "ACTIVE");
            var disabled = addAssessmentUser(jdbc, 6, Role.MEMBER, "DISABLED");
            var ended = addAssessmentUser(jdbc, 7, Role.MEMBER, "ACTIVE");
            insertCheckIn(jdbc, eligible, date, "PUBLIC", "PENDING", false);
            insertCheckIn(jdbc, eligible, date, "PUBLIC", "APPROVED", true);
            insertCheckIn(jdbc, manual, date, "ADMIN_MANUAL", "APPROVED", false);
            insertCheckIn(jdbc, rejected, date, "PUBLIC", "REJECTED", false);
            insertCheckIn(jdbc, yesterday, date.minusDays(1), "PUBLIC", "APPROVED", false);
            insertCheckIn(jdbc, disabled, date, "PUBLIC", "APPROVED", false);
            insertCheckIn(jdbc, ended, date, "PUBLIC", "APPROVED", true);
            insertCheckIn(jdbc, minister, date, "PUBLIC", "AUTO_APPROVED", false);

            var ministerAccount = repository.findSummaryById(minister).orElseThrow();
            AuthContext.set(new AuthUser(minister, ministerAccount.studentNo(), ministerAccount.name(),
                    Role.MINISTER, clock.instant().plusSeconds(60)));
            assertThat(service.todayAssessmentMembers(date)).extracting(UserService.AssessmentMember::id)
                    .containsOnlyOnce(eligible, ended).doesNotContain(manual, rejected, yesterday, disabled, minister);
            var tokens = new TokenService(12);
            var token = tokens.issue(minister, ministerAccount.studentNo(), ministerAccount.name(), Role.MINISTER);
            var presence = new KioskPresenceService(new AttendanceRepository(jdbc), mock(FixedScheduleCalendarService.class), clock);
            var controller = new MinisterSkillAssessmentController(service, presence, clock);
            var mvc = MockMvcBuilders.standaloneSetup(controller, new UserController(service))
                    .addInterceptors(new AuthInterceptor(tokens, repository, new RemoteAccessPolicy(8081)))
                    .setControllerAdvice(new GlobalExceptionHandler()).build();
            mvc.perform(get("/api/skill-assessments/today").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.date").value(date.toString()));
            mvc.perform(get("/api/skill-assessments/today").header("Authorization", "Bearer " + token)
                            .with(request -> { request.setLocalPort(8081); return request; }))
                    .andExpect(status().isForbidden());
            var edit = """
                    {"date":"2026-11-21","disassemblyAssessment":"PASSED","osReinstallAssessment":"PASSED",
                    "previousDisassemblyAssessment":"NOT_PASSED","previousOsReinstallAssessment":"NOT_PASSED"}
                    """;
            mvc.perform(patch("/api/skill-assessments/{id}", manual).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(edit)).andExpect(status().isConflict());
            mvc.perform(patch("/api/skill-assessments/{id}", eligible).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(edit.replace("2026-11-21", "2026-11-20")))
                    .andExpect(status().isConflict());
            mvc.perform(patch("/api/users/{id}/assessments", eligible).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(edit)).andExpect(status().isForbidden());
            mvc.perform(patch("/api/skill-assessments/{id}", eligible).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(edit)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.disassemblyAssessment").value("PASSED"))
                    .andExpect(jsonPath("$.osReinstallAssessment").value("PASSED"))
                    .andExpect(jsonPath("$.phone").doesNotExist());
            mvc.perform(patch("/api/skill-assessments/{id}", eligible).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(edit)).andExpect(status().isConflict());
            mvc.perform(patch("/api/skill-assessments/{id}", eligible).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"date":"2026-11-21","disassemblyAssessment":"NOT_PASSED","osReinstallAssessment":"PASSED",
                                    "previousDisassemblyAssessment":"PASSED","previousOsReinstallAssessment":"PASSED"}
                                    """)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.disassemblyAssessment").value("NOT_PASSED"))
                    .andExpect(jsonPath("$.osReinstallAssessment").value("PASSED"));
            jdbc.update("UPDATE attendance_records SET check_in_status = 'REJECTED' WHERE user_id = ?", eligible);
            mvc.perform(patch("/api/skill-assessments/{id}", eligible).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(edit.replace(
                                    "\"previousOsReinstallAssessment\":\"NOT_PASSED\"",
                                    "\"previousOsReinstallAssessment\":\"PASSED\"")))
                    .andExpect(status().isConflict());
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM operation_logs WHERE operator_user_id = ? AND action_type = 'UPDATE_USER'
                    """, Integer.class, minister)).isEqualTo(2);
            database.rollbackAll();
            assertThat(repository.findSummaryById(eligible)).isEmpty();
        } finally {
            AuthContext.clear();
            database.destroy();
        }
    }

    private static long addAssessmentUser(JdbcTemplate jdbc, int index, Role role, String status) {
        return jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES (?, ?, 'verification-only', ?, ?, 0) RETURNING id
                """, Long.class, "009981226710000" + index, "虚拟考核验证" + index, role.name(), status);
    }

    private static void insertCheckIn(JdbcTemplate jdbc, long user, LocalDate date, String source, String status, boolean ended) {
        jdbc.update("""
                INSERT INTO attendance_records (user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                    check_in_time, check_out_time, check_in_status, check_out_status, source)
                VALUES (?, '0099812267100000', '虚拟考核验证签到', ?, ?, ?, ?, ?, ?, ?)
                """, user, date.toString(), date.getDayOfWeek().getValue(), date + " 10:00:00",
                ended ? date + " 11:00:00" : null, status, ended ? "APPROVED" : "NOT_SUBMITTED", source);
    }

    @Test
    void migrationAppointmentAndAuditRollbackUsingTheOriginalDatabase() throws Exception {
        Path path = Path.of(System.getProperty("assessment.verification.db"));
        ExistingDatabaseRollbackDataSource database = new ExistingDatabaseRollbackDataSource(path);
        try {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            int originalVersion = jdbc.queryForObject("PRAGMA user_version", Integer.class);
            new DatabaseMigrator(database).run();
            assertThat(jdbc.queryForObject("PRAGMA user_version", Integer.class)).isEqualTo(12);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM users WHERE role <> 'MEMBER'
                      AND (disassembly_assessment <> 'PASSED' OR os_reinstall_assessment <> 'PASSED')
                    """, Integer.class)).isZero();
            if (originalVersion == 11) {
                assertThat(jdbc.queryForObject("""
                        SELECT COUNT(*) FROM users WHERE role = 'MEMBER'
                          AND (disassembly_assessment <> 'NOT_PASSED' OR os_reinstall_assessment <> 'NOT_PASSED')
                        """, Integer.class)).isZero();
            }

            UserRepository repository = new UserRepository(jdbc);
            long adminId = jdbc.queryForObject("""
                    SELECT id FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE' AND must_change_password = 0 LIMIT 1
                    """, Long.class);
            var admin = repository.findSummaryById(adminId).orElseThrow();
            AuthContext.set(new AuthUser(admin.id(), admin.studentNo(), admin.name(), Role.ADMIN, Instant.now().plusSeconds(60)));
            UserService users = transactionalService(database, jdbc, repository);
            long memberId = jdbc.queryForObject("""
                    INSERT INTO users (student_no, name, password_hash, role)
                    VALUES ('0099812267000001', '考核验证成员', 'verification-only', 'MEMBER') RETURNING id
                    """, Long.class);
            long ministerId = jdbc.queryForObject("""
                    INSERT INTO users (student_no, name, password_hash, role)
                    VALUES ('0099812267000002', '考核验证部长', 'verification-only', 'MINISTER') RETURNING id
                    """, Long.class);
            assertThat(repository.findSummaryById(ministerId).orElseThrow().disassemblyAssessment())
                    .isEqualTo(AssessmentStatus.PASSED);
            assertThat(repository.findSummaryById(ministerId).orElseThrow().osReinstallAssessment())
                    .isEqualTo(AssessmentStatus.PASSED);

            var initial = repository.findSummaryById(memberId).orElseThrow();
            assertThat(initial.disassemblyAssessment()).isEqualTo(AssessmentStatus.NOT_PASSED);
            assertThat(initial.osReinstallAssessment()).isEqualTo(AssessmentStatus.NOT_PASSED);
            var passDisassembly = new UserService.AssessmentUpdateRequest("PASSED", null, "考核验证");
            jdbc.execute("""
                    CREATE TEMP TRIGGER fail_assessment_audit BEFORE INSERT ON main.operation_logs
                    WHEN NEW.action_type = 'UPDATE_USER'
                    BEGIN SELECT RAISE(ABORT, 'verification audit failure'); END
                    """);
            try {
                assertThatThrownBy(() -> users.updateAssessments(memberId, passDisassembly))
                        .isInstanceOf(DataAccessException.class);
                assertThat(repository.findSummaryById(memberId)).contains(initial);
                assertThatThrownBy(() -> users.update(memberId, new UserService.UpdateUserRequest(
                        "虚拟资料修改", null, null, null, null, null, null, "考核验证", "PASSED", null)))
                        .isInstanceOf(DataAccessException.class);
                assertThat(repository.findSummaryById(memberId)).contains(initial);
            } finally {
                jdbc.execute("DROP TRIGGER temp.fail_assessment_audit");
            }
            var assessed = users.updateAssessments(memberId, passDisassembly);
            assertThat(assessed.disassemblyAssessment()).isEqualTo(AssessmentStatus.PASSED);
            assertThat(assessed.osReinstallAssessment()).isEqualTo(AssessmentStatus.NOT_PASSED);

            for (Role role : new Role[]{Role.MINISTER, Role.PRESIDENT, Role.ADMIN}) {
                var appointed = users.update(memberId, changeRole(role));
                assertThat(appointed.disassemblyAssessment()).isEqualTo(AssessmentStatus.PASSED);
                assertThat(appointed.osReinstallAssessment()).isEqualTo(AssessmentStatus.PASSED);
                assertThatThrownBy(() -> users.updateAssessments(memberId,
                        new UserService.AssessmentUpdateRequest("NOT_PASSED", null, null)))
                        .isInstanceOf(ApiException.class);
                assertThatThrownBy(() -> jdbc.update("UPDATE users SET os_reinstall_assessment = 'NOT_PASSED' WHERE id = ?", memberId))
                        .isInstanceOf(DataAccessException.class);
            }
            var demoted = users.update(memberId, changeRole(Role.MEMBER));
            assertThat(demoted.disassemblyAssessment()).isEqualTo(AssessmentStatus.PASSED);
            assertThat(demoted.osReinstallAssessment()).isEqualTo(AssessmentStatus.PASSED);
            var tokens = new TokenService(12);
            String token = tokens.issue(admin.id(), admin.studentNo(), admin.name(), Role.ADMIN);
            var mvc = MockMvcBuilders.standaloneSetup(new UserController(users))
                    .addInterceptors(new AuthInterceptor(tokens, repository, new RemoteAccessPolicy(8081)))
                    .setControllerAdvice(new GlobalExceptionHandler()).build();
            mvc.perform(patch("/api/users/{id}/assessments", memberId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"osReinstallAssessment":"NOT_PASSED"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.disassemblyAssessment").value("PASSED"))
                    .andExpect(jsonPath("$.osReinstallAssessment").value("NOT_PASSED"));
            mvc.perform(put("/api/users/{id}", memberId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"虚拟考核资料","disassemblyAssessment":"NOT_PASSED"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("虚拟考核资料"))
                    .andExpect(jsonPath("$.disassemblyAssessment").value("NOT_PASSED"))
                    .andExpect(jsonPath("$.osReinstallAssessment").value("NOT_PASSED"));
            mvc.perform(put("/api/users/{id}", ministerId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"disassemblyAssessment":"NOT_PASSED"}
                                    """))
                    .andExpect(status().isBadRequest());

            database.rollbackAll();
            assertThat(jdbc.queryForObject("PRAGMA user_version", Integer.class)).isEqualTo(originalVersion);
            assertThat(repository.findLoginByStudentNo("0099812267000001")).isEmpty();
        } finally {
            AuthContext.clear();
            database.destroy();
        }
    }

    private static UserService transactionalService(ExistingDatabaseRollbackDataSource database, JdbcTemplate jdbc,
                                                    UserRepository repository) {
        var logs = new OperationLogService(jdbc, JsonMapper.builder().build());
        var target = new UserService(repository, jdbc, null, logs, null, new TokenService(12), null);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(new JdbcTransactionManager(database));
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(interceptor);
        return (UserService) proxy.getProxy();
    }

    private static UserService.UpdateUserRequest changeRole(Role role) {
        return new UserService.UpdateUserRequest(null, role.name(), null, null, null, null, null, "考核验证");
    }
}
