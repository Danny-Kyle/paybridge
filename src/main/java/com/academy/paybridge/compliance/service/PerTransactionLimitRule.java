package com.academy.paybridge.compliance.service;

import com.academy.paybridge.compliance.api.Decision;
import com.academy.paybridge.compliance.api.ScreeningRequest;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
class PerTransactionLimitRule implements ComplianceRule {
    private final ComplianceProperties props;
    PerTransactionLimitRule(ComplianceProperties props) { this.props = props; }

    @Override public Optional<Finding> evaluate(ScreeningRequest r) {
        return r.amount().minorUnits() > props.maxPerTransactionMinor()
                ? Optional.of(new Finding(Decision.BLOCK, "PER_TRANSACTION_LIMIT_EXCEEDED")) : Optional.empty();
    }
}