package com.ca.attendance.user;

import com.ca.attendance.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserInputPolicyTest {

    @Test
    void acceptsAndNormalizesBoundaryValues() {
        assertThat(UserInputPolicy.newStudentNo(" 123456 ")).isEqualTo("123456");
        assertThat(UserInputPolicy.newStudentNo("1".repeat(32))).hasSize(32);
        assertThat(UserInputPolicy.name(" 张三 ")).isEqualTo("张三");
        assertThat(UserInputPolicy.name("名".repeat(64))).hasSize(64);
        assertThat(UserInputPolicy.password("1".repeat(6))).hasSize(6);
        assertThat(UserInputPolicy.password("1".repeat(64))).hasSize(64);
        assertThat(UserInputPolicy.phone("1".repeat(64))).hasSize(64);
        assertThat(UserInputPolicy.college("院".repeat(128))).hasSize(128);
        assertThat(UserInputPolicy.qq("1".repeat(32))).hasSize(32);
    }

    @Test
    void rejectsInvalidNewAccountsNamesAndPasswords() {
        assertBadRequest(() -> UserInputPolicy.newStudentNo("12345"), "6 至 32 位纯数字");
        assertBadRequest(() -> UserInputPolicy.newStudentNo("1".repeat(33)), "6 至 32 位纯数字");
        assertBadRequest(() -> UserInputPolicy.newStudentNo("12345A"), "6 至 32 位纯数字");
        assertBadRequest(() -> UserInputPolicy.name("   "), "姓名不能为空");
        assertBadRequest(() -> UserInputPolicy.name("名".repeat(65)), "64");
        assertBadRequest(() -> UserInputPolicy.password("12345"), "6 至 64");
        assertBadRequest(() -> UserInputPolicy.password("      "), "6 至 64");
        assertBadRequest(() -> UserInputPolicy.password("1".repeat(65)), "6 至 64");
    }

    @Test
    void rejectsOversizedOptionalProfileFields() {
        assertBadRequest(() -> UserInputPolicy.phone("1".repeat(65)), "联系方式不能超过 64");
        assertBadRequest(() -> UserInputPolicy.college("院".repeat(129)), "学院不能超过 128");
        assertBadRequest(() -> UserInputPolicy.qq("1".repeat(33)), "QQ不能超过 32");
    }

    @Test
    void defaultPasswordRequiresAtLeastSixAccountDigits() {
        assertThat(UserInputPolicy.defaultPassword("1004231224")).isEqualTo("231224");
        assertBadRequest(() -> UserInputPolicy.defaultPassword("12345"), "手动输入");
    }

    private void assertBadRequest(Runnable action, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(message)
                .extracting(throwable -> ((ApiException) throwable).status().value())
                .isEqualTo(400);
    }
}
