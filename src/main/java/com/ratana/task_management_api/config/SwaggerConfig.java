package com.ratana.task_management_api.config;


import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI()
    {
        return new OpenAPI()
                .info(apiInfo())
                .components(securityComponents())
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));

    }
    private Info apiInfo() {
        return new Info()
                .title("Employee Work Management System API")
                .description("Task Management + KPI + Approval System")
                .version("1.0.0")
                .contact(new Contact()
                        .name("Your Name / Team")
                        .url("https://yourcompany.com")
                        .email("your@email.com"))
                .license(new License()
                        .name("Private License")
                        .url("https://yourcompany.com/license"));
    }

    private Components securityComponents()
    {
        return new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,apiKeyScheme());

    }

    private SecurityScheme apiKeyScheme()
    {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("Enter JWT token (without 'Bearer ' prefix)");
    }
}
