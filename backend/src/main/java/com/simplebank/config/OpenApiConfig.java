package com.simplebank.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: adds an "Authorize" button. Log in with POST /api/auth/login, paste the
 * accessToken there, and every request made from Swagger UI sends it.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Simple Bank API", version = "1.0",
                description = "Users, accounts, deposits, withdrawals, transfers, search, and an audit trail. "
                        + "Log in with POST /api/auth/login and use the token with the Authorize button."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
