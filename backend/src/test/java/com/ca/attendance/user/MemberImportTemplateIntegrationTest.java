package com.ca.attendance.user;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ExcelCellTextReader;
import com.ca.attendance.common.ExcelImportPolicy;
import com.ca.attendance.common.Role;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MemberImportTemplateIntegrationTest {
    private static final Path STORAGE_ROOT = temporaryRoot();
    private static final Path TEMPLATE = Path.of("../frontend/public/templates/member-import-template.xlsx");
    private static final String LONG_STUDENT_NO = "00" + "1234567890".repeat(3);

    @Autowired private UserService users;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM operation_logs");
        jdbc.update("DELETE FROM users");
        long id = jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('template-admin', '模板测试管理员', 'test-hash', 'ADMIN', 'ACTIVE', 0)
                RETURNING id
                """, Long.class);
        AuthContext.set(new AuthUser(id, "template-admin", "模板测试管理员", Role.ADMIN,
                Instant.now().plusSeconds(3600)));
    }

    @AfterEach
    void clearAuthentication() {
        AuthContext.clear();
    }

    @Test
    void templateCopiesKeepTheMachineReadableBlankInputContract() throws Exception {
        byte[] bytes = Files.readAllBytes(TEMPLATE);
        assertArrayEquals(bytes, Files.readAllBytes(Path.of("../docs/成员批量导入模板.xlsx")));
        assertArrayEquals(bytes, Files.readAllBytes(Path.of("src/main/resources/static/templates/member-import-template.xlsx")));
        try (var input = Files.newInputStream(TEMPLATE); var workbook = WorkbookFactory.create(input)) {
            var sheet = workbook.getSheetAt(0);
            var reader = new ExcelCellTextReader(workbook);
            assertEquals("成员导入模板", sheet.getSheetName());
            assertEquals(List.of("学号", "姓名", "手机号", "学院", "年级", "QQ"),
                    java.util.stream.IntStream.range(0, 6).mapToObj(c -> reader.read(sheet.getRow(0), c)).toList());
            assertEquals(3000, sheet.getLastRowNum());
            assertDoesNotThrow(() -> ExcelImportPolicy.validateRowCount(sheet, 1, "成员"));
            assertTrue(sheet.getPaneInformation().isFreezePane());
            assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition());
            for (int row = 1; row <= 3000; row++) {
                for (int col = 0; col < 6; col++) {
                    var cell = sheet.getRow(row).getCell(col);
                    assertEquals("", reader.read(cell), "首表数据区必须为空");
                    assertEquals("@", cell.getCellStyle().getDataFormatString(), "3000 行均需文本格式");
                }
            }
            var gradeValidations = sheet.getDataValidations().stream()
                    .filter(v -> v.getValidationConstraint().getValidationType() == DataValidationConstraint.ValidationType.LIST)
                    .toList();
            assertEquals(1, gradeValidations.size(), "不得残留重叠旧下拉");
            var validation = gradeValidations.getFirst();
            assertEquals("E2:E3001", validation.getRegions().getCellRangeAddresses()[0].formatAsString());
            assertTrue(validation.getEmptyCellAllowed());
            assertFalse(validation.getShowErrorBox(), "下拉不得阻止合法四位年份手填");
            assertEquals("MemberImportGrades", validation.getValidationConstraint().getFormula1());
            assertEquals("'年级选项'!$A$1:$A$33", workbook.getName("MemberImportGrades").getRefersToFormula());
            var options = workbook.getSheet("年级选项");
            assertTrue(workbook.isSheetHidden(workbook.getSheetIndex(options)));
            // A static template is deliberately checked against the current backend range.
            int minimum = LocalDate.now().getYear() - 30;
            for (int index = 0; index < 33; index++) {
                assertEquals((minimum + index) + "级", reader.read(options.getRow(index), 0), "跨年时重新生成静态模板");
            }
            assertEquals("", reader.read(options.getRow(33), 0));
            var examples = workbook.getSheet("填写示例");
            assertTrue(workbook.getSheetIndex(examples) > 0);
            assertEquals(CellType.STRING, examples.getRow(2).getCell(0).getCellType());
            assertEquals("000001", reader.read(examples.getRow(2), 0));
            assertEquals(LONG_STUDENT_NO, reader.read(examples.getRow(3), 0));
        }
        try (var archive = new ZipFile(TEMPLATE.toFile())) {
            for (var entry : archive.stream().filter(e -> e.getName().endsWith(".xml")).toList()) {
                try (var input = archive.getInputStream(entry)) {
                    assertFalse(new String(input.readAllBytes(), StandardCharsets.UTF_8).contains("专业"), entry.getName());
                }
            }
        }
    }

    @Test
    void untouchedTemplateNeverImportsReferenceExamples() throws Exception {
        var file = upload(Files.readAllBytes(TEMPLATE));
        try (var workbook = WorkbookFactory.create(file.getInputStream())) {
            var reader = new ExcelCellTextReader(workbook);
            for (int c = 0; c < 6; c++) assertEquals("", reader.read(workbook.getSheetAt(0).getRow(3000), c), "上传读取第 3001 行");
        }
        var preview = users.previewImport(file);
        assertTrue(preview.valid(), preview.errors().toString());
        assertEquals(0, preview.created());
        assertEquals(0, preview.updated());
        assertTrue(preview.changes().isEmpty());
        var result = users.importMembers(file, preview.token());
        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
    }

    @Test
    void templatePreservesLeadingZerosAndThirtyTwoDigitIdentifiersThroughPreviewAndImport() throws Exception {
        byte[] bytes;
        try (var input = Files.newInputStream(TEMPLATE); var workbook = WorkbookFactory.create(input);
             var output = new ByteArrayOutputStream()) {
            var sheet = workbook.getSheetAt(0);
            sheet.getRow(1).getCell(0).setCellValue("000001");
            sheet.getRow(1).getCell(1).setCellValue("文本示例甲");
            sheet.getRow(1).getCell(2).setCellValue("00000000000");
            sheet.getRow(1).getCell(5).setCellValue("000000001");
            sheet.getRow(3000).getCell(0).setCellValue(LONG_STUDENT_NO);
            sheet.getRow(3000).getCell(1).setCellValue("文本示例乙");
            sheet.getRow(3000).getCell(4).setCellValue(String.valueOf(LocalDate.now().getYear() + 2));
            workbook.write(output);
            bytes = output.toByteArray();
        }
        var file = upload(bytes);
        var preview = users.previewImport(file);
        assertTrue(preview.valid(), preview.errors().toString());
        assertEquals(2, preview.created());
        assertEquals(List.of("000001", LONG_STUDENT_NO), preview.changes().stream().map(UserService.ImportChange::studentNo).toList());
        var result = users.importMembers(file, preview.token());
        assertEquals(2, result.created());
        assertEquals("00000000000", jdbc.queryForObject("SELECT phone FROM users WHERE student_no = '000001'", String.class));
        assertEquals("000000001", jdbc.queryForObject("SELECT qq FROM users WHERE student_no = '000001'", String.class));
        assertEquals((LocalDate.now().getYear() + 2) + "级", jdbc.queryForObject("SELECT grade FROM users WHERE student_no = ?", String.class, LONG_STUDENT_NO));
    }

    private static MockMultipartFile upload(byte[] bytes) {
        return new MockMultipartFile("file", "members.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private static Path temporaryRoot() {
        try {
            return Files.createTempDirectory("ca-member-template-test-");
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
