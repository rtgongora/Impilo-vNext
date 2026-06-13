"use client";

/**
 * Provider Noticeboard — Full announcement management.
 * Route: /scheduling/noticeboard
 *
 * Lovable reference: ProviderNoticeboard with categories,
 * priorities, pinning, expiry, and acknowledgment.
 */

import { useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
  Megaphone, Bell, BookOpen, Calendar, AlertTriangle, Clock,
  Pin, Plus, CheckCircle, Loader2, ArrowLeft, X,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { FacilityWorkClusterRibbon } from "@/components/experience/FacilityWorkClusterRibbon";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const categoryIcons: Record<string, typeof Bell> = {
  general: Bell,
  policy: BookOpen,
  training: Calendar,
  emergency: AlertTriangle,
  shift: Clock,
};

const priorityColors: Record<string, string> = {
  low: "bg-gray-500",
  normal: "bg-primary",
  high: "bg-orange-500",
  urgent: "bg-red-600",
};

const categories = ["general", "policy", "training", "emergency", "shift"];
const priorities = ["low", "normal", "high", "urgent"];

interface Announcement {
  id: string;
  title: string;
  content: string;
  category: string;
  priority: string;
  is_pinned: boolean;
  requires_acknowledgment: boolean;
  published_by: string;
  published_at: string;
  expires_at: string | null;
}

export default function ProviderNoticeboardPage() {
  const searchParams = useSearchParams();
  const fromOrgAdmin = searchParams.get("from") === "organization-admin";
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState("all");
  const [showCreate, setShowCreate] = useState(false);
  const [selected, setSelected] = useState<Announcement | null>(null);

  const [form, setForm] = useState({
    title: "", content: "", category: "general", priority: "normal",
    isPinned: false, requiresAcknowledgment: false, expiresInDays: "",
  });

  const { data, isLoading } = useQuery<{ data: Announcement[]; stats: Record<string, number> }>({
    queryKey: ["announcements", activeTab],
    queryFn: () => {
      const params = activeTab !== "all" && activeTab !== "pinned"
        ? `?category=${activeTab}` : "";
      return apiClient.get(`/internal/v1/communication/announcements${params}`);
    },
  });

  const announcements = (data?.data ?? []).filter((a) => {
    if (activeTab === "pinned") return a.is_pinned;
    return true;
  });
  const stats = data?.stats ?? { total: 0, pinned: 0, urgent: 0 };

  const createMutation = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post("/internal/v1/communication/announcements", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["announcements"] });
      setShowCreate(false);
      setForm({ title: "", content: "", category: "general", priority: "normal", isPinned: false, requiresAcknowledgment: false, expiresInDays: "" });
    },
  });

  const acknowledgeMutation = useMutation({
    mutationFn: (id: string) =>
      apiClient.post(`/internal/v1/communication/announcements/${id}/acknowledge`, { userId: "current-user" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["announcements"] }),
  });

  return (
    <AppLayout>
      <PageShell title="Provider Noticeboard" icon={<Megaphone className="w-5 h-5" />}>
        <OrganizationPlaneContextBar />
        {fromOrgAdmin && (
          <div className="mb-4">
            <Link
              href="/organization-admin/staffing?from=organization-admin"
              className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              <ArrowLeft className="h-4 w-4" /> Back to staffing hub
            </Link>
          </div>
        )}
        <FacilityWorkClusterRibbon shiftExpected={false} />
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <Link href="/home" className="p-2 hover:bg-neutral-100 rounded-lg transition-colors">
              <ArrowLeft className="w-4 h-4 text-muted-foreground" />
            </Link>
            <p className="text-sm text-muted-foreground">Announcements & updates for clinical staff</p>
          </div>
          <button
            onClick={() => setShowCreate(true)}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-primary text-white rounded-lg hover:bg-primary-hover transition-colors"
          >
            <Plus className="w-4 h-4" />
            New Announcement
          </button>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-3 gap-4 mb-6">
          <div className="bg-card rounded-lg border border-border p-4 text-center">
            <p className="text-3xl font-bold text-foreground">{stats.total}</p>
            <p className="text-sm text-muted-foreground">Total</p>
          </div>
          <div className="bg-card rounded-lg border border-warning/35 p-4 text-center">
            <p className="text-3xl font-bold text-amber-600">{stats.pinned}</p>
            <p className="text-sm text-muted-foreground">Pinned</p>
          </div>
          <div className="bg-card rounded-lg border border-danger/28 p-4 text-center">
            <p className="text-3xl font-bold text-red-600">{stats.urgent}</p>
            <p className="text-sm text-muted-foreground">Urgent</p>
          </div>
        </div>

        {/* Category Tabs */}
        <div className="flex flex-wrap gap-1 bg-neutral-100 rounded-lg p-1 mb-6">
          {[{ id: "all", label: "All" }, { id: "pinned", label: "Pinned", icon: Pin }, ...categories.map((c) => ({ id: c, label: c.charAt(0).toUpperCase() + c.slice(1) }))].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-1 px-3 py-1.5 text-sm font-medium rounded-md transition-colors capitalize ${
                activeTab === tab.id ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Announcements List */}
        {isLoading ? (
          <div className="space-y-3">
            {[0, 1, 2].map((i) => (
              <div key={i} className="bg-card rounded-lg border border-border p-4 animate-pulse">
                <div className="h-16 bg-neutral-100 rounded" />
              </div>
            ))}
          </div>
        ) : announcements.length === 0 ? (
          <div className="text-center py-16 bg-background rounded-lg border border-border">
            <Megaphone className="w-12 h-12 text-muted-foreground mx-auto mb-4" />
            <h3 className="text-lg font-medium text-muted-foreground mb-1">No announcements</h3>
            <p className="text-sm text-muted-foreground">
              {activeTab === "all" ? "No announcements yet" : `No ${activeTab} announcements`}
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {announcements.map((a) => {
              const Icon = categoryIcons[a.category] || Bell;
              return (
                <button
                  key={a.id}
                  onClick={() => setSelected(a)}
                  className={`w-full text-left bg-card rounded-lg border p-4 transition-all hover:shadow-md ${
                    a.is_pinned ? "border-amber-300 bg-warning-soft/30" : a.priority === "urgent" ? "border-red-300" : "border-border"
                  }`}
                >
                  <div className="flex items-start gap-4">
                    <div className={`p-2 rounded-lg shrink-0 ${a.priority === "urgent" ? "bg-red-100" : "bg-blue-100"}`}>
                      <Icon className={`w-5 h-5 ${a.priority === "urgent" ? "text-red-600" : "text-primary"}`} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1 flex-wrap">
                        {a.is_pinned && <Pin className="w-4 h-4 text-amber-600" />}
                        <h3 className="font-semibold text-foreground truncate">{a.title}</h3>
                        <span className={`px-2 py-0.5 text-xs font-medium text-white rounded ${priorityColors[a.priority]}`}>
                          {a.priority}
                        </span>
                        <span className="px-2 py-0.5 text-xs font-medium text-muted-foreground bg-neutral-100 rounded capitalize">
                          {a.category}
                        </span>
                      </div>
                      <p className="text-sm text-muted-foreground line-clamp-2">{a.content}</p>
                      <div className="flex items-center gap-4 mt-2 text-xs text-muted-foreground">
                        <span className="flex items-center gap-1">
                          <Clock className="w-3 h-3" />
                          {timeAgo(a.published_at)}
                        </span>
                        {a.published_by && <span>by {a.published_by}</span>}
                        {a.requires_acknowledgment && (
                          <span className="flex items-center gap-1 text-orange-600">
                            <CheckCircle className="w-3 h-3" />
                            Requires acknowledgment
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                </button>
              );
            })}
          </div>
        )}

        {/* Create Announcement Modal */}
        {showCreate && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-card rounded-xl shadow-xl max-w-lg w-full mx-4 p-6">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-lg font-semibold">Create Announcement</h3>
                <button onClick={() => setShowCreate(false)} className="p-1 hover:bg-neutral-100 rounded"><X className="w-5 h-5" /></button>
              </div>
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-foreground mb-1">Title *</label>
                  <input type="text" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })}
                    placeholder="Announcement title" className="w-full px-3 py-2 border border-border rounded-lg text-sm" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-foreground mb-1">Content *</label>
                  <textarea value={form.content} onChange={(e) => setForm({ ...form, content: e.target.value })}
                    placeholder="Announcement content..." rows={4} className="w-full px-3 py-2 border border-border rounded-lg text-sm" />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-foreground mb-1">Category</label>
                    <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}
                      className="w-full px-3 py-2 border border-border rounded-lg text-sm capitalize">
                      {categories.map((c) => <option key={c} value={c} className="capitalize">{c}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-foreground mb-1">Priority</label>
                    <select value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value })}
                      className="w-full px-3 py-2 border border-border rounded-lg text-sm capitalize">
                      {priorities.map((p) => <option key={p} value={p} className="capitalize">{p}</option>)}
                    </select>
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-foreground mb-1">Expires In</label>
                  <select value={form.expiresInDays} onChange={(e) => setForm({ ...form, expiresInDays: e.target.value })}
                    className="w-full px-3 py-2 border border-border rounded-lg text-sm">
                    <option value="">Never expires</option>
                    <option value="1">1 day</option>
                    <option value="7">1 week</option>
                    <option value="14">2 weeks</option>
                    <option value="30">1 month</option>
                  </select>
                </div>
                <div className="flex items-center gap-6">
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input type="checkbox" checked={form.isPinned} onChange={(e) => setForm({ ...form, isPinned: e.target.checked })} className="rounded" />
                    <span className="text-sm">Pin to top</span>
                  </label>
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input type="checkbox" checked={form.requiresAcknowledgment} onChange={(e) => setForm({ ...form, requiresAcknowledgment: e.target.checked })} className="rounded" />
                    <span className="text-sm">Require acknowledgment</span>
                  </label>
                </div>
              </div>
              <div className="flex justify-end gap-3 mt-6">
                <button onClick={() => setShowCreate(false)} className="px-4 py-2 text-sm text-muted-foreground hover:text-foreground">Cancel</button>
                <button
                  onClick={() => createMutation.mutate({
                    title: form.title, content: form.content, category: form.category,
                    priority: form.priority, isPinned: form.isPinned,
                    requiresAcknowledgment: form.requiresAcknowledgment,
                    expiresInDays: form.expiresInDays ? parseInt(form.expiresInDays) : null,
                  })}
                  disabled={!form.title || !form.content || createMutation.isPending}
                  className="px-4 py-2 text-sm font-medium bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-50"
                >
                  {createMutation.isPending ? (
                    <span className="flex items-center gap-2"><Loader2 className="w-4 h-4 animate-spin" />Publishing...</span>
                  ) : "Publish"}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Detail Modal */}
        {selected && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-card rounded-xl shadow-xl max-w-lg w-full mx-4 p-6">
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                  {selected.is_pinned && <Pin className="w-4 h-4 text-amber-600" />}
                  <span className={`px-2 py-0.5 text-xs font-medium text-white rounded ${priorityColors[selected.priority]}`}>
                    {selected.priority}
                  </span>
                  <span className="px-2 py-0.5 text-xs font-medium text-muted-foreground bg-neutral-100 rounded capitalize">
                    {selected.category}
                  </span>
                </div>
                <button onClick={() => setSelected(null)} className="p-1 hover:bg-neutral-100 rounded"><X className="w-5 h-5" /></button>
              </div>
              <h3 className="text-lg font-semibold text-foreground mb-1">{selected.title}</h3>
              <p className="text-sm text-muted-foreground mb-4">
                Published {timeAgo(selected.published_at)} {selected.published_by && `by ${selected.published_by}`}
              </p>
              <p className="text-sm text-foreground whitespace-pre-wrap mb-6">{selected.content}</p>
              {selected.requires_acknowledgment && (
                <button
                  onClick={() => { acknowledgeMutation.mutate(selected.id); setSelected(null); }}
                  className="w-full flex items-center justify-center gap-2 px-4 py-3 text-sm font-medium bg-primary text-white rounded-lg hover:bg-primary-hover transition-colors"
                >
                  <CheckCircle className="w-4 h-4" />
                  Acknowledge
                </button>
              )}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function timeAgo(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  if (diffMins < 60) return `${diffMins}m ago`;
  const diffHours = Math.floor(diffMins / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 7) return `${diffDays}d ago`;
  return date.toLocaleDateString();
}
