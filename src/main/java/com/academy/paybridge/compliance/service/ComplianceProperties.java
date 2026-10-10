package com.academy.paybridge.compliance.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("paybridge.compliance")
public record ComplianceProperties(long maxPerTransactionMinor, long dailyLimitMinor) {}