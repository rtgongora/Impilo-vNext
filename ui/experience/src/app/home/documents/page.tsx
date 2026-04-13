"use client";

/**
 * My Documents - absorbs self-service/my-documents sidecar
 * Personal document vault backed by the Experience document bridge.
 * Route: /home/documents | Guard: auth
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import { Download, ExternalLink, FileText, Loader2, Search, Shield, Share2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  usePersonalDocumentDownloadUrl,
  usePersonalDocuments,
  type PersonalDocumentResource,
} from "@/hooks/queries/usePersonalDocuments";

function formatDate(value: string): string {
  if (!value) return "Unknown date";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString();
}

function formatFileSize(bytes: number | null): string {
  if (!bytes || bytes <= 0) return "";
  const units = ["B", "KB", "MB", "GB"];
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const amount = bytes / 1024 ** exponent;
  return `${amount.toFixed(exponent === 0 ? 0 : 1)} ${units[exponent]}`;
}

function documentMatches(document: PersonalDocumentResource, query: string): boolean {
  if (!query) return true;
  const normalized = query.toLowerCase();
  return [
    document.title,
    document.originalFilename,
    document.documentTypeCode,
    document.mimeType,
    document.lifecycleState,
  ].some((value) => value.toLowerCase().includes(normalized));
}

export default function MyDocumentsPage() {
  const [query, setQuery] = useState("");
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [activeDownloadId, setActiveDownloadId] = useState<string | null>(null);
  const [downloadLinks, setDownloadLinks] = useState<Record<string, string>>({});

  const { data, isLoading, isError } = usePersonalDocuments();
  const downloadMutation = usePersonalDocumentDownloadUrl();

  const documents = data?.data ?? [];
  const filteredDocuments = useMemo(
    () => documents.filter((document) => documentMatches(document, query)),
    [documents, query],
  );
  const recentDocuments = documents.filter((document) => {
    if (!document.createdAt) return false;
    const createdAt = new Date(document.createdAt).getTime();
    return Number.isFinite(createdAt) && createdAt >= Date.now() - 1000 * 60 * 60 * 24 * 30;
  }).length;
  const downloadableDocuments = documents.filter((document) => document.id).length;

  async function handlePrepareDownload(document: PersonalDocumentResource) {
    if (!document.id) return;
    setActiveDownloadId(document.id);
    setDownloadError(null);

    try {
      const response = await downloadMutation.mutateAsync(document.id);
      const url = response.data.url;
      if (url) {
        setDownloadLinks((current) => ({ ...current, [document.id]: url }));
      } else {
        setDownloadError(`Download link is not available yet for ${document.title}.`);
      }
    } catch {
      setDownloadError(`We could not prepare a download for ${document.title}.`);
    } finally {
      setActiveDownloadId(null);
    }
  }

  return (
    <AppLayout>
      <PageShell
        title="My Documents"
        subtitle="Your personal document vault in Experience"
        icon={<FileText className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="rounded-xl border border-impilo-200 bg-impilo-50 p-4 text-sm text-impilo-800">
            <p className="font-semibold text-impilo-800">Canonical Experience document vault</p>
            <p className="mt-1">
              This page now reads your document vault through the Experience clinical-tools bridge. It focuses on retrieval
              and download access. Public claim flow still lives at{" "}
              <Link href="/share/claim" className="font-medium text-impilo-600 underline underline-offset-2">
                /share/claim
              </Link>
              .
            </p>
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <div className="rounded-lg border border-gray-200 bg-white p-5">
              <div className="mb-3 flex items-center gap-2">
                <FileText className="h-5 w-5 text-impilo-500" />
                <h3 className="font-semibold text-gray-900">Vault</h3>
              </div>
              <p className="text-2xl font-semibold text-gray-900">{documents.length}</p>
              <p className="mt-2 text-sm text-gray-600">Documents currently visible through the Experience bridge</p>
            </div>
            <div className="rounded-lg border border-gray-200 bg-white p-5">
              <div className="mb-3 flex items-center gap-2">
                <Download className="h-5 w-5 text-green-600" />
                <h3 className="font-semibold text-gray-900">Ready to download</h3>
              </div>
              <p className="text-2xl font-semibold text-gray-900">{downloadableDocuments}</p>
              <p className="mt-2 text-sm text-gray-600">Documents with a retrievable object identifier</p>
            </div>
            <div className="rounded-lg border border-gray-200 bg-white p-5">
              <div className="mb-3 flex items-center gap-2">
                <Share2 className="h-5 w-5 text-green-600" />
                <h3 className="font-semibold text-gray-900">Recent activity</h3>
              </div>
              <p className="text-2xl font-semibold text-gray-900">{recentDocuments}</p>
              <p className="mt-2 text-sm text-gray-600">Documents added or updated in the last 30 days</p>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-[minmax(0,1fr)_280px]">
            <div className="rounded-lg border border-gray-200 bg-white p-5">
              <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                <div>
                  <h2 className="text-base font-semibold text-gray-900">Document vault</h2>
                  <p className="text-sm text-gray-500">Search your current document view and prepare download links on demand.</p>
                </div>
                <label className="relative block md:w-80">
                  <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
                  <input
                    type="search"
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Search title, type, or mime type"
                    className="w-full rounded-lg border border-gray-300 py-2 pl-9 pr-3 text-sm focus:border-impilo-400 focus:outline-none focus:ring-2 focus:ring-impilo-400/20"
                  />
                </label>
              </div>

              {downloadError ? (
                <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
                  {downloadError}
                </div>
              ) : null}

              {isLoading ? (
                <div className="flex items-center justify-center py-16">
                  <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
                </div>
              ) : isError ? (
                <div className="rounded-lg border border-red-200 bg-red-50 p-5 text-sm text-red-800">
                  We could not load your document vault from the Experience bridge right now.
                </div>
              ) : filteredDocuments.length === 0 ? (
                <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-10 text-center text-sm text-gray-500">
                  <FileText className="mx-auto mb-3 h-8 w-8 text-gray-300" />
                  {documents.length === 0
                    ? "No documents are available yet in your Experience vault."
                    : "No documents match your current search."}
                </div>
              ) : (
                <div className="mt-5 space-y-3">
                  {filteredDocuments.map((document) => {
                    const downloadUrl = downloadLinks[document.id];
                    const isPreparing = activeDownloadId === document.id;

                    return (
                      <article
                        key={document.id || document.title}
                        className="rounded-lg border border-gray-200 p-4 transition-colors hover:border-gray-300"
                      >
                        <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                          <div className="min-w-0 flex-1">
                            <div className="flex flex-wrap items-center gap-2">
                              <h3 className="text-sm font-semibold text-gray-900">{document.title}</h3>
                              <span className="rounded-full bg-impilo-50 px-2.5 py-1 text-xs font-medium text-impilo-600">
                                {document.documentTypeCode}
                              </span>
                              <span className="rounded-full bg-gray-100 px-2.5 py-1 text-xs text-gray-600">
                                {document.lifecycleState}
                              </span>
                            </div>
                            <p className="mt-1 text-sm text-gray-600">{document.originalFilename}</p>
                            <div className="mt-2 flex flex-wrap gap-3 text-xs text-gray-500">
                              <span>{document.mimeType}</span>
                              <span>{formatDate(document.createdAt)}</span>
                              {document.fileSize ? <span>{formatFileSize(document.fileSize)}</span> : null}
                            </div>
                          </div>

                          <div className="flex flex-wrap items-center gap-2">
                            {downloadUrl ? (
                              <a
                                href={downloadUrl}
                                target="_blank"
                                rel="noreferrer"
                                className="inline-flex items-center gap-1.5 rounded-lg bg-impilo-500 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-impilo-600"
                              >
                                <Download className="h-4 w-4" />
                                Download document
                              </a>
                            ) : (
                              <button
                                type="button"
                                onClick={() => void handlePrepareDownload(document)}
                                disabled={!document.id || isPreparing}
                                className="inline-flex items-center gap-1.5 rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
                              >
                                {isPreparing ? (
                                  <>
                                    <Loader2 className="h-4 w-4 animate-spin" />
                                    Preparing...
                                  </>
                                ) : (
                                  <>
                                    <ExternalLink className="h-4 w-4" />
                                    Prepare download
                                  </>
                                )}
                              </button>
                            )}
                          </div>
                        </div>
                      </article>
                    );
                  })}
                </div>
              )}
            </div>

            <div className="space-y-4">
              <div className="rounded-lg border border-gray-200 bg-white p-5">
                <div className="mb-3 flex items-center gap-2">
                  <Share2 className="h-5 w-5 text-green-600" />
                  <h3 className="font-semibold text-gray-900">Shared documents</h3>
                </div>
                <p className="text-sm text-gray-600">
                  Claim time-limited shared bundles through the public Experience flow when someone sends you a share token.
                </p>
                <Link href="/share/claim" className="mt-3 inline-flex text-sm font-medium text-green-700 hover:underline">
                  Open claim flow
                </Link>
              </div>

              <div className="rounded-lg border border-gray-200 bg-white p-5">
                <div className="mb-3 flex items-center gap-2">
                  <Shield className="h-5 w-5 text-purple-600" />
                  <h3 className="font-semibold text-gray-900">Scope of this route</h3>
                </div>
                <p className="text-sm text-gray-600">
                  This route now replaces the self-service read and download vault. Upload, consent, and print-job workflows
                  still live in their own operational surfaces and are not simulated here.
                </p>
              </div>
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
