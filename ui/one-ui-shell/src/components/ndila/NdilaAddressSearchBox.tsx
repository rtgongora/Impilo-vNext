"use client";

/**
 * NdilaAddressSearchBox — typeahead geocoding box.
 * Every keystroke is debounced through Ndila's `geocode` endpoint which routes
 * provider selection through NdilaProviderPolicyService (Mock by default,
 * configured Nominatim/Pelias/commercial provider otherwise).
 */

import { useEffect, useMemo, useRef, useState } from "react";
import { Loader2, Search } from "lucide-react";
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
  const debounceRef = useRef<number | null>(null);

  useEffect(() => {
    if (debounceRef.current) window.clearTimeout(debounceRef.current);
    if (!q || q.trim().length < 3) { setResults([]); return; }
    debounceRef.current = window.setTimeout(() => {
      setLoading(true); setError(null);
      ndilaGeocode(q, { country, province, district, purposeOfUse })
        .then((resp) => {
          if (!resp.success) { setError(resp.denialReason || "Geocoding denied"); setResults([]); return; }
          setProvider(resp.provider);
          setResults(resp.results || []);
        })
        .catch(() => setError("Ndila unreachable"))
        .finally(() => setLoading(false));
    }, 350);
    return () => { if (debounceRef.current) window.clearTimeout(debounceRef.current); };
  }, [q, country, province, district, purposeOfUse]);

  const headerLabel = useMemo(
    () => provider ? `provider: ${provider}` : "Type to search via Ndila",
    [provider]
  );

  return (
    <div className="relative">
      <div className="flex items-center gap-1 rounded border border-border px-2 py-1">
        <Search className="h-3.5 w-3.5 text-muted-foreground" />
        <input
          value={q} onChange={(e) => setQ(e.target.value)}
          placeholder="Search location (place, facility, address)…"
          className="flex-1 text-sm outline-none bg-transparent"
        />
        {loading && <Loader2 className="h-3.5 w-3.5 animate-spin text-muted-foreground" />}
      </div>
      <p className="text-[10px] text-muted-foreground mt-0.5">{headerLabel}</p>
      {error && <p className="text-[10px] text-red-600 mt-0.5">{error}</p>}
      {results.length > 0 && (
        <ul className="mt-1 max-h-56 overflow-y-auto rounded border border-border bg-card shadow-sm divide-y divide-gray-100">
          {results.map((r, idx) => (
            <li key={`${r.providerPlaceId ?? idx}`}>
              <button
                type="button"
                onClick={() => onSelect?.(r)}
                className="block w-full px-2 py-1.5 text-left text-xs hover:bg-info-soft"
              >
                <div className="font-medium text-foreground">{r.name ?? r.address ?? "Result"}</div>
                <div className="text-[10px] text-muted-foreground tabular-nums">
                  {r.latitude.toFixed(4)}, {r.longitude.toFixed(4)}
                  {r.confidence !== undefined ? ` · confidence ${(r.confidence * 100).toFixed(0)}%` : ""}
                  {r.provider ? ` · ${r.provider}` : ""}
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
