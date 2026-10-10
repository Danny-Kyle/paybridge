package com.academy.paybridge.compliance.service;

import com.academy.paybridge.compliance.api.Decision;
import com.academy.paybridge.compliance.api.ScreeningRequest;
import com.academy.paybridge.compliance.repository.ComplianceCheckRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

@Component
class DailyLimitRule implements ComplianceRule {
    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    private final ComplianceCheckRepository checks;
    private final ComplianceProperties props;
    private final Clock clock;

    DailyLimitRule(ComplianceCheckRepository checks, ComplianceProperties props, Clock clock) {
        this.checks = checks; this.props = props; this.clock = clock;
    }

    @Override public Optional<Finding> evaluate(ScreeningRequest r) {
        Instant startOfDay = clock.instant().atZone(LAGOS).toLocalDate().atStartOfDay(LAGOS).toInstant();
        long used = checks.sumAllowedSince(r.senderCustomerId(), startOfDay);
        return used + r.amount().minorUnits() > props.dailyLimitMinor()
                ? Optional.of(new Finding(Decision.BLOCK, "DAILY_LIMIT_EXCEEDED")) : Optional.empty();
    }
}