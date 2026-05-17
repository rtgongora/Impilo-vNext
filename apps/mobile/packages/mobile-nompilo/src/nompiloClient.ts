/**
 * Nompilo mobile client.
 *
 * Calls the experience-bff LLM gateway at `/internal/v1/llm/chat`. The gateway
 * is responsible for routing to the configured Impilo LLM provider, applying
 * Tshepo/governance checks and audit. The mobile client only:
 *   - constructs a role-aware system prompt
 *   - forwards user history
 *   - falls back gracefully when the gateway is unavailable
 *   - applies a final mobile-side guardrail check
 */

import { apiClient } from "@impilo/mobile-api-client";
import { buildFallbackResponse, NOMPILO_DISCLAIMER } from "./fallback";
import type {
  NompiloAskRequest,
  NompiloAskResult,
  NompiloMessage,
  NompiloRole,
} from "./types";

const CHAT_PATH = "/internal/v1/llm/chat";

interface NompiloClientConfig {
  /** Override chat endpoint (BFF). Defaults to the standard LLM gateway path. */
  chatPath?: string;
  /** Force fallback (e.g. for offline mode or smoke tests). */
  forceFallback?: boolean;
  /** Logger override; default is a no-op. */
  log?: (level: "info" | "warn" | "error", msg: string, meta?: unknown) => void;
}

let config: NompiloClientConfig = { chatPath: CHAT_PATH };

export function configureNompilo(next: NompiloClientConfig): void {
  config = { ...config, ...next };
}

function systemPromptFor(role: NompiloRole): string {
  switch (role) {
    case "PROVIDER":
      return [
        "You are Nompilo, a warm, concise, mobile assistant for Impilo providers.",
        "Help with navigation, workflow guidance, delivery tracking, integration status interpretation, learning suggestions, and policy explanations.",
        "Never produce clinical diagnoses, prescriptions, dosages, or definitive legal/financial verdicts.",
        "Never claim to bypass Tshepo authorization, consent, or audit.",
        "Keep replies short and mobile-friendly. Prefer bullet lists.",
      ].join(" ");
    case "CITIZEN":
      return [
        "You are Nompilo, a warm, simple, mobile health concierge for Impilo citizens.",
        "Help citizens find services, book appointments, understand deliveries, payments, consent and privacy in plain language.",
        "Never provide diagnoses, doses, or treatment plans. Recommend a clinician when relevant.",
        "Keep replies short, kind and culturally appropriate for African/Zimbabwean users.",
      ].join(" ");
    case "COURIER":
      return [
        "You are Nompilo, a calm mobile assistant for Impilo couriers and dispatch riders.",
        "Help with assignment status, pickup/handover/proof steps, exception reporting and Ndila routing.",
        "Never pretend to know live traffic if Ndila has not returned it.",
      ].join(" ");
    case "SUPERVISOR":
      return [
        "You are Nompilo, an operations assistant for Impilo supervisors.",
        "Summarise team load, escalations, inventory, finance status, and delivery operations.",
        "Always defer high-risk decisions to the relevant facility or system administrator.",
      ].join(" ");
    case "OUTREACH":
      return [
        "You are Nompilo, an outreach team assistant.",
        "Help with household visits, screening, follow-up, sync status, and offline capture guidance.",
        "Remind the user to confirm consent before recording sensitive details.",
      ].join(" ");
    case "ADMIN":
      return [
        "You are Nompilo, a careful administrative helper.",
        "Most admin operations remain on web. From mobile, summarise statuses and point users to the right web surface.",
      ].join(" ");
    default:
      return "You are Nompilo, the Impilo mobile assistant. Be warm, brief and helpful.";
  }
}

function buildMessages(req: NompiloAskRequest): NompiloMessage[] {
  const role = req.context?.role ?? "CITIZEN";
  const messages: NompiloMessage[] = [
    { role: "system", content: systemPromptFor(role) },
  ];

  if (req.context?.surface) {
    messages.push({
      role: "system",
      content: `Caller surface: ${req.context.surface}.`,
    });
  }
  if (req.context?.hints) {
    const hintLines = Object.entries(req.context.hints)
      .filter(([, v]) => v !== undefined && v !== null && v !== "")
      .map(([k, v]) => `- ${k}: ${String(v)}`);
    if (hintLines.length > 0) {
      messages.push({
        role: "system",
        content: `Context hints:\n${hintLines.join("\n")}`,
      });
    }
  }

  if (req.history && req.history.length > 0) {
    messages.push(...req.history.slice(-10));
  }
  messages.push({ role: "user", content: req.prompt });
  return messages;
}

function parseModelReply(payload: unknown): { reply?: string; correlationId?: string } {
  if (!payload || typeof payload !== "object") return {};
  const root = payload as Record<string, unknown>;
  // Standard envelope: { content: string } | { reply: string } | { choices: [{ message: {content} }] }
  if (typeof root.content === "string") return { reply: root.content };
  if (typeof root.reply === "string") return { reply: root.reply };
  if (typeof root.message === "string") return { reply: root.message };
  const choices = root.choices as Array<{ message?: { content?: string } }> | undefined;
  if (Array.isArray(choices) && choices[0]?.message?.content) {
    return { reply: choices[0].message.content };
  }
  if (typeof root.text === "string") return { reply: root.text };
  return {};
}

export async function askNompilo(req: NompiloAskRequest): Promise<NompiloAskResult> {
  if (config.forceFallback) {
    return buildFallbackResponse(req);
  }

  const path = config.chatPath ?? CHAT_PATH;
  const messages = buildMessages(req);

  try {
    const response = await apiClient.post<unknown>(
      path,
      {
        messages,
        riskLevel: req.riskLevel ?? "MODERATE",
        requiresAudit: true,
        requiresHumanApprovalForActions: true,
        temperature: 0.2,
      },
      {
        purposeOfUse: req.context?.purposeOfUse,
        noRetry: false,
      }
    );

    const parsed = parseModelReply(response.data);
    if (!parsed.reply || parsed.reply.trim().length === 0) {
      config.log?.("warn", "nompilo: empty model reply, falling back", { path });
      return buildFallbackResponse(req);
    }

    return {
      reply: parsed.reply.trim(),
      modelAvailable: true,
      usedFallback: false,
      correlationId: response.correlationId,
      disclaimer: NOMPILO_DISCLAIMER,
    };
  } catch (err) {
    config.log?.("warn", "nompilo: model unavailable, falling back", { err });
    return buildFallbackResponse(req);
  }
}

export const nompilo = {
  ask: askNompilo,
};

export type Nompilo = typeof nompilo;
