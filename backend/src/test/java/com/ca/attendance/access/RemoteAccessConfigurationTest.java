package com.ca.attendance.access;

import org.junit.jupiter.api.Test;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteAccessConfigurationTest {
    @Test
    void addsLoopbackOnlyRemoteAdminConnector() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();

        new RemoteAccessConfiguration(8081, 8080).customize(factory);

        assertThat(factory.getAdditionalConnectors()).hasSize(1);
        var connector = factory.getAdditionalConnectors().getFirst();
        assertThat(connector.getPort()).isEqualTo(8081);
        assertThat(String.valueOf(connector.getProperty("address"))).endsWith("127.0.0.1");
    }

    @Test
    void rejectsRemotePortThatMatchesLocalPort() {
        assertThatThrownBy(() -> new RemoteAccessConfiguration(8080, 8080))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能与本机服务端口相同");
    }
}
