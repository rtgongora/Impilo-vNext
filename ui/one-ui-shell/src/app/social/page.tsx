"use client";

/**
 * /social — Three-column social timeline.
 *
 * Layout:
 *   - Left rail: navigation, spaces, optional moderation shortcuts.
 *   - Center: feed scope tabs, composer, feed list.
 *   - Right rail: suggestions, trending, public health, upcoming.
 *
 * Role-aware shortcuts derive from the auth store. The composer surfaces
 * announcement post type only for actors with announcement permissions.
 */

import { useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useSocialFeed,
  useSocialCommunities,
  useSocialGroups,
  useSocialPages,
  type FeedScope,
} from "@/hooks/queries/useSocial";
import { ThreeColumnTimelineLayout } from "@/components/social/ThreeColumnTimelineLayout";
import { SocialLeftRail } from "@/components/social/SocialLeftRail";
import { SocialRightRail } from "@/components/social/SocialRightRail";
import { TimelineComposer } from "@/components/social/TimelineComposer";
import { FeedScopeTabs } from "@/components/social/FeedScopeTabs";
import { FeedList } from "@/components/social/FeedList";
import { SocialCommunityOrchestrationRail } from "@/components/social/SocialCommunityOrchestrationRail";

const ADMIN_ROLES = new Set(["facility-admin", "public-health-officer", "moderator", "tenant-admin", "district-manager", "provincial-manager", "national-manager"]);
const ANNOUNCEMENT_ROLES = new Set(["facility-admin", "public-health-officer", "tenant-admin", "district-manager", "provincial-manager", "national-manager"]);

export default function SocialTimelinePage() {
  const params = useSearchParams();
  const user = useAuthStore((s) => s.user);
  const initialScope = (params?.get("scope") as FeedScope | null) ?? "home";
  const [scope, setScope] = useState<FeedScope>(initialScope);

  const feedQ = useSocialFeed(scope);
  const communitiesQ = useSocialCommunities();
  const groupsQ = useSocialGroups();
  const pagesQ = useSocialPages();

  const roles = useMemo(() => new Set(user?.roles ?? []), [user]);
  const isAdmin = useMemo(() => [...roles].some((r) => ADMIN_ROLES.has(r)), [roles]);
  const canPublishAnnouncements = useMemo(
    () => user?.actorType === "OPERATOR" || [...roles].some((r) => ANNOUNCEMENT_ROLES.has(r)),
    [roles, user?.actorType],
  );

  return (
    <AppLayout>
      <ThreeColumnTimelineLayout
        left={<SocialLeftRail showModeration={isAdmin} activeScope={scope} />}
        center={
          <div className="space-y-4">
            <SocialCommunityOrchestrationRail />
            <FeedScopeTabs active={scope} onChange={setScope} />
            <TimelineComposer
              communities={communitiesQ.data ?? []}
              groups={groupsQ.data ?? []}
              pages={pagesQ.data ?? []}
              canPublishAnnouncements={canPublishAnnouncements}
            />
            <FeedList
              posts={feedQ.data?.items}
              isLoading={feedQ.isLoading}
              error={feedQ.error}
              emptyTitle={emptyTitle(scope)}
              emptyHint={emptyHint(scope)}
            />
          </div>
        }
        right={<SocialRightRail recentPosts={feedQ.data?.items ?? []} />}
      />
    </AppLayout>
  );
}

function emptyTitle(scope: FeedScope): string {
  switch (scope) {
    case "saved": return "No saved posts";
    case "my_posts": return "You haven’t posted yet";
    case "drafts": return "No drafts saved";
    case "moderation": return "Moderation queue is clear";
    case "announcements": return "No announcements right now";
    default: return "No posts yet";
  }
}

function emptyHint(scope: FeedScope): string {
  switch (scope) {
    case "saved": return "Bookmark a post to save it for later.";
    case "my_posts": return "Use the composer above to share an update.";
    case "drafts": return "Drafts you save will appear here.";
    case "moderation": return "Reported posts will appear here for review.";
    default: return "Follow communities and pages to populate this feed.";
  }
}
