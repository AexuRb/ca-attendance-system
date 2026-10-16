package com.ca.attendance.schedule;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.GlobalExceptionHandler;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.settings.DutyPeriodService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Exercise real controller/service authorization without creating or writing any database.
class DutyScheduleReadPermissionTest {
    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        var logs = mock(OperationLogService.class);
        var periods = mock(DutyPeriodService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DutyScheduleController(
                new DutyScheduleService(jdbc, logs, periods),
                new DutyScheduleImportService(jdbc, logs, periods)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void cleanup() { AuthContext.clear(); }

    private void actor(Role role) {
        AuthContext.set(new AuthUser(1, "test", "test", role, Instant.now().plusSeconds(60)));
    }

    @Test
    void ministerCanReadButCannotWriteOrLoadEditingResources() throws Exception {
        actor(Role.MINISTER);
        mvc.perform(get("/api/schedules")).andExpect(status().isOk()).andExpect(content().json("[]"));
        clearInvocations(jdbc);
        mvc.perform(get("/api/schedules/assignee-candidates")).andExpect(status().isForbidden());
        mvc.perform(get("/api/schedules/import-template")).andExpect(status().isForbidden());
        mvc.perform(post("/api/schedules").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/schedules/1").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/schedules/1")).andExpect(status().isForbidden());
        var file = new MockMultipartFile("file", "test.xlsx", "application/octet-stream", new byte[]{1});
        mvc.perform(multipart("/api/schedules/import/preview").file(file)).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/schedules/import").file(file)).andExpect(status().isForbidden());
        verifyNoInteractions(jdbc);
    }

    @Test
    void ordinaryMemberStillCannotReadTheAdministrativeSchedule() throws Exception {
        actor(Role.MEMBER);
        mvc.perform(get("/api/schedules")).andExpect(status().isForbidden());
        verifyNoInteractions(jdbc);
    }
}
