"use client";

import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { AlertCircle, Check, CheckCheck, Loader2, MessageSquare, Search, Send } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useChannelMessages,
  useMessageChannels,
  useSendClinicalMessage,
} from "@/hooks/queries/useCommunication";
import { useAuthStore } from "@/hooks/useAuthStore";

function formatDateTime(value?: string | null) {
  if (!value) return "No activity yet";
  return new Date(value).toLocaleString();
}

export default function SecureMessagingPage() {
  const searchParams = useSearchParams();
  const requestedChannelId = searchParams.get("channelId");
  const user = useAuthStore((state) => state.user);
  const { data: channelsData, isLoading: channelsLoading } = useMessageChannels();
  const channels = channelsData?.data ?? [];
  const [selectedChannelId, setSelectedChannelId] = useState<string | null>(requestedChannelId);
  const [draftMessage, setDraftMessage] = useState("");
  const [searchQuery, setSearchQuery] = useState("");

  useEffect(() => {
    if (requestedChannelId) {
      setSelectedChannelId(requestedChannelId);
      return;
    }
    if (!selectedChannelId && channels.length > 0) {
      setSelectedChannelId(channels[0].id);
    }
  }, [channels, requestedChannelId, selectedChannelId]);

  const { data: messagesData, isLoading: messagesLoading } = useChannelMessages(selectedChannelId);
  const sendMessage = useSendClinicalMessage();

  const filteredChannels = useMemo(() => {
    if (!searchQuery.trim()) return channels;
    const query = searchQuery.toLowerCase();
    return channels.filter((channel) => {
      const haystack = `${channel.name ?? ""} ${channel.channel_type}`.toLowerCase();
      return haystack.includes(query);
    });
  }, [channels, searchQuery]);

  const selectedChannel = channels.find((channel) => channel.id === selectedChannelId) ?? null;
  const orderedMessages = useMemo(() => {
    const source = messagesData?.data ?? [];
    return [...source].sort((left, right) => {
      const leftTime = left.created_at ? new Date(left.created_at).getTime() : 0;
      const rightTime = right.created_at ? new Date(right.created_at).getTime() : 0;
      return leftTime - rightTime;
    });
  }, [messagesData]);

  const handleSend = () => {
    if (!selectedChannelId || !draftMessage.trim()) return;
    sendMessage.mutate(
      {
        channelId: selectedChannelId,
        senderId: user?.id ?? "system",
        senderName: user?.displayName ?? "Unknown sender",
        content: draftMessage.trim(),
        messageType: "TEXT",
      },
      {
        onSuccess: () => setDraftMessage(""),
      },
    );
  };

  return (
    <AppLayout>
      <PageShell title="Secure Messaging" subtitle="Encrypted clinical messaging backed by live message channels">
        {channelsLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading channels...</span>
          </div>
        ) : (
          <div className="overflow-hidden rounded-lg border border-gray-200 bg-white" style={{ height: "calc(100vh - 220px)" }}>
            <div className="flex h-full">
              <div className="flex w-80 shrink-0 flex-col border-r border-gray-200">
                <div className="border-b border-gray-200 p-3">
                  <div className="mb-2 flex items-center justify-between">
                    <h3 className="text-sm font-medium text-gray-900">Message channels</h3>
                    <span className="text-xs text-gray-400">{channels.length}</span>
                  </div>
                  <div className="relative">
                    <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
                    <input
                      type="text"
                      value={searchQuery}
                      onChange={(event) => setSearchQuery(event.target.value)}
                      className="w-full rounded-lg border border-gray-200 py-2 pl-9 pr-3 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                      placeholder="Search channels..."
                    />
                  </div>
                </div>
                <div className="flex-1 overflow-y-auto">
                  {filteredChannels.length === 0 ? (
                    <div className="px-4 py-10 text-center text-sm text-gray-500">No channels match this search.</div>
                  ) : (
                    filteredChannels.map((channel) => {
                      const active = channel.id === selectedChannelId;
                      return (
                        <button
                          key={channel.id}
                          onClick={() => setSelectedChannelId(channel.id)}
                          className={`w-full border-b border-gray-50 px-3 py-3 text-left transition-colors hover:bg-gray-50 ${
                            active ? "bg-impilo-50" : ""
                          }`}
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0 flex-1">
                              <p className="truncate text-sm font-medium text-gray-900">
                                {channel.name || `${channel.channel_type} channel`}
                              </p>
                              <p className="text-xs text-gray-400">{channel.channel_type}</p>
                              <p className="mt-1 truncate text-xs text-gray-500">{formatDateTime(channel.last_message_at)}</p>
                            </div>
                            {active ? <span className="text-xs font-medium text-impilo-600">Open</span> : null}
                          </div>
                        </button>
                      );
                    })
                  )}
                </div>
              </div>

              <div className="flex min-w-0 flex-1 flex-col">
                {selectedChannel ? (
                  <>
                    <div className="flex items-center justify-between border-b border-gray-200 px-5 py-3">
                      <div>
                        <p className="text-sm font-medium text-gray-900">{selectedChannel.name || `${selectedChannel.channel_type} channel`}</p>
                        <p className="text-[10px] text-gray-400">{selectedChannel.channel_type} - {formatDateTime(selectedChannel.last_message_at)}</p>
                      </div>
                      <div className="flex items-center gap-1 text-[10px] text-green-600">
                        <AlertCircle className="h-3 w-3" /> End-to-end encrypted
                      </div>
                    </div>

                    <div className="flex-1 overflow-y-auto p-5">
                      {messagesLoading ? (
                        <div className="flex items-center justify-center py-12">
                          <Loader2 className="h-5 w-5 animate-spin text-gray-400" />
                          <span className="ml-2 text-sm text-gray-500">Loading thread...</span>
                        </div>
                      ) : orderedMessages.length === 0 ? (
                        <div className="rounded-lg border border-dashed border-gray-200 bg-gray-50 p-6 text-center">
                          <MessageSquare className="mx-auto mb-3 h-8 w-8 text-gray-300" />
                          <p className="text-sm font-medium text-gray-600">No messages in this channel yet</p>
                          <p className="mt-1 text-xs text-gray-400">Send the first message to begin the thread.</p>
                        </div>
                      ) : (
                        <div className="space-y-4">
                          {orderedMessages.map((message) => {
                            const isOwn = message.sender_id === user?.id;
                            return (
                              <div key={message.id} className={`flex ${isOwn ? "justify-end" : "justify-start"}`}>
                                <div className={`max-w-md ${isOwn ? "order-2" : ""}`}>
                                  <div className={`rounded-lg px-4 py-2.5 ${isOwn ? "bg-impilo-500 text-white" : "bg-gray-100 text-gray-800"}`}>
                                    <p className="mb-1 text-[11px] font-medium opacity-80">{message.sender_name || message.sender_id}</p>
                                    <p className="text-sm leading-relaxed">{message.content}</p>
                                  </div>
                                  <div className={`mt-1 flex items-center gap-1 ${isOwn ? "justify-end" : ""}`}>
                                    <span className="text-[10px] text-gray-400">{formatDateTime(message.created_at)}</span>
                                    {isOwn ? (
                                      message.is_read ? <CheckCheck className="h-3 w-3 text-impilo-400" /> : <Check className="h-3 w-3 text-gray-400" />
                                    ) : null}
                                  </div>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>

                    <div className="border-t border-gray-200 px-5 py-3">
                      <div className="flex items-center gap-2">
                        <input
                          type="text"
                          value={draftMessage}
                          onChange={(event) => setDraftMessage(event.target.value)}
                          className="flex-1 rounded-lg border border-gray-200 px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                          placeholder="Type a message..."
                          onKeyDown={(event) => {
                            if (event.key === "Enter" && draftMessage.trim()) {
                              handleSend();
                            }
                          }}
                        />
                        <button
                          onClick={handleSend}
                          disabled={!draftMessage.trim() || sendMessage.isPending}
                          aria-label="Send message"
                          className="rounded-lg bg-impilo-500 p-2.5 text-white transition-colors hover:bg-impilo-600 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          <Send className="h-4 w-4" />
                        </button>
                      </div>
                    </div>
                  </>
                ) : (
                  <div className="flex flex-1 items-center justify-center p-8 text-center">
                    <div>
                      <MessageSquare className="mx-auto mb-3 h-10 w-10 text-gray-300" />
                      <p className="text-sm font-medium text-gray-600">Select a channel to open the thread</p>
                      <p className="mt-1 text-xs text-gray-400">This page only shows live message channels already available to the signed-in user.</p>
                    </div>
                  </div>
                )}
              </div>

              {selectedChannel ? (
                <div className="hidden w-64 border-l border-gray-200 p-4 lg:block">
                  <div className="mb-4 text-center">
                    <div className="mx-auto mb-2 flex h-16 w-16 items-center justify-center rounded-full bg-impilo-100 text-xl font-semibold text-impilo-600">
                      {(selectedChannel.name || selectedChannel.channel_type).slice(0, 2).toUpperCase()}
                    </div>
                    <p className="font-medium text-gray-900">{selectedChannel.name || `${selectedChannel.channel_type} channel`}</p>
                    <p className="text-xs text-gray-500">Last activity: {formatDateTime(selectedChannel.last_message_at)}</p>
                  </div>
                  <div className="space-y-3 text-xs text-gray-500">
                    <div>
                      <p className="font-medium text-gray-700">Messages in thread</p>
                      <p className="mt-1">{orderedMessages.length}</p>
                    </div>
                    <div>
                      <p className="font-medium text-gray-700">Delivery mode</p>
                      <p className="mt-1">Encrypted staff messaging</p>
                    </div>
                    <div>
                      <p className="font-medium text-gray-700">Available actions</p>
                      <p className="mt-1">Review, send, and continue from communication or chart context.</p>
                    </div>
                  </div>
                </div>
              ) : null}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
