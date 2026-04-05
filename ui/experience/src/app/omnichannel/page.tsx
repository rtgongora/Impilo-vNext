"use client";

/**
 * Omnichannel Hub — All access channels in one view.
 * 7 tabs: Overview, SMS, USSD, IVR, Callbacks, Disclosure, AI Agent.
 * Backed by OmnichannelController + existing messaging infrastructure.
 */

import { useState } from "react";
import {
  Radio, MessageSquare, Phone, PhoneCall, Headphones,
  Shield, Bot, Loader2, Plus, CheckCircle2, AlertCircle,
  Clock, Users,
} from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

type ActiveTab = "overview" | "sms" | "ussd" | "ivr" | "callbacks" | "disclosure" | "ai-agent";

const TABS: { key: ActiveTab; label: string; icon: typeof Radio }[] = [
  { key: "overview", label: "Overview", icon: Radio },
  { key: "sms", label: "SMS Journeys", icon: MessageSquare },
  { key: "ussd", label: "USSD Menus", icon: Phone },
  { key: "ivr", label: "IVR / Voice", icon: PhoneCall },
  { key: "callbacks", label: "Callbacks", icon: Headphones },
  { key: "disclosure", label: "Disclosure", icon: Shield },
  { key: "ai-agent", label: "AI Agent", icon: Bot },
];

export default function OmnichannelPage() {
  const [activeTab, setActiveTab] = useState<ActiveTab>("overview");

  return (
    <AppLayout>
      <PageShell title="Omnichannel Hub" subtitle="SMS, USSD, IVR, callbacks, and AI-powered access channels">
        <div className="flex gap-1 mb-6 border-b border-gray-200 overflow-x-auto">
          {TABS.map((tab) => {
            const Icon = tab.icon;
            return (
              <button key={tab.key} onClick={() => setActiveTab(tab.key)}
                className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                  activeTab === tab.key ? "border-teal-600 text-teal-600" : "border-transparent text-gray-500 hover:text-gray-700"
                }`}>
                <Icon className="w-4 h-4" /> {tab.label}
              </button>
            );
          })}
        </div>

        {activeTab === "overview" && <OverviewTab />}
        {activeTab === "sms" && <SmsTab />}
        {activeTab === "ussd" && <UssdTab />}
        {activeTab === "ivr" && <IvrTab />}
        {activeTab === "callbacks" && <CallbacksTab />}
        {activeTab === "disclosure" && <DisclosureTab />}
        {activeTab === "ai-agent" && <AiAgentTab />}
      </PageShell>
    </AppLayout>
  );
}

function OverviewTab() {
  const { data: callbacksData } = useQuery<{ data: unknown[] }>({
    queryKey: ["omni-callbacks"], queryFn: () => apiClient.get("/internal/v1/omnichannel/callbacks"),
  });
  const { data: smsData } = useQuery<{ data: unknown[] }>({
    queryKey: ["omni-sms"], queryFn: () => apiClient.get("/internal/v1/omnichannel/sms-journeys"),
  });
  const callbacks = (callbacksData?.data ?? []) as Array<Record<string, unknown>>;
  const journeys = (smsData?.data ?? []) as Array<Record<string, unknown>>;
  const pending = callbacks.filter((c) => c.status === "PENDING").length;

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-4 gap-4">
        {([["SMS Journeys", journeys.length, "teal"], ["Pending Callbacks", pending, "amber"], ["Active Channels", "4", "blue"], ["AI Conversations", "—", "purple"]] as const).map(([label, val, color]) => (
          <div key={label} className={`bg-${color}-50 rounded-lg border border-${color}-200 p-4 text-center`}>
            <p className={`text-2xl font-bold text-${color}-700`}>{val}</p>
            <p className={`text-xs text-${color}-600`}>{label}</p>
          </div>
        ))}
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <h3 className="text-sm font-semibold text-gray-900 mb-3">Channel Status</h3>
          {(["SMS", "USSD", "IVR", "WhatsApp", "Email", "Webchat"] as const).map((ch) => (
            <div key={ch} className="flex items-center justify-between py-1.5 border-b border-gray-100 last:border-0">
              <span className="text-sm text-gray-700">{ch}</span>
              <span className={`px-2 py-0.5 text-xs rounded-full ${ch === "SMS" ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>
                {ch === "SMS" ? "Active" : "Configured"}
              </span>
            </div>
          ))}
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <h3 className="text-sm font-semibold text-gray-900 mb-3">Recent Callbacks</h3>
          {callbacks.length === 0 ? (
            <p className="text-sm text-gray-400">No callbacks in queue</p>
          ) : (
            callbacks.slice(0, 5).map((cb, i) => (
              <div key={i} className="flex items-center justify-between py-1.5 border-b border-gray-100 last:border-0">
                <span className="text-sm text-gray-700">{String(cb.caller_name ?? cb.caller_id)}</span>
                <span className={`px-2 py-0.5 text-xs rounded-full ${cb.status === "PENDING" ? "bg-amber-100 text-amber-700" : "bg-green-100 text-green-700"}`}>
                  {String(cb.status)}
                </span>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

function SmsTab() {
  const { data, isLoading } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["omni-sms"], queryFn: () => apiClient.get("/internal/v1/omnichannel/sms-journeys"),
  });
  const [showForm, setShowForm] = useState(false);
  const queryClient = useQueryClient();
  const create = useMutation({
    mutationFn: (body: Record<string, string>) => apiClient.post("/internal/v1/omnichannel/sms-journeys", body),
    onSuccess: () => { setShowForm(false); queryClient.invalidateQueries({ queryKey: ["omni-sms"] }); },
  });
  const journeys = data?.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-gray-900">SMS Journeys</h3>
        <button onClick={() => setShowForm(!showForm)} className="inline-flex items-center gap-1.5 px-3 py-2 bg-teal-600 text-white text-sm font-medium rounded-lg hover:bg-teal-700">
          <Plus className="w-4 h-4" /> New Journey
        </button>
      </div>
      {showForm && (
        <div className="bg-white rounded-lg border border-teal-200 p-5 space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <input type="text" placeholder="Journey Name" id="sms-name" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" />
            <input type="text" placeholder="Trigger Event (e.g., APPOINTMENT_REMINDER)" id="sms-trigger" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          </div>
          <textarea placeholder="Message template (use {{name}}, {{date}}, {{facility}})" id="sms-template" rows={3} className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          <input type="text" placeholder="Schedule cron (e.g., 0 8 * * *)" id="sms-cron" className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          <div className="flex gap-2">
            <button onClick={() => setShowForm(false)} className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm rounded-lg">Cancel</button>
            <button onClick={() => create.mutate({
              name: (document.getElementById("sms-name") as HTMLInputElement)?.value,
              triggerEvent: (document.getElementById("sms-trigger") as HTMLInputElement)?.value,
              messageTemplate: (document.getElementById("sms-template") as HTMLTextAreaElement)?.value,
              scheduleCron: (document.getElementById("sms-cron") as HTMLInputElement)?.value,
            })} disabled={create.isPending} className="flex-1 py-2 bg-teal-600 text-white text-sm rounded-lg hover:bg-teal-700 disabled:opacity-50">
              {create.isPending ? "Creating..." : "Create Journey"}
            </button>
          </div>
        </div>
      )}
      {isLoading ? <Loader2 className="w-6 h-6 animate-spin text-gray-400 mx-auto" /> : journeys.length === 0 ? (
        <div className="bg-white rounded-lg border p-12 text-center"><MessageSquare className="w-10 h-10 text-gray-300 mx-auto mb-3" /><p className="text-gray-400 text-sm">No SMS journeys configured</p></div>
      ) : (
        <div className="space-y-3">
          {journeys.map((j, i) => (
            <div key={i} className="bg-white rounded-lg border border-gray-200 p-4 flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-900">{String(j.name)}</p>
                <p className="text-xs text-gray-500">Trigger: {String(j.trigger_event)} · Sent: {Number(j.sent_count ?? 0)}</p>
              </div>
              <span className={`px-2 py-0.5 text-xs rounded-full ${j.is_active ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>{j.is_active ? "Active" : "Inactive"}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function UssdTab() {
  const { data } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["omni-ussd"], queryFn: () => apiClient.get("/internal/v1/omnichannel/ussd-menus"),
  });
  return (
    <div className="space-y-4">
      <h3 className="text-base font-semibold text-gray-900">USSD Menu Definitions</h3>
      <p className="text-sm text-gray-500">Feature-phone access — no data connection needed. Define menu trees for USSD short codes.</p>
      <div className="bg-white rounded-lg border p-5">
        <p className="text-sm text-gray-500 mb-3">USSD flow example:</p>
        <div className="bg-gray-50 rounded-lg p-4 font-mono text-xs space-y-1">
          <p>*123# → Welcome to Impilo Health</p>
          <p>1. Check appointment</p>
          <p>2. Request refill</p>
          <p>3. Find nearest clinic</p>
          <p>4. Emergency helpline</p>
        </div>
      </div>
      {(data?.data ?? []).length === 0 && (
        <div className="bg-white rounded-lg border p-12 text-center"><Phone className="w-10 h-10 text-gray-300 mx-auto mb-3" /><p className="text-gray-400 text-sm">No USSD menus configured</p></div>
      )}
    </div>
  );
}

function IvrTab() {
  const { data } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["omni-ivr"], queryFn: () => apiClient.get("/internal/v1/omnichannel/ivr-flows"),
  });
  return (
    <div className="space-y-4">
      <h3 className="text-base font-semibold text-gray-900">IVR / Voice Flows</h3>
      <p className="text-sm text-gray-500">Voice-guided access for patients who prefer phone interaction or have limited literacy.</p>
      <div className="bg-white rounded-lg border p-5">
        <p className="text-sm text-gray-500 mb-3">IVR flow example:</p>
        <div className="bg-gray-50 rounded-lg p-4 text-xs space-y-1">
          <p className="font-medium">Welcome prompt → Language selection → Main menu</p>
          <p>Press 1: Appointment status</p>
          <p>Press 2: Medication refill</p>
          <p>Press 3: Speak to a health worker</p>
          <p>Press 0: Emergency</p>
        </div>
      </div>
      {(data?.data ?? []).length === 0 && (
        <div className="bg-white rounded-lg border p-12 text-center"><PhoneCall className="w-10 h-10 text-gray-300 mx-auto mb-3" /><p className="text-gray-400 text-sm">No IVR flows configured</p></div>
      )}
    </div>
  );
}

function CallbacksTab() {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["omni-callbacks"], queryFn: () => apiClient.get("/internal/v1/omnichannel/callbacks"),
  });
  const [showForm, setShowForm] = useState(false);
  const create = useMutation({
    mutationFn: (body: Record<string, string>) => apiClient.post("/internal/v1/omnichannel/callbacks", body),
    onSuccess: () => { setShowForm(false); queryClient.invalidateQueries({ queryKey: ["omni-callbacks"] }); },
  });
  const complete = useMutation({
    mutationFn: (id: string) => apiClient.post(`/internal/v1/omnichannel/callbacks/${id}/complete`, {}),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["omni-callbacks"] }),
  });
  const callbacks = data?.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-gray-900">Callback Queue</h3>
        <button onClick={() => setShowForm(!showForm)} className="inline-flex items-center gap-1.5 px-3 py-2 bg-amber-600 text-white text-sm font-medium rounded-lg hover:bg-amber-700">
          <Plus className="w-4 h-4" /> Add Callback
        </button>
      </div>
      {showForm && (
        <div className="bg-white rounded-lg border border-amber-200 p-5 space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <input type="text" placeholder="Caller ID / Phone" id="cb-caller" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" />
            <input type="text" placeholder="Caller Name" id="cb-name" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          </div>
          <textarea placeholder="Reason for callback..." id="cb-reason" rows={2} className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          <select id="cb-priority" className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg">
            <option value="NORMAL">Normal Priority</option><option value="HIGH">High</option><option value="URGENT">Urgent</option>
          </select>
          <div className="flex gap-2">
            <button onClick={() => setShowForm(false)} className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm rounded-lg">Cancel</button>
            <button onClick={() => create.mutate({
              callerId: (document.getElementById("cb-caller") as HTMLInputElement)?.value,
              callerName: (document.getElementById("cb-name") as HTMLInputElement)?.value,
              reason: (document.getElementById("cb-reason") as HTMLTextAreaElement)?.value,
              priority: (document.getElementById("cb-priority") as HTMLSelectElement)?.value,
            })} disabled={create.isPending} className="flex-1 py-2 bg-amber-600 text-white text-sm rounded-lg hover:bg-amber-700 disabled:opacity-50">
              {create.isPending ? "Adding..." : "Add to Queue"}
            </button>
          </div>
        </div>
      )}
      {isLoading ? <Loader2 className="w-6 h-6 animate-spin text-gray-400 mx-auto" /> : callbacks.length === 0 ? (
        <div className="bg-white rounded-lg border p-12 text-center"><Headphones className="w-10 h-10 text-gray-300 mx-auto mb-3" /><p className="text-gray-400 text-sm">No callbacks in queue</p></div>
      ) : (
        <div className="space-y-2">
          {callbacks.map((cb, i) => (
            <div key={i} className="bg-white rounded-lg border border-gray-200 p-4 flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-900">{String(cb.caller_name ?? cb.caller_id)}</p>
                <p className="text-xs text-gray-500">{String(cb.reason ?? "—")} · {String(cb.channel ?? "PHONE")}</p>
              </div>
              <div className="flex items-center gap-2">
                <span className={`px-2 py-0.5 text-xs rounded-full ${
                  cb.status === "PENDING" ? "bg-amber-100 text-amber-700" : cb.status === "COMPLETED" ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"
                }`}>{String(cb.status)}</span>
                {cb.status === "PENDING" && (
                  <button onClick={() => complete.mutate(String(cb.id))} disabled={complete.isPending}
                    className="px-2 py-1 text-xs bg-green-600 text-white rounded hover:bg-green-700 disabled:opacity-50">Complete</button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function DisclosureTab() {
  const { data } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["omni-disclosure"], queryFn: () => apiClient.get("/internal/v1/omnichannel/disclosure-rules"),
  });
  const [showForm, setShowForm] = useState(false);
  const queryClient = useQueryClient();
  const create = useMutation({
    mutationFn: (body: Record<string, string>) => apiClient.post("/internal/v1/omnichannel/disclosure-rules", body),
    onSuccess: () => { setShowForm(false); queryClient.invalidateQueries({ queryKey: ["omni-disclosure"] }); },
  });
  const rules = data?.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-gray-900">Trust & Disclosure Rules</h3>
        <button onClick={() => setShowForm(!showForm)} className="inline-flex items-center gap-1.5 px-3 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700">
          <Plus className="w-4 h-4" /> Add Rule
        </button>
      </div>
      {showForm && (
        <div className="bg-white rounded-lg border border-indigo-200 p-5 space-y-3">
          <div className="grid grid-cols-3 gap-3">
            <select id="dr-channel" className="px-3 py-2 text-sm border border-gray-300 rounded-lg">
              <option value="SMS">SMS</option><option value="USSD">USSD</option><option value="IVR">IVR</option>
              <option value="WHATSAPP">WhatsApp</option><option value="EMAIL">Email</option><option value="WEBCHAT">Webchat</option>
            </select>
            <select id="dr-category" className="px-3 py-2 text-sm border border-gray-300 rounded-lg">
              <option value="DEMOGRAPHICS">Demographics</option><option value="CLINICAL">Clinical</option>
              <option value="FINANCIAL">Financial</option><option value="CONTACT">Contact</option><option value="LOCATION">Location</option>
            </select>
            <select id="dr-level" className="px-3 py-2 text-sm border border-gray-300 rounded-lg">
              <option value="FULL">Full</option><option value="SUMMARY">Summary</option><option value="MINIMAL">Minimal</option><option value="NONE">None</option>
            </select>
          </div>
          <div className="flex gap-2">
            <button onClick={() => setShowForm(false)} className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm rounded-lg">Cancel</button>
            <button onClick={() => create.mutate({
              channelType: (document.getElementById("dr-channel") as HTMLSelectElement)?.value,
              dataCategory: (document.getElementById("dr-category") as HTMLSelectElement)?.value,
              disclosureLevel: (document.getElementById("dr-level") as HTMLSelectElement)?.value,
            })} disabled={create.isPending} className="flex-1 py-2 bg-indigo-600 text-white text-sm rounded-lg hover:bg-indigo-700 disabled:opacity-50">
              {create.isPending ? "Creating..." : "Create Rule"}
            </button>
          </div>
        </div>
      )}
      {rules.length === 0 ? (
        <div className="bg-white rounded-lg border p-12 text-center"><Shield className="w-10 h-10 text-gray-300 mx-auto mb-3" /><p className="text-gray-400 text-sm">No disclosure rules configured</p></div>
      ) : (
        <div className="space-y-2">
          {rules.map((rule, i) => (
            <div key={i} className="bg-white rounded-lg border border-gray-200 p-4 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <span className="px-2 py-0.5 text-xs rounded bg-indigo-100 text-indigo-700">{String(rule.channel_type)}</span>
                <span className="text-sm text-gray-900">{String(rule.data_category)}</span>
              </div>
              <span className={`px-2 py-0.5 text-xs rounded-full font-medium ${
                rule.disclosure_level === "FULL" ? "bg-green-100 text-green-700" : rule.disclosure_level === "NONE" ? "bg-red-100 text-red-700" : "bg-amber-100 text-amber-700"
              }`}>{String(rule.disclosure_level)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function AiAgentTab() {
  return (
    <div className="space-y-4">
      <h3 className="text-base font-semibold text-gray-900">AI Interaction Agent</h3>
      <p className="text-sm text-gray-500">Governed AI across channels — manages automated responses, triage routing, and escalation to human agents.</p>
      <div className="grid grid-cols-2 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <Bot className="w-8 h-8 text-cyan-500 mb-3" />
          <h4 className="text-sm font-semibold text-gray-900">Conversation Routing</h4>
          <p className="text-xs text-gray-500 mt-1">AI triages incoming messages and routes to appropriate queue or automated response.</p>
          <div className="mt-3 space-y-1.5 text-xs">
            <div className="flex justify-between"><span className="text-gray-600">Appointment queries</span><span className="text-blue-600">→ Auto-respond</span></div>
            <div className="flex justify-between"><span className="text-gray-600">Clinical questions</span><span className="text-amber-600">→ Route to provider</span></div>
            <div className="flex justify-between"><span className="text-gray-600">Emergency</span><span className="text-red-600">→ Immediate escalation</span></div>
          </div>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <Shield className="w-8 h-8 text-indigo-500 mb-3" />
          <h4 className="text-sm font-semibold text-gray-900">Governance Controls</h4>
          <p className="text-xs text-gray-500 mt-1">All AI interactions are logged, auditable, and subject to disclosure rules.</p>
          <div className="mt-3 space-y-1.5 text-xs">
            <div className="flex justify-between"><span className="text-gray-600">Classification</span><span className="text-gray-400">I1 (Non-clinical)</span></div>
            <div className="flex justify-between"><span className="text-gray-600">Override Required</span><span className="text-gray-400">Clinical recommendations</span></div>
            <div className="flex justify-between"><span className="text-gray-600">Audit Trail</span><span className="text-green-600">All interactions logged</span></div>
          </div>
        </div>
      </div>
    </div>
  );
}
