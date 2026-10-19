package com.ca.attendance.settings;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.JdbcWriteChecks;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class CreditsService {
    static final String KEY = "ACKNOWLEDGEMENTS";
    public static final String INTRO = "感谢为本系统的诞生与完善付出时间和心力的每一位贡献者。";
    public static final Entry FOUNDER = new Entry("2025 会长-陈禹杭", "项目发起、产品设计与开发");
    private static final TypeReference<List<Entry>> ENTRIES = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final OperationLogService logs;
    private final ObjectMapper mapper;
    public CreditsService(JdbcTemplate jdbc, OperationLogService logs, ObjectMapper mapper) {
        this.jdbc = jdbc; this.logs = logs; this.mapper = mapper;
    }
    public Credits current() { AuthContext.current(); return fromStored(readStored()); }

    @Transactional
    public Credits update(Update request) {
        if (AuthContext.current().role() != Role.ADMIN) throw ApiException.forbidden("只有管理员可以编辑鸣谢");
        if (request == null || !INTRO.equals(request.intro()) || request.contributors() == null
                || request.contributors().isEmpty() || !FOUNDER.equals(request.contributors().getFirst())) {
            throw ApiException.badRequest("感谢语和首条贡献信息不可修改、删除或调整顺序");
        }
        if (request.contributors().size() > 200) throw ApiException.badRequest("贡献信息最多支持200条");
        List<Entry> extras = new ArrayList<>();
        for (Entry entry : request.contributors().subList(1, request.contributors().size())) {
            if (entry == null) throw ApiException.badRequest("贡献信息不能为空");
            extras.add(new Entry(clean(entry.name(), 100, false), clean(entry.contribution(), 1000, true)));
        }
        String stored = readStored();
        Credits before = fromStored(stored);
        if (!before.revision().equals(request.revision())) throw ApiException.conflict("鸣谢已被更新，当前输入已保留，请重新载入后核对");
        if (before.contributors().subList(1, before.contributors().size()).equals(extras)) return before;
        String value = mapper.writeValueAsString(extras);
        int affected = jdbc.update("""
                INSERT INTO app_settings (setting_key, setting_value, description, updated_by)
                VALUES (?, ?, '鸣谢贡献信息', ?)
                ON CONFLICT (setting_key) DO UPDATE SET
                  setting_value = excluded.setting_value, updated_by = excluded.updated_by,
                  updated_at = datetime('now', 'localtime')
                """, KEY, value, AuthContext.current().id());
        JdbcWriteChecks.requireOne(affected, "鸣谢保存失败，请重试");
        Credits after = fromStored(value);
        logs.log("UPDATE_ACKNOWLEDGEMENTS", "app_settings", null, before, after, "编辑鸣谢贡献信息");
        return after;
    }
    private String readStored() {
        List<String> values = jdbc.query("SELECT setting_value FROM app_settings WHERE setting_key = ?",
                (rs, i) -> rs.getString(1), KEY);
        return values.isEmpty() ? "" : values.getFirst();
    }
    private Credits fromStored(String stored) {
        try {
            List<Entry> entries = new ArrayList<>(); entries.add(FOUNDER);
            if (!stored.isEmpty()) {
                List<Entry> extras = mapper.readValue(stored, ENTRIES);
                if (extras == null || extras.size() > 199) throw new IllegalArgumentException();
                for (Entry extra : extras) {
                    if (extra == null) throw new IllegalArgumentException();
                    entries.add(new Entry(clean(extra.name(), 100, false), clean(extra.contribution(), 1000, true)));
                }
            }
            String revision = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stored.getBytes(StandardCharsets.UTF_8)));
            return new Credits(INTRO, List.copyOf(entries), revision);
        } catch (JacksonException | IllegalArgumentException | NoSuchAlgorithmException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "鸣谢信息暂时无法读取，请联系管理员核对");
        }
    }
    private String clean(String value, int limit, boolean multiline) {
        if (value == null || value.isBlank()) throw ApiException.badRequest("请填写职务-姓名和贡献");
        String result = value.strip();
        if (result.length() > limit) throw ApiException.badRequest("职务-姓名最多100字，贡献最多1000字");
        if (result.chars().anyMatch(c -> Character.isISOControl(c) && !(multiline && (c == '\n' || c == '\r' || c == '\t')))) {
            throw ApiException.badRequest("贡献信息包含不支持的字符");
        }
        return result;
    }
    public record Entry(String name, String contribution) {}
    public record Credits(String intro, List<Entry> contributors, String revision) {}
    public record Update(String intro, List<Entry> contributors, String revision) {}
}
