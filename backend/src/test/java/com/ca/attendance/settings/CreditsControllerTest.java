package com.ca.attendance.settings;
import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.GlobalExceptionHandler;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CreditsControllerTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final OperationLogService logs=mock(OperationLogService.class);
    final ObjectMapper mapper=new ObjectMapper();
    MockMvc mvc;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        when(jdbc.query(anyString(),any(RowMapper.class),eq(CreditsService.KEY))).thenReturn(List.of());
        mvc=MockMvcBuilders.standaloneSetup(new CreditsController(new CreditsService(jdbc,logs,mapper))).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void clear(){AuthContext.clear();}
    void role(Role role){AuthContext.set(new AuthUser(1,"local-check","local-check",role,Instant.now().plusSeconds(60)));}
    @Test void presidentCannotReadOrPutDirectly() throws Exception {
        role(Role.PRESIDENT);mvc.perform(get("/api/settings/credits")).andExpect(status().isForbidden());
        mvc.perform(put("/api/settings/credits").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());verifyNoInteractions(jdbc,logs);
    }
    @Test void ministerCannotUseEitherEndpointDirectly() throws Exception {
        role(Role.MINISTER);mvc.perform(get("/api/settings/credits")).andExpect(status().isForbidden());
        mvc.perform(put("/api/settings/credits").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());verifyNoInteractions(jdbc,logs);
    }
    @Test void administratorCannotTamperWithFixedContentThroughJson() throws Exception {
        role(Role.ADMIN);
        var payload=new CreditsService.Update("修改感谢语",List.of(CreditsService.FOUNDER),"");
        mvc.perform(put("/api/settings/credits").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(payload))).andExpect(status().isBadRequest());verifyNoInteractions(jdbc,logs);
    }
}
