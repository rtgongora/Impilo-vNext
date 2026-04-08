"use client";

/**
 * Communication Hub — Messages, Pages, and Calls for clinical teams.
 * Route: /communication
 *
 * Lovable reference: CommunicationHub with 3 tabs.
 */

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import {
  MessageSquare, Bell, Phone, Send, Loader2,
  Clock, AlertTriangle, CheckCircle, User,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type TabId = "messages" | "pages" | "calls";

export default function CommunicationPage() {
  const searchParams = useSearchParams();
  const defaultTab = (searchParams.get("tab") as TabId) || "messages";
  const [activeTab, setActiveTab] = useState<TabId>(defaultTab);

  const tabs: { id: TabId; label: string; icon: typeof MessageSquare }[] = [
    { id: "messages", label: "Messages", icon: MessageSquare },
    { id: "pages", label: "Pages", icon: Bell },
    { id: "calls", label: "Calls", icon: Phone },
  ];

  return (
    <AppLayout>
      <PageShell title="Communication Hub" icon={<MessageSquare className="w-5 h-5" />}>
        <p className="text-sm text-gray-500 mb-4">Messaging, paging, and voice calls for clinical teams</p>

        {/* Tab bar */}
        <div className="flex gap-1 bg-gray-100 rounded-lg p-1 mb-6 max-w-md">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex-1 flex items-center justify-center gap-2 px-3 py-2 text-sm font-medium rounded-md transition-colors ${
                  activeTab === tab.id ? "bg-white text-gray-900 shadow-sm" : "text-gray-500 hover:text-gray-700"
                }`}
              >
                <Icon className="w-4 h-4" />
                {tab.label}
              </button>
            );
          })}
        </div>

        {activeTab === "messages" && <MessagesTab />}
        {activeTab === "pages" && <PagesTab />}
        {activeTab === "calls" && <CallsTab />}
      </PageShell>
    </AppLayout>
  );
}

// ── Messages Tab ──────────────────────────────────────────────────

function MessagesTab() {
  const { data, isLoading } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["message-channels"],
    queryFn: () => apiClient.get("/internal/v1/communication/messages/channels"),
  });
  const channels = data?.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">Secure Messages</h3>
      </div>

      {isLoading ? (
        <div className="flex items-center gap-2 py-8 justify-center">
          <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
          <span className="text-sm text-gray-500">Loading channels...</span>
        </div>
      ) : channels.length === 0 ? (
        <div className="text-center py-12 bg-gray-50 rounded-lg border border-gray-200">
          <MessageSquare className="w-10 h-10 text-gray-300 mx-auto mb-3" />
          <h4 className="text-sm font-medium text-gray-600">No message channels</h4>
          <p className="text-xs text-gray-400 mt-1">Start a conversation to see it here</p>
        </div>
      ) : (
        <div className="space-y-2">
          {channels.map((ch) => (
            <div key={ch.id as string}
              className="flex items-center gap-3 p-4 bg-white rounded-lg border border-gray-200 hover:border-gray-300 cursor-pointer transition-colors"
            >
              <div className="w-10 h-10 rounded-full bg-blue-100 flex items-center justify-center">
                <User className="w-5 h-5 text-blue-600" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-900 truncate">{ch.name as string || "Direct Message"}</p>
                <p className="text-xs text-gray-500">{ch.channel_type as string} channel</p>
              </div>
              {typeof ch.last_message_at === "string" && (
                <span className="text-xs text-gray-400">
                  {new Date(ch.last_message_at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                </span>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── Pages Tab ─────────────────────────────────────────────────────

function PagesTab() {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["clinical-pages"],
    queryFn: () => apiClient.get("/internal/v1/communication/pages"),
  });
  const pages = data?.data ?? [];

  const [showSend, setShowSend] = useState(false);
  const [pageForm, setPageForm] = useState({ recipientName: "", message: "", urgency: "normal", callbackNumber: "" });

  const sendPage = useMutation({
    mutationFn: (body: Record<string, string>) =>
      apiClient.post("/internal/v1/communication/pages", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["clinical-pages"] });
      setShowSend(false);
      setPageForm({ recipientName: "", message: "", urgency: "normal", callbackNumber: "" });
    },
  });

  const urgencyColors: Record<string, string> = {
    urgent: "bg-red-100 text-red-700",
    high: "bg-orange-100 text-orange-700",
    normal: "bg-blue-100 text-blue-700",
    low: "bg-gray-100 text-gray-600",
  };

  const statusIcons: Record<string, typeof Bell> = {
    SENT: Clock,
    READ: CheckCircle,
    RESPONDED: CheckCircle,
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">Clinical Pages</h3>
        <button
          onClick={() => setShowSend(!showSend)}
          className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Send className="w-4 h-4" />
          Send Page
        </button>
      </div>

      {showSend && (
        <div className="bg-white rounded-lg border-2 border-blue-200 p-4 space-y-3">
          <input
            type="text"
            placeholder="Recipient name"
            value={pageForm.recipientName}
            onChange={(e) => setPageForm({ ...pageForm, recipientName: e.target.value })}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
          <textarea
            placeholder="Page message..."
            value={pageForm.message}
            onChange={(e) => setPageForm({ ...pageForm, message: e.target.value })}
            rows={3}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
          <div className="flex gap-3">
            <select
              value={pageForm.urgency}
              onChange={(e) => setPageForm({ ...pageForm, urgency: e.target.value })}
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
            >
              <option value="low">Low</option>
              <option value="normal">Normal</option>
              <option value="high">High</option>
              <option value="urgent">Urgent</option>
            </select>
            <input
              type="text"
              placeholder="Callback number (optional)"
              value={pageForm.callbackNumber}
              onChange={(e) => setPageForm({ ...pageForm, callbackNumber: e.target.value })}
              className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm"
            />
          </div>
          <div className="flex justify-end gap-2">
            <button onClick={() => setShowSend(false)} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancel</button>
            <button
              onClick={() => sendPage.mutate({
                recipientId: pageForm.recipientName,
                recipientName: pageForm.recipientName,
                message: pageForm.message,
                urgency: pageForm.urgency,
                callbackNumber: pageForm.callbackNumber,
              })}
              disabled={!pageForm.recipientName || !pageForm.message || sendPage.isPending}
              className="px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
            >
              {sendPage.isPending ? "Sending..." : "Send Page"}
            </button>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="flex items-center gap-2 py-8 justify-center">
          <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
        </div>
      ) : pages.length === 0 ? (
        <div className="text-center py-12 bg-gray-50 rounded-lg border border-gray-200">
          <Bell className="w-10 h-10 text-gray-300 mx-auto mb-3" />
          <h4 className="text-sm font-medium text-gray-600">No pages</h4>
          <p className="text-xs text-gray-400 mt-1">Send a page to another provider</p>
        </div>
      ) : (
        <div className="space-y-2">
          {pages.map((page) => {
            const StatusIcon = statusIcons[(page.status as string) || "SENT"] || Clock;
            return (
              <div key={page.id as string}
                className={`p-4 bg-white rounded-lg border transition-colors ${
                  page.urgency === "urgent" ? "border-red-200" : "border-gray-200"
                }`}
              >
                <div className="flex items-start justify-between">
                  <div className="flex items-start gap-3">
                    <div className={`p-1.5 rounded ${page.urgency === "urgent" ? "bg-red-100" : "bg-gray-100"}`}>
                      <AlertTriangle className={`w-4 h-4 ${page.urgency === "urgent" ? "text-red-600" : "text-gray-500"}`} />
                    </div>
                    <div>
                      <p className="text-sm font-medium text-gray-900">To: {page.recipient_name as string}</p>
                      <p className="text-sm text-gray-600 mt-1">{page.message as string}</p>
                      <div className="flex items-center gap-3 mt-2 text-xs text-gray-400">
                        <span className="flex items-center gap-1">
                          <Clock className="w-3 h-3" />
                          {page.sent_at ? new Date(page.sent_at as string).toLocaleString() : "—"}
                        </span>
                        <span className={`px-1.5 py-0.5 rounded text-xs font-medium ${urgencyColors[(page.urgency as string) || "normal"]}`}>
                          {page.urgency as string}
                        </span>
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-1">
                    <StatusIcon className={`w-4 h-4 ${page.status === "RESPONDED" ? "text-green-500" : "text-gray-400"}`} />
                    <span className="text-xs text-gray-500">{page.status as string}</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

// ── Calls Tab ─────────────────────────────────────────────────────

function CallsTab() {
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">Voice & Video Calls</h3>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-6 text-center">
          <Phone className="w-10 h-10 text-green-500 mx-auto mb-3" />
          <h4 className="text-sm font-semibold text-gray-900">Voice Call</h4>
          <p className="text-xs text-gray-500 mt-1 mb-4">Audio-only call to a colleague</p>
          <button className="px-4 py-2 text-sm font-medium bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors">
            Start Voice Call
          </button>
        </div>

        <div className="bg-white rounded-lg border border-gray-200 p-6 text-center">
          <Phone className="w-10 h-10 text-blue-500 mx-auto mb-3" />
          <h4 className="text-sm font-semibold text-gray-900">Video Call</h4>
          <p className="text-xs text-gray-500 mt-1 mb-4">Face-to-face video consultation</p>
          <button className="px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors">
            Start Video Call
          </button>
        </div>
      </div>

      <div className="bg-gray-50 rounded-lg border border-gray-200 p-4">
        <h4 className="text-sm font-medium text-gray-700 mb-2">Recent Calls</h4>
        <p className="text-sm text-gray-400">No recent calls</p>
      </div>
    </div>
  );
}
