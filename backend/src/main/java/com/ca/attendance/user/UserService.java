package com.ca.attendance.user;

import com.ca.attendance.access.RolePermissionPolicy;
import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.ExcelCellTextReader;
import com.ca.attendance.common.ExcelImportPolicy;
import com.ca.attendance.common.PaginationPolicy;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.maintenance.BackupService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;

@Service
public class UserService {
    private static final int IMPORT_ISSUE_LIMIT = 20;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService logs;
    private final BackupService backups;
    private final TokenService tokens;
    private final UserDeletionHistoryGuard deletionHistory;

    public UserService(UserRepository users, JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                       OperationLogService logs, BackupService backups, TokenService tokens,
                       UserDeletionHistoryGuard deletionHistory) {
        this.users = users;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.logs = logs;
        this.backups = backups;
        this.tokens = tokens;
        this.deletionHistory = deletionHistory;
    }

    @Transactional(readOnly = true)
    public List<UserSummary> search(String keyword, String role, String status, String grade) {
        requireManageUsers();
        SearchFilters filters = searchFilters(role, status, grade);
        return users.search(keyword, filters.role(), filters.status(), filters.grade());
    }

    @Transactional(readOnly = true)
    public UserRepository.UserPage searchPage(String keyword, String role, String status, String grade, int page, int pageSize) {
        requireManageUsers();
        PaginationPolicy.PageRequest paging = PaginationPolicy.normalize(page, pageSize);
        SearchFilters filters = searchFilters(role, status, grade);
        return users.searchPage(keyword, filters.role(), filters.status(), filters.grade(), paging.page(), paging.pageSize());
    }

    @Transactional(readOnly = true)
    public List<String> grades() {
        requireManageUsers();
        return users.grades();
    }

    @Transactional
    public UserSummary create(CreateUserRequest request) {
        AuthUser current = AuthContext.current();
        requireCreateUsers();
        Role role = parseRole(request.role() == null || request.role().isBlank() ? "MEMBER" : request.role());
        validateRoleAssignment(current.role(), null, role);
        String studentNo = UserInputPolicy.newStudentNo(request.studentNo());
        String password = UserInputPolicy.defaultPassword(studentNo);
        String name = UserInputPolicy.name(request.name());
        String phone = UserInputPolicy.phone(request.phone());
        String college = UserInputPolicy.college(request.major());
        String grade = UserInputPolicy.grade(request.grade());
        String qq = UserInputPolicy.qq(request.qq());
        try {
            jdbc.update("""
                    INSERT INTO users (
                      student_no, name, password_hash, role, status, phone, major, grade, qq,
                      must_change_password, created_by, updated_by
                    )
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, 1, ?, ?)
                    """,
                    studentNo,
                    name,
                    passwordEncoder.encode(password),
                    role.name(),
                    phone,
                    college,
                    grade,
                    qq,
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

    @Transactional
    public ImportResult importMembers(MultipartFile file) {
        AuthUser current = AuthContext.current();
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.MEMBERS_MANAGE,
                "只有会长或管理员可以批量导入成员");
        ExcelImportPolicy.validateFile(file, "成员");

        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                throw ApiException.badRequest("Excel 文件没有工作表");
            }
            ImportResult result = importMembersFromSheet(sheet, current, new ExcelCellTextReader(workbook));
            if (!result.errors().isEmpty()) {
                throw ApiException.badRequest(importFailureMessage("成员文件校验未通过", result.errors()));
            }
            logs.log("IMPORT_USERS", "users", null, null, result, "批量导入成员");
            return result;
        } catch (ApiException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("Excel 文件读取失败，请确认文件格式正确");
        }
    }

    @Transactional
    public void updateProfile(ProfileRequest request) {
        AuthUser current = AuthContext.current();
        if (request.grade() != null && !request.grade().isBlank()) {
            throw ApiException.badRequest("年级只能由会长或管理员在成员管理中修改");
        }
        UserSummary before = users.findSummaryById(current.id())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        String phone = UserInputPolicy.phone(request.phone());
        String college = UserInputPolicy.college(request.major());
        String qq = UserInputPolicy.qq(request.qq());
        int updated = jdbc.update("""
                UPDATE users
                SET phone = ?, major = ?, qq = ?, updated_by = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ?
                """, phone, college, qq, current.id(), current.id());
        if (updated != 1) {
            throw ApiException.notFound("用户不存在");
        }
        UserSummary after = users.findSummaryById(current.id()).orElseThrow();
        logs.log("UPDATE_PROFILE", "users", current.id(), before, after, "修改个人资料");
    }

    @Transactional
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
        protectAdminContinuity(current, before, targetRole, targetStatus);
        String name = UserInputPolicy.name(request.name() == null ? before.name() : request.name());
        String phone = UserInputPolicy.phone(request.phone() == null ? before.phone() : request.phone());
        String college = UserInputPolicy.college(request.major() == null ? before.major() : request.major());
        String grade = UserInputPolicy.grade(request.grade() == null ? before.grade() : request.grade());
        String qq = UserInputPolicy.qq(request.qq() == null ? before.qq() : request.qq());
        String reason = UserInputPolicy.reason(request.reason());
        jdbc.update("""
                UPDATE users
                SET name = ?, role = ?, status = ?, phone = ?, major = ?, grade = ?, qq = ?,
                    disabled_at = CASE WHEN ? = 'DISABLED' THEN COALESCE(disabled_at, datetime('now', 'localtime')) ELSE NULL END,
                    disabled_by = CASE WHEN ? = 'DISABLED' THEN ? ELSE NULL END,
                    updated_by = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ?
                """,
                name,
                targetRole.name(),
                targetStatus,
                phone,
                college,
                grade,
                qq,
                targetStatus,
                targetStatus,
                current.id(),
                current.id(),
                id
        );
        tokens.revokeUser(id);
        UserSummary after = users.findSummaryById(id).orElseThrow();
        logs.log("UPDATE_USER", "users", id, before, after, reason == null ? "修改成员信息" : reason);
        return after;
    }

    @Transactional
    public void resetPassword(long id, ResetPasswordRequest request) {
        AuthUser current = AuthContext.current();
        requireManageUsers();
        UserSummary target = users.findSummaryById(id).orElseThrow(() -> ApiException.notFound("用户不存在"));
        if (target.role() == Role.ADMIN && current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有管理员可以重置管理员密码");
        }
        String password = request.newPassword();
        if (password == null || password.isBlank()) {
            password = UserInputPolicy.defaultPassword(target.studentNo());
        } else {
            password = UserInputPolicy.password(password);
        }
        String reason = UserInputPolicy.reason(request.reason());
        jdbc.update("""
                UPDATE users
                SET password_hash = ?, must_change_password = 1, updated_by = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ?
                """, passwordEncoder.encode(password), current.id(), id);
        tokens.revokeUser(id);
        logs.log("RESET_PASSWORD", "users", id, Map.of("studentNo", target.studentNo()), Map.of("mustChangePassword", true),
                reason == null ? "重置密码" : reason);
    }

    @Transactional
    public void delete(long id, String reason) {
        AuthUser current = AuthContext.current();
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.MEMBERS_DELETE,
                "只有管理员可以删除成员");
        if (current.id() == id) {
            throw ApiException.badRequest("不能删除当前登录账号");
        }
        String normalizedReason = UserInputPolicy.reason(reason);
        if (normalizedReason == null) {
            throw ApiException.badRequest("删除成员必须填写原因");
        }
        int locked = jdbc.update("UPDATE users SET updated_at = updated_at WHERE id = ?", id);
        if (locked != 1) {
            throw ApiException.notFound("用户不存在或已被删除");
        }
        UserSummary target = users.findSummaryById(id).orElseThrow(() -> ApiException.notFound("用户不存在"));
        rejectDeletionWithHistory(id);
        BackupService.BackupItem safetyBackup = backups.create();
        int deleted = jdbc.update("DELETE FROM users WHERE id = ?", id);
        if (deleted != 1) {
            throw ApiException.notFound("用户不存在或已被删除");
        }
        logs.log("DELETE_USER", "users", id, target, Map.of("deleted", true),
                normalizedReason + "；删除前自动备份：" + safetyBackup.filename());
        tokens.revokeUser(id);
    }

    private void rejectDeletionWithHistory(long id) {
        List<String> references = deletionHistory.findReferences(id);
        if (!references.isEmpty()) {
            throw ApiException.conflict(
                    "该成员已有" + String.join("、", references) + "，不能永久删除，请改为停用账号"
            );
        }
    }

    @Transactional
    public BulkStatusResult bulkStatus(BulkStatusRequest request) {
        AuthUser current = AuthContext.current();
        requireManageUsers();
        String reason = UserInputPolicy.reason(request.reason());
        SearchFilters filters = searchFilters(request.role(), request.statusFilter(), request.grade());
        List<Long> targetIds = request.ids() == null || request.ids().isEmpty()
                ? users.searchIds(request.keyword(), filters.role(), filters.status(), filters.grade())
                : request.ids();
        if (targetIds.isEmpty()) {
            throw ApiException.badRequest("请选择要处理的成员");
        }
        String targetStatus = request.status() == null ? "" : request.status().trim().toUpperCase();
        if (!targetStatus.equals("ACTIVE") && !targetStatus.equals("DISABLED")) {
            throw ApiException.badRequest("账号状态只能是 ACTIVE 或 DISABLED");
        }

        int unchanged = 0;
        int skipped = 0;
        List<String> issues = new ArrayList<>();
        Set<Long> seenIds = new LinkedHashSet<>();
        List<UserSummary> changes = new ArrayList<>();

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
            changes.add(target);
        }

        BackupService.BackupItem safetyBackup = "DISABLED".equals(targetStatus) && !changes.isEmpty()
                ? backups.create()
                : null;
        List<Object[]> batchArgs = changes.stream()
                .map(target -> new Object[]{
                        targetStatus,
                        targetStatus,
                        targetStatus,
                        current.id(),
                        current.id(),
                        target.id()
                })
                .toList();
        int[] updateCounts = batchArgs.isEmpty()
                ? new int[0]
                : jdbc.batchUpdate("""
                        UPDATE users
                        SET status = ?,
                            disabled_at = CASE WHEN ? = 'DISABLED' THEN COALESCE(disabled_at, datetime('now', 'localtime')) ELSE NULL END,
                            disabled_by = CASE WHEN ? = 'DISABLED' THEN ? ELSE NULL END,
                            updated_by = ?, updated_at = datetime('now', 'localtime')
                        WHERE id = ?
                        """, batchArgs);
        if (updateCounts.length != changes.size()) {
            throw ApiException.badRequest("批量修改账号状态时返回的更新数量不正确");
        }
        for (int count : updateCounts) {
            if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
                throw ApiException.badRequest("批量修改账号状态时有成员未成功更新");
            }
        }

        int updated = changes.size();
        BulkStatusResult result = new BulkStatusResult(updated, unchanged, skipped, issues, safetyBackup);
        logs.log("BULK_UPDATE_USER_STATUS", "users", null,
                Map.of("ids", seenIds, "targetStatus", targetStatus),
                result,
                bulkStatusReason(reason, safetyBackup));
        changes.forEach(target -> tokens.revokeUser(target.id()));
        return result;
    }

    private String bulkStatusReason(String reason, BackupService.BackupItem safetyBackup) {
        String text = reason == null ? "批量修改账号状态" : reason;
        return safetyBackup == null ? text : text + "；停用前自动备份：" + safetyBackup.filename();
    }

    private ImportResult importMembersFromSheet(Sheet sheet, AuthUser current, ExcelCellTextReader reader) {
        int headerRowIndex = findHeaderRow(sheet, reader);
        Map<String, Integer> columns = headerRowIndex >= 0
                ? readHeaderColumns(sheet.getRow(headerRowIndex), reader)
                : fallbackColumns();
        int startRow = headerRowIndex >= 0 ? headerRowIndex + 1 : 2;
        ExcelImportPolicy.validateRowCount(sheet, startRow, "成员");

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
            ImportCandidate candidate = readImportCandidate(row, columns, reader);
            if (candidate.isBlank()) {
                continue;
            }

            String studentNo;
            String name;
            String phone;
            String college;
            String grade;
            String qq;
            boolean existing;
            try {
                studentNo = UserInputPolicy.accountReference(candidate.studentNo());
                existing = userExists(studentNo);
                if (!existing) {
                    studentNo = UserInputPolicy.newStudentNo(studentNo);
                }
                name = UserInputPolicy.name(candidate.name());
                phone = UserInputPolicy.phone(candidate.phone());
                college = UserInputPolicy.college(candidate.major());
                grade = UserInputPolicy.grade(candidate.grade());
                qq = UserInputPolicy.qq(candidate.qq());
            } catch (ApiException ex) {
                skipped++;
                addImportIssue(issues, "第 " + (i + 1) + " 行：" + ex.getMessage());
                continue;
            }
            if (!seenStudentNos.add(studentNo)) {
                skipped++;
                addImportIssue(issues, "第 " + (i + 1) + " 行：学号在本次文件中重复");
                continue;
            }

            if (existing) {
                if (userRole(studentNo) == Role.ADMIN && current.role() != Role.ADMIN) {
                    skipped++;
                    addImportIssue(issues, "第 " + (i + 1) + " 行：会长不能通过导入修改管理员账号");
                    continue;
                }
                jdbc.update("""
                        UPDATE users
                        SET name = ?, phone = COALESCE(?, phone), major = COALESCE(?, major),
                            grade = COALESCE(?, grade), qq = COALESCE(?, qq),
                            updated_by = ?, updated_at = datetime('now', 'localtime')
                        WHERE student_no = ?
                        """,
                        name,
                        phone,
                        college,
                        grade,
                        qq,
                        current.id(),
                        studentNo
                );
                updated++;
            } else {
                String password = UserInputPolicy.defaultPassword(studentNo);
                jdbc.update("""
                        INSERT INTO users (
                          student_no, name, password_hash, role, status, phone, major, grade, qq,
                          must_change_password, created_by, updated_by
                        )
                        VALUES (?, ?, ?, 'MEMBER', 'ACTIVE', ?, ?, ?, ?, 1, ?, ?)
                        """,
                        studentNo,
                        name,
                        passwordEncoder.encode(password),
                        phone,
                        college,
                        grade,
                        qq,
                        current.id(),
                        current.id()
                );
                created++;
            }
        }

        return new ImportResult(created, updated, skipped, issues);
    }

    private String importFailureMessage(String summary, List<String> issues) {
        return summary + "，未写入任何成员：" + String.join("；", issues);
    }

    private int findHeaderRow(Sheet sheet, ExcelCellTextReader reader) {
        int last = Math.min(sheet.getLastRowNum(), 9);
        for (int rowIndex = 0; rowIndex <= last; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            boolean hasName = false;
            boolean hasStudentNo = false;
            for (int col = Math.max(row.getFirstCellNum(), 0); col < row.getLastCellNum(); col++) {
                String text = reader.read(row, col);
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

    private Map<String, Integer> readHeaderColumns(Row row, ExcelCellTextReader reader) {
        Map<String, Integer> columns = new HashMap<>();
        if (row == null) {
            return fallbackColumns();
        }
        for (int col = Math.max(row.getFirstCellNum(), 0); col < row.getLastCellNum(); col++) {
            String header = reader.read(row, col).toLowerCase();
            if (header.contains("姓名")) {
                columns.putIfAbsent("name", col);
            }
            if (header.contains("学号")) {
                columns.putIfAbsent("studentNo", col);
            }
            if (header.contains("联系方式") || header.contains("手机号") || header.contains("手机") || header.contains("电话")) {
                columns.putIfAbsent("phone", col);
            }
            if (header.contains("学院")) {
                columns.putIfAbsent("major", col);
            }
            if (header.contains("年级")) {
                columns.putIfAbsent("grade", col);
            }
            if (header.contains("qq")) {
                columns.putIfAbsent("qq", col);
            }
        }
        if (!columns.containsKey("name") || !columns.containsKey("studentNo")) {
            return fallbackColumns();
        }
        columns.putIfAbsent("phone", -1);
        columns.putIfAbsent("major", -1);
        columns.putIfAbsent("grade", -1);
        columns.putIfAbsent("qq", -1);
        return columns;
    }

    private Map<String, Integer> fallbackColumns() {
        return Map.of(
                "name", 1,
                "major", 3,
                "grade", 4,
                "studentNo", 5,
                "phone", 8,
                "qq", -1
        );
    }

    private ImportCandidate readImportCandidate(Row row, Map<String, Integer> columns, ExcelCellTextReader reader) {
        return new ImportCandidate(
                cell(row, columns.get("studentNo"), reader),
                cell(row, columns.get("name"), reader),
                cell(row, columns.get("phone"), reader),
                cell(row, columns.get("major"), reader),
                cell(row, columns.get("grade"), reader),
                cell(row, columns.get("qq"), reader)
        );
    }

    private String cell(Row row, Integer index, ExcelCellTextReader reader) {
        if (row == null || index == null || index < 0) {
            return "";
        }
        return reader.read(row, index);
    }

    private boolean userExists(String studentNo) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE student_no = ?", Integer.class, studentNo);
        return count != null && count > 0;
    }

    private Role userRole(String studentNo) {
        String role = jdbc.queryForObject("SELECT role FROM users WHERE student_no = ?", String.class, studentNo);
        return Role.valueOf(role);
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
        RolePermissionPolicy.require(AuthContext.current().role(),
                RolePermissionPolicy.Permission.MEMBERS_MANAGE,
                "无权管理成员");
    }

    private void requireCreateUsers() {
        RolePermissionPolicy.require(AuthContext.current().role(),
                RolePermissionPolicy.Permission.MEMBERS_MANAGE,
                "无权新增成员");
    }

    private void validateRoleAssignment(Role operator, Role oldRole, Role newRole) {
        if (oldRole == null && newRole == Role.MEMBER
                && RolePermissionPolicy.allows(operator, RolePermissionPolicy.Permission.MEMBERS_MANAGE)) {
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

    private void protectAdminContinuity(AuthUser current, UserSummary before, Role targetRole, String targetStatus) {
        if (before.role() != Role.ADMIN) {
            return;
        }
        boolean roleChanged = targetRole != Role.ADMIN;
        boolean disabled = !"ACTIVE".equals(targetStatus);
        if (!roleChanged && !disabled) {
            return;
        }
        if (before.id() == current.id()) {
            throw ApiException.badRequest("不能停用或调整当前管理员账号的角色，请由另一名管理员操作");
        }
        Integer remainingActiveAdmins = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM users
                WHERE role = 'ADMIN' AND status = 'ACTIVE' AND id <> ?
                """, Integer.class, before.id());
        if (remainingActiveAdmins == null || remainingActiveAdmins == 0) {
            throw ApiException.badRequest("系统必须保留至少一个启用的管理员账号");
        }
    }

    private Role parseRole(String value) {
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw ApiException.badRequest("角色不合法");
        }
    }

    private SearchFilters searchFilters(String role, String status, String grade) {
        String normalizedRole = null;
        if (role != null && !role.isBlank()) {
            normalizedRole = parseRole(role).name();
        }
        String normalizedStatus = null;
        if (status != null && !status.isBlank()) {
            normalizedStatus = status.trim().toUpperCase();
            if (!normalizedStatus.equals("ACTIVE") && !normalizedStatus.equals("DISABLED")) {
                throw ApiException.badRequest("账号状态只能是 ACTIVE 或 DISABLED");
            }
        }
        return new SearchFilters(normalizedRole, normalizedStatus, UserInputPolicy.grade(grade));
    }

    public record CreateUserRequest(String studentNo, String name, String role, String phone, String major, String grade, String qq) {
    }

    public record UpdateUserRequest(String name, String role, String status, String phone, String major, String grade, String qq, String reason) {
    }

    public record ProfileRequest(String phone, String major, String grade, String qq) {
    }

    public record ResetPasswordRequest(String newPassword, String reason) {
    }

    public record DeleteUserRequest(String reason) {
    }

    public record BulkStatusRequest(List<Long> ids, String keyword, String role, String statusFilter, String grade, String status, String reason) {
    }

    public record BulkStatusResult(int updated, int unchanged, int skipped, List<String> errors, BackupService.BackupItem safetyBackup) {
    }

    public record ImportResult(int created, int updated, int skipped, List<String> errors) {
    }

    private record ImportCandidate(String studentNo, String name, String phone, String major, String grade, String qq) {
        boolean isBlank() {
            return studentNo.isBlank() && name.isBlank() && phone.isBlank() && major.isBlank() && grade.isBlank() && qq.isBlank();
        }
    }

    private record SearchFilters(String role, String status, String grade) {
    }
}
