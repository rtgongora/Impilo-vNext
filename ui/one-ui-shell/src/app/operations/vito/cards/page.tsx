"use client";

import Link from "next/link";
import { useState } from "react";
import { CreditCard, Search, CheckCircle, XCircle, MinusCircle, AlertTriangle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useActiveCard,
  useCardHistory,
  useCardsByStatus,
  useRequestCard,
  useActivateCard,
  useInactivateCard,
  useRevokeCard,
  type CardStatus,
  type SmartCard,
  type RevocationReason,
} from "@/hooks/queries/useVitoCards";

const CARD_STATUSES: CardStatus[] = ["PENDING", "ACTIVE", "INACTIVE", "REVOKED", "EXPIRED", "PRINTED"];

function statusBadge(status: CardStatus) {
  const map: Record<CardStatus, string> = {
    ACTIVE: "bg-success-soft text-primary-hover border-success/25",
    PENDING: "bg-warning-soft text-warning-foreground border-warning/35",
    PRINTED: "bg-sky-50 text-sky-700 border-sky-200",
    INACTIVE: "bg-neutral-100 text-muted-foreground border-border",
    REVOKED: "bg-danger-soft text-danger border-danger/28",
    EXPIRED: "bg-danger-soft text-rose-600 border-danger/28",
  };
  return (
    <span className={`inline-block rounded-full border px-2.5 py-0.5 text-xs font-medium ${map[status]}`}>
      {status}
    </span>
  );
}

function fmt(value: string | undefined) {
  if (!value) return "—";
  return new Date(value).toLocaleDateString("en-ZW", { dateStyle: "medium" });
}

function CardRow({ card, onActivate, onInactivate, onRevoke, isActing }: {
  card: SmartCard;
  onActivate: (id: string) => void;
  onInactivate: (id: string) => void;
  onRevoke: (id: string, reason: RevocationReason) => void;
  isActing: boolean;
}) {
  return (
    <div className="rounded-2xl border border-border bg-card p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            {statusBadge(card.status)}
            <span className="text-xs text-muted-foreground">ID {card.cardId}</span>
          </div>
          <p className="text-sm font-medium text-foreground">
            {card.serialNumber ?? "No serial number"}
          </p>
          <p className="text-xs text-muted-foreground">
            Issued {fmt(card.issuedAt)} · Expires {fmt(card.expiresAt)}
          </p>
          {card.revokedAt && (
            <p className="text-xs text-red-500">
              Revoked {fmt(card.revokedAt)} · {card.revocationReason ?? "—"}
            </p>
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          {card.status === "PRINTED" && (
            <button
              type="button"
              disabled={isActing}
              onClick={() => onActivate(card.cardId)}
              className="inline-flex items-center gap-1.5 rounded-xl border border-emerald-300 bg-success-soft px-3 py-1.5 text-xs font-medium text-primary-hover hover:bg-emerald-100 disabled:opacity-50"
            >
              <CheckCircle className="h-3.5 w-3.5" />
              Activate
            </button>
          )}
          {card.status === "ACTIVE" && (
            <button
              type="button"
              disabled={isActing}
              onClick={() => onInactivate(card.cardId)}
              className="inline-flex items-center gap-1.5 rounded-xl border border-border bg-background px-3 py-1.5 text-xs font-medium text-foreground hover:bg-neutral-100 disabled:opacity-50"
            >
              <MinusCircle className="h-3.5 w-3.5" />
              Inactivate
            </button>
          )}
          {(card.status === "ACTIVE" || card.status === "INACTIVE" || card.status === "PRINTED") && (
            <button
              type="button"
              disabled={isActing}
              onClick={() => onRevoke(card.cardId, "LOST")}
              className="inline-flex items-center gap-1.5 rounded-xl border border-danger/28 bg-danger-soft px-3 py-1.5 text-xs font-medium text-danger hover:bg-red-100 disabled:opacity-50"
            >
              <XCircle className="h-3.5 w-3.5" />
              Revoke
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

function SearchPanel() {
  const [input, setInput] = useState("");
  const [healthId, setHealthId] = useState<string | undefined>(undefined);

  const activeCard = useActiveCard(healthId);
  const cardHistory = useCardHistory(healthId);
  const requestCard = useRequestCard();
  const activateCard = useActivateCard();
  const inactivateCard = useInactivateCard();
  const revokeCard = useRevokeCard();

  const isActing = activateCard.isPending || inactivateCard.isPending || revokeCard.isPending;

  function handleSearch() {
    const trimmed = input.trim();
    if (trimmed) setHealthId(trimmed);
  }

  function handleRequest() {
    if (!healthId) return;
    requestCard.mutate({ healthId });
  }

  const historyCards: SmartCard[] = cardHistory.data?.data ?? [];
  const active = activeCard.data?.data;

  return (
    <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
      <h2 className="text-sm font-semibold text-foreground">Search by Health ID</h2>
      <div className="flex gap-2">
        <input
          className="flex-1 rounded-xl border border-border px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
          placeholder="e.g. b0000000-0000-4000-8000-000000000001"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && handleSearch()}
        />
        <button
          type="button"
          onClick={handleSearch}
          className="inline-flex items-center gap-1.5 rounded-xl bg-primary-hover px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700"
        >
          <Search className="h-4 w-4" />
          Search
        </button>
      </div>

      {healthId && (
        <>
          <div className="flex items-center justify-between">
            <p className="text-xs text-muted-foreground">
              Results for <span className="font-medium text-foreground">{healthId}</span>
            </p>
            <button
              type="button"
              disabled={requestCard.isPending || !healthId}
              onClick={handleRequest}
              className="inline-flex items-center gap-1.5 rounded-xl border border-primary/25 bg-primary-soft px-3 py-1.5 text-xs font-medium text-impilo-900 hover:border-impilo-300 disabled:opacity-50"
            >
              <CreditCard className="h-3.5 w-3.5" />
              {requestCard.isPending ? "Requesting…" : "Request Card"}
            </button>
          </div>

          {activeCard.isLoading && <p className="text-sm text-muted-foreground">Loading active card…</p>}
          {activeCard.isError && (
            <p className="text-sm text-amber-600">No active card found for this Health ID.</p>
          )}
          {active && (
            <div className="space-y-2">
              <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Active card</p>
              <CardRow
                card={active}
                onActivate={(id) => activateCard.mutate(id)}
                onInactivate={(id) => inactivateCard.mutate(id)}
                onRevoke={(id, reason) => revokeCard.mutate({ cardId: id, reason })}
                isActing={isActing}
              />
            </div>
          )}

          {cardHistory.isLoading && <p className="text-sm text-muted-foreground">Loading card history…</p>}
          {historyCards.length > 0 && (
            <div className="space-y-2">
              <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                History ({historyCards.length})
              </p>
              <div className="space-y-2">
                {historyCards.map((card) => (
                  <CardRow
                    key={card.cardId}
                    card={card}
                    onActivate={(id) => activateCard.mutate(id)}
                    onInactivate={(id) => inactivateCard.mutate(id)}
                    onRevoke={(id, reason) => revokeCard.mutate({ cardId: id, reason })}
                    isActing={isActing}
                  />
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function StatusListPanel() {
  const [status, setStatus] = useState<CardStatus>("ACTIVE");
  const [page, setPage] = useState(0);
  const activateCard = useActivateCard();
  const inactivateCard = useInactivateCard();
  const revokeCard = useRevokeCard();
  const isActing = activateCard.isPending || inactivateCard.isPending || revokeCard.isPending;

  const query = useCardsByStatus(status, page, 10);
  const paged = query.data?.data;

  return (
    <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-foreground">Cards by status</h2>
        <div className="flex flex-wrap gap-2">
          {CARD_STATUSES.map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => { setStatus(s); setPage(0); }}
              className={`rounded-xl border px-3 py-1 text-xs font-medium transition-colors ${
                status === s
                  ? "border-impilo-300 bg-primary-soft text-impilo-900"
                  : "border-border bg-card text-muted-foreground hover:border-border"
              }`}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {query.isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
      {query.isError && (
        <div className="flex items-center gap-2 rounded-2xl bg-warning-soft p-4 text-sm text-warning-foreground">
          <AlertTriangle className="h-4 w-4 flex-shrink-0" />
          Failed to load cards. The service may be unavailable.
        </div>
      )}

      {paged && (
        <>
          {paged.items.length === 0 ? (
            <div className="rounded-2xl bg-background p-4 text-sm text-muted-foreground">
              No {status.toLowerCase()} cards found.
            </div>
          ) : (
            <div className="space-y-2">
              {paged.items.map((card) => (
                <CardRow
                  key={card.cardId}
                  card={card}
                  onActivate={(id) => activateCard.mutate(id)}
                  onInactivate={(id) => inactivateCard.mutate(id)}
                  onRevoke={(id, reason) => revokeCard.mutate({ cardId: id, reason })}
                  isActing={isActing}
                />
              ))}
            </div>
          )}

          {paged.totalPages > 1 && (
            <div className="flex items-center justify-between pt-2">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
                className="rounded-xl border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:border-gray-400 disabled:opacity-40"
              >
                Previous
              </button>
              <span className="text-xs text-muted-foreground">
                Page {page + 1} of {paged.totalPages} · {paged.totalElements} total
              </span>
              <button
                type="button"
                disabled={page >= paged.totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="rounded-xl border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:border-gray-400 disabled:opacity-40"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

export default function VitoCardsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Smart Card Management"
        subtitle="Search, request, activate, inactivate, and revoke Health ID smart cards"
        icon={<CreditCard className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="flex flex-wrap gap-3">
            <Link
              href="/operations/vito"
              className="text-sm text-muted-foreground hover:text-foreground underline-offset-2 hover:underline"
            >
              ← Identity operations
            </Link>
          </div>

          <SearchPanel />
          <StatusListPanel />
        </div>
      </PageShell>
    </AppLayout>
  );
}
