package com.mertalptekin.productservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Product Service API")
                        .version("v1")
                        .description("Product Service endpoint dokumantasyonu")
                        .contact(new Contact()
                                .name("Spring Cloud Microservices")
                                .url("https://github.com"))
                        .license(new License()
                                .name("Internal Use")));
    }
}

