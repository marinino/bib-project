import { useCallback, useEffect, useState } from "react";
import { health, runtimeMetrics } from "../api/endpoints";
import type { HealthResponse, RuntimeMetrics } from "../api/endpoints";

const EMPTY: RuntimeMetrics = {
  asyncActive: null,
  asyncCompleted: null,
  poolActive: null,
  poolIdle: null,
};

/**
 * Liest ausschliesslich Actuator-Endpoints. Die sind in SecurityConfig auf permitAll
 * gesetzt und funktionieren deshalb auch ohne Login. Diese Calls landen bewusst nicht im
 * Request-Log - dort soll nur die eigentliche API stehen.
 */
export function BackendPanel() {
  const [status, setStatus] = useState<HealthResponse | null>(null);
  const [metrics, setMetrics] = useState<RuntimeMetrics>(EMPTY);
  const [reachable, setReachable] = useState(true);

  const refresh = useCallback(async () => {
    try {
      setStatus(await health());
      setReachable(true);
      setMetrics(await runtimeMetrics());
    } catch {
      setStatus(null);
      setMetrics(EMPTY);
      setReachable(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
    const timer = setInterval(() => void refresh(), 5000);
    return () => clearInterval(timer);
  }, [refresh]);

  const db = status?.components?.["db"];

  return (
    <section className="panel">
      <h2>
        <span>Backend</span>
        <span className="row tight">
          <span className="endpoint">/actuator</span>
          <button className="link" onClick={() => void refresh()}>
            aktualisieren
          </button>
        </span>
      </h2>
      <div className="body">
        <div className="row small">
          <span className={`badge ${reachable && status?.status === "UP" ? "ok" : "err"}`}>
            {reachable ? (status?.status ?? "?") : "nicht erreichbar"}
          </span>
          {db !== undefined && (
            <span className={`badge ${db.status === "UP" ? "ok" : "err"}`}>
              db {String(db.details?.["database"] ?? "")}
            </span>
          )}
        </div>

        {reachable && (
          <>
            <div className="row small" style={{ marginTop: 6 }}>
              <span className="badge">
                async {format(metrics.asyncActive)} aktiv / {format(metrics.asyncCompleted)}{" "}
                erledigt
              </span>
              <span className="badge">
                pool {format(metrics.poolActive)} aktiv / {format(metrics.poolIdle)} frei
              </span>
            </div>
            <p className="note" style={{ marginBottom: 0 }}>
              Die linke Zahl ist der <code>executionTaskExecutor</code>, auf dem die LLM-Calls
              laufen; die rechte der HikariCP-Pool. Beim Start einer Ausfuehrung steigt der
              Executor-Zaehler, die aktiven DB-Connections bleiben bei null — genau das ist der
              Punkt: waehrend des LLM-Calls ist keine Transaktion offen.
            </p>
          </>
        )}

        {!reachable && (
          <p className="muted small" style={{ marginBottom: 0 }}>
            Laeuft <code>./mvnw spring-boot:run</code> auf Port 8080?
          </p>
        )}
      </div>
    </section>
  );
}

function format(value: number | null): string {
  return value === null ? "?" : String(value);
}
