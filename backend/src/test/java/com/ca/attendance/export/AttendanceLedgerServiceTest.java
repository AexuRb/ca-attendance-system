package com.ca.attendance.export;
import com.ca.attendance.auth.*;
import com.ca.attendance.common.*;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.settings.DutyWeekdayService;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class AttendanceLedgerServiceTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final DutyWeekdayService weekdays=mock(DutyWeekdayService.class);final OperationLogService logs=mock(OperationLogService.class);
 final AttendanceLedgerService service=new AttendanceLedgerService(jdbc,weekdays,logs,new ObjectMapper());
 @BeforeEach void setup(){auth(Role.ADMIN);when(weekdays.list()).thenReturn(List.of(Map.of("weekday",1,"enabled",true),Map.of("weekday",2,"enabled",true),Map.of("weekday",5,"enabled",true)));when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());}
 @AfterEach void cleanup(){AuthContext.clear();}
 void auth(Role role){AuthContext.set(new AuthUser(1,"900001","示例操作员",role,Instant.now().plusSeconds(300)));}
 Map<String,Object> record(String number,String date,String status,String hours){return Map.of("studentNo",number,"name","示例成员","dutyDate",date,"status",status,"hours",hours);}
 AttendanceLedgerService.Request req(String kind){return new AttendanceLedgerService.Request(kind,kind.equals("weekly")?"2026-10-19":"2026-10-01",kind.equals("weekly")?"2026-10-25":"2026-10-31","");}
 @Test void dailyValidHoursAggregateAndNonDutyDaysAreOmittedWhileZeroAndMissingRemainDistinct(){
  var data=List.of(record("000001","2026-10-19","VALID","1.25"),record("000001","2026-10-19","VALID","2.50"),record("000001","2026-10-20","PENDING","99"),record("000002","2026-10-20","VALID","0"),record("000003","2026-10-23","INCOMPLETE","90"),record("000004","2026-10-21","VALID","8"));
  var d=AttendanceLedgerService.build("weekly",LocalDate.parse("2026-10-19"),LocalDate.parse("2026-10-25"),List.of(1,2,5),data);
  assertEquals(3,d.totalRows());assertEquals(5,d.sourceRows());assertEquals(4,d.columns().size());
  assertEquals(3.75,d.rows().getFirst().values().getFirst().doubleValue());assertNull(d.rows().getFirst().values().get(1));assertEquals(3.75,d.rows().getFirst().values().getLast().doubleValue());
  assertEquals(0,d.rows().get(1).values().get(1).signum());assertNull(d.rows().get(1).values().getFirst());assertTrue(d.rows().get(2).values().subList(0,3).stream().allMatch(Objects::isNull));
 }
 @Test void monthsAndArbitrarySemesterRangesUseChronologicalPartialWeeksWithoutOutsideDates(){
  for(var range:List.of(List.of("monthly","2026-10-01","2026-10-31"),List.of("semester","2026-10-13","2026-11-03"))){
   var d=AttendanceLedgerService.build(range.get(0),LocalDate.parse(range.get(1)),LocalDate.parse(range.get(2)),List.of(1,2,5),List.of());
   for(var c:d.columns())if(c.kind().equals("date")){assertTrue(c.date().compareTo(range.get(1))>=0&&c.date().compareTo(range.get(2))<=0);assertTrue(List.of(1,2,5).contains(LocalDate.parse(c.date()).getDayOfWeek().getValue()));}
   assertEquals(d.groups().size(),d.columns().stream().filter(c->c.kind().equals("subtotal")).count());
  }
 }
 @Test void workbookContainsMergedHeadersTextIdsFrozenIdentityAndLiveFormulasWithCorrectCaches() throws Exception {
  for(String kind:List.of("weekly","monthly","semester")){
   var r=req(kind);var d=AttendanceLedgerService.build(kind,LocalDate.parse(r.from()),LocalDate.parse(r.to()),List.of(1,2,5),List.of(record("00123456789012345678901234567890","2026-10-19","VALID","4"),record("000002","2026-10-20","VALID","0")));
   byte[] bytes=AttendanceLedgerWorkbook.write(d);try(var book=WorkbookFactory.create(new ByteArrayInputStream(bytes))){var s=book.getSheetAt(0);int first=kind.equals("weekly")?4:5;assertEquals(2,s.getPaneInformation().getVerticalSplitPosition());assertEquals(first,s.getPaneInformation().getHorizontalSplitPosition());assertEquals(CellType.STRING,s.getRow(first+1).getCell(0).getCellType());assertEquals("00123456789012345678901234567890",s.getRow(first+1).getCell(0).getStringCellValue());var total=s.getRow(first+1).getCell(d.columns().size()+1);assertEquals(CellType.FORMULA,total.getCellType());assertEquals(4,total.getNumericCellValue());assertTrue(total.getCellFormula().startsWith("SUM("));assertTrue(s.getNumMergedRegions()>0);assertEquals(1,s.getSheetConditionalFormatting().getNumConditionalFormattings());new org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator((org.apache.poi.xssf.usermodel.XSSFWorkbook)book).evaluateAll();assertEquals(4,total.getNumericCellValue());}
   String dir=System.getProperty("ledger.preview.dir");if(dir!=null){Files.createDirectories(Path.of(dir));Files.write(Path.of(dir,kind+".xlsx"),bytes);}
  }
 }
 @Test void previewIsReadOnlyAndDownloadsRejectStaleSettingsOrData(){
  var r=req("weekly");var p=service.preview(r);verifyNoInteractions(logs);when(weekdays.list()).thenReturn(List.of(Map.of("weekday",3,"enabled",true)));
  assertEquals(409,assertThrows(ApiException.class,()->service.export(new AttendanceLedgerService.Request(r.period(),r.from(),r.to(),p.revision()))).status().value());verifyNoInteractions(logs);
 }
 @Test void invalidPeriodsDatesAndOversizedRangesAreRejected(){for(var r:List.of(new AttendanceLedgerService.Request("bad","2026-10-01","2026-10-31",""),new AttendanceLedgerService.Request("weekly","2026-10-20","2026-10-25",""),new AttendanceLedgerService.Request("monthly","2026-10-02","2026-10-31",""),new AttendanceLedgerService.Request("semester","2026-11-01","2026-10-01","")))assertThrows(ApiException.class,()->service.preview(r));verifyNoInteractions(jdbc,logs);}
 @ParameterizedTest @EnumSource(Role.class) void directPreviewAndExportEnforceRoles(Role role)throws Exception{
  auth(role);var mvc=MockMvcBuilders.standaloneSetup(new AttendanceLedgerController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();String body=new ObjectMapper().writeValueAsString(req("weekly"));boolean allowed=role==Role.ADMIN||role==Role.PRESIDENT;
  mvc.perform(post("/api/exports/attendance/templates/preview").contentType("application/json").content(body)).andExpect(allowed?status().isOk():status().isForbidden());
  if(!allowed){mvc.perform(post("/api/exports/attendance/templates/excel").contentType("application/json").content(body)).andExpect(status().isForbidden());verifyNoInteractions(jdbc,logs);}
 }
 @Test void anonymousRequestsFailBeforeAnyReading(){AuthContext.clear();assertEquals(401,assertThrows(ApiException.class,()->service.preview(req("weekly"))).status().value());verifyNoInteractions(jdbc,weekdays,logs);}
}
