package com.ca.attendance.user;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ExcelCellTextReader;
import com.ca.attendance.common.GlobalExceptionHandler;
import com.ca.attendance.common.Role;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Template generation and endpoint authorization without opening a database.
class MemberImportTemplateServiceTest {
    private final MemberImportTemplateService templates = new MemberImportTemplateService();

    @BeforeEach void administrator() { actor(Role.ADMIN); }
    @AfterEach void cleanup() { AuthContext.clear(); }

    private void actor(Role role) {
        AuthContext.set(new AuthUser(1, "template-test", "示例管理员", role, Instant.now().plusSeconds(60)));
    }

    @Test void blankInputAndFictionalExamplesRemainSeparate() throws Exception {
        var file = templates.generate();
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(file.bytes()))) {
            var data = workbook.getSheetAt(0);
            var reader = new ExcelCellTextReader(workbook);
            assertEquals("成员导入模板", data.getSheetName());
            assertEquals(3000, data.getLastRowNum());
            assertTrue(data.getPaneInformation().isFreezePane());
            for (int row = 1; row <= 3000; row++) for (int col = 0; col < 6; col++) {
                assertEquals("", reader.read(data.getRow(row), col));
                assertEquals("@", data.getRow(row).getCell(col).getCellStyle().getDataFormatString());
            }
            var validation = data.getDataValidations().getFirst();
            assertEquals("E2:E3001", validation.getRegions().getCellRangeAddresses()[0].formatAsString());
            assertTrue(validation.getEmptyCellAllowed());
            assertFalse(validation.getShowErrorBox());
            assertTrue(workbook.isSheetHidden(workbook.getSheetIndex("年级选项")));
            var examples = workbook.getSheet("填写示例");
            assertEquals("000001", reader.read(examples.getRow(2), 0));
            assertEquals("00123456789012345678901234567890", reader.read(examples.getRow(3), 0));
            assertEquals("示例成员甲", reader.read(examples.getRow(2), 1));
            assertEquals("000000001", reader.read(examples.getRow(2), 5));
            assertEquals("'年级选项'!$A$1:$A$33", workbook.getName("MemberImportGrades").getRefersToFormula());
        }
        String previewDir = System.getProperty("template.preview.dir");
        if (previewDir != null) Files.write(Path.of(previewDir).resolve("members.xlsx"), file.bytes());
    }

    @Test void nextYearUsesNextYearsGradeRange() throws Exception {
        for (int year : new int[]{LocalDate.now().getYear(), LocalDate.now().getYear() + 1}) {
            try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(templates.generate(year).bytes()))) {
                var grades = workbook.getSheet("年级选项");
                assertEquals((year - 30) + "级", grades.getRow(0).getCell(0).getStringCellValue());
                assertEquals((year + 2) + "级", grades.getRow(32).getCell(0).getStringCellValue());
            }
        }
    }

    @ParameterizedTest @EnumSource(Role.class)
    void downloadUsesMemberManagementPermissions(Role role) throws Exception {
        actor(role);
        var mvc = MockMvcBuilders.standaloneSetup(new MemberImportTemplateController(templates))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var result = mvc.perform(get("/api/users/import-template"));
        if (role == Role.ADMIN || role == Role.PRESIDENT) result.andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().exists("Content-Disposition"));
        else result.andExpect(status().isForbidden());
    }

    @Test void unauthenticatedDownloadIsRejected() throws Exception {
        AuthContext.clear();
        MockMvcBuilders.standaloneSetup(new MemberImportTemplateController(templates))
                .setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/api/users/import-template")).andExpect(status().isUnauthorized());
    }
}
