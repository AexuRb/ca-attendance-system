package com.ca.attendance.schedule;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.settings.DutyPeriodItem;
import com.ca.attendance.settings.DutyPeriodService;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DutyScheduleImportTemplateTest {
    @Test void templateKeepsConfiguredInputAndFictionalExamplesSeparate() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var periods = mock(DutyPeriodService.class);
        when(periods.listEnabled()).thenReturn(List.of(new DutyPeriodItem(0, "09:00", "10:00", true)));
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(call -> {
            var row = mock(ResultSet.class);
            when(row.getInt("weekday")).thenReturn(1);
            when(row.getString("weekday_name")).thenReturn("星期一");
            return List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0));
        });
        AuthContext.set(new AuthUser(1, "template-test", "示例管理员", Role.ADMIN, Instant.now().plusSeconds(60)));
        try {
            var file = new DutyScheduleImportService(jdbc, mock(OperationLogService.class), periods).exportTemplate();
            try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(file.bytes()))) {
                var input = workbook.getSheetAt(0);
                assertEquals("排班导入", input.getSheetName());
                assertEquals("星期一", input.getRow(1).getCell(0).getStringCellValue());
                assertEquals("09:00-10:00", input.getRow(1).getCell(1).getStringCellValue());
                assertEquals("", input.getRow(1).getCell(2).getStringCellValue());
                assertEquals("@", input.getColumnStyle(2).getDataFormatString());
                assertEquals("示例部长甲", workbook.getSheet("填写示例").getRow(2).getCell(3).getStringCellValue());
                assertEquals("000001", workbook.getSheet("填写示例").getRow(2).getCell(2).getStringCellValue());
            }
            String previewDir = System.getProperty("template.preview.dir");
            if (previewDir != null) Files.write(Path.of(previewDir).resolve("schedules.xlsx"), file.bytes());
        } finally { AuthContext.clear(); }
    }
}
