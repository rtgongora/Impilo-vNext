"use client";

import { useState, useEffect } from "react";
import { Card } from "shared-ui";

interface MyDocument {
  documentId: string;
  documentTypeCode: string;
  originalFilename: string;
  mimeType: string;
  lifecycleState: string;
  createdAt: string;
}

export default function MyDocumentsPage() {
  const [documents, setDocuments] = useState<MyDocument[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        const res = await fetch("/api/v1/documents?scope=MY");
        const data = await res.json();
        setDocuments(data.data?.items ?? []);
      } catch {
        setDocuments([]);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  return (
    <div>
      <h1 className="text-2xl font-bold mb-2">My Documents</h1>
      <p className="text-neutral-500 mb-6">View and download your health-related documents.</p>

      {loading && <div className="text-center py-12 text-neutral-500">Loading...</div>}

      {!loading && documents.length === 0 && (
        <Card>
          <div className="text-center py-12 text-neutral-500">No documents found</div>
        </Card>
      )}

      {documents.length > 0 && (
        <div className="space-y-3">
          {documents.map((doc) => (
            <Card key={doc.documentId} className="p-4 flex items-center justify-between">
              <div>
                <p className="font-medium text-sm">{doc.originalFilename}</p>
                <p className="text-xs text-neutral-500">{doc.documentTypeCode} &middot; {doc.mimeType}</p>
                <p className="text-xs text-neutral-400 mt-1">{new Date(doc.createdAt).toLocaleDateString()}</p>
              </div>
              <div className="flex items-center gap-2">
                <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                  doc.lifecycleState === "ACTIVE" ? "bg-green-100 text-green-800" : "bg-neutral-100 text-neutral-600"
                }`}>{doc.lifecycleState}</span>
                <a href={`/api/v1/documents/${doc.documentId}/download`}
                  className="px-3 py-1.5 bg-brand-primary text-white rounded-lg text-xs font-medium hover:bg-brand-primary/90">
                  Download
                </a>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
