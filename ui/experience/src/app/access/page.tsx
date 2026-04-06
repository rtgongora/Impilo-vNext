"use client";

/**
 * Access Channels — Communication, Landela (document services), and Kiosk.
 * Route: /access
 */

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { MessageSquare, FileText, Monitor, Loader2, Search, Send } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery, useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type Tab = "communication" | "landela" | "kiosk";

export default function AccessPage() {
  const searchParams = useSearchParams();
  const initialTab = (searchParams.get("tab") as Tab) ?? "communication";
  const [tab, setTab] = useState<Tab>(initialTab);

  const tabs: { id: Tab; label: string; icon: typeof MessageSquare }[] = [
    { id: "communication", label: "Communication", icon: MessageSquare },
    { id: "landela", label: "Landela Documents", icon: FileText },
    { id: "kiosk", label: "Kiosk", icon: Monitor },
  ];

  return (
    <AppLayout>
      <PageShell title="Access Channels" icon={<MessageSquare className="w-5 h-5" />}>
        <p className="text-sm text-gray-500 mb-4">Communication, document services, and self-service kiosk management</p>

        <div className="flex gap-1 mb-6 border-b border-gray-200">
          {tabs.map((t) => {
            const Icon = t.icon;
            return (
              <button key={t.id} onClick={() => setTab(t.id)}
                className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                  tab === t.id ? "border-blue-600 text-blue-600" : "border-transparent text-gray-500 hover:text-gray-700"
                }`}>
                <Icon className="w-4 h-4" /> {t.label}
              </button>
            );
          })}
        </div>

        {tab === "communication" && <CommunicationTab />}
        {tab === "landela" && <LandelaTab />}
        {tab === "kiosk" && <KioskTab />}
      </PageShell>
    </AppLayout>
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
          <select value={form.channel} onChange={(e) => setForm({ ...form, channel: e.target.value })} className="px-3 py-2 border border-gray-300 rounded-lg text-sm">
            <option value="SMS">SMS</option><option value="EMAIL">Email</option><option value="PUSH">Push</option><option value="WHATSAPP">WhatsApp</option>
          </select>
          <input type="text" placeholder="Recipient ID" value={form.recipientId} onChange={(e) => setForm({ ...form, recipientId: e.target.value })} className="px-3 py-2 border border-gray-300 rounded-lg text-sm" />
        </div>
        <textarea placeholder="Message content" value={form.message} onChange={(e) => setForm({ ...form, message: e.target.value })} rows={3} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
        <button onClick={() => sendMutation.mutate(form)} disabled={!form.recipientId || !form.message || sendMutation.isPending}
          className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg disabled:opacity-50">
          <Send className="w-4 h-4" /> {sendMutation.isPending ? "Sending..." : "Send"}
        </button>
        {sendMutation.isSuccess && <p className="text-sm text-green-600">Notification sent</p>}
      </div>
      <h3 className="text-lg font-semibold">Recent Notifications</h3>
      {isLoading ? <Loader2 className="w-5 h-5 animate-spin text-gray-400" /> : (
        <p className="text-sm text-gray-400">{(data?.data ?? []).length} recent notifications</p>
      )}
    </div>
  );
}

function LandelaTab() {
  const [searchQuery, setSearchQuery] = useState("");
  const { data: templates, isLoading: tLoading } = useQuery<{ data: unknown[] }>({
    queryKey: ["landela-templates"],
    queryFn: () => apiClient.get("/internal/v1/access/landela/templates"),
  });
  const { data: docs, isLoading: dLoading } = useQuery<{ data: unknown[] }>({
    queryKey: ["landela-docs", searchQuery],
    queryFn: () => apiClient.get(`/internal/v1/access/landela/documents/search${searchQuery ? "?query=" + searchQuery : ""}`),
  });

  return (
    <div className="space-y-4">
      <h3 className="text-lg font-semibold">Document Templates</h3>
      {tLoading ? <Loader2 className="w-5 h-5 animate-spin text-gray-400" /> : (
        <p className="text-sm text-gray-500">{(templates?.data ?? []).length} templates available</p>
      )}
      <h3 className="text-lg font-semibold">Search Documents</h3>
      <div className="flex gap-2">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-2.5 w-4 h-4 text-gray-400" />
          <input type="text" placeholder="Search documents..." value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-3 py-2 border border-gray-300 rounded-lg text-sm" />
        </div>
      </div>
      {dLoading ? <Loader2 className="w-5 h-5 animate-spin text-gray-400" /> : (
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
        <p className="text-sm text-gray-600">Kiosk check-in is available at <strong>/kiosk</strong></p>
        <p className="text-xs text-gray-400 mt-1">Patients can self-check-in, verify identity, and join queues</p>
        <a href="/kiosk" className="mt-4 inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700">
          Open Kiosk Mode
        </a>
      </div>
    </div>
  );
}
