"use client";

import { useState } from "react";
import { Card, CardHeader, CardTitle } from "shared-ui";

interface AuditEntry {
  id: string;
  documentId: string;
  action: string;
  actorId: string;
  actorType: string;
  reason: string;
  createdAt: string;
}

export default function DocumentAuditPage() {
  const [documentId, setDocumentId] = useState("");
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [loading, setLoading] = useState(false);

  async function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    if (!documentId) return;
    setLoading(true);
    try {
      const res = await fetch(`/api/v1/documents/${documentId}/audit`);
      const data = await res.json();
      setEntries(data.data?.items ?? data.data ?? []);
    } catch {
      setEntries([]);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Document Audit Trail</h1>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle>Lookup</CardTitle>
        </CardHeader>
        <form onSubmit={handleSearch} className="p-4 flex gap-4 items-end">
          <div className="flex-1">
            <label className="block text-sm font-medium text-neutral-700 mb-1">Document ID</label>
            <input
              type="text"
              value={documentId}
              onChange={(e) => setDocumentId(e.target.value)}
              placeholder="Enter document UUID"
              className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm"
              required
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50"
          >
            {loading ? "Loading..." : "View Audit"}
          </button>
        </form>
      </Card>

      {entries.length > 0 && (
        <Card>
          <div className="p-4">
            <h2 className="text-lg font-semibold mb-4">Audit Timeline</h2>
            <div className="space-y-3">
              {entries.map((entry) => (
                <div key={entry.id} className="flex items-start gap-3 p-3 bg-neutral-50 rounded-lg">
                  <div className={`w-2 h-2 rounded-full mt-2 ${
                    entry.action === "CREATED" ? "bg-green-500" :
                    entry.action === "REVOKED" ? "bg-red-500" :
                    entry.action === "DOWNLOADED" ? "bg-blue-500" :
                    "bg-neutral-400"
                  }`} />
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium">{entry.action}</span>
                      <span className="text-xs text-neutral-500">by {entry.actorType}/{entry.actorId.slice(0, 8)}</span>
                    </div>
                    {entry.reason && <p className="text-xs text-neutral-600 mt-1">{entry.reason}</p>}
                    <p className="text-xs text-neutral-400 mt-1">{new Date(entry.createdAt).toLocaleString()}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </Card>
      )}
    </div>
  );
}
