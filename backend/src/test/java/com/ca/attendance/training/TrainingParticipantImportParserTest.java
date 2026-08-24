package com.ca.attendance.training;

import com.ca.attendance.common.ApiException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingParticipantImportParserTest {
    private final TrainingParticipantImportParser parser = new TrainingParticipantImportParser();

    @Test
    void parsesReorderedHeadersAndNormalizesValuesWithoutDatabaseAccess() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("培训名单");
            sheet.createRow(0).createCell(0).setCellValue("培训参与名单导入模板");
            Row header = sheet.createRow(2);
            header.createCell(0).setCellValue("备注说明");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("培训时长（小时）");
            header.createCell(3).setCellValue("学号");
            Row data = sheet.createRow(3);
            data.createCell(0).setCellValue("  主讲人  ");
            data.createCell(1).setCellValue("  测试成员  ");
            data.createCell(2).setCellValue("1.5小时");
            data.createCell(3).setCellValue(" 9900 0001 ");

            TrainingParticipantImportParser.ParseResult result = parser.parse(sheet, new BigDecimal("2"));

            assertEquals(0, result.skipped());
            assertTrue(result.errors().isEmpty());
            assertEquals(1, result.rows().size());
            TrainingParticipantImportParser.ParsedRow row = result.rows().getFirst();
            assertEquals(4, row.rowNumber());
            assertEquals("99000001", row.studentNo());
            assertEquals("测试成员", row.name());
            assertEquals(new BigDecimal("1.50"), row.durationHours());
            assertEquals("主讲人", row.remark());
        }
    }

    @Test
    void usesFallbackColumnsAndDefaultDurationWhenWorkbookHasNoHeader() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("培训名单");
            Row data = sheet.createRow(0);
            data.createCell(0).setCellValue("99000002");
            data.createCell(1).setCellValue("无表头成员");

            TrainingParticipantImportParser.ParseResult result = parser.parse(sheet, new BigDecimal("2"));

            assertTrue(result.errors().isEmpty());
            assertEquals(1, result.rows().size());
            assertEquals(new BigDecimal("2.00"), result.rows().getFirst().durationHours());
            assertNull(result.rows().getFirst().remark());
        }
    }

    @Test
    void preservesLongNumericAndFormulaStudentNumbersWithoutScientificNotation() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("培训名单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");

            Row numeric = sheet.createRow(1);
            numeric.createCell(0).setCellValue(202301012347d);
            numeric.createCell(1).setCellValue("数值学号成员");

            Row formula = sheet.createRow(2);
            formula.createCell(0).setCellFormula("202301012345+3");
            formula.createCell(1).setCellValue("公式学号成员");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();

            TrainingParticipantImportParser.ParseResult result = parser.parse(sheet, new BigDecimal("2"));

            assertTrue(result.errors().isEmpty());
            assertEquals(2, result.rows().size());
            assertEquals("202301012347", result.rows().get(0).studentNo());
            assertEquals("202301012348", result.rows().get(1).studentNo());
        }
    }

    @Test
    void collectsRowFormatErrorsAndKeepsValidRowsForCallerDecision() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("培训名单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("时长");
            row(sheet, 1, "99000003", "有效成员", "2");
            row(sheet, 2, "99000004", "", "2");
            row(sheet, 3, "99000003", "重复成员", "2");
            row(sheet, 4, "invalid", "错误学号", "2");
            row(sheet, 5, "99000005", "错误时长", "两小时");
            row(sheet, 6, "99000006", "负数时长", "-1");

            TrainingParticipantImportParser.ParseResult result = parser.parse(sheet, BigDecimal.ZERO);

            assertEquals(5, result.skipped());
            assertEquals(1, result.rows().size());
            assertEquals(5, result.errors().size());
            assertTrue(result.errors().get(0).contains("第 3 行：缺少姓名"));
            assertTrue(result.errors().get(1).contains("第 4 行：名单在本次文件中重复"));
            assertTrue(result.errors().get(2).contains("第 5 行：学号格式不正确"));
            assertTrue(result.errors().get(3).contains("第 6 行：培训时长应填写数字"));
            assertTrue(result.errors().get(4).contains("第 7 行：培训时长不能为负数"));
        }
    }

    @Test
    void rejectsDurationAboveSupportedRange() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("培训名单");
            Row data = sheet.createRow(0);
            data.createCell(0).setCellValue("99000007");
            data.createCell(1).setCellValue("超长培训");
            data.createCell(2).setCellValue("1000");

            TrainingParticipantImportParser.ParseResult result = parser.parse(sheet, BigDecimal.ZERO);

            assertEquals(1, result.skipped());
            assertTrue(result.errors().getFirst().contains("培训时长不能超过 999.99 小时"));
        }
    }

    @Test
    void acceptsOnlyNumericDurationWithAnOptionalSuffixUnit() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("培训名单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("时长");
            row(sheet, 1, "99000008", "有效小时单位", "1.5 H");
            row(sheet, 2, "99000009", "有效中文单位", "2时");
            row(sheet, 3, "99000010", "单位位于中间", "1H30");
            row(sheet, 4, "99000011", "不支持的单位", "2课时");

            TrainingParticipantImportParser.ParseResult result = parser.parse(sheet, BigDecimal.ZERO);

            assertEquals(2, result.rows().size());
            assertEquals(new BigDecimal("1.50"), result.rows().get(0).durationHours());
            assertEquals(new BigDecimal("2.00"), result.rows().get(1).durationHours());
            assertEquals(2, result.skipped());
            assertTrue(result.errors().get(0).contains("数字，可选单位"));
            assertTrue(result.errors().get(1).contains("数字，可选单位"));
        }
    }

    @Test
    void rejectsWorkbooksWithMoreThanThreeThousandDataRows() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("培训名单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            for (int index = 1; index <= 3001; index++) {
                row(sheet, index, String.valueOf(99000000L + index), "成员" + index, "2");
            }

            ApiException exception = assertThrows(
                    ApiException.class,
                    () -> parser.parse(sheet, BigDecimal.ZERO)
            );

            assertTrue(exception.getMessage().contains("超过 3000 行"));
        }
    }

    private void row(Sheet sheet, int index, String studentNo, String name, String duration) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(studentNo);
        row.createCell(1).setCellValue(name);
        row.createCell(2).setCellValue(duration);
    }
}
