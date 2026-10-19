package com.ca.attendance.export;

import com.ca.attendance.access.RolePermissionPolicy;
import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.ExportRowLimit;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.settings.DutyWeekdayService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.*;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AttendanceLedgerService {
    private final JdbcTemplate jdbc;
    private final DutyWeekdayService weekdays;
    private final OperationLogService logs;
    private final ObjectMapper json;
    public AttendanceLedgerService(JdbcTemplate jdbc,DutyWeekdayService weekdays,OperationLogService logs,ObjectMapper json) {
        this.jdbc=jdbc;this.weekdays=weekdays;this.logs=logs;this.json=json;
    }
    @Transactional(readOnly=true)
    public Preview preview(Request request) {
        var model=prepare(request);
        return new Preview(model.document(),model.revision(),model.document().rows().stream().limit(50).toList());
    }
    @Transactional
    public ExportFile export(Request request) {
        var prepared=prepare(request);
        if(request.revision()==null||!request.revision().equals(prepared.revision()))throw ApiException.conflict("数据或值班日设置已变化，请重新生成预览");
        var doc=prepared.document();
        byte[] bytes=AttendanceLedgerWorkbook.write(doc);
        String name="值班记录_"+label(doc.period())+"_"+doc.from()+"_"+doc.to()+".xlsx";
        logs.logExport("ATTENDANCE_LEDGER_"+doc.period().toUpperCase(Locale.ROOT),"值班记录·"+label(doc.period()),
                Map.of("from",doc.from(),"to",doc.to(),"weekdays",doc.weekdays()),doc.totalRows(),name);
        return new ExportFile(name,bytes);
    }
    private Prepared prepare(Request request) {
        RolePermissionPolicy.require(AuthContext.current().role(),RolePermissionPolicy.Permission.CUSTOM_EXPORT,"当前账号无权导出值班记录");
        if(request==null||!Set.of("weekly","monthly","semester").contains(Objects.toString(request.period(),"")))throw ApiException.badRequest("请选择周、月或学期");
        LocalDate from,to;
        try {from=LocalDate.parse(request.from());to=LocalDate.parse(request.to());}
        catch(Exception ignored){throw ApiException.badRequest("请选择有效的开始和结束日期");}
        if(from.isAfter(to)||ChronoUnit.DAYS.between(from,to)>3660)throw ApiException.badRequest("日期范围无效或过大，请缩小范围");
        if(request.period().equals("weekly")&&(from.getDayOfWeek()!=DayOfWeek.MONDAY||!to.equals(from.plusDays(6))))throw ApiException.badRequest("周模板请选择完整的周一至周日");
        if(request.period().equals("monthly")&&(from.getDayOfMonth()!=1||!to.equals(from.withDayOfMonth(from.lengthOfMonth()))))throw ApiException.badRequest("月模板请选择完整月份");
        var enabled=weekdays.list().stream().filter(row->Boolean.TRUE.equals(row.get("enabled")))
                .map(row->((Number)row.get("weekday")).intValue()).sorted().toList();
        var records=jdbc.queryForList("""
                SELECT student_no_snapshot AS studentNo, name_snapshot AS name, duty_date AS dutyDate,
                       effective_status AS status, valid_hours AS hours
                FROM attendance_records WHERE duty_date >= ? AND duty_date <= ?
                ORDER BY duty_date, id LIMIT 50001
                """,from.toString(),to.toString());
        ExportRowLimit.requireWithinLimit(records.size());
        var doc=build(request.period(),from,to,enabled,records);
        try {return new Prepared(doc,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(doc))));}
        catch(ApiException error){throw error;}catch(Exception ignored){throw ApiException.badRequest("生成预览失败，请重试");}
    }
    static Document build(String period,LocalDate from,LocalDate to,List<Integer> weekdays,List<Map<String,Object>> records) {
        List<Column> columns=new ArrayList<>();List<Group> groups=new ArrayList<>();
        LocalDate monday=from.minusDays(from.getDayOfWeek().getValue()-1);int week=0;
        for(LocalDate start=monday;!start.isAfter(to);start=start.plusWeeks(1)) {
            week++;
            LocalDate lo=start.isBefore(from)?from:start,hi=start.plusDays(6).isAfter(to)?to:start.plusDays(6);
            int first=columns.size();
            for(LocalDate date=lo;!date.isAfter(hi);date=date.plusDays(1))if(weekdays.contains(date.getDayOfWeek().getValue()))
                columns.add(new Column("date","周"+"一二三四五六日".charAt(date.getDayOfWeek().getValue()-1)+"（"+shortDate(date)+"）",date.toString()));
            if(first==columns.size())continue;
            if(!period.equals("weekly"))columns.add(new Column("subtotal","周小计",""));
            groups.add(new Group("第"+week+"周（"+shortDate(lo)+"—"+shortDate(hi)+"）",first,columns.size()-1,lo.toString(),hi.toString()));
        }
        columns.add(new Column("total","总计",""));
        Map<String,Member> members=new TreeMap<>();int included=0;
        for(var row:records) {
            LocalDate date=LocalDate.parse(row.get("dutyDate").toString());
            if(date.isBefore(from)||date.isAfter(to)||!weekdays.contains(date.getDayOfWeek().getValue()))continue;
            included++;
            String number=row.get("studentNo").toString();Member member=members.computeIfAbsent(number,key->new Member());
            member.name=Objects.toString(row.get("name"),"");
            if("VALID".equals(row.get("status")))member.hours.merge(date,new BigDecimal(Objects.toString(row.get("hours"),"0")),BigDecimal::add);
        }
        if((long)columns.size()*members.size()>1_000_000)throw ApiException.badRequest("表格单元格过多，请缩小日期范围");
        List<MemberRow> rows=new ArrayList<>();
        for(var entry:members.entrySet()) {
            List<BigDecimal> values=new ArrayList<>();BigDecimal total=BigDecimal.ZERO,subtotal=BigDecimal.ZERO;boolean hasValue=false;
            for(var column:columns) {
                if(column.kind().equals("date")) {
                    BigDecimal raw=entry.getValue().hours.get(LocalDate.parse(column.date()));
                    BigDecimal hours=raw==null?null:raw.setScale(2,RoundingMode.HALF_UP);values.add(hours);
                    if(hours!=null){subtotal=subtotal.add(hours);total=total.add(hours);hasValue=true;}
                } else if(column.kind().equals("subtotal")) {
                    values.add(hasValue?subtotal:null);subtotal=BigDecimal.ZERO;hasValue=false;
                } else values.add(total);
            }
            rows.add(new MemberRow(entry.getKey(),entry.getValue().name,Collections.unmodifiableList(values)));
        }
        return new Document(period,from.toString(),to.toString(),List.copyOf(weekdays),List.copyOf(columns),List.copyOf(groups),List.copyOf(rows),members.size(),included);
    }
    private static String shortDate(LocalDate date){return "%02d/%02d".formatted(date.getMonthValue(),date.getDayOfMonth());}
    static String label(String period){return switch(period){case "weekly"->"周";case "monthly"->"月";default->"学期";};}
    private static final class Member {String name="";final Map<LocalDate,BigDecimal> hours=new HashMap<>();}
    public record Request(String period,String from,String to,String revision){}
    public record Column(String kind,String label,String date){}
    public record Group(String label,int start,int end,String from,String to){}
    public record MemberRow(String studentNo,String name,List<BigDecimal> values){}
    public record Document(String period,String from,String to,List<Integer> weekdays,List<Column> columns,List<Group> groups,
                           List<MemberRow> rows,int totalRows,int sourceRows){}
    // Metadata excludes the full row list; the preview contains the first 50 members only.
    public record Preview(String period,String from,String to,List<Integer> weekdays,List<Column> columns,List<Group> groups,
                          int totalRows,int sourceRows,String revision,List<MemberRow> rows) {
        Preview(Document d,String revision,List<MemberRow> rows){this(d.period(),d.from(),d.to(),d.weekdays(),d.columns(),d.groups(),d.totalRows(),d.sourceRows(),revision,rows);}
    }
    public record ExportFile(String filename,byte[] bytes){}
    private record Prepared(Document document,String revision){}
}
