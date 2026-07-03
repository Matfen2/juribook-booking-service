-- Cache local, synchronisé via Kafka (lawyer-events), de la
-- disponibilité d'un avocat pour de nouvelles réservations.
--
-- lawyerId est directement la clé primaire (pas d'auto-génération) :
-- une seule ligne par avocat, upsert à chaque événement reçu.
CREATE TABLE lawyer_status_cache (
    lawyer_id   BIGINT PRIMARY KEY,
    available   BOOLEAN NOT NULL,
    updated_at  TIMESTAMP NOT NULL
);