-- ═══════════════════════════════════════════════════════════
--  Table time_slots - créneaux concrets réservables
--  Sprint 3.1
-- ═══════════════════════════════════════════════════════════

CREATE TABLE time_slots (
    id                  BIGSERIAL PRIMARY KEY,

    -- Lien logique vers Lawyer du lawyer-service
    lawyer_id           BIGINT NOT NULL,

    -- Lien optionnel vers la disponibilité récurrente source
    -- (null si créneau créé ponctuellement)
    availability_id     BIGINT,

    -- Date et horaires du créneau
    date                DATE NOT NULL,
    start_time          TIME NOT NULL,
    end_time            TIME NOT NULL,

    -- Statut du créneau
    status              VARCHAR(10) NOT NULL DEFAULT 'AVAILABLE',

    -- Lien vers la réservation associée (renseigné si status = BOOKED)
    -- Sprint 3.2 - pas de FK stricte, booking_id pointera vers une future table bookings
    booking_id          BIGINT,

    -- Audit
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),

    -- Contrainte métier : l'heure de fin doit être après l'heure de début
    CONSTRAINT chk_timeslot_time_order CHECK (end_time > start_time),

    -- Contrainte métier : statut parmi les valeurs autorisées
    CONSTRAINT chk_timeslot_status CHECK (
        status IN ('AVAILABLE', 'BOOKED', 'BLOCKED', 'CANCELLED', 'COMPLETED')
    ),

    -- Un avocat ne peut pas avoir deux créneaux identiques (même date + même heure de début)
    CONSTRAINT uq_timeslot_lawyer_date_start UNIQUE (lawyer_id, date, start_time)
);

-- Index pour les requêtes fréquentes du service
CREATE INDEX idx_time_slots_lawyer_id ON time_slots(lawyer_id);
CREATE INDEX idx_time_slots_lawyer_date ON time_slots(lawyer_id, date);
CREATE INDEX idx_time_slots_lawyer_status ON time_slots(lawyer_id, status);
CREATE INDEX idx_time_slots_availability_id ON time_slots(availability_id);