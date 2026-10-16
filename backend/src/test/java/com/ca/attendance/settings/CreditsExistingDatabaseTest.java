package com.ca.attendance.settings;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteDataSource;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in against the explicitly authorized original DB. No schema/seed/copy; every test write rolls back. */
@EnabledIfEnvironmentVariable(named="CA_EXISTING_DB", matches=".+")
class CreditsExistingDatabaseTest {
    JdbcTemplate jdbc; DataSourceTransactionManager manager; ObjectMapper mapper = new ObjectMapper();
    @BeforeEach void setup() {
        Path path = Path.of(System.getenv("CA_EXISTING_DB")).toAbsolutePath().normalize();
        assertThat(Files.isRegularFile(path)).isTrue();
        SQLiteDataSource source = new SQLiteDataSource(); source.setUrl("jdbc:sqlite:"+path);
        jdbc = new JdbcTemplate(source); manager = new DataSourceTransactionManager(source);
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE role='ADMIN' LIMIT 1",Long.class);
        AuthContext.set(new AuthUser(id,"local-check","local-check",Role.ADMIN,Instant.now().plusSeconds(120)));
    }
    @AfterEach void clear() { AuthContext.clear(); }
    CreditsService proxy(OperationLogService logs) {
        ProxyFactory factory = new ProxyFactory(new CreditsService(jdbc,logs,mapper)); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        return (CreditsService)factory.getProxy();
    }
    List<?> stored() { return jdbc.queryForList("SELECT * FROM app_settings WHERE setting_key=?",CreditsService.KEY); }
    @Test void savesSettingsAndAuditInOneTransactionWithoutLeavingTestData() {
        var original = stored(); var count = jdbc.queryForObject("SELECT COUNT(*) FROM operation_logs",Long.class);
        var service = proxy(new OperationLogService(jdbc,mapper));
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var before = service.current();
            service.update(new CreditsService.Update(CreditsService.INTRO,List.of(CreditsService.FOUNDER,new CreditsService.Entry("验证条目","事务回滚验证")),before.revision()));
            assertThat(service.current().contributors()).hasSize(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_logs",Long.class)).isEqualTo(count+1);
            status.setRollbackOnly();
        });
        assertThat(stored()).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_logs",Long.class)).isEqualTo(count);
    }
    @Test void auditFailureRollsBackTheSetting() {
        var original=stored(); var logs=mock(OperationLogService.class);
        doThrow(new IllegalStateException("audit failure")).when(logs).log(anyString(),anyString(),isNull(),any(),any(),anyString());
        var service=proxy(logs);var before=service.current();
        assertThatThrownBy(()->service.update(new CreditsService.Update(CreditsService.INTRO,List.of(CreditsService.FOUNDER,new CreditsService.Entry("验证条目","失败回滚验证")),before.revision()))).isInstanceOf(IllegalStateException.class);
        assertThat(stored()).isEqualTo(original);
    }
}
