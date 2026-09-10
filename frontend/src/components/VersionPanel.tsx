import { useState } from "react";
import { activateVersion, createVersion } from "../api/endpoints";
import type { VersionResponse } from "../api/types";
import { ErrorBox, Note, Panel, formatInstant } from "./ui";

const MARKERS = [
  { marker: "__FAIL__", hint: "LlmException → Retry, dann FAILED" },
  { marker: "__BUG__", hint: "IllegalStateException → Catch-all-Netz" },
  { marker: "__SLOW__", hint: "kuenstliche Latenz" },
];

export function VersionPanel({
  promptId,
  versions,
  currentVersionNo,
  isOwner,
  onChanged,
}: {
  promptId: string;
  versions: VersionResponse[];
  currentVersionNo: number | null;
  isOwner: boolean;
  onChanged: () => void;
}) {
  const [content, setContent] = useState("");
  const [notes, setNotes] = useState("");
  const [parameters, setParameters] = useState("");
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

  async function submit() {
    let parsed: Record<string, unknown> | null = null;
    if (parameters.trim().length > 0) {
      try {
        parsed = JSON.parse(parameters) as Record<string, unknown>;
      } catch {
        setError(new Error("parameters ist kein gueltiges JSON"));
        return;
      }
    }
    await run(async () => {
      await createVersion(promptId, {
        content,
        parameters: parsed,
        notes: notes === "" ? null : notes,
      });
      setContent("");
      setNotes("");
      setParameters("");
    });
  }

  return (
    <Panel title="Versionen" endpoint="GET · POST /api/v1/prompts/{id}/versions">
      <table>
        <thead>
          <tr>
            <th>Nr.</th>
            <th>Inhalt</th>
            <th>Parameter</th>
            <th>Notiz</th>
            <th>angelegt</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {versions.map((version) => (
            <tr key={version.versionNo}>
              <td className="mono">
                v{version.versionNo}
                {version.versionNo === currentVersionNo && (
                  <>
                    {" "}
                    <span className="badge accent">aktiv</span>
                  </>
                )}
              </td>
              <td className="mono" style={{ maxWidth: 320, whiteSpace: "pre-wrap" }}>
                {version.content}
              </td>
              <td className="mono muted">
                {version.parameters === null ? "—" : JSON.stringify(version.parameters)}
              </td>
              <td className="small muted">{version.notes ?? "—"}</td>
              <td className="small muted">{formatInstant(version.createdAt)}</td>
              <td>
                <button
                  className="link"
                  disabled={!isOwner || busy || version.versionNo === currentVersionNo}
                  onClick={() => void run(() => activateVersion(promptId, version.versionNo))}
                >
                  aktivieren
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <Note>
        <b>Versionsnummern werden nicht geraten.</b> <code>POST</code> sperrt die Prompt-Zeile per{" "}
        <code>SELECT … FOR UPDATE</code>, bevor es <code>max(version_no) + 1</code> bildet — sonst
        koennten zwei parallele Requests denselben veralteten Hoechstwert lesen und beide dieselbe
        Nummer vergeben. Ein <code>UNIQUE(prompt_id, version_no)</code> liegt als Sicherheitsnetz
        darunter. Der Nachweis sind 10 echte parallele Requests im{" "}
        <code>PromptVersionConcurrencyTest</code>.
      </Note>

      <div className="stack" style={{ marginTop: 12 }}>
        <label className="field">
          Inhalt der neuen Version
          <textarea rows={3} value={content} onChange={(e) => setContent(e.target.value)} />
        </label>
        <div className="row tight">
          <span className="muted small">Mock-Marker einfuegen:</span>
          {MARKERS.map((m) => (
            <button
              key={m.marker}
              className="badge"
              title={m.hint}
              onClick={() => setContent((c) => `${c} ${m.marker}`.trim())}
            >
              {m.marker}
            </button>
          ))}
        </div>
        <div className="row">
          <label className="field" style={{ flex: 1 }}>
            parameters (JSON, optional)
            <input
              value={parameters}
              onChange={(e) => setParameters(e.target.value)}
              placeholder='{"temperature": 0.2}'
            />
          </label>
          <label className="field" style={{ flex: 1 }}>
            notes (optional)
            <input value={notes} onChange={(e) => setNotes(e.target.value)} />
          </label>
        </div>
        <div className="row">
          <button className="primary" disabled={!isOwner || busy} onClick={() => void submit()}>
            Version anlegen
          </button>
          {!isOwner && <span className="muted small">nur der Owner darf schreiben</span>}
        </div>
        <ErrorBox error={error} />
      </div>

      <Note>
        <b>Die Marker gehoeren zum Backend, nicht zu diesem UI.</b>{" "}
        <code>MockLlmClient</code> ist aktiv, solange kein <code>real</code>-Profil laeuft, und
        erkennt sie im Prompt-Text. Damit lassen sich Fehlerpfade ohne API-Key und ohne Flakiness
        ausloesen: <code>__FAIL__</code> wirft eine <code>LlmException</code> (erwarteter Fehler,
        wird retried), <code>__BUG__</code> eine <code>IllegalStateException</code> (echter Bug —
        das Netz dafuer ist der Catch-all im <code>ExecutionRunner</code>).
      </Note>
      <Note>
        <b><code>parameters</code> ist eine <code>jsonb</code>-Spalte.</b> Deshalb laufen Tests
        gegen echtes Postgres per Testcontainers und nicht gegen H2 — H2 kennt weder{" "}
        <code>jsonb</code> noch <code>gen_random_uuid()</code>.
      </Note>
    </Panel>
  );
}
