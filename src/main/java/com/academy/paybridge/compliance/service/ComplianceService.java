package com.academy.paybridge.compliance.service;

import com.academy.paybridge.compliance.api.ComplianceApi;
import com.academy.paybridge.compliance.api.Decision;
import com.academy.paybridge.compliance.api.ScreeningRequest;
import com.academy.paybridge.compliance.api.ScreeningResult;
import com.academy.paybridge.compliance.domain.ComplianceCheck;
import com.academy.paybridge.compliance.repository.ComplianceCheckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
class ComplianceService implements ComplianceApi {

    private final List<ComplianceRule> rules;
    private final ComplianceCheckRepository checks;
    private final Clock clock;

    ComplianceService(List<ComplianceRule> rules, ComplianceCheckRepository checks, Clock clock) {
        this.rules = rules; this.checks = checks; this.clock = clock;
    }

    @Override
    @Transactional
    public ScreeningResult screen(ScreeningRequest request) {
        List<Finding> findings = rules.stream()
                .map(rule -> rule.evaluate(request))
                .flatMap(Optional::stream)
                .toList();
        Decision decision = findings.stream().map(Finding::decision)
                .max(Comparator.naturalOrder()).orElse(Decision.ALLOW);
        List<String> reasons = findings.stream().map(Finding::reason).toList();
        checks.save(ComplianceCheck.of(request, decision, reasons, clock.instant()));
        return new ScreeningResult(decision, reasons);
    }
}