/**
 * Deterministic mobile-side fallback responder for Nompilo.
 *
 * Used when the LLM gateway is unreachable, returns an unhealthy provider
 * response, or is explicitly disabled by feature flag. The fallback never
 * claims to be the real model and always invites the user to retry or open the
 * relevant in-app surface.
 */

import type {
  NompiloAskRequest,
  NompiloAskResult,
  NompiloRole,
} from "./types";

const DISCLAIMER =
  "Nompilo is a helper, not a clinician. Always confirm clinical, legal, or financial decisions with the right person.";

const FALLBACK_DISCLAIMER =
  "Nompilo's smart assistant isn't available right now, so I'm replying with a short standard guide. Please try again in a few minutes.";

interface RoleHints {
  greeting: string;
  generic: string;
  delivery: string;
  payment: string;
  navigation: string;
  consent: string;
  suggestions: string[];
}

const ROLE_HINTS: Record<NompiloRole, RoleHints> = {
  PROVIDER: {
    greeting: "Hi, I'm Nompilo — your provider helper.",
    generic:
      "I can summarise alerts, queues, deliveries, learning content and integration statuses. Try asking 'show my pending tasks' or 'explain this lab status'.",
    delivery:
      "For dispatch and delivery, the Courier mode has the live list. Tracking shows on a Ndila map only when the courier has shared their position.",
    payment:
      "Open Finance → MusheX for payment status. Failed transactions usually retry automatically.",
    navigation:
      "Use the bottom tabs. Press the mode switcher (top of the dashboard) to move between Provider, Outreach, Courier or Offline modes.",
    consent:
      "Tshepo checks consent before sensitive screens open. If you see 'why is this blocked', that screen tells you the missing purpose or consent.",
    suggestions: [
      "Show my urgent tasks",
      "Explain the delivery status",
      "Open the courier mode",
      "Open Fundo learning",
    ],
  },
  CITIZEN: {
    greeting: "Hi, I'm Nompilo — your Impilo helper.",
    generic:
      "I can help you find a service, book an appointment, understand a status, or track a delivery. What would you like to do?",
    delivery:
      "Open Track delivery from the Health tab. You'll see status, timeline, and a Confirm receipt button if a courier asks for an OTP.",
    payment:
      "Open Finance from the Health tab to see payment status, subsidies, and wallet activity. We never share card details with sellers.",
    navigation:
      "Use the bottom tabs. Home for a snapshot, Health for personal records, Services for the marketplace, Messages for chats, Public for community programmes.",
    consent:
      "Privacy is in Health → Settings. You can change who sees what, withdraw consent, or download your records.",
    suggestions: [
      "Find a nearby clinic",
      "Track my delivery",
      "Book an appointment",
      "Show my health summary",
    ],
  },
  COURIER: {
    greeting: "Hi, I'm Nompilo — your courier helper.",
    generic:
      "Open the Courier mode to see assignments. Each step (Accept, Pickup, In Transit, Proof) is a tap. Keep the app online when you can so locations sync.",
    delivery:
      "If a delivery is offline, the proof is queued and synced when you reconnect. Don't refuse a delivery unless you have a real reason — Nhume requires a reason code.",
    payment: "Couriers don't handle payments in the app. Talk to the dispatcher if the recipient refuses to pay.",
    navigation:
      "Use the three tabs: Assignments, Proof & custody, Alerts.",
    consent:
      "Capture location only while a job is active. Background tracking is disabled unless the dispatcher explicitly enables it.",
    suggestions: [
      "Show my next pickup",
      "How do I capture proof?",
      "Report a delivery exception",
      "Where is the map?",
    ],
  },
  SUPERVISOR: {
    greeting: "Hi, I'm Nompilo — your supervisor view helper.",
    generic:
      "I can summarise team load, stock, escalations and shift coverage. Try 'show open escalations' or 'how is stock today'.",
    delivery: "Use Operations → Dispatch to see active deliveries and reassign.",
    payment: "Finance overview is in Operations → Finance. MusheX details remain on web for audit reasons.",
    navigation: "Bottom tabs cover the four supervisor surfaces.",
    consent: "Sensitive actions still go through Tshepo step-up.",
    suggestions: [
      "Show open escalations",
      "How is stock today?",
      "Open team overview",
    ],
  },
  OUTREACH: {
    greeting: "Hi, I'm Nompilo — your outreach helper.",
    generic:
      "Use Outreach for household visits, screening and follow-up. Capture works offline; it syncs when you reconnect.",
    delivery:
      "Outreach delivery items are handled inside Nhume — open the relevant household and follow the prompts.",
    payment: "Outreach doesn't normally handle payments. Refer the household to the marketplace or to a facility.",
    navigation: "Bottom tabs cover Dashboard, Households, Screening and Follow-up.",
    consent: "Always confirm the household understands what you're recording.",
    suggestions: [
      "Show today's households",
      "Start a screening",
      "Sync now",
    ],
  },
  ADMIN: {
    greeting: "Hi, I'm Nompilo — your operations helper.",
    generic:
      "Most administration lives on the web for safety. From mobile I can summarise statuses, open notifications, and link to web for deeper config.",
    delivery: "For Nhume admin, open the operations console on web.",
    payment: "MusheX configuration is web-only.",
    navigation: "Use the bottom tabs to move between surfaces.",
    consent: "Admin actions are always audited.",
    suggestions: [
      "Show pending approvals",
      "Open the audit feed",
      "What's failing today?",
    ],
  },
};

function detectIntent(prompt: string): keyof RoleHints {
  const lower = prompt.toLowerCase();
  if (/(deliver|courier|nhume|track|parcel|sample|stock pickup)/.test(lower)) {
    return "delivery";
  }
  if (/(pay|wallet|musex|mushex|claim|subsid|exempt|invoice|refund)/.test(lower)) {
    return "payment";
  }
  if (/(navigate|where|find|open|tab|menu|how do i|how to)/.test(lower)) {
    return "navigation";
  }
  if (/(consent|privacy|why.*block|why.*denied|access denied|tshepo)/.test(lower)) {
    return "consent";
  }
  if (/(hello|hi|hey|good morning|good afternoon)/.test(lower)) {
    return "greeting";
  }
  return "generic";
}

/** Build a deterministic mobile-side reply when no real model is available. */
export function buildFallbackResponse(req: NompiloAskRequest): NompiloAskResult {
  const role = req.context?.role ?? "CITIZEN";
  const hints = ROLE_HINTS[role] ?? ROLE_HINTS.CITIZEN;
  const intent = detectIntent(req.prompt);
  const reply = `${hints[intent]}\n\n${FALLBACK_DISCLAIMER}`;

  return {
    reply,
    suggestions: hints.suggestions,
    modelAvailable: false,
    usedFallback: true,
    disclaimer: DISCLAIMER,
  };
}

export { DISCLAIMER as NOMPILO_DISCLAIMER };
