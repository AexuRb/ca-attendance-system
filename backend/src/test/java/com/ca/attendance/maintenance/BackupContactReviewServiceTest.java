package com.ca.attendance.maintenance;

import com.ca.attendance.auth.*;
import com.ca.attendance.common.*;
import com.ca.attendance.config.StoragePaths;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackupContactReviewServiceTest {
    @TempDir Path root;
    final ObjectMapper mapper = new ObjectMapper();
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final TransactionTemplate tx = mock(TransactionTemplate.class);
    final TokenService tokens = mock(TokenService.class);
    BackupService service;
    @BeforeEach void setup() { service = new BackupService(jdbc, mapper, tx, tokens, new StoragePaths(root.toString())); role(Role.ADMIN); }
    @AfterEach void clear() { AuthContext.clear(); }
    void role(Role role) { AuthContext.set(new AuthUser(1,"9900000001","合成账号",role,Instant.now().plusSeconds(60))); }
    MockMultipartFile archive() throws Exception {
        List<String> tables = List.of("users","attendance_records","operation_logs","duty_weekday_settings");
        Map<String,Object> user = new LinkedHashMap<>();
        for (String key : BackupSchema.REQUIRED_KEYS.get("users")) user.put(key,null);
        user.putAll(Map.of("id",1,"student_no","9900000001","name","合成成员","role","MEMBER","status","ACTIVE","phone","中文电话","qq","中文QQ"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("metadata.json")); zip.write(mapper.writeValueAsBytes(Map.of("tables",tables))); zip.closeEntry();
            for(String table:tables) { zip.putNextEntry(new ZipEntry(table+".json")); zip.write(mapper.writeValueAsBytes(table.equals("users")?List.of(user):List.of())); zip.closeEntry(); }
        }
        return new MockMultipartFile("file","backup.zip","application/zip",bytes.toByteArray());
    }
    @Test void reviewAndRejectedRestoresNeverReachDatabaseBackupOrTokenRevocation() throws Exception {
        var file = archive();
        var review = service.reviewRestore(file);
        assertThat(review.phoneCount()).isEqualTo(1); assertThat(review.qqCount()).isEqualTo(1);
        assertThatThrownBy(()->service.restore(file)).isInstanceOf(ApiException.class).hasMessageContaining("明确确认");
        assertThatThrownBy(()->service.restore(file,true,"wrong-file-hash")).isInstanceOf(ApiException.class).hasMessageContaining("不一致");
        verifyNoInteractions(jdbc, tx, tokens);
        try (var paths=Files.walk(root)) { assertThat(paths.noneMatch(p->p.toString().endsWith(".db"))).isTrue(); }
        try (var paths=Files.walk(root.resolve("backups"))) { assertThat(paths.noneMatch(Files::isRegularFile)).isTrue(); }
    }
    @Test void nonAdministratorsCannotPreviewOrRestore() throws Exception {
        var file=archive();
        for(Role role:List.of(Role.MEMBER,Role.MINISTER,Role.PRESIDENT)) {
            role(role);
            assertThatThrownBy(()->service.reviewRestore(file)).isInstanceOf(ApiException.class).hasMessageContaining("管理员");
            assertThatThrownBy(()->service.restore(file,true,"anything")).isInstanceOf(ApiException.class).hasMessageContaining("管理员");
        }
        verifyNoInteractions(jdbc,tx,tokens);
    }
    @Test @SuppressWarnings("unchecked") void validExplicitConsentReachesTheTransactionBoundary() throws Exception {
        var file=archive();var review=service.reviewRestore(file);
        when(tx.execute(any(org.springframework.transaction.support.TransactionCallback.class)))
                .thenThrow(new IllegalStateException("transaction boundary"));
        assertThatThrownBy(()->service.restore(file,true,review.fileHash()))
                .isInstanceOf(IllegalStateException.class).hasMessage("transaction boundary");
        verify(tx).execute(any(org.springframework.transaction.support.TransactionCallback.class));
        verifyNoInteractions(jdbc,tokens);
    }
}
