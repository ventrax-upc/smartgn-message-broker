package com.ventrax.broker;

import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "SmartGN - Message Broker",
        version = "v1",
        license = @License(name = "MIT"),
        description = "Internal adapter for EMQX credentials, topic authorization and service-event publishing."))
@SecurityScheme(
        name = "serviceAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "opaque service token")
public class SmartGnBrokerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartGnBrokerApplication.class, args);
    }
    @Bean
    OpenApiCustomizer brokerAuthenticationResponse() {
        return api -> api.getPaths().forEach((path, item) -> item.readOperations().forEach(operation ->
                operation.getResponses().putIfAbsent("401",
                        new ApiResponse().description("Service token is missing or invalid."))));
    }
}
