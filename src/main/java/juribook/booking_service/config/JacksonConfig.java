package juribook.booking_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fournit explicitement un bean ObjectMapper (Jackson 2 classique).
 *
 * Spring Boot 4 utilise Jackson 3 (JsonMapper) par défaut pour la
 * sérialisation HTTP des @RestController, ce qui fonctionne très bien
 * pour l'API REST du service, mais ne crée aucun bean
 * com.fasterxml.jackson.databind.ObjectMapper (Jackson 2 classique) tout
 * seul. BookingEventPublisherImpl et SlotEventPublisherImpl utilisent ce
 * type explicitement (choix fait au Sprint 4.6, avant l'arrivée de
 * Jackson 3 dans nos réflexes) pour sérialiser les payloads Kafka.
 *
 * findAndRegisterModules() découvre et enregistre automatiquement les
 * modules Jackson disponibles sur le classpath (ex: JavaTimeModule pour
 * LocalDateTime) sans dépendance dure explicite dans le code.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}