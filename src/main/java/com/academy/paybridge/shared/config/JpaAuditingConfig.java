package com.academy.paybridge.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
//    @Bean
//    ExecutorService executorService = Executors.newFixedThreadPool(10);


}
