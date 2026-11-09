package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackupContactPolicyTest {
    @TempDir Path directory;
    final ObjectMapper mapper = new ObjectMapper();

    @Test void reviewReportsAllInvalidContactsWithoutChangingArchiveAndBoundsTheList() throws Exception {
        Path users = directory.resolve("users.json");
        Files.writeString(users, mapper.writeValueAsString(java.util.Collections.nCopies(60,
                Map.of("phone", "中文电话", "qq", "中文QQ"))));
        byte[] original = Files.readAllBytes(users);
        var file = new MockMultipartFile("file", "backup.zip", "application/zip", original);
        var review = BackupContactPolicy.review(file, users, mapper);
        assertThat(review.phoneCount()).isEqualTo(60);
        assertThat(review.qqCount()).isEqualTo(60);
        assertThat(review.issues()).hasSize(100);
        assertThat(review.truncated()).isTrue();
        assertThat(review.fileHash()).hasSize(64);
        assertThat(Files.readAllBytes(users)).isEqualTo(original);
    }

    @Test void onlyConfirmedInvalidContactFieldsAreCleared() {
        assertThatThrownBy(() -> BackupContactPolicy.restoreValue("users", "phone", "中文", false)).isInstanceOf(ApiException.class);
        assertThat(BackupContactPolicy.restoreValue("users", "phone", "中文", true)).isNull();
        assertThat(BackupContactPolicy.restoreValue("users", "qq", "012345", true)).isNull();
        assertThat(BackupContactPolicy.restoreValue("users", "qq", "12345678901234567890", true)).isEqualTo("12345678901234567890");
        assertThat(BackupContactPolicy.restoreValue("users", "phone", " 13800000000 ", true)).isEqualTo(" 13800000000 ");
        assertThat(BackupContactPolicy.restoreValue("users", "name", "保留原姓名", true)).isEqualTo("保留原姓名");
        assertThat(BackupContactPolicy.restoreValue("repair_cases", "phone", "原联系方式", true)).isEqualTo("原联系方式");
        assertThat(BackupContactPolicy.restoreValue("users", "qq", 123456789L, true)).isNull();
    }

    @Test void executorBindsOnlyBadContactsAsNullAndKeepsTwentyDigitQqAsString() throws Exception {
        Path users = directory.resolve("users.json");
        Files.writeString(users, mapper.writeValueAsString(List.of(Map.of("id", 1L, "phone", "中文", "qq", "12345678901234567890", "name", "合成成员"))));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new DatabaseRestoreExecutor(jdbc, mapper).restore(new BackupRestorePayload(Map.of("users", users), true, 1, 0));
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        var values = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), values.capture());
        String columns = sql.getValue().substring(sql.getValue().indexOf('(')+1, sql.getValue().indexOf(')'));
        String[] names = columns.replace("`", "").split(", ");
        Map<String, Object> bound = new java.util.HashMap<>();
        for (int i=0;i<names.length;i++) bound.put(names[i],values.getValue()[i]);
        assertThat(bound).containsEntry("phone", null).containsEntry("qq", "12345678901234567890").containsEntry("name", "合成成员");
    }
}
