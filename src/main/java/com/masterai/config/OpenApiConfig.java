package com.masterai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI masterAiOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Master AI Backend API")
                .description("Spring Boot backend for Aptitude & Coding AI system.")
                .version("v0.0.1")
                .license(new License().name("Apache 2.0").url("http://springdoc.org"))
                .contact(new Contact().name("Nataraj").email("nataraj@example.com")));
    }
}
