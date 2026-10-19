package com.ca.attendance.user;

import com.ca.attendance.access.RolePermissionPolicy;
import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.ExcelImportPolicy;
import com.ca.attendance.common.ExcelImportTemplateGuide;
import com.ca.attendance.common.ExcelImportTemplateGuide.Item;
import com.ca.attendance.common.ExcelImportTemplateGuide.Section;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Service
public class MemberImportTemplateService {
    public ExportDocument generate() {
        return generate(LocalDate.now().getYear());
    }

    ExportDocument generate(int year) {
        RolePermissionPolicy.require(AuthContext.current().role(), RolePermissionPolicy.Permission.MEMBERS_MANAGE,
                "只有会长或管理员可以下载成员导入模板");
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            write(workbook, year);
            workbook.write(output);
            return new ExportDocument("成员名册导入模板.xlsx", output.toByteArray());
        } catch (IOException ex) {
            throw ApiException.badRequest("生成成员导入模板失败");
        }
    }

    private void write(Workbook workbook, int year) {
        Sheet data = workbook.createSheet("成员导入模板");
        data.setDisplayGridlines(false);
        String[] headers = {"学号", "姓名", "手机号", "学院", "年级", "QQ"};
        int[] widths = {42, 22, 22, 26, 18, 22};
        Font font = workbook.createFont();
        font.setFontName("Microsoft YaHei");
        font.setFontHeightInPoints((short) 11);
        CellStyle text = workbook.createCellStyle();
        text.setFont(font);
        text.setDataFormat(workbook.createDataFormat().getFormat("@"));
        text.setVerticalAlignment(VerticalAlignment.CENTER);
        CellStyle heading = workbook.createCellStyle();
        heading.cloneStyleFrom(text);
        heading.setAlignment(HorizontalAlignment.CENTER);
        heading.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font bold = workbook.createFont();
        bold.setFontName("Microsoft YaHei");
        bold.setFontHeightInPoints((short) 11);
        bold.setBold(true);
        heading.setFont(bold);
        Row header = data.createRow(0);
        header.setHeightInPoints(28);
        for (int col = 0; col < headers.length; col++) {
            header.createCell(col).setCellValue(headers[col]);
            header.getCell(col).setCellStyle(heading);
            data.setColumnWidth(col, widths[col] * 256);
            data.setDefaultColumnStyle(col, text);
        }
        for (int index = 1; index <= ExcelImportPolicy.MAX_DATA_ROWS; index++) {
            Row row = data.createRow(index);
            for (int col = 0; col < headers.length; col++) row.createCell(col).setCellStyle(text);
        }
        data.createFreezePane(0, 1);
        ExcelImportTemplateGuide.fitPrintWidth(data, false);
        Sheet grades = workbook.createSheet("年级选项");
        int minimum = year - 30;
        int maximum = year + 2;
        for (int value = minimum; value <= maximum; value++) grades.createRow(value - minimum).createCell(0).setCellValue(value + "级");
        Name gradeName = workbook.createName();
        gradeName.setNameName("MemberImportGrades");
        gradeName.setRefersToFormula("'年级选项'!$A$1:$A$33");
        workbook.setSheetHidden(1, true);
        DataValidationHelper validation = data.getDataValidationHelper();
        DataValidation dropdown = validation.createValidation(validation.createFormulaListConstraint("MemberImportGrades"),
                new CellRangeAddressList(1, ExcelImportPolicy.MAX_DATA_ROWS, 4, 4));
        dropdown.setEmptyCellAllowed(true);
        dropdown.setShowErrorBox(false);
        dropdown.setSuppressDropDownArrow(true);
        data.addValidationData(dropdown);

        ExcelImportTemplateGuide.write(workbook, "成员名册导入说明",
                "先在第一张表填写实际成员，上传校验并核对新增、更新和警告，最后确认导入。模板按下载年份生成，本次年级范围为" + minimum + "级—" + maximum + "级。", List.of(
                        new Section("一、填写规则", List.of(
                                new Item("填写位置", "第一张表", "在“成员导入模板”第2—3001行填写，一行一人。保留第1行六列表头，不添加说明行。"),
                                new Item("学号", "必填", "新账号为6—32位纯数字，同一文件不能重复。以文本填写并保留前导零；历史账号保持原学号。"),
                                new Item("姓名", "必填", "最多64个字符。已有学号重导入时更新姓名。"),
                                new Item("手机号", "可选", "最多64个字符。已有账号留空保留原值。"),
                                new Item("学院", "可选", "最多128个字符。已有账号留空保留原值。"),
                                new Item("年级", "可选", "可从下拉选择，也可填写四位年份或四位年份加“级”，如" + year + "或" + year + "级。系统按导入时的年份校验范围。"),
                                new Item("QQ", "可选", "最多32个字符，按文本填写。已有账号留空保留原值。")
                        )),
                        new Section("二、导入后会发生什么", List.of(
                                new Item("新学号", "新增普通成员", "创建为启用的普通成员，初始密码为学号后六位，首次登录必须修改。"),
                                new Item("已有学号", "更新基础资料", "按学号更新姓名及已填写的可选资料，不改变角色、账号状态和密码。"),
                                new Item("可选项留空", "已有资料保留", "已有账号的手机号、学院、年级和QQ留空不会清除原资料；新账号留空则不填写。"),
                                new Item("会长导入管理员行", "跳过并提示", "管理员所在行不作修改，其余行完整校验后整批写入。"),
                                new Item("其他校验错误", "整批不写入", "重复学号、格式等任一错误均需修正后重新校验，不会只导入部分成员。")
                        )),
                        new Section("三、导入前核对", List.of(
                                new Item("粘贴方式", "仅粘贴值", "首表3000行已设为文本。源数据也应为文本，核对长学号和前导零；已经丢失的数字需从原始资料重新填写。"),
                                new Item("文件限制", "5 MB / 3000行", "只在首表第2—3001行填写，中间空白行也占行号范围。删除范围外多余行。"),
                                new Item("确认前", "核对新增与更新", "上传后查看校验预览，核对差异和跳过警告。文件或系统成员资料变化后重新校验。"),
                                new Item("填写示例", "仅供参照", "示例表姓名和学号均为虚构，系统不读取该表。首表保持空白，不会误导入示例。"),
                                new Item("跨年使用", "重新下载", "年级选项按下载时年份生成。跨年后重新下载，以当前系统校验为准。")
                        ))
                ));
        ExcelImportTemplateGuide.examples(workbook, headers, List.of(
                List.of("000001", "示例成员甲", "00000000000", "示例学院", year + "级", "000000001"),
                List.of("00123456789012345678901234567890", "示例成员乙", "", "示例学院", String.valueOf(year - 1), "")
        ), "示例均为虚构，系统只读取第一张表。学号、手机号、QQ按文本保留前导零；可选项留空时，已有账号保留原资料，新账号不填写。");
        workbook.setActiveSheet(0);
    }

    public record ExportDocument(String filename, byte[] bytes) {}
}
