package juribook.booking_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active le scheduling Spring (@Scheduled), nécessaire pour
 * BookingReminderJob. Isolé dans son propre fichier de
 * config plutôt que sur la classe principale, pour ne pas avoir à
 * connaître/modifier son contenu exact.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}