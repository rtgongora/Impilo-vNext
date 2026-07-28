"use client";

/**
 * `/my-life` — intent-resolution shim onto the canonical personal space.
 *
 * This route was linked as a destination (the visit-feedback page's "back to
 * my life" action, and `/my-life/*` sub-features like feedback and fundraisers
 * imply a parent) but had no page and no routes.ts entry, so following it 404'd.
 *
 * It resolves to `/home` rather than the reverse. The plan's F5 wording had
 * `/home` becoming a router *into* `/my-life`, but that inverts the code as
 * built: `/home` is already the canonical personal landing — navZone "life",
 * the destination `resolvePostLoginDestination` gives every citizen with
 * `operationalMode: "my_life"` — while `/my-life` only ever grew a couple of
 * feature sub-trees. Moving the personal landing to a new prefix would churn
 * the highest-traffic route in the app and break existing links to deliver
 * nothing a person can see. F5's actual goal — one canonical personal
 * destination, no competing dashboard — is met by making that canonical
 * destination reachable under both names.
 *
 * Same shape as `/provider-workspace` after F6: resolves and renders nothing.
 */

import { useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";

export function resolveMyLifeTarget(searchParams: URLSearchParams): string {
  const query = searchParams.toString();
  return query ? `/home?${query}` : "/home";
}

export default function MyLifeShimPage() {
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    router.replace(resolveMyLifeTarget(searchParams));
  }, [router, searchParams]);

  return null;
}
