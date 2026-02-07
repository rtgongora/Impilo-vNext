"use client";

import { useState, useEffect } from "react";
import { Card } from "shared-ui";

interface ShareLink {
  linkId: string;
  subjectType: string;
  subjectId: string;
  purpose: string;
  status: string;
  claimCount: number;
  maxClaims: number;
  expiresAt: string;
  createdAt: string;
}

export default function ShareLinksPage() {
  const [links, setLinks] = useState<ShareLink[]>([]);
  const [loading, setLoading] = useState(false);

  async function loadLinks() {
    setLoading(true);
    try {
      const res = await fetch("/api/v1/share-links");
      const data = await res.json();
      setLinks(data.data?.items ?? []);
    } catch {
      setLinks([]);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { loadLinks(); }, []);

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Share Links</h1>

      <Card>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-neutral-200">
                <th className="text-left p-3 font-medium text-neutral-500">Link ID</th>
                <th className="text-left p-3 font-medium text-neutral-500">Subject</th>
                <th className="text-left p-3 font-medium text-neutral-500">Purpose</th>
                <th className="text-left p-3 font-medium text-neutral-500">Claims</th>
                <th className="text-left p-3 font-medium text-neutral-500">Status</th>
                <th className="text-left p-3 font-medium text-neutral-500">Expires</th>
              </tr>
            </thead>
            <tbody>
              {links.map((link) => (
                <tr key={link.linkId} className="border-b border-neutral-100 hover:bg-neutral-50">
                  <td className="p-3 font-mono text-xs">{link.linkId.slice(0, 8)}...</td>
                  <td className="p-3">{link.subjectType}/{link.subjectId.slice(0, 8)}</td>
                  <td className="p-3">{link.purpose}</td>
                  <td className="p-3">{link.claimCount}/{link.maxClaims}</td>
                  <td className="p-3">
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                      link.status === "ACTIVE" ? "bg-green-100 text-green-800" :
                      link.status === "CLAIMED" ? "bg-blue-100 text-blue-800" :
                      link.status === "EXPIRED" ? "bg-yellow-100 text-yellow-800" :
                      "bg-red-100 text-red-800"
                    }`}>{link.status}</span>
                  </td>
                  <td className="p-3 text-neutral-500 text-xs">{new Date(link.expiresAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {links.length === 0 && !loading && (
          <div className="text-center py-8 text-neutral-500 text-sm">No share links found</div>
        )}
      </Card>
    </div>
  );
}
