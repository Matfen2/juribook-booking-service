package juribook.booking_service.entity;

/**
 * UPLOADED : stocké temporairement, en attente de traitement.
 * READY    : traité et déplacé en stockage permanent, jamais atteint
 *            par ce sprint, réservé au futur consumer asynchrone.
 */
public enum DocumentStatus {
    UPLOADED,
    READY
}