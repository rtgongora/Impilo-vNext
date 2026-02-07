"use client";

import { useState } from "react";
import { Card, CardHeader, CardTitle, Badge } from "shared-ui";

interface CredentialResult {
  credentialId: string;
  subjectType: string;
  subjectName: string;
  credentialType: string;
  title: string;
  issuedBy: string;
  status: string;
  validFrom: string;
  validTo: string;
}

export default function CredentialSearchPage() {
  const [subjectType, setSubjectType] = useState("");
  const [credentialType, setCredentialType] = useState("");
  const [results, setResults] = useState<CredentialResult[]>([]);
  const [loading, setLoading] = useState(false);

  async function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (subjectType) params.set("subjectType", subjectType);
      if (credentialType) params.set("credentialType", credentialType);
      const res = await fetch(`/api/v1/credentials?${params}`);
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
      <h1 className="text-2xl font-bold mb-6">Credential Search</h1>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle>Search Filters</CardTitle>
        </CardHeader>
        <form onSubmit={handleSearch} className="p-4 flex gap-4 items-end">
          <div className="w-48">
            <label className="block text-sm font-medium text-neutral-700 mb-1">Subject Type</label>
            <select value={subjectType} onChange={(e) => setSubjectType(e.target.value)}
              className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm">
              <option value="">All</option>
              <option value="PROVIDER">Provider</option>
              <option value="CLIENT">Client</option>
              <option value="FACILITY">Facility</option>
            </select>
          </div>
          <div className="w-48">
            <label className="block text-sm font-medium text-neutral-700 mb-1">Credential Type</label>
            <select value={credentialType} onChange={(e) => setCredentialType(e.target.value)}
              className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm">
              <option value="">All</option>
              <option value="LICENSE">License</option>
              <option value="CERTIFICATE">Certificate</option>
              <option value="REGISTRATION">Registration</option>
              <option value="BADGE">Badge</option>
            </select>
          </div>
          <button type="submit" disabled={loading}
            className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50">
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
                  <th className="text-left p-3 font-medium text-neutral-500">Subject</th>
                  <th className="text-left p-3 font-medium text-neutral-500">Type</th>
                  <th className="text-left p-3 font-medium text-neutral-500">Title</th>
                  <th className="text-left p-3 font-medium text-neutral-500">Issued By</th>
                  <th className="text-left p-3 font-medium text-neutral-500">Status</th>
                  <th className="text-left p-3 font-medium text-neutral-500">Valid</th>
                </tr>
              </thead>
              <tbody>
                {results.map((cred) => (
                  <tr key={cred.credentialId} className="border-b border-neutral-100 hover:bg-neutral-50">
                    <td className="p-3">{cred.subjectName}</td>
                    <td className="p-3">{cred.credentialType}</td>
                    <td className="p-3">{cred.title}</td>
                    <td className="p-3">{cred.issuedBy}</td>
                    <td className="p-3">
                      <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                        cred.status === "ACTIVE" ? "bg-green-100 text-green-800" :
                        cred.status === "REVOKED" ? "bg-red-100 text-red-800" :
                        cred.status === "EXPIRED" ? "bg-yellow-100 text-yellow-800" :
                        "bg-neutral-100 text-neutral-600"
                      }`}>{cred.status}</span>
                    </td>
                    <td className="p-3 text-neutral-500 text-xs">{cred.validFrom} — {cred.validTo ?? "∞"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}
