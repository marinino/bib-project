import { useState } from "react";
import { decodeJwt } from "../api/client";
import { unauthenticatedProbe } from "../api/endpoints";
import { ErrorBox, Note, Panel } from "./ui";

/** Zeigt das aktuelle Token samt dekodierter Payload und die Ownership-relevante User-ID. */
export function SessionPanel({
  token,
  email,
  onLogout,
}: {
  token: string;
  email: string;
  onLogout: () => void;
}) {
  const [showToken, setShowToken] = useState(false);
  const [probeError, setProbeError] = useState<unknown>(null);
  const claims = decodeJwt(token);
  const expiresAt = claims === null ? null : new Date(claims.exp * 1000);

  async function probe() {
    setProbeError(null);
    try {
      await unauthenticatedProbe();
    } catch (e) {
      setProbeError(e);
    }
  }

  return (
    <Panel
      title="Sitzung"
      endpoint={email}
      actions={
        <button className="link" onClick={onLogout}>
          abmelden
        </button>
      }
    >
      <div className="row small">
        <span className="badge accent">sub {claims?.sub.slice(0, 8) ?? "?"}…</span>
        <span className="muted">
          gueltig bis {expiresAt === null ? "?" : expiresAt.toLocaleTimeString("de-DE")}
        </span>
        <button className="link" onClick={() => setShowToken((v) => !v)}>
          {showToken ? "Token verbergen" : "Token zeigen"}
        </button>
      </div>

      {showToken && (
        <>
          <pre className="json" style={{ marginTop: 8 }}>
            {token}
          </pre>
          <pre className="json" style={{ marginTop: 4 }}>
            {JSON.stringify(claims, null, 2)}
          </pre>
        </>
      )}

      <Note>
        <b>Der Server merkt sich nichts.</b> Die <code>sub</code>-Claim ist die User-ID; genau sie
        landet als <code>owner_id</code> an jedem Prompt und entscheidet spaeter ueber jeden
        Zugriff. <code>JwtAuthenticationFilter</code> laedt den Nutzer bei jedem Request direkt
        ueber diese ID — nicht nochmal ueber <code>UserDetailsService</code>, der wird nur beim
        Login fuer den Passwortvergleich gebraucht.
      </Note>

      <div className="row">
        <button onClick={() => void probe()}>Request ohne Token senden</button>
        <span className="muted small">erzeugt bewusst ein 401</span>
      </div>
      <ErrorBox error={probeError} />
      <Note>
        <b>Warum das interessant ist:</b> Diese Ablehnung kommt aus der Security-Filterkette und
        erreicht nie einen Controller — also auch nie <code>@RestControllerAdvice</code>. Spring
        Securitys Default waere ein nacktes <code>sendError(401)</code> mit{" "}
        <code>Content-Length: 0</code> gewesen. <code>ProblemDetailSecurityHandlers</code>{" "}
        schreibt stattdessen dasselbe ProblemDetail-JSON wie jeder andere Fehler. Im Request-Log
        rechts steht der Body.
      </Note>
    </Panel>
  );
}
