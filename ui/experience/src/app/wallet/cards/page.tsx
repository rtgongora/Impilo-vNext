"use client";

/**
 * Card Management — view, activate, block, replace, and sync health data for cards.
 * Route: /wallet/cards
 *
 * Table of cards with actions, request new card form, and expandable card details.
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  ArrowLeft,
  CreditCard,
  Loader2,
  Lock,
  Plus,
  RefreshCw,
  Shield,
  Unlock,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useWalletByOwner,
  useCards,
  useIssueCard,
  useActivateCard,
  useBlockCard,
  useUnblockCard,
  useReplaceCard,
  useSyncHealthData,
} from "@/hooks/queries/useMusheWallet";
import { apiClient } from "@/lib/api-client";
import { useQuery } from "@tanstack/react-query";

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" ? (v as Record<string, unknown>) : {};
}

function readStr(r: Record<string, unknown>, ...keys: string[]) {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "string" && x.length > 0) return x;
  }
  return "";
}

function readNum(r: Record<string, unknown>, ...keys: string[]) {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "number") return x;
    if (typeof x === "string" && x.trim()) {
      const n = Number(x);
      if (!Number.isNaN(n)) return n;
    }
  }
  return 0;
}

function unwrapItems(v: unknown): unknown[] {
  if (!v) return [];
  if (Array.isArray(v)) return v;
  const r = v as Record<string, unknown>;
  if (Array.isArray(r.items)) return r.items;
  if (Array.isArray(r.content)) return r.content;
  if (Array.isArray(r.cards)) return r.cards;
  if (r.data != null) return unwrapItems(r.data);
  return [];
}

function statusBadge(status: string) {
  const s = status.toUpperCase();
  if (s === "ACTIVE") return "bg-green-100 text-green-800";
  if (s === "ISSUED") return "bg-blue-100 text-blue-800";
  if (s === "BLOCKED") return "bg-red-100 text-red-800";
  if (s === "EXPIRED") return "bg-gray-100 text-gray-600";
  if (s === "REPLACED") return "bg-amber-100 text-amber-800";
  return "bg-gray-100 text-gray-700";
}

const CARD_TYPES = ["DUAL", "PAYMENT_ONLY", "HEALTH_ONLY"] as const;
const CARD_NETWORKS = ["ZIMSWITCH", "VISA", "MASTERCARD"] as const;

export default function CardManagementPage() {
  const cpid = useAuthStore((s) => s.user?.id) ?? "";
  const [showNewCardForm, setShowNewCardForm] = useState(false);
  const [newCardType, setNewCardType] = useState("DUAL");
  const [newCardNetwork, setNewCardNetwork] = useState("ZIMSWITCH");
  const [expandedCardId, setExpandedCardId] = useState<string | null>(null);

  const walletQ = useWalletByOwner(cpid ? "PERSON" : null, cpid || null);
  const walletData = useMemo(() => asRecord((walletQ.data as Record<string, unknown>)?.data ?? walletQ.data), [walletQ.data]);
  const walletId = readStr(walletData, "walletId", "wallet_id", "id");

  const cardsQ = useCards(walletId || null);
  const cards = useMemo(() => {
    const raw = (cardsQ.data as Record<string, unknown>)?.data ?? cardsQ.data;
    return unwrapItems(raw).map(asRecord);
  }, [cardsQ.data]);

  const issueCard = useIssueCard();
  const activateCard = useActivateCard();
  const blockCard = useBlockCard();
  const unblockCard = useUnblockCard();
  const replaceCard = useReplaceCard();
  const syncHealth = useSyncHealthData();

  // Pending updates for expanded card
  const pendingUpdatesQ = useQuery({
    queryKey: ["mushe-card-pending-updates", expandedCardId],
    queryFn: () => apiClient.get<unknown>(`/internal/v1/cards/${expandedCardId}/pending-updates`),
    enabled: Boolean(expandedCardId),
  });
  const pendingUpdates = useMemo(() => {
    if (!pendingUpdatesQ.data) return [];
    const raw = (pendingUpdatesQ.data as Record<string, unknown>)?.data ?? pendingUpdatesQ.data;
    const r = raw as Record<string, unknown>;
    if (Array.isArray(r.pendingUpdates)) return (r.pendingUpdates as unknown[]).map(asRecord);
    return unwrapItems(raw).map(asRecord);
  }, [pendingUpdatesQ.data]);

  const submitNewCard = () => {
    if (!walletId) return;
    issueCard.mutate(
      { walletId, cardType: newCardType, cardNetwork: newCardNetwork },
      { onSuccess: () => setShowNewCardForm(false) },
    );
  };

  const handleActivate = (cardId: string) => {
    const pin = window.prompt("Set a 4-digit PIN for this card:");
    if (!pin || pin.length < 4) return;
    activateCard.mutate({ cardId, body: { pin } });
  };

  const handleBlock = (cardId: string) => {
    const reason = window.prompt("Reason for blocking (optional):", "USER_REQUEST");
    blockCard.mutate({ cardId, body: { reason: reason ?? "USER_REQUEST" } });
  };

  return (
    <AppLayout>
      <PageShell
        title="Card Management"
        subtitle="View and manage your Mushe smart cards"
        icon={<CreditCard className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link
            href="/wallet"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="h-4 w-4" /> Back to wallet
          </Link>
        </div>

        {!cpid && (
          <p className="text-sm text-amber-800 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
            Sign in to manage your cards.
          </p>
        )}

        {cpid && walletQ.isLoading && (
          <div className="flex items-center gap-2 text-gray-600 text-sm py-8">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading...
          </div>
        )}

        {cpid && !walletQ.isLoading && !walletId && (
          <p className="text-sm text-red-700 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
            No wallet found. Please set up your wallet first.
          </p>
        )}

        {cpid && walletId && (
          <div className="space-y-6">
            {/* Actions bar */}
            <div className="flex items-center justify-between">
              <h2 className="text-base font-semibold text-gray-900">Your cards</h2>
              <button
                type="button"
                onClick={() => setShowNewCardForm(!showNewCardForm)}
                className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700"
              >
                <Plus className="h-4 w-4" /> Request New Card
              </button>
            </div>

            {/* New card form */}
            {showNewCardForm && (
              <div className="rounded-xl border border-blue-200 bg-blue-50/40 p-4 space-y-3">
                <h3 className="text-sm font-semibold text-gray-900">Request a new card</h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Card type</label>
                    <select
                      className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                      value={newCardType}
                      onChange={(e) => setNewCardType(e.target.value)}
                    >
                      {CARD_TYPES.map((t) => (
                        <option key={t} value={t}>{t.replace(/_/g, " ")}</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Network</label>
                    <select
                      className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                      value={newCardNetwork}
                      onChange={(e) => setNewCardNetwork(e.target.value)}
                    >
                      {CARD_NETWORKS.map((n) => (
                        <option key={n} value={n}>{n}</option>
                      ))}
                    </select>
                  </div>
                </div>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => setShowNewCardForm(false)}
                    className="rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={submitNewCard}
                    disabled={issueCard.isPending}
                    className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
                  >
                    {issueCard.isPending && <Loader2 className="h-4 w-4 animate-spin inline mr-1" />}
                    Request Card
                  </button>
                </div>
                {issueCard.isError && (
                  <p className="text-xs text-red-600">Failed to request card.</p>
                )}
              </div>
            )}

            {/* Cards table */}
            {cardsQ.isLoading && (
              <div className="flex items-center gap-2 text-gray-600 text-sm py-8">
                <Loader2 className="h-5 w-5 animate-spin" /> Loading cards...
              </div>
            )}
            {cardsQ.isError && (
              <p className="text-sm text-red-700 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
                Could not load cards.
              </p>
            )}

            {!cardsQ.isLoading && !cardsQ.isError && cards.length === 0 && (
              <p className="text-sm text-gray-500">No cards issued yet. Request your first card above.</p>
            )}

            {!cardsQ.isLoading && !cardsQ.isError && cards.length > 0 && (
              <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-gray-50 text-left text-gray-600">
                      <th className="px-3 py-2">Card number</th>
                      <th className="px-3 py-2">Type</th>
                      <th className="px-3 py-2">Network</th>
                      <th className="px-3 py-2">Status</th>
                      <th className="px-3 py-2">Health data synced</th>
                      <th className="px-3 py-2 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {cards.map((card, i) => {
                      const cardId = readStr(card, "cardId", "card_id");
                      const status = readStr(card, "status") || "UNKNOWN";
                      const masked = readStr(card, "maskedCardNumber", "masked_card_number");
                      const cardType = readStr(card, "cardType", "card_type");
                      const network = readStr(card, "cardNetwork", "card_network");
                      const healthUpdated = readStr(card, "healthDataUpdatedAt", "health_data_updated_at");
                      const isExpanded = expandedCardId === cardId;
                      const statusUpper = status.toUpperCase();

                      return (
                        <tr key={cardId || String(i)} className="group">
                          <td className="px-3 py-2">
                            <button
                              type="button"
                              onClick={() => setExpandedCardId(isExpanded ? null : cardId)}
                              className="font-mono text-xs text-gray-900 hover:text-blue-700"
                            >
                              {masked || "****"}
                            </button>
                          </td>
                          <td className="px-3 py-2">{cardType.replace(/_/g, " ")}</td>
                          <td className="px-3 py-2">{network}</td>
                          <td className="px-3 py-2">
                            <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${statusBadge(status)}`}>
                              {status}
                            </span>
                          </td>
                          <td className="px-3 py-2 text-xs text-gray-600">
                            {healthUpdated ? new Date(healthUpdated).toLocaleDateString() : "Not synced"}
                          </td>
                          <td className="px-3 py-2 text-right">
                            <div className="flex items-center justify-end gap-1.5">
                              {statusUpper === "ISSUED" && cardId && (
                                <button
                                  type="button"
                                  onClick={() => handleActivate(cardId)}
                                  disabled={activateCard.isPending}
                                  className="inline-flex items-center gap-1 text-xs font-medium text-green-700 hover:text-green-900 disabled:opacity-40"
                                >
                                  <Shield className="h-3 w-3" /> Activate
                                </button>
                              )}
                              {statusUpper === "ACTIVE" && cardId && (
                                <>
                                  <button
                                    type="button"
                                    onClick={() => handleBlock(cardId)}
                                    disabled={blockCard.isPending}
                                    className="inline-flex items-center gap-1 text-xs font-medium text-red-700 hover:text-red-900 disabled:opacity-40"
                                  >
                                    <Lock className="h-3 w-3" /> Block
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => syncHealth.mutate({ cardId })}
                                    disabled={syncHealth.isPending}
                                    className="inline-flex items-center gap-1 text-xs font-medium text-blue-700 hover:text-blue-900 disabled:opacity-40"
                                  >
                                    <RefreshCw className="h-3 w-3" /> Sync
                                  </button>
                                </>
                              )}
                              {statusUpper === "BLOCKED" && cardId && (
                                <button
                                  type="button"
                                  onClick={() => unblockCard.mutate({ cardId })}
                                  disabled={unblockCard.isPending}
                                  className="inline-flex items-center gap-1 text-xs font-medium text-green-700 hover:text-green-900 disabled:opacity-40"
                                >
                                  <Unlock className="h-3 w-3" /> Unblock
                                </button>
                              )}
                              {(statusUpper === "ACTIVE" || statusUpper === "BLOCKED") && cardId && (
                                <button
                                  type="button"
                                  onClick={() => replaceCard.mutate({ cardId })}
                                  disabled={replaceCard.isPending}
                                  className="inline-flex items-center gap-1 text-xs font-medium text-amber-700 hover:text-amber-900 disabled:opacity-40"
                                >
                                  Replace
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {/* Expanded card detail */}
            {expandedCardId && (
              <div className="rounded-xl border border-gray-200 bg-white shadow-sm p-4 space-y-3">
                <h3 className="text-sm font-semibold text-gray-900">Card details</h3>
                {(() => {
                  const card = cards.find((c) => readStr(c, "cardId", "card_id") === expandedCardId);
                  if (!card) return <p className="text-sm text-gray-500">Card not found.</p>;
                  const pinTries = readNum(card, "pinTriesRemaining", "pin_tries_remaining");
                  const appletVersion = readStr(card, "healthAppletVersion", "health_applet_version");
                  const ipsVersion = readStr(card, "ipsVersion", "ips_version");
                  const ipsUpdated = readStr(card, "ipsUpdatedAt", "ips_updated_at");
                  const activatedAt = readStr(card, "activatedAt", "activated_at");
                  const blockedReason = readStr(card, "blockedReason", "blocked_reason");
                  const replacedBy = readStr(card, "replacedBy", "replaced_by");
                  return (
                    <div className="grid grid-cols-2 md:grid-cols-3 gap-3 text-xs">
                      <div className="rounded-lg bg-gray-50 p-2">
                        <div className="text-gray-500">PIN tries remaining</div>
                        <div className="font-medium text-gray-900">{pinTries}</div>
                      </div>
                      <div className="rounded-lg bg-gray-50 p-2">
                        <div className="text-gray-500">Health applet version</div>
                        <div className="font-medium text-gray-900">{appletVersion || "N/A"}</div>
                      </div>
                      <div className="rounded-lg bg-gray-50 p-2">
                        <div className="text-gray-500">IPS version</div>
                        <div className="font-medium text-gray-900">{ipsVersion || "N/A"}</div>
                      </div>
                      {ipsUpdated && (
                        <div className="rounded-lg bg-gray-50 p-2">
                          <div className="text-gray-500">IPS updated</div>
                          <div className="font-medium text-gray-900">{new Date(ipsUpdated).toLocaleString()}</div>
                        </div>
                      )}
                      {activatedAt && (
                        <div className="rounded-lg bg-gray-50 p-2">
                          <div className="text-gray-500">Activated</div>
                          <div className="font-medium text-gray-900">{new Date(activatedAt).toLocaleString()}</div>
                        </div>
                      )}
                      {blockedReason && (
                        <div className="rounded-lg bg-red-50 p-2">
                          <div className="text-red-600">Block reason</div>
                          <div className="font-medium text-red-800">{blockedReason}</div>
                        </div>
                      )}
                      {replacedBy && (
                        <div className="rounded-lg bg-amber-50 p-2">
                          <div className="text-amber-600">Replaced by</div>
                          <div className="font-mono font-medium text-amber-800">{replacedBy}</div>
                        </div>
                      )}
                    </div>
                  );
                })()}

                {/* Pending updates */}
                <div>
                  <h4 className="text-xs font-semibold text-gray-700 mb-2">Pending updates</h4>
                  {pendingUpdatesQ.isLoading && (
                    <div className="flex items-center gap-2 text-gray-600 text-xs">
                      <Loader2 className="h-3 w-3 animate-spin" /> Loading...
                    </div>
                  )}
                  {!pendingUpdatesQ.isLoading && pendingUpdates.length === 0 && (
                    <p className="text-xs text-gray-500">No pending updates.</p>
                  )}
                  {!pendingUpdatesQ.isLoading && pendingUpdates.length > 0 && (
                    <ul className="divide-y divide-gray-100 border border-gray-100 rounded-lg text-xs">
                      {pendingUpdates.map((upd, j) => (
                        <li key={readStr(upd, "queueId", "queue_id") || String(j)} className="px-3 py-2 flex justify-between">
                          <span className="text-gray-800">{readStr(upd, "updateType", "update_type")}</span>
                          <span className="text-gray-500">
                            {readStr(upd, "status")} - Attempt {readNum(upd, "attempts")}
                          </span>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>

                <button
                  type="button"
                  onClick={() => setExpandedCardId(null)}
                  className="text-xs font-medium text-gray-500 hover:text-gray-700"
                >
                  Close details
                </button>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
