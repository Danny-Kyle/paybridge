package com.academy.paybridge.shared.config;

import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public JpaAuditingConfig {
    @Bean
    ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Bean
    Clock clock = Clock.systemUTC();
}
