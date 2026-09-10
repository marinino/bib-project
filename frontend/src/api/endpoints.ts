import { request } from "./client";
import type {
  AuthResponse,
  ExecutionResponse,
  PageResponse,
  PromptResponse,
  TagResponse,
  VersionResponse,
  Visibility,
} from "./types";

// Eine Funktion pro Backend-Endpoint, in derselben Reihenfolge wie die Controller.

// --- AuthController: /api/v1/auth (permitAll) ---

export const register = (email: string, password: string) =>
  request<AuthResponse>("/api/v1/auth/register", { method: "POST", body: { email, password } });

export const login = (email: string, password: string) =>
  request<AuthResponse>("/api/v1/auth/login", { method: "POST", body: { email, password } });

// --- PromptController: /api/v1/prompts ---

export interface SearchParams {
  query?: string;
  tags?: string[];
  page?: number;
  size?: number;
  sort?: string;
}

export function searchPrompts(params: SearchParams) {
  const search = new URLSearchParams();
  if (params.query) search.set("query", params.query);
  // Set<String> tags: Spring bindet wiederholte Parameter derselben Namens zur Menge.
  params.tags?.forEach((tag) => search.append("tags", tag));
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 20));
  search.set("sort", params.sort ?? "updatedAt,desc");
  return request<PageResponse<PromptResponse>>(`/api/v1/prompts?${search}`);
}

export const getPrompt = (id: string) => request<PromptResponse>(`/api/v1/prompts/${id}`);

export interface CreatePromptBody {
  title: string;
  description?: string | null;
  content: string;
  tags?: string[];
  visibility?: Visibility;
}

export const createPrompt = (body: CreatePromptBody) =>
  request<PromptResponse>("/api/v1/prompts", { method: "POST", body });

export interface UpdatePromptBody {
  title?: string;
  description?: string;
  tags?: string[];
  visibility?: Visibility;
}

export const updatePrompt = (id: string, body: UpdatePromptBody) =>
  request<PromptResponse>(`/api/v1/prompts/${id}`, { method: "PATCH", body });

export const deletePrompt = (id: string) =>
  request<void>(`/api/v1/prompts/${id}`, { method: "DELETE" });

// --- PromptVersionController: /api/v1/prompts/{promptId}/versions ---

export const listVersions = (promptId: string) =>
  request<VersionResponse[]>(`/api/v1/prompts/${promptId}/versions`);

export interface CreateVersionBody {
  content: string;
  parameters?: Record<string, unknown> | null;
  notes?: string | null;
}

export const createVersion = (promptId: string, body: CreateVersionBody) =>
  request<VersionResponse>(`/api/v1/prompts/${promptId}/versions`, { method: "POST", body });

export const activateVersion = (promptId: string, versionNo: number) =>
  request<PromptResponse>(`/api/v1/prompts/${promptId}/versions/${versionNo}/activate`, {
    method: "POST",
  });

// --- ExecutionController: /api/v1/executions ---

export interface CreateExecutionBody {
  promptId: string;
  versionNo: number;
  model?: string | null;
  inputParams?: Record<string, unknown> | null;
}

export const createExecution = (body: CreateExecutionBody) =>
  request<ExecutionResponse>("/api/v1/executions", { method: "POST", body });

export const getExecution = (id: string) =>
  request<ExecutionResponse>(`/api/v1/executions/${id}`);

export const listExecutions = (promptId: string) =>
  request<ExecutionResponse[]>(`/api/v1/executions?promptId=${promptId}`);

// --- TagController: /api/v1/tags ---

export const listTags = () => request<TagResponse[]>("/api/v1/tags");

// --- Actuator (permitAll, silent: gehoert nicht in das API-Request-Log) ---

export interface HealthResponse {
  status: string;
  components?: Record<string, { status: string; details?: Record<string, unknown> }>;
}

export const health = () => request<HealthResponse>("/actuator/health", { silent: true });

interface MetricResponse {
  measurements: { statistic: string; value: number }[];
}

async function metricValue(name: string, tag?: string): Promise<number | null> {
  try {
    const query = tag === undefined ? "" : `?tag=${tag}`;
    const metric = await request<MetricResponse>(`/actuator/metrics/${name}${query}`, {
      silent: true,
    });
    return metric.measurements[0]?.value ?? null;
  } catch {
    return null;
  }
}

export interface RuntimeMetrics {
  asyncActive: number | null;
  asyncCompleted: number | null;
  poolActive: number | null;
  poolIdle: number | null;
}

/**
 * Zwei Zahlenpaare, die genau die Entwurfsentscheidung hinter der Async-Ausfuehrung
 * belegen: wie viele Threads des `executionTaskExecutor` gerade arbeiten und wie viele
 * Connections der Pool dabei belegt hat (naemlich keine, solange nur der LLM-Call laeuft).
 */
export async function runtimeMetrics(): Promise<RuntimeMetrics> {
  const executor = "name:executionTaskExecutor";
  const pool = "pool:HikariPool-1";
  const [asyncActive, asyncCompleted, poolActive, poolIdle] = await Promise.all([
    metricValue("executor.active", executor),
    metricValue("executor.completed", executor),
    metricValue("hikaricp.connections.active", pool),
    metricValue("hikaricp.connections.idle", pool),
  ]);
  return { asyncActive, asyncCompleted, poolActive, poolIdle };
}

/** Absichtlich ohne Authorization-Header - zeigt den 401-ProblemDetail aus der Filterkette. */
export const unauthenticatedProbe = () =>
  request<unknown>("/api/v1/prompts?page=0&size=1", { omitToken: true });
