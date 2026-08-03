/**
 * Contract between the shell and the separate sign-in window it opens.
 *
 * This lives in lib, not beside the page that sends it, because a Next.js page module may only
 * export a default component and Next's own reserved fields — an extra `export const` there
 * fails `next build` with "is not a valid Page export field". Type-checking and unit tests both
 * pass on that arrangement, so the build is the only thing that catches it.
 */

/**
 * Sent by /auth/complete to whichever window opened it, once the OIDC round trip has produced
 * a live session. Both ends also check event.origin — the message type is a routing label, not
 * a security boundary; anyone can send a string.
 */
export const SIGN_IN_COMPLETE_MESSAGE = "impilo:sign-in-complete";

export interface SignInCompleteMessage {
  type: typeof SIGN_IN_COMPLETE_MESSAGE;
  /** Same-origin path the opener should navigate to. Never a full URL. */
  destination: string;
}
