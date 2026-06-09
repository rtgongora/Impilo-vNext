"use client";

import { useEffect, useRef, useState } from "react";
import { Loader2, Search } from "lucide-react";
import { apiClient } from "@/lib/api-client";

export interface Icd11Hit {
  code: string;
  description: string;
  chapter?: string;
}

interface Props {
  label?: string;
  value?: Icd11Hit | null;
  onSelect: (hit: Icd11Hit) => void;
  placeholder?: string;
}

export function Icd11SearchField({ label = "ICD-11 diagnosis", value, onSelect, placeholder }: Props) {
  const [q, setQ] = useState(value?.description ?? "");
  const [results, setResults] = useState<Icd11Hit[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const debounceRef = useRef<number | null>(null);

  useEffect(() => {
    if (value?.description) setQ(value.description);
  }, [value?.code, value?.description]);

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
        .get<{ data: Icd11Hit[] }>(`/internal/v1/diagnosis/icd11/search?q=${encodeURIComponent(q.trim())}`)
        .then((res) => setResults(res.data ?? []))
        .catch(() => setError("ICD-11 search unavailable"))
        .finally(() => setLoading(false));
    }, 350);
    return () => {
      if (debounceRef.current) window.clearTimeout(debounceRef.current);
    };
  }, [q]);

  return (
    <div className="space-y-1">
      <label className="text-sm font-medium">{label}</label>
      <div className="relative">
        <div className="flex items-center gap-1 rounded border border-gray-300 px-2 py-1">
          <Search className="h-3.5 w-3.5 text-gray-400" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder={placeholder ?? "Search ICD-11 by condition name…"}
            className="flex-1 text-sm outline-none bg-transparent"
          />
          {loading && <Loader2 className="h-3.5 w-3.5 animate-spin text-gray-400" />}
        </div>
        {value?.code && (
          <p className="text-xs text-gray-600 mt-1">
            Selected: <code>{value.code}</code>
          </p>
        )}
        {error && <p className="text-xs text-red-600">{error}</p>}
        {results.length > 0 && (
          <ul className="absolute z-10 mt-1 w-full max-h-48 overflow-y-auto rounded border bg-white shadow divide-y">
            {results.map((r) => (
              <li key={`${r.code}-${r.description}`}>
                <button
                  type="button"
                  onClick={() => {
                    onSelect(r);
                    setQ(r.description);
                    setResults([]);
                  }}
                  className="block w-full px-2 py-2 text-left text-sm hover:bg-red-50"
                >
                  <span className="font-mono text-xs text-gray-500">{r.code}</span>
                  <span className="ml-2">{r.description}</span>
                  {r.chapter && <span className="block text-xs text-gray-400">{r.chapter}</span>}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
