package com.ca.attendance.attendance;

import com.ca.attendance.common.ApiException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class PublicMemberSelectionService {
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final String TOKEN_PREFIX = "sel_";

    private final Clock clock;
    private final Duration ttl;
    private final int maxTokens;
    private final Map<String, Selection> selections = new ConcurrentHashMap<>();

    public PublicMemberSelectionService() {
        this(Clock.systemUTC(), DEFAULT_TTL, DEFAULT_MAX_TOKENS);
    }

    PublicMemberSelectionService(Clock clock, Duration ttl, int maxTokens) {
        this.clock = clock;
        this.ttl = ttl;
        this.maxTokens = maxTokens;
    }

    public String issue(String studentNo) {
        String normalized = studentNo == null ? "" : studentNo.trim();
        if (normalized.isBlank()) {
            throw ApiException.badRequest("成员信息无效，请重新查询");
        }
        cleanupExpired();
        if (selections.size() >= maxTokens) {
            throw ApiException.tooManyRequests("签到查询较多，请稍后再试");
        }
        String token = TOKEN_PREFIX + UUID.randomUUID().toString().replace("-", "");
        selections.put(token, new Selection(normalized, clock.instant().plus(ttl), null));
        return token;
    }

    public String resolve(String token) {
        return update(token, null);
    }

    public String bindForSubmission(String token, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw ApiException.badRequest("提交编号无效，请重新查询");
        }
        return update(token, requestId.trim());
    }

    private String update(String token, String requestId) {
        String normalizedToken = normalizeToken(token);
        AtomicReference<String> studentNo = new AtomicReference<>();
        selections.compute(normalizedToken, (key, selection) -> {
            if (selection == null || !selection.expiresAt().isAfter(clock.instant())) {
                return null;
            }
            if (requestId != null
                    && selection.requestId() != null
                    && !selection.requestId().equals(requestId)) {
                throw ApiException.conflict("该身份确认已提交，请重新查询后再试");
            }
            studentNo.set(selection.studentNo());
            return requestId == null || requestId.equals(selection.requestId())
                    ? selection
                    : new Selection(selection.studentNo(), selection.expiresAt(), requestId);
        });
        if (studentNo.get() == null) {
            throw ApiException.badRequest("身份确认已失效，请重新查询");
        }
        return studentNo.get();
    }

    private String normalizeToken(String token) {
        String normalized = token == null ? "" : token.trim();
        if (!normalized.matches("sel_[a-f0-9]{32}")) {
            throw ApiException.badRequest("身份确认已失效，请重新查询");
        }
        return normalized;
    }

    private void cleanupExpired() {
        Instant now = clock.instant();
        selections.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record Selection(String studentNo, Instant expiresAt, String requestId) {
    }
}
