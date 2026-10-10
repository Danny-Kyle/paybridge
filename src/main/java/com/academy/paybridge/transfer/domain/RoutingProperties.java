package com.academy.paybridge.transfer.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("paybridge.routing")
public record RoutingProperties(long nextOfKinThresholdMinor) {}