package com.sense.prolt_operations.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!prod")
@SecuritySchemes({
        @SecurityScheme(name = "Bearer Authentication", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT", in = SecuritySchemeIn.HEADER, description = "Enter Bearer token")
})
public class SwaggerConfig {

    @Bean
    GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("api")
                .packagesToScan("com.sense.prolt_operations.controller")
                .build();
    }

    @Bean
    OpenAPI openAPI() {
        String name = "Prolt Energy / Probus Smart Things Pvt. Ltd.";
        String url = "https://probus.io";
        String email = "info@probus.io";
        String title = "Prolt Energy - IEX Analytics & Operations API";
        String description = "Enterprise REST API for IEX Analytics, Power Portfolio Management and Forecasting";
        String version = "v1";

        return new OpenAPI().info(new Info()
                .title(title)
                .version(version)
                .description(description)
                .termsOfService(url)
                .contact(new Contact().name(name).email(email).url(url))
                .license(new License().name("Proprietary").url(url)));
    }
}
