"use client";

/**
 * useNompiloChat — the single conversational seam for the Nompilo assistant.
 *
 * Both the docked ProactiveAssistant and the NompiloGlobalCommandBar "Ask" affordance route
 * through this hook so there is exactly one client for /internal/v1/assistant/chat and one place
 * that enforces Nompilo's honesty doctrine:
 *   - it sends real context (route, facility, journey stage, and any recent failure signal),
 *   - it renders the backend's reply + genuine next-step actions,
 *   - when the backend is degraded (or unreachable) it says so and offers safe actions — it
 *     NEVER fabricates an answer and NEVER fakes success/availability.
 *
 * The conversation lives in useAssistantUiStore so it survives minimise/navigation.
 */

import { useCallback, useState } from "react";
import { usePathname } from "next/navigation";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useAssistantUiStore, type AssistantChatMessage } from "@/hooks/useAssistantUiStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useNompiloFailureStore, isFailureFresh } from "@/lib/nompilo-failure";
import { classifyRouteJourney } from "@/lib/ui-route-journey-map";

export interface AssistantChatResponse {
  reply: string;
  actions?: { label: string; href: string }[];
  degraded: boolean;
  disclaimer?: string;
  sources?: { label?: string; title?: string; href?: string }[];
}

/** Honest fallback when the assistant service cannot be reached — no fabricated answer. */
const UNREACHABLE_MESSAGE: AssistantChatMessage = {
  role: "assistant",
  degraded: true,
  text:
    "I could not reach the assistant service just now, so I will not guess at an answer. " +
    "You can try again in a moment, or report a problem and a person will follow up.",
  actions: [{ label: "Report a problem", href: "/welcome/report" }],
};

export function useNompiloChat() {
  const pathname = usePathname();
  const appendChatMessage = useAssistantUiStore((s) => s.appendChatMessage);
  const facility = useFacilityStore((s) => s.facility);
  const lastFailure = useNompiloFailureStore((s) => s.lastFailure);
  const [pending, setPending] = useState(false);

  const send = useCallback(
    async (raw: string) => {
      const message = raw.trim();
      if (!message || pending) return;

      appendChatMessage({ role: "user", text: message });
      setPending(true);

      // Only attach a failure signal that is recent enough to be relevant, and only the
      // privacy-safe fields (code/action/correlationId) — never PII or free text.
      const freshFailure = isFailureFresh(lastFailure) && lastFailure
        ? {
            code: lastFailure.code,
            action: lastFailure.action,
            correlationId: lastFailure.correlationId,
          }
        : undefined;

      try {
        const res = await apiClient.post<ApiResponse<AssistantChatResponse>>(
          "/internal/v1/assistant/chat",
          {
            message,
            context: {
              route: pathname ?? undefined,
              facilityId: facility?.id ?? undefined,
              journeyStage: classifyRouteJourney(pathname ?? ""),
              failure: freshFailure,
            },
          },
        );

        const data = res?.data;
        if (!data || typeof data.reply !== "string") {
          appendChatMessage(UNREACHABLE_MESSAGE);
          return;
        }

        appendChatMessage({
          role: "assistant",
          text: data.reply,
          actions: data.actions,
          degraded: data.degraded,
          disclaimer: data.disclaimer,
          sources: data.sources,
        });
      } catch {
        // Honest degraded state — never a fabricated answer, never a fake success.
        appendChatMessage(UNREACHABLE_MESSAGE);
      } finally {
        setPending(false);
      }
    },
    [appendChatMessage, facility?.id, lastFailure, pathname, pending],
  );

  return { send, pending };
}
