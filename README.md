# juribook-booking-service

Microservice de gestion des disponibilités, créneaux et réservations pour **JuriBook** : déclaration des disponibilités récurrentes par les avocats, génération automatique des créneaux concrets, gestion ponctuelle (ajout, blocage de période, déblocage), consultation publique des créneaux libres, réservation par les clients avec protection contre la double réservation, confirmation/refus par les avocats, annulation encadrée par une règle des 24h, et publication d'événements Kafka à chaque changement d'état.

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
│   └── BookingController.java            # POST /api/bookings + PATCH confirm/reject/cancel
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
│       └── BookingResponse.java
├── entity/
│   ├── Availability.java                 # Disponibilité récurrente hebdomadaire
│   ├── TimeSlot.java                     # Créneau concret daté, réservable
│   ├── SlotStatus.java                   # AVAILABLE | BOOKED | BLOCKED | CANCELLED | COMPLETED
│   ├── Booking.java                      # Réservation d'un créneau par un client
│   └── BookingStatus.java                # PENDING | CONFIRMED | COMPLETED | CANCELLED
├── event/
│   ├── BookingEventPublisher.java        # Interface — événements booking-events (Sprint 4.6)
│   ├── BookingEvent.java                 # Payload JSON booking.created/confirmed/cancelled
│   ├── KafkaBookingEventPublisher.java   # Impl réelle, active si KafkaTemplate en contexte
│   ├── NoOpBookingEventPublisher.java    # Impl de repli, dev local sans broker
│   ├── SlotEventPublisher.java           # Interface — événements slot-events (Sprint 4.7)
│   ├── SlotReleasedEvent.java            # Payload JSON slot.released (lawyerId + slotId)
│   ├── KafkaSlotEventPublisher.java      # Impl réelle, active si KafkaTemplate en contexte
│   └── NoOpSlotEventPublisher.java       # Impl de repli, dev local sans broker
├── exception/
│   ├── GlobalExceptionHandler.java       # Handlers 400/403/404/409/500
│   ├── InvalidAvailabilityException.java
│   ├── AvailabilityNotFoundException.java
│   ├── InvalidTimeSlotException.java
│   ├── TimeSlotNotFoundException.java
│   ├── BookingConflictException.java     # 409 — créneau déjà réservé (Sprint 4.3)
│   ├── BookingNotFoundException.java     # 404 — réservation introuvable (Sprint 4.4)
│   └── InvalidBookingException.java      # 400 — transition de statut Booking invalide (Sprint 4.4/4.5)
├── filter/
│   └── JwtAuthenticationFilter.java      # Filtre Spring Security (OncePerRequestFilter)
├── repository/
│   ├── AvailabilityRepository.java
│   ├── TimeSlotRepository.java           # Inclut findByIdForUpdate (verrou pessimiste, Sprint 4.3)
│   └── BookingRepository.java
└── service/
    ├── AvailabilityService.java          # Validation, chevauchement, génération des créneaux
    ├── TimeSlotService.java              # Créneaux ponctuels, blocage de période, consultation
    ├── BookingService.java               # Réservation, confirmation/refus, annulation, publication d'événements
    └── JwtService.java                   # Validation des tokens JWT (lecture seule)
src/main/resources/
├── application.yaml
└── db/migration/
    ├── V1__create_availabilities_table.sql
    ├── V2__create_time_slots_table.sql
    ├── V3__add_block_reason_to_time_slots.sql
    ├── V4__create_bookings_table.sql
    └── V5__add_unique_active_booking_per_slot.sql
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
| `GET` | `/api/lawyers/{lawyerId}/slots` | Consultation des créneaux — deux modes, voir ci-dessous |

### Protégés LAWYER (token JWT requis, rôle LAWYER)

| Méthode | URL | Description |
|---|---|---|
| `POST` | `/api/lawyers/{lawyerId}/availabilities` | Déclarer une disponibilité récurrente (génère immédiatement les créneaux) |
| `DELETE` | `/api/lawyers/{lawyerId}/availabilities/{id}` | Désactiver une disponibilité (ne supprime pas les créneaux déjà générés) |
| `POST` | `/api/lawyers/{lawyerId}/slots` | Ajouter un créneau ponctuel, hors récurrence |
| `DELETE` | `/api/lawyers/{lawyerId}/slots/{id}` | Supprimer un créneau (refusé si BOOKED ou COMPLETED) |
| `POST` | `/api/lawyers/{lawyerId}/slots/block` | Bloquer une période (congés, indisponibilité) |
| `POST` | `/api/lawyers/{lawyerId}/slots/{id}/unblock` | Débloquer un créneau |
| `PATCH` | `/api/bookings/{id}/confirm` | Confirmer une demande de réservation PENDING — publie `booking.confirmed` |
| `PATCH` | `/api/bookings/{id}/reject` | Refuser une demande de réservation PENDING — publie `booking.cancelled` + `slot.released` |

### Protégés CLIENT (token JWT requis, rôle CLIENT)

| Méthode | URL | Description |
|---|---|---|
| `POST` | `/api/bookings` | Réserver un créneau — crée une réservation PENDING, marque le créneau BOOKED, protégé contre la double réservation (409), publie `booking.created` |

### Protégés CLIENT ou LAWYER (token JWT requis)

| Méthode | URL | Description |
|---|---|---|
| `PATCH` | `/api/bookings/{id}/cancel` | Annuler une réservation CONFIRMED, refusé si moins de 24h avant le rendez-vous — publie `booking.cancelled` + `slot.released` |

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
Mode `date` : retourne uniquement les créneaux **AVAILABLE** de ce jour précis — usage typique côté client cherchant un rendez-vous.

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
Publie un événement `booking.created` sur `booking-events` (Sprint 4.6) — voir [Kafka](#kafka).

---

### Réserver un créneau déjà pris (Sprint 4.3)
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
Garanti même en cas de requêtes simultanées sur le même créneau, voir [Protection anti-concurrence](#protection-anti-concurrence-sprint-43).

---

### Confirmer une réservation (avocat) — Sprint 4.4
```
PATCH http://localhost:8083/api/bookings/1/confirm
Authorization: Bearer <token_jwt_avocat>
```
Réponse - 200, le `Booking` passe en **CONFIRMED**. Le créneau reste **BOOKED** (aucun changement). Refusé (400) si la réservation n'est pas en statut **PENDING**. Publie `booking.confirmed` sur `booking-events`.

---

### Refuser une réservation (avocat) — Sprint 4.4
```
PATCH http://localhost:8083/api/bookings/2/reject
Authorization: Bearer <token_jwt_avocat>
```
Réponse - 200, le `Booking` passe en **CANCELLED** et le créneau associé est immédiatement libéré (retour à **AVAILABLE**). Refusé (400) si la réservation n'est pas en statut **PENDING**. Pas de règle des 24h : rien n'a encore été confirmé. Publie `booking.cancelled` sur `booking-events` **et** `slot.released` sur `slot-events` (Sprint 4.7).

---

### Annuler une réservation confirmée (client ou avocat) — Sprint 4.5
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

### Lister les réservations d'un client

```bash
docker exec -it juribook-postgres-booking psql -U juribook -d bookingdb -c "SELECT id, lawyer_id, time_slot_id, status, reason, created_at FROM bookings WHERE client_id = 1 ORDER BY created_at DESC;"
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
```

Pas de FK JPA entre ces entités :
- `TimeSlot.availabilityId` est une colonne de corrélation nullable (nullable car un créneau peut être créé ponctuellement, hors récurrence). Cela permet de désactiver ou modifier une `Availability` sans contrainte de suppression en cascade sur des créneaux déjà réservés ou passés.
- `Booking.timeSlotId` et `TimeSlot.bookingId` (référence croisée) sont également sans FK stricte, pour le même principe de découplage et d'évolution indépendante des deux entités.

`lawyerId` (sur les trois entités) et `clientId` (sur `Booking`) référencent respectivement l'entité `Lawyer` du `lawyer-service` et l'entité `User` de l'`auth-service` par leur id technique — pas de jointure inter-services, pas de FK en base, simples colonnes de corrélation (principe microservices : *database per service*).

### Cycle de vie d'un TimeSlot (SlotStatus)

```
AVAILABLE  → BOOKED     (un client réserve)
AVAILABLE  → BLOCKED    (l'avocat bloque le créneau manuellement)
BOOKED     → CANCELLED  (annulation côté client ou avocat)
BOOKED     → COMPLETED  (rendez-vous honoré, passé)
CANCELLED  → AVAILABLE  (le créneau redevient réservable après annulation)
BLOCKED    → AVAILABLE  (l'avocat débloque le créneau)
```

En pratique (Sprint 4.4/4.5), le refus (`/reject`) et l'annulation (`/cancel`) font passer le créneau **directement** de `BOOKED` à `AVAILABLE`, sans matérialiser l'état intermédiaire `CANCELLED` en base — c'est l'événement Kafka `slot.released` (Sprint 4.7) qui porte l'information de la libération, pas une valeur de `SlotStatus` transitoire.

### Cycle de vie d'un Booking (BookingStatus)

```
PENDING    → CONFIRMED  (l'avocat accepte la demande — Sprint 4.4)
PENDING    → CANCELLED  (l'avocat refuse la demande — Sprint 4.4, pas de règle des 24h)
CONFIRMED  → CANCELLED  (annulation par le client ou l'avocat, règle des 24h — Sprint 4.5)
CONFIRMED  → COMPLETED  (rendez-vous honoré — sprint à venir)
```
Contrairement à `SlotStatus`, pas de transition retour : `CANCELLED` et `COMPLETED` sont terminaux. La création d'un `Booking` (Sprint 4.2) le place toujours en `PENDING` et fait passer le `TimeSlot` associé en `BOOKED` dans la même transaction. Chaque transition publie un événement Kafka correspondant — voir [Kafka](#kafka).

### Génération des créneaux

La création d'une `Availability` déclenche **immédiatement** la génération des `TimeSlot` concrets correspondants, sur une fenêtre glissante (4 semaines par défaut, configurable via `generationWeeks`, 1 à 12 semaines). La génération est **idempotente** : un appel répété sur la même fenêtre n'insère jamais de doublon (vérification applicative + filet de sécurité via la contrainte `UNIQUE(lawyer_id, date, start_time)` en base).

### Contrainte de chevauchement

Aucune contrainte d'exclusion PostgreSQL n'est en place pour empêcher le chevauchement de créneaux, la vérification est faite **au niveau service** (`AvailabilityService` et `TimeSlotService`), avant tout insert. Une contrainte d'exclusion serait plus robuste mais plus complexe à mettre en place avec Flyway/Hibernate ; repoussée en V2 si besoin.

### Protection anti-concurrence (Sprint 4.3)

La réservation (`BookingService.createBooking`) lit le `TimeSlot` via `TimeSlotRepository.findByIdForUpdate`, qui pose un verrou pessimiste en écriture (`SELECT ... FOR UPDATE`). Si deux clients réservent le même créneau au même instant :

1. La première transaction obtient le verrou, lit `AVAILABLE`, crée le `Booking`, passe le créneau en `BOOKED`, commit (et libère le verrou).
2. La seconde transaction attendait le verrou, elle l'obtient seulement après le commit de la première, relit le créneau et voit `BOOKED` → rejet immédiat avec `BookingConflictException` → **409 Conflict**.

En filet de sécurité supplémentaire (au cas où le verrou serait contourné, ex. accès direct en base), un index unique partiel (`V5__add_unique_active_booking_per_slot.sql`) empêche plus d'une réservation **active** (statut différent de `CANCELLED`) par créneau au niveau base de données. Une violation de cette contrainte est aussi convertie en 409.

### Règle des 24h (Sprint 4.5)

`BookingService.validateCancellationDeadline` compare `now()` à `(date + startTime du TimeSlot) - 24h`. Si l'instant présent dépasse cette limite, l'annulation est refusée avec un message explicite donnant la date/heure du rendez-vous. Cette règle ne s'applique qu'à `/cancel` (réservations `CONFIRMED`) — `/reject` (réservations encore `PENDING`) n'y est pas soumis, puisque rien n'a été confirmé côté agenda de l'avocat.

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
| `booking-events` | `BookingService` | `booking.created`, `booking.confirmed`, `booking.cancelled` | `POST /bookings`, `PATCH /confirm`, `PATCH /reject`, `PATCH /cancel` |
| `slot-events` | `BookingService` | `slot.released` | `PATCH /reject`, `PATCH /cancel` (le créneau redevient AVAILABLE) |

Le refus (`/reject`) d'une demande **PENDING** publie `booking.cancelled` (pas d'événement `booking.rejected` distinct : `BookingStatus` n'a que `PENDING` / `CONFIRMED` / `COMPLETED` / `CANCELLED`, cf. [Booking.java](#cycle-de-vie-dun-booking-bookingstatus)).

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

`slot-events` (`SlotReleasedEvent`) — volontairement minimal, conforme au cahier des charges (`lawyerId` + `slotId`) :
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

- **Publication synchrone, dans la même transaction `@Transactional`** que l'écriture en base (pas de pattern Outbox). Si Kafka échoue, l'erreur est logguée mais la transaction métier n'est pas annulée — on privilégie la disponibilité du service de réservation à la garantie stricte de livraison de l'événement.
- **Pas de provisioning explicite des topics** (partitions, réplication, rétention) — repoussé au Sprint 5.1, qui couvre la configuration complète de tous les topics Kafka de la plateforme. En dev, `KAFKA_AUTO_CREATE_TOPICS_ENABLE: true` (cf. `docker-compose.yml`) crée les topics à la volée avec les valeurs par défaut au premier message publié.

---

## Sécurité JWT

Le booking-service **ne génère pas** de JWT, il valide uniquement les tokens émis par l'auth-service grâce au secret partagé (`jwt.secret`).

Les claims extraits du token :
- `sub` → email de l'utilisateur
- `id` → authUserId (Long) - stocké dans `SecurityContext.principal`, utilisé directement comme `clientId` pour `POST /api/bookings` et comme `actorId` pour `PATCH /api/bookings/{id}/cancel`
- `role` → LAWYER | CLIENT | ADMIN - utilisé pour les règles d'accès et, pour `/cancel`, pour déterminer si la vérification d'appartenance côté client s'applique

---

## Limites connues

- **`lawyerId` du path non vérifié contre l'utilisateur authentifié** : `AvailabilityController` et `TimeSlotController` vérifient uniquement le rôle `LAWYER` du token, pas que le `lawyerId` de l'URL correspond bien à l'avocat authentifié. Cette correspondance nécessite un appel inter-services vers le `lawyer-service` (résolution `authUserId` → `lawyerId`). À corriger avant la mise en production.
- **Créneaux passés du jour même non filtrés à l'affichage** : `GET /slots?date=...` filtre par jour (`AVAILABLE` uniquement) mais ne tient pas compte de l'heure. Un créneau du jour déjà passé dans la journée peut donc encore apparaître dans la réponse, alors qu'il n'est plus réservable (`TimeSlot.isBookable()` existe mais n'est pas encore branché sur `TimeSlotService.getSlots()`).
- **Aucune vérification d'appartenance côté avocat sur `confirm`/`reject`/`cancel`** : même limitation de fond que ci-dessus (pas de résolution `authUserId → lawyerId` sans appel au `lawyer-service`), mais plus sensible ici — n'importe quel compte `LAWYER` peut confirmer, refuser ou annuler la réservation d'un **autre** avocat. Côté client, la vérification est en revanche active sur `/cancel` (`Booking.clientId` = `authUserId` directement, pas d'appel inter-services nécessaire) : un client ne peut annuler que ses propres réservations (403 sinon). À corriger côté avocat avant la mise en production.
- **Publication d'événements Kafka non transactionnelle avec la base** (pas de pattern Outbox) : cf. [Limites assumées pour ce sprint](#limites-assumées-pour-ce-sprint) dans la section Kafka. Un échec de publication après un commit BDD réussi ne fait pas échouer la requête HTTP, mais l'événement est perdu (seulement logué en erreur).