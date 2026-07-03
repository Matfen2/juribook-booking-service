-- ═══════════════════════════════════════════════════════════
--  Empêche plus d'une réservation active sur un même créneau
--
--  Filet de sécurité en base, en complément du verrou pessimiste
--  applicatif (TimeSlotRepository.findByIdForUpdate dans BookingService).
--  Index PARTIEL : autorise plusieurs réservations CANCELLED sur le même
--  créneau (historique d'annulations successives), mais une seule
--  réservation active (PENDING / CONFIRMED / COMPLETED) à la fois.
-- ═══════════════════════════════════════════════════════════

CREATE UNIQUE INDEX uq_bookings_active_time_slot
    ON bookings (time_slot_id)
    WHERE status <> 'CANCELLED';