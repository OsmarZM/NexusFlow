package com.nexusflow.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI nexusFlowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("NexusFlow — Distributed Order Management & Fulfillment Platform")
                        .description("High-performance distributed backend platform for order orchestration, inventory locking, sagas, outbox events, and logistics.")
                        .version("v0.1.0")
                        .contact(new Contact()
                                .name("NexusFlow Core Team")
                                .email("engineering@nexusflow.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
