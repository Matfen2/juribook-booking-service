package juribook.booking_service.job;

import juribook.booking_service.entity.Booking;
import juribook.booking_service.entity.BookingStatus;
import juribook.booking_service.entity.TimeSlot;
import juribook.booking_service.event.BookingEventPublisher;
import juribook.booking_service.repository.BookingRepository;
import juribook.booking_service.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Job planifié : rappel automatique 24h avant le rendez-vous.
 *
 * Tourne toutes les 15 minutes, cherche les réservations CONFIRMED pas
 * encore rappelées (reminderSent = false), et publie un événement
 * booking.reminder sur booking-events pour celles dont le rendez-vous a
 * lieu dans moins de 24h (mais pas encore passé).
 *
 * Choix de conception :
 * - Le rappel est déclenché en franchissant le seuil des 24h, pas via
 *   une fenêtre étroite type "entre 23h50 et 24h10 avant", cette
 *   approche est indépendante de la fréquence exacte du job (peu
 *   importe si le job tourne toutes les 5, 15 ou 30 minutes, aucune
 *   réservation ne peut passer entre les mailles du filet).
 * - reminderSent évite les doublons entre deux exécutions du job.
 * - Le filtrage sur la date/heure exacte se fait en Java, pas en JPQL :
 *   le rendez-vous vit dans TimeSlot (date + startTime), pas
 *   directement sur Booking, et la comparaison combinée n'est pas
 *   trivialement exprimable en JPQL sans SQL natif, acceptable ici vu
 *   le volume de données attendu pour ce projet (liste des CONFIRMED
 *   non rappelées, pas toutes les réservations).
 *
 * ⚠️ Limite connue : si booking-service tournait en plusieurs instances
 * (non prévu à ce stade du projet), deux instances pourraient
 * théoriquement traiter la même réservation en même temps (pas de
 * verrou distribué sur le job). Sans conséquence en dev/mono-instance.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingReminderJob {

    private static final int REMINDER_WINDOW_HOURS = 24;

    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final BookingEventPublisher bookingEventPublisher;

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void sendReminders() {
        List<Booking> candidates = bookingRepository.findByStatusAndReminderSentFalse(BookingStatus.CONFIRMED);

        if (candidates.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reminderThreshold = now.plusHours(REMINDER_WINDOW_HOURS);
        int sentCount = 0;

        for (Booking booking : candidates) {
            TimeSlot slot = timeSlotRepository.findById(booking.getTimeSlotId()).orElse(null);
            if (slot == null || slot.getDate() == null || slot.getStartTime() == null) {
                log.warn("Créneau introuvable ou incomplet pour bookingId={}, rappel ignoré", booking.getId());
                continue;
            }

            LocalDateTime appointmentDateTime = LocalDateTime.of(slot.getDate(), slot.getStartTime());

            // Rendez-vous dans moins de 24h, mais pas déjà passé
            boolean dueForReminder = appointmentDateTime.isAfter(now)
                    && !appointmentDateTime.isAfter(reminderThreshold);

            if (dueForReminder) {
                bookingEventPublisher.publishBookingReminder(booking);
                booking.setReminderSent(true);
                bookingRepository.save(booking);
                sentCount++;
            }
        }

        if (sentCount > 0) {
            log.info("Job de rappel 24h : {} rappel(s) publié(s) sur booking-events (sur {} candidat(s) examiné(s))",
                    sentCount, candidates.size());
        }
    }
}