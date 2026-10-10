package com.academy.paybridge.compliance.service;

import com.academy.paybridge.compliance.api.Decision;
import com.academy.paybridge.compliance.api.ScreeningRequest;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
class KycVerifiedRule implements ComplianceRule {
    @Override public Optional<Finding> evaluate(ScreeningRequest r) {
        return r.senderKycVerified() ? Optional.empty() : Optional.of(new Finding(Decision.BLOCK, "SENDER_KYC_NOT_VERIFIED"));
    }
}