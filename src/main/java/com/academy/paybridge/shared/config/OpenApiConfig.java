package com.academy.paybridge.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Arrays;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI paybridgeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("PayBridge API")
                .version("v1")
                .description("""
                        Money transfer, cross-border settlement and USD to NGN remittance.

                        **All amounts are in minor units** (kobo for NGN, cents for USD): 500000 = NGN 5,000.00.

                        **Errors** use RFC 9457 `application/problem+json`. Business-rule failures (422) carry a
                        machine-readable `code`; validation failures (400) carry an `errors` map keyed by field.
                        """));
    }

    @Bean GroupedOpenApi allApi() {
        return GroupedOpenApi.builder().group("all").pathsToMatch("/api/**").build();
    }

    @Bean GroupedOpenApi customerApi() {
        return GroupedOpenApi.builder().group("customers").pathsToMatch("/api/v1/customers/**").build();
    }

    @Bean GroupedOpenApi accountApi() {
        return GroupedOpenApi.builder().group("accounts").pathsToMatch("/api/v1/accounts/**").build();
    }

    @Bean GroupedOpenApi transferApi() {
        return GroupedOpenApi.builder().group("transfers").pathsToMatch("/api/v1/transfers/**").build();
    }

    /** Adds the error responses our GlobalExceptionHandler can produce to every operation. */
    @Bean
    OperationCustomizer standardErrorResponses() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            if (responses == null) return operation;

            responses.putIfAbsent("400", new ApiResponse().description("Validation failed (problem+json with an 'errors' map)"));
            responses.putIfAbsent("422", new ApiResponse().description("Business rule violated (problem+json with a 'code')"));
            responses.putIfAbsent("500", new ApiResponse().description("Unexpected error"));

            boolean hasPathVariable = Arrays.stream(handlerMethod.getMethod().getParameters())
                    .anyMatch(p -> p.isAnnotationPresent(PathVariable.class));
            if (hasPathVariable)
                responses.putIfAbsent("404", new ApiResponse().description("Resource not found (problem+json)"));
            return operation;
        };
    }
}