# juribook-booking-service

Microservice de gestion des disponibilités, créneaux et réservations pour **JuriBook** : déclaration des disponibilités récurrentes par les avocats, génération automatique des créneaux concrets, gestion ponctuelle (ajout, blocage de période, déblocage), consultation publique des créneaux libres, réservation par les clients avec protection contre la double réservation, confirmation/refus par les avocats, annulation encadrée par une règle des 24h, liste d'attente sur avocat complet, historique des réservations client, tableau de bord des réservations avocat, et publication d'événements Kafka à chaque changement d'état.

## Stack

- Java 21 · Spring Boot 4.1.0 · Maven
- Spring Security · JWT (validation des tokens émis par l'auth-service)
- PostgreSQL 16 · Flyway (migrations)
- Apache Kafka (producer conditionnel, voir [Kafka](#kafka))
- Springdoc OpenAPI (Swagger UI)
- Port : **8083**

## Structure du projet

```
src/main/java/juribook/booking_service/
├── config/
│   ├── SecurityConfig.java               # Règles d'accès par rôle + CORS + filtre JWT
│   └── OpenApiConfig.java                # Configuration Swagger UI
├── controller/
│   ├── AvailabilityController.java       # POST/GET/DELETE /api/lawyers/{id}/availabilities
│   ├── TimeSlotController.java           # POST/GET/DELETE /api/lawyers/{id}/slots + block/unblock
│   ├── BookingController.java            # POST/GET /api/bookings + PATCH confirm/reject/cancel
│   ├── LawyerBookingsController.java     # GET /api/lawyers/{id}/bookings — tableau de bord avocat
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
│   ├── Booking.java                      # Réservation d'un créneau par un client
│   ├── BookingStatus.java                # PENDING | CONFIRMED | COMPLETED | CANCELLED
│   └── WaitlistEntry.java                # Inscription d'un client sur la liste d'attente d'un avocat
├── event/
│   ├── BookingEventPublisher.java        # Interface - événements booking-events
│   ├── BookingEvent.java                 # Payload JSON booking.created/confirmed/cancelled
│   ├── KafkaBookingEventPublisher.java   # Impl réelle, active si KafkaTemplate en contexte
│   ├── NoOpBookingEventPublisher.java    # Impl de repli, dev local sans broker
│   ├── SlotEventPublisher.java           # Interface - événements slot-events
│   ├── SlotReleasedEvent.java            # Payload JSON slot.released (lawyerId + slotId)
│   ├── KafkaSlotEventPublisher.java      # Impl réelle, active si KafkaTemplate en contexte
│   └── NoOpSlotEventPublisher.java       # Impl de repli, dev local sans broker
├── exception/
│   ├── GlobalExceptionHandler.java       # Handlers 400/403/404/409/500
│   ├── InvalidAvailabilityException.java
│   ├── AvailabilityNotFoundException.java
│   ├── InvalidTimeSlotException.java
│   ├── TimeSlotNotFoundException.java
│   ├── BookingConflictException.java     # 409 - créneau déjà réservé
│   ├── BookingNotFoundException.java     # 404 - réservation introuvable
│   ├── InvalidBookingException.java      # 400 - transition de statut Booking invalide
│   └── AlreadyOnWaitlistException.java   # 409 - déjà inscrit sur cette liste d'attente
├── filter/
│   └── JwtAuthenticationFilter.java      # Filtre Spring Security (OncePerRequestFilter)
├── repository/
│   ├── AvailabilityRepository.java
│   ├── TimeSlotRepository.java           # Inclut findByIdForUpdate (verrou pessimiste)
│   ├── BookingRepository.java            # Inclut findByClientId (historique) et findByLawyerId (tableau de bord)
│   └── WaitlistRepository.java
└── service/
    ├── AvailabilityService.java          # Validation, chevauchement, génération des créneaux
    ├── TimeSlotService.java              # Créneaux ponctuels, blocage de période, consultation
    ├── BookingService.java               # Réservation, confirmation/refus, annulation, historique, tableau de bord, événements
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
    └── V6__create_waitlist_entries_table.sql
src/test/java/juribook/booking_service/
├── entity/
│   └── TimeSlotTest.java                 # isBookable() - créneaux passés/futurs, statuts
└── service/
    ├── AvailabilityServiceTest.java      # Chevauchement, génération, idempotence
    ├── TimeSlotServiceTest.java          # Chevauchement, blocage, suppression, consultation
    ├── BookingServiceTest.java           # create/confirm/reject/cancel/getMyBookings, 409, règle 24h
    ├── BookingLifecycleTest.java         # Cycle complet create→confirm→cancel et create→reject
    └── WaitlistServiceTest.java          # Inscription, doublon, race condition, consultation
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
| `GET` | `/api/lawyers/{lawyerId}/availabilities` | Liste des disponibilités récurrentes d'un avocat (actives et inactives) |
| `GET` | `/api/lawyers/{lawyerId}/slots` | Consultation des créneaux, deux modes, voir ci-dessous |
| `GET` | `/api/waitlist/{lawyerId}` | Liste des clients en attente pour un avocat (consommé par le notification-service) |

### Protégés LAWYER (token JWT requis, rôle LAWYER)

| Méthode | URL | Description |
|---|---|---|
| `POST` | `/api/lawyers/{lawyerId}/availabilities` | Déclarer une disponibilité récurrente (génère immédiatement les créneaux) |
| `DELETE` | `/api/lawyers/{lawyerId}/availabilities/{id}` | Désactiver une disponibilité (ne supprime pas les créneaux déjà générés) |
| `POST` | `/api/lawyers/{lawyerId}/slots` | Ajouter un créneau ponctuel, hors récurrence |
| `DELETE` | `/api/lawyers/{lawyerId}/slots/{id}` | Supprimer un créneau (refusé si BOOKED ou COMPLETED) |
| `POST` | `/api/lawyers/{lawyerId}/slots/block` | Bloquer une période (congés, indisponibilité) |
| `POST` | `/api/lawyers/{lawyerId}/slots/{id}/unblock` | Débloquer un créneau |
| `GET` | `/api/lawyers/{lawyerId}/bookings` | Tableau de bord : toutes les réservations de l'avocat, triées de la plus proche à la plus lointaine |
| `PATCH` | `/api/bookings/{id}/confirm` | Confirmer une demande de réservation PENDING, publie `booking.confirmed` |
| `PATCH` | `/api/bookings/{id}/reject` | Refuser une demande de réservation PENDING, publie `booking.cancelled` + `slot.released` |

### Protégés CLIENT (token JWT requis, rôle CLIENT)

| Méthode | URL | Description |
|---|---|---|
| `POST` | `/api/bookings` | Réserver un créneau, crée une réservation PENDING, marque le créneau BOOKED, protégé contre la double réservation (409), publie `booking.created` |
| `GET` | `/api/bookings` | Historique de mes réservations, enrichi de la date/heure du créneau, trié du plus récent au plus ancien |
| `POST` | `/api/waitlist/{lawyerId}` | S'inscrire sur la liste d'attente d'un avocat |

### Protégés CLIENT ou LAWYER (token JWT requis)

| Méthode | URL | Description |
|---|---|---|
| `PATCH` | `/api/bookings/{id}/cancel` | Annuler une réservation CONFIRMED, refusé si moins de 24h avant le rendez-vous, publie `booking.cancelled` + `slot.released` |

---

## Exemples Postman

### Déclarer une disponibilité récurrente
```json
POST http://localhost:8083/api/lawyers/1/availabilities
Authorization: Bearer <token_jwt_avocat>
Content-Type: application/json

{
    "dayOfWeek": "MONDAY",
    "startTime": "09:00:00",
    "endTime": "17:00:00",
    "slotDurationMinutes": 30,
    "generationWeeks": 4
}
```
Réponse - 201, génère immédiatement les créneaux sur la fenêtre demandée :
```json
{
    "id": 1,
    "lawyerId": 1,
    "dayOfWeek": "MONDAY",
    "startTime": "09:00:00",
    "endTime": "17:00:00",
    "slotDurationMinutes": 30,
    "active": true,
    "validFrom": "2026-06-30",
    "generatedSlotsCount": 64
}
```

---

### Lister les disponibilités d'un avocat (public)
```
GET http://localhost:8083/api/lawyers/1/availabilities
```

---

### Consulter les créneaux libres d'un jour donné (public)
```
GET http://localhost:8083/api/lawyers/1/slots?date=2026-07-06
```
Mode `date` : retourne uniquement les créneaux **AVAILABLE** de ce jour précis, usage typique côté client cherchant un rendez-vous.

---

### Consulter le planning complet d'un avocat (public)
```
GET http://localhost:8083/api/lawyers/1/slots?fromDate=2026-07-01&toDate=2026-07-31
```
Mode `fromDate`/`toDate` : tous statuts confondus par défaut (défaut si aucun paramètre : aujourd'hui → +1 mois).

---

### Forcer un statut précis
```
GET http://localhost:8083/api/lawyers/1/slots?date=2026-07-06&status=BOOKED
```
`status` prime toujours sur le comportement par défaut.

---

### Ajouter un créneau ponctuel
```json
POST http://localhost:8083/api/lawyers/1/slots
Authorization: Bearer <token_jwt_avocat>
Content-Type: application/json

{
    "date": "2026-07-11",
    "startTime": "10:00:00",
    "endTime": "10:30:00"
}
```
Réponse - 201. Refusé (400) si le créneau est dans le passé ou chevauche un créneau existant.

---

### Bloquer une période (congés)
```json
POST http://localhost:8083/api/lawyers/1/slots/block
Authorization: Bearer <token_jwt_avocat>
Content-Type: application/json

{
    "fromDate": "2026-08-01",
    "toDate": "2026-08-15",
    "reason": "Congés"
}
```
Réponse - 200, bascule tous les créneaux **AVAILABLE** de la période en **BLOCKED**. Les créneaux déjà **BOOKED** ne sont jamais affectés.
```json
{
    "fromDate": "2026-08-01",
    "toDate": "2026-08-15",
    "reason": "Congés",
    "blockedSlotsCount": 30,
    "blockedSlots": [ ... ]
}
```

---

### Débloquer un créneau
```
POST http://localhost:8083/api/lawyers/1/slots/42/unblock
Authorization: Bearer <token_jwt_avocat>
```
Réponse - 200, remet le créneau en **AVAILABLE**. Échoue (400) si le créneau n'est pas **BLOCKED**.

---

### Supprimer un créneau
```
DELETE http://localhost:8083/api/lawyers/1/slots/42
Authorization: Bearer <token_jwt_avocat>
```
Réponse - 204. Refusé (400) si le créneau est **BOOKED** ou **COMPLETED**.

---

### Réserver un créneau (client)
```json
POST http://localhost:8083/api/bookings
Authorization: Bearer <token_jwt_client>
Content-Type: application/json

{
    "timeSlotId": 67,
    "reason": "Litige avec mon employeur"
}
```
Réponse - 201. Le `clientId` n'est jamais pris dans le body, il est extrait du JWT. Le créneau passe immédiatement en **BOOKED**, la réservation est créée en statut **PENDING** (en attente de réponse de l'avocat) :
```json
{
    "id": 1,
    "clientId": 1,
    "lawyerId": 1,
    "timeSlotId": 67,
    "status": "PENDING",
    "reason": "Litige avec mon employeur",
    "createdAt": "2026-06-30T21:29:51.633193",
    "updatedAt": "2026-06-30T21:29:51.633193"
}
```
Publie un événement `booking.created` sur `booking-events`, voir [Kafka](#kafka).

---

### Réserver un créneau déjà pris
```json
POST http://localhost:8083/api/bookings
Authorization: Bearer <token_jwt_client>
Content-Type: application/json

{
    "timeSlotId": 67,
    "reason": "Deuxième tentative"
}
```
Réponse - **409 Conflict** si le créneau 67 est déjà `BOOKED` :
```json
{
    "message": "Ce créneau est déjà réservé"
}
```
Garanti même en cas de requêtes simultanées sur le même créneau, voir [Protection anti-concurrence](#protection-anti-concurrence).

---

### Confirmer une réservation (avocat)
```
PATCH http://localhost:8083/api/bookings/1/confirm
Authorization: Bearer <token_jwt_avocat>
```
Réponse - 200, le `Booking` passe en **CONFIRMED**. Le créneau reste **BOOKED** (aucun changement). Refusé (400) si la réservation n'est pas en statut **PENDING**. Publie `booking.confirmed` sur `booking-events`.

---

### Refuser une réservation (avocat)
```
PATCH http://localhost:8083/api/bookings/2/reject
Authorization: Bearer <token_jwt_avocat>
```
Réponse - 200, le `Booking` passe en **CANCELLED** et le créneau associé est immédiatement libéré (retour à **AVAILABLE**). Refusé (400) si la réservation n'est pas en statut **PENDING**. Pas de règle des 24h : rien n'a encore été confirmé. Publie `booking.cancelled` sur `booking-events` **et** `slot.released` sur `slot-events`.

---

### Annuler une réservation confirmée (client ou avocat)
```
PATCH http://localhost:8083/api/bookings/1/cancel
Authorization: Bearer <token_jwt_client_ou_avocat>
```
Réponse - 200 si le rendez-vous a lieu dans **plus de 24h**. Le `Booking` passe en **CANCELLED**, le créneau repasse en **AVAILABLE**. Réservé aux réservations **CONFIRMED** (un `PENDING` passe par `/confirm` ou `/reject`). Publie `booking.cancelled` sur `booking-events` **et** `slot.released` sur `slot-events`.

Refusé — **400** si moins de 24h avant le rendez-vous :
```json
{
    "message": "Annulation impossible : le rendez-vous a lieu le 01/07/2026 à 23:00, soit dans moins de 24h. Contactez directement l'autre partie pour convenir d'une solution."
}
```

Refusé — **403** si un client tente d'annuler la réservation d'un autre client :
```json
{
    "message": "Cette réservation n'appartient pas à ce client"
}
```

---

### S'inscrire sur la liste d'attente d'un avocat
```
POST http://localhost:8083/api/waitlist/1
Authorization: Bearer <token_jwt_client>
```
Réponse - 201 :
```json
{
    "id": 1,
    "lawyerId": 1,
    "clientId": 1,
    "createdAt": "2026-07-01T..."
}
```
Refusé — **409** si déjà inscrit sur la liste d'attente de cet avocat :
```json
{
    "message": "Vous êtes déjà inscrit sur la liste d'attente de cet avocat"
}
```
⚠️ Aucune vérification que l'avocat est réellement complet (aucun créneau `AVAILABLE`), cf. [Limites connues](#limites-connues).

---

### Consulter la liste d'attente d'un avocat (public)
```
GET http://localhost:8083/api/waitlist/1
```
Réponse - 200, triée par ordre d'inscription. Consommé par le `notification-service` (pas ce dépôt) pour résoudre les clients à notifier suite à un événement `slot.released` :
```json
[
    { "id": 1, "lawyerId": 1, "clientId": 3, "createdAt": "2026-07-01T09:00:00" },
    { "id": 2, "lawyerId": 1, "clientId": 7, "createdAt": "2026-07-01T10:15:00" }
]
```

---

### Consulter mon historique de réservations (client)
```
GET http://localhost:8083/api/bookings
Authorization: Bearer <token_jwt_client>
```
Réponse - 200, tous statuts confondus, triée du rendez-vous le plus récent au plus ancien. Chaque entrée est enrichie de la date/heure du créneau (résolues côté service, `Booking` ne stocke que `timeSlotId`) :
```json
[
    {
        "id": 2,
        "lawyerId": 2,
        "timeSlotId": 1,
        "status": "PENDING",
        "reason": "Litige avec mon employeur",
        "date": "2026-07-04",
        "startTime": "09:00:00",
        "endTime": "09:30:00",
        "createdAt": "2026-07-01T..."
    }
]
```

---

### Consulter le tableau de bord d'un avocat
```
GET http://localhost:8083/api/lawyers/1/bookings
Authorization: Bearer <token_jwt_avocat>
```
Réponse - 200, tous statuts confondus, triée du rendez-vous **le plus proche au plus lointain** (contrairement à l'historique client, ici c'est une file à traiter, pas un journal) :
```json
[
    {
        "id": 2,
        "lawyerId": 1,
        "timeSlotId": 15,
        "status": "PENDING",
        "reason": "Consultation initiale",
        "date": "2026-07-03",
        "startTime": "10:00:00",
        "endTime": "10:30:00",
        "createdAt": "2026-07-01T..."
    },
    {
        "id": 1,
        "lawyerId": 1,
        "timeSlotId": 67,
        "status": "CONFIRMED",
        "date": "2026-07-06",
        "startTime": "09:00:00",
        "endTime": "09:30:00",
        "createdAt": "2026-06-30T..."
    }
]
```

---

### Cas d'erreur

```
POST /availabilities avec endTime <= startTime          → 400 "L'heure de fin doit être après l'heure de début"
POST /availabilities chevauchant une dispo existante     → 400 "Cette plage horaire chevauche une disponibilité existante pour ce jour"
POST /slots dans le passé                                 → 400 "Impossible de créer un créneau dans le passé"
POST /slots chevauchant un créneau existant                → 400 "Ce créneau chevauche un créneau existant à cette date"
DELETE /slots/{id} sur un créneau BOOKED                  → 400 "Impossible de supprimer un créneau réservé"
DELETE /slots/{id} sur un créneau COMPLETED                → 400 "Impossible de supprimer un créneau déjà honoré"
POST /slots/{id}/unblock sur un créneau non bloqué         → 400 "Ce créneau n'est pas bloqué"
POST /bookings sur un timeSlotId inexistant                → 404 "Créneau introuvable : id=..."
POST /bookings sur un créneau déjà BOOKED                  → 409 "Ce créneau est déjà réservé"
POST /bookings sur un créneau BLOCKED/CANCELLED/COMPLETED   → 400 "Ce créneau n'est plus disponible à la réservation"
POST /bookings sur un créneau déjà passé                   → 400 "Impossible de réserver un créneau déjà passé"
POST /bookings sans reason                                  → 400 "Le motif de consultation est obligatoire"
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

### Lister les disponibilités d'un avocat

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb -c "SELECT id, lawyer_id, day_of_week, start_time, end_time, active FROM availabilities WHERE lawyer_id = 1;"
```

### Lister les créneaux d'un avocat sur une période

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb -c "SELECT id, date, start_time, end_time, status FROM time_slots WHERE lawyer_id = 1 ORDER BY date, start_time;"
```

### Compter les créneaux par statut

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb -c "SELECT status, COUNT(*) FROM time_slots GROUP BY status;"
```

### Lister les réservations d'un client, enrichies de la date du créneau

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb -c "SELECT b.id, b.status, b.reason, t.date, t.start_time FROM bookings b JOIN time_slots t ON t.id = b.time_slot_id WHERE b.client_id = 1 ORDER BY t.date DESC, t.start_time DESC;"
```

### Lister les réservations d'un avocat, enrichies de la date du créneau

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb -c "SELECT b.id, b.status, b.client_id, t.date, t.start_time FROM bookings b JOIN time_slots t ON t.id = b.time_slot_id WHERE b.lawyer_id = 1 ORDER BY t.date ASC, t.start_time ASC;"
```

### Compter les réservations par statut

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb -c "SELECT status, COUNT(*) FROM bookings GROUP BY status;"
```

### Vérifier la cohérence créneau / réservation

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb -c "SELECT b.id AS booking_id, b.status AS booking_status, t.id AS slot_id, t.status AS slot_status FROM bookings b JOIN time_slots t ON t.id = b.time_slot_id;"
```

### Vérifier qu'aucun créneau n'a deux réservations actives (devrait toujours retourner 0 ligne)

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb -c "SELECT time_slot_id, COUNT(*) FROM bookings WHERE status <> 'CANCELLED' GROUP BY time_slot_id HAVING COUNT(*) > 1;"
```

### Lister les inscriptions à la liste d'attente d'un avocat

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb -c "SELECT id, client_id, created_at FROM waitlist_entries WHERE lawyer_id = 1 ORDER BY created_at ASC;"
```

### Vérifier les migrations Flyway

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb -c "SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank;"
```

### Réinitialiser le schéma (dev uniquement)

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
```

---

## Variables d'environnement

| Variable | Description | Valeur par défaut |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL PostgreSQL | `jdbc:postgresql://localhost:5434/bookingdb` |
| `SPRING_DATASOURCE_USERNAME` | Utilisateur PostgreSQL | `juribook` |
| `SPRING_DATASOURCE_PASSWORD` | Mot de passe PostgreSQL | `juribook` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Adresse Kafka | `localhost:9092` |
| `JWT_SECRET` | Secret JWT partagé avec l'auth-service | valeur de dev |

---

## Modèle de données

### Relations

```
Availability ──< génère >── TimeSlot   (1 Availability produit N TimeSlot)
TimeSlot     ──< réservé par >── Booking   (1 TimeSlot a au plus 1 Booking actif)
WaitlistEntry ──> Lawyer (lawyer-service)  (N clients peuvent attendre le même avocat)
```

Pas de FK JPA entre ces entités :
- `TimeSlot.availabilityId` est une colonne de corrélation nullable (nullable car un créneau peut être créé ponctuellement, hors récurrence). Cela permet de désactiver ou modifier une `Availability` sans contrainte de suppression en cascade sur des créneaux déjà réservés ou passés.
- `Booking.timeSlotId` et `TimeSlot.bookingId` (référence croisée) sont également sans FK stricte, pour le même principe de découplage et d'évolution indépendante des deux entités.

`lawyerId` (sur les quatre entités) et `clientId` (sur `Booking` et `WaitlistEntry`) référencent respectivement l'entité `Lawyer` du `lawyer-service` et l'entité `User` de l'`auth-service` par leur id technique, pas de jointure inter-services, pas de FK en base, simples colonnes de corrélation (principe microservices : *database per service*). `WaitlistEntry` porte une contrainte `UNIQUE(lawyer_id, client_id)` : un client ne peut s'inscrire qu'une fois par avocat.

### Cycle de vie d'un TimeSlot (SlotStatus)

```
AVAILABLE  → BOOKED     (un client réserve)
AVAILABLE  → BLOCKED    (l'avocat bloque le créneau manuellement)
BOOKED     → CANCELLED  (annulation côté client ou avocat)
BOOKED     → COMPLETED  (rendez-vous honoré, passé)
CANCELLED  → AVAILABLE  (le créneau redevient réservable après annulation)
BLOCKED    → AVAILABLE  (l'avocat débloque le créneau)
```

En pratique, le refus (`/reject`) et l'annulation (`/cancel`) font passer le créneau **directement** de `BOOKED` à `AVAILABLE`, sans matérialiser l'état intermédiaire `CANCELLED` en base, c'est l'événement Kafka `slot.released` qui porte l'information de la libération, pas une valeur de `SlotStatus` transitoire.

### Cycle de vie d'un Booking (BookingStatus)

```
PENDING    → CONFIRMED  (l'avocat accepte la demande)
PENDING    → CANCELLED  (l'avocat refuse la demande, pas de règle des 24h)
CONFIRMED  → CANCELLED  (annulation par le client ou l'avocat, règle des 24h)
CONFIRMED  → COMPLETED  (rendez-vous honoré - sprint à venir)
```
Contrairement à `SlotStatus`, pas de transition retour : `CANCELLED` et `COMPLETED` sont terminaux. La création d'un `Booking` (Sprint 4.2) le place toujours en `PENDING` et fait passer le `TimeSlot` associé en `BOOKED` dans la même transaction. Chaque transition publie un événement Kafka correspondant — voir [Kafka](#kafka).

### Historique client vs tableau de bord avocat

`BookingService` expose deux vues sur les mêmes données, toutes deux enrichies de la date/heure du créneau via une jointure applicative groupée (`TimeSlotRepository.findAllById`, jamais un aller-retour par réservation) :
- `getMyBookings(clientId)` : historique du client, trié du rendez-vous **le plus récent au plus ancien** (`BookingRepository.findByClientId`).
- `getLawyerBookings(lawyerId)` : file de traitement de l'avocat, triée du rendez-vous **le plus proche au plus lointain** (`BookingRepository.findByLawyerId`), c'est une file à traiter, pas un journal, donc l'échéance la plus urgente doit remonter en premier.

Les deux méthodes partagent la même logique d'enrichissement (`BookingService.enrichAndSort`), seul l'ordre de tri diffère (paramètre booléen). Le statut `COMPLETED` n'étant pas encore posé automatiquement par le système (aucun job ne fait encore la transition `CONFIRMED → COMPLETED`), la distinction passé/à venir côté UI repose en pratique sur la comparaison date/heure du créneau avec l'instant présent, pas uniquement sur le statut.

### Génération des créneaux

La création d'une `Availability` déclenche **immédiatement** la génération des `TimeSlot` concrets correspondants, sur une fenêtre glissante (4 semaines par défaut, configurable via `generationWeeks`, 1 à 12 semaines). La génération est **idempotente** : un appel répété sur la même fenêtre n'insère jamais de doublon (vérification applicative + filet de sécurité via la contrainte `UNIQUE(lawyer_id, date, start_time)` en base).

### Contrainte de chevauchement

Aucune contrainte d'exclusion PostgreSQL n'est en place pour empêcher le chevauchement de créneaux, la vérification est faite **au niveau service** (`AvailabilityService` et `TimeSlotService`), avant tout insert. Une contrainte d'exclusion serait plus robuste mais plus complexe à mettre en place avec Flyway/Hibernate ; repoussée en V2 si besoin.

### Protection anti-concurrence

La réservation (`BookingService.createBooking`) lit le `TimeSlot` via `TimeSlotRepository.findByIdForUpdate`, qui pose un verrou pessimiste en écriture (`SELECT ... FOR UPDATE`). Si deux clients réservent le même créneau au même instant :

1. La première transaction obtient le verrou, lit `AVAILABLE`, crée le `Booking`, passe le créneau en `BOOKED`, commit (et libère le verrou).
2. La seconde transaction attendait le verrou, elle l'obtient seulement après le commit de la première, relit le créneau et voit `BOOKED` → rejet immédiat avec `BookingConflictException` → **409 Conflict**.

En filet de sécurité supplémentaire (au cas où le verrou serait contourné, ex. accès direct en base), un index unique partiel (`V5__add_unique_active_booking_per_slot.sql`) empêche plus d'une réservation **active** (statut différent de `CANCELLED`) par créneau au niveau base de données. Une violation de cette contrainte est aussi convertie en 409.

### Règle des 24h

`BookingService.validateCancellationDeadline` compare `now()` à `(date + startTime du TimeSlot) - 24h`. Si l'instant présent dépasse cette limite, l'annulation est refusée avec un message explicite donnant la date/heure du rendez-vous. Cette règle ne s'applique qu'à `/cancel` (réservations `CONFIRMED`), `/reject` (réservations encore `PENDING`) n'y est pas soumis, puisque rien n'a été confirmé côté agenda de l'avocat.

---

## Kafka

### Activation

Contrôlée par `spring.autoconfigure.exclude` dans `application.yaml` :
- **Dev local** (par défaut) : les deux lignes d'exclude sont actives → aucun `KafkaTemplate` n'est créé → les publishers `NoOp*` prennent le relais (logs en `DEBUG` uniquement, aucune connexion réseau tentée).
- **Kafka actif** (test local avec broker, ou production) : commenter/supprimer les deux lignes d'exclude → Spring Boot autoconfigure normalement `KafkaTemplate` → les publishers `Kafka*` prennent le relais automatiquement, sans aucun autre changement de code.

```yaml
autoconfigure:
    exclude:
      # - org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
      # - org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration
```

En production Docker, `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092` est déjà positionné par `docker-compose.yml` (listener inter-conteneurs). En local avec le broker lancé via `docker compose up -d kafka`, la valeur par défaut `localhost:9092` (listener `PLAINTEXT_HOST` exposé sur l'hôte) convient sans rien changer.

### Topics et événements publiés

| Topic | Producteur | Événements | Déclencheurs |
|---|---|---|---|
| `booking-events` | `BookingService` (ce dépôt) | `booking.created`, `booking.confirmed`, `booking.cancelled` | `POST /bookings`, `PATCH /confirm`, `PATCH /reject`, `PATCH /cancel` |
| `slot-events` | `BookingService` (ce dépôt) | `slot.released` | `PATCH /reject`, `PATCH /cancel` (le créneau redevient AVAILABLE) |

`slot-events` est consommé par le **notification-service** (dépôt séparé, `juribook-notification-service`), sur réception de `slot.released`, il rappelle `GET /api/waitlist/{lawyerId}` sur ce service pour résoudre les clients à notifier. Le refus (`/reject`) d'une demande **PENDING** publie `booking.cancelled` (pas d'événement `booking.rejected` distinct : `BookingStatus` n'a que `PENDING` / `CONFIRMED` / `COMPLETED` / `CANCELLED`, cf. [Booking.java](#cycle-de-vie-dun-booking-bookingstatus)).

### Format des payloads

`booking-events` (`BookingEvent`) :
```json
{
    "eventType": "booking.created",
    "bookingId": 1,
    "clientId": 1,
    "lawyerId": 1,
    "timeSlotId": 67,
    "status": "PENDING",
    "reason": "Litige avec mon employeur",
    "occurredAt": "2026-07-01T12:20:43.331"
}
```

`slot-events` (`SlotReleasedEvent`) - volontairement minimal, conforme au cahier des charges (`lawyerId` + `slotId`) :
```json
{
    "eventType": "slot.released",
    "lawyerId": 1,
    "slotId": 67,
    "occurredAt": "2026-07-01T12:25:10.045"
}
```

Clé de partition Kafka = `bookingId` (pour `booking-events`) ou `slotId` (pour `slot-events`) : garantit l'ordre des événements successifs concernant une même réservation ou un même créneau au sein d'une partition.

### Vérifier manuellement (broker actif)

```bash
docker exec -it juribook-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic booking-events --from-beginning
docker exec -it juribook-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic slot-events --from-beginning
```

### Limites assumées pour ce sprint

- **Publication synchrone, dans la même transaction `@Transactional`** que l'écriture en base (pas de pattern Outbox). Si Kafka échoue, l'erreur est logguée mais la transaction métier n'est pas annulée, on privilégie la disponibilité du service de réservation à la garantie stricte de livraison de l'événement.
- **Pas de provisioning explicite des topics** (partitions, réplication, rétention), repoussé au Sprint 5.1, qui couvre la configuration complète de tous les topics Kafka de la plateforme. En dev, `KAFKA_AUTO_CREATE_TOPICS_ENABLE: true` (cf. `docker-compose.yml`) crée les topics à la volée avec les valeurs par défaut au premier message publié.

---

## Tests

63 tests unitaires (JUnit 5 + Mockito + AssertJ), aucune dépendance à une base de données ou un broker Kafka réels, tous les repositories, publishers d'événements et clients externes sont mockés.

```bash
mvn test
```

### Organisation

| Fichier | Ce qu'il couvre |
|---|---|
| `AvailabilityServiceTest.java` | Chevauchement de disponibilités récurrentes, génération des créneaux, idempotence |
| `TimeSlotServiceTest.java` | Chevauchement de créneaux ponctuels, blocage/déblocage de période, suppression, consultation filtrée |
| `TimeSlotTest.java` | `isBookable()`, créneaux passés/futurs, tous les statuts |
| `BookingServiceTest.java` | Réservation, confirmation, refus, annulation, historique client, cas nominaux et erreurs, méthode par méthode |
| `BookingLifecycleTest.java` | **Cycle complet** : create → confirm → cancel et create → reject enchaînés sur la même instance, vérifie la cohérence `TimeSlot`/`Booking` à chaque étape |
| `WaitlistServiceTest.java` | Inscription, doublon (vérification applicative et race condition via `DataIntegrityViolationException`), consultation triée |

### Points spécifiquement couverts

- **Double réservation** : verrou pessimiste (`findByIdForUpdate`) contourné en simulant deux lectures successives du même créneau ; filet de sécurité BDD testé via `DataIntegrityViolationException` sur `save()`.
- **Annulation tardive** : créneau positionné à moins de 24h dans `cancelBooking_throws_whenLessThan24hBeforeAppointment`, avec un garde-fou qui ignore le test si l'horaire calculé franchit minuit pendant l'exécution (évite un faux négatif rare en CI) ; cas symétrique `cancelBooking_succeeds_whenExactlyMoreThan24hBefore`.
- **Cycle complet** : `BookingLifecycleTest` enchaîne les transitions réelles plutôt que de les tester isolément, avec vérification qu'aucun événement Kafka superflu n'est publié en trop (`times(1)` sur chaque type d'événement).
- **Liste d'attente** : les deux chemins vers `AlreadyOnWaitlistException` (contrôle applicatif préalable et contrainte UNIQUE en base), et vérification que le filtrage par avocat est délégué au repository plutôt que fait en mémoire (évite une fuite de données entre avocats).

---

## Sécurité JWT

Le booking-service **ne génère pas** de JWT, il valide uniquement les tokens émis par l'auth-service grâce au secret partagé (`jwt.secret`).

Les claims extraits du token :
- `sub` → email de l'utilisateur
- `id` → authUserId (Long) - stocké dans `SecurityContext.principal`, utilisé directement comme `clientId` pour `POST`/`GET /api/bookings` et `POST /api/waitlist/{lawyerId}`, et comme `actorId` pour `PATCH /api/bookings/{id}/cancel`
- `role` → LAWYER | CLIENT | ADMIN - utilisé pour les règles d'accès et, pour `/cancel`, pour déterminer si la vérification d'appartenance côté client s'applique

---

## Limites connues

- **`lawyerId` du path non vérifié contre l'utilisateur authentifié** : `AvailabilityController`, `TimeSlotController` et `LawyerBookingsController` vérifient uniquement le rôle `LAWYER` du token, pas que le `lawyerId` de l'URL correspond bien à l'avocat authentifié. Cette correspondance nécessite un appel inter-services vers le `lawyer-service` (résolution `authUserId` → `lawyerId`). N'importe quel compte `LAWYER` peut donc consulter le tableau de bord d'un autre avocat. À corriger avant la mise en production.
- **Créneaux passés du jour même non filtrés à l'affichage** : `GET /slots?date=...` filtre par jour (`AVAILABLE` uniquement) mais ne tient pas compte de l'heure. Un créneau du jour déjà passé dans la journée peut donc encore apparaître dans la réponse, alors qu'il n'est plus réservable (`TimeSlot.isBookable()` existe mais n'est pas encore branché sur `TimeSlotService.getSlots()`).
- **Aucune vérification d'appartenance côté avocat sur `confirm`/`reject`/`cancel`** : même limitation de fond que ci-dessus (pas de résolution `authUserId → lawyerId` sans appel au `lawyer-service`), mais plus sensible ici, n'importe quel compte `LAWYER` peut confirmer, refuser ou annuler la réservation d'un **autre** avocat. Côté client, la vérification est en revanche active sur `/cancel` (`Booking.clientId` = `authUserId` directement, pas d'appel inter-services nécessaire) : un client ne peut annuler que ses propres réservations (403 sinon). À corriger côté avocat avant la mise en production.
- **Publication d'événements Kafka non transactionnelle avec la base** (pas de pattern Outbox) : cf. [Limites assumées pour ce sprint](#limites-assumées-pour-ce-sprint) dans la section Kafka. Un échec de publication après un commit BDD réussi ne fait pas échouer la requête HTTP, mais l'événement est perdu (seulement logué en erreur).
- **Inscription à la liste d'attente sans vérifier que l'avocat est réellement complet** : `POST /api/waitlist/{lawyerId}` accepte l'inscription même si l'avocat a des créneaux `AVAILABLE`. Le seul garde-fou est anti-doublon (contrainte `UNIQUE(lawyer_id, client_id)`). Ajout simple si besoin, via `TimeSlotRepository.findAvailableSlots`.
- **Aucun nom de client sur le tableau de bord avocat** : `GET /api/lawyers/{lawyerId}/bookings` ne retourne que `clientId` (colonne de corrélation), il n'existe pas d'endpoint public dans l'auth-service pour résoudre un id en nom/email. Le frontend affiche `Client #<id>` en attendant.