-- ═══════════════════════════════════════════════════════════
--  Table bookings - réservations de créneaux par les clients
-- ═══════════════════════════════════════════════════════════

CREATE TABLE bookings (
    id                  BIGSERIAL PRIMARY KEY,

    -- Lien logique vers User (rôle CLIENT) de l'auth-service
    client_id           BIGINT NOT NULL,

    -- Lien logique vers Lawyer du lawyer-service (dénormalisé depuis le créneau)
    lawyer_id           BIGINT NOT NULL,

    -- Lien vers le créneau réservé (table time_slots, même base de données,
    -- pas de FK stricte, cf. Booking.java pour la justification)
    time_slot_id        BIGINT NOT NULL,

    -- Statut et motif
    status              VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    reason              VARCHAR(500),

    -- Audit
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),

    -- Contrainte métier : statut parmi les valeurs autorisées
    CONSTRAINT chk_booking_status CHECK (
        status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED')
    )
);

-- Index pour les requêtes fréquentes du service
CREATE INDEX idx_bookings_client_id ON bookings(client_id);
CREATE INDEX idx_bookings_lawyer_id ON bookings(lawyer_id);
CREATE INDEX idx_bookings_time_slot_id ON bookings(time_slot_id);
CREATE INDEX idx_bookings_status ON bookings(status);