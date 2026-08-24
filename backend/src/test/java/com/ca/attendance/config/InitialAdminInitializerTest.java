package com.ca.attendance.config;

import com.ca.attendance.setup.SetupService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InitialAdminInitializerTest {
    @Test
    void delegatesConfiguredInitializationToTheSetupBoundary() {
        SetupService setup = mock(SetupService.class);
        InitialAdminInitializer initializer = new InitialAdminInitializer(
                setup,
                "9900000001",
                "configured-password"
        );

        initializer.run();

        verify(setup).initializeConfigured("9900000001", "configured-password");
    }
}
