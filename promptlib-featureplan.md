# PromptLib – Feature- und Umsetzungsplan

Backend-Übungsprojekt zum Auffrischen von Java, Spring Boot und Backend-Grundlagen.
Domäne: Verwaltung, Versionierung und Ausführung von Prompts bzw. generativen Workflows.

---

## 0. Voraussetzungen und Setup

### Werkzeuge, die installiert sein müssen

| Werkzeug | Version | Zweck |
|---|---|---|
| JDK | 21 (LTS) | Records, Pattern Matching, Virtual Threads |
| Maven | 3.9+ | Build (alternativ Gradle) |
| Docker + Compose | aktuell | Postgres, später Testcontainers |
| IntelliJ IDEA | Community reicht | Ultimate ist als Student kostenlos |
| HTTPie oder Bruno | – | API manuell testen, `.http`-Dateien ins Repo |

### Projekt erzeugen

Über den [Spring Initializr](https://start.spring.io) mit folgenden Dependencies:

- Spring Web
- Spring Data JPA
- PostgreSQL Driver
- Flyway Migration
- Validation
- Lombok (optional, aber spart viel Boilerplate)
- Spring Boot Actuator

Später ergänzt: Spring Security, Testcontainers, Resilience4j, springdoc-openapi, MapStruct.

### Paketstruktur

Nach Feature schneiden, nicht nach technischer Schicht. Das skaliert besser und ist genau
die Diskussion, die im Interview gerne aufkommt.

```
de.marinic.promptlib
├── prompt
│   ├── Prompt.java              // Entity
│   ├── PromptVersion.java
│   ├── PromptRepository.java
│   ├── PromptService.java
│   ├── PromptController.java
│   └── dto/                     // Request-/Response-Records
├── tag
├── execution
├── llm                          // Adapter für externes Modell-API
└── common
    ├── error/                   // GlobalExceptionHandler, ProblemDetail
    ├── config/
    └── page/                    // PageResponse-Wrapper
```

### Definition of Done für Stufe 0

- `docker compose up` startet Postgres auf Port 5432
- `mvn spring-boot:run` startet ohne Fehler
- `GET /actuator/health` liefert `{"status":"UP"}`
- Git-Repo initialisiert, `.gitignore` sauber, erster Commit

---

## Stufe 1 – Datenmodell und CRUD

Ziel: eine funktionierende REST-API für Prompts, ohne Versionierung, ohne Auth.

### 1.1 Datenmodell festlegen

**prompt**

| Spalte | Typ | Bemerkung |
|---|---|---|
| id | uuid | PK, `gen_random_uuid()` |
| title | varchar(200) | not null |
| description | text | nullable |
| created_at | timestamptz | not null |
| updated_at | timestamptz | not null |
| current_version_no | int | nullable, zeigt auf aktive Version |

**prompt_version**

| Spalte | Typ | Bemerkung |
|---|---|---|
| id | uuid | PK |
| prompt_id | uuid | FK → prompt, on delete cascade |
| version_no | int | not null, unique zusammen mit prompt_id |
| content | text | not null, der eigentliche Prompt-Text |
| parameters | jsonb | Platzhalter-Definitionen, Defaults |
| notes | text | Änderungsnotiz |
| created_at | timestamptz | not null |

**tag** und **prompt_tag**

| Spalte | Typ |
|---|---|
| tag.id | uuid |
| tag.name | varchar(50), unique, lowercase |
| prompt_tag.prompt_id / tag_id | uuid, zusammengesetzter PK |

### 1.2 Flyway-Migration schreiben

`src/main/resources/db/migration/V1__init.sql` mit den vier Tabellen, Indizes auf
`prompt.title` und `prompt_version.prompt_id`. Wichtig: von Anfang an Flyway benutzen und
`spring.jpa.hibernate.ddl-auto=validate` setzen. Kein `update`, kein `create-drop`.
Das ist der Standard in jedem Team und du gewöhnst dir gleich das Richtige an.

### 1.3 Entities und Repositories

- `@Entity`-Klassen mit `@Id UUID`, Beziehungen konsequent `FetchType.LAZY`
- `@ManyToMany` zwischen Prompt und Tag, oder bewusst als eigene Entity modellieren
- `equals`/`hashCode` nur über die ID, nicht über alle Felder (klassische JPA-Falle)
- `@CreatedDate`/`@LastModifiedDate` über `@EnableJpaAuditing`
- Repositories als `JpaRepository<Prompt, UUID>`

### 1.4 DTOs statt Entities nach außen

Java Records als Request- und Response-Typen:

```java
public record CreatePromptRequest(
    @NotBlank @Size(max = 200) String title,
    String description,
    @NotBlank String content,
    Set<String> tags
) {}

public record PromptResponse(
    UUID id, String title, String description,
    int currentVersionNo, Set<String> tags,
    Instant createdAt, Instant updatedAt
) {}
```

Mapping zunächst per Hand in einem `PromptMapper`, später optional MapStruct.
Entities niemals direkt als Response zurückgeben – sonst Lazy-Loading-Exceptions,
ungewollte Feldpreisgabe und eine API, die sich nicht unabhängig weiterentwickeln lässt.

### 1.5 Service-Schicht

`PromptService` mit `@Transactional`. Regel: der Controller kennt HTTP, der Service kennt
die Fachlogik, das Repository kennt die Datenbank. Keine Repository-Aufrufe im Controller.

### 1.6 Controller

```
POST   /api/v1/prompts              → 201 + Location-Header
GET    /api/v1/prompts/{id}         → 200 / 404
PATCH  /api/v1/prompts/{id}         → Metadaten ändern
DELETE /api/v1/prompts/{id}         → 204
GET    /api/v1/tags                 → alle Tags mit Anzahl
```

### 1.7 Fehlerbehandlung

Ein `@RestControllerAdvice`, das `ProblemDetail` (RFC 9457, in Spring 6 eingebaut) zurückgibt:

- `NotFoundException` → 404
- `MethodArgumentNotValidException` → 400 mit Feldfehlerliste
- alles andere → 500, ohne Stacktrace nach außen

### Definition of Done für Stufe 1

- Alle fünf Endpoints per `.http`-Datei durchgetestet
- Validierungsfehler liefern eine strukturierte 400-Antwort
- Mindestens ein `@DataJpaTest` und ein `@WebMvcTest` mit gemocktem Service
- Commit-Historie in nachvollziehbaren Schritten

---

## Stufe 2 – Versionierung, Suche, Pagination

Hier wird es fachlich interessant. Prompts werden nicht überschrieben, sondern versioniert.

### 2.1 Versionierung

```
POST /api/v1/prompts/{id}/versions              → neue Version anlegen
GET  /api/v1/prompts/{id}/versions              → Liste, absteigend
GET  /api/v1/prompts/{id}/versions/{no}         → einzelne Version
POST /api/v1/prompts/{id}/versions/{no}/activate → als aktuelle Version setzen
GET  /api/v1/prompts/{id}/diff?from=1&to=3      → optional, Textdiff
```

Fachliche Punkte, die du bewusst lösen solltest:

- Die nächste `version_no` ermitteln, ohne Race Condition. Naiv wäre
  `max(version_no) + 1` im Service – das bricht bei parallelen Requests.
  Lösungen: Unique-Constraint plus Retry, `SELECT ... FOR UPDATE` auf dem Prompt,
  oder optimistisches Locking über `@Version` auf der Prompt-Entity.
- Versionen sind unveränderlich. Kein `PUT` auf eine bestehende Version.

### 2.2 Suche und Filter

```
GET /api/v1/prompts?query=video&tags=comfyui,cv&page=0&size=20&sort=updatedAt,desc
```

Umsetzung wahlweise über JPA Specifications oder eine `@Query` mit dynamischem Filter.
Postgres Volltextsuche (`tsvector`, GIN-Index) ist der interessantere Weg und ein gutes
Gesprächsthema.

Eigenen `PageResponse<T>`-Record bauen statt Springs `Page` zu serialisieren – dessen
JSON-Struktur ist instabil und nicht als API-Contract gedacht.

### 2.3 N+1 gezielt provozieren und lösen

1. `spring.jpa.show-sql=true` plus Hibernate-Statistiken aktivieren
2. Prompt-Liste mit Tags laden, Queries zählen → du siehst 1 + n
3. Mit `@EntityGraph` oder `join fetch` auf eine Query reduzieren
4. Bei Pagination plus Fetch-Join die Warnung `HHH90003004` beachten und verstehen,
   warum Hibernate dann im Speicher paginiert

Das ist die Übung mit dem größten Lerneffekt im ganzen Projekt.

### Definition of Done für Stufe 2

- Versionierung funktioniert, auch bei zwei parallelen Requests
- Suche mit Pagination und Sortierung
- Nachweis im README, wie die Query-Anzahl von n+1 auf 1 gesenkt wurde

---

## Stufe 3 – Ausführung gegen ein LLM-API

### 3.1 Adapter-Schicht

Interface `LlmClient` mit einer Implementierung für ein echtes API und einer
`MockLlmClient`-Variante über Spring-Profile. So laufen Tests ohne Netz und ohne Kosten.

```java
public interface LlmClient {
    LlmResult complete(LlmRequest request);
}
```

Konfiguration über `@ConfigurationProperties`, API-Key aus Umgebungsvariablen.
Niemals ins Repo.

### 3.2 Execution-Entity

| Spalte | Typ |
|---|---|
| id | uuid |
| prompt_version_id | uuid FK |
| status | varchar – PENDING, RUNNING, SUCCEEDED, FAILED |
| model | varchar |
| input_params | jsonb |
| output | text |
| latency_ms | int |
| tokens_in / tokens_out | int |
| error_message | text |
| created_at / finished_at | timestamptz |

### 3.3 Endpoints

```
POST /api/v1/executions            → 202 Accepted, gibt Execution-ID zurück
GET  /api/v1/executions/{id}       → Status abfragen
GET  /api/v1/executions?promptId=  → Historie
```

### 3.4 Asynchronität

Erste Ausbaustufe: `@Async` mit eigenem `TaskExecutor`, Status wird in der DB
fortgeschrieben. Alternativ mit Java 21 Virtual Threads
(`spring.threads.virtual.enabled=true`).

Wichtig: die Transaktion des Requests darf nicht bis zum Ende des LLM-Calls offen bleiben.
Kurze Transaktion zum Anlegen, dann außerhalb ausführen, dann neue Transaktion zum
Schreiben des Ergebnisses. Genau hier verstehst du, warum `@Transactional` und externe
Calls sich nicht vertragen.

### 3.5 Resilienz

- `RestClient` mit Connect- und Read-Timeout
- Resilience4j: Retry mit Backoff, Circuit Breaker, Fallback auf FAILED-Status
- Rate Limiting pro Nutzer

### Definition of Done für Stufe 3

- Ausführung läuft asynchron, Status ist per Polling abfragbar
- Netzwerkfehler führen zu einer sauberen FAILED-Execution, nicht zu einer 500
- Circuit Breaker im Test nachweisbar

---

## Stufe 4 – Sicherheit, Tests, Betrieb

### 4.1 Spring Security

- Registrierung und Login, Passwort mit BCrypt
- JWT als Access Token, Filter in der Security-Chain
- Prompts gehören einem Nutzer, `owner_id`-Spalte plus Autorisierungsprüfung im Service
- Öffentliche versus private Prompts als Sichtbarkeitsflag

### 4.2 Tests

| Ebene | Werkzeug | Umfang |
|---|---|---|
| Unit | JUnit 5, AssertJ, Mockito | Servicelogik, Versionsnummern, Mapping |
| Web | `@WebMvcTest` | Statuscodes, Validierung, Serialisierung |
| Persistenz | `@DataJpaTest` + Testcontainers | echte Postgres, keine H2 |
| Integration | `@SpringBootTest` + Testcontainers | zwei bis drei Happy Paths |

H2 bewusst vermeiden: andere SQL-Dialekte, jsonb funktioniert nicht, und du testest
am Ende etwas anderes als du betreibst.

### 4.3 Observability und Betrieb

- Actuator: health, info, metrics, prometheus
- Eigene Metrik: Anzahl und Dauer der Executions über `MeterRegistry`
- Strukturiertes Logging als JSON, Correlation-ID pro Request via `MDC`
- Mehrstufiges Dockerfile mit Layered Jar
- GitHub Actions: Build, Tests, optional Spotless und ein Container-Build

### 4.4 Dokumentation

- springdoc-openapi, Swagger UI unter `/swagger-ui.html`
- README mit Architekturüberblick, Setup in drei Befehlen, und einem Abschnitt
  „Entscheidungen und Trade-offs“

Dieser README-Abschnitt ist im Bewerbungsgespräch oft wertvoller als der Code selbst,
weil er zeigt, dass du Alternativen abgewogen hast.

---

## Stufe 5 – Optionales Frontend

Schlankes React- plus Vite-Frontend, damit das Projekt vorzeigbar ist:

- Liste mit Suche und Tag-Filter
- Detailansicht mit Versionsverlauf und Diff
- Editor für eine neue Version
- Ausführung starten, Status pollen, Ergebnis anzeigen

Hier bist du zu Hause – bewusst klein halten, damit der Backend-Fokus erkennbar bleibt.

---

## Zeitliche Einordnung

| Stufe | Aufwand neben Vollzeit | Ergebnis |
|---|---|---|
| 0 + 1 | 1 Wochenende | lauffähige CRUD-API |
| 2 | 1–2 Wochenenden | das fachliche Herzstück |
| 3 | 2 Wochenenden | die eigentliche Lernstufe |
| 4 | 2 Wochenenden | produktionsnah, vorzeigbar |
| 5 | 1 Wochenende | Fullstack-Abrundung |

Ein Abbruch nach Stufe 2 ergibt bereits ein sauberes, zeigbares Projekt. Wichtiger als
Vollständigkeit ist, dass jede Stufe abgeschlossen und committet ist.

---

## Häufige Interviewfragen, die dieses Projekt abdeckt

- Wie verhindert man N+1 und wann fällt es auf?
- Was macht `@Transactional` genau, und warum ist ein Selbstaufruf wirkungslos?
- Warum DTOs statt Entities?
- Optimistisches versus pessimistisches Locking – wann was?
- Wie testet man gegen externe Systeme?
- Wie geht man mit Timeouts und teilweisen Ausfällen um?
- Warum Flyway statt `ddl-auto=update`?

Bei jedem dieser Punkte solltest du am Ende auf eine konkrete Codestelle zeigen können.
