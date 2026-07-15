"use client";

/**
 * Patient search field (WU6) — resolve a patient by NAME / Impilo ID against the client
 * registry (MPI), replacing raw CPID/UUID paste in the ED surfaces. Debounced live
 * search over GET /internal/v1/client-registry/clients. Emits the selected Health ID
 * (CPID) to the caller. No mocks.
 */

import { useEffect, useRef, useState } from "react";
import { Loader2, Search } from "lucide-react";
import { apiClient } from "@/lib/api-client";

export interface PatientHit {
  healthId: string;
  displayName?: string;
  crid?: string;
  impiloId?: string;
}

interface Props {
  label?: string;
  value?: PatientHit | null;
  onSelect: (hit: PatientHit) => void;
  placeholder?: string;
}

export function PatientSearchField({ label = "Patient", value, onSelect, placeholder }: Props) {
  const [q, setQ] = useState(value?.displayName ?? "");
  const [results, setResults] = useState<PatientHit[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const debounceRef = useRef<number | null>(null);

  useEffect(() => {
    if (value?.displayName) setQ(value.displayName);
  }, [value?.healthId, value?.displayName]);

  useEffect(() => {
    if (debounceRef.current) window.clearTimeout(debounceRef.current);
    if (!q || q.trim().length < 2) {
      setResults([]);
      return;
    }
    debounceRef.current = window.setTimeout(() => {
      setLoading(true);
      setError(null);
      apiClient
        .get<{ data: { items: PatientHit[] } }>(`/internal/v1/client-registry/clients?query=${encodeURIComponent(q.trim())}&page=0&size=8`)
        .then((res) => setResults(res.data?.items ?? []))
        .catch(() => setError("Patient search unavailable"))
        .finally(() => setLoading(false));
    }, 350);
    return () => {
      if (debounceRef.current) window.clearTimeout(debounceRef.current);
    };
  }, [q]);

  return (
    <div className="space-y-1">
      <label className="text-xs font-medium text-muted-foreground">{label}</label>
      <div className="relative">
        <div className="flex items-center gap-1 rounded border border-border px-2 py-1">
          <Search className="h-3.5 w-3.5 text-muted-foreground" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder={placeholder ?? "Search by name or Impilo ID…"}
            className="flex-1 bg-transparent text-sm outline-none"
            data-testid="patient-search-input"
          />
          {loading && <Loader2 className="h-3.5 w-3.5 animate-spin text-muted-foreground" />}
        </div>
        {value?.healthId && (
          <p className="mt-1 text-xs text-muted-foreground">
            Selected: <code className="font-mono">{value.displayName || value.healthId}</code>
          </p>
        )}
        {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
        {results.length > 0 && (
          <ul className="absolute z-10 mt-1 max-h-48 w-full divide-y overflow-y-auto rounded border bg-card shadow">
            {results.map((r) => (
              <li key={r.healthId}>
                <button
                  type="button"
                  onClick={() => {
                    onSelect(r);
                    setQ(r.displayName ?? r.healthId);
                    setResults([]);
                  }}
                  className="block w-full px-2 py-2 text-left text-sm hover:bg-background"
                >
                  <span>{r.displayName || "Unnamed"}</span>
                  <span className="ml-2 font-mono text-xs text-muted-foreground">{r.impiloId || r.healthId.slice(0, 10)}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
