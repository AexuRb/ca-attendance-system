package com.ca.attendance.log;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.GlobalExceptionHandler;
import com.ca.attendance.common.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OperationLogQueryServiceTest {
    @AfterEach void clearAuth() { AuthContext.clear(); }
    @Test void removedClearEndpointRejectsDeleteWithoutInvokingService() throws Exception {
        var service = mock(OperationLogQueryService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new OperationLogController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(delete("/api/logs")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("请求方法不支持"));
        verifyNoInteractions(service);
    }
    @Test void administratorCanStillReadTheLogWithoutWrites() {
        AuthContext.set(new AuthUser(1, "90001", "管理员", Role.ADMIN, Instant.now().plusSeconds(600)));
        var jdbc = mock(JdbcTemplate.class);
        var logs = mock(OperationLogService.class);
        var result = new OperationLogQueryService(jdbc, logs).search(null, null, null, null, 1, 20);
        assertEquals(0, result.total());
        assertTrue(result.items().isEmpty());
        assertTrue(mockingDetails(jdbc).getInvocations().stream().allMatch(i -> i.getMethod().getName().startsWith("query")));
        verifyNoInteractions(logs);
    }
    @Test void ministerRemainsUnableToReadLogs() {
        AuthContext.set(new AuthUser(2, "90002", "部长", Role.MINISTER, Instant.now().plusSeconds(600)));
        var jdbc = mock(JdbcTemplate.class);
        var logs = mock(OperationLogService.class);
        assertThrows(ApiException.class, () -> new OperationLogQueryService(jdbc, logs).search(null, null, null, null, 1, 20));
        verifyNoInteractions(jdbc, logs);
    }
}
