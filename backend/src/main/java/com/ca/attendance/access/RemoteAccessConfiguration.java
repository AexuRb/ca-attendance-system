package com.ca.attendance.access;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.TomcatWebServerFactory;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RemoteAccessConfiguration implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    private final int remotePort;
    private final int localPort;

    public RemoteAccessConfiguration(@Value("${app.remote.port:8081}") int remotePort,
                                     @Value("${server.port:8080}") int localPort) {
        this.remotePort = remotePort;
        this.localPort = localPort;
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        if (remotePort <= 0 || remotePort == localPort) {
            return;
        }
        Connector connector = new Connector(TomcatWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(remotePort);
        connector.setProperty("address", "127.0.0.1");
        factory.addAdditionalConnectors(connector);
    }
}
