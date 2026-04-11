"use client";

/**
 * My Documents — absorbs self-service/my-documents sidecar
 * Personal document vault, uploaded records, share links.
 * Route: /home/documents | Guard: auth
 */

import { FileText, Upload, Share2, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function MyDocumentsPage() {
  return (
    <AppLayout>
      <PageShell title="My Documents" subtitle="Your personal health document vault" icon={<FileText className="h-6 w-6" />}>
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="rounded-lg border border-gray-200 bg-white p-5">
              <div className="flex items-center gap-2 mb-3">
                <Upload className="h-5 w-5 text-blue-600" />
                <h3 className="font-semibold text-gray-900">Upload</h3>
              </div>
              <p className="text-sm text-gray-600 mb-3">Upload scans, results, or records from external providers</p>
              <button className="text-sm text-blue-600 font-medium hover:underline">Upload document →</button>
            </div>
            <div className="rounded-lg border border-gray-200 bg-white p-5">
              <div className="flex items-center gap-2 mb-3">
                <Share2 className="h-5 w-5 text-green-600" />
                <h3 className="font-semibold text-gray-900">Share</h3>
              </div>
              <p className="text-sm text-gray-600 mb-3">Create time-limited share links for your documents</p>
              <button className="text-sm text-green-600 font-medium hover:underline">Manage share links →</button>
            </div>
            <div className="rounded-lg border border-gray-200 bg-white p-5">
              <div className="flex items-center gap-2 mb-3">
                <Shield className="h-5 w-5 text-purple-600" />
                <h3 className="font-semibold text-gray-900">Consent</h3>
              </div>
              <p className="text-sm text-gray-600 mb-3">Review who has access to your documents</p>
              <button className="text-sm text-purple-600 font-medium hover:underline">Review access →</button>
            </div>
          </div>
          <div className="rounded-lg border border-gray-200 bg-white p-6 text-center text-sm text-gray-500">
            <FileText className="h-8 w-8 text-gray-300 mx-auto mb-2" />
            <p>No documents uploaded yet. Upload your first document to get started.</p>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
