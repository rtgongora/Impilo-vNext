"use client";

import { useState } from "react";
import { Check, Link2, Loader2 } from "lucide-react";
import { meetingInviteUrl, mintMeetingInvite } from "@/hooks/queries/useMeeting";

/**
 * Host/cohost invite-link mint: khuluma issues a short-TTL HMAC token; the copied URL routes
 * through /meet/join which resolves it for AUTHENTICATED users. (Unauthenticated guests are an
 * honest platform seam — the tshepo guest tier is undefined.)
 */
export function InviteLinkButton({ conversationId }: { conversationId: string }) {
  const [state, setState] = useState<"idle" | "minting" | "copied" | "error">("idle");

  const mint = async () => {
    setState("minting");
    try {
      const invite = await mintMeetingInvite(conversationId);
      const url = meetingInviteUrl(invite.token);
      try {
        await navigator.clipboard.writeText(url);
      } catch {
        window.prompt("Copy the meeting invite link:", url);
      }
      setState("copied");
      setTimeout(() => setState("idle"), 2500);
    } catch {
      setState("error");
      setTimeout(() => setState("idle"), 2500);
    }
  };

  return (
    <button
      type="button"
      aria-label="Copy invite link"
      data-testid="invite-link-button"
      className="flex items-center gap-1 rounded-md bg-neutral-800 px-2 py-1.5 text-xs text-white hover:bg-neutral-700"
      onClick={mint}
      disabled={state === "minting"}
    >
      {state === "minting" ? (
        <Loader2 className="h-3.5 w-3.5 animate-spin" />
      ) : state === "copied" ? (
        <Check className="h-3.5 w-3.5 text-emerald-400" />
      ) : (
        <Link2 className="h-3.5 w-3.5" />
      )}
      {state === "copied" ? "Copied" : state === "error" ? "Failed" : "Invite"}
    </button>
  );
}
