package com.ca.attendance.repair;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RepairAgreementServiceTest {
    private final RepairAgreementService service = new RepairAgreementService();

    @Test
    void personalAgreementRetainsOriginalClausesNumberingTableAndManualSignatures() throws Exception {
        var document = service.generate(item("PERSONAL_DEVICE", "测试送修人", "测试维修员"));
        String html = new String(document.bytes(), StandardCharsets.UTF_8);
        assertTrue(document.filename().startsWith("维修协议_"));
        assertEquals(2, occurrences(html, "class=\"sheet\""));
        assertEquals(4, occurrences(html, "<ol>"));
        assertEquals(21, occurrences(html, "<li>"));
        assertEquals(8, occurrences(html, "<tr>"));
        assertEquals(3, occurrences(html, "此处签名"));
        assertTrue(html.contains("□清灰、换硅脂（拆机）"));
        assertTrue(html.contains("JXWX-TEST-001"));
        assertTrue(html.contains("测试送修人"));
        assertTrue(html.contains("测试维修员"));
        assertTrue(html.contains("2026 年 10 月 14 日"));
        assertTrue(html.contains("window.print()"));
        assertFalse(html.contains("summary-grid"));
        assertFalse(html.contains("确认：已确认"));
        assertFalse(html.contains("本地离线值班后台生成"));
        assertOriginalClauses(html, "personal");
    }

    @Test
    void deviceAgreementKeepsOnlyOneCopyOfTheOriginalHalfSheet() throws Exception {
        String html = html(item("PUBLIC_DEVICE", "测试送修人", "测试维修员"));
        assertEquals(1, occurrences(html, "class=\"sheet device-sheet\""));
        assertEquals(1, occurrences(html, "class=\"device-copy\""));
        assertEquals(1, occurrences(html, "故障维修免责声明："));
        assertEquals(1, occurrences(html, "测试故障"));
        assertEquals(2, occurrences(html, "此处签名"));
        assertFalse(html.contains("赔偿比例"));
        assertOriginalClauses(html, "device");
    }

    @Test
    void fillsEscapedValuesOnceWithoutTreatingUserTextAsTemplateFields() {
        String html = html(item("PERSONAL_DEVICE", "O'Brien <script>{{PHONE}}", "<img src=x onerror=alert(1)>"));
        assertTrue(html.contains("O&#39;Brien &lt;script&gt;{{PHONE}}"));
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<img"));
        assertFalse(html.contains("http://"));
        assertFalse(html.contains("https://"));
    }

    private void assertOriginalClauses(String html, String name) throws Exception {
        String clauses = new ClassPathResource("repair-agreements/" + name + "-clauses.txt").getContentAsString(StandardCharsets.UTF_8);
        for (String clause : clauses.lines().toList()) assertTrue(html.contains(clause), clause);
    }

    private String html(RepairCaseItem item) {
        return new String(service.generate(item).bytes(), StandardCharsets.UTF_8);
    }

    private static int occurrences(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }

    private RepairCaseItem item(String type, String owner, String handler) {
        LocalDateTime date = LocalDateTime.of(2026, 10, 14, 14, 0);
        return new RepairCaseItem(1L, "JXWX-TEST-001", type, owner, "00000000000", null,
                "笔记本", "测试品牌", "测试型号", null, "测试附件", "测试故障", null,
                false, false, false, "REPAIRING", date, null, 1L, handler, null,
                null, null, null, date, date, null);
    }
}
