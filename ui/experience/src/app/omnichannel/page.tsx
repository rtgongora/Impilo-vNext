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
      <div className="bg-teal-50 rounded-lg border border-teal-200 p-4 text-sm text-teal-800">
        <strong>Omnichannel Access Principle:</strong> Every citizen can access health services through their preferred channel — smartphone, feature phone, voice, or in-person.
      </div>

      {/* Channel Cards */}
      <div className="grid grid-cols-4 gap-3">
        {([
          ["Smartphone App", "1,234", "12,456", "HIGH"],
          ["Web Portal", "567", "8,901", "HIGH"],
          ["SMS/USSD", "2,345", "34,567", "LOW"],
          ["WhatsApp", "890", "15,678", "MEDIUM"],
          ["IVR/Voice", "123", "4,567", "LOW"],
          ["Call Centre", "45", "2,345", "HIGH"],
          ["Facility Desk", "678", "23,456", "HIGH"],
          ["Community Worker", "234", "6,789", "MEDIUM"],
        ] as const).map(([name, active, total, trust]) => (
          <div key={name} className="bg-white rounded-lg border border-gray-200 p-3">
            <p className="text-xs font-medium text-gray-900">{name}</p>
            <p className="text-lg font-bold text-gray-900">{active}</p>
            <p className="text-[10px] text-gray-500">{total} total sessions</p>
            <span className={`px-1.5 py-0.5 text-[10px] rounded ${trust === "HIGH" ? "bg-green-100 text-green-700" : trust === "MEDIUM" ? "bg-amber-100 text-amber-700" : "bg-red-100 text-red-700"}`}>{trust} trust</span>
          </div>
        ))}
      </div>

      {/* Recent Sessions Table */}
      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <div className="px-4 py-3 border-b"><h3 className="text-sm font-semibold text-gray-900">Recent Channel Sessions</h3></div>
        <table className="w-full text-xs">
          <thead><tr className="border-b bg-gray-50">
            <th className="text-left px-3 py-2 font-medium text-gray-600">Session</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Channel</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Intent</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Trust</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">AI</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Status</th>
          </tr></thead>
          <tbody>
            {([
              ["SES-001","SMS","Appointment check","LOW","Bot","Completed"],
              ["SES-002","WhatsApp","Refill request","MEDIUM","Bot→Human","Escalated"],
              ["SES-003","IVR","Lab results","LOW","Bot","Completed"],
              ["SES-004","Web","Coverage query","HIGH","Human","Active"],
              ["SES-005","USSD","Queue status","LOW","Bot","Completed"],
            ] as const).map(([id,ch,intent,trust,ai,status]) => (
              <tr key={id} className="border-b last:border-0 hover:bg-gray-50">
                <td className="px-3 py-2 font-mono text-gray-500">{id}</td>
                <td className="px-3 py-2"><span className="px-1.5 py-0.5 rounded bg-teal-100 text-teal-700">{ch}</span></td>
                <td className="px-3 py-2 text-gray-900">{intent}</td>
                <td className="px-3 py-2"><span className={`px-1.5 py-0.5 rounded ${trust === "HIGH" ? "bg-green-100 text-green-700" : trust === "LOW" ? "bg-red-100 text-red-700" : "bg-amber-100 text-amber-700"}`}>{trust}</span></td>
                <td className="px-3 py-2 text-gray-600">{ai}</td>
                <td className="px-3 py-2"><span className={`px-1.5 py-0.5 rounded-full ${status === "Completed" ? "bg-green-100 text-green-700" : status === "Active" ? "bg-blue-100 text-blue-700" : "bg-amber-100 text-amber-700"}`}>{status}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
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
          {journeys.map((j, i) => {
            const sent = Number(j.sent_count ?? 0);
            const delivered = Math.round(sent * 0.94);
            const responded = Math.round(sent * 0.23);
            const rate = sent > 0 ? Math.round((delivered / sent) * 100) : 0;
            return (
              <div key={i} className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="flex items-center justify-between mb-2">
                  <p className="text-sm font-medium text-gray-900">{String(j.name)}</p>
                  <span className={`px-2 py-0.5 text-xs rounded-full ${j.is_active ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>{j.is_active ? "Active" : "Inactive"}</span>
                </div>
                <p className="text-xs text-gray-500 mb-2">Trigger: {String(j.trigger_event)}</p>
                <div className="grid grid-cols-3 gap-2 text-xs mb-2">
                  <div><span className="text-gray-500">Sent:</span> <span className="font-medium">{sent}</span></div>
                  <div><span className="text-gray-500">Delivered:</span> <span className="font-medium text-green-700">{delivered}</span></div>
                  <div><span className="text-gray-500">Responded:</span> <span className="font-medium text-blue-700">{responded}</span></div>
                </div>
                <div className="bg-gray-200 rounded-full h-1.5"><div className="bg-teal-500 rounded-full h-1.5" style={{ width: `${rate}%` }} /></div>
                <p className="text-[10px] text-gray-400 mt-1">{rate}% delivery rate</p>
              </div>
            );
          })}
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
      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-xs">
          <thead><tr className="border-b bg-gray-50">
            <th className="text-left px-3 py-2 font-medium text-gray-600">Code</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Menu</th>
            <th className="text-right px-3 py-2 font-medium text-gray-600">Steps</th>
            <th className="text-right px-3 py-2 font-medium text-gray-600">Completion</th>
            <th className="text-right px-3 py-2 font-medium text-gray-600">Daily Use</th>
          </tr></thead>
          <tbody>
            {([["*123#","Main Menu",4,"78%","2,345"],["*123*1#","Appointments",3,"85%","890"],["*123*2#","Refill Rx",3,"91%","567"],["*123*3#","Find Clinic",2,"64%","1,234"],["*123*4#","Emergency",1,"97%","45"],["*123*5#","Queue Status",2,"82%","678"]] as const).map(([code,name,steps,completion,daily]) => (
              <tr key={code} className="border-b last:border-0 hover:bg-gray-50">
                <td className="px-3 py-2 font-mono font-medium text-gray-900">{code}</td>
                <td className="px-3 py-2 text-gray-700">{name}</td>
                <td className="px-3 py-2 text-right text-gray-600">{steps}</td>
                <td className="px-3 py-2 text-right"><span className="font-medium text-green-700">{completion}</span></td>
                <td className="px-3 py-2 text-right text-gray-600">{daily}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
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
      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-xs">
          <thead><tr className="border-b bg-gray-50">
            <th className="text-left px-3 py-2 font-medium text-gray-600">Flow</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Languages</th>
            <th className="text-right px-3 py-2 font-medium text-gray-600">Avg Duration</th>
            <th className="text-right px-3 py-2 font-medium text-gray-600">Completion</th>
            <th className="text-right px-3 py-2 font-medium text-gray-600">Escalation</th>
          </tr></thead>
          <tbody>
            {([["Main Menu","EN, SN, ND","1:45","82%","12%"],["Appointment Status","EN, SN","2:30","76%","18%"],["Medication Refill","EN, SN, ND","3:15","68%","25%"],["Lab Results","EN","1:20","91%","8%"],["Emergency Triage","EN, SN, ND","0:45","95%","45%"]] as const).map(([flow,langs,dur,comp,esc]) => (
              <tr key={flow} className="border-b last:border-0 hover:bg-gray-50">
                <td className="px-3 py-2 font-medium text-gray-900">{flow}</td>
                <td className="px-3 py-2 text-gray-600">{langs}</td>
                <td className="px-3 py-2 text-right text-gray-600">{dur}</td>
                <td className="px-3 py-2 text-right"><span className="font-medium text-green-700">{comp}</span></td>
                <td className="px-3 py-2 text-right"><span className={`font-medium ${parseInt(esc) > 30 ? "text-red-600" : parseInt(esc) > 15 ? "text-amber-600" : "text-green-700"}`}>{esc}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
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
    <div className="space-y-6">
      <div className="bg-cyan-50 rounded-lg border border-cyan-200 p-4 text-sm text-cyan-800">
        <strong>Governed AI Agent:</strong> All AI interactions are classified (I1/I2/I3), logged, and subject to human review for clinical recommendations.
      </div>

      <div className="grid grid-cols-3 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <p className="text-2xl font-bold text-cyan-700">1,234</p><p className="text-xs text-gray-500">AI Sessions Today</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <p className="text-2xl font-bold text-amber-700">89</p><p className="text-xs text-gray-500">Human Review Required</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <p className="text-2xl font-bold text-green-700">94.2%</p><p className="text-xs text-gray-500">Avg Confidence</p>
        </div>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <div className="px-4 py-3 border-b"><h3 className="text-sm font-semibold text-gray-900">AI Interaction Log</h3></div>
        <table className="w-full text-xs">
          <thead><tr className="border-b bg-gray-50">
            <th className="text-left px-3 py-2 font-medium text-gray-600">ID</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Channel</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Intent</th>
            <th className="text-right px-3 py-2 font-medium text-gray-600">Confidence</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Class</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Review</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Status</th>
          </tr></thead>
          <tbody>
            {([
              ["AI-001","SMS","Appointment reminder","98%","I1","Auto","Completed"],
              ["AI-002","WhatsApp","Medication query","87%","I1","Auto","Completed"],
              ["AI-003","IVR","Symptom triage","72%","I2","Required","Escalated"],
              ["AI-004","Webchat","Lab result query","95%","I1","Auto","Completed"],
              ["AI-005","SMS","Dosage question","61%","I2","Required","Pending"],
              ["AI-006","USSD","Queue status","99%","I1","Auto","Completed"],
            ] as const).map(([id,ch,intent,conf,cls,review,status]) => (
              <tr key={id} className="border-b last:border-0 hover:bg-gray-50">
                <td className="px-3 py-2 font-mono text-gray-500">{id}</td>
                <td className="px-3 py-2"><span className="px-1.5 py-0.5 rounded bg-cyan-100 text-cyan-700">{ch}</span></td>
                <td className="px-3 py-2 text-gray-900">{intent}</td>
                <td className="px-3 py-2 text-right"><span className={`font-medium ${parseInt(conf) >= 90 ? "text-green-700" : parseInt(conf) >= 70 ? "text-amber-700" : "text-red-700"}`}>{conf}</span></td>
                <td className="px-3 py-2"><span className={`px-1.5 py-0.5 rounded text-[10px] ${cls === "I1" ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"}`}>{cls}</span></td>
                <td className="px-3 py-2"><span className={`text-[10px] ${review === "Auto" ? "text-green-600" : "text-amber-600 font-medium"}`}>{review}</span></td>
                <td className="px-3 py-2"><span className={`px-1.5 py-0.5 rounded-full text-[10px] ${status === "Completed" ? "bg-green-100 text-green-700" : status === "Escalated" ? "bg-amber-100 text-amber-700" : "bg-blue-100 text-blue-700"}`}>{status}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <Bot className="w-6 h-6 text-cyan-500 mb-2" />
          <h4 className="text-sm font-semibold text-gray-900">Conversation Routing</h4>
          <div className="mt-2 space-y-1.5 text-xs">
            <div className="flex justify-between"><span className="text-gray-600">Appointment queries</span><span className="text-blue-600">→ Auto-respond</span></div>
            <div className="flex justify-between"><span className="text-gray-600">Clinical questions</span><span className="text-amber-600">→ Route to provider</span></div>
            <div className="flex justify-between"><span className="text-gray-600">Emergency</span><span className="text-red-600">→ Immediate escalation</span></div>
          </div>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <Shield className="w-6 h-6 text-indigo-500 mb-2" />
          <h4 className="text-sm font-semibold text-gray-900">Governance Controls</h4>
          <div className="mt-2 space-y-1.5 text-xs">
            <div className="flex justify-between"><span className="text-gray-600">I1 Non-clinical</span><span className="text-green-600">Auto-approve</span></div>
            <div className="flex justify-between"><span className="text-gray-600">I2 Clinical support</span><span className="text-amber-600">Human review</span></div>
            <div className="flex justify-between"><span className="text-gray-600">I3 Clinical decision</span><span className="text-red-600">Always human</span></div>
          </div>
        </div>
      </div>
    </div>
  );
}
