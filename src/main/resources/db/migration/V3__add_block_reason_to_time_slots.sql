-- ═══════════════════════════════════════════════════════════
--  Ajout de block_reason sur time_slots
--  Motif de blocage (congés, indisponibilités)
-- ═══════════════════════════════════════════════════════════

ALTER TABLE time_slots
    ADD COLUMN block_reason VARCHAR(255);