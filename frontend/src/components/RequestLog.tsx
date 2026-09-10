import { clearEntries, useRequestLog } from "../api/log";
import { Json } from "./ui";

/**
 * Zeigt jeden API-Call mit Methode, Pfad, Statuscode und beiden Bodies. Damit werden die
 * Backend-Entscheidungen direkt sichtbar, die man einer normalen Oberflaeche sonst nicht
 * ansieht: 202 statt 200 beim Start einer Execution, 404 statt 403 bei fremden privaten
 * Prompts, ProblemDetail-JSON statt leerem Body bei 401.
 */
export function RequestLog() {
  const entries = useRequestLog();

  return (
    <section className="panel">
      <h2>
        <span>Request-Log</span>
        <span className="row tight">
          <span className="endpoint">{entries.length} Calls</span>
          <button className="link" onClick={clearEntries} disabled={entries.length === 0}>
            leeren
          </button>
        </span>
      </h2>
      <div className="body">
        {entries.length === 0 ? (
          <p className="muted small" style={{ margin: 0 }}>
            Noch keine Requests. Jeder Aufruf gegen <code>/api/v1</code> landet hier.
          </p>
        ) : (
          entries.map((entry) => (
            <details key={entry.id} className="log-entry">
              <summary>
                <span className={`badge ${entry.ok ? "ok" : "err"}`}>{entry.status ?? "ERR"}</span>
                <b>{entry.method}</b>
                <span className="path">{entry.path}</span>
                <span className="muted">{entry.durationMs} ms</span>
              </summary>
              <div className="detail">
                <div className="muted small">{entry.at}</div>
                {entry.requestBody !== null && (
                  <>
                    <h4>Request-Body</h4>
                    <Json value={entry.requestBody} />
                  </>
                )}
                <h4>Response-Body</h4>
                {entry.responseBody === null ? (
                  <pre className="json">(leer)</pre>
                ) : (
                  <Json value={entry.responseBody} />
                )}
              </div>
            </details>
          ))
        )}
      </div>
    </section>
  );
}
