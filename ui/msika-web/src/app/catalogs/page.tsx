"use client";
import { useState, useEffect } from "react";
import { apiClient, StepUpRequiredError, type PagedList } from "@/lib/apiClient";

interface Catalog {
  catalogId: string;
  name: string;
  scope: string;
  status: string;
  version: string;
  itemCount: number;
  publishedAt: string | null;
  createdAt: string;
}

export default function CatalogsPage() {
  const [catalogs, setCatalogs] = useState<Catalog[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState("");
  const [scope, setScope] = useState("NATIONAL");
  const [version, setVersion] = useState("1.0.0");
  const [stepUp, setStepUp] = useState(false);

  const load = async () => {
    try {
      const data = await apiClient.get<PagedList<Catalog>>("/v1/catalogs?size=50");
      setCatalogs(data?.items || []);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const create = async () => {
    try {
      await apiClient.post("/v1/catalogs", { name, scope, version });
      setShowCreate(false);
      setName(""); setVersion("1.0.0");
      load();
    } catch (e) {
      if (e instanceof StepUpRequiredError) setStepUp(true);
      else alert(String(e));
    }
  };

  const action = async (id: string, act: string) => {
    try {
      setStepUp(false);
      await apiClient.post(`/v1/catalogs/${id}/${act}`);
      load();
    } catch (e) {
      if (e instanceof StepUpRequiredError) setStepUp(true);
      else alert(String(e));
    }
  };

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-xl font-bold">Catalogs</h2>
        <button onClick={() => setShowCreate(!showCreate)}
          className="bg-primary text-white px-4 py-2 rounded text-sm hover:bg-primary-dark">
          + New Catalog
        </button>
      </div>

      {stepUp && (
        <div className="bg-warning-soft border border-amber-300 text-warning-foreground px-4 py-3 rounded mb-4">
          Step-up authentication required. Please re-authenticate with elevated privileges.
        </div>
      )}

      {showCreate && (
        <div className="bg-card p-4 rounded shadow mb-4 border">
          <div className="grid grid-cols-3 gap-4">
            <input placeholder="Catalog name" value={name} onChange={e => setName(e.target.value)}
              className="border rounded px-3 py-2 text-sm" />
            <select value={scope} onChange={e => setScope(e.target.value)}
              className="border rounded px-3 py-2 text-sm">
              <option value="NATIONAL">National</option>
              <option value="TENANT">Tenant</option>
            </select>
            <input placeholder="Version (1.0.0)" value={version} onChange={e => setVersion(e.target.value)}
              className="border rounded px-3 py-2 text-sm" />
          </div>
          <button onClick={create}
            className="mt-3 bg-primary text-white px-4 py-2 rounded text-sm">Create</button>
        </div>
      )}

      {loading ? (
        <p className="text-muted-foreground">Loading...</p>
      ) : (
        <table className="w-full bg-card rounded shadow text-sm">
          <thead className="bg-background">
            <tr>
              <th className="text-left px-4 py-3">Name</th>
              <th className="text-left px-4 py-3">Scope</th>
              <th className="text-left px-4 py-3">Version</th>
              <th className="text-left px-4 py-3">Status</th>
              <th className="text-left px-4 py-3">Items</th>
              <th className="text-left px-4 py-3">Actions</th>
            </tr>
          </thead>
          <tbody>
            {catalogs.map(c => (
              <tr key={c.catalogId} className="border-t hover:bg-background">
                <td className="px-4 py-3 font-medium">{c.name}</td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-0.5 rounded text-xs ${c.scope === 'NATIONAL' ? 'bg-blue-100 text-blue-800' : 'bg-purple-100 text-purple-800'}`}>
                    {c.scope}
                  </span>
                </td>
                <td className="px-4 py-3 font-mono text-xs">{c.version}</td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-0.5 rounded text-xs ${
                    c.status === 'PUBLISHED' ? 'bg-green-100 text-green-800' :
                    c.status === 'APPROVED' ? 'bg-teal-100 text-teal-800' :
                    c.status === 'REVIEW' ? 'bg-yellow-100 text-yellow-800' :
                    'bg-neutral-100 text-foreground'
                  }`}>{c.status}</span>
                </td>
                <td className="px-4 py-3">{c.itemCount}</td>
                <td className="px-4 py-3 space-x-2">
                  {c.status === 'DRAFT' && (
                    <button onClick={() => action(c.catalogId, 'submit-review')}
                      className="text-xs text-primary hover:underline">Submit Review</button>
                  )}
                  {c.status === 'REVIEW' && (
                    <button onClick={() => action(c.catalogId, 'approve')}
                      className="text-xs text-teal-600 hover:underline">Approve</button>
                  )}
                  {c.status === 'APPROVED' && (
                    <button onClick={() => action(c.catalogId, 'publish')}
                      className="text-xs text-green-600 hover:underline">Publish</button>
                  )}
                  <a href={`/items?catalogId=${c.catalogId}`}
                    className="text-xs text-muted-foreground hover:underline">View Items</a>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
