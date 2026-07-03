# juribook-booking-service

Microservice de gestion des disponibilités, créneaux et réservations pour **JuriBook** : déclaration des disponibilités récurrentes par les avocats, génération automatique des créneaux concrets, gestion ponctuelle (ajout, blocage de période, déblocage), consultation publique des créneaux libres, réservation par les clients avec protection contre la double réservation, confirmation/refus par les avocats, annulation encadrée par une règle des 24h, liste d'attente sur avocat complet, historique des réservations client, tableau de bord avocat, rappel automatique 24h avant le rendez-vous, synchronisation avec le statut de disponibilité de l'avocat (lawyer-service), et publication d'événements Kafka à chaque changement d'état.

## Stack

- Java 21 · Spring Boot 4.1.0 · Maven
- Spring Security · JWT (validation des tokens émis par l'auth-service)
- PostgreSQL 16 · Flyway (migrations)
- Apache Kafka (producer **et** consumer, voir [Kafka](#kafka))
- Spring Scheduling (`@Scheduled`, job de rappel 24h)
- Springdoc OpenAPI (Swagger UI)
- Port : **8083**

## Structure du projet

```
src/main/java/juribook/booking_service/
├── config/
│   ├── SecurityConfig.java               # Règles d'accès par rôle + CORS + filtre JWT
│   ├── OpenApiConfig.java                # Configuration Swagger UI
│   └── SchedulingConfig.java             # Active @Scheduled - nécessaire à BookingReminderJob
├── controller/
│   ├── AvailabilityController.java       # POST/GET/DELETE /api/lawyers/{id}/availabilities
│   ├── TimeSlotController.java           # POST/GET/DELETE /api/lawyers/{id}/slots + block/unblock
│   ├── BookingController.java            # POST/GET /api/bookings + GET /{id} + PATCH confirm/reject/cancel
│   ├── LawyerBookingsController.java     # GET /api/lawyers/{id}/bookings, tableau de bord avocat
│   └── WaitlistController.java           # POST/GET /api/waitlist/{lawyerId}
├── dto/
│   ├── request/
│   │   ├── CreateAvailabilityRequest.java
│   │   ├── CreateTimeSlotRequest.java
│   │   ├── BlockPeriodRequest.java
│   │   └── CreateBookingRequest.java
│   └── response/
│       ├── AvailabilityResponse.java
│       ├── TimeSlotResponse.java
│       ├── BlockPeriodResponse.java
│       ├── BookingResponse.java
│       ├── BookingHistoryResponse.java   # BookingResponse enrichi de la date/heure du créneau
│       └── WaitlistEntryResponse.java
├── entity/
│   ├── Availability.java                 # Disponibilité récurrente hebdomadaire
│   ├── TimeSlot.java                     # Créneau concret daté, réservable
│   ├── SlotStatus.java                   # AVAILABLE | BOOKED | BLOCKED | CANCELLED | COMPLETED
│   ├── Booking.java                      # Réservation d'un créneau par un client — inclut reminderSent
│   ├── BookingStatus.java                # PENDING | CONFIRMED | COMPLETED | CANCELLED
│   ├── WaitlistEntry.java                # Inscription d'un client sur la liste d'attente d'un avocat
│   └── LawyerStatusCache.java            # Cache local de disponibilité avocat, synchronisé via lawyer-events
├── event/
│   ├── BookingEventPublisher.java        # Interface — booking.created/confirmed/cancelled/reminder
│   ├── BookingEvent.java                 # Payload JSON booking-events
│   ├── BookingEventPublisherImpl.java    # Impl unique, décide à l'exécution via ObjectProvider<KafkaTemplate>
│   ├── SlotEventPublisher.java           # Interface — événements slot-events
│   ├── SlotReleasedEvent.java            # Payload JSON slot.released (lawyerId + slotId)
│   ├── SlotEventPublisherImpl.java       # Impl unique, même pattern que BookingEventPublisherImpl
│   ├── LawyerEvent.java                  # Miroir du payload lawyer-events (lawyer-service)
│   └── LawyerEventConsumer.java          # @KafkaListener sur lawyer-events — première consommation de ce service
├── exception/
│   ├── GlobalExceptionHandler.java       # Handlers 400/403/404/409/500
│   ├── InvalidAvailabilityException.java
│   ├── AvailabilityNotFoundException.java
│   ├── InvalidTimeSlotException.java
│   ├── TimeSlotNotFoundException.java
│   ├── BookingConflictException.java     # 409 - créneau déjà réservé
│   ├── BookingNotFoundException.java     # 404 - réservation introuvable
│   ├── InvalidBookingException.java      # 400 - transition de statut Booking invalide (inclut avocat indisponible)
│   └── AlreadyOnWaitlistException.java   # 409 - déjà inscrit sur cette liste d'attente
├── filter/
│   └── JwtAuthenticationFilter.java      # Filtre Spring Security (OncePerRequestFilter)
├── job/
│   └── BookingReminderJob.java           # @Scheduled — rappel 24h avant le rendez-vous
├── repository/
│   ├── AvailabilityRepository.java
│   ├── TimeSlotRepository.java           # Inclut findByIdForUpdate (verrou pessimiste)
│   ├── BookingRepository.java            # findByClientId, findByLawyerId, findByStatusAndReminderSentFalse, findByLawyerIdAndStatus
│   ├── WaitlistRepository.java
│   └── LawyerStatusCacheRepository.java  # Sprint 5.9
└── service/
    ├── AvailabilityService.java          # Validation, chevauchement, génération des créneaux
    ├── TimeSlotService.java              # Créneaux ponctuels, blocage de période, consultation
    ├── BookingService.java               # Réservation, confirmation/refus, annulation, historique, tableau de bord, détail inter-services, synchronisation avocat, événements
    ├── WaitlistService.java              # Inscription et consultation de la liste d'attente
    └── JwtService.java                   # Validation des tokens JWT (lecture seule)
src/main/resources/
├── application.yaml
└── db/migration/
    ├── V1__create_availabilities_table.sql
    ├── V2__create_time_slots_table.sql
    ├── V3__add_block_reason_to_time_slots.sql
    ├── V4__create_bookings_table.sql
    ├── V5__add_unique_active_booking_per_slot.sql
    ├── V6__create_waitlist_entries_table.sql
    ├── V7__add_reminder_sent_to_bookings.sql
    └── V8__create_lawyer_status_cache_table.sql
src/test/java/juribook/booking_service/
├── entity/
│   └── TimeSlotTest.java
└── service/
    ├── AvailabilityServiceTest.java
    ├── TimeSlotServiceTest.java
    ├── BookingServiceTest.java
    ├── BookingLifecycleTest.java
    └── WaitlistServiceTest.java
```

## Lancer en local (hors Docker)

```bash
# Prérequis : PostgreSQL sur localhost:5434 avec la base bookingdb
mvn spring-boot:run
```

## Lancer via Docker Compose

```bash
# Depuis juribook-docker/docker/
docker compose up -d postgres-booking booking-service
```

## Lancer les tests

```bash
mvn test
```

## Swagger UI

[http://localhost:8083/swagger-ui.html](http://localhost:8083/swagger-ui.html)

## Health check

[http://localhost:8083/actuator/health](http://localhost:8083/actuator/health)

---

## Endpoints

### Publics (pas de token requis)

| Méthode | URL | Description |
|---|---|---|
| `GET` | `/api/lawyers/{lawyerId}/availabilities` | Liste des disponibilités récurrentes d'un avocat |
| `GET` | `/api/lawyers/{lawyerId}/slots` | Consultation des créneaux, deux modes, voir ci-dessous |
| `GET` | `/api/waitlist/{lawyerId}` | Liste des clients en attente pour un avocat |
| `GET` | `/api/bookings/{id}` | Détail enrichi d'une réservation par id (résolution inter-services) |

### Protégés LAWYER (token JWT requis, rôle LAWYER)

| Méthode | URL | Description |
|---|---|---|
| `POST` | `/api/lawyers/{lawyerId}/availabilities` | Déclarer une disponibilité récurrente |
| `DELETE` | `/api/lawyers/{lawyerId}/availabilities/{id}` | Désactiver une disponibilité |
| `POST` | `/api/lawyers/{lawyerId}/slots` | Ajouter un créneau ponctuel |
| `DELETE` | `/api/lawyers/{lawyerId}/slots/{id}` | Supprimer un créneau |
| `POST` | `/api/lawyers/{lawyerId}/slots/block` | Bloquer une période |
| `POST` | `/api/lawyers/{lawyerId}/slots/{id}/unblock` | Débloquer un créneau |
| `GET` | `/api/lawyers/{lawyerId}/bookings` | Tableau de bord : réservations de l'avocat, triées de la plus proche à la plus lointaine |
| `PATCH` | `/api/bookings/{id}/confirm` | Confirmer une demande PENDING |
| `PATCH` | `/api/bookings/{id}/reject` | Refuser une demande PENDING |

### Protégés CLIENT (token JWT requis, rôle CLIENT)

| Méthode | URL | Description |
|---|---|---|
| `POST` | `/api/bookings` | Réserver un créneau, **refusé si l'avocat est indisponible** |
| `GET` | `/api/bookings` | Historique de mes réservations |
| `POST` | `/api/waitlist/{lawyerId}` | S'inscrire sur la liste d'attente |

### Protégés CLIENT ou LAWYER (token JWT requis)

| Méthode | URL | Description |
|---|---|---|
| `PATCH` | `/api/bookings/{id}/cancel` | Annuler une réservation CONFIRMED, règle des 24h |

---

## Synchronisation avec la disponibilité de l'avocat

### Le problème (UC-K4 du cahier des charges)

Un avocat se marque indisponible (`PUT /api/lawyers/profile` avec `available: false` côté `lawyer-service`). Sans synchronisation, `booking-service` continuerait à accepter de nouvelles réservations pour lui, il n'a aucune notion de cet état, ses seules données sont les `TimeSlot`, indépendants du statut global de l'avocat.

### La solution

`lawyer-service` publie `lawyer.status-changed` sur `lawyer-events` à chaque changement réel d'`available` (cf. README de `lawyer-service`). `LawyerEventConsumer` (première consommation Kafka de ce service, qui n'avait fait que produire jusqu'ici) maintient un cache local, `LawyerStatusCache` (une ligne par avocat, upsert à chaque événement) :

```java
@KafkaListener(topics = "lawyer-events", groupId = "${spring.kafka.consumer.group-id}")
public void onLawyerEvent(String payload) {
    // 1. Met à jour LawyerStatusCache
    // 2. Si available devient false : annule automatiquement les
    //    réservations PENDING de cet avocat (cf. ci-dessous)
}
```

`BookingService.createBooking` lit ce cache **localement**, de façon synchrone, avant toute réservation — jamais d'appel réseau au `lawyer-service` à chaque `POST /api/bookings`. C'est le principe de cohérence éventuelle (eventual consistency) plutôt que synchrone : il peut exister une courte fenêtre entre le changement réel côté `lawyer-service` et sa prise en compte ici, le temps que l'événement Kafka soit consommé, acceptable pour ce cas d'usage.

**Comportement fail-open** : si un `lawyerId` n'a jamais d'entrée dans le cache (avant ce sprint, ou avant son premier changement de disponibilité), la réservation est **autorisée**, pas bloquée, un avocat qui n'a jamais explicitement changé sa disponibilité ne doit pas se retrouver arbitrairement inaccessible.

### Annulation automatique des PENDING

Quand un avocat devient indisponible, `BookingService.cancelPendingBookingsForInactiveLawyer(lawyerId)` est appelée automatiquement par `LawyerEventConsumer` : toutes ses réservations encore `PENDING` (jamais validées par l'avocat) sont annulées, leurs créneaux libérés, `booking.cancelled` + `slot.released` publiés pour chacune, exactement le même comportement qu'un refus manuel (`/reject`), en boucle.

⚠️ **Limite assumée** : les réservations déjà `CONFIRMED` **ne sont pas touchées**. Un rendez-vous confirmé reste engagé même si l'avocat se marque indisponible après coup, l'annulation d'un rendez-vous confirmé reste un choix explicite via `/cancel` (avec sa règle des 24h), pas une conséquence automatique de la désactivation.

Côté `notification-service`, `booking.cancelled` déclenche désormais un vrai email au client, plus un simple log, cf. son README.

---

## Cas d'erreur

```
POST /availabilities avec endTime <= startTime          → 400 "L'heure de fin doit être après l'heure de début"
POST /availabilities chevauchant une dispo existante     → 400 "Cette plage horaire chevauche une disponibilité existante pour ce jour"
POST /slots dans le passé                                 → 400 "Impossible de créer un créneau dans le passé"
POST /slots chevauchant un créneau existant                → 400 "Ce créneau chevauche un créneau existant à cette date"
DELETE /slots/{id} sur un créneau BOOKED                  → 400 "Impossible de supprimer un créneau réservé"
DELETE /slots/{id} sur un créneau COMPLETED                → 400 "Impossible de supprimer un créneau déjà honoré"
POST /slots/{id}/unblock sur un créneau non bloqué         → 400 "Ce créneau n'est pas bloqué"
POST /bookings pour un avocat indisponible (Sprint 5.9)     → 400 "Cet avocat n'accepte plus de nouvelles réservations pour le moment"
POST /bookings sur un timeSlotId inexistant                → 404 "Créneau introuvable : id=..."
POST /bookings sur un créneau déjà BOOKED                  → 409 "Ce créneau est déjà réservé"
POST /bookings sur un créneau BLOCKED/CANCELLED/COMPLETED   → 400 "Ce créneau n'est plus disponible à la réservation"
POST /bookings sur un créneau déjà passé                   → 400 "Impossible de réserver un créneau déjà passé"
POST /bookings sans reason                                  → 400 "Le motif de consultation est obligatoire"
GET /bookings/{id} sur un bookingId inconnu                 → 404 "Réservation introuvable : id=..."
PATCH /bookings/{id}/confirm ou /reject sur bookingId inconnu → 404 "Réservation introuvable : id=..."
PATCH /bookings/{id}/confirm ou /reject si pas PENDING      → 400 "Cette action nécessite une réservation PENDING (statut actuel : ...)"
PATCH /bookings/{id}/cancel si pas CONFIRMED                → 400 "Cette action nécessite une réservation CONFIRMED (statut actuel : ...)"
PATCH /bookings/{id}/cancel à moins de 24h du rendez-vous    → 400 "Annulation impossible : le rendez-vous a lieu le ..., soit dans moins de 24h. ..."
PATCH /bookings/{id}/cancel par un client non propriétaire   → 403 "Cette réservation n'appartient pas à ce client"
POST /waitlist/{lawyerId} déjà inscrit                        → 409 "Vous êtes déjà inscrit sur la liste d'attente de cet avocat"
GET /bookings d'un client sans aucune réservation              → 200 []
GET /lawyers/{lawyerId}/bookings d'un avocat sans réservation   → 200 []
Toute route LAWYER sans token                               → 401 Unauthorized
Toute route LAWYER avec token CLIENT                         → 403 Forbidden
Toute route CLIENT avec token LAWYER                          → 403 Forbidden
```

---

## Commandes SQL utiles

### Accéder à la base PostgreSQL

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb
```

### Vérifier le cache de statut avocat

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb -c "SELECT lawyer_id, available, updated_at FROM lawyer_status_cache ORDER BY updated_at DESC;"
```

### Vérifier les candidats au rappel 24h

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb -c "SELECT b.id, b.status, b.reminder_sent, t.date, t.start_time FROM bookings b JOIN time_slots t ON t.id = b.time_slot_id WHERE b.status = 'CONFIRMED' AND b.reminder_sent = false ORDER BY t.date, t.start_time;"
```

### Compter les réservations par statut

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb -c "SELECT status, COUNT(*) FROM bookings GROUP BY status;"
```

### Vérifier les migrations Flyway

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb -c "SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank;"
```

---

## Variables d'environnement

| Variable | Description | Valeur par défaut |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL PostgreSQL | `jdbc:postgresql://localhost:5434/bookingdb` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Adresse Kafka | `localhost:9092` |
| `JWT_SECRET` | Secret JWT partagé avec l'auth-service | valeur de dev |

---

## Kafka

### Activation

Une seule implémentation par interface (`BookingEventPublisherImpl`, `SlotEventPublisherImpl`), qui décide **à l'exécution** si Kafka est disponible, via `ObjectProvider<KafkaTemplate<String, String>>` — cf. l'historique du fix (`@ConditionalOnBean` sur un `@Component` classique est cassé par l'ordre de scan vs autoconfiguration Spring Boot).

### Topics produits

| Topic | Événements | Déclencheurs |
|---|---|---|
| `booking-events` | `booking.created`, `booking.confirmed`, `booking.cancelled`, `booking.reminder` | Actions client/avocat + `BookingReminderJob` + annulation automatique |
| `slot-events` | `slot.released` | `/reject`, `/cancel`, annulation automatique |

### Topic consommé - nouveauté

| Topic | Consumer | Événements | Effet |
|---|---|---|---|
| `lawyer-events` | `LawyerEventConsumer` | `lawyer.status-changed` | Met à jour `LawyerStatusCache` + déclenche l'annulation des PENDING si l'avocat devient indisponible |

Ce service publie sur `booking-events`/`slot-events` **et** consomme `lawyer-events`.

### Vérifier manuellement (broker actif)

```bash
docker exec -it juribook-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic booking-events --from-beginning
docker exec -it juribook-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic lawyer-events --from-beginning
```

### Limites assumées

- **Publication synchrone, dans la même transaction `@Transactional`** que l'écriture en base (pas de pattern Outbox).

---

## Job planifié — rappel 24h (Sprint 5.5)

`BookingReminderJob` (`@Scheduled(fixedRate = 15, TimeUnit.MINUTES)`) cherche les réservations `CONFIRMED` pas encore rappelées (`reminderSent = false`), publie `booking.reminder` pour celles dont le rendez-vous a lieu dans moins de 24h. `reminderSent` évite les doublons entre deux exécutions.

---

## Tests

```bash
mvn test
```

| Fichier | Ce qu'il couvre |
|---|---|
| `AvailabilityServiceTest.java` | Chevauchement, génération, idempotence |
| `TimeSlotServiceTest.java` | Chevauchement, blocage, suppression, consultation |
| `TimeSlotTest.java` | `isBookable()` |
| `BookingServiceTest.java` | create/confirm/reject/cancel/getMyBookings, 409, règle 24h |
| `BookingLifecycleTest.java` | Cycle complet create→confirm→cancel et create→reject |
| `WaitlistServiceTest.java` | Inscription, doublon, race condition, consultation |

⚠️ Pas encore de test dédié pour `LawyerEventConsumer`/`cancelPendingBookingsForInactiveLawyer`, à couvrir dans une prochaine itération.

---

## Sécurité JWT

Le booking-service **ne génère pas** de JWT, il valide uniquement les tokens émis par l'auth-service.

---

## Limites connues

- **`lawyerId` du path non vérifié contre l'utilisateur authentifié** : pas de résolution `authUserId → lawyerId` sans appel au `lawyer-service`.
- **Créneaux passés du jour même non filtrés à l'affichage**.
- **Aucune vérification d'appartenance côté avocat sur `confirm`/`reject`/`cancel`**, côté client, la vérification est active sur `/cancel`.
- **Publication d'événements Kafka non transactionnelle avec la base** (pas de pattern Outbox).
- **Inscription à la liste d'attente sans vérifier que l'avocat est réellement complet**.
- **`GET /api/bookings/{id}` public, sans restriction**, pensé pour un usage inter-services.
- **`BookingReminderJob` non résilient à une instance multiple**, pas de verrou distribué.
- **Le cache `LawyerStatusCache` n'est jamais nettoyé** : un avocat supprimé côté `lawyer-service` (hypothèse non gérée à ce stade du projet, pas d'endpoint de suppression) laisserait une entrée orpheline ici, sans conséquence pratique (juste une ligne inutile).
- **Les réservations `CONFIRMED` d'un avocat désactivé ne sont pas automatiquement annulées**, volontaire, cf. section dédiée.