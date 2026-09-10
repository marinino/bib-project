import { useSyncExternalStore } from "react";

export interface LogEntry {
  id: number;
  method: string;
  path: string;
  status: number | null;
  ok: boolean;
  durationMs: number;
  requestBody: unknown;
  responseBody: unknown;
  at: string;
}

// Winziger Store ohne Zustandsbibliothek: das UI soll nichts erklaeren muessen ausser
// dem Backend.
let entries: LogEntry[] = [];
let nextId = 1;
const listeners = new Set<() => void>();

const MAX_ENTRIES = 60;

export function pushEntry(entry: Omit<LogEntry, "id" | "at">): void {
  const full: LogEntry = {
    ...entry,
    id: nextId++,
    at: new Date().toLocaleTimeString("de-DE"),
  };
  entries = [full, ...entries].slice(0, MAX_ENTRIES);
  listeners.forEach((l) => l());
}

export function clearEntries(): void {
  entries = [];
  listeners.forEach((l) => l());
}

export function useRequestLog(): LogEntry[] {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => entries,
  );
}
