"use client";
import { useState, useEffect } from "react";
import { apiClient } from "@/lib/apiClient";
import { useSearchParams } from "next/navigation";

interface CatalogItem {
  itemId: string;
  kind: string;
  canonicalCode: string;
  displayName: string;
  description: string;
  tags: string[];
  active: boolean;
  createdAt: string;
}

export default function ItemsPage() {
  const searchParams = useSearchParams();
  const catalogId = searchParams.get("catalogId") || "";
  const [items, setItems] = useState<CatalogItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [kind, setKind] = useState("");
  const [showAdd, setShowAdd] = useState(false);
  const [form, setForm] = useState({ kind: "PRODUCT", canonicalCode: "", displayName: "", description: "" });

  const load = async () => {
    if (!catalogId) return;
    setLoading(true);
    try {
      const params = new URLSearchParams({ size: "50" });
      if (kind) params.set("kind", kind);
      const data = await apiClient.get<any>(`/v1/catalogs/${catalogId}/items?${params}`);
      setItems(data?.items || []);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [catalogId, kind]);

  const addItem = async () => {
    try {
      await apiClient.post(`/v1/catalogs/${catalogId}/items`, form);
      setShowAdd(false);
      setForm({ kind: "PRODUCT", canonicalCode: "", displayName: "", description: "" });
      load();
    } catch (e) {
      alert(String(e));
    }
  };

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <div>
          <h2 className="text-xl font-bold">Catalog Items</h2>
          {catalogId && <p className="text-sm text-gray-500 mt-1">Catalog: {catalogId}</p>}
        </div>
        <div className="flex gap-2">
          <select value={kind} onChange={e => setKind(e.target.value)}
            className="border rounded px-3 py-2 text-sm">
            <option value="">All Kinds</option>
            <option value="PRODUCT">Product</option>
            <option value="SERVICE">Service</option>
            <option value="ORDERABLE">Orderable</option>
            <option value="CHARGEABLE">Chargeable</option>
            <option value="CAPABILITY_FACILITY">Facility Capability</option>
            <option value="CAPABILITY_PROVIDER">Provider Capability</option>
          </select>
          {catalogId && (
            <button onClick={() => setShowAdd(!showAdd)}
              className="bg-primary text-white px-4 py-2 rounded text-sm hover:bg-primary-dark">
              + Add Item
            </button>
          )}
        </div>
      </div>

      {!catalogId && (
        <div className="bg-blue-50 border border-blue-200 text-blue-800 px-4 py-3 rounded">
          Select a catalog from the Catalogs page to view its items.
        </div>
      )}

      {showAdd && (
        <div className="bg-white p-4 rounded shadow mb-4 border">
          <div className="grid grid-cols-2 gap-4">
            <select value={form.kind} onChange={e => setForm({...form, kind: e.target.value})}
              className="border rounded px-3 py-2 text-sm">
              <option value="PRODUCT">Product</option>
              <option value="SERVICE">Service</option>
              <option value="ORDERABLE">Orderable</option>
              <option value="CHARGEABLE">Chargeable</option>
              <option value="CAPABILITY_FACILITY">Facility Capability</option>
              <option value="CAPABILITY_PROVIDER">Provider Capability</option>
            </select>
            <input placeholder="Canonical Code" value={form.canonicalCode}
              onChange={e => setForm({...form, canonicalCode: e.target.value})}
              className="border rounded px-3 py-2 text-sm" />
            <input placeholder="Display Name" value={form.displayName}
              onChange={e => setForm({...form, displayName: e.target.value})}
              className="border rounded px-3 py-2 text-sm" />
            <input placeholder="Description" value={form.description}
              onChange={e => setForm({...form, description: e.target.value})}
              className="border rounded px-3 py-2 text-sm" />
          </div>
          <button onClick={addItem}
            className="mt-3 bg-primary text-white px-4 py-2 rounded text-sm">Add Item</button>
        </div>
      )}

      {loading ? (
        <p className="text-gray-500">Loading...</p>
      ) : items.length > 0 ? (
        <table className="w-full bg-white rounded shadow text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="text-left px-4 py-3">Code</th>
              <th className="text-left px-4 py-3">Name</th>
              <th className="text-left px-4 py-3">Kind</th>
              <th className="text-left px-4 py-3">Tags</th>
              <th className="text-left px-4 py-3">Active</th>
            </tr>
          </thead>
          <tbody>
            {items.map(item => (
              <tr key={item.itemId} className="border-t hover:bg-gray-50">
                <td className="px-4 py-3 font-mono text-xs">{item.canonicalCode}</td>
                <td className="px-4 py-3 font-medium">{item.displayName}</td>
                <td className="px-4 py-3">
                  <span className="px-2 py-0.5 rounded text-xs bg-gray-100">{item.kind}</span>
                </td>
                <td className="px-4 py-3">
                  {item.tags?.map(t => (
                    <span key={t} className="mr-1 px-2 py-0.5 bg-blue-50 text-blue-700 rounded text-xs">{t}</span>
                  ))}
                </td>
                <td className="px-4 py-3">{item.active ? "Yes" : "No"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : catalogId ? (
        <p className="text-gray-500">No items found in this catalog.</p>
      ) : null}
    </div>
  );
}
