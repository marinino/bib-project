import { useCallback, useEffect, useState } from "react";
import { listTags, searchPrompts } from "../api/endpoints";
import type { PageResponse, PromptResponse, TagResponse } from "../api/types";
import { ErrorBox, Note, Panel, formatInstant } from "./ui";

const SORTS = [
  { value: "updatedAt,desc", label: "zuletzt geaendert" },
  { value: "createdAt,desc", label: "neueste zuerst" },
  { value: "title,asc", label: "Titel A–Z" },
];

export function PromptList({
  ownUserId,
  reloadKey,
  onSelect,
}: {
  ownUserId: string | null;
  reloadKey: number;
  onSelect: (id: string) => void;
}) {
  const [query, setQuery] = useState("");
  const [activeTags, setActiveTags] = useState<string[]>([]);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState(SORTS[0].value);

  const [result, setResult] = useState<PageResponse<PromptResponse> | null>(null);
  const [tags, setTags] = useState<TagResponse[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      setResult(await searchPrompts({ query, tags: activeTags, page, size, sort }));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }, [query, activeTags, page, size, sort]);

  useEffect(() => {
    void load();
  }, [load, reloadKey]);

  useEffect(() => {
    listTags().then(setTags).catch(setError);
  }, [reloadKey]);

  function toggleTag(name: string) {
    setPage(0);
    setActiveTags((current) =>
      current.includes(name) ? current.filter((t) => t !== name) : [...current, name],
    );
  }

  return (
    <Panel
      title="Prompts suchen"
      endpoint="GET /api/v1/prompts?query=&tags=&page=&size=&sort="
      actions={
        <button className="link" onClick={() => void load()} disabled={busy}>
          neu laden
        </button>
      }
    >
      <div className="row">
        <input
          style={{ flex: 1 }}
          value={query}
          placeholder="Volltext in Titel und Beschreibung"
          onChange={(e) => {
            setPage(0);
            setQuery(e.target.value);
          }}
        />
        <select
          value={sort}
          onChange={(e) => {
            setPage(0);
            setSort(e.target.value);
          }}
        >
          {SORTS.map((s) => (
            <option key={s.value} value={s.value}>
              {s.label}
            </option>
          ))}
        </select>
        <select
          value={size}
          onChange={(e) => {
            setPage(0);
            setSize(Number(e.target.value));
          }}
        >
          {[5, 10, 20, 50].map((n) => (
            <option key={n} value={n}>
              {n} / Seite
            </option>
          ))}
        </select>
      </div>

      <div className="row tight" style={{ marginTop: 8 }}>
        <span className="muted small">Tags:</span>
        {tags.length === 0 && <span className="muted small">noch keine</span>}
        {tags.map((tag) => (
          <button
            key={tag.name}
            className="badge"
            style={
              activeTags.includes(tag.name)
                ? { background: "var(--accent)", color: "#fff", borderColor: "var(--accent)" }
                : undefined
            }
            onClick={() => toggleTag(tag.name)}
          >
            {tag.name} ({tag.promptCount})
          </button>
        ))}
      </div>

      <ErrorBox error={error} />

      <table style={{ marginTop: 12 }}>
        <thead>
          <tr>
            <th>Titel</th>
            <th>Tags</th>
            <th>Version</th>
            <th>Sichtbarkeit</th>
            <th>geaendert</th>
          </tr>
        </thead>
        <tbody>
          {result?.content.map((prompt) => (
            <tr key={prompt.id} className="clickable" onClick={() => onSelect(prompt.id)}>
              <td>
                <b>{prompt.title}</b>
                {prompt.description !== null && (
                  <div className="muted small">{prompt.description}</div>
                )}
              </td>
              <td className="row tight">
                {prompt.tags.map((t) => (
                  <span key={t} className="badge">
                    {t}
                  </span>
                ))}
              </td>
              <td className="mono">v{prompt.currentVersionNo ?? "—"}</td>
              <td>
                <span className={`badge ${prompt.visibility === "PUBLIC" ? "ok" : ""}`}>
                  {prompt.visibility}
                </span>
                {prompt.ownerId !== ownUserId && <span className="badge warn">fremd</span>}
              </td>
              <td className="muted small">{formatInstant(prompt.updatedAt)}</td>
            </tr>
          ))}
          {result !== null && result.content.length === 0 && (
            <tr>
              <td colSpan={5} className="muted small">
                Keine Treffer.
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {result !== null && (
        <div className="spread" style={{ marginTop: 8 }}>
          <span className="muted small mono">
            page {result.page} / {Math.max(result.totalPages - 1, 0)} · size {result.size} ·
            totalElements {result.totalElements}
          </span>
          <span className="row tight">
            <button disabled={result.page === 0} onClick={() => setPage((p) => p - 1)}>
              zurueck
            </button>
            <button
              disabled={result.page >= result.totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              weiter
            </button>
          </span>
        </div>
      )}

      <Note>
        <b>Diese Liste kostet konstant 2 SQL-Statements</b> — eine Content-Query und eine
        Count-Query — egal wie viele Prompts auf der Seite stehen. Ohne den{" "}
        <code>LEFT JOIN FETCH</code> in <code>PromptSpecifications.fetchTags()</code> wuerde die
        Tag-Spalte pro Zeile eine eigene Lazy-Load-Query ausloesen: das klassische N+1
        (2+n statt 2). Der Fetch greift bewusst nur bei der Content-Query, nicht bei der
        Count-Query.
      </Note>
      <Note>
        <b>Die Suche ist Teil der Autorisierung.</b> <code>visibleTo(requesterId)</code> haengt als
        eigene Specification an jeder Abfrage, kombiniert mit Volltext- und Tag-Filter. Fremde
        private Prompts tauchen deshalb gar nicht erst als Treffer auf — sie werden nicht
        nachtraeglich aus dem Ergebnis gefiltert, sondern nie geladen. Der Tag-Filter ist ein
        ODER (<code>hasAnyTag</code>).
      </Note>
      <Note>
        <b>Sort und Pagination gehen direkt an Spring Data.</b> <code>page</code>,{" "}
        <code>size</code> und <code>sort</code> bindet <code>@PageableDefault</code> an ein{" "}
        <code>Pageable</code>; die Antwort ist ein eigenes <code>PageResponse</code>-DTO statt
        Springs <code>Page</code>, dessen JSON-Form nicht stabil ist.
      </Note>
    </Panel>
  );
}
