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

## Tests

```bash
./mvnw test
```

Testcontainers startet dafür automatisch einen Postgres-Container über Docker — echte Datenbank statt
H2, weil die Migration Postgres-spezifisches SQL nutzt (`gen_random_uuid()`, `jsonb`), das H2 nicht
versteht.
