"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import {
  AlertTriangle,
  Bell,
  CheckCircle,
  Clock,
  Loader2,
  MessageSquare,
  Phone,
  Send,
  User,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useClinicalPages,
  useMarkClinicalPageRead,
  useMessageChannels,
  useRespondToClinicalPage,
  useSendClinicalPage,
} from "@/hooks/queries/useCommunication";
import { useOmnichannelCallbacks } from "@/hooks/queries/useOmnichannel";
import { useAuthStore } from "@/hooks/useAuthStore";

type TabId = "messages" | "pages" | "calls";

const TAB_IDS: TabId[] = ["messages", "pages", "calls"];

function formatDateTime(value?: string | null) {
  if (!value) return "Not yet recorded";
  return new Date(value).toLocaleString();
}

function toParticipantsCount(participants?: string[] | string | null) {
  if (Array.isArray(participants)) return participants.length;
  if (typeof participants === "string" && participants.trim().length > 0) {
    return participants.split(",").map((item) => item.trim()).filter(Boolean).length;
  }
  return 0;
}

export default function CommunicationPage() {
  const searchParams = useSearchParams();
  const requestedTab = searchParams.get("tab");
  const [activeTab, setActiveTab] = useState<TabId>(
    TAB_IDS.includes(requestedTab as TabId) ? (requestedTab as TabId) : "messages",
  );

  useEffect(() => {
    if (TAB_IDS.includes(requestedTab as TabId)) {
      setActiveTab(requestedTab as TabId);
    }
  }, [requestedTab]);

  const tabs = [
    { id: "messages" as const, label: "Messages", icon: MessageSquare },
    { id: "pages" as const, label: "Pages", icon: Bell },
    { id: "calls" as const, label: "Calls", icon: Phone },
  ];

  return (
    <AppLayout>
      <PageShell title="Communication Hub" icon={<MessageSquare className="h-5 w-5" />}>
        <p className="mb-4 text-sm text-gray-500">
          Messaging, paging, and callback coordination for clinical and operational teams.
        </p>

        <div className="mb-6 flex max-w-md gap-1 rounded-lg bg-gray-100 p-1">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex flex-1 items-center justify-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                  activeTab === tab.id ? "bg-white text-gray-900 shadow-sm" : "text-gray-500 hover:text-gray-700"
                }`}
              >
                <Icon className="h-4 w-4" />
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

function MessagesTab() {
  const { data, isLoading } = useMessageChannels();
  const channels = data?.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h3 className="text-lg font-semibold">Secure Messages</h3>
          <p className="text-sm text-gray-500">Open the real messaging workspace for each active channel.</p>
        </div>
        <Link
          href="/communication/secure-messaging"
          className="inline-flex items-center gap-2 rounded-lg border border-impilo-200 px-4 py-2 text-sm font-medium text-impilo-600 transition-colors hover:bg-impilo-50"
        >
          <MessageSquare className="h-4 w-4" />
          Open inbox
        </Link>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center gap-2 py-8">
          <Loader2 className="h-5 w-5 animate-spin text-gray-400" />
          <span className="text-sm text-gray-500">Loading channels...</span>
        </div>
      ) : channels.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-gray-50 py-12 text-center">
          <MessageSquare className="mx-auto mb-3 h-10 w-10 text-gray-300" />
          <h4 className="text-sm font-medium text-gray-600">No message channels</h4>
          <p className="mt-1 text-xs text-gray-400">Create or receive a message to populate the clinical inbox.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {channels.map((channel) => {
            const participants = toParticipantsCount(channel.participants);
            const channelLabel = channel.name || `${channel.channel_type} channel`;
            return (
              <Link
                key={channel.id}
                href={`/communication/secure-messaging?channelId=${channel.id}`}
                className="flex items-center gap-3 rounded-lg border border-gray-200 bg-white p-4 transition-colors hover:border-gray-300"
              >
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-blue-100">
                  <User className="h-5 w-5 text-impilo-500" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-gray-900">{channelLabel}</p>
                  <p className="text-xs text-gray-500">
                    {channel.channel_type}
                    {participants > 0 ? ` - ${participants} participants` : " - ready"}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-xs text-gray-500">{formatDateTime(channel.last_message_at)}</p>
                  <span className="text-xs font-medium text-impilo-600">Open thread</span>
                </div>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}

function PagesTab() {
  const user = useAuthStore((state) => state.user);
  const { data, isLoading } = useClinicalPages();
  const sendPage = useSendClinicalPage();
  const markRead = useMarkClinicalPageRead();
  const respondToPage = useRespondToClinicalPage();
  const pages = useMemo(() => data?.data ?? [], [data?.data]);

  const [showSend, setShowSend] = useState(false);
  const [pageForm, setPageForm] = useState({
    recipientId: "",
    recipientName: "",
    message: "",
    urgency: "normal",
    callbackNumber: "",
  });

  const urgencyColors: Record<string, string> = {
    urgent: "bg-red-100 text-red-700",
    high: "bg-orange-100 text-orange-700",
    normal: "bg-impilo-100 text-impilo-600",
    low: "bg-gray-100 text-gray-600",
  };

  const outstandingPages = useMemo(
    () => pages.filter((page) => page.status !== "RESPONDED").length,
    [pages],
  );

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h3 className="text-lg font-semibold">Clinical Pages</h3>
          <p className="text-sm text-gray-500">
            {outstandingPages} page{outstandingPages === 1 ? "" : "s"} still need acknowledgement or response.
          </p>
        </div>
        <button
          type="button"
          aria-label="Open page composer"
          onClick={() => setShowSend((value) => !value)}
          className="inline-flex items-center gap-2 rounded-lg bg-impilo-500 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-impilo-600"
        >
          <Send className="h-4 w-4" />
          Send page
        </button>
      </div>

      {showSend ? (
        <div className="space-y-3 rounded-lg border-2 border-impilo-200 bg-white p-4">
          <div className="grid gap-3 md:grid-cols-2">
            <input
              type="text"
              placeholder="Recipient ID"
              value={pageForm.recipientId}
              onChange={(event) => setPageForm({ ...pageForm, recipientId: event.target.value })}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
            />
            <input
              type="text"
              placeholder="Recipient name"
              value={pageForm.recipientName}
              onChange={(event) => setPageForm({ ...pageForm, recipientName: event.target.value })}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
            />
          </div>
          <textarea
            placeholder="Page message..."
            value={pageForm.message}
            onChange={(event) => setPageForm({ ...pageForm, message: event.target.value })}
            rows={3}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
          />
          <div className="flex gap-3">
            <select
              value={pageForm.urgency}
              onChange={(event) => setPageForm({ ...pageForm, urgency: event.target.value })}
              className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
            >
              <option value="low">Low</option>
              <option value="normal">Normal</option>
              <option value="high">High</option>
              <option value="urgent">Urgent</option>
            </select>
            <input
              type="text"
              placeholder="Callback number"
              value={pageForm.callbackNumber}
              onChange={(event) => setPageForm({ ...pageForm, callbackNumber: event.target.value })}
              className="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm"
            />
          </div>
          <div className="flex justify-end gap-2">
            <button
              onClick={() => setShowSend(false)}
              className="px-4 py-2 text-sm text-gray-600 transition-colors hover:text-gray-800"
            >
              Cancel
            </button>
            <button
              type="button"
              aria-label="Submit clinical page"
              onClick={() =>
                sendPage.mutate(
                  {
                    senderId: user?.id ?? "system",
                    senderName: user?.displayName ?? "Unknown sender",
                    recipientId: pageForm.recipientId || pageForm.recipientName,
                    recipientName: pageForm.recipientName || pageForm.recipientId,
                    message: pageForm.message,
                    urgency: pageForm.urgency,
                    callbackNumber: pageForm.callbackNumber || undefined,
                  },
                  {
                    onSuccess: () => {
                      setShowSend(false);
                      setPageForm({
                        recipientId: "",
                        recipientName: "",
                        message: "",
                        urgency: "normal",
                        callbackNumber: "",
                      });
                    },
                  },
                )
              }
              disabled={!pageForm.message || (!pageForm.recipientId && !pageForm.recipientName) || sendPage.isPending}
              className="rounded-lg bg-impilo-500 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-600 disabled:opacity-50"
            >
              {sendPage.isPending ? "Sending..." : "Send page"}
            </button>
          </div>
        </div>
      ) : null}

      {isLoading ? (
        <div className="flex items-center justify-center gap-2 py-8">
          <Loader2 className="h-5 w-5 animate-spin text-gray-400" />
        </div>
      ) : pages.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-gray-50 py-12 text-center">
          <Bell className="mx-auto mb-3 h-10 w-10 text-gray-300" />
          <h4 className="text-sm font-medium text-gray-600">No pages</h4>
          <p className="mt-1 text-xs text-gray-400">Send a page to another provider to begin the loop.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {pages.map((page) => (
            <div
              key={page.id}
              className={`rounded-lg border bg-white p-4 transition-colors ${
                page.urgency === "urgent" ? "border-red-200" : "border-gray-200"
              }`}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-start gap-3">
                  <div className={`rounded p-1.5 ${page.urgency === "urgent" ? "bg-red-100" : "bg-gray-100"}`}>
                    <AlertTriangle className={`h-4 w-4 ${page.urgency === "urgent" ? "text-red-600" : "text-gray-500"}`} />
                  </div>
                  <div>
                    <p className="text-sm font-medium text-gray-900">
                      {page.sender_name
                        ? `${page.sender_name} -> ${page.recipient_name ?? page.recipient_id}`
                        : `To: ${page.recipient_name ?? page.recipient_id}`}
                    </p>
                    <p className="mt-1 text-sm text-gray-600">{page.message}</p>
                    <div className="mt-2 flex flex-wrap items-center gap-3 text-xs text-gray-400">
                      <span className="flex items-center gap-1">
                        <Clock className="h-3 w-3" />
                        {formatDateTime(page.sent_at)}
                      </span>
                      <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${urgencyColors[page.urgency]}`}>
                        {page.urgency}
                      </span>
                      {page.callback_number ? <span>Callback: {page.callback_number}</span> : null}
                    </div>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-xs font-medium text-gray-600">{page.status}</p>
                  <div className="mt-2 flex gap-2">
                    {page.status === "SENT" ? (
                      <button
                        onClick={() => markRead.mutate(page.id)}
                        disabled={markRead.isPending}
                        className="rounded border border-gray-200 px-2 py-1 text-xs font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
                      >
                        Mark read
                      </button>
                    ) : null}
                    {page.status !== "RESPONDED" ? (
                      <button
                        onClick={() => respondToPage.mutate(page.id)}
                        disabled={respondToPage.isPending}
                        className="rounded bg-green-600 px-2 py-1 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-50"
                      >
                        Responded
                      </button>
                    ) : (
                      <span className="inline-flex items-center gap-1 rounded bg-green-100 px-2 py-1 text-xs font-medium text-green-700">
                        <CheckCircle className="h-3 w-3" />
                        Loop closed
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function CallsTab() {
  const { data, isLoading } = useOmnichannelCallbacks();
  const callbacks = data?.data ?? [];
  const pendingCallbacks = callbacks.filter((callback) => callback.status === "PENDING");
  const completedCallbacks = callbacks.filter((callback) => callback.status === "COMPLETED");

  return (
    <div className="space-y-4">
      <div>
        <h3 className="text-lg font-semibold">Voice and video coordination</h3>
        <p className="text-sm text-gray-500">
          Calls are coordinated through telemedicine sessions and the callback queue.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <Link
          href="/telemedicine"
          className="rounded-lg border border-gray-200 bg-white p-6 text-center transition-colors hover:border-impilo-200"
        >
          <Phone className="mx-auto mb-3 h-10 w-10 text-impilo-400" />
          <h4 className="text-sm font-semibold text-gray-900">Telemedicine sessions</h4>
          <p className="mt-1 text-xs text-gray-500">Launch or join real audio and video consult workflows.</p>
          <span className="mt-4 inline-flex text-sm font-medium text-impilo-600">Open telemedicine</span>
        </Link>

        <Link
          href="/omnichannel?tab=callbacks"
          className="rounded-lg border border-gray-200 bg-white p-6 text-center transition-colors hover:border-amber-300"
        >
          <Phone className="mx-auto mb-3 h-10 w-10 text-amber-500" />
          <h4 className="text-sm font-semibold text-gray-900">Callback queue</h4>
          <p className="mt-1 text-xs text-gray-500">Manage missed calls and queued follow-up from other channels.</p>
          <span className="mt-4 inline-flex text-sm font-medium text-amber-700">Open callback queue</span>
        </Link>
      </div>

      <div className="rounded-lg border border-gray-200 bg-white p-4">
        <div className="flex items-center justify-between">
          <h4 className="text-sm font-medium text-gray-700">Current callback workload</h4>
          <Link href="/omnichannel?tab=callbacks" className="text-xs font-medium text-impilo-600 hover:underline">
            See all callbacks
          </Link>
        </div>

        {isLoading ? (
          <div className="mt-4 flex items-center gap-2">
            <Loader2 className="h-4 w-4 animate-spin text-gray-400" />
            <span className="text-sm text-gray-500">Loading callback queue...</span>
          </div>
        ) : (
          <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-3">
            <div className="rounded-lg bg-amber-50 p-3">
              <p className="text-xs text-amber-700">Pending callbacks</p>
              <p className="mt-1 text-2xl font-semibold text-amber-900">{pendingCallbacks.length}</p>
            </div>
            <div className="rounded-lg bg-green-50 p-3">
              <p className="text-xs text-green-700">Completed callbacks</p>
              <p className="mt-1 text-2xl font-semibold text-green-900">{completedCallbacks.length}</p>
            </div>
            <div className="rounded-lg bg-impilo-50 p-3">
              <p className="text-xs text-impilo-600">Total tracked calls</p>
              <p className="mt-1 text-2xl font-semibold text-impilo-800">{callbacks.length}</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
