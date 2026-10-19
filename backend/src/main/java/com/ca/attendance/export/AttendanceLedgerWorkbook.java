package com.ca.attendance.export;

import com.ca.attendance.common.ApiException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;

final class AttendanceLedgerWorkbook {
    private AttendanceLedgerWorkbook(){}
    static byte[] write(AttendanceLedgerService.Document doc) {
        var book=new SXSSFWorkbook(100);book.setCompressTempFiles(true);
        try(book;var output=new ByteArrayOutputStream()) {
            Sheet sheet=book.createSheet("值班记录");sheet.setDisplayGridlines(false);
            int last=doc.columns().size()+1;boolean weekly=doc.period().equals("weekly");int headerBottom=weekly?3:4,firstRow=headerBottom+1;
            CellStyle body=style(book,false,"FFFFFF"),header=style(book,true,"EEF2F6"),subtotal=style(book,true,"EDF2F7"),totalStyle=style(book,true,"DDE7F1");
            body.setDataFormat(book.createDataFormat().getFormat("0.##"));subtotal.setDataFormat(body.getDataFormat());totalStyle.setDataFormat(body.getDataFormat());
            CellStyle stripe=book.createCellStyle();stripe.cloneStyleFrom(body);fill(stripe,"F8FAFC");
            CellStyle text=book.createCellStyle();text.cloneStyleFrom(body);text.setDataFormat(book.createDataFormat().getFormat("@"));text.setAlignment(HorizontalAlignment.LEFT);
            CellStyle stripeText=book.createCellStyle();stripeText.cloneStyleFrom(text);fill(stripeText,"F8FAFC");
            CellStyle totalHeader=book.createCellStyle();totalHeader.cloneStyleFrom(header);fill(totalHeader,"DDE7F1");
            var title=sheet.createRow(0);title.setHeightInPoints(28);title.createCell(0).setCellValue("值班记录（"+AttendanceLedgerService.label(doc.period())+"）");
            CellStyle titleStyle=book.createCellStyle();Font titleFont=book.createFont();titleFont.setFontName("等线");titleFont.setFontHeightInPoints((short)15);titleFont.setBold(true);titleStyle.setFont(titleFont);title.getCell(0).setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0,0,0,Math.min(last,7)));
            var meta=sheet.createRow(1);meta.setHeightInPoints(22);meta.createCell(0).setCellValue(doc.from()+"—"+doc.to()+"    单位：小时");sheet.addMergedRegion(new CellRangeAddress(1,1,0,Math.min(last,7)));
            sheet.createRow(2).setHeightInPoints(6);
            for(int r=3;r<=headerBottom;r++) {Row row=sheet.createRow(r);row.setHeightInPoints(28);for(int c=0;c<=last;c++){Cell cell=row.createCell(c);cell.setCellStyle(header);}}
            sheet.getRow(3).getCell(0).setCellValue("学号");sheet.getRow(3).getCell(1).setCellValue("姓名");
            if(!weekly) {
                sheet.addMergedRegion(new CellRangeAddress(3,4,0,0));sheet.addMergedRegion(new CellRangeAddress(3,4,1,1));
                sheet.addMergedRegion(new CellRangeAddress(3,4,last,last));
                for(var group:doc.groups()) {
                    sheet.getRow(3).getCell(group.start()+2).setCellValue(group.label());
                    if(group.end()>group.start())sheet.addMergedRegion(new CellRangeAddress(3,3,group.start()+2,group.end()+2));
                }
            }
            for(int i=0;i<doc.columns().size();i++) {
                var column=doc.columns().get(i);int c=i+2;
                sheet.getRow(column.kind().equals("total")?3:headerBottom).getCell(c).setCellValue(column.label());
                sheet.setColumnWidth(c,(column.kind().equals("date")?16:12)*256);
            }
            sheet.setColumnWidth(0,18*256);sheet.setColumnWidth(1,16*256);
            for(int index=0;index<doc.rows().size();index++) {
                var person=doc.rows().get(index);Row row=sheet.createRow(firstRow+index);row.setHeightInPoints(Math.max(24,Math.max((person.studentNo().length()+17)/18,(person.name().length()+7)/8)*14));int excel=row.getRowNum()+1;
                row.createCell(0).setCellValue(person.studentNo());row.getCell(0).setCellStyle(index%2==0?text:stripeText);row.createCell(1).setCellValue(person.name());row.getCell(1).setCellStyle(index%2==0?text:stripeText);
                List<String> sums=new ArrayList<>();
                for(int i=0;i<doc.columns().size();i++) {
                    Cell cell=row.createCell(i+2);BigDecimal value=person.values().get(i);var column=doc.columns().get(i);
                    if(column.kind().equals("subtotal")) {
                        var group=doc.groups().stream().filter(g->g.end()==cell.getColumnIndex()-2).findFirst().orElseThrow();
                        String range=CellReference.convertNumToColString(group.start()+2)+excel+":"+CellReference.convertNumToColString(i+1)+excel;
                        cell.setCellFormula("IF(COUNT("+range+")=0,\"\",SUM("+range+"))");sums.add(cell.getAddress().formatAsString());
                    } else if(column.kind().equals("total")) {
                        cell.setCellFormula("SUM("+(weekly?(i==0?"0":"C"+excel+":"+CellReference.convertNumToColString(i+1)+excel):String.join(",",sums).isEmpty()?"0":String.join(",",sums))+")");
                    }
                    if(value!=null)cell.setCellValue(value.doubleValue());else if(!column.kind().equals("date"))cell.setCellValue("");
                    cell.setCellStyle(column.kind().equals("total")?totalStyle:column.kind().equals("subtotal")?subtotal:index%2==0?body:stripe);
                }
            }
            for(int r=3;r<=headerBottom;r++)sheet.getRow(r).getCell(last).setCellStyle(totalHeader);
            if(!doc.rows().isEmpty()) {
                CTDxf dxf=CTDxf.Factory.newInstance();var format=dxf.addNewNumFmt();format.setNumFmtId(1);format.setFormatCode("0");
                int dxfId=book.getXSSFWorkbook().getStylesSource().putDxf(dxf)-1;
                var cf=book.getXSSFWorkbook().getSheetAt(0).getCTWorksheet().addNewConditionalFormatting();
                cf.setSqref(List.of("C"+(firstRow+1)+":"+CellReference.convertNumToColString(last)+(firstRow+doc.rows().size())));
                var rule=cf.addNewCfRule();rule.setType(STCfType.EXPRESSION);rule.setDxfId(dxfId);rule.setPriority(1);
                rule.addFormula("AND(ISNUMBER(C"+(firstRow+1)+"),MOD(C"+(firstRow+1)+",1)=0)");
            }
            sheet.createFreezePane(2,firstRow);book.setForceFormulaRecalculation(true);
            book.write(output);return output.toByteArray();
        }catch(Exception ignored){throw ApiException.badRequest("生成值班记录 Excel 失败，请缩小范围后重试");}
        finally{book.dispose();}
    }
    private static void fill(CellStyle style,String hex){((XSSFCellStyle)style).setFillForegroundColor(new XSSFColor(java.util.HexFormat.of().parseHex(hex),null));style.setFillPattern(FillPatternType.SOLID_FOREGROUND);}
    private static CellStyle style(Workbook book,boolean bold,String color){
        CellStyle style=book.createCellStyle();Font font=book.createFont();font.setFontName("等线");font.setFontHeightInPoints((short)11);font.setBold(bold);style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);style.setWrapText(true);style.setAlignment(bold?HorizontalAlignment.CENTER:HorizontalAlignment.RIGHT);
        style.setBorderBottom(BorderStyle.THIN);style.setBorderTop(BorderStyle.THIN);style.setBorderLeft(BorderStyle.THIN);style.setBorderRight(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        if(!color.equals("FFFFFF"))fill(style,color);return style;
    }
}
