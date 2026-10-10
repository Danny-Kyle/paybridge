package com.academy.paybridge.compliance.service;

import com.academy.paybridge.compliance.api.ScreeningRequest;
import java.util.Optional;

interface ComplianceRule {
    Optional<Finding> evaluate(ScreeningRequest request);
}