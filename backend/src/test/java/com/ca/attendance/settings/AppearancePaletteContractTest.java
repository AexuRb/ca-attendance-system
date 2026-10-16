package com.ca.attendance.settings;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.GlobalExceptionHandler;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contract coverage without opening, creating or copying any database. */
class AppearancePaletteContractTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final OperationLogService logs=mock(OperationLogService.class);
    final AtomicReference<String> stored=new AtomicReference<>();
    MockMvc mvc;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        when(jdbc.query(anyString(),any(RowMapper.class),eq("UI_APPEARANCE")))
            .thenAnswer(call -> stored.get()==null ? List.of() : List.of(stored.get()));
        when(jdbc.update(anyString(),eq("UI_APPEARANCE"),anyString(),eq(1L)))
            .thenAnswer(call -> { stored.set(call.getArgument(2)); return 1; });
        mvc=MockMvcBuilders.standaloneSetup(new AppearanceController(new AppearanceService(jdbc,logs)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void clear(){ AuthContext.clear(); }
    void role(Role role){ AuthContext.set(new AuthUser(1,"contract-check","contract-check",role,Instant.now().plusSeconds(60))); }
    @ParameterizedTest @EnumSource(AppearanceService.Appearance.class)
    void administratorSavesAndPublicStartupRestoresEveryOldAndNewValue(AppearanceService.Appearance appearance) throws Exception {
        role(Role.ADMIN);
        mvc.perform(put("/api/settings/appearance").contentType(MediaType.APPLICATION_JSON)
            .content("{\"appearance\":\""+appearance.name()+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.appearance").value(appearance.name()));
        AuthContext.clear();
        mvc.perform(get("/api/public/appearance")).andExpect(status().isOk())
            .andExpect(jsonPath("$.appearance").value(appearance.name()));
        verify(logs).log(eq("UPDATE_UI_APPEARANCE"),eq("app_settings"),isNull(),any(),any(),eq("切换全局界面"));
    }
    @ParameterizedTest @EnumSource(value=Role.class,names={"PRESIDENT","MINISTER","MEMBER"})
    void directNewPaletteWriteRemainsAdministratorOnly(Role role) throws Exception {
        role(role);
        mvc.perform(put("/api/settings/appearance").contentType(MediaType.APPLICATION_JSON).content("{\"appearance\":\"ROSE\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(jdbc,logs);
    }
    @ParameterizedTest @ValueSource(strings={"","unknown","CLASSIC_INVALID"})
    void invalidStoredValuesRetainClassicFallback(String value) throws Exception {
        stored.set(value);
        mvc.perform(get("/api/public/appearance")).andExpect(status().isOk()).andExpect(jsonPath("$.appearance").value("CLASSIC"));
    }
    @ParameterizedTest @ValueSource(strings={"INVALID","__proto__"})
    void unsupportedWriteIsRejectedBeforeDatabaseAccess(String value) throws Exception {
        role(Role.ADMIN);
        mvc.perform(put("/api/settings/appearance").contentType(MediaType.APPLICATION_JSON).content("{\"appearance\":\""+value+"\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc,logs);
    }
}
