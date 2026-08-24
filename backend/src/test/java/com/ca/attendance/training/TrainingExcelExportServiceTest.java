package com.ca.attendance.training;

import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingExcelExportServiceTest {
    private final TrainingExcelExportService exports = new TrainingExcelExportService();

    @Test
    void generatesGenericAndSessionImportTemplatesWithTheExistingLayout() throws Exception {
        TrainingExcelExportService.ExportDocument generic =
                exports.generateImportTemplate(null, BigDecimal.ZERO);
        TrainingExcelExportService.ExportDocument sessionTemplate =
                exports.generateImportTemplate(session(), new BigDecimal("2.00"));

        assertEquals("培训名单导入模板.xlsx", generic.filename());
        assertEquals("培训名单导入模板_系统_安全培训_2026-08-15.xlsx", sessionTemplate.filename());
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(sessionTemplate.bytes()))) {
            Sheet participants = workbook.getSheet("参与名单");
            assertNotNull(participants);
            assertEquals(4, participants.getRow(0).getLastCellNum());
            assertEquals("学号", participants.getRow(0).getCell(0).getStringCellValue());
            assertEquals("备注", participants.getRow(0).getCell(3).getStringCellValue());
            assertEquals("测试主讲人", participants.getRow(1).getCell(1).getStringCellValue());
            assertEquals(2.0, participants.getRow(1).getCell(2).getNumericCellValue());
            assertEquals("主讲人", participants.getRow(1).getCell(3).getStringCellValue());
            assertEquals("@", participants.getColumnStyle(0).getDataFormatString());
            assertEquals("@", participants.getRow(1).getCell(0).getCellStyle().getDataFormatString());
            assertEquals(IndexedColors.PALE_BLUE.getIndex(),
                    participants.getRow(0).getCell(0).getCellStyle().getFillForegroundColor());
            assertTrue(participants.getPaneInformation().isFreezePane());
            assertEquals(1, participants.getPaneInformation().getHorizontalSplitPosition());
            assertEquals("培训参与名单导入模板",
                    workbook.getSheet("填写说明").getRow(0).getCell(0).getStringCellValue());
        }
    }

    @Test
    void generatesSessionWorkbookWithMetadataAndParticipantRows() throws Exception {
        TrainingParticipantItem participant = new TrainingParticipantItem(
                9L,
                7L,
                3L,
                "20260001",
                "测试成员",
                new BigDecimal("1.50"),
                null,
                "创建人",
                "更新人",
                LocalDateTime.of(2026, 8, 15, 14, 0),
                LocalDateTime.of(2026, 8, 15, 16, 0)
        );

        TrainingExcelExportService.ExportDocument export = exports.generateSession(session(), List.of(participant));

        assertEquals("培训名单_系统_安全培训_2026-08-15.xlsx", export.filename());
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(export.bytes()))) {
            Sheet sheet = workbook.getSheet("培训名单");
            assertNotNull(sheet);
            assertEquals("系统 安全培训", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("测试地点", sheet.getRow(1).getCell(3).getStringCellValue());
            assertEquals("序号", sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("测试成员", sheet.getRow(4).getCell(2).getStringCellValue());
            assertEquals(1.5, sheet.getRow(4).getCell(3).getNumericCellValue());
            assertEquals("-", sheet.getRow(4).getCell(4).getStringCellValue());
            assertTrue(sheet.getPaneInformation().isFreezePane());
            assertEquals(3, sheet.getPaneInformation().getHorizontalSplitPosition());
        }
    }

    @Test
    void generatesSummaryWorkbookWithSessionAndMemberSheets() throws Exception {
        LocalDate day = LocalDate.of(2026, 8, 15);
        List<Map<String, Object>> memberRows = List.of(Map.of(
                "studentNo", "20260001",
                "name", "测试成员",
                "trainingCount", 2,
                "durationHours", new BigDecimal("3.50")
        ));

        TrainingExcelExportService.ExportDocument export =
                exports.generateSummary(List.of(session()), memberRows, day, day);

        assertEquals("培训统计_2026-08-15_2026-08-15.xlsx", export.filename());
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(export.bytes()))) {
            Sheet sessions = workbook.getSheet("培训场次");
            Sheet members = workbook.getSheet("成员统计");
            assertNotNull(sessions);
            assertNotNull(members);
            assertEquals("培训统计 2026-08-15 至 2026-08-15",
                    sessions.getRow(0).getCell(0).getStringCellValue());
            assertEquals("系统 安全培训", sessions.getRow(3).getCell(1).getStringCellValue());
            assertEquals(4.0, sessions.getRow(3).getCell(5).getNumericCellValue());
            assertEquals("20260001", members.getRow(1).getCell(0).getStringCellValue());
            assertEquals(2.0, members.getRow(1).getCell(2).getNumericCellValue());
            assertEquals(3.5, members.getRow(1).getCell(3).getNumericCellValue());
        }
    }

    private TrainingSessionItem session() {
        return new TrainingSessionItem(
                7L,
                "系统 安全培训",
                LocalDate.of(2026, 8, 15),
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                "测试地点",
                "测试主讲人",
                "测试培训",
                "COMPLETED",
                2,
                new BigDecimal("4.00"),
                "创建人",
                "更新人",
                LocalDateTime.of(2026, 8, 15, 13, 0),
                LocalDateTime.of(2026, 8, 15, 16, 0)
        );
    }
}
