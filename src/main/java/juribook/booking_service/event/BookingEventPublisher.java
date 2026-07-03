package juribook.booking_service.event;

import juribook.booking_service.entity.Booking;

/**
 * Abstraction de publication des événements liés au cycle de vie d'un
 * Booking, sur le topic Kafka booking-events.
 */
public interface BookingEventPublisher {

    void publishBookingCreated(Booking booking);

    void publishBookingConfirmed(Booking booking);

    void publishBookingCancelled(Booking booking);

    /**
     * Rappel automatique 24h avant le rendez-vous, publié
     * par BookingReminderJob, pas par une action utilisateur directe,
     * contrairement aux trois autres méthodes.
     */
    void publishBookingReminder(Booking booking);
}