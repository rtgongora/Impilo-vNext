"use client";

/**
 * Facility announcements — khuluma channels & broadcast (R4/G13).
 * Route: /communication/announcements (auth)
 *
 * Discover the announcement channels for a facility (or programme) scope, create one, and
 * broadcast an announcement into it. Khuluma enforces the OWNER/MODERATOR broadcast role and
 * fans the broadcast out to connected members in realtime; this page surfaces the previously
 * un-exposed broadcast path.
 */

import { useState } from "react";
import { Loader2, Megaphone, Plus, Send } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useDiscoverChannels,
  useCreateChannel,
  useBroadcastChannel,
  useChannelMessages,
  type ChannelView,
} from "@/hooks/queries/useKhulumaChannels";

const SCOPE_TYPES = ["FACILITY", "PROGRAMME", "COMMUNITY"];

function fmt(value?: unknown) {
  const s = typeof value === "string" ? value : "";
  return s ? new Date(s).toLocaleString() : "";
}

export default function AnnouncementsPage() {
  const [scopeType, setScopeType] = useState(SCOPE_TYPES[0]);
  const [scopeRefInput, setScopeRefInput] = useState("");
  const [scopeRef, setScopeRef] = useState("");
  const [selected, setSelected] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [newTitle, setNewTitle] = useState("");

  const channelsQ = useDiscoverChannels(scopeRef ? scopeType : undefined, scopeRef || undefined);
  const channels = channelsQ.data ?? [];
  const createChannel = useCreateChannel();
  const broadcast = useBroadcastChannel();
  const messagesQ = useChannelMessages(selected ?? undefined);
  const messages = messagesQ.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Facility announcements">
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Discover the announcement channels for a scope and broadcast to their members. Broadcasting
            is restricted to channel owners/moderators (enforced by Khuluma).
          </p>

          {/* Scope selector */}
          <div className="flex flex-wrap items-end gap-2 rounded-lg border border-border bg-card p-4">
            <label className="text-xs text-muted-foreground">
              Scope
              <select
                value={scopeType}
                onChange={(e) => setScopeType(e.target.value)}
                className="mt-0.5 block rounded border border-border bg-card px-2 py-1.5 text-sm"
              >
                {SCOPE_TYPES.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </label>
            <label className="text-xs text-muted-foreground">
              Scope ref (facility/programme id)
              <input
                value={scopeRefInput}
                onChange={(e) => setScopeRefInput(e.target.value)}
                placeholder="UUID"
                className="mt-0.5 block w-72 rounded border border-border px-2 py-1.5 text-sm"
              />
            </label>
            <button
              type="button"
              disabled={!scopeRefInput.trim()}
              onClick={() => { setScopeRef(scopeRefInput.trim()); setSelected(null); }}
              className="rounded-lg border border-border px-3 py-1.5 text-sm hover:bg-background disabled:opacity-50"
            >
              Discover
            </button>
            <button
              type="button"
              disabled={!scopeRef}
              onClick={() => setShowCreate((s) => !s)}
              className="inline-flex items-center gap-1 rounded-lg border border-border px-3 py-1.5 text-sm hover:bg-background disabled:opacity-50"
            >
              <Plus className="h-3.5 w-3.5" /> New channel
            </button>
          </div>

          {showCreate && scopeRef && (
            <div className="flex flex-wrap items-end gap-2 rounded-lg border border-border/60 bg-background p-3">
              <input
                value={newTitle}
                onChange={(e) => setNewTitle(e.target.value)}
                placeholder="Channel title"
                className="w-64 rounded border border-border px-2 py-1.5 text-sm"
              />
              <button
                type="button"
                disabled={createChannel.isPending || !newTitle.trim()}
                onClick={() =>
                  createChannel.mutate(
                    { conversationType: "CHANNEL", scopeType, scopeRef, title: newTitle.trim() },
                    { onSuccess: () => { setShowCreate(false); setNewTitle(""); } },
                  )
                }
                className="rounded-lg bg-blue-600 px-3 py-1.5 text-sm text-white hover:bg-blue-700 disabled:opacity-50"
              >
                Create
              </button>
            </div>
          )}

          {scopeRef && (
            <div className="grid gap-4 md:grid-cols-[1fr_2fr]">
              {/* Channel list */}
              <div className="rounded-lg border border-border bg-card">
                <div className="flex items-center gap-2 border-b border-border px-3 py-2 text-sm font-medium">
                  <Megaphone className="h-4 w-4 text-primary" /> Channels
                </div>
                {channelsQ.isLoading ? (
                  <div className="flex items-center gap-2 p-3 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
                ) : channels.length === 0 ? (
                  <p className="p-3 text-sm text-muted-foreground">No channels for this scope yet.</p>
                ) : (
                  <ul className="divide-y divide-border">
                    {channels.map((c: ChannelView) => (
                      <li key={c.conversationId}>
                        <button
                          type="button"
                          onClick={() => setSelected(c.conversationId)}
                          className={`w-full px-3 py-2 text-left text-sm hover:bg-background ${selected === c.conversationId ? "bg-background font-medium" : ""}`}
                        >
                          {c.title || c.conversationId}
                          <span className="block text-[10px] text-muted-foreground">{c.conversationType} · {c.status}</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              {/* Broadcast + messages */}
              <div className="rounded-lg border border-border bg-card">
                {selected ? (
                  <>
                    <div className="space-y-2 border-b border-border p-3">
                      <textarea
                        value={draft}
                        onChange={(e) => setDraft(e.target.value)}
                        rows={2}
                        placeholder="Announcement to broadcast…"
                        className="w-full rounded border border-border px-2 py-1.5 text-sm"
                      />
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          disabled={broadcast.isPending || !draft.trim()}
                          onClick={() =>
                            broadcast.mutate(
                              { conversationId: selected, body: draft.trim() },
                              { onSuccess: () => setDraft("") },
                            )
                          }
                          className="inline-flex items-center gap-1 rounded-lg bg-blue-600 px-3 py-1.5 text-sm text-white hover:bg-blue-700 disabled:opacity-50"
                        >
                          <Send className="h-3.5 w-3.5" /> Broadcast
                        </button>
                        {broadcast.isError && <span className="text-xs text-red-600">Not permitted or channel unavailable.</span>}
                      </div>
                    </div>
                    <div className="p-3">
                      {messagesQ.isLoading ? (
                        <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
                      ) : messages.length === 0 ? (
                        <p className="text-sm text-muted-foreground">No messages in this channel yet.</p>
                      ) : (
                        <ul className="space-y-2">
                          {messages.map((m, i) => (
                            <li key={String(m.messageId ?? i)} className="rounded-md border border-border/60 p-2 text-sm">
                              <p className="text-foreground">{String(m.body ?? "")}</p>
                              <p className="text-[10px] text-muted-foreground">
                                {String(m.contentType ?? "")} · {fmt(m.createdAt ?? m.created_at)}
                              </p>
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                  </>
                ) : (
                  <p className="p-3 text-sm text-muted-foreground">Select a channel to broadcast.</p>
                )}
              </div>
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
