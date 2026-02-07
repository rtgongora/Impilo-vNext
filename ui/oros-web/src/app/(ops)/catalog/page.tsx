"use client";

import { useState, useEffect, useCallback } from "react";
import {
  orosApi,
  type CatalogItem,
  type OrderType,
  type CatalogImportItem,
} from "@/lib/orosApi";

const ORDER_TYPES: OrderType[] = ["LAB", "IMAGING", "PHARMACY", "REFERRAL", "PROCEDURE", "SUPPLY"];

const TYPE_BADGE: Record<OrderType, string> = {
  LAB: "badge bg-purple-100 text-purple-800",
  IMAGING: "badge bg-blue-100 text-blue-800",
  PHARMACY: "badge bg-emerald-100 text-emerald-800",
  REFERRAL: "badge bg-teal-100 text-teal-800",
  PROCEDURE: "badge bg-amber-100 text-amber-800",
  SUPPLY: "badge bg-neutral-100 text-neutral-700",
};

export default function CatalogPage() {
  const [items, setItems] = useState<CatalogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Filters
  const [filterType, setFilterType] = useState<string>("");
  const [filterActive, setFilterActive] = useState<string>("");
  const [searchQuery, setSearchQuery] = useState<string>("");

  // Import form
  const [showImportForm, setShowImportForm] = useState(false);
  const [importContent, setImportContent] = useState("");

  const loadCatalog = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params: Record<string, string> = {};
      if (filterType) params.type = filterType;
      if (filterActive) params.active = filterActive;
      if (searchQuery) params.q = searchQuery;
      const data = await orosApi.getCatalog(params);
      setItems(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load catalog");
    } finally {
      setLoading(false);
    }
  }, [filterType, filterActive, searchQuery]);

  useEffect(() => {
    loadCatalog();
  }, [loadCatalog]);

  const handleImport = async () => {
    if (!importContent.trim()) {
      setError("Import content is required.");
      return;
    }

    setActionLoading(true);
    setError(null);

    try {
      let importItems: CatalogImportItem[];
      try {
        const parsed = JSON.parse(importContent);
        importItems = Array.isArray(parsed) ? parsed : parsed.items || [parsed];
      } catch {
        setError("Invalid JSON. Expected an array of catalog items or { items: [...] }.");
        setActionLoading(false);
        return;
      }

      const result = await orosApi.importCatalog(importItems);
      setSuccessMessage(
        `Import complete: ${result.imported} imported, ${result.updated} updated, ${result.skipped} skipped, ${result.errors.length} errors.`,
      );
      setImportContent("");
      setShowImportForm(false);
      await loadCatalog();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Import failed");
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-neutral-200 rounded w-1/3" />
          <div className="h-4 bg-neutral-200 rounded w-2/3" />
          <div className="card p-6 space-y-3">
            {[1, 2, 3, 4, 5].map((i) => (
              <div key={i} className="h-10 bg-neutral-100 rounded" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Catalog</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Browse and import orderable items for clinical order placement.
          </p>
        </div>
        <button
          onClick={() => setShowImportForm(!showImportForm)}
          className="btn-primary"
        >
          Import Catalog
        </button>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="mb-4 p-3 rounded-lg bg-emerald-50 border border-emerald-200 text-sm text-emerald-800">
          {successMessage}
          <button
            onClick={() => setSuccessMessage(null)}
            className="ml-2 text-emerald-600 hover:text-emerald-800 font-medium"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Import form */}
      {showImportForm && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Import Catalog Items</h2>
          <p className="text-sm text-neutral-600">
            Paste a JSON array of catalog items. Each item needs at minimum: code, name, and orderType.
          </p>
          <div>
            <label htmlFor="importContent" className="block text-sm font-medium text-neutral-700 mb-1">
              JSON Content
            </label>
            <textarea
              id="importContent"
              value={importContent}
              onChange={(e) => setImportContent(e.target.value)}
              placeholder={'[\n  {\n    "code": "2160-0",\n    "codeSystem": "http://loinc.org",\n    "name": "Serum Creatinine",\n    "orderType": "LAB",\n    "category": "Chemistry",\n    "active": true\n  }\n]'}
              rows={12}
              className="input-field font-mono text-xs"
            />
          </div>
          <div className="flex gap-2">
            <button onClick={handleImport} disabled={actionLoading} className="btn-primary">
              {actionLoading ? "Importing..." : "Import"}
            </button>
            <button onClick={() => setShowImportForm(false)} className="btn-secondary">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Filters */}
      <div className="flex gap-4 mb-4">
        <div className="flex-1">
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by name, code, or description..."
            className="input-field"
          />
        </div>
        <select
          value={filterType}
          onChange={(e) => setFilterType(e.target.value)}
          className="select-field w-48"
        >
          <option value="">All Types</option>
          {ORDER_TYPES.map((t) => (
            <option key={t} value={t}>
              {t.charAt(0) + t.slice(1).toLowerCase()}
            </option>
          ))}
        </select>
        <select
          value={filterActive}
          onChange={(e) => setFilterActive(e.target.value)}
          className="select-field w-36"
        >
          <option value="">All Status</option>
          <option value="true">Active</option>
          <option value="false">Inactive</option>
        </select>
      </div>

      {/* Catalog table */}
      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Code</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Name</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Type</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Category</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Specimen</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">TAT (min)</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Active</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Updated</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {items.map((item) => (
              <tr key={item.id} className="hover:bg-neutral-50 transition-colors">
                <td className="px-4 py-3">
                  <div className="text-neutral-900 font-mono text-xs font-medium">
                    {item.code}
                  </div>
                  {item.codeSystem && (
                    <div className="text-neutral-400 font-mono text-[10px] mt-0.5 truncate max-w-[200px]">
                      {item.codeSystem}
                    </div>
                  )}
                </td>
                <td className="px-4 py-3 text-neutral-900">
                  {item.name}
                  {item.description && (
                    <div className="text-neutral-400 text-xs mt-0.5 truncate max-w-[250px]">
                      {item.description}
                    </div>
                  )}
                </td>
                <td className="px-4 py-3">
                  <span className={TYPE_BADGE[item.orderType]}>
                    {item.orderType}
                  </span>
                </td>
                <td className="px-4 py-3 text-neutral-600 text-xs">
                  {item.category || "--"}
                </td>
                <td className="px-4 py-3 text-neutral-600 text-xs">
                  {item.specimenType || "--"}
                </td>
                <td className="px-4 py-3 text-neutral-600 text-xs">
                  {item.turnaroundMinutes != null ? item.turnaroundMinutes : "--"}
                </td>
                <td className="px-4 py-3">
                  {item.active ? (
                    <span className="badge bg-emerald-100 text-emerald-800">Active</span>
                  ) : (
                    <span className="badge bg-neutral-100 text-neutral-500">Inactive</span>
                  )}
                </td>
                <td className="px-4 py-3 text-neutral-500 text-xs">
                  {new Date(item.updatedAt).toLocaleDateString()}
                </td>
              </tr>
            ))}
            {items.length === 0 && (
              <tr>
                <td colSpan={8} className="px-4 py-12 text-center text-neutral-500">
                  No catalog items found. Import items to populate the catalog.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="mt-3 text-xs text-neutral-400">
        {items.length} item{items.length !== 1 ? "s" : ""} total
      </div>
    </div>
  );
}
