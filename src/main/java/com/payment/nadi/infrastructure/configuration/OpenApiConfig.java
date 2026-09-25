package com.payment.nadi.infrastructure.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 configuration providing interactive Swagger UI documentation,
 * security schemes, metadata, and server definitions.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_BEARER = "BearerAuth";
    private static final String SECURITY_SCHEME_API_KEY = "ApiKeyAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Nadi Payment Service API")
                        .version("1.0.0")
                        .description("""
                                Enterprise Payment Orchestration and Double-Entry Ledger Microservice.
                                
                                ### Key Features:
                                * **Top-Up (C2B / Pay-In)**: Inbound collections via mobile money (Telebirr C2B).
                                * **Withdrawal (B2C / Payout)**: Outbound disbursements from customer wallet to mobile money.
                                * **Transfer (P2P / Ledger)**: Atomic internal account balance movement.
                                * **Double-Entry Ledger**: Auditable financial accounting records.
                                * **Idempotency Protection**: Enforced via `Idempotency-Key` headers on all mutating endpoints.
                                * **Cryptographic Security**: RSA-2048 & SHA256withRSA signature verification.
                                """)
                        .contact(new Contact()
                                .name("Naditech Engineering Team")
                                .email("engineering@naditech.com")
                                .url("https://naditech.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://naditech.com/terms")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development Server"),
                        new Server().url("https://api.naditech.com").description("Production Gateway")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_BEARER))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_BEARER,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_BEARER)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT Authorization header using the Bearer scheme. Example: \"Bearer {token}\""))
                        .addSecuritySchemes(SECURITY_SCHEME_API_KEY,
                                new SecurityScheme()
                                        .name("X-API-KEY")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("API key for service-to-service communication")));
    }
}
