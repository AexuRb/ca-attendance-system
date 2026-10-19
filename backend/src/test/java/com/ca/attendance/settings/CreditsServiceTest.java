package com.ca.attendance.settings;
import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreditsServiceTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final OperationLogService logs = mock(OperationLogService.class);
    final CreditsService service = new CreditsService(jdbc, logs, new ObjectMapper());
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        role(Role.ADMIN);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(CreditsService.KEY))).thenReturn(List.of());
        when(jdbc.update(anyString(), eq(CreditsService.KEY), anyString(), anyLong())).thenReturn(1);
    }
    @AfterEach void clear() { AuthContext.clear(); }
    void role(Role role) { AuthContext.set(new AuthUser(1,"local-check","local-check",role,Instant.now().plusSeconds(60))); }
    CreditsService.Update request(List<CreditsService.Entry> entries) { return new CreditsService.Update(CreditsService.INTRO,entries,service.current().revision()); }
    @Test void defaultsAreReadOnlyAndNeverSeedTheDatabase() {
        var current = service.current();
        assertThat(current.intro()).isEqualTo(CreditsService.INTRO);
        assertThat(current.contributors()).containsExactly(CreditsService.FOUNDER);
        verify(jdbc, never()).update(anyString(),any(Object[].class)); verifyNoInteractions(logs);
    }
    @Test void onlyAdministratorCanWrite() {
        var request = request(List.of(CreditsService.FOUNDER));
        clearInvocations(jdbc);
        for (Role role : List.of(Role.PRESIDENT, Role.MINISTER, Role.MEMBER)) {
            role(role); assertThatThrownBy(() -> service.update(request)).isInstanceOf(ApiException.class).hasMessageContaining("管理员");
        }
        verifyNoInteractions(jdbc, logs);
    }
    @Test void allAuthenticatedRolesCanReadSavedContributorsWithoutWriting() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(CreditsService.KEY)))
                .thenReturn(List.of("[{\"name\":\"维护者－示例\",\"contribution\":\"维护\"}]"));
        for (Role role : Role.values()) {
            role(role);
            assertThat(service.current().contributors()).containsExactly(CreditsService.FOUNDER,
                    new CreditsService.Entry("维护者－示例", "维护"));
        }
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(logs);
    }
    @Test void rejectsFounderEditsDeletionReorderingAndThanksEdits() {
        var extra = new CreditsService.Entry("维护者－姓名","功能改进");
        for (var rows : List.of(List.<CreditsService.Entry>of(),List.of(extra),List.of(extra,CreditsService.FOUNDER),List.of(new CreditsService.Entry(CreditsService.FOUNDER.name(),"修改贡献")))) {
            assertThatThrownBy(() -> service.update(new CreditsService.Update(CreditsService.INTRO,rows,""))).isInstanceOf(ApiException.class).hasMessageContaining("不可修改");
        }
        assertThatThrownBy(() -> service.update(new CreditsService.Update("替换感谢语",List.of(CreditsService.FOUNDER),""))).isInstanceOf(ApiException.class);
        verifyNoInteractions(logs);
    }
    @Test void validatesTheWholeListBeforeWriting() {
        for (var extra : List.of(new CreditsService.Entry(" ","贡献"),new CreditsService.Entry("姓名",""),new CreditsService.Entry("x".repeat(101),"贡献"),new CreditsService.Entry("姓名","x".repeat(1001)),new CreditsService.Entry("姓名\n换行","贡献"))) {
            assertThatThrownBy(() -> service.update(request(List.of(CreditsService.FOUNDER,extra)))).isInstanceOf(ApiException.class);
        }
        verifyNoInteractions(logs);
    }
    @Test void savesOnlyAdditionalEntriesAndAuditsTheOrderedResult() {
        var a = new CreditsService.Entry(" 维护者－甲 ","界面\n测试");
        var b = new CreditsService.Entry("维护者－乙","维护");
        var result = service.update(request(List.of(CreditsService.FOUNDER,b,a)));
        assertThat(result.contributors()).containsExactly(CreditsService.FOUNDER,b,new CreditsService.Entry("维护者－甲","界面\n测试"));
        verify(jdbc).update(anyString(),eq(CreditsService.KEY),argThat((String value)->value.contains("维护者－乙")&&!value.contains("陈禹杭")&&!value.contains(CreditsService.INTRO)),eq(1L));
        verify(logs).log(eq("UPDATE_ACKNOWLEDGEMENTS"),eq("app_settings"),isNull(),any(),eq(result),anyString());
    }
    @Test void staleRevisionCannotOverwriteAnotherEditorsChanges() {
        assertThatThrownBy(() -> service.update(new CreditsService.Update(CreditsService.INTRO,List.of(CreditsService.FOUNDER,new CreditsService.Entry("甲","乙")),"old"))).isInstanceOf(ApiException.class).hasMessageContaining("已被更新");
        verifyNoInteractions(logs);
    }
    @Test @SuppressWarnings("unchecked") void corruptStoredListIsNotSilentlyReplaced() {
        when(jdbc.query(anyString(),any(RowMapper.class),eq(CreditsService.KEY))).thenReturn(List.of("not-json"));
        assertThatThrownBy(service::current).isInstanceOf(ApiException.class).hasMessageContaining("暂时无法读取");
        verifyNoInteractions(logs);
    }
    @Test void unchangedSaveDoesNotCreateASettingOrAudit() {
        service.update(request(List.of(CreditsService.FOUNDER))); verifyNoInteractions(logs);
    }
}
