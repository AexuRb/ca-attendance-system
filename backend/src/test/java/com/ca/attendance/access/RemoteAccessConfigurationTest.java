package com.ca.attendance.access;

import org.junit.jupiter.api.Test;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;

import static org.assertj.core.api.Assertions.assertThat;

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
}
