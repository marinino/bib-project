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

## Tests

```bash
./mvnw test
```

Testcontainers startet dafür automatisch einen Postgres-Container über Docker — echte Datenbank statt
H2, weil die Migration Postgres-spezifisches SQL nutzt (`gen_random_uuid()`, `jsonb`), das H2 nicht
versteht.
