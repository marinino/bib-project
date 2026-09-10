// Spiegelt 1:1 die Java-Records aus dem Backend. Bewusst handgeschrieben statt generiert:
// so bleibt sichtbar, welcher Vertrag hier eigentlich konsumiert wird.

/** de.marinic.promptlib.prompt.Visibility */
export type Visibility = "PRIVATE" | "PUBLIC";

/** de.marinic.promptlib.execution.ExecutionStatus */
export type ExecutionStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED";

/** prompt.dto.PromptResponse */
export interface PromptResponse {
  id: string;
  title: string;
  description: string | null;
  currentVersionNo: number | null;
  tags: string[];
  ownerId: string | null;
  visibility: Visibility;
  createdAt: string;
  updatedAt: string;
}

/** prompt.dto.VersionResponse */
export interface VersionResponse {
  versionNo: number;
  content: string;
  parameters: Record<string, unknown> | null;
  notes: string | null;
  createdAt: string;
}

/** execution.dto.ExecutionResponse */
export interface ExecutionResponse {
  id: string;
  promptId: string;
  versionNo: number;
  status: ExecutionStatus;
  model: string | null;
  output: string | null;
  latencyMs: number | null;
  tokensIn: number | null;
  tokensOut: number | null;
  errorMessage: string | null;
  createdAt: string;
  finishedAt: string | null;
}

/** tag.dto.TagResponse */
export interface TagResponse {
  name: string;
  promptCount: number;
}

/** common.page.PageResponse<T> */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** auth.dto.AuthResponse */
export interface AuthResponse {
  token: string;
}

/**
 * RFC 9457 ProblemDetail, wie GlobalExceptionHandler und ProblemDetailSecurityHandlers
 * es liefern. `errors` setzt nur der Validierungs-Handler als Zusatzfeld.
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: { field: string; message: string }[];
}
