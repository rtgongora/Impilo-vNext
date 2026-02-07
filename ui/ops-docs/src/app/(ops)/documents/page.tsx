"use client";

import { useState } from "react";
import { Card, CardHeader, CardTitle } from "shared-ui";

interface DocumentResult {
  documentId: string;
  subjectType: string;
  subjectId: string;
  documentTypeCode: string;
  originalFilename: string;
  mimeType: string;
  confidentiality: string;
  lifecycleState: string;
  createdAt: string;
}

export default function DocumentSearchPage() {
  const [query, setQuery] = useState("");
  const [subjectType, setSubjectType] = useState("");
  const [results, setResults] = useState<DocumentResult[]>([]);
  const [loading, setLoading] = useState(false);

  async function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (query) params.set("q", query);
      if (subjectType) params.set("subjectType", subjectType);
      const res = await fetch(`/api/v1/documents?${params}`);
      const data = await res.json();
      setResults(data.data?.items ?? []);
    } catch {
      setResults([]);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Document Search</h1>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle>Search Filters</CardTitle>
        </CardHeader>
        <form onSubmit={handleSearch} className="p-4 flex gap-4 items-end">
          <div className="flex-1">
            <label className="block text-sm font-medium text-neutral-700 mb-1">
              Search Query
            </label>
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Document ID, filename, or subject ID..."
              className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:ring-2 focus:ring-brand-primary focus:border-brand-primary"
            />
          </div>
          <div className="w-48">
            <label className="block text-sm font-medium text-neutral-700 mb-1">
              Subject Type
            </label>
            <select
              value={subjectType}
              onChange={(e) => setSubjectType(e.target.value)}
              className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:ring-2 focus:ring-brand-primary"
            >
              <option value="">All</option>
              <option value="CLIENT">Client</option>
              <option value="PROVIDER">Provider</option>
              <option value="FACILITY">Facility</option>
              <option value="ENCOUNTER">Encounter</option>
            </select>
          </div>
          <button
            type="submit"
            disabled={loading}
            className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50"
          >
            {loading ? "Searching..." : "Search"}
          </button>
        </form>
      </Card>

      {results.length > 0 && (
        <Card>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-200">
                  <th className="text-left p-3 font-medium text-neutral-500">Document ID</th>
                  <th className="text-left p-3 font-medium text-neutral-500">Type</th>
                  <th className="text-left p-3 font-medium text-neutral-500">Subject</th>
                  <th className="text-left p-3 font-medium text-neutral-500">Filename</th>
                  <th className="text-left p-3 font-medium text-neutral-500">State</th>
                  <th className="text-left p-3 font-medium text-neutral-500">Created</th>
                </tr>
              </thead>
              <tbody>
                {results.map((doc) => (
                  <tr key={doc.documentId} className="border-b border-neutral-100 hover:bg-neutral-50">
                    <td className="p-3 font-mono text-xs">{doc.documentId.slice(0, 8)}...</td>
                    <td className="p-3">{doc.documentTypeCode}</td>
                    <td className="p-3">{doc.subjectType}/{doc.subjectId.slice(0, 8)}</td>
                    <td className="p-3">{doc.originalFilename}</td>
                    <td className="p-3">
                      <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                        doc.lifecycleState === "ACTIVE" ? "bg-green-100 text-green-800" :
                        doc.lifecycleState === "REVOKED" ? "bg-red-100 text-red-800" :
                        "bg-neutral-100 text-neutral-600"
                      }`}>
                        {doc.lifecycleState}
                      </span>
                    </td>
                    <td className="p-3 text-neutral-500">{new Date(doc.createdAt).toLocaleDateString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {results.length === 0 && !loading && (
        <div className="text-center py-12 text-neutral-500">
          Search for documents by keyword, subject type, or document ID
        </div>
      )}
    </div>
  );
}
