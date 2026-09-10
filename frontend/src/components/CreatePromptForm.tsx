import { useState } from "react";
import { createPrompt } from "../api/endpoints";
import type { PromptResponse, Visibility } from "../api/types";
import { ErrorBox, Note, Panel } from "./ui";

export function CreatePromptForm({ onCreated }: { onCreated: (prompt: PromptResponse) => void }) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [content, setContent] = useState("");
  const [tags, setTags] = useState("");
  const [visibility, setVisibility] = useState<Visibility>("PRIVATE");
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      const prompt = await createPrompt({
        title,
        description: description === "" ? null : description,
        content,
        tags: splitTags(tags),
        visibility,
      });
      setTitle("");
      setDescription("");
      setContent("");
      setTags("");
      onCreated(prompt);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Panel title="Neuer Prompt" endpoint="POST /api/v1/prompts → 201">
      <div className="stack">
        <label className="field">
          Titel (Pflicht, max. 200)
          <input value={title} onChange={(e) => setTitle(e.target.value)} />
        </label>
        <label className="field">
          Beschreibung
          <input value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
        <label className="field">
          Inhalt von Version 1 (Pflicht)
          <textarea rows={3} value={content} onChange={(e) => setContent(e.target.value)} />
        </label>
        <div className="row">
          <label className="field" style={{ flex: 1 }}>
            Tags (kommagetrennt)
            <input
              value={tags}
              onChange={(e) => setTags(e.target.value)}
              placeholder="summary, german"
            />
          </label>
          <label className="field">
            Sichtbarkeit
            <select
              value={visibility}
              onChange={(e) => setVisibility(e.target.value as Visibility)}
            >
              <option value="PRIVATE">PRIVATE</option>
              <option value="PUBLIC">PUBLIC</option>
            </select>
          </label>
        </div>
        <div className="row">
          <button className="primary" disabled={busy} onClick={() => void submit()}>
            Anlegen
          </button>
          <span className="muted small">
            Leerer Titel oder Inhalt → 400 mit Feldfehlern (Bean Validation)
          </span>
        </div>
        <ErrorBox error={error} />
      </div>

      <Note>
        <b>Ein Request, drei Tabellen.</b> Der Service legt in einer Transaktion den Prompt,
        seine Version 1 und die Tags an. Tags entstehen per{" "}
        <code>INSERT … ON CONFLICT (name) DO NOTHING</code> statt Find-or-Create — sonst wuerden
        zwei parallele Requests mit demselben neuen Tag in eine{" "}
        <code>DataIntegrityViolationException</code> laufen, die die Transaktion beschaedigt.
        Namen werden dabei auf Kleinschreibung normalisiert.
      </Note>
      <Note>
        <b>Owner kommt nicht aus dem Body.</b> <code>owner_id</code> setzt der Controller aus dem{" "}
        <code>@AuthenticationPrincipal</code>, nie aus dem Request — sonst koennte sich jeder
        Aufrufer beim Anlegen einen fremden Owner eintragen.
      </Note>
    </Panel>
  );
}

function splitTags(raw: string): string[] {
  return raw
    .split(",")
    .map((t) => t.trim())
    .filter((t) => t.length > 0);
}
