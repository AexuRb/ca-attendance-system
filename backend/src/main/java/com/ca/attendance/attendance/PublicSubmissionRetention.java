package com.ca.attendance.attendance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class PublicSubmissionRetention implements CommandLineRunner {
    static final int RETENTION_DAYS = 30;

    private static final Logger log = LoggerFactory.getLogger(PublicSubmissionRetention.class);
    private final PublicSubmissionRepository submissions;

    public PublicSubmissionRetention(PublicSubmissionRepository submissions) {
        this.submissions = submissions;
    }

    @Override
    public void run(String... args) {
        int deleted = cleanupExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired public attendance submission receipts", deleted);
        }
    }

    public int cleanupExpired(LocalDateTime now) {
        return submissions.deleteCreatedBefore(now.minusDays(RETENTION_DAYS));
    }
}
