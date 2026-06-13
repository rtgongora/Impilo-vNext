"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Bot, Headphones, Loader2, MessageSquare, Phone, PhoneCall, Plus, Radio, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { NotificationCommsOrchestrationRail } from "@/components/platform/NotificationCommsOrchestrationRail";
import { PageShell } from "@/components/PageShell";
import { OutreachCatchmentMapPanel } from "@/components/maps/OutreachCatchmentMapPanel";
import {
  useCompleteOmnichannelCallback,
  useCreateDisclosureRule,
  useCreateOmnichannelCallback,
  useCreateSmsJourney,
  useOmnichannelCallbacks,
  useOmnichannelDashboard,
  useOmnichannelChannels,
  useOmnichannelDisclosureRules,
  useOmnichannelIvrFlows,
  useOmnichannelSmsJourneys,
  useOmnichannelUssdMenus,
  useOmnichannelCampaigns,
  useCreateOmnichannelCampaign,
} from "@/hooks/queries/useOmnichannel";

type ActiveTab = "overview" | "campaigns" | "sms" | "ussd" | "ivr" | "callbacks" | "disclosure" | "ai-agent";

const TABS: { key: ActiveTab; label: string; icon: typeof Radio }[] = [
  { key: "overview", label: "Overview", icon: Radio },
  { key: "campaigns", label: "Campaigns", icon: Plus },
  { key: "sms", label: "SMS Journeys", icon: MessageSquare },
  { key: "ussd", label: "USSD Menus", icon: Phone },
  { key: "ivr", label: "IVR / Voice", icon: PhoneCall },
  { key: "callbacks", label: "Callbacks", icon: Headphones },
  { key: "disclosure", label: "Disclosure", icon: Shield },
  { key: "ai-agent", label: "AI Agent", icon: Bot },
];

function countDefinitionNodes(value: unknown): number {
  if (!value || typeof value !== "object") return 0;
  if (Array.isArray(value)) return value.reduce<number>((sum, item) => sum + countDefinitionNodes(item), 0);
  const entries = Object.values(value as Record<string, unknown>);
  return entries.length + entries.reduce<number>((sum, item) => sum + countDefinitionNodes(item), 0);
}

function formatDateTime(value?: string | null) {
  if (!value) return "Not yet recorded";
  return new Date(value).toLocaleString();
}

function metricNumber(dashboard: Record<string, unknown> | undefined, key: string, fallback = 0) {
  if (!dashboard) return fallback;
  const legacy = dashboard[key];
  if (typeof legacy === "number") return legacy;
  const operations = (dashboard.operations as Record<string, unknown> | undefined) ?? {};
  const communications = (dashboard.communications as Record<string, unknown> | undefined) ?? {};
  const clinical = (dashboard.clinical as Record<string, unknown> | undefined) ?? {};
  const candidates = [
    operations[key],
    communications[key],
    clinical[key],
  ];
  for (const candidate of candidates) {
    if (typeof candidate === "number") return candidate;
  }
  return fallback;
}

export default function OmnichannelPage() {
  const searchParams = useSearchParams();
  const requestedTab = searchParams.get("tab");
  const [activeTab, setActiveTab] = useState<ActiveTab>(TABS.some((tab) => tab.key === requestedTab) ? (requestedTab as ActiveTab) : "overview");

  useEffect(() => {
    if (TABS.some((tab) => tab.key === requestedTab)) {
      setActiveTab(requestedTab as ActiveTab);
    }
  }, [requestedTab]);

  return (
    <AppLayout>
      <PageShell title="Omnichannel Hub" subtitle="SMS, USSD, IVR, callbacks, disclosure, and governed AI access">
        <div className="mb-4">
          <NotificationCommsOrchestrationRail />
        </div>
        <div className="mb-6 flex gap-1 overflow-x-auto border-b border-border">
          {TABS.map((tab) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className={`whitespace-nowrap border-b-2 px-3 py-2.5 text-sm font-medium transition-colors ${activeTab === tab.key ? "border-teal-600 text-teal-600" : "border-transparent text-muted-foreground hover:text-foreground"}`}
              >
                <span className="inline-flex items-center gap-1.5"><Icon className="h-4 w-4" />{tab.label}</span>
              </button>
            );
          })}
        </div>

        {activeTab === "overview" && <OverviewTab />}
        {activeTab === "campaigns" && <CampaignsTab />}
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
  const { data: dashboardData, isLoading: dashboardLoading } = useOmnichannelDashboard();
  const { data: callbacksData, isLoading: callbacksLoading } = useOmnichannelCallbacks();
  const { data: channelsData, isLoading: channelsLoading } = useOmnichannelChannels();
  const { data: smsData, isLoading: smsLoading } = useOmnichannelSmsJourneys();
  const { data: ussdData, isLoading: ussdLoading } = useOmnichannelUssdMenus();
  const { data: ivrData, isLoading: ivrLoading } = useOmnichannelIvrFlows();
  const { data: disclosureData, isLoading: disclosureLoading } = useOmnichannelDisclosureRules();
  const dashboard = dashboardData?.data;
  const callbacks = callbacksData?.data ?? [];
  const channels = channelsData?.data ?? [];
  const smsJourneys = smsData?.data ?? [];
  const ussdMenus = ussdData?.data ?? [];
  const ivrFlows = ivrData?.data ?? [];
  const disclosureRules = disclosureData?.data ?? [];
  const loading = dashboardLoading || callbacksLoading || channelsLoading || smsLoading || ussdLoading || ivrLoading || disclosureLoading;

  const sourceHealth =
    (dashboard?.governance as { source_health?: Record<string, string> } | undefined)?.source_health
    ?? dashboard?.source_health
    ?? {};
  const lastRefreshedAt =
    (dashboard?.governance as { last_refreshed_at?: string | null } | undefined)?.last_refreshed_at
    ?? dashboard?.last_refreshed_at;

  return (
    <div className="space-y-6">
      <div className="rounded-lg border border-teal-200 bg-teal-50 p-4 text-sm text-teal-800">
        <strong>Omnichannel access principle:</strong> every citizen reaches the same platform through a governed channel, not a separate product.
      </div>
      {loading ? (
        <div className="flex items-center justify-center gap-2 py-8"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /><span className="text-sm text-muted-foreground">Loading omnichannel telemetry...</span></div>
      ) : (
        <>
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            <SummaryCard href="/omnichannel?tab=overview" label="Configured channels" value={String(metricNumber(dashboard as Record<string, unknown> | undefined, "configured_channels", channels.filter((item) => item.is_active).length))} helper="Active channel configs" tone="teal" />
            <SummaryCard href="/omnichannel?tab=callbacks" label="Pending callbacks" value={String(metricNumber(dashboard as Record<string, unknown> | undefined, "pending_callbacks", callbacks.filter((item) => item.status === "PENDING").length))} helper="Queue items needing follow-up" tone="amber" />
            <SummaryCard href="/omnichannel?tab=sms" label="SMS journeys" value={String(metricNumber(dashboard as Record<string, unknown> | undefined, "active_sms_journeys", smsJourneys.filter((item) => item.is_active).length))} helper="Active outbound journeys" tone="blue" />
            <SummaryCard href="/omnichannel?tab=ussd" label="USSD menus" value={String(metricNumber(dashboard as Record<string, unknown> | undefined, "ussd_flows", ussdMenus.length))} helper="Published feature-phone paths" tone="green" />
            <SummaryCard href="/omnichannel?tab=ivr" label="IVR flows" value={String(metricNumber(dashboard as Record<string, unknown> | undefined, "ivr_flows", ivrFlows.length))} helper="Voice flow definitions" tone="purple" />
            <SummaryCard href="/omnichannel?tab=disclosure" label="Disclosure rules" value={String(metricNumber(dashboard as Record<string, unknown> | undefined, "disclosure_rules", disclosureRules.length))} helper="Active data release policies" tone="indigo" />
          </div>
          <div className="rounded-lg border border-border bg-card p-4">
            <div className="flex flex-wrap items-center gap-2 text-xs">
              <span className="font-medium text-muted-foreground">Source health:</span>
              {Object.entries(sourceHealth).map(([source, status]) => (
                <span
                  key={source}
                  className={`rounded-full px-2 py-0.5 ${status === "UP" ? "bg-green-100 text-green-700" : "bg-red-100 text-danger"}`}
                >
                  {source}: {status}
                </span>
              ))}
              {lastRefreshedAt ? <span className="ml-auto text-muted-foreground">Updated {formatDateTime(lastRefreshedAt)}</span> : null}
            </div>
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <div className="overflow-hidden rounded-lg border border-border bg-card">
              <div className="border-b px-4 py-3"><h3 className="text-sm font-semibold text-foreground">Channel configuration</h3></div>
              <table className="w-full text-xs"><thead><tr className="border-b bg-background"><th className="px-3 py-2 text-left font-medium text-muted-foreground">Channel</th><th className="px-3 py-2 text-left font-medium text-muted-foreground">Name</th><th className="px-3 py-2 text-left font-medium text-muted-foreground">Status</th></tr></thead><tbody>{channels.length === 0 ? <tr><td colSpan={3} className="px-3 py-6 text-center text-muted-foreground">No active channel configs found.</td></tr> : channels.map((channel) => <tr key={channel.id} className="border-b last:border-0 hover:bg-background"><td className="px-3 py-2 font-medium text-foreground">{channel.channel_type}</td><td className="px-3 py-2 text-muted-foreground">{channel.name}</td><td className="px-3 py-2"><span className={`rounded-full px-2 py-0.5 ${channel.is_active ? "bg-green-100 text-green-700" : "bg-neutral-100 text-muted-foreground"}`}>{channel.is_active ? "Active" : "Inactive"}</span></td></tr>)}</tbody></table>
            </div>
            <div className="overflow-hidden rounded-lg border border-border bg-card">
              <div className="border-b px-4 py-3"><h3 className="text-sm font-semibold text-foreground">Current callback queue</h3></div>
              <table className="w-full text-xs"><thead><tr className="border-b bg-background"><th className="px-3 py-2 text-left font-medium text-muted-foreground">Caller</th><th className="px-3 py-2 text-left font-medium text-muted-foreground">Priority</th><th className="px-3 py-2 text-left font-medium text-muted-foreground">Status</th></tr></thead><tbody>{callbacks.length === 0 ? <tr><td colSpan={3} className="px-3 py-6 text-center text-muted-foreground">No callbacks are waiting.</td></tr> : callbacks.slice(0, 5).map((callback) => <tr key={callback.id} className="border-b last:border-0 hover:bg-background"><td className="px-3 py-2 text-foreground">{callback.caller_name || callback.caller_id}</td><td className="px-3 py-2 text-muted-foreground">{callback.priority}</td><td className="px-3 py-2"><span className={`rounded-full px-2 py-0.5 ${callback.status === "PENDING" ? "bg-amber-100 text-warning-foreground" : "bg-green-100 text-green-700"}`}>{callback.status}</span></td></tr>)}</tbody></table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

function CampaignsTab() {
  const { data: campaigns, isLoading } = useOmnichannelCampaigns();
  const createCampaign = useCreateOmnichannelCampaign();
  const [form, setForm] = useState({ name: "", channel: "SMS", messageTemplate: "" });

  return (
    <div className="space-y-4">
      <OutreachCatchmentMapPanel />
      <form
        className="grid gap-3 rounded-lg border border-border bg-card p-4 md:grid-cols-2"
        onSubmit={(e) => {
          e.preventDefault();
          createCampaign.mutate({
            name: form.name,
            channel: form.channel,
            messageTemplate: form.messageTemplate,
            campaignType: "OMNICHANNEL_CAMPAIGN",
          });
        }}
      >
        <input
          className="rounded-lg border border-border px-3 py-2 text-sm"
          placeholder="Campaign name"
          value={form.name}
          onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
          required
        />
        <select
          className="rounded-lg border border-border px-3 py-2 text-sm"
          value={form.channel}
          onChange={(e) => setForm((p) => ({ ...p, channel: e.target.value }))}
        >
          <option value="SMS">SMS</option>
          <option value="EMAIL">Email</option>
          <option value="IVR">IVR</option>
        </select>
        <textarea
          className="md:col-span-2 rounded-lg border border-border px-3 py-2 text-sm"
          rows={3}
          placeholder="Message template"
          value={form.messageTemplate}
          onChange={(e) => setForm((p) => ({ ...p, messageTemplate: e.target.value }))}
        />
        <button
          type="submit"
          disabled={createCampaign.isPending}
          className="md:col-span-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-teal-800 disabled:opacity-50"
        >
          {createCampaign.isPending ? "Creating…" : "Create campaign"}
        </button>
      </form>
      {isLoading ? (
        <div className="flex items-center gap-2 py-6">
          <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
          <span className="text-sm text-muted-foreground">Loading campaigns…</span>
        </div>
      ) : (
        <ul className="divide-y divide-gray-100 rounded-lg border border-border bg-card">
          {(campaigns ?? []).length === 0 ? (
            <li className="p-4 text-sm text-muted-foreground">No campaigns from campaigns-service.</li>
          ) : (
            campaigns?.map((row, index) => (
              <li key={String(row.id ?? index)} className="p-4">
                <p className="text-sm font-medium text-foreground">{String(row.name ?? row.id)}</p>
                <p className="text-xs text-muted-foreground">
                  {String(row.channel ?? "—")} · {String(row.status ?? "DRAFT")}
                </p>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}

function SummaryCard({ label, value, helper, tone, href }: { label: string; value: string; helper: string; tone: "teal" | "amber" | "blue" | "green" | "purple" | "indigo"; href?: string }) {
  const toneClasses: Record<string, string> = { teal: "bg-teal-50 text-teal-900", amber: "bg-warning-soft text-warning-foreground", blue: "bg-primary-soft text-impilo-800", green: "bg-green-50 text-green-900", purple: "bg-warning-soft text-purple-900", indigo: "bg-info-soft text-primary-hover" };
  const content = <div className={`rounded-lg p-4 ${toneClasses[tone]}`}><p className="text-xs font-medium uppercase tracking-wide opacity-70">{label}</p><p className="mt-1 text-2xl font-semibold">{value}</p><p className="mt-1 text-xs opacity-80">{helper}</p></div>;
  return href ? <Link href={href}>{content}</Link> : content;
}

function SmsTab() {
  const { data, isLoading } = useOmnichannelSmsJourneys();
  const createJourney = useCreateSmsJourney();
  const journeys = data?.data ?? [];
  const [showForm, setShowForm] = useState(false);
  const [formState, setFormState] = useState({ name: "", triggerEvent: "", messageTemplate: "", scheduleCron: "" });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between"><h3 className="text-base font-semibold text-foreground">SMS Journeys</h3><button onClick={() => setShowForm((value) => !value)} className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-3 py-2 text-sm font-medium text-white hover:bg-primary"><Plus className="h-4 w-4" />New journey</button></div>
      {showForm ? <div className="space-y-3 rounded-lg border border-teal-200 bg-card p-5"><div className="grid gap-3 md:grid-cols-2"><input type="text" placeholder="Journey name" value={formState.name} onChange={(event) => setFormState({ ...formState, name: event.target.value })} className="rounded-lg border border-border px-3 py-2 text-sm" /><input type="text" placeholder="Trigger event" value={formState.triggerEvent} onChange={(event) => setFormState({ ...formState, triggerEvent: event.target.value })} className="rounded-lg border border-border px-3 py-2 text-sm" /></div><textarea placeholder="Message template" value={formState.messageTemplate} onChange={(event) => setFormState({ ...formState, messageTemplate: event.target.value })} rows={3} className="w-full rounded-lg border border-border px-3 py-2 text-sm" /><input type="text" placeholder="Schedule cron" value={formState.scheduleCron} onChange={(event) => setFormState({ ...formState, scheduleCron: event.target.value })} className="w-full rounded-lg border border-border px-3 py-2 text-sm" /><div className="flex gap-2"><button onClick={() => setShowForm(false)} className="flex-1 rounded-lg bg-neutral-100 py-2 text-sm text-foreground">Cancel</button><button onClick={() => createJourney.mutate(formState, { onSuccess: () => { setShowForm(false); setFormState({ name: "", triggerEvent: "", messageTemplate: "", scheduleCron: "" }); } })} disabled={!formState.name || !formState.triggerEvent || !formState.messageTemplate || createJourney.isPending} className="flex-1 rounded-lg bg-teal-600 py-2 text-sm text-white hover:bg-primary disabled:opacity-50">{createJourney.isPending ? "Creating..." : "Create journey"}</button></div></div> : null}
      {isLoading ? <div className="flex items-center justify-center gap-2 py-8"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /><span className="text-sm text-muted-foreground">Loading SMS journeys...</span></div> : journeys.length === 0 ? <div className="rounded-lg border border-border bg-card p-12 text-center"><MessageSquare className="mx-auto mb-3 h-10 w-10 text-muted-foreground" /><p className="text-sm text-muted-foreground">No SMS journeys configured</p></div> : <div className="space-y-3">{journeys.map((journey) => <div key={journey.id} className="rounded-lg border border-border bg-card p-4"><div className="mb-2 flex items-center justify-between gap-3"><p className="text-sm font-medium text-foreground">{journey.name}</p><span className={`rounded-full px-2 py-0.5 text-xs ${journey.is_active ? "bg-green-100 text-green-700" : "bg-neutral-100 text-muted-foreground"}`}>{journey.is_active ? "Active" : "Inactive"}</span></div><div className="grid gap-2 text-xs text-muted-foreground md:grid-cols-2"><div><span className="font-medium text-foreground">Trigger:</span> {journey.trigger_event}</div><div><span className="font-medium text-foreground">Schedule:</span> {journey.schedule_cron || "Manual / external trigger"}</div><div><span className="font-medium text-foreground">Sent count:</span> {journey.sent_count ?? 0}</div><div><span className="font-medium text-foreground">Created:</span> {formatDateTime(journey.created_at)}</div></div><p className="mt-3 rounded bg-background p-3 text-sm text-foreground">{journey.message_template}</p></div>)}</div>}
    </div>
  );
}

function UssdTab() {
  const { data, isLoading } = useOmnichannelUssdMenus();
  const menus = data?.data ?? [];

  return (
    <div className="space-y-4">
      <div><h3 className="text-base font-semibold text-foreground">USSD menu definitions</h3><p className="text-sm text-muted-foreground">Feature-phone access paths currently configured in the platform.</p></div>
      <div className="overflow-hidden rounded-lg border border-border bg-card"><table className="w-full text-xs"><thead><tr className="border-b bg-background"><th className="px-3 py-2 text-left font-medium text-muted-foreground">Short code</th><th className="px-3 py-2 text-left font-medium text-muted-foreground">Status</th><th className="px-3 py-2 text-right font-medium text-muted-foreground">Definition nodes</th><th className="px-3 py-2 text-left font-medium text-muted-foreground">Created</th></tr></thead><tbody>{isLoading ? <tr><td colSpan={4} className="px-3 py-6 text-center text-muted-foreground">Loading USSD menus...</td></tr> : menus.length === 0 ? <tr><td colSpan={4} className="px-3 py-6 text-center text-muted-foreground">No USSD menus configured.</td></tr> : menus.map((menu) => <tr key={menu.id} className="border-b last:border-0 hover:bg-background"><td className="px-3 py-2 font-medium text-foreground">{menu.short_code}</td><td className="px-3 py-2"><span className={`rounded-full px-2 py-0.5 ${menu.is_active ? "bg-green-100 text-green-700" : "bg-neutral-100 text-muted-foreground"}`}>{menu.is_active ? "Active" : "Inactive"}</span></td><td className="px-3 py-2 text-right text-muted-foreground">{countDefinitionNodes(menu.menu_tree)}</td><td className="px-3 py-2 text-muted-foreground">{formatDateTime(menu.created_at)}</td></tr>)}</tbody></table></div>
    </div>
  );
}

function IvrTab() {
  const { data, isLoading } = useOmnichannelIvrFlows();
  const flows = data?.data ?? [];

  return (
    <div className="space-y-4">
      <div><h3 className="text-base font-semibold text-foreground">IVR and voice flows</h3><p className="text-sm text-muted-foreground">Voice entry points currently configured for omnichannel access.</p></div>
      <div className="overflow-hidden rounded-lg border border-border bg-card"><table className="w-full text-xs"><thead><tr className="border-b bg-background"><th className="px-3 py-2 text-left font-medium text-muted-foreground">Flow</th><th className="px-3 py-2 text-left font-medium text-muted-foreground">Phone number</th><th className="px-3 py-2 text-right font-medium text-muted-foreground">Definition nodes</th><th className="px-3 py-2 text-left font-medium text-muted-foreground">Created</th></tr></thead><tbody>{isLoading ? <tr><td colSpan={4} className="px-3 py-6 text-center text-muted-foreground">Loading IVR flows...</td></tr> : flows.length === 0 ? <tr><td colSpan={4} className="px-3 py-6 text-center text-muted-foreground">No IVR flows configured.</td></tr> : flows.map((flow) => <tr key={flow.id} className="border-b last:border-0 hover:bg-background"><td className="px-3 py-2 font-medium text-foreground">{flow.name}</td><td className="px-3 py-2 text-muted-foreground">{flow.phone_number || "Not assigned"}</td><td className="px-3 py-2 text-right text-muted-foreground">{countDefinitionNodes(flow.flow_definition)}</td><td className="px-3 py-2 text-muted-foreground">{formatDateTime(flow.created_at)}</td></tr>)}</tbody></table></div>
    </div>
  );
}

function CallbacksTab() {
  const { data, isLoading } = useOmnichannelCallbacks();
  const createCallback = useCreateOmnichannelCallback();
  const completeCallback = useCompleteOmnichannelCallback();
  const callbacks = data?.data ?? [];
  const [showForm, setShowForm] = useState(false);
  const [formState, setFormState] = useState({ callerId: "", callerName: "", reason: "", priority: "NORMAL" });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between"><h3 className="text-base font-semibold text-foreground">Callback queue</h3><button onClick={() => setShowForm((value) => !value)} className="inline-flex items-center gap-1.5 rounded-lg bg-amber-600 px-3 py-2 text-sm font-medium text-white hover:bg-amber-700"><Plus className="h-4 w-4" />Add callback</button></div>
      {showForm ? <div className="space-y-3 rounded-lg border border-warning/35 bg-card p-5"><div className="grid gap-3 md:grid-cols-2"><input type="text" placeholder="Caller ID / phone" value={formState.callerId} onChange={(event) => setFormState({ ...formState, callerId: event.target.value })} className="rounded-lg border border-border px-3 py-2 text-sm" /><input type="text" placeholder="Caller name" value={formState.callerName} onChange={(event) => setFormState({ ...formState, callerName: event.target.value })} className="rounded-lg border border-border px-3 py-2 text-sm" /></div><textarea placeholder="Reason for callback" value={formState.reason} onChange={(event) => setFormState({ ...formState, reason: event.target.value })} rows={2} className="w-full rounded-lg border border-border px-3 py-2 text-sm" /><select value={formState.priority} onChange={(event) => setFormState({ ...formState, priority: event.target.value })} className="w-full rounded-lg border border-border px-3 py-2 text-sm"><option value="NORMAL">Normal</option><option value="HIGH">High</option><option value="URGENT">Urgent</option></select><div className="flex gap-2"><button onClick={() => setShowForm(false)} className="flex-1 rounded-lg bg-neutral-100 py-2 text-sm text-foreground">Cancel</button><button onClick={() => createCallback.mutate(formState, { onSuccess: () => { setShowForm(false); setFormState({ callerId: "", callerName: "", reason: "", priority: "NORMAL" }); } })} disabled={!formState.callerId || !formState.reason || createCallback.isPending} className="flex-1 rounded-lg bg-amber-600 py-2 text-sm text-white hover:bg-amber-700 disabled:opacity-50">{createCallback.isPending ? "Adding..." : "Add to queue"}</button></div></div> : null}
      {isLoading ? <div className="flex items-center justify-center gap-2 py-8"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /><span className="text-sm text-muted-foreground">Loading callback queue...</span></div> : callbacks.length === 0 ? <div className="rounded-lg border border-border bg-card p-12 text-center"><Headphones className="mx-auto mb-3 h-10 w-10 text-muted-foreground" /><p className="text-sm text-muted-foreground">No callbacks in queue</p></div> : <div className="space-y-2">{callbacks.map((callback) => <div key={callback.id} className="flex items-center justify-between gap-3 rounded-lg border border-border bg-card p-4"><div><p className="text-sm font-medium text-foreground">{callback.caller_name || callback.caller_id}</p><p className="text-xs text-muted-foreground">{callback.reason || "No reason recorded"}</p><p className="mt-1 text-[11px] text-muted-foreground">{callback.channel} - {formatDateTime(callback.created_at)}</p></div><div className="flex items-center gap-2"><span className={`rounded-full px-2 py-0.5 text-xs ${callback.status === "PENDING" ? "bg-amber-100 text-warning-foreground" : "bg-green-100 text-green-700"}`}>{callback.status}</span>{callback.status === "PENDING" ? <button onClick={() => completeCallback.mutate(callback.id)} disabled={completeCallback.isPending} className="rounded bg-green-600 px-2 py-1 text-xs text-white hover:bg-green-700 disabled:opacity-50">Complete</button> : null}</div></div>)}</div>}
    </div>
  );
}

function DisclosureTab() {
  const { data, isLoading } = useOmnichannelDisclosureRules();
  const createRule = useCreateDisclosureRule();
  const rules = data?.data ?? [];
  const [showForm, setShowForm] = useState(false);
  const [formState, setFormState] = useState({ channelType: "SMS", dataCategory: "DEMOGRAPHICS", disclosureLevel: "FULL" });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between"><h3 className="text-base font-semibold text-foreground">Trust and disclosure rules</h3><button onClick={() => setShowForm((value) => !value)} className="inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-3 py-2 text-sm font-medium text-white hover:bg-primary"><Plus className="h-4 w-4" />Add rule</button></div>
      {showForm ? <div className="space-y-3 rounded-lg border border-info/25 bg-card p-5"><div className="grid gap-3 md:grid-cols-3"><select value={formState.channelType} onChange={(event) => setFormState({ ...formState, channelType: event.target.value })} className="rounded-lg border border-border px-3 py-2 text-sm"><option value="SMS">SMS</option><option value="USSD">USSD</option><option value="IVR">IVR</option><option value="WHATSAPP">WhatsApp</option><option value="EMAIL">Email</option><option value="WEBCHAT">Webchat</option></select><select value={formState.dataCategory} onChange={(event) => setFormState({ ...formState, dataCategory: event.target.value })} className="rounded-lg border border-border px-3 py-2 text-sm"><option value="DEMOGRAPHICS">Demographics</option><option value="CLINICAL">Clinical</option><option value="FINANCIAL">Financial</option><option value="CONTACT">Contact</option><option value="LOCATION">Location</option></select><select value={formState.disclosureLevel} onChange={(event) => setFormState({ ...formState, disclosureLevel: event.target.value })} className="rounded-lg border border-border px-3 py-2 text-sm"><option value="FULL">Full</option><option value="SUMMARY">Summary</option><option value="MINIMAL">Minimal</option><option value="NONE">None</option></select></div><div className="flex gap-2"><button onClick={() => setShowForm(false)} className="flex-1 rounded-lg bg-neutral-100 py-2 text-sm text-foreground">Cancel</button><button onClick={() => createRule.mutate(formState, { onSuccess: () => { setShowForm(false); setFormState({ channelType: "SMS", dataCategory: "DEMOGRAPHICS", disclosureLevel: "FULL" }); } })} disabled={createRule.isPending} className="flex-1 rounded-lg bg-indigo-600 py-2 text-sm text-white hover:bg-primary disabled:opacity-50">{createRule.isPending ? "Creating..." : "Create rule"}</button></div></div> : null}
      {isLoading ? <div className="flex items-center justify-center gap-2 py-8"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /><span className="text-sm text-muted-foreground">Loading disclosure rules...</span></div> : rules.length === 0 ? <div className="rounded-lg border border-border bg-card p-12 text-center"><Shield className="mx-auto mb-3 h-10 w-10 text-muted-foreground" /><p className="text-sm text-muted-foreground">No disclosure rules configured</p></div> : <div className="space-y-2">{rules.map((rule) => <div key={rule.id} className="flex items-center justify-between rounded-lg border border-border bg-card p-4"><div className="flex items-center gap-3"><span className="rounded bg-indigo-100 px-2 py-0.5 text-xs text-primary-hover">{rule.channel_type}</span><span className="text-sm text-foreground">{rule.data_category}</span></div><span className={`rounded-full px-2 py-0.5 text-xs font-medium ${rule.disclosure_level === "FULL" ? "bg-green-100 text-green-700" : rule.disclosure_level === "NONE" ? "bg-red-100 text-danger" : "bg-amber-100 text-warning-foreground"}`}>{rule.disclosure_level}</span></div>)}</div>}
    </div>
  );
}

function AiAgentTab() {
  const { data: channelsData } = useOmnichannelChannels();
  const { data: callbacksData } = useOmnichannelCallbacks();
  const { data: rulesData } = useOmnichannelDisclosureRules();
  const { data: smsData } = useOmnichannelSmsJourneys();
  const channels = channelsData?.data ?? [];
  const callbacks = callbacksData?.data ?? [];
  const rules = rulesData?.data ?? [];
  const smsJourneys = smsData?.data ?? [];

  return (
    <div className="space-y-6">
      <div className="rounded-lg border border-cyan-200 bg-cyan-50 p-4 text-sm text-cyan-800"><strong>Governed AI agent:</strong> automation is only acceptable where disclosure policy, escalation routing, and human-review boundaries are explicit.</div>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4"><SummaryCard label="Channel configs" value={String(channels.length)} helper="Available omnichannel entry points" tone="teal" /><SummaryCard label="Pending callbacks" value={String(callbacks.filter((item) => item.status === "PENDING").length)} helper="Human queue requiring follow-up" tone="amber" /><SummaryCard label="Disclosure rules" value={String(rules.length)} helper="Active policy gates" tone="indigo" /><SummaryCard label="SMS journeys" value={String(smsJourneys.length)} helper="Automated outbound journeys" tone="blue" /></div>
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-lg border border-border bg-card p-5"><h4 className="text-sm font-semibold text-foreground">Automation guardrails</h4><div className="mt-3 space-y-2 text-sm text-muted-foreground"><p>Low-risk access flows can use automation only when disclosure rules already cover the data category.</p><p>Clinical or identity-sensitive requests should escalate to staffed callbacks, telemedicine, or registry/admin workspaces.</p><p>AI operations should be reviewed in the governance workspace rather than inferred from fake telemetry.</p></div></div>
        <div className="rounded-lg border border-border bg-card p-5"><h4 className="text-sm font-semibold text-foreground">Continue into governed surfaces</h4><div className="mt-3 space-y-2 text-sm"><Link href="/ai-governance" className="block rounded-lg border border-cyan-200 px-3 py-2 text-cyan-700 hover:bg-cyan-50">AI Governance Hub</Link><Link href="/omnichannel?tab=disclosure" className="block rounded-lg border border-info/25 px-3 py-2 text-primary-hover hover:bg-info-soft">Review disclosure rules</Link><Link href="/omnichannel?tab=callbacks" className="block rounded-lg border border-warning/35 px-3 py-2 text-warning-foreground hover:bg-warning-soft">Review staffed callback queue</Link></div></div>
      </div>
    </div>
  );
}
