"use client";
import { useState } from "react";
import { apiClient, type PagedList } from "@/lib/apiClient";

interface CatalogItemProduct {
  form?: string;
  strength?: string;
  route?: string;
  barcode?: string;
}

interface CatalogItemService {
  serviceCategory?: string;
  durationMinutes?: number;
}

interface CatalogItem {
  itemId: string;
  kind: string;
  canonicalCode: string;
  displayName: string;
  description: string;
  restrictions: Record<string, unknown> | null;
  product?: CatalogItemProduct | null;
  service?: CatalogItemService | null;
}

export default function BrowsePage() {
  const [query, setQuery] = useState("");
  const [kind, setKind] = useState("");
  const [results, setResults] = useState<CatalogItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  const search = async () => {
    setLoading(true);
    setSearched(true);
    try {
      const params = new URLSearchParams({ size: "50" });
      if (query) params.set("q", query);
      if (kind) params.set("kind", kind);
      const data = await apiClient.get<PagedList<CatalogItem>>(`/v1/search?${params}`);
      setResults(data?.items || []);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <h2 className="text-xl font-bold mb-4">Browse & Search</h2>

      <div className="flex gap-3 mb-6">
        <input placeholder="Search products, services, codes..."
          value={query} onChange={e => setQuery(e.target.value)}
          onKeyDown={e => e.key === "Enter" && search()}
          className="flex-1 border rounded px-4 py-2 text-sm" />
        <select value={kind} onChange={e => setKind(e.target.value)}
          className="border rounded px-3 py-2 text-sm">
          <option value="">All Kinds</option>
          <option value="PRODUCT">Product</option>
          <option value="SERVICE">Service</option>
          <option value="ORDERABLE">Orderable</option>
          <option value="CHARGEABLE">Chargeable</option>
        </select>
        <button onClick={search}
          className="bg-primary text-white px-6 py-2 rounded text-sm hover:bg-primary-dark">
          Search
        </button>
      </div>

      {loading ? (
        <p className="text-gray-500">Searching...</p>
      ) : results.length > 0 ? (
        <div className="space-y-3">
          {results.map(item => (
            <div key={item.itemId} className="bg-white p-4 rounded shadow border">
              <div className="flex justify-between items-start">
                <div>
                  <h3 className="font-semibold">{item.displayName}</h3>
                  <p className="text-xs font-mono text-gray-400">{item.canonicalCode}</p>
                  {item.description && <p className="text-sm text-gray-600 mt-1">{item.description}</p>}
                </div>
                <span className="px-2 py-0.5 rounded text-xs bg-gray-100">{item.kind}</span>
              </div>
              {item.product && (
                <div className="mt-2 text-xs text-gray-500">
                  {[item.product.form, item.product.strength, item.product.route].filter(Boolean).join(" | ")}
                  {item.product.barcode && ` | GTIN: ${item.product.barcode}`}
                </div>
              )}
              {item.service && (
                <div className="mt-2 text-xs text-gray-500">
                  {item.service.serviceCategory} {item.service.durationMinutes && `| ${item.service.durationMinutes} min`}
                </div>
              )}
            </div>
          ))}
        </div>
      ) : searched ? (
        <p className="text-gray-500">No results found.</p>
      ) : (
        <p className="text-gray-500">Enter a search term or select a kind to browse.</p>
      )}
    </div>
  );
}
