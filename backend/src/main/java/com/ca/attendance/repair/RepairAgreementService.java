package com.ca.attendance.repair;

import com.ca.attendance.common.DownloadFilename;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.ca.attendance.repair.RepairDisplayText.agreementType;
import static com.ca.attendance.repair.RepairDisplayText.status;
import static com.ca.attendance.repair.RepairDisplayText.time;
import static com.ca.attendance.repair.RepairDisplayText.valueOrDash;

@Service
public class RepairAgreementService {
    private static final DateTimeFormatter AGREEMENT_DATE = DateTimeFormatter.ofPattern("yyyy 年 MM 月 dd 日");
    private static final DateTimeFormatter AGREEMENT_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String REPAIR_AGREEMENT_TEXT = """
            协议编号JXWX2024（1）    涂改无效
            中国地质大学（北京）计算机协会个人电脑维修协议书

            亲爱的同学和老师，感谢您能信任本协会并让我们帮助您维护、修理您的个人计算机。为了更好的为您服务和有效地维护您的权利，请您在本协会维修前，仔细阅读并确认以下细则（以下条目均是为在协会指定义务维修时间内所遵循的条例）

            委托须知：
            本协会维修服务不收取任何人工服务费用。
            在计算机维护过程中造成的本协会内消耗品的消耗等和易损品损坏等不需要您支付额外费用。
            数据无价，无论您委托本协会进行何种服务都请提前备份好重要数据，如经提醒后仍没备份数据，则在维修期间（更换、升级系统，系统重装等）导致的数据丢失，本协会概不负责。
            由于数据特性易损，在本协会协助您进行数据备份或恢复的相关操作中产生的数据损坏将由您个人承担全部结果。
            请提前使用计协维修预约小程序进行预约，按照预约时间前往窗口并填写维修记录单，签署维修协议书。请您携带好维修所需的计算机配件，如电源适配器等。请您保持设备干净整洁。

            维修过程中：
            您计算机中的某些软件可能导致您的计算机发生蓝屏等故障，在处理此类问题中可能需要清除您的个人数据，这将在您的许可下进行。
            由于硬件升级或损坏需要购买相关配件的，本协会可以提供购买建议，请您自行购备，本协会不会与您产生任何交易。
            本协会不会以任何形式存储、传播您的个人数据。在维修过程中由计算机病毒等客观不可抗因素产生的数据泄露本协会概不负责。
            请您在送修时详细跟本协会窗口人员登记您留在本窗口的全部配件，如因您的疏忽未详细登记的配件如有遗失，本协会概不负责。
            本协会不保证一定能够解决您的电脑故障，若已确认本协会无法维修，将联系您取回。
            本协会作为非盈利组织，硅脂等耗材的品质能达到不低于平均水准，如您需要更加高级的耗材请您自备，由此产生的费用由您自行承担。
            如您认为维修员可能存在不当操作，请您自行录像留证并及时向值班组长报告。

            维修完成后：
            请您在当天值班结束（18：00）之前取回您委托的设备（超时维修的设备除外），若设备滞留窗口导致设备丢失或损坏，本协会概不负责。
            离开时请检查自己的计算机配件，如：鼠标，键盘膜，电源线等。如您忘记携带请在值班结束前领取，值班结束后若发生丢失或损坏本协会概不负责。
            本协会不提供保修服务。对于维修完成的设备，取走前请仔细检查设备的外观和运行情况，并签字确认。对于未能解决的问题，请仔细检查设备是否还原回原故障，并签字确认。本协会不对签字确认后的设备负责。

            赔偿条款：
            若是在我们维修过程中，由于维修员的不当操作对您的设备造成不可逆的损坏并已经影响正常使用，我们将按照以下细则赔偿您的损失。单笔赔付金额不超过 500 元（如果超过按照 500 元进行赔付）。
            对于已经签字完成的维修单，不在赔付范围之内。
            在我们维修过程中，维修员的正常拆解操作也可能对设备外观造成不可逆的损坏，对于不影响正常使用的划痕和弯折，我们不给予赔偿。
            若您曾对设备进行过拆解和维修，由此产生的继生损坏将由您自行承担。或是若谎报，瞒报，少报计算机情况，如：设备短路，进水等，因此造成的损失由您自行承担，且对维修员的健康安全造成威胁的，也需要您承担相关责任和后果。
            对于进水，不开机，外观严重破损，使用液金散热的设备或是设备使用环境恶劣，一般情况下我们建议您寻找第三方进行维修，若您委托维修员进行清灰等其它操作导致设备出现新的或是原本未显现的故障，您将对此承担全部后果。
            对于维修员操作不当的主张需要您提供相关证据，针对符合赔偿条款的设备，需要提供维修方开具的发票和关于故障原因的详细描述并提供在机器每次转手时对疑似故障的部件进行的拍照留证，对于出现二次损坏的设备我们将拒绝赔偿。
            设备尚在保修期内，因为维修操作失去保修， 您将对此承担全部后果。

            赔偿比例：
            项目              需要维修    需要更换
            键盘、触摸板       40%         25%
            屏幕              25%         15%
            内存条            35%         25%
            主板（包括CPU、显卡）20%       10%
            散热模组          50%         30%
            硬盘              25%         10%
            外壳              50%         30%

            维护免责声明：
            本协会有指定的值班时间，非值班时间不进行无偿维修并有权拒绝您的维修请求。由您个人请求的、非工作时间的维修工作造成的损失由您本人和维修人员协商共同承担。

            本人已仔细阅读此维修协议书，并同意遵守此协议书上的所有内容：        此处签名
            设备型号：        购买时间：        联系方式：
            委托目的：□清灰、换硅脂（拆机） □重装系统、硬盘分区 □软件安装(安装新软件如：office)
            携带配件：________________________________________
            维修人：      此处签名
            已完成：      此处签名      ；
            日期：                    年           月    日；    时间：    :
            本协议最终解释权归中国地质大学（北京）计算机协会所有
            """;
    private static final String DISCLAIMER_AGREEMENT_TEXT = """
            协议编号JXWX2024（2）    涂改无效
            中国地质大学（北京）计算机协会设备维修协议书

            故障维修免责声明：
            在任何情况下，无论因何种原因，中国地质大学（北京）计算机协会及维修人均不承担对于送修设备在维修过程中的任何间接、直接、特别或附带损失责任。

            本人已仔细阅读此维修协议书，并同意遵守此协议书上的所有内容：          此处签名
            设备型号：_______________________________购买时间：____________________________
            联系方式：_______________________________故障类型：____________________________
            维修人：      此处签名
            日期：                    年           月    日；    时间：    :
            本协议最终解释权归中国地质大学（北京）计算机协会所有
            """;

    AgreementDocument generate(RepairCaseItem item) {
        String filename = DownloadFilename.stem(
                agreementType(item.agreementType()) + "_" + item.caseNo(),
                "维修协议"
        ) + ".html";
        return new AgreementDocument(filename, agreementHtml(item).getBytes(StandardCharsets.UTF_8));
    }

    private String agreementHtml(RepairCaseItem item) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>维修协议预览</title>
                  <style>
                    :root {
                      --ink: #142538;
                      --muted: #61788d;
                      --line: #b9d3e9;
                      --soft: #eef7ff;
                      --panel: #f7fbff;
                      --brand: #1f6fb2;
                      --brand-dark: #144b82;
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      color: var(--ink);
                      background: #e9f3fb;
                      font-family: "SimSun", "Songti SC", "Microsoft YaHei", serif;
                      -webkit-print-color-adjust: exact;
                      print-color-adjust: exact;
                    }
                    .paper {
                      position: relative;
                      width: 210mm;
                      min-height: 297mm;
                      margin: 18px auto;
                      padding: 16mm 17mm 18mm;
                      background: #fff;
                      border: 1px solid #d7e6f2;
                      box-shadow: 0 18px 48px rgba(30, 87, 132, .18);
                    }
                    .paper::before {
                      content: "";
                      position: absolute;
                      inset: 0 auto 0 0;
                      width: 5mm;
                      background: linear-gradient(180deg, #dff0ff, #f8fcff 48%, #d9ecff);
                    }
                    .doc-head {
                      display: grid;
                      grid-template-columns: 1fr auto;
                      gap: 18px;
                      align-items: start;
                      padding-bottom: 12px;
                      border-bottom: 2px solid var(--brand);
                    }
                    .association {
                      margin: 0 0 6px;
                      color: var(--brand-dark);
                      font-family: "Microsoft YaHei", "Segoe UI", sans-serif;
                      font-size: 13px;
                      font-weight: 700;
                    }
                    h1 {
                      margin: 0;
                      font-family: "Microsoft YaHei", "Segoe UI", sans-serif;
                      font-size: 25px;
                      line-height: 1.25;
                      letter-spacing: 0;
                    }
                    .doc-subtitle {
                      margin: 8px 0 0;
                      color: var(--muted);
                      font-size: 13px;
                    }
                    .number-card {
                      min-width: 210px;
                      padding: 10px 12px;
                      border: 1px solid var(--line);
                      background: var(--soft);
                      text-align: right;
                    }
                    .number-card span {
                      display: block;
                      color: var(--muted);
                      font-size: 12px;
                    }
                    .number-card strong {
                      display: block;
                      margin-top: 4px;
                      color: var(--brand-dark);
                      font-family: Consolas, "Microsoft YaHei", monospace;
                      font-size: 18px;
                      letter-spacing: 0;
                    }
                    .status-line {
                      display: flex;
                      gap: 10px;
                      justify-content: flex-end;
                      margin-top: 7px;
                      color: var(--muted);
                      font-size: 12px;
                    }
                    .summary-grid {
                      display: grid;
                      grid-template-columns: repeat(3, 1fr);
                      border: 1px solid var(--line);
                      border-bottom: 0;
                      margin-top: 14px;
                    }
                    .summary-item {
                      min-height: 58px;
                      padding: 8px 10px;
                      border-right: 1px solid var(--line);
                      border-bottom: 1px solid var(--line);
                      background: #fff;
                    }
                    .summary-item:nth-child(3n) { border-right: 0; }
                    .summary-item span {
                      display: block;
                      color: var(--muted);
                      font-family: "Microsoft YaHei", "Segoe UI", sans-serif;
                      font-size: 12px;
                    }
                    .summary-item strong {
                      display: block;
                      margin-top: 5px;
                      font-size: 15px;
                      line-height: 1.45;
                      font-weight: 700;
                      word-break: break-word;
                    }
                    .confirm-row {
                      display: grid;
                      grid-template-columns: repeat(3, 1fr);
                      gap: 8px;
                      margin-top: 10px;
                    }
                    .confirm-row span {
                      padding: 7px 9px;
                      border: 1px solid var(--line);
                      background: var(--panel);
                      color: var(--brand-dark);
                      font-size: 12px;
                    }
                    .agreement-copy {
                      margin-top: 16px;
                      padding-top: 4px;
                    }
                    .agreement-copy h2 {
                      margin: 16px 0 7px;
                      padding: 5px 9px;
                      border-left: 4px solid var(--brand);
                      background: var(--soft);
                      font-family: "Microsoft YaHei", "Segoe UI", sans-serif;
                      font-size: 15px;
                    }
                    .agreement-copy p {
                      margin: 5px 0;
                      font-size: 13.5px;
                      line-height: 1.75;
                      text-align: justify;
                    }
                    .agreement-copy .fill-line {
                      margin: 8px 0;
                      padding: 7px 9px;
                      border: 1px dashed var(--line);
                      background: #fbfdff;
                      color: #102a43;
                      font-family: "Microsoft YaHei", "Segoe UI", sans-serif;
                      text-align: left;
                    }
                    .agreement-copy .mini-table {
                      white-space: pre-wrap;
                      font-family: Consolas, "Microsoft YaHei", monospace;
                      text-align: left;
                    }
                    .signature-grid {
                      display: grid;
                      grid-template-columns: 1fr 1fr;
                      gap: 22px;
                      margin-top: 22px;
                      page-break-inside: avoid;
                    }
                    .signature-box {
                      min-height: 86px;
                      padding: 12px 12px 8px;
                      border: 1px solid var(--line);
                      background: #fff;
                    }
                    .signature-box span {
                      display: block;
                      color: var(--muted);
                      font-size: 12px;
                    }
                    .signature-box strong {
                      display: block;
                      margin-top: 28px;
                      border-top: 1px solid #7895ad;
                      padding-top: 8px;
                      font-size: 14px;
                      font-weight: 400;
                    }
                    .doc-footer {
                      margin-top: 14px;
                      padding-top: 9px;
                      border-top: 1px solid var(--line);
                      color: var(--muted);
                      font-size: 12px;
                      text-align: center;
                    }
                    .print {
                      position: fixed;
                      right: 24px;
                      bottom: 24px;
                      min-height: 42px;
                      padding: 0 18px;
                      border: 0;
                      border-radius: 6px;
                      color: #fff;
                      background: var(--brand-dark);
                      box-shadow: 0 10px 26px rgba(20, 75, 130, .22);
                      cursor: pointer;
                    }
                    @page { size: A4; margin: 10mm; }
                    @media (max-width: 820px) {
                      .paper { width: calc(100% - 20px); min-height: auto; padding: 18px 18px 24px 24px; }
                      .doc-head, .summary-grid, .confirm-row, .signature-grid { grid-template-columns: 1fr; }
                      .number-card { text-align: left; }
                    }
                    @media print {
                      body { background: #fff; }
                      .paper { width: auto; min-height: auto; margin: 0; padding: 0; border: 0; box-shadow: none; }
                      .paper::before, .print { display: none; }
                    }
                  </style>
                </head>
                <body>
                <main class="paper">
                """);
        html.append("<header class=\"doc-head\"><div><p class=\"association\">中国地质大学（北京）计算机协会</p><h1>")
                .append(escape(documentTitle(item.agreementType())))
                .append("</h1><p class=\"doc-subtitle\">本协议由本地离线值班后台生成，打印后由送修人与协会经手人签字确认。</p></div>")
                .append("<aside class=\"number-card\"><span>协议编号 / 维修编号</span><strong>")
                .append(escape(item.caseNo()))
                .append("</strong><div class=\"status-line\"><span>")
                .append(escape(agreementType(item.agreementType())))
                .append("</span><span>")
                .append(escape(status(item.status())))
                .append("</span></div></aside></header>");

        html.append("<section class=\"summary-grid\">");
        appendSummaryItem(html, "送修人", item.ownerName());
        appendSummaryItem(html, "联系方式", item.ownerPhone());
        appendSummaryItem(html, "设备型号", deviceText(item));
        appendSummaryItem(html, "携带配件", item.accessories());
        appendSummaryItem(html, "故障类型", item.faultDescription());
        appendSummaryItem(html, "维修人", item.handlerName());
        appendSummaryItem(html, "接收时间", time(item.receivedAt()));
        appendSummaryItem(html, "完成时间", time(item.completedAt()));
        appendSummaryItem(html, "处理记录", item.serviceDescription());
        html.append("</section>");

        html.append("<section class=\"confirm-row\"><span>数据备份提醒：")
                .append(escape(boolText(item.dataBackupConfirmed())))
                .append("</span><span>维修风险确认：")
                .append(escape(boolText(item.riskAcknowledged())))
                .append("</span><span>隐私提示确认：")
                .append(escape(boolText(item.privacyAcknowledged())))
                .append("</span></section>");

        html.append("<section class=\"agreement-copy\">");
        appendAgreementCopy(html, filledAgreementCopy(item));
        html.append("</section>");

        html.append("""
                <section class="signature-grid">
                  <div class="signature-box"><span>送修人确认</span><strong>签字：</strong></div>
                  <div class="signature-box"><span>协会经手人确认</span><strong>签字：</strong></div>
                </section>
                <footer class="doc-footer">本协议最终解释权归中国地质大学（北京）计算机协会所有</footer>
                """);

        html.append("""
                </main>
                <button class="print" onclick="window.print()">打印协议</button>
                </body>
                </html>
                """);
        return html.toString();
    }

    private void appendSummaryItem(StringBuilder html, String label, String value) {
        html.append("<div class=\"summary-item\"><span>")
                .append(escape(label))
                .append("</span><strong>")
                .append(escape(valueOrDash(value)))
                .append("</strong></div>");
    }

    private void appendAgreementCopy(StringBuilder html, String copy) {
        String[] lines = copy.split("\\R");
        boolean inRatioTable = false;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("协议编号") || line.contains("协议书") || line.startsWith("本协议最终解释权")) {
                continue;
            }
            if (line.endsWith("：") && line.length() <= 12) {
                if (inRatioTable) {
                    html.append("</p>");
                    inRatioTable = false;
                }
                html.append("<h2>").append(escape(line)).append("</h2>");
                continue;
            }
            if (isRatioLine(line)) {
                if (!inRatioTable) {
                    html.append("<p class=\"mini-table\">");
                    inRatioTable = true;
                } else {
                    html.append("<br>");
                }
                html.append(escape(line));
                continue;
            }
            if (inRatioTable) {
                html.append("</p>");
                inRatioTable = false;
            }
            html.append("<p");
            if (isFillLine(line)) {
                html.append(" class=\"fill-line\"");
            }
            html.append(">")
                    .append(escape(line))
                    .append("</p>");
        }
        if (inRatioTable) {
            html.append("</p>");
        }
    }

    private boolean isRatioLine(String line) {
        return line.startsWith("项目")
                || line.startsWith("键盘")
                || line.startsWith("屏幕")
                || line.startsWith("内存")
                || line.startsWith("主板")
                || line.startsWith("散热")
                || line.startsWith("硬盘")
                || line.startsWith("外壳");
    }

    private boolean isFillLine(String line) {
        return line.startsWith("本人已仔细阅读")
                || line.startsWith("设备型号")
                || line.startsWith("联系方式")
                || line.startsWith("委托目的")
                || line.startsWith("携带配件")
                || line.startsWith("维修人")
                || line.startsWith("已完成")
                || line.startsWith("日期");
    }

    private String deviceText(RepairCaseItem item) {
        List<String> parts = new ArrayList<>();
        parts.add(item.deviceType());
        if (item.deviceBrand() != null && !item.deviceBrand().isBlank()) {
            parts.add(item.deviceBrand());
        }
        if (item.deviceModel() != null && !item.deviceModel().isBlank()) {
            parts.add(item.deviceModel());
        }
        return String.join(" / ", parts);
    }

    private String documentTitle(String type) {
        return "PUBLIC_DEVICE".equals(type)
                ? "计算机协会设备维修协议书"
                : "计算机协会个人电脑维修协议书";
    }

    private String agreementCopy(String type) {
        return "PUBLIC_DEVICE".equals(type) ? DISCLAIMER_AGREEMENT_TEXT : REPAIR_AGREEMENT_TEXT;
    }

    private String filledAgreementCopy(RepairCaseItem item) {
        String copy = agreementCopy(item.agreementType())
                .replace("协议编号JXWX2024（1）    涂改无效", "协议编号：" + item.caseNo() + "    涂改无效")
                .replace("协议编号JXWX2024（2）    涂改无效", "协议编号：" + item.caseNo() + "    涂改无效")
                .replace("维修人：      此处签名", "维修人：" + fieldValue(item.handlerName()) + "      签名：")
                .replace("日期：                    年           月    日；    时间：    :",
                        "日期：" + agreementDateText(item.receivedAt()) + "；    时间：" + agreementTimeText(item.receivedAt()));
        if ("PUBLIC_DEVICE".equals(item.agreementType())) {
            return copy
                    .replace("设备型号：_______________________________购买时间：____________________________",
                            "设备型号：" + fieldValue(deviceText(item)) + "    购买时间：________")
                    .replace("联系方式：_______________________________故障类型：____________________________",
                            "联系方式：" + fieldValue(item.ownerPhone()) + "    故障类型：" + fieldValue(item.faultDescription()));
        }
        return copy
                .replace("设备型号：        购买时间：        联系方式：",
                        "设备型号：" + fieldValue(deviceText(item)) + "    购买时间：________    联系方式：" + fieldValue(item.ownerPhone()))
                .replace("委托目的：□清灰、换硅脂（拆机） □重装系统、硬盘分区 □软件安装(安装新软件如：office)",
                        "委托目的：" + fieldValue(item.faultDescription()))
                .replace("携带配件：________________________________________", "携带配件：" + fieldValue(item.accessories()))
                .replace("已完成：      此处签名      ；", "已完成：" + completedText(item) + "      签名：");
    }

    private String fieldValue(String value) {
        return value == null || value.isBlank() ? "________" : value.trim();
    }

    private String completedText(RepairCaseItem item) {
        return item.completedAt() == null ? "未完成" : time(item.completedAt());
    }

    private String agreementDateText(LocalDateTime value) {
        return (value == null ? LocalDateTime.now() : value).format(AGREEMENT_DATE);
    }

    private String agreementTimeText(LocalDateTime value) {
        return (value == null ? LocalDateTime.now() : value).format(AGREEMENT_TIME);
    }

    private String boolText(boolean value) {
        return value ? "已确认" : "未确认";
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, StandardCharsets.UTF_8.name());
    }

    record AgreementDocument(String filename, byte[] bytes) {
    }
}
