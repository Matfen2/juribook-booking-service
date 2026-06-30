# juribook-booking-service

Microservice de gestion des disponibilités et créneaux pour **JuriBook** : déclaration des disponibilités récurrentes par les avocats, génération automatique des créneaux concrets, gestion ponctuelle (ajout, blocage de période, déblocage), consultation publique des créneaux libres.

## Stack

- Java 21 · Spring Boot 4.1.0 · Maven
- Spring Security · JWT (validation des tokens émis par l'auth-service)
- PostgreSQL 16 · Flyway (migrations)
- Apache Kafka (désactivé en dev local, voir [Kafka](#kafka))
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
│   └── TimeSlotController.java           # POST/GET/DELETE /api/lawyers/{id}/slots + block/unblock
├── dto/
│   ├── request/
│   │   ├── CreateAvailabilityRequest.java
│   │   ├── CreateTimeSlotRequest.java
│   │   └── BlockPeriodRequest.java
│   └── response/
│       ├── AvailabilityResponse.java
│       ├── TimeSlotResponse.java
│       └── BlockPeriodResponse.java
├── entity/
│   ├── Availability.java                 # Disponibilité récurrente hebdomadaire
│   ├── TimeSlot.java                     # Créneau concret daté, réservable
│   └── SlotStatus.java                   # AVAILABLE | BOOKED | BLOCKED | CANCELLED | COMPLETED
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── InvalidAvailabilityException.java
│   ├── AvailabilityNotFoundException.java
│   ├── InvalidTimeSlotException.java
│   └── TimeSlotNotFoundException.java
├── filter/
│   └── JwtAuthenticationFilter.java      # Filtre Spring Security (OncePerRequestFilter)
├── repository/
│   ├── AvailabilityRepository.java
│   └── TimeSlotRepository.java
└── service/
    ├── AvailabilityService.java          # Validation, chevauchement, génération des créneaux
    ├── TimeSlotService.java              # Créneaux ponctuels, blocage de période, consultation
    └── JwtService.java                   # Validation des tokens JWT (lecture seule)
src/main/resources/
├── application.yaml
└── db/migration/
    ├── V1__create_availabilities_table.sql
    ├── V2__create_time_slots_table.sql
    └── V3__add_block_reason_to_time_slots.sql
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

### Protégés CLIENT (à venir — Sprint 3.x)

| Méthode | URL | Description |
|---|---|---|
| `POST` | `/api/bookings` | Réserver un créneau (route déclarée dans SecurityConfig, pas encore implémentée) |

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

### Cas d'erreur

```
POST /availabilities avec endTime <= startTime          → 400 "L'heure de fin doit être après l'heure de début"
POST /availabilities chevauchant une dispo existante     → 400 "Cette plage horaire chevauche une disponibilité existante pour ce jour"
POST /slots dans le passé                                 → 400 "Impossible de créer un créneau dans le passé"
POST /slots chevauchant un créneau existant                → 400 "Ce créneau chevauche un créneau existant à cette date"
DELETE /slots/{id} sur un créneau BOOKED                  → 400 "Impossible de supprimer un créneau réservé"
DELETE /slots/{id} sur un créneau COMPLETED                → 400 "Impossible de supprimer un créneau déjà honoré"
POST /slots/{id}/unblock sur un créneau non bloqué         → 400 "Ce créneau n'est pas bloqué"
Toute route LAWYER sans token                              → 401 Unauthorized
Toute route LAWYER avec token CLIENT                        → 403 Forbidden
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
```

Pas de FK JPA entre les deux entités : `TimeSlot.availabilityId` est une colonne de corrélation nullable (nullable car un créneau peut être créé ponctuellement, hors récurrence). Cela permet de désactiver ou modifier une `Availability` sans contrainte de suppression en cascade sur des créneaux déjà réservés ou passés.

`lawyerId` (sur les deux entités) référence l'entité `Lawyer` du `lawyer-service` par son id technique, pas de jointure inter-services, pas de FK en base, simple colonne de corrélation (principe microservices : *database per service*).

### Cycle de vie d'un TimeSlot (SlotStatus)

```
AVAILABLE  → BOOKED     (un client réserve)
AVAILABLE  → BLOCKED    (l'avocat bloque le créneau manuellement)
BOOKED     → CANCELLED  (annulation côté client ou avocat)
BOOKED     → COMPLETED  (rendez-vous honoré, passé)
CANCELLED  → AVAILABLE  (le créneau redevient réservable après annulation)
BLOCKED    → AVAILABLE  (l'avocat débloque le créneau)
```

### Génération des créneaux

La création d'une `Availability` déclenche **immédiatement** la génération des `TimeSlot` concrets correspondants, sur une fenêtre glissante (4 semaines par défaut, configurable via `generationWeeks`, 1 à 12 semaines). La génération est **idempotente** : un appel répété sur la même fenêtre n'insère jamais de doublon (vérification applicative + filet de sécurité via la contrainte `UNIQUE(lawyer_id, date, start_time)` en base).

### Contrainte de chevauchement

Aucune contrainte d'exclusion PostgreSQL n'est en place pour empêcher le chevauchement de créneaux — la vérification est faite **au niveau service** (`AvailabilityService` et `TimeSlotService`), avant tout insert. Une contrainte d'exclusion serait plus robuste mais plus complexe à mettre en place avec Flyway/Hibernate ; repoussée en V2 si besoin.

---

## Kafka

Désactivé en développement local (pas de broker disponible) via `spring.autoconfigure.exclude` dans `application.yaml`. À réactiver en production en supprimant l'exclusion et en passant `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092` (cf. Docker Compose). Topics prévus côté booking-service (Sprint 3.x suivant) : `booking-events`, `slot-events`.

---

## Sécurité JWT

Le booking-service **ne génère pas** de JWT, il valide uniquement les tokens émis par l'auth-service grâce au secret partagé (`jwt.secret`).

Les claims extraits du token :
- `sub` → email de l'utilisateur
- `id` → authUserId (Long) - stocké dans `SecurityContext.principal`
- `role` → LAWYER | CLIENT | ADMIN - utilisé pour les règles d'accès

---

## Limites connues

- **`lawyerId` du path non vérifié contre l'utilisateur authentifié** : `AvailabilityController` et `TimeSlotController` vérifient uniquement le rôle `LAWYER` du token, pas que le `lawyerId` de l'URL correspond bien à l'avocat authentifié. Cette correspondance nécessite un appel inter-services vers le `lawyer-service` (résolution `authUserId` → `lawyerId`). À corriger avant la mise en production.
- **Créneaux passés du jour même non filtrés à l'affichage** : `GET /slots?date=...` filtre par jour (`AVAILABLE` uniquement) mais ne tient pas compte de l'heure. Un créneau du jour déjà passé dans la journée peut donc encore apparaître dans la réponse, alors qu'il n'est plus réservable (`TimeSlot.isBookable()` existe mais n'est pas encore branché sur `TimeSlotService.getSlots()`).