package com.ca.attendance.user;

import com.ca.attendance.common.ApiException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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
        assertThat(UserInputPolicy.phone(" 13800000000 ")).isEqualTo("13800000000");
        assertThat(UserInputPolicy.college("院".repeat(128))).hasSize(128);
        assertThat(UserInputPolicy.qq("1".repeat(20))).hasSize(20);
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
        assertBadRequest(() -> UserInputPolicy.phone("1".repeat(12)), "手机号不能超过 11");
        assertBadRequest(() -> UserInputPolicy.college("院".repeat(129)), "学院不能超过 128");
        assertBadRequest(() -> UserInputPolicy.qq("1".repeat(21)), "QQ不能超过 20");
    }

    @Test void contactFormatsRejectNonDigitsAndPreserveTwentyDigitQq() {
        for (String value : new String[]{"中文", "1380000000", "12800000000", "1380000000a", "１３８００００００００", "138 00000000"})
            assertThatThrownBy(() -> UserInputPolicy.phone(value)).isInstanceOf(ApiException.class);
        for (String value : new String[]{"中文", "1234", "012345", "123a45", "１２３４５", "123 45"})
            assertThatThrownBy(() -> UserInputPolicy.qq(value)).isInstanceOf(ApiException.class);
        assertThat(UserInputPolicy.phone(" ")).isNull();
        assertThat(UserInputPolicy.qq(null)).isNull();
        assertThat(UserInputPolicy.qq(" 12345678901234567890 ")).isEqualTo("12345678901234567890");
    }

    @Test
    void normalizesGradeWithinTheCurrentDynamicRange() {
        int currentYear = LocalDate.now().getYear();

        assertThat(UserInputPolicy.grade((currentYear - 30) + "级"))
                .isEqualTo((currentYear - 30) + "级");
        assertThat(UserInputPolicy.grade(String.valueOf(currentYear + 2)))
                .isEqualTo((currentYear + 2) + "级");
        assertBadRequest(() -> UserInputPolicy.grade((currentYear - 31) + "级"), "年级范围应为");
        assertBadRequest(() -> UserInputPolicy.grade((currentYear + 3) + "级"), "年级范围应为");
        assertBadRequest(() -> UserInputPolicy.grade("本科" + currentYear), "年级格式应为");
    }

    @Test
    void defaultPasswordRequiresAtLeastSixAccountDigits() {
        assertThat(UserInputPolicy.defaultPassword("9900000001")).isEqualTo("000001");
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
