import { useCallback, useEffect, useState } from "react";
import { deletePrompt, getPrompt, listVersions, updatePrompt } from "../api/endpoints";
import type { PromptResponse, VersionResponse, Visibility } from "../api/types";
import { ExecutionPanel } from "./ExecutionPanel";
import { VersionPanel } from "./VersionPanel";
import { ErrorBox, Note, Panel, formatInstant } from "./ui";

export function PromptDetail({
  promptId,
  ownUserId,
  onBack,
  onChanged,
}: {
  promptId: string;
  ownUserId: string | null;
  onBack: () => void;
  onChanged: () => void;
}) {
  const [prompt, setPrompt] = useState<PromptResponse | null>(null);
  const [versions, setVersions] = useState<VersionResponse[]>([]);
  const [error, setError] = useState<unknown>(null);

  const reload = useCallback(async () => {
    setError(null);
    try {
      const loaded = await getPrompt(promptId);
      setPrompt(loaded);
      setVersions(await listVersions(promptId));
    } catch (e) {
      setError(e);
      setPrompt(null);
    }
  }, [promptId]);

  useEffect(() => {
    void reload();
  }, [reload]);

  if (prompt === null) {
    return (
      <Panel title="Prompt" endpoint={`GET /api/v1/prompts/${promptId}`}>
        <ErrorBox error={error} />
        <div className="row" style={{ marginTop: 8 }}>
          <button onClick={onBack}>zurueck zur Liste</button>
        </div>
      </Panel>
    );
  }

  const isOwner = prompt.ownerId === ownUserId;

  return (
    <>
      <PromptMeta
        prompt={prompt}
        isOwner={isOwner}
        onBack={onBack}
        onChanged={() => {
          void reload();
          onChanged();
        }}
        onDeleted={() => {
          onBack();
          onChanged();
        }}
      />
      <VersionPanel
        promptId={promptId}
        versions={versions}
        currentVersionNo={prompt.currentVersionNo}
        isOwner={isOwner}
        onChanged={() => {
          void reload();
          onChanged();
        }}
      />
      <ExecutionPanel promptId={promptId} versions={versions} />
    </>
  );
}

function PromptMeta({
  prompt,
  isOwner,
  onBack,
  onChanged,
  onDeleted,
}: {
  prompt: PromptResponse;
  isOwner: boolean;
  onBack: () => void;
  onChanged: () => void;
  onDeleted: () => void;
}) {
  const [title, setTitle] = useState(prompt.title);
  const [description, setDescription] = useState(prompt.description ?? "");
  const [tags, setTags] = useState(prompt.tags.join(", "));
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      onChanged();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  // Eigener Pfad statt run(): nach dem 204 gibt es nichts mehr nachzuladen - ein reload()
  // wuerde hier nur ein 404 auf die gerade geloeschte Zeile erzeugen.
  async function remove() {
    if (!window.confirm(`Prompt "${prompt.title}" wirklich loeschen?`)) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await deletePrompt(prompt.id);
      onDeleted();
    } catch (e) {
      setError(e);
      setBusy(false);
    }
  }

  const otherVisibility: Visibility = prompt.visibility === "PRIVATE" ? "PUBLIC" : "PRIVATE";

  return (
    <Panel
      title={prompt.title}
      endpoint={`PATCH · DELETE /api/v1/prompts/${prompt.id.slice(0, 8)}…`}
      actions={
        <button className="link" onClick={onBack}>
          zurueck
        </button>
      }
    >
      <div className="row small">
        <span className="badge accent">v{prompt.currentVersionNo ?? "—"} aktiv</span>
        <span className={`badge ${prompt.visibility === "PUBLIC" ? "ok" : ""}`}>
          {prompt.visibility}
        </span>
        <span className={`badge ${isOwner ? "ok" : "warn"}`}>
          {isOwner ? "eigener Prompt" : "fremder Prompt"}
        </span>
        <span className="muted">angelegt {formatInstant(prompt.createdAt)}</span>
        <span className="muted">geaendert {formatInstant(prompt.updatedAt)}</span>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        <label className="field">
          Titel
          <input value={title} onChange={(e) => setTitle(e.target.value)} disabled={!isOwner} />
        </label>
        <label className="field">
          Beschreibung
          <input
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            disabled={!isOwner}
          />
        </label>
        <label className="field">
          Tags (kommagetrennt)
          <input value={tags} onChange={(e) => setTags(e.target.value)} disabled={!isOwner} />
        </label>
        <div className="row">
          <button
            className="primary"
            disabled={!isOwner || busy}
            onClick={() =>
              void run(() =>
                updatePrompt(prompt.id, {
                  title,
                  description,
                  tags: tags
                    .split(",")
                    .map((t) => t.trim())
                    .filter((t) => t.length > 0),
                }),
              )
            }
          >
            Speichern
          </button>
          <button
            disabled={!isOwner || busy}
            onClick={() => void run(() => updatePrompt(prompt.id, { visibility: otherVisibility }))}
          >
            auf {otherVisibility} umschalten
          </button>
          <button disabled={!isOwner || busy} onClick={() => void remove()}>
            Loeschen
          </button>
        </div>
        <ErrorBox error={error} />
      </div>

      <Note>
        <b>PATCH ist partiell, und zwar wirklich.</b> <code>UpdatePromptRequest</code> hat lauter
        nullable Felder; der Service schreibt nur, was ungleich <code>null</code> ankommt. Der
        Sichtbarkeits-Knopf schickt deshalb ausschliesslich <code>{"{ visibility }"}</code> — Titel,
        Beschreibung und Tags bleiben unberuehrt. Im Request-Log rechts ist der Body zu sehen.
      </Note>
      <Note>
        <b>Zwei Stufen, zwei Statuscodes.</b> Lesen prueft <code>requireReadable</code>: ein
        fremder <code>PRIVATE</code>-Prompt liefert <code>404</code> — bewusst nicht{" "}
        <code>403</code>, denn ein 403 wuerde verraten, dass unter dieser ID ueberhaupt etwas
        existiert. Schreiben prueft <code>requireOwner</code> und liefert <code>403</code>, weil
        die Existenz an der Stelle ohnehin schon bekannt ist. Zum Ausprobieren: Prompt auf{" "}
        <code>PUBLIC</code> stellen, in einem zweiten Browserprofil einen anderen Nutzer
        registrieren und dort speichern wollen.
      </Note>
    </Panel>
  );
}
