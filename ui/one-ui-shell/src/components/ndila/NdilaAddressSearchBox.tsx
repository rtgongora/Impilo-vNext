"use client";

/**
 * NdilaAddressSearchBox — typeahead geocoding box with keyboard navigation.
 */

import { useEffect, useMemo, useRef, useState } from "react";
import { Loader2, MapPin, Search, X } from "lucide-react";
import { ndilaGeocode, type NdilaGeocodeResult } from "@/lib/ndila/ndila-client";

interface Props {
  initialQuery?: string;
  country?: string;
  province?: string;
  district?: string;
  purposeOfUse?: string;
  onSelect?: (result: NdilaGeocodeResult) => void;
}

export function NdilaAddressSearchBox({
  initialQuery = "", country, province, district, purposeOfUse, onSelect,
}: Props) {
  const [q, setQ] = useState(initialQuery);
  const [results, setResults] = useState<NdilaGeocodeResult[]>([]);
  const [provider, setProvider] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeIndex, setActiveIndex] = useState(-1);
  const debounceRef = useRef<number | null>(null);

  useEffect(() => {
    if (debounceRef.current) window.clearTimeout(debounceRef.current);
    if (!q || q.trim().length < 3) { setResults([]); setActiveIndex(-1); return; }
    debounceRef.current = window.setTimeout(() => {
      setLoading(true); setError(null);
      ndilaGeocode(q, { country, province, district, purposeOfUse })
        .then((resp) => {
          if (!resp.success) { setError(resp.denialReason || "Geocoding denied"); setResults([]); return; }
          setProvider(resp.provider);
          setResults(resp.results || []);
          setActiveIndex(resp.results?.length ? 0 : -1);
        })
        .catch(() => setError("Ndila unreachable"))
        .finally(() => setLoading(false));
    }, 300);
    return () => { if (debounceRef.current) window.clearTimeout(debounceRef.current); };
  }, [q, country, province, district, purposeOfUse]);

  const headerLabel = useMemo(
    () => provider ? `Searching via ${provider}` : "Type 3+ characters to search places",
    [provider],
  );

  const pickResult = (result: NdilaGeocodeResult) => {
    setQ(result.name ?? result.address ?? q);
    setResults([]);
    setActiveIndex(-1);
    onSelect?.(result);
  };

  return (
    <div className="relative">
      <div className="flex items-center gap-1 rounded-lg border border-border bg-card px-2 py-1.5 shadow-sm">
        <Search className="h-3.5 w-3.5 text-muted-foreground" />
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(event) => {
            if (event.key === "ArrowDown") {
              event.preventDefault();
              setActiveIndex((idx) => Math.min(idx + 1, results.length - 1));
            } else if (event.key === "ArrowUp") {
              event.preventDefault();
              setActiveIndex((idx) => Math.max(idx - 1, 0));
            } else if (event.key === "Enter" && activeIndex >= 0 && results[activeIndex]) {
              event.preventDefault();
              pickResult(results[activeIndex]);
            } else if (event.key === "Escape") {
              setResults([]);
              setActiveIndex(-1);
            }
          }}
          placeholder="Search places, facilities, addresses…"
          className="flex-1 text-sm outline-none bg-transparent"
          aria-autocomplete="list"
        />
        {q ? (
          <button type="button" onClick={() => { setQ(""); setResults([]); }} aria-label="Clear search">
            <X className="h-3.5 w-3.5 text-muted-foreground" />
          </button>
        ) : null}
        {loading && <Loader2 className="h-3.5 w-3.5 animate-spin text-muted-foreground" />}
      </div>
      <p className="text-[10px] text-muted-foreground mt-0.5">{headerLabel}</p>
      {error && <p className="text-[10px] text-red-600 mt-0.5">{error}</p>}
      {results.length > 0 && (
        <ul className="absolute z-30 mt-1 max-h-56 w-full overflow-y-auto rounded-lg border border-border bg-card shadow-lg divide-y divide-border">
          {results.map((r, idx) => (
            <li key={`${r.providerPlaceId ?? idx}`}>
              <button
                type="button"
                onClick={() => pickResult(r)}
                className={`block w-full px-3 py-2 text-left text-xs ${idx === activeIndex ? "bg-indigo-50" : "hover:bg-muted"}`}
              >
                <div className="flex items-start gap-2">
                  <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0 text-indigo-600" />
                  <div>
                    <div className="font-medium text-foreground">{r.name ?? r.address ?? "Result"}</div>
                    <div className="text-[10px] text-muted-foreground tabular-nums">
                      {r.latitude.toFixed(4)}, {r.longitude.toFixed(4)}
                      {r.confidence !== undefined ? ` · ${(r.confidence * 100).toFixed(0)}% match` : ""}
                    </div>
                  </div>
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
