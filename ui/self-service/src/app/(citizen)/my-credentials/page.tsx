"use client";

import { useState, useEffect } from "react";
import { Card } from "shared-ui";

interface MyCredential {
  credentialId: string;
  credentialType: string;
  title: string;
  issuedBy: string;
  status: string;
  validFrom: string;
  validTo: string;
  verificationUrl: string;
}

export default function MyCredentialsPage() {
  const [credentials, setCredentials] = useState<MyCredential[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        const res = await fetch("/api/v1/credentials?scope=MY");
        const data = await res.json();
        setCredentials(data.data?.items ?? []);
      } catch {
        setCredentials([]);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  return (
    <div>
      <h1 className="text-2xl font-bold mb-2">My Credentials</h1>
      <p className="text-neutral-500 mb-6">View your certificates, licenses, and registration credentials.</p>

      {loading && <div className="text-center py-12 text-neutral-500">Loading...</div>}

      {!loading && credentials.length === 0 && (
        <Card>
          <div className="text-center py-12 text-neutral-500">No credentials found</div>
        </Card>
      )}

      {credentials.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2">
          {credentials.map((cred) => (
            <Card key={cred.credentialId} className="p-4">
              <div className="flex items-start justify-between mb-3">
                <div>
                  <p className="font-semibold text-sm">{cred.title}</p>
                  <p className="text-xs text-neutral-500">{cred.credentialType}</p>
                </div>
                <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                  cred.status === "ACTIVE" ? "bg-green-100 text-green-800" :
                  cred.status === "EXPIRED" ? "bg-yellow-100 text-yellow-800" :
                  "bg-red-100 text-red-800"
                }`}>{cred.status}</span>
              </div>
              <dl className="space-y-1 text-xs">
                <div className="flex justify-between">
                  <dt className="text-neutral-500">Issued By</dt><dd>{cred.issuedBy}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-neutral-500">Valid</dt><dd>{cred.validFrom} — {cred.validTo ?? "No expiry"}</dd>
                </div>
              </dl>
              <div className="flex gap-2 mt-3">
                <a href={`/api/v1/credentials/${cred.credentialId}/pdf`}
                  className="px-3 py-1.5 bg-brand-primary text-white rounded-lg text-xs font-medium hover:bg-brand-primary/90">
                  Download PDF
                </a>
                {cred.verificationUrl && (
                  <a href={cred.verificationUrl} target="_blank" rel="noopener noreferrer"
                    className="px-3 py-1.5 bg-neutral-200 text-neutral-700 rounded-lg text-xs font-medium hover:bg-neutral-300">
                    Verify
                  </a>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
