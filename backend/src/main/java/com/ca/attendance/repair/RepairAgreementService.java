package com.ca.attendance.repair;

import com.ca.attendance.common.DownloadFilename;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class RepairAgreementService {
    private static final Pattern FIELD = Pattern.compile("\\{\\{([A-Z_]+)}}");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy 年 MM 月 dd 日");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final String personalTemplate = loadTemplate("personal.html");
    private final String deviceTemplate = loadTemplate("device.html");

    public AgreementDocument generate(RepairCaseItem item) {
        boolean device = "PUBLIC_DEVICE".equals(item.agreementType());
        LocalDateTime received = item.receivedAt() == null ? LocalDateTime.now() : item.receivedAt();
        Map<String, String> values = Map.ofEntries(
                Map.entry("CASE_NO", item.caseNo()),
                Map.entry("OWNER", value(item.ownerName())),
                Map.entry("DEVICE", deviceText(item)),
                Map.entry("PHONE", value(item.ownerPhone())),
                Map.entry("PURCHASE", "　"),
                Map.entry("ACCESSORIES", value(item.accessories())),
                Map.entry("FAULT", value(item.faultDescription())),
                Map.entry("HANDLER", value(item.handlerName())),
                Map.entry("COMPLETED", item.completedAt() == null ? "" : item.completedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))),
                Map.entry("DATE", received.format(DATE)),
                Map.entry("TIME", received.format(TIME)));
        String template = device ? deviceTemplate : personalTemplate;
        Matcher matcher = FIELD.matcher(template);
        StringBuilder html = new StringBuilder();
        while (matcher.find()) {
            String replacement = values.get(matcher.group(1));
            if (replacement == null) throw new IllegalStateException("Unknown agreement template field");
            matcher.appendReplacement(html, Matcher.quoteReplacement(HtmlUtils.htmlEscape(replacement, StandardCharsets.UTF_8.name())));
        }
        matcher.appendTail(html);
        String filename = DownloadFilename.stem((device ? "免责协议_" : "维修协议_") + item.caseNo(), "维修协议") + ".html";
        return new AgreementDocument(filename, html.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String loadTemplate(String name) {
        try {
            return new ClassPathResource("repair-agreements/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Agreement template unavailable", exception);
        }
    }

    private static String value(String text) {
        return text == null || text.isBlank() ? "________" : text.trim();
    }

    private static String deviceText(RepairCaseItem item) {
        return Stream.of(item.deviceType(), item.deviceBrand(), item.deviceModel())
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim).reduce((a, b) -> a + " / " + b).orElse("________");
    }

    record AgreementDocument(String filename, byte[] bytes) {}
}
