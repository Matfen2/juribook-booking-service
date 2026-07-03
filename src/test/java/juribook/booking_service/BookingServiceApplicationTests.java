package juribook.booking_service;

import org.junit.jupiter.api.Test;

/**
 * Smoke test - vérifie que la classe principale existe et compile.
 *
 * On n'utilise PAS @SpringBootTest car le démarrage complet du contexte
 * nécessite PostgreSQL + Kafka qui ne sont pas disponibles en CI.
 *
 * Les tests unitaires sont couverts par AvailabilityServiceTest,
 * TimeSlotServiceTest, etc. (à venir)
 */
class BookingServiceApplicationTests {

    @Test
    void applicationClassExists() {
        Class<?> appClass = BookingServiceApplication.class;
        assert appClass != null;
    }
}