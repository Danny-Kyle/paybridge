package com.academy.paybridge;

import com.academy.paybridge.compliance.service.ComplianceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
@ConfigurationPropertiesScan
public class PayBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayBridgeApplication.class, args);
    }
}
