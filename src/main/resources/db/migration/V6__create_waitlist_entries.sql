-- ═══════════════════════════════════════════════════════════
--  Table waitlist_entries - liste d'attente d'un avocat complet
-- ═══════════════════════════════════════════════════════════

CREATE TABLE waitlist_entries (
    id          BIGSERIAL PRIMARY KEY,

    -- Lien logique vers Lawyer du lawyer-service
    lawyer_id   BIGINT NOT NULL,

    -- Lien logique vers User (rôle CLIENT) de l'auth-service
    client_id   BIGINT NOT NULL,

    created_at  TIMESTAMP NOT NULL DEFAULT now(),

    -- Un client ne peut s'inscrire qu'une fois sur la liste d'attente
    -- d'un même avocat
    CONSTRAINT uq_waitlist_lawyer_client UNIQUE (lawyer_id, client_id)
);

CREATE INDEX idx_waitlist_lawyer_id ON waitlist_entries(lawyer_id);
CREATE INDEX idx_waitlist_client_id ON waitlist_entries(client_id);