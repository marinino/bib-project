import { pushEntry } from "./log";
import type { ProblemDetail } from "./types";

const TOKEN_KEY = "promptlib.token";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null): void {
  if (token === null) {
    localStorage.removeItem(TOKEN_KEY);
  } else {
    localStorage.setItem(TOKEN_KEY, token);
  }
}

/** Ein fehlgeschlagener Request samt geparstem ProblemDetail-Body des Backends. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem: ProblemDetail | null,
  ) {
    super(problem?.detail ?? problem?.title ?? `HTTP ${status}`);
    this.name = "ApiError";
  }
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  /** Token bewusst weglassen - fuer die 401-Demo. */
  omitToken?: boolean;
  /** Infrastruktur-Calls (/actuator) nicht ins Request-Log schreiben. */
  silent?: boolean;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, omitToken = false, silent = false } = options;

  const headers: Record<string, string> = {};
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  // Stateless: kein Cookie, keine Session. Der Client haengt das JWT bei jedem
  // Request selbst an - genau deshalb ist CSRF-Schutz im Backend abgeschaltet.
  const token = getToken();
  if (token !== null && !omitToken) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const startedAt = performance.now();
  let status: number | null = null;
  let payload: unknown = null;

  try {
    const response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    status = response.status;
    payload = await readBody(response);

    if (!response.ok) {
      throw new ApiError(response.status, payload as ProblemDetail | null);
    }
    return payload as T;
  } finally {
    if (!silent) {
      pushEntry({
        method,
        path,
        status,
        ok: status !== null && status >= 200 && status < 300,
        durationMs: Math.round(performance.now() - startedAt),
        requestBody: body ?? null,
        responseBody: payload,
      });
    }
  }
}

async function readBody(response: Response): Promise<unknown> {
  // 204 No Content (DELETE /prompts/{id}) hat definitionsgemaess keinen Body.
  if (response.status === 204) {
    return null;
  }
  const text = await response.text();
  if (text.length === 0) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export interface JwtClaims {
  sub: string;
  iat: number;
  exp: number;
  [key: string]: unknown;
}

/**
 * Dekodiert die Payload lokal - reine Anzeige. Die Signatur prueft ausschliesslich das
 * Backend (JwtService, HS256); ein Client koennte das gar nicht, er hat das Secret nicht.
 */
export function decodeJwt(token: string): JwtClaims | null {
  const parts = token.split(".");
  if (parts.length !== 3) {
    return null;
  }
  try {
    const json = atob(parts[1].replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(json) as JwtClaims;
  } catch {
    return null;
  }
}
