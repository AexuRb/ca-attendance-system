package com.ca.attendance.user;

import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.Role;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.remote.port=0")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MemberImportHttpIntegrationTest {
    private static final Path ROOT = storageRoot();
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokens;
    @Value("${local.server.port}") int port;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", ROOT::toString);
    }

    @Test
    void realMultipartRejectsLimitsThenImportsEveryProfileField() throws Exception {
        long admin = jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('http-import-admin', '隔离导入管理员', 'test-hash', 'ADMIN', 'ACTIVE', 0)
                RETURNING id
                """, Long.class);
        String token = tokens.issue(admin, "http-import-admin", "隔离导入管理员", Role.ADMIN);
        var before = jdbc.queryForList("SELECT * FROM users ORDER BY id");
        var logs = jdbc.queryForList("SELECT * FROM operation_logs ORDER BY id");
        try (HttpClient client = HttpClient.newHttpClient()) {
            var tooLarge = upload(client, token, new byte[5 * 1024 * 1024 + 1]);
            assertEquals(400, tooLarge.statusCode());
            assertTrue(tooLarge.body().contains("不能超过 5 MB"));
            var tooMany = upload(client, token, workbook("学院", 3001, "边界成员", false));
            assertEquals(400, tooMany.statusCode());
            assertTrue(tooMany.body().contains("超过 3000 行"));
            assertEquals(before, jdbc.queryForList("SELECT * FROM users ORDER BY id"));
            assertEquals(logs, jdbc.queryForList("SELECT * FROM operation_logs ORDER BY id"));

            var result = upload(client, token, workbook("学院", 1, "六字段虚构成员", false));
            assertEquals(200, result.statusCode(), result.body());
            var row = jdbc.queryForMap("SELECT * FROM users WHERE student_no = '9900000061'");
            assertEquals("六字段虚构成员", row.get("name"));
            assertEquals("13800000000", row.get("phone"));
            assertEquals("虚构学院", row.get("major"));
            assertEquals("2026级", row.get("grade"));
            assertEquals("123456789", row.get("qq"));
            assertEquals("MEMBER", row.get("role"));
            assertEquals("ACTIVE", row.get("status"));
            Object hash = row.get("password_hash");
            var blank = upload(client, token, workbook("学院", 1, "更新虚构姓名", true));
            assertEquals(200, blank.statusCode(), blank.body());
            var updated = jdbc.queryForMap("SELECT * FROM users WHERE student_no = '9900000061'");
            assertEquals("更新虚构姓名", updated.get("name"));
            for (String field : List.of("phone", "major", "grade", "qq", "role", "status")) {
                assertEquals(row.get(field), updated.get(field), field);
            }
            assertEquals(hash, updated.get("password_hash"));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM operation_logs WHERE action_type='IMPORT_USERS'", Integer.class));
        }
    }

    private HttpResponse<String> upload(HttpClient client, String token, byte[] file) throws Exception {
        String boundary = "ca-import-boundary";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"members.xlsx\"\r\n"
                + "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(file);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/users/import"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private byte[] workbook(String collegeHeader, int rowIndex, String name, boolean blank) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("成员");
            String[] headers = {"学号", "姓名", "手机号", collegeHeader, "年级", "QQ"};
            String[] values = {"9900000061", name, blank ? "" : "13800000000", blank ? "" : "虚构学院",
                    blank ? "" : "2026级", blank ? "" : "123456789"};
            var header = sheet.createRow(0);
            var row = sheet.createRow(rowIndex);
            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
                row.createCell(index).setCellValue(values[index]);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static Path storageRoot() {
        try { return Files.createTempDirectory("ca-member-http-"); }
        catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
}
