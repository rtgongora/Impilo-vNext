"use client";

/**
 * CommunityCard / GroupCard / PageCard — compact list-row representations
 * used in left rail, suggestions panel, and the dedicated index pages.
 */

import Link from "next/link";
import { CheckCircle2, ShieldCheck, Users } from "lucide-react";
import type { SocialCommunity, SocialGroup, SocialPage } from "@/hooks/queries/useSocial";

export function CommunityCard({ community, onJoin, joining }: { community: SocialCommunity; onJoin?: () => void; joining?: boolean }) {
  const isMember = community.viewerMembership !== "none";
  return (
    <div className="impilo-surface-card flex flex-col gap-3 p-4">
      <div className="flex items-start gap-3">
        <span
          className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl text-base font-semibold text-white"
          style={{ background: community.coverColor || "var(--primary)" }}
        >
          {community.name.charAt(0)}
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5">
            <Link href={`/communities/${community.id}`} className="truncate text-sm font-semibold text-[color:var(--text-primary)] hover:text-[color:var(--primary)]">
              {community.name}
            </Link>
            {community.kind === "verified" && <ShieldCheck className="h-3.5 w-3.5 text-[color:var(--primary)]" />}
          </div>
          <p className="text-[11px] uppercase tracking-wide text-[color:var(--text-muted)]">
            {community.category} · {community.kind}
          </p>
          <p className="mt-1 line-clamp-2 text-xs text-[color:var(--text-secondary)]">
            {community.description || "Connect, share, and learn together."}
          </p>
        </div>
      </div>
      <div className="flex items-center justify-between text-xs text-[color:var(--text-muted)]">
        <span className="inline-flex items-center gap-1">
          <Users className="h-3.5 w-3.5" /> {community.memberCount.toLocaleString()} members
        </span>
        {onJoin && (
          <button
            type="button"
            onClick={onJoin}
            disabled={joining || isMember}
            className={`rounded-full px-3 py-1 text-[11px] font-semibold ${
              isMember
                ? "border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] text-[color:var(--text-secondary)]"
                : "bg-[color:var(--primary)] text-white hover:bg-[color:var(--primary-hover)]"
            }`}
          >
            {isMember ? (
              <span className="inline-flex items-center gap-1">
                <CheckCircle2 className="h-3 w-3" /> Joined
              </span>
            ) : joining ? "Joining…" : "Join"}
          </button>
        )}
      </div>
    </div>
  );
}

export function GroupCard({ group }: { group: SocialGroup }) {
  return (
    <div className="impilo-surface-card flex items-start gap-3 p-4">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-[color:var(--primary-soft)] text-[color:var(--primary-hover)]">
        <Users className="h-4 w-4" />
      </span>
      <div className="min-w-0 flex-1">
        <Link href={`/groups/${group.id}`} className="truncate text-sm font-semibold text-[color:var(--text-primary)] hover:text-[color:var(--primary)]">
          {group.name}
        </Link>
        <p className="text-[11px] text-[color:var(--text-muted)]">
          {group.category || "Group"} · {group.memberCount.toLocaleString()} members
        </p>
        <p className="mt-1 line-clamp-2 text-xs text-[color:var(--text-secondary)]">
          {group.description || "Coordinate, plan, and chat with your group."}
        </p>
      </div>
    </div>
  );
}

export function PageCard({ page, onFollow, following }: { page: SocialPage; onFollow?: () => void; following?: boolean }) {
  return (
    <div className="impilo-surface-card flex items-start gap-3 p-4">
      <span
        className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-[color:var(--primary-soft)] text-base font-semibold text-[color:var(--primary-hover)]"
      >
        {page.name.charAt(0)}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          <Link href={`/pages/${page.id}`} className="truncate text-sm font-semibold text-[color:var(--text-primary)] hover:text-[color:var(--primary)]">
            {page.name}
          </Link>
          {page.verified && <ShieldCheck className="h-3.5 w-3.5 text-[color:var(--primary)]" />}
        </div>
        <p className="text-[11px] uppercase tracking-wide text-[color:var(--text-muted)]">{page.kind}</p>
        {page.bio && (
          <p className="mt-1 line-clamp-2 text-xs text-[color:var(--text-secondary)]">{page.bio}</p>
        )}
        <p className="mt-1 text-[11px] text-[color:var(--text-muted)]">{page.followerCount.toLocaleString()} followers</p>
      </div>
      {onFollow && (
        <button
          type="button"
          onClick={onFollow}
          disabled={following}
          className={`shrink-0 rounded-full px-3 py-1 text-[11px] font-semibold ${
            page.viewerFollows
              ? "border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] text-[color:var(--text-secondary)]"
              : "bg-[color:var(--primary)] text-white hover:bg-[color:var(--primary-hover)]"
          }`}
        >
          {page.viewerFollows ? "Following" : following ? "…" : "Follow"}
        </button>
      )}
    </div>
  );
}
