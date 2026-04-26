"use client";

/**
 * Access Channels — Communication, Landela (document services), and Kiosk.
 * Route: /access
 *
 * Deep links: {@code ?tab=landela&documentId=<uuid>} opens Landela tab and loads document metadata.
 */

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { MessageSquare, FileText, Monitor, Loader2, Search, Send, ExternalLink } from "lucide-react";
import { useQuery, useMutation } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { isLikelyLandelaDocumentUuid, registryIntakeLandelaPrefillHref } from "@/lib/landela-routes";

type Tab = "communication" | "landela" | "kiosk";

export default function AccessPage() {
  return (
    <AppLayout>
      <Suspense
        fallback={
          <PageShell title="Access Channels" icon={<MessageSquare className="w-5 h-5" />}>
            <div className="flex items-center gap-2 text-sm text-gray-500">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading…
            </div>
          </PageShell>
        }
      >
        <AccessPageContent />
      </Suspense>
    </AppLayout>
  );
}

function AccessPageContent() {
  const searchParams = useSearchParams();
  const [tab, setTab] = useState<Tab>("communication");

  useEffect(() => {
    const t = searchParams.get("tab");
    if (t === "landela" || t === "communication" || t === "kiosk") {
      setTab(t);
    }
  }, [searchParams]);

  const focusedDocumentId = searchParams.get("documentId")?.trim() ?? "";

  const tabs: { id: Tab; label: string; icon: typeof MessageSquare }[] = [
    { id: "communication", label: "Communication", icon: MessageSquare },
    { id: "landela", label: "Landela Documents", icon: FileText },
    { id: "kiosk", label: "Kiosk", icon: Monitor },
  ];

  return (
    <PageShell title="Access Channels" icon={<MessageSquare className="w-5 h-5" />}>
      <p className="text-sm text-gray-500 mb-4">
        Communication, document services, and self-service kiosk management
      </p>

      <div className="flex gap-1 mb-6 border-b border-gray-200">
        {tabs.map((t) => {
          const Icon = t.icon;
          return (
            <button
              key={t.id}
              type="button"
              onClick={() => setTab(t.id)}
              className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                tab === t.id
                  ? "border-impilo-500 text-impilo-500"
                  : "border-transparent text-gray-500 hover:text-gray-700"
              }`}
            >
              <Icon className="w-4 h-4" /> {t.label}
            </button>
          );
        })}
      </div>

      {tab === "communication" && <CommunicationTab />}
      {tab === "landela" && <LandelaTab focusedDocumentId={focusedDocumentId} />}
      {tab === "kiosk" && <KioskTab />}
    </PageShell>
  );
}

function CommunicationTab() {
  const sendMutation = useMutation({
    mutationFn: (body: Record<string, string>) => apiClient.post("/internal/v1/access/notifications/send", body),
  });
  const { data, isLoading } = useQuery<{ data: unknown[] }>({
    queryKey: ["access-notifications"],
    queryFn: () => apiClient.get("/internal/v1/access/notifications/recent"),
  });

  const [form, setForm] = useState({ channel: "SMS", recipientId: "", message: "" });

  return (
    <div className="space-y-4">
      <h3 className="text-lg font-semibold">Send Notification</h3>
      <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <select
            value={form.channel}
            onChange={(e) => setForm({ ...form, channel: e.target.value })}
            className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
          >
            <option value="SMS">SMS</option>
            <option value="EMAIL">Email</option>
            <option value="PUSH">Push</option>
            <option value="WHATSAPP">WhatsApp</option>
          </select>
          <input
            type="text"
            placeholder="Recipient ID"
            value={form.recipientId}
            onChange={(e) => setForm({ ...form, recipientId: e.target.value })}
            className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <textarea
          placeholder="Message content"
          value={form.message}
          onChange={(e) => setForm({ ...form, message: e.target.value })}
          rows={3}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
        />
        <button
          type="button"
          onClick={() => sendMutation.mutate(form)}
          disabled={!form.recipientId || !form.message || sendMutation.isPending}
          className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-impilo-500 text-white rounded-lg disabled:opacity-50"
        >
          <Send className="w-4 h-4" /> {sendMutation.isPending ? "Sending..." : "Send"}
        </button>
        {sendMutation.isSuccess && <p className="text-sm text-green-600">Notification sent</p>}
      </div>
      <h3 className="text-lg font-semibold">Recent Notifications</h3>
      {isLoading ? (
        <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
      ) : (
        <p className="text-sm text-gray-400">{(data?.data ?? []).length} recent notifications</p>
      )}
    </div>
  );
}

function unwrapLandelaMetadata(res: { data?: unknown }): unknown {
  const outer = res?.data;
  if (outer && typeof outer === "object" && "data" in outer) {
    return (outer as { data: unknown }).data;
  }
  return outer;
}

function LandelaTab({ focusedDocumentId }: { focusedDocumentId: string }) {
  const [searchQuery, setSearchQuery] = useState("");
  const { data: templates, isLoading: tLoading } = useQuery<{ data: unknown[] }>({
    queryKey: ["landela-templates"],
    queryFn: () => apiClient.get("/internal/v1/access/landela/templates"),
  });
  const { data: docs, isLoading: dLoading } = useQuery<{ data: unknown[] }>({
    queryKey: ["landela-docs", searchQuery],
    queryFn: () =>
      apiClient.get(`/internal/v1/access/landela/documents/search${searchQuery ? "?query=" + searchQuery : ""}`),
  });

  const docIdForFetch = focusedDocumentId && isLikelyLandelaDocumentUuid(focusedDocumentId) ? focusedDocumentId : "";
  const { data: docMeta, isLoading: metaLoading, isError: metaError } = useQuery({
    queryKey: ["landela-document-metadata", docIdForFetch],
    queryFn: () => apiClient.get(`/internal/v1/access/landela/documents/${docIdForFetch}`),
    enabled: Boolean(docIdForFetch),
  });

  return (
    <div className="space-y-4">
      {docIdForFetch ? (
        <div className="rounded-lg border border-violet-200 bg-violet-50/80 p-4">
          <h3 className="text-sm font-semibold text-violet-900">Focused document</h3>
          <p className="mt-1 font-mono text-xs text-violet-800">{docIdForFetch}</p>
          {metaLoading ? (
            <div className="mt-3 flex items-center gap-2 text-xs text-violet-800">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading metadata…
            </div>
          ) : metaError ? (
            <p className="mt-2 text-xs text-red-700">
              Could not load metadata (confirm Landela is up and trust headers are accepted).
            </p>
          ) : (
            <pre className="mt-3 max-h-56 overflow-auto rounded-md bg-white/90 p-3 text-[11px] text-slate-800">
              {JSON.stringify(unwrapLandelaMetadata(docMeta as { data?: unknown }), null, 2)}
            </pre>
          )}
          <div className="mt-3 flex flex-wrap gap-3 text-xs">
            <Link
              href={registryIntakeLandelaPrefillHref(docIdForFetch)}
              className="inline-flex items-center gap-1 font-medium text-violet-800 hover:underline"
            >
              <ExternalLink className="h-3 w-3" />
              Use for registry CSV import
            </Link>
            <span className="text-violet-400">|</span>
            <Link href="/registry/intake" className="text-violet-700 hover:underline">
              Registry intake hub
            </Link>
          </div>
        </div>
      ) : (
        <p className="text-xs text-gray-500">
          Tip: open a document from{" "}
          <Link href="/registry/intake" className="text-impilo-600 hover:underline">
            Registry intake
          </Link>{" "}
          using &quot;Open in Landela workspace&quot;, or append{" "}
          <code className="rounded bg-gray-100 px-1">?tab=landela&amp;documentId=&lt;uuid&gt;</code> to this page URL.
        </p>
      )}

      <h3 className="text-lg font-semibold">Document Templates</h3>
      {tLoading ? (
        <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
      ) : (
        <p className="text-sm text-gray-500">{(templates?.data ?? []).length} templates available</p>
      )}
      <h3 className="text-lg font-semibold">Search Documents</h3>
      <div className="flex gap-2">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-2.5 w-4 h-4 text-gray-400" />
          <input
            type="text"
            placeholder="Search documents..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
      </div>
      {dLoading ? (
        <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
      ) : (
        <p className="text-sm text-gray-500">{(docs?.data ?? []).length} documents found</p>
      )}
    </div>
  );
}

function KioskTab() {
  return (
    <div className="space-y-4">
      <h3 className="text-lg font-semibold">Self-Service Kiosk</h3>
      <div className="bg-white rounded-lg border border-gray-200 p-6 text-center">
        <Monitor className="w-12 h-12 text-gray-300 mx-auto mb-3" />
        <p className="text-sm text-gray-600">
          Kiosk check-in is available at <strong>/kiosk</strong>
        </p>
        <p className="text-xs text-gray-400 mt-1">Patients can self-check-in, verify identity, and join queues</p>
        <Link
          href="/kiosk"
          className="mt-4 inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-impilo-500 text-white rounded-lg hover:bg-impilo-600"
        >
          Open Kiosk Mode
        </Link>
      </div>
    </div>
  );
}
