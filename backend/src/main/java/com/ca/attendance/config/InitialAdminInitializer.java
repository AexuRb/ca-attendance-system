package com.ca.attendance.config;

import com.ca.attendance.setup.SetupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class InitialAdminInitializer implements CommandLineRunner {
    private final SetupService setup;
    private final String studentNo;
    private final String password;

    public InitialAdminInitializer(SetupService setup,
                                   @Value("${app.initial-admin.student-no}") String studentNo,
                                   @Value("${app.initial-admin.password:}") String password) {
        this.setup = setup;
        this.studentNo = studentNo;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        setup.initializeConfigured(studentNo, password);
    }
}
