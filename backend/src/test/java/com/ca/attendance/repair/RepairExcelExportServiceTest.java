package com.ca.attendance.repair;

import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairExcelExportServiceTest {
    @Test
    void generatesTheExistingRepairWorkbookLayoutAndDisplayValues() throws Exception {
        LocalDate day = LocalDate.of(2026, 8, 15);
        RepairCaseItem item = new RepairCaseItem(
                1L,
                "JXWX20260815-0001",
                "PERSONAL_DEVICE",
                "测试成员",
                null,
                null,
                "笔记本电脑",
                "测试品牌",
                "测试型号",
                null,
                "电源适配器",
                "无法开机",
                "已完成系统检查",
                true,
                false,
                true,
                "COMPLETED",
                LocalDateTime.of(2026, 8, 15, 14, 0),
                LocalDateTime.of(2026, 8, 15, 16, 30),
                8L,
                "处理人",
                null,
                "创建人",
                "更新人",
                null,
                LocalDateTime.of(2026, 8, 15, 14, 0),
                LocalDateTime.of(2026, 8, 15, 16, 30),
                null
        );

        RepairExcelExportService.ExportDocument export =
                new RepairExcelExportService().generate(List.of(item), day, day);

        assertEquals("维修事务_2026-08-15_2026-08-15.xlsx", export.filename());
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(export.bytes()))) {
            Sheet sheet = workbook.getSheet("维修事务");
            assertNotNull(sheet);
            assertEquals("维修事务 2026-08-15 至 2026-08-15", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals(18, sheet.getRow(2).getLastCellNum());
            assertEquals("编号", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("备注", sheet.getRow(2).getCell(17).getStringCellValue());
            assertEquals("已完成", sheet.getRow(3).getCell(1).getStringCellValue());
            assertEquals("维修协议", sheet.getRow(3).getCell(2).getStringCellValue());
            assertEquals("2026-08-15 14:00", sheet.getRow(3).getCell(3).getStringCellValue());
            assertEquals("-", sheet.getRow(3).getCell(6).getStringCellValue());
            assertEquals("是", sheet.getRow(3).getCell(13).getStringCellValue());
            assertEquals("否", sheet.getRow(3).getCell(14).getStringCellValue());
            assertEquals("-", sheet.getRow(3).getCell(17).getStringCellValue());
            assertTrue(sheet.getRow(3).getCell(11).getCellStyle().getWrapText());
            assertEquals(IndexedColors.PALE_BLUE.getIndex(),
                    sheet.getRow(2).getCell(0).getCellStyle().getFillForegroundColor());
            assertNotNull(sheet.getPaneInformation());
            assertTrue(sheet.getPaneInformation().isFreezePane());
            assertEquals(3, sheet.getPaneInformation().getHorizontalSplitPosition());
        }
    }
}
