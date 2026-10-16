package com.ca.attendance.training;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainingImportPreviewTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final OperationLogService logs = mock(OperationLogService.class);
    final TrainingQueryService queries = mock(TrainingQueryService.class);
    final TrainingService service = new TrainingService(jdbc, logs, queries, mock(TrainingExcelExportService.class), new TrainingParticipantImportParser());
    @AfterEach void clearAuth() { AuthContext.clear(); }
    void setup() {
        AuthContext.set(new AuthUser(1, "90000", "测试管理", Role.ADMIN, Instant.now().plusSeconds(600)));
        when(queries.findSession(4)).thenReturn(Optional.of(new TrainingSessionItem(4, "培训", LocalDate.of(2026,10,15), LocalTime.of(14,0), LocalTime.of(16,0), null, null, null, "ACTIVE", 0, BigDecimal.ZERO, null, null, null, null)));
    }
    @Test void previewsNormalizedRowsWithoutWritesOrAudit() throws Exception {
        setup();
        var result = service.previewParticipants(4, file("90001", "测试成员", ""));
        assertTrue(result.valid()); assertEquals(1, result.sourceRows()); assertEquals(2, result.rows().getFirst().row());
        assertEquals(new BigDecimal("2.00"), result.rows().getFirst().durationHours());
        verifyNoInteractions(logs);
        assertTrue(mockingDetails(jdbc).getInvocations().stream().allMatch(i -> i.getMethod().getName().equals("query")));
    }
    @Test void reportsNameResolutionFailureBeforeConfirmation() throws Exception {
        setup(); var result = service.previewParticipants(4, file("", "无法唯一匹配的名字", "1h"));
        assertFalse(result.valid()); assertEquals(1, result.errorCount()); assertTrue(result.errors().getFirst().contains("姓名未能唯一匹配")); verifyNoInteractions(logs);
    }
    @Test void parserErrorsBlockWholeFileAndKeepExactRowNumber() throws Exception {
        setup(); var result = service.previewParticipants(4, file("90001", "测试成员", "两小时"));
        assertFalse(result.valid()); assertEquals(1, result.sourceRows()); assertTrue(result.errors().getFirst().contains("第 2 行")); verifyNoInteractions(jdbc, logs);
    }
    @Test void emptyFileIsNotImportable() throws Exception {
        setup(); var result = service.previewParticipants(4, file("", "", "")); assertFalse(result.valid()); assertEquals(0, result.sourceRows()); verifyNoInteractions(jdbc, logs);
    }
    @Test void ministerIsRejectedBeforeAnyDataAccess() throws Exception {
        AuthContext.set(new AuthUser(2, "90002", "测试部长", Role.MINISTER, Instant.now().plusSeconds(600)));
        assertThrows(ApiException.class, () -> service.previewParticipants(4, file("90001", "测试成员", "1h"))); verifyNoInteractions(jdbc, queries, logs);
    }
    @Test void actualImportUsesTheSameValidationBeforeWriting() throws Exception {
        setup();
        assertThrows(ApiException.class, () -> service.importParticipants(4, file("90001", "测试成员", "两小时")));
        verifyNoInteractions(jdbc, logs);
    }
    MockMultipartFile file(String studentNo, String name, String duration) throws Exception {
        try(var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("名单"); var header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号"); header.createCell(1).setCellValue("姓名"); header.createCell(2).setCellValue("时长");
            var row = sheet.createRow(1); row.createCell(0).setCellValue(studentNo); row.createCell(1).setCellValue(name); row.createCell(2).setCellValue(duration);
            workbook.write(output); return new MockMultipartFile("file", "名单.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
