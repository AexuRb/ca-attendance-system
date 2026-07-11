package com.ca.attendance.desktop;

import com.ca.attendance.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@Service
public class DesktopControlService {
    private final ConfigurableApplicationContext applicationContext;
    private final String controlToken;

    public DesktopControlService(ConfigurableApplicationContext applicationContext,
                                 @Value("${app.desktop.control-token:${APP_DESKTOP_CONTROL_TOKEN:${APP_RECOVERY_TOKEN:}}}") String controlToken) {
        this.applicationContext = applicationContext;
        this.controlToken = controlToken == null ? "" : controlToken;
    }

    public void shutdown(String suppliedToken) {
        requireToken(suppliedToken);
        Thread shutdown = new Thread(() -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            applicationContext.close();
        }, "desktop-shutdown");
        shutdown.setDaemon(false);
        shutdown.start();
    }

    private void requireToken(String suppliedToken) {
        if (controlToken.isBlank() || suppliedToken == null || suppliedToken.isBlank()) {
            throw ApiException.forbidden("桌面控制通道未启用");
        }
        byte[] expected = controlToken.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = suppliedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw ApiException.forbidden("桌面控制令牌无效");
        }
    }
}
