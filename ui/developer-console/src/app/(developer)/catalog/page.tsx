"use client";

import React, { useEffect, useState } from "react";
import {
  getDiscovery,
  getSchemaCatalog,
  getSchemaSubject,
} from "@/lib/developerApi";
import type {
  DiscoveryEndpoint,
  SchemaCatalogEntry,
} from "@/types/developer";

type TabId = "api" | "events" | "schemas";

export default function CatalogPage() {
  const [tab, setTab] = useState<TabId>("api");
  const [endpoints, setEndpoints] = useState<DiscoveryEndpoint[]>([]);
  const [schemas, setSchemas] = useState<SchemaCatalogEntry[]>([]);
  const [selectedSubject, setSelectedSubject] = useState<string | null>(null);
  const [subjectDetail, setSubjectDetail] = useState<{
    subject: string;
    compatibility: string;
    versions: { version: number; fingerprint: string; created_at: string }[];
  } | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function load() {
      try {
        const [discovery, catalog] = await Promise.all([
          getDiscovery(),
          getSchemaCatalog(),
        ]);
        setEndpoints(discovery.endpoints);
        setSchemas(catalog.items);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load catalog");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  async function handleViewSubject(subject: string) {
    setSelectedSubject(subject);
    try {
      const detail = await getSchemaSubject(subject);
      setSubjectDetail(detail);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load schema");
    }
  }

  const METHOD_COLORS: Record<string, string> = {
    GET: "text-info bg-info/10",
    POST: "text-success bg-success/10",
    PUT: "text-warning bg-warning/10",
    PATCH: "text-warning bg-warning/10",
    DELETE: "text-danger bg-danger/10",
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-sm text-neutral-500">Loading catalog...</div>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">API Catalog</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Discover APIs, event schemas, and integration contracts
        </p>
      </div>

      {error && (
        <div className="bg-danger/10 border border-danger/20 rounded-lg p-4 text-sm text-danger mb-6">
          {error}
        </div>
      )}

      {/* Tabs */}
      <div className="flex gap-1 mb-6 border-b border-neutral-200">
        {([
          { id: "api" as TabId, label: "API Endpoints" },
          { id: "events" as TabId, label: "Event Catalog" },
          { id: "schemas" as TabId, label: "Schema Registry" },
        ]).map(({ id, label }) => (
          <button
            key={id}
            onClick={() => setTab(id)}
            className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors -mb-px ${
              tab === id
                ? "border-brand-primary text-brand-primary"
                : "border-transparent text-neutral-500 hover:text-neutral-700"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {/* API Endpoints */}
      {tab === "api" && (
        <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-6">
          <div className="space-y-2">
            {endpoints.map((ep, i) => (
              <div key={i} className="flex items-center gap-3 py-2.5 px-3 rounded-lg hover:bg-neutral-50 transition-colors">
                <span className={`inline-flex px-2 py-0.5 rounded text-xs font-mono font-semibold ${METHOD_COLORS[ep.method] ?? "text-neutral-600 bg-neutral-100"}`}>
                  {ep.method}
                </span>
                <code className="text-sm font-mono text-neutral-700 flex-1">{ep.path}</code>
                <span className="text-xs text-neutral-500">{ep.description}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Event Catalog */}
      {tab === "events" && (
        <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-6">
          {schemas.length === 0 ? (
            <p className="text-sm text-neutral-500 py-4 text-center">No event schemas registered yet</p>
          ) : (
            <div className="space-y-3">
              {schemas.map((s, i) => (
                <div key={i} className="border border-neutral-100 rounded-lg p-4">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <code className="text-sm font-mono text-brand-primary font-medium">{s.subject}</code>
                      <span className="text-xs px-1.5 py-0.5 rounded bg-info/10 text-info font-medium">
                        v{s.latest_version ?? 1}
                      </span>
                    </div>
                    <span className="text-xs text-neutral-500">{s.compatibility}</span>
                  </div>
                  {s.description && <p className="text-xs text-neutral-600 mb-2">{s.description}</p>}
                  <div className="flex gap-4 text-xs text-neutral-500">
                    {s.service && <span>Service: <span className="text-neutral-700">{s.service}</span></span>}
                    {s.entity && <span>Entity: <span className="text-neutral-700">{s.entity}</span></span>}
                    {s.action && <span>Action: <span className="text-neutral-700">{s.action}</span></span>}
                    <span>Versions: <span className="text-neutral-700">{s.version_count}</span></span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Schema Registry */}
      {tab === "schemas" && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-6">
            <h3 className="text-sm font-semibold text-neutral-900 mb-3">Subjects</h3>
            {schemas.length === 0 ? (
              <p className="text-sm text-neutral-500 py-4 text-center">No schemas registered</p>
            ) : (
              <div className="space-y-1">
                {schemas.map((s, i) => (
                  <button
                    key={i}
                    onClick={() => handleViewSubject(s.subject)}
                    className={`w-full text-left px-3 py-2 rounded-lg text-sm transition-colors ${
                      selectedSubject === s.subject
                        ? "bg-brand-primary/10 text-brand-primary"
                        : "text-neutral-700 hover:bg-neutral-50"
                    }`}
                  >
                    <code className="text-xs font-mono">{s.subject}</code>
                    <span className="text-xs text-neutral-400 ml-2">({s.version_count} versions)</span>
                  </button>
                ))}
              </div>
            )}
          </div>
          <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-6">
            <h3 className="text-sm font-semibold text-neutral-900 mb-3">Version History</h3>
            {!subjectDetail ? (
              <p className="text-sm text-neutral-500 py-4 text-center">Select a subject to view versions</p>
            ) : (
              <div>
                <div className="mb-3">
                  <code className="text-sm font-mono text-brand-primary">{subjectDetail.subject}</code>
                  <span className="text-xs text-neutral-500 ml-2">({subjectDetail.compatibility})</span>
                </div>
                <div className="space-y-2">
                  {subjectDetail.versions.map((v) => (
                    <div key={v.version} className="flex items-center justify-between py-2 px-3 rounded-lg bg-neutral-50">
                      <div className="flex items-center gap-2">
                        <span className="text-xs px-1.5 py-0.5 rounded bg-info/10 text-info font-medium">
                          v{v.version}
                        </span>
                        <code className="text-xs font-mono text-neutral-500">{v.fingerprint.slice(0, 12)}...</code>
                      </div>
                      <span className="text-xs text-neutral-500">
                        {new Date(v.created_at).toLocaleDateString()}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
