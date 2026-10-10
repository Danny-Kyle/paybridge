package com.academy.paybridge.compliance.service;

import com.academy.paybridge.compliance.api.Decision;
import com.academy.paybridge.compliance.api.ScreeningRequest;
import com.academy.paybridge.compliance.repository.SanctionsEntryRepository;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
class SanctionsNameRule implements ComplianceRule {
    private final SanctionsEntryRepository sanctions;
    SanctionsNameRule(SanctionsEntryRepository sanctions) { this.sanctions = sanctions; }

    @Override public Optional<Finding> evaluate(ScreeningRequest r) {
        String normalised = r.recipientName().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return sanctions.existsByNameNormalized(normalised)
                ? Optional.of(new Finding(Decision.BLOCK, "RECIPIENT_ON_SANCTIONS_LIST")) : Optional.empty();
    }
}