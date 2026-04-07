package com.tunegocio.turnosapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de Swagger / OpenAPI 3.
 *
 * <p>Acceder en: <a href="http://localhost:8080/swagger-ui.html">swagger-ui.html</a>
 * <br>JSON spec: <a href="http://localhost:8080/v3/api-docs">v3/api-docs</a>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TuNegocio API")
                        .description("""
                                Plataforma SaaS multi-vertical de gestión de negocios.

                                **Autenticación:** Todos los endpoints protegidos requieren un JWT Bearer Token.
                                Obtené tu token en `POST /api/auth/login` e ingresalo en el botón Authorize.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("TuNegocio Platform")
                                .email("dev@tunegocio.com")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Ingresá el JWT token obtenido del endpoint de login")));
    }
}
