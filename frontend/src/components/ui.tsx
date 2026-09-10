import type { ReactNode } from "react";
import { ApiError } from "../api/client";

export function Panel({
  title,
  endpoint,
  actions,
  children,
}: {
  title: string;
  endpoint?: string;
  actions?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="panel">
      <h2>
        <span>{title}</span>
        <span className="row tight">
          {endpoint !== undefined && <span className="endpoint">{endpoint}</span>}
          {actions}
        </span>
      </h2>
      <div className="body">{children}</div>
    </section>
  );
}

/** Kurze Erklaerung, welches Backend-Verhalten das Bedienelement daneben sichtbar macht. */
export function Note({ children }: { children: ReactNode }) {
  return <p className="note">{children}</p>;
}

export function Json({ value }: { value: unknown }) {
  return <pre className="json">{JSON.stringify(value, null, 2)}</pre>;
}

/**
 * Fehleranzeige direkt aus dem ProblemDetail des Backends - inklusive der Feldfehler,
 * die GlobalExceptionHandler bei Bean-Validation als Zusatzfeld `errors` mitliefert.
 */
export function ErrorBox({ error }: { error: unknown }) {
  if (error === null || error === undefined) {
    return null;
  }
  if (error instanceof ApiError) {
    return (
      <div className="error">
        <span className="badge err">{error.status}</span>{" "}
        {error.problem?.detail ?? error.problem?.title ?? error.message}
        {error.problem?.errors !== undefined && (
          <ul className="small" style={{ margin: "4px 0 0", paddingLeft: 18 }}>
            {error.problem.errors.map((e) => (
              <li key={e.field}>
                <code>{e.field}</code>: {e.message}
              </li>
            ))}
          </ul>
        )}
      </div>
    );
  }
  return <div className="error">{error instanceof Error ? error.message : String(error)}</div>;
}

export function statusClass(status: string): string {
  switch (status) {
    case "SUCCEEDED":
      return "ok";
    case "FAILED":
      return "err";
    case "RUNNING":
    case "PENDING":
      return "warn";
    default:
      return "";
  }
}

export function formatInstant(value: string | null): string {
  if (value === null) {
    return "—";
  }
  return new Date(value).toLocaleString("de-DE");
}
