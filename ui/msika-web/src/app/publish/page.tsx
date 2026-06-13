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
  checksum: string | null;
}

export default function PublishPage() {
  const [catalogs, setCatalogs] = useState<Catalog[]>([]);
  const [loading, setLoading] = useState(true);
  const [stepUp, setStepUp] = useState(false);

  const load = async () => {
    try {
      const data = await apiClient.get<PagedList<Catalog>>("/v1/catalogs?size=50");
      setCatalogs((data?.items ?? []).filter((c) => c.status === "APPROVED" || c.status === "PUBLISHED"));
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const publish = async (id: string) => {
    try {
      setStepUp(false);
      await apiClient.post(`/v1/catalogs/${id}/publish`);
      load();
    } catch (e) {
      if (e instanceof StepUpRequiredError) setStepUp(true);
      else alert(String(e));
    }
  };

  return (
    <div>
      <h2 className="text-xl font-bold mb-4">Publish Catalogs</h2>
      <p className="text-sm text-muted-foreground mb-4">
        Publishing requires step-up authentication. Only APPROVED catalogs can be published.
      </p>

      {stepUp && (
        <div className="bg-warning-soft border border-amber-300 text-warning-foreground px-4 py-3 rounded mb-4">
          Step-up authentication required. Please re-authenticate with elevated privileges to publish.
        </div>
      )}

      {loading ? (
        <p className="text-muted-foreground">Loading...</p>
      ) : (
        <div className="space-y-4">
          {catalogs.map(c => (
            <div key={c.catalogId} className="bg-card p-4 rounded shadow border flex justify-between items-center">
              <div>
                <h3 className="font-semibold">{c.name} <span className="text-sm font-mono text-muted-foreground">v{c.version}</span></h3>
                <p className="text-sm text-muted-foreground">{c.itemCount} items | {c.scope} | Checksum: {c.checksum || "pending"}</p>
              </div>
              <div className="flex items-center gap-3">
                <span className={`px-3 py-1 rounded text-xs font-medium ${
                  c.status === 'PUBLISHED' ? 'bg-green-100 text-green-800' : 'bg-teal-100 text-teal-800'
                }`}>{c.status}</span>
                {c.status === 'APPROVED' && (
                  <button onClick={() => publish(c.catalogId)}
                    className="bg-green-600 text-white px-4 py-2 rounded text-sm hover:bg-green-700">
                    Publish Now
                  </button>
                )}
              </div>
            </div>
          ))}
          {catalogs.length === 0 && <p className="text-muted-foreground">No catalogs ready for publishing.</p>}
        </div>
      )}
    </div>
  );
}
