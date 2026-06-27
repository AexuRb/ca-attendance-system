package com.ca.attendance.user;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class UserService {
    private static final Pattern STUDENT_NO_PATTERN = Pattern.compile("\\d{1,32}");
    private static final int IMPORT_ISSUE_LIMIT = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService logs;

    public UserService(UserRepository users, JdbcTemplate jdbc, PasswordEncoder passwordEncoder, OperationLogService logs) {
        this.users = users;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.logs = logs;
    }

    public List<UserSummary> search(String keyword, String role, String status, String grade) {
        requireManageUsers();
        return users.search(keyword, role, status, grade);
    }

    public UserRepository.UserPage searchPage(String keyword, String role, String status, String grade, int page, int pageSize) {
        requireManageUsers();
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        return users.searchPage(keyword, role, status, grade, safePage, safePageSize);
    }

    public List<String> grades() {
        requireManageUsers();
        return users.grades();
    }

    public UserSummary create(CreateUserRequest request) {
        AuthUser current = AuthContext.current();
        requireCreateUsers();
        Role role = parseRole(request.role() == null || request.role().isBlank() ? "MEMBER" : request.role());
        validateRoleAssignment(current.role(), null, role);
        String studentNo = required(request.studentNo(), "学号不能为空");
        String password = studentNo.length() <= 6 ? studentNo : studentNo.substring(studentNo.length() - 6);
        try {
            jdbc.update("""
                    INSERT INTO users (
                      student_no, name, password_hash, role, status, grade,
                      must_change_password, created_by, updated_by
                    )
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?, 1, ?, ?)
                    """,
                    studentNo,
                    required(request.name(), "姓名不能为空"),
                    passwordEncoder.encode(password),
                    role.name(),
                    normalizeGrade(request.grade()),
                    current.id(),
                    current.id()
            );
        } catch (DuplicateKeyException ex) {
            throw ApiException.badRequest("学号已存在");
        }
        UserSummary created = users.findActiveByStudentNo(studentNo).orElseThrow();
        logs.log("CREATE_USER", "users", created.id(), null, created, "新增成员");
        return created;
    }

    public ImportResult importMembers(MultipartFile file) {
        AuthUser current = AuthContext.current();
        if (!current.role().canManageUsers()) {
            throw ApiException.forbidden("只有会长或管理员可以批量导入成员");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择 Excel 文件");
        }

        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                throw ApiException.badRequest("Excel 文件没有工作表");
            }
            ImportResult result = importMembersFromSheet(sheet, current);
            logs.log("IMPORT_USERS", "users", null, null, result, "批量导入成员");
            return result;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("Excel 文件读取失败，请确认文件格式正确");
        }
    }

    public void updateProfile(ProfileRequest request) {
        AuthUser current = AuthContext.current();
        jdbc.update("""
                UPDATE users
                SET grade = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, normalizeGrade(request.grade()), current.id(), current.id());
    }

    public UserSummary update(long id, UpdateUserRequest request) {
        AuthUser current = AuthContext.current();
        requireManageUsers();
        UserSummary before = users.findSummaryById(id).orElseThrow(() -> ApiException.notFound("用户不存在"));
        if (before.role() == Role.ADMIN && current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有管理员可以修改管理员账号");
        }
        Role targetRole = request.role() == null || request.role().isBlank() ? before.role() : parseRole(request.role());
        validateRoleAssignment(current.role(), before.role(), targetRole);
        String targetStatus = request.status() == null || request.status().isBlank() ? before.status() : request.status().trim().toUpperCase();
        if (!targetStatus.equals("ACTIVE") && !targetStatus.equals("DISABLED")) {
            throw ApiException.badRequest("账号状态只能是 ACTIVE 或 DISABLED");
        }
        jdbc.update("""
                UPDATE users
                SET name = ?, role = ?, status = ?, grade = ?,
                    disabled_at = CASE WHEN ? = 'DISABLED' THEN COALESCE(disabled_at, CURRENT_TIMESTAMP) ELSE NULL END,
                    disabled_by = CASE WHEN ? = 'DISABLED' THEN ? ELSE NULL END,
                    updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                required(request.name() == null ? before.name() : request.name(), "姓名不能为空"),
                targetRole.name(),
                targetStatus,
                normalizeGrade(request.grade() == null ? before.grade() : request.grade()),
                targetStatus,
                targetStatus,
                current.id(),
                current.id(),
                id
        );
        UserSummary after = users.findSummaryById(id).orElseThrow();
        logs.log("UPDATE_USER", "users", id, before, after, request.reason() == null ? "修改成员信息" : request.reason());
        return after;
    }

    public void resetPassword(long id, ResetPasswordRequest request) {
        AuthUser current = AuthContext.current();
        requireManageUsers();
        UserSummary target = users.findSummaryById(id).orElseThrow(() -> ApiException.notFound("用户不存在"));
        if (target.role() == Role.ADMIN && current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有管理员可以重置管理员密码");
        }
        String password = request.newPassword();
        if (password == null || password.isBlank()) {
            password = target.studentNo().substring(Math.max(0, target.studentNo().length() - 6));
        }
        jdbc.update("""
                UPDATE users
                SET password_hash = ?, must_change_password = 1, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, passwordEncoder.encode(password), current.id(), id);
        logs.log("RESET_PASSWORD", "users", id, Map.of("studentNo", target.studentNo()), Map.of("mustChangePassword", true),
                request.reason() == null ? "重置密码" : request.reason());
    }

    public void delete(long id) {
        AuthUser current = AuthContext.current();
        if (current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有管理员可以删除成员");
        }
        if (current.id() == id) {
            throw ApiException.badRequest("不能删除当前登录账号");
        }
        UserSummary target = users.findSummaryById(id).orElseThrow(() -> ApiException.notFound("用户不存在"));
        Integer recordCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM attendance_records WHERE user_id = ?",
                Integer.class,
                id
        );
        if (recordCount != null && recordCount > 0) {
            throw ApiException.badRequest("该成员已有值班记录，不能删除，请改为停用账号");
        }
        logs.log("DELETE_USER", "users", id, target, Map.of("deleted", true), "删除成员");
        jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    public BulkStatusResult bulkStatus(BulkStatusRequest request) {
        AuthUser current = AuthContext.current();
        requireManageUsers();
        List<Long> targetIds = request.ids() == null || request.ids().isEmpty()
                ? users.searchIds(request.keyword(), request.role(), request.statusFilter(), request.grade())
                : request.ids();
        if (targetIds.isEmpty()) {
            throw ApiException.badRequest("请选择要处理的成员");
        }
        String targetStatus = request.status() == null ? "" : request.status().trim().toUpperCase();
        if (!targetStatus.equals("ACTIVE") && !targetStatus.equals("DISABLED")) {
            throw ApiException.badRequest("账号状态只能是 ACTIVE 或 DISABLED");
        }

        int updated = 0;
        int unchanged = 0;
        int skipped = 0;
        List<String> issues = new ArrayList<>();
        Set<Long> seenIds = new LinkedHashSet<>();

        for (Long id : targetIds) {
            if (id == null || !seenIds.add(id)) {
                continue;
            }
            UserSummary target = users.findSummaryById(id).orElse(null);
            if (target == null) {
                skipped++;
                addBulkIssue(issues, "用户不存在：" + id);
                continue;
            }
            if (target.role() == Role.ADMIN && current.role() != Role.ADMIN) {
                skipped++;
                addBulkIssue(issues, target.name() + "：会长不能修改管理员账号");
                continue;
            }
            if (target.id() == current.id() && targetStatus.equals("DISABLED")) {
                skipped++;
                addBulkIssue(issues, target.name() + "：当前登录账号不会被批量停用");
                continue;
            }
            if (target.status().equals(targetStatus)) {
                unchanged++;
                continue;
            }

            jdbc.update("""
                    UPDATE users
                    SET status = ?,
                        disabled_at = CASE WHEN ? = 'DISABLED' THEN COALESCE(disabled_at, CURRENT_TIMESTAMP) ELSE NULL END,
                        disabled_by = CASE WHEN ? = 'DISABLED' THEN ? ELSE NULL END,
                        updated_by = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    targetStatus,
                    targetStatus,
                    targetStatus,
                    current.id(),
                    current.id(),
                    id
            );
            updated++;
        }

        BulkStatusResult result = new BulkStatusResult(updated, unchanged, skipped, issues);
        logs.log("BULK_UPDATE_USER_STATUS", "users", null,
                Map.of("ids", seenIds, "targetStatus", targetStatus),
                result,
                request.reason() == null ? "批量修改账号状态" : request.reason());
        return result;
    }

    private ImportResult importMembersFromSheet(Sheet sheet, AuthUser current) {
        DataFormatter formatter = new DataFormatter();
        int headerRowIndex = findHeaderRow(sheet, formatter);
        Map<String, Integer> columns = headerRowIndex >= 0
                ? readHeaderColumns(sheet.getRow(headerRowIndex), formatter)
                : fallbackColumns();
        int startRow = headerRowIndex >= 0 ? headerRowIndex + 1 : 2;

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> issues = new ArrayList<>();
        Set<String> seenStudentNos = new LinkedHashSet<>();

        for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            ImportCandidate candidate = readImportCandidate(row, columns, formatter);
            if (candidate.isBlank()) {
                continue;
            }

            String studentNo = candidate.studentNo().replaceAll("\\s+", "");
            if (candidate.name().isBlank() || studentNo.isBlank()) {
                skipped++;
                addImportIssue(issues, "第 " + (i + 1) + " 行：缺少姓名或学号");
                continue;
            }
            if (!STUDENT_NO_PATTERN.matcher(studentNo).matches()) {
                skipped++;
                addImportIssue(issues, "第 " + (i + 1) + " 行：学号格式不正确");
                continue;
            }
            if (!seenStudentNos.add(studentNo)) {
                skipped++;
                addImportIssue(issues, "第 " + (i + 1) + " 行：学号在本次文件中重复");
                continue;
            }

            String grade;
            try {
                grade = normalizeGrade(candidate.grade());
            } catch (ApiException ex) {
                skipped++;
                addImportIssue(issues, "第 " + (i + 1) + " 行：" + ex.getMessage());
                continue;
            }

            if (userExists(studentNo)) {
                jdbc.update("""
                        UPDATE users
                        SET name = ?, grade = ?,
                            updated_by = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE student_no = ?
                        """,
                        candidate.name(),
                        grade,
                        current.id(),
                        studentNo
                );
                updated++;
            } else {
                String password = studentNo.length() <= 6 ? studentNo : studentNo.substring(studentNo.length() - 6);
                jdbc.update("""
                        INSERT INTO users (
                          student_no, name, password_hash, role, status, grade,
                          must_change_password, created_by, updated_by
                        )
                        VALUES (?, ?, ?, 'MEMBER', 'ACTIVE', ?, 1, ?, ?)
                        """,
                        studentNo,
                        candidate.name(),
                        passwordEncoder.encode(password),
                        grade,
                        current.id(),
                        current.id()
                );
                created++;
            }
        }

        return new ImportResult(created, updated, skipped, issues);
    }

    private int findHeaderRow(Sheet sheet, DataFormatter formatter) {
        int last = Math.min(sheet.getLastRowNum(), 9);
        for (int rowIndex = 0; rowIndex <= last; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            boolean hasName = false;
            boolean hasStudentNo = false;
            for (int col = Math.max(row.getFirstCellNum(), 0); col < row.getLastCellNum(); col++) {
                String text = cleanCell(formatter.formatCellValue(row.getCell(col)));
                if (text.contains("姓名")) {
                    hasName = true;
                }
                if (text.contains("学号")) {
                    hasStudentNo = true;
                }
            }
            if (hasName && hasStudentNo) {
                return rowIndex;
            }
        }
        return -1;
    }

    private Map<String, Integer> readHeaderColumns(Row row, DataFormatter formatter) {
        Map<String, Integer> columns = new HashMap<>();
        if (row == null) {
            return fallbackColumns();
        }
        for (int col = Math.max(row.getFirstCellNum(), 0); col < row.getLastCellNum(); col++) {
            String header = cleanCell(formatter.formatCellValue(row.getCell(col))).toLowerCase();
            if (header.contains("姓名")) {
                columns.putIfAbsent("name", col);
            }
            if (header.contains("学号")) {
                columns.putIfAbsent("studentNo", col);
            }
            if (header.contains("年级")) {
                columns.putIfAbsent("grade", col);
            }
        }
        if (!columns.containsKey("name") || !columns.containsKey("studentNo")) {
            return fallbackColumns();
        }
        columns.putIfAbsent("grade", -1);
        return columns;
    }

    private Map<String, Integer> fallbackColumns() {
        return Map.of(
                "name", 1,
                "grade", 4,
                "studentNo", 5
        );
    }

    private ImportCandidate readImportCandidate(Row row, Map<String, Integer> columns, DataFormatter formatter) {
        return new ImportCandidate(
                cell(row, columns.get("studentNo"), formatter),
                cell(row, columns.get("name"), formatter),
                cell(row, columns.get("grade"), formatter)
        );
    }

    private String cell(Row row, Integer index, DataFormatter formatter) {
        if (row == null || index == null || index < 0) {
            return "";
        }
        return cleanCell(formatter.formatCellValue(row.getCell(index)));
    }

    private String cleanCell(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean userExists(String studentNo) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE student_no = ?", Integer.class, studentNo);
        return count != null && count > 0;
    }

    private void addImportIssue(List<String> issues, String issue) {
        if (issues.size() < IMPORT_ISSUE_LIMIT) {
            issues.add(issue);
        }
    }

    private void addBulkIssue(List<String> issues, String issue) {
        if (issues.size() < IMPORT_ISSUE_LIMIT) {
            issues.add(issue);
        }
    }

    private void requireManageUsers() {
        if (!AuthContext.current().role().canManageUsers()) {
            throw ApiException.forbidden("无权管理成员");
        }
    }

    private void requireCreateUsers() {
        if (!AuthContext.current().role().canManageUsers()) {
            throw ApiException.forbidden("无权新增成员");
        }
    }

    private void validateRoleAssignment(Role operator, Role oldRole, Role newRole) {
        if (oldRole == null && newRole == Role.MEMBER && operator.canManageUsers()) {
            return;
        }
        if (newRole == Role.ADMIN && operator != Role.ADMIN) {
            throw ApiException.forbidden("管理员只能由管理员任命");
        }
        if (oldRole == Role.ADMIN && operator != Role.ADMIN) {
            throw ApiException.forbidden("只有管理员可以调整管理员角色");
        }
        if ((newRole == Role.PRESIDENT || newRole == Role.MINISTER || newRole == Role.MEMBER)
                && !(operator == Role.PRESIDENT || operator == Role.ADMIN)) {
            throw ApiException.forbidden("无权调整该角色");
        }
    }

    private Role parseRole(String value) {
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw ApiException.badRequest("角色不合法");
        }
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(message);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeGrade(String value) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.length() != 4) {
            throw ApiException.badRequest("年级格式应为 2007级 到 2057级");
        }
        int year = Integer.parseInt(digits);
        if (year < 2007 || year > 2057) {
            throw ApiException.badRequest("年级范围应为 2007级 到 2057级");
        }
        return year + "级";
    }

    public record CreateUserRequest(String studentNo, String name, String role, String grade) {
    }

    public record UpdateUserRequest(String name, String role, String status, String grade, String reason) {
    }

    public record ProfileRequest(String grade) {
    }

    public record ResetPasswordRequest(String newPassword, String reason) {
    }

    public record BulkStatusRequest(List<Long> ids, String keyword, String role, String statusFilter, String grade, String status, String reason) {
    }

    public record BulkStatusResult(int updated, int unchanged, int skipped, List<String> errors) {
    }

    public record ImportResult(int created, int updated, int skipped, List<String> errors) {
    }

    private record ImportCandidate(String studentNo, String name, String grade) {
        boolean isBlank() {
            return studentNo.isBlank() && name.isBlank() && grade.isBlank();
        }
    }
}
