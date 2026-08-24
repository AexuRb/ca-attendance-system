package com.ca.attendance.export;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomExportServicePreviewTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final OperationLogService logs = mock(OperationLogService.class);

    @BeforeEach
    void authenticateAdmin() {
        AuthContext.set(new AuthUser(1, "admin", "管理员", Role.ADMIN, Instant.now().plusSeconds(60)));
    }

    @AfterEach
    void clearAuthentication() {
        AuthContext.clear();
    }

    @Test
    void previewReturnsExactTotalWithoutMaterializingEveryMatchingRow() {
        List<Map<String, Object>> databaseRows = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentNo", "S" + index);
            row.put("__preview_total", 60_000);
            databaseRows.add(row);
        }
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(databaseRows);

        CustomExportService service = new CustomExportService(jdbc, logs);
        CustomExportService.ExportPreview preview = service.preview(new CustomExportService.ExportRequest(
                "members",
                List.of("studentNo"),
                Map.of(),
                ""
        ));

        assertThat(preview.totalRows()).isEqualTo(60_000);
        assertThat(preview.rows()).hasSize(12);
        assertThat(preview.truncated()).isTrue();
        assertThat(preview.rows()).allSatisfy(row -> assertThat(row).containsOnlyKeys("studentNo"));
    }
}
