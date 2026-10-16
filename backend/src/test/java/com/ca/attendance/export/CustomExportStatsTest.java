package com.ca.attendance.export;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.stats.StatsService;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomExportStatsTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final OperationLogService logs = mock(OperationLogService.class);
    private final StatsService stats = mock(StatsService.class);
    private final CustomExportService service = new CustomExportService(jdbc, logs, stats);
    private final LocalDate from = LocalDate.of(2026, 10, 1);
    private final LocalDate to = LocalDate.of(2026, 10, 15);
    @BeforeEach void login() { role(Role.ADMIN); }
    @AfterEach void logout() { AuthContext.clear(); }
    private void role(Role role) { AuthContext.set(new AuthUser(1,"synthetic","测试账号",role,Instant.now().plusSeconds(60))); }
    private CustomExportService.ExportRequest request() { return new CustomExportService.ExportRequest("stats",List.of("studentNo","attendanceHours","trainingHours","totalHours"),Map.of("from",from.toString(),"to",to.toString()),""); }
    private StatsService.SummaryItem item(int id) { return new StatsService.SummaryItem(id,"T"+id,"测试成员",null,"MEMBER",2,1,new BigDecimal("3.25"),new BigDecimal("1.50"),3,new BigDecimal("4.75")); }
    @Test void exposesSummaryToLeadersWithoutExpandingMinisterPermissions() {
        role(Role.PRESIDENT);
        assertThat(service.options().sources()).anySatisfy(source -> { assertThat(source.id()).isEqualTo("stats"); assertThat(source.label()).isEqualTo("统计汇总"); });
        role(Role.MINISTER);
        assertThatThrownBy(service::options).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.preview(request())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.export(request())).isInstanceOf(ApiException.class);
        verifyNoInteractions(stats, jdbc, logs);
    }
    @Test void previewsTheSameRoundedSummaryAndCountsTheFullRange() {
        when(stats.summary(from,to)).thenReturn(IntStream.rangeClosed(1,14).mapToObj(this::item).toList());
        var result=service.preview(request());
        assertThat(result.totalRows()).isEqualTo(14);
        assertThat(result.rows()).hasSize(12);
        assertThat(result.truncated()).isTrue();
        assertThat(result.rows().getFirst()).containsOnlyKeys("studentNo","attendanceHours","trainingHours","totalHours").containsEntry("totalHours",new BigDecimal("4.75"));
        assertThat(result.filters()).containsEntry("from",from.toString()).containsEntry("to",to.toString());
        verify(stats).summary(from,to);
        verifyNoInteractions(jdbc,logs);
    }
    @Test void exportsIdenticalNumericTotalsAndAuditsOnlyAfterGeneration() throws Exception {
        when(stats.summary(from,to)).thenReturn(List.of(item(1)));
        var file=service.export(request());
        assertThat(file.rowCount()).isEqualTo(1);
        try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(file.bytes()))) {
            var row=workbook.getSheetAt(0).getRow(4);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("T1");
            assertThat(row.getCell(1).getNumericCellValue()).isEqualTo(3.25);
            assertThat(row.getCell(2).getNumericCellValue()).isEqualTo(1.5);
            assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(4.75);
        }
        verify(logs).logExport(eq("CUSTOM_STATS"),anyString(),eq(request().filters()),eq(1),eq(file.filename()),anyMap());
        verifyNoInteractions(jdbc);
    }
    @Test void requiresBothDatesAndRejectsInvalidFieldsBeforeReadingStatistics() {
        assertThatThrownBy(() -> service.preview(new CustomExportService.ExportRequest("stats",List.of("totalHours"),Map.of(),""))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.preview(new CustomExportService.ExportRequest("stats",List.of("secret"),request().filters(),""))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.preview(new CustomExportService.ExportRequest("stats",List.of("totalHours"),Map.of("from",to.toString(),"to",from.toString()),""))).isInstanceOf(ApiException.class);
        verifyNoInteractions(stats,jdbc,logs);
    }
}
