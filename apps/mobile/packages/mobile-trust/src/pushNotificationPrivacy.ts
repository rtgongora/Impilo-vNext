/**
 * Privacy-safe notification copy for mobile (and server templates).
 * Lock-screen text must never reveal conditions, results, meds, or sensitive context.
 */

export const PUSH_APP_NAME = "Impilo";

export const PUSH_GENERIC_NEW_MESSAGE_BODY =
  "You have a new message. Open Impilo to view.";

export const PUSH_GENERIC_HEALTH_UPDATE_BODY =
  "You have a new health notification. Open Impilo to view.";

export const PUSH_APPOINTMENT_CHANGED_BODY =
  "Your appointment was updated. Open Impilo to view details.";

export const PUSH_CONSENT_ACTION_REQUIRED_BODY =
  "A consent request needs your attention in Impilo.";

export const PUSH_WALLET_OR_BILLING_UPDATE_BODY =
  "Your Impilo wallet or billing was updated. Open the app to review.";

export const PUSH_QUEUE_TURN_BODY =
  "It may be your turn in line. Open Impilo to check queue status.";

/**
 * Returns a body string with no clinical or financial detail.
 * @param kind — high-level channel only
 */
export function buildGenericHealthNotificationBody(
  kind: "message" | "health" | "appointment" | "consent" | "billing" | "queue",
): string {
  switch (kind) {
    case "message":
      return PUSH_GENERIC_NEW_MESSAGE_BODY;
    case "appointment":
      return PUSH_APPOINTMENT_CHANGED_BODY;
    case "consent":
      return PUSH_CONSENT_ACTION_REQUIRED_BODY;
    case "billing":
      return PUSH_WALLET_OR_BILLING_UPDATE_BODY;
    case "queue":
      return PUSH_QUEUE_TURN_BODY;
    case "health":
    default:
      return PUSH_GENERIC_HEALTH_UPDATE_BODY;
  }
}

/** Title only — keep short; avoid medical words. */
export function genericNotificationTitle(): string {
  return PUSH_APP_NAME;
}
