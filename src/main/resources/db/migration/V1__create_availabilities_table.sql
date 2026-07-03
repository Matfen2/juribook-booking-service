-- ═══════════════════════════════════════════════════════════
--  Table availabilities - disponibilités récurrentes d'un avocat
--  Sprint 3.1
-- ═══════════════════════════════════════════════════════════

CREATE TABLE availabilities (
    id                      BIGSERIAL PRIMARY KEY,

    -- Lien logique vers Lawyer du lawyer-service (pas de FK inter-services)
    lawyer_id               BIGINT NOT NULL,

    -- Récurrence hebdomadaire
    day_of_week             VARCHAR(10) NOT NULL,   -- MONDAY, TUESDAY, ... (java.time.DayOfWeek)
    start_time              TIME NOT NULL,
    end_time                TIME NOT NULL,
    slot_duration_minutes   INTEGER NOT NULL DEFAULT 30,

    -- Statut et fenêtre de validité
    active                  BOOLEAN NOT NULL DEFAULT true,
    valid_from              DATE,
    valid_until             DATE,

    -- Audit
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now(),

    -- Contrainte métier : l'heure de fin doit être après l'heure de début
    CONSTRAINT chk_availability_time_order CHECK (end_time > start_time),

    -- Contrainte métier : la durée d'un créneau doit être positive
    CONSTRAINT chk_slot_duration_positive CHECK (slot_duration_minutes > 0)
);

-- Index pour la recherche fréquente "disponibilités actives d'un avocat"
CREATE INDEX idx_availabilities_lawyer_id ON availabilities(lawyer_id);
CREATE INDEX idx_availabilities_lawyer_active ON availabilities(lawyer_id, active);