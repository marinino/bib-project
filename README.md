# PromptLib

Backend-Übungsprojekt zum Auffrischen von Java, Spring Boot und Backend-Grundlagen.
Verwaltung, Versionierung und Suche von Prompts über eine REST-API.

Der vollständige Feature- und Umsetzungsplan steht in [promptlib-featureplan.md](promptlib-featureplan.md).

## Setup

```bash
docker compose up -d
./mvnw spring-boot:run
```

`GET /actuator/health` sollte danach `{"status":"UP"}` liefern.

Manuelle Requests: [requests/prompts.http](requests/prompts.http).

## Architektur

```
Controller → Service → Repository (+ Specification) → Entity → PostgreSQL
```

- **Controller** kennt nur HTTP (Status-Codes, Query-Parameter), arbeitet ausschließlich mit DTOs
- **Service** kapselt Fachlogik und Transaktionsgrenzen, mappt Entities → DTOs *innerhalb* der Transaktion
  (wegen `spring.jpa.open-in-view=false` — sonst `LazyInitializationException`)
- **Repository** kennt nur Datenzugriff (`JpaRepository`, `JpaSpecificationExecutor`)

## Entscheidungen und Trade-offs

### Race Condition bei der Versionsnummer-Vergabe

Beim Anlegen einer neuen `PromptVersion` muss die nächste freie `version_no` ermittelt werden.
Naiv (`max(version_no) + 1` im Service) ist das nicht race-safe: zwei parallele Requests können
denselben, veralteten Höchstwert lesen und beide dieselbe Nummer vergeben wollen.

Gelöst über **Pessimistic Locking** (`SELECT ... FOR UPDATE` auf der `Prompt`-Zeile, siehe
`PromptRepository.findByIdForUpdate`): der zweite Request wartet, bis der erste committed hat, und
berechnet danach auf Basis des aktuellen Stands. Ein `UNIQUE`-Constraint
(`uq_prompt_version_no` auf `(prompt_id, version_no)`) dient zusätzlich als Sicherheitsnetz.

Bewiesen durch `PromptVersionConcurrencyTest`: 10 echte parallele Requests gegen denselben Prompt
liefern lückenlose, eindeutige Versionsnummern.

### Suche: JPA Specifications statt Volltextsuche

Für `GET /api/v1/prompts?query=...&tags=...` wurden **Specifications** (statt Postgres-Volltextsuche
mit `tsvector`) gewählt, weil Specifications direkt auf Entity-Feldnamen arbeiten und sich dadurch
sauber mit Spring Data's `Pageable`/`Sort` kombinieren lassen. Eine native Volltextsuche-Query hätte
für die COUNT-Query und Sortierung zusätzliche manuelle Arbeit bedeutet.

### N+1 bei der Prompt-Suche

**Problem:** `GET /api/v1/prompts` lädt eine Seite von Prompts; jeder Prompt hat eine `@ManyToMany`-Beziehung
zu `Tag` (`FetchType.LAZY`). Beim Mapping zu `PromptResponse` (`prompt.getTags()`) löst das für **jeden**
Prompt auf der Seite eine eigene Lazy-Load-Query aus.

**Nachweis (Hibernate-Statistiken, 5 Prompts mit je 2 Tags, Persistence Context zwischen Anlegen und
Suche geleert, damit der First-Level-Cache das Problem nicht verdeckt):**

```
1x SELECT prompt        (Content-Query mit LIMIT/OFFSET)
1x SELECT count(id)     (Count-Query für Pagination)
5x SELECT ... prompt_tag JOIN tag WHERE prompt_id = ?   (eine pro Prompt!)
= 7 Statements für 5 Prompts  →  2 + n
```

**Erster Lösungsversuch (naiv):** ein `fetch("tags", LEFT)` direkt in der Specification, kombiniert mit
Pagination. Klassisch bekannt als Auslöser der Hibernate-Warnung `HHH90003004`
("firstResult/maxResults specified with collection fetch; applying in memory") — Hibernate kann bei
einem Collection-Fetch-Join kein SQL-`LIMIT`/`OFFSET` mehr anwenden (der Join vervielfacht Zeilen pro
Tag) und paginiert deshalb im Speicher, was bei größeren Datenmengen die gesamte Tabelle lädt.

**Tatsächliches Ergebnis mit Hibernate 6 (Spring Boot 4.1.1):** Die Warnung trat **nicht** auf.
Hibernate generiert stattdessen eine Subquery, die zuerst paginiert (`LIMIT`/`OFFSET` auf den reinen
Prompt-IDs) und erst danach die Tags an die bereits eingeschränkte Ergebnismenge joint:

```sql
select p1_0.id, ..., t1_1.name, ...
from (
    select distinct p1_0.id, ... from prompt p1_0
    order by p1_0.updated_at desc
    offset ? rows fetch first ? rows only
) p1_0
left join prompt_tag t1_0 on p1_0.id = t1_0.prompt_id
left join tag t1_1 on t1_1.id = t1_0.tag_id
```

`HHH90003004` ist demnach ein **historisches** Problem älterer Hibernate-Versionen (5.x), das in
Hibernate 6 durch diese Subquery-Strategie gelöst wurde — ein gutes Beispiel dafür, dass
Framework-Fallstricke aus Tutorials/Blogposts nicht automatisch für die aktuell verwendete Version
gelten.

**Finale Lösung:** `PromptSpecifications.fetchTags()` — ein `LEFT JOIN FETCH` auf `tags`, das nur bei der
Content-Query greift (nicht bei der COUNT-Query, wo ein Fetch semantisch ungültig wäre; das wird über
`cq.getResultType() != Long.class` geprüft). Immer als Teil der Suche aktiv.

**Ergebnis:** konstant **2 Statements**, unabhängig von der Anzahl der Prompts auf der Seite
(bewiesen in `PromptSearchQueryCountTest`, Hibernate-Statistiken via `SessionFactory.getStatistics()`).

### Race Condition beim Tag-Anlegen

`resolveTags()` (Prompt anlegen/ändern) legt neue Tags per Find-or-Create an. Der ursprüngliche
Ansatz (`findByName().orElseGet(() -> save(...))`) hatte dieselbe Art von Lücke wie oben: zwei
parallele Requests, die dasselbe neue Tag zum ersten Mal anlegen, konnten beide `save()` versuchen —
einer scheiterte mit `DataIntegrityViolationException` auf dem `UNIQUE`-Constraint.

Ein einfaches `try/catch` um den `save()` reicht hier nicht zuverlässig: schlägt ein `flush()` mit
einer Constraint-Verletzung fehl, markiert Hibernate die aktuelle Transaktion oft als beschädigt —
weitere Arbeit in *derselben* Transaktion kann danach unvorhersehbar scheitern, selbst wenn die
Exception im Code gefangen wird. Der "sichere" Weg wäre eine separate Transaktion
(`@Transactional(REQUIRES_NEW)`) nur fürs Tag-Anlegen gewesen — deutlich mehr Komplexität.

**Lösung:** `TagRepository.upsertByName()` — `INSERT ... ON CONFLICT (name) DO NOTHING` (natives SQL).
Wirft nie, egal wie viele Requests gleichzeitig dasselbe neue Tag anlegen wollen; Postgres serialisiert
das intern über eine Sperre auf dem Unique-Index. Danach garantiert ein `findByName()`, dass die Zeile
existiert — unabhängig davon, wer sie tatsächlich eingefügt hat.

Verifiziert in beide Richtungen in `PromptTagConcurrencyTest`: 10 parallele Prompt-Erstellungen mit
demselben neuen Tag laufen mit dem Fix durch; gegen den alten Code (kurz zurückgeschaltet, um zu
prüfen, dass der Test wirklich etwas beweist) schlägt derselbe Test zuverlässig fehl.

### Stufe 3: Async-Ausführung ohne offene Transaktion während des LLM-Calls

`POST /api/v1/executions` legt eine `Execution`-Zeile mit Status `PENDING` an und gibt sofort `202
Accepted` zurück; der eigentliche LLM-Call läuft danach asynchron. Zwei Fallstricke dabei, die der
naive Ansatz ("Async-Methode direkt aus der Transaktion heraus aufrufen") beide hätte:

1. **Die Transaktion darf nicht offen bleiben, während der LLM-Call läuft** — ein Request an eine
   externe API kann Sekunden dauern; eine offene DB-Transaktion so lange zu halten, blockiert
   Connections aus dem Pool unnötig lange.
2. **Der Async-Thread darf nicht starten, bevor die erzeugende Transaktion committed hat** — würde
   `ExecutionService.create()` den Async-Aufruf direkt (noch innerhalb der eigenen `@Transactional`-
   Methode) auslösen, könnte der neue Thread versuchen, die gerade erst eingefügte `Execution`-Zeile
   zu lesen, *bevor* sie überhaupt committed und damit für andere Transaktionen sichtbar ist.

**Lösung:**
- `ExecutionService.create()` speichert die `Execution`-Zeile in einer kurzen Transaktion und
  publiziert ein `ExecutionCreatedEvent` — Events werden aber erst zugestellt, wenn `create()`
  zurückkehrt.
- `ExecutionCreatedListener` reagiert per `@TransactionalEventListener(phase = AFTER_COMMIT)` — feuert
  garantiert erst, *nachdem* die erzeugende Transaktion erfolgreich committed hat.
- Erst dann startet `ExecutionRunner.run()` auf einem eigenen `TaskExecutor`. Jeder Repository-Aufruf
  darin (`findById`/`save`) ist seine eigene kurze Transaktion; der LLM-Call selbst läuft dazwischen
  komplett ohne offene Transaktion.

`ExecutionRunner` ist eine eigene Klasse (statt einer Methode auf `ExecutionService`) — aber *nicht*
wegen der `@Async`-Selbstaufruf-Falle: `ExecutionCreatedListener` ruft `run()` ohnehin über eine
injizierte Fremd-Bean-Referenz auf, kein `this.`-Aufruf, also hätte `@Async` so oder so funktioniert
(live mit Thread-Namen-Logging nachgewiesen: `create()`/`onExecutionCreated()` laufen synchron auf
`main`, erst `run()` wechselt auf `execution-N` — einzig wegen `@Async` auf dieser Methode, unabhängig
von der Klassenzugehörigkeit). Der eigentliche Grund für die Trennung ist reine
Verantwortungstrennung: `ExecutionService` verwaltet Transaktionsgrenzen, `ExecutionRunner` die
Async-Arbeit ohne Transaktionsgrenze.

Bewiesen in `ExecutionIntegrationTest`: Erfolgs- und Fehlerfall laufen gegen die echte (Mock-)LLM-
Anbindung durch, mit `Awaitility` auf den finalen Status gepollt — kein Mocking der eigenen Klassen,
weil genau das Zusammenspiel zwischen ihnen geprüft werden soll.

### Verschluckte Exceptions bei `@Async void`-Methoden

`ExecutionRunner.run()` fing ursprünglich nur `LlmException` ab (den kontrollierten Fehlerfall). Ein
echter, unerwarteter Bug im Code (nicht die LLM-API selbst) hätte diesen `catch`-Block übersprungen —
mit einem gravierenden Effekt: Bei einer `@Async void`-Methode gibt es **keinen Aufrufer, der noch auf
das Ergebnis wartet** (der Request ist ja längst mit `202 Accepted` beantwortet). Eine Exception, die
aus so einer Methode entkommt, landet nur bei Spring's `SimpleAsyncUncaughtExceptionHandler`, der sie
loggt und dann verwirft — die `Execution`-Zeile bleibt für immer bei `RUNNING` stehen, ohne
`finishedAt`, ohne `errorMessage`, unsichtbar für jeden API-Konsumenten.

Live nachgestellt: ein `IllegalStateException` (bewusst *keine* `LlmException`) im Mock-Client
provoziert, Status blieb nach mehreren Sekunden bei `RUNNING`, `finishedAt: null`, einzige Spur ein
Log-Eintrag von Springs generischem Handler.

**Fix:** `run()` fängt jetzt jede `Exception` (nicht nur `LlmException`) und ruft in jedem Fall
`recordFailure(...)` auf — mit unterschiedlichem Log-Level: `WARN` ohne Stacktrace für erwartete
`LlmException`, `ERROR` mit vollem Stacktrace für alles andere (ein echter Bug verdient Aufmerksamkeit,
eine erwartete LLM-Fehlermeldung nicht). Regressionstest: `unexpectedBugIsStillRecordedAsFailed`.

### Stufe 3.5: Retry, Circuit Breaker, Timeouts (Resilience4j)

`LlmClient.complete()` (beide Implementierungen) trägt `@Retry(name = "llm")` +
`@CircuitBreaker(name = "llm")`. Für den "Fallback auf FAILED-Status" aus dem Plan brauchte es
keinen extra Resilience4j-`fallbackMethod` — der `ExecutionRunner`-Catch-all von oben übernimmt das
bereits automatisch, egal welche Exception am Ende durchkommt (`LlmException`,
`CallNotPermittedException` vom offenen Breaker, oder ein echter Bug).

**`spring-boot-starter-aop` heißt in Boot 4 `spring-boot-starter-aspectj`** — ohne dieses Umbenennen
zu kennen, schlägt die Dependency-Auflösung fehl (`resilience4j-spring-boot3` selbst ist offiziell
für Boot 3 gebaut, funktioniert aber unverändert auf Boot 4.1.1 — Kontext lädt sauber).

**Zusammenspiel Retry + Circuit Breaker live gemessen** (nicht angenommen), über einen Aufrufzähler
in `MockLlmClient` (`resilience4j.retry.instances.llm.max-attempts=3`,
`sliding-window-size=4`, `minimum-number-of-calls=4`, `failure-rate-threshold=50%`):

| Aufruf | Ergebnis | Tatsächliche `complete()`-Aufrufe |
|---|---|---|
| 1 | `LlmException` (Retry erschöpft) | 3 |
| 2 | `CallNotPermittedException` (Breaker klappt mitten im 1. Retry-Versuch auf) | 1 |
| 3+ | `CallNotPermittedException`, sofort | 0 |

Spring Boot's Default-Reihenfolge legt `@Retry` **außen**, `@CircuitBreaker` **innen** — jeder
Retry-Versuch ist selbst ein Circuit-Breaker-Aufruf. `CallNotPermittedException` steht nicht in
`retry-exceptions`, wird also nicht selbst nochmal retried, sondern fliegt direkt durch. Regressionstest:
`ResilienceTest.repeatedFailuresRetryThenTripTheCircuitBreaker`.

**Falle dabei:** Der Circuit Breaker ist Singleton-Zustand (`CLOSED`/`OPEN`/...), geteilt über den
kompletten Testlauf — genau wie die Datenbank bei den `@SpringBootTest`-Klassen weiter oben, nur
diesmal im Speicher statt in Postgres. Ohne expliziten Reset ließ ein zuerst laufender Test (Reihenfolge
nicht garantiert) den Breaker für alle danach `OPEN` zurück. Fix: `CircuitBreakerRegistry.circuitBreaker(
"llm").reset()` in `@BeforeEach` von `ResilienceTest` und `ExecutionIntegrationTest`.

**Echter Bug, gefunden durch tatsächliches Warten (nicht nur Doku gelesen):** `OPEN → HALF_OPEN` passiert
**nicht** automatisch, nur weil `wait-duration-in-open-state` verstrichen ist — `resilience4j`'s eigener
Default für `automatic-transition-from-open-to-half-open-enabled` ist `false`. Ohne diese Property
explizit auf `true` zu setzen, wäre unser Breaker nach dem ersten Auslösen für immer `OPEN` geblieben
(bis zum App-Neustart) — hätte jede weitere Execution permanent abgelehnt, selbst Stunden nachdem sich
die LLM-API längst wieder erholt hätte. Mit Zeitstempel-Logging live nachgestellt: nach 3s Warten bei
`false` immer noch `OPEN`; mit `automatic-transition-from-open-to-half-open-enabled=true` schaltet ein
Hintergrund-Task von sich aus auf `HALF_OPEN` um — sogar **während** man wartet, nicht erst beim
nächsten Aufruf. Regressionstest: `ResilienceTest.circuitBreakerSelfHealsAfterWaitDuration`.

**Timeouts:** `RealLlmClient` bekommt Connect-/Read-Timeout aus `LlmProperties` über einen
`SimpleClientHttpRequestFactory` auf dem `RestClient.Builder` — ohne das würde ein hängender Request
nie enden.

**Nicht umgesetzt:** Rate Limiting "pro Nutzer" braucht einen Nutzer-Begriff, den es erst mit Auth in
Stufe 4 gibt. Ein globaler Rate Limiter hätte die eigentliche Anforderung nicht erfüllt, deshalb bewusst
verschoben statt vorgetäuscht.

### Stufe 4.1 — Spring Security: Registrierung, Login, Ownership

**JWT statt Session:** Login/Registrierung geben ein signiertes JWT zurück (`AuthService`,
`JwtService`, HS256 über `io.jsonwebtoken:jjwt`), jeder weitere Request trägt es im
`Authorization: Bearer <token>`-Header. `SecurityConfig` schaltet Sessions komplett ab
(`SessionCreationPolicy.STATELESS`) und deaktiviert CSRF — CSRF schützt vor **ungewollten**
Requests, die der Browser bei Cookie-basierter Session-Auth automatisch mitschickt; das gibt es
hier nicht, der Client muss den Header selbst aktiv setzen.

**Zwei getrennte Prinzipal-Wege, bewusst:** `UserDetailsServiceImpl.loadUserByUsername(email)`
wird nur einmal gebraucht — beim Login, wenn `AuthenticationManager`/`DaoAuthenticationProvider`
Email+Passwort (BCrypt) prüfen. `JwtAuthenticationFilter` läuft dagegen auf **jedem** Request und
hat die User-ID schon direkt im Token (`sub`-Claim) — er lädt den `User` daher per ID über
`UserRepository`, nicht nochmal über `UserDetailsService`. Beide Wege landen im selben
`AppUserPrincipal` (implementiert `UserDetails`), aber "username" bedeutet in den beiden Fällen
etwas anderes (Email vs. ID) — deshalb zwei Pfade statt einem überladenen.

**Owner + Sichtbarkeit statt reiner Auth:** `prompt.owner_id` (FK auf `app_user`, nullable —
Prompts aus Stufe 1–3 haben keinen Owner) plus `visibility` (`PRIVATE`/`PUBLIC`,
`PromptAccess.requireReadable`/`requireOwner`, wiederverwendet von `PromptService`,
`PromptVersionService` **und** `ExecutionService`, obwohl die beiden letzteren nie
`PromptService` selbst aufrufen). Eine fehlende und eine fremde private Prompt-ID geben absichtlich
denselben 404 zurück — ein 403 würde verraten, dass unter dieser ID überhaupt etwas Privates
existiert. Nachweis (echter HTTP-Layer, zwei echte registrierte Nutzer, echtes Postgres):
`PromptOwnershipIntegrationTest.privatePromptIsHiddenFromOthersAndVisibleOnceMadePublic`.

**Stolperfalle beim `@WebMvcTest` der Controller:** Mit Spring Security auf dem Classpath wird
`JwtAuthenticationFilter` als `Filter`-Bean automatisch Teil jedes `@WebMvcTest`-Slices (Boot zählt
`Filter`-Implementierungen zu den slice-relevanten Typen) — sein Konstruktor braucht dann aber
`JwtService`/`UserRepository`, die dort sonst nicht existieren. Erster Versuch,
`addFilters = false` zu setzen, hat das zwar behoben, aber gleichzeitig
`@AuthenticationPrincipal` kaputt gemacht: der über `SecurityMockMvcRequestPostProcessors.user(...)`
gesetzte Principal erreicht `SecurityContextHolder` erst über `SecurityContextHolderFilter` — läuft
die echte Filterkette gar nicht erst, bleibt der Controller-Parameter `null`, und zwei Tests fielen
mit stillen Fehlbindungen statt sauberen Fehlern auf (einmal `NullPointerException`, einmal ein
Mockito-Aufruf mit `null` statt der erwarteten User-ID — je nachdem, ob der Controller-Code den
Principal direkt dereferenziert oder nur weiterreicht). Fix: `@Import(SecurityConfig.class)` plus
echte Filterkette (kein `addFilters = false` mehr), mit `JwtService`/`UserRepository`/
`UserDetailsServiceImpl` als `@MockitoBean`, da nie ein echtes Token durch die Tests geschickt wird.

**Nicht umgesetzt:** Owner-/Sichtbarkeitsprüfung für `GET /executions` und `GET
/executions/{id}` (nur `POST /executions` prüft, dass der Prompt für den Aufrufer lesbar ist,
über `PromptAccess.requireReadable` auf `version.getPrompt()`) — bewusst kleiner Schnitt für
diesen Commit, siehe möglicher Folgeschritt.

## Tests

```bash
./mvnw test
```

Testcontainers startet dafür automatisch einen Postgres-Container über Docker — echte Datenbank statt
H2, weil die Migration Postgres-spezifisches SQL nutzt (`gen_random_uuid()`, `jsonb`), das H2 nicht
versteht.
