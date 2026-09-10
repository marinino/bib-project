import { useState } from "react";
import { login, register } from "../api/endpoints";
import { ErrorBox, Note, Panel } from "./ui";

/**
 * Registrierung und Login gegen /api/v1/auth - die einzigen Endpoints, die
 * SecurityConfig auf permitAll setzt. Alles andere ist authenticated().
 */
export function AuthPanel({ onToken }: { onToken: (token: string) => void }) {
  const [email, setEmail] = useState("demo@promptlib.local");
  const [password, setPassword] = useState("demo-passwort");
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function submit(mode: "register" | "login") {
    setBusy(true);
    setError(null);
    try {
      const call = mode === "register" ? register : login;
      const response = await call(email, password);
      onToken(response.token);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Panel title="Anmeldung" endpoint="POST /api/v1/auth/register · /login">
      <div className="stack" style={{ maxWidth: 380 }}>
        <label className="field">
          E-Mail
          <input value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" />
        </label>
        <label className="field">
          Passwort (mind. 8 Zeichen)
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </label>
        <div className="row">
          <button className="primary" disabled={busy} onClick={() => void submit("register")}>
            Registrieren
          </button>
          <button disabled={busy} onClick={() => void submit("login")}>
            Einloggen
          </button>
        </div>
        <ErrorBox error={error} />
      </div>

      <Note>
        <b>BCrypt + JWT.</b> Beide Endpoints geben dasselbe <code>AuthResponse</code> mit einem
        HS256-signierten Token zurueck. Es gibt keine Session: <code>SecurityConfig</code> setzt{" "}
        <code>SessionCreationPolicy.STATELESS</code>, jeder folgende Request traegt das Token im{" "}
        <code>Authorization: Bearer</code>-Header.
      </Note>
      <Note>
        <b>Keine Nutzer-Enumeration.</b> Falsches Passwort und unbekannte E-Mail liefern beide
        exakt <code>401 &quot;Invalid email or password&quot;</code>. Zum Nachsehen: absichtlich ein
        falsches Passwort eingeben und den Response-Body im Request-Log vergleichen. Eine zweite
        Registrierung derselben Adresse ergibt dagegen <code>409 Conflict</code>.
      </Note>
      <Note>
        <b>Bean Validation.</b> Ein Passwort mit weniger als 8 Zeichen oder eine ungueltige E-Mail
        kommt gar nicht bis in den Service: <code>@Valid</code> loest aus, und{" "}
        <code>GlobalExceptionHandler</code> macht daraus ein <code>400</code> mit einer
        Feldfehler-Liste im <code>errors</code>-Feld des ProblemDetail.
      </Note>
    </Panel>
  );
}
