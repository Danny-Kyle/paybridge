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
    OpenAPI paybridgeOpenApi(@org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        return new OpenAPI().components(new io.swagger.v3.oas.models.Components()
          .addSecuritySchemes("oidc",new io.swagger.v3.oas.models.security.SecurityScheme()
            .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.OAUTH2)
            .flows(new io.swagger.v3.oas.models.security.OAuthFlows().authorizationCode(
              new io.swagger.v3.oas.models.security.OAuthFlow().authorizationUrl(issuer+"/protocol/openid-connect/auth")
              .tokenUrl(issuer+"/protocol/openid-connect/token")
              .scopes(new io.swagger.v3.oas.models.security.Scopes().addString("openid","OpenID Connect").addString("profile","Profile").addString("email","Email"))))))
          .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("oidc"))
          .info(new Info()
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

            responses.putIfAbsent("401", new ApiResponse().description("Missing, expired or invalid token"));
            responses.putIfAbsent("403", new ApiResponse().description("Role or resource ownership denied"));
            responses.putIfAbsent("409", new ApiResponse().description("Conflicting idempotency request or state"));
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