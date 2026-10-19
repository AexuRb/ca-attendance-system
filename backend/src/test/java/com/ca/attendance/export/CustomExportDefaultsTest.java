package com.ca.attendance.export;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.stats.StatsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CustomExportDefaultsTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final OperationLogService logs=mock(OperationLogService.class);
    final StatsService stats=mock(StatsService.class);
    final CustomExportService service=new CustomExportService(jdbc,logs,stats);
    @AfterEach void cleanup(){AuthContext.clear();}

    @Test void defaultsMatchConfirmedFieldsAndOptionalFieldsRemainAvailable() {
        AuthContext.set(new AuthUser(1,"900001","示例管理员",Role.ADMIN,Instant.now().plusSeconds(300)));
        var sources=service.options().sources();
        assertEquals(List.of("members","attendance","schedule","repairs","training","logs"),sources.stream().map(CustomExportService.SourceOption::id).toList());
        assertEquals(List.of("studentNo","name","major","grade"),defaults("members"));
        assertEquals(List.of("weekday","period","name"),defaults("schedule"));
        assertEquals(List.of("createdAt","operatorName","actionType","targetType","reason"),defaults("logs"));
        assertEquals(10,sources.stream().filter(s->s.id().equals("members")).findFirst().orElseThrow().fields().size());
        assertEquals(9,sources.stream().filter(s->s.id().equals("schedule")).findFirst().orElseThrow().fields().size());
        verifyNoInteractions(jdbc,logs,stats);
    }
    List<String> defaults(String source){return service.options().sources().stream().filter(s->s.id().equals(source)).findFirst().orElseThrow().fields().stream().filter(CustomExportService.FieldOption::defaultSelected).map(CustomExportService.FieldOption::id).toList();}
}
