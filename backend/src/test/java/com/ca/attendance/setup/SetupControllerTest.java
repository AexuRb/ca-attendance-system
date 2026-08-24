package com.ca.attendance.setup;

import com.ca.attendance.access.RemoteAccessPolicy;
import com.ca.attendance.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetupControllerTest {
    @Test
    void remoteConnectorCannotReadSetupStatusEvenWhenTunnelPeerIsLoopback() {
        SetupService setup = mock(SetupService.class);
        SetupController controller = new SetupController(setup, new RemoteAccessPolicy(8081));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/setup/status");
        request.setLocalPort(8081);
        request.setRemoteAddr("127.0.0.1");

        assertThatThrownBy(() -> controller.status(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("本机");
    }

    @Test
    void localConnectorCanReadMinimalSetupStatus() {
        SetupService setup = mock(SetupService.class);
        when(setup.status()).thenReturn(new SetupService.SetupStatus(true));
        SetupController controller = new SetupController(setup, new RemoteAccessPolicy(8081));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/setup/status");
        request.setLocalPort(8080);
        request.setRemoteAddr("127.0.0.1");

        controller.status(request);

        verify(setup).status();
    }
}
