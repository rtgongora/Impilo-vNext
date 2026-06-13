"use client";

/**
 * Audit Entry Detail — Full audit entry with actor, resource, and changes.
 * Route: /admin/audit/[id] | pageTitle: "Audit Entry"
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Loader2, FileSearch, AlertCircle, User, Clock, Database } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuditEntry } from "@/hooks/queries/useAudit";

export default function AuditEntryDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const { data, isLoading, error } = useAuditEntry(id);
  const entry = data?.data;

  return (
    <AppLayout>
      <PageShell
        title="Audit Entry"
        subtitle={entry ? `Entry ${entry.id}` : "Loading entry..."}
      >
        <div className="mb-4">
          <Link
            href="/admin/audit"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to audit trail
          </Link>
        </div>

        {error ? (
          <div className="bg-card rounded-lg border border-danger/28 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load audit entry</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading audit entry...</span>
          </div>
        ) : !entry ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <FileSearch className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">Audit entry not found</p>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Summary Card */}
            <div className="bg-card rounded-lg border border-border p-6">
              <div className="flex items-center gap-3 mb-4">
                <span className="inline-block px-2.5 py-1 text-xs rounded-full bg-primary-soft text-primary font-medium">
                  {entry.attributes.action}
                </span>
                <span className="text-sm text-muted-foreground">
                  {new Date(entry.attributes.timestamp).toLocaleString()}
                </span>
              </div>
              <p className="text-sm text-muted-foreground font-mono">Entry ID: {entry.id}</p>
            </div>

            {/* Detail Grid */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {/* Actor */}
              <div className="bg-card rounded-lg border border-border p-5">
                <div className="flex items-center gap-2 mb-3">
                  <User className="w-4 h-4 text-impilo-400" />
                  <h3 className="text-sm font-medium text-foreground">Actor</h3>
                </div>
                <div className="space-y-2">
                  <div>
                    <p className="text-xs text-muted-foreground">Actor ID</p>
                    <p className="text-sm text-foreground font-mono">{entry.attributes.actorId}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">Actor Type</p>
                    <p className="text-sm text-foreground">{entry.attributes.actorType}</p>
                  </div>
                </div>
              </div>

              {/* Resource */}
              <div className="bg-card rounded-lg border border-border p-5">
                <div className="flex items-center gap-2 mb-3">
                  <Database className="w-4 h-4 text-green-500" />
                  <h3 className="text-sm font-medium text-foreground">Resource</h3>
                </div>
                <div className="space-y-2">
                  <div>
                    <p className="text-xs text-muted-foreground">Resource Type</p>
                    <p className="text-sm text-foreground">{entry.attributes.resourceType}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">Resource ID</p>
                    <p className="text-sm text-foreground font-mono">{entry.attributes.resourceId}</p>
                  </div>
                </div>
              </div>

              {/* Timestamp */}
              <div className="bg-card rounded-lg border border-border p-5">
                <div className="flex items-center gap-2 mb-3">
                  <Clock className="w-4 h-4 text-amber-500" />
                  <h3 className="text-sm font-medium text-foreground">Timing</h3>
                </div>
                <div className="space-y-2">
                  <div>
                    <p className="text-xs text-muted-foreground">Timestamp</p>
                    <p className="text-sm text-foreground">
                      {new Date(entry.attributes.timestamp).toLocaleString()}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">Action</p>
                    <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-primary-soft text-primary">
                      {entry.attributes.action}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            {/* Details / Changes */}
            <div className="bg-card rounded-lg border border-border">
              <div className="px-5 py-4 border-b">
                <h3 className="text-sm font-medium text-foreground">Details &amp; Changes</h3>
              </div>
              <div className="p-5">
                {Object.keys(entry.attributes.details).length === 0 ? (
                  <p className="text-muted-foreground text-sm">No additional details recorded</p>
                ) : (
                  <div className="space-y-3">
                    {Object.entries(entry.attributes.details).map(([key, value]) => (
                      <div key={key} className="flex items-start gap-4">
                        <span className="text-xs font-medium text-muted-foreground w-32 shrink-0 pt-0.5">
                          {key}
                        </span>
                        <pre className="text-xs text-foreground bg-background rounded px-3 py-2 flex-1 overflow-x-auto">
                          {typeof value === "object"
                            ? JSON.stringify(value, null, 2)
                            : String(value)}
                        </pre>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
