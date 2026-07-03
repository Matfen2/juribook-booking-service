package juribook.booking_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
            .info(new Info()
                .title("JuriBook - Booking Service")
                .description("Gestion des disponibilités (Availability), créneaux (TimeSlot) et réservations des avocats")
                .version("0.0.1-SNAPSHOT")
                .contact(new Contact()
                    .name("Mathieu FENOUIL")
                    .email("matfen3.05@gmail.com")))
            // Déclare le schéma JWT Bearer pour le bouton "Authorize" de Swagger UI
            .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
            .components(new Components()
                .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                    .name(securitySchemeName)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Coller le token JWT obtenu via POST /api/auth/login sur l'auth-service (port 8081)")));
    }
}