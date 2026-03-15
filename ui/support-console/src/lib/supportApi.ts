/**
 * Support Console — Support Service API
 *
 * Typed API client methods for all support-service endpoints.
 * All requests route through the trust-aware apiClient.
 */

import { apiClient } from "./apiClient";
import type {
  Ticket,
  TicketComment,
  TicketAssignment,
  SupportMessage,
  KnowledgeArticle,
  DashboardStats,
  PagedResult,
  CreateTicketParams,
  UpdateTicketParams,
  TicketFilters,
} from "@/types/support";

const BASE = "/internal/v1/support";

function buildQuery(params: Record<string, string | number | undefined>): string {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined);
  if (entries.length === 0) return "";
  return "?" + entries.map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`).join("&");
}

// ── Tickets ──

export function listTickets(filters: TicketFilters = {}): Promise<PagedResult<Ticket>> {
  const qs = buildQuery({
    status: filters.status,
    priority: filters.priority,
    category: filters.category,
    assigneeRef: filters.assigneeRef,
    cursor: filters.cursor ?? 0,
    limit: filters.limit ?? 20,
  });
  return apiClient.get<PagedResult<Ticket>>(`${BASE}/tickets${qs}`);
}

export function getTicket(ticketId: string): Promise<Ticket> {
  return apiClient.get<Ticket>(`${BASE}/tickets/${ticketId}`);
}

export function createTicket(params: CreateTicketParams): Promise<Ticket> {
  return apiClient.post<Ticket>(`${BASE}/tickets`, params);
}

export function updateTicket(ticketId: string, params: UpdateTicketParams): Promise<Ticket> {
  return apiClient.patch<Ticket>(`${BASE}/tickets/${ticketId}`, params);
}

// ── Ticket Comments ──

export function listComments(ticketId: string): Promise<PagedResult<TicketComment>> {
  return apiClient.get<PagedResult<TicketComment>>(`${BASE}/tickets/${ticketId}/comments`);
}

export function addComment(ticketId: string, authorRef: string, body: string): Promise<TicketComment> {
  return apiClient.post<TicketComment>(`${BASE}/tickets/${ticketId}/comments`, { authorRef, body });
}

// ── Ticket Assignments ──

export function listAssignments(ticketId: string): Promise<PagedResult<TicketAssignment>> {
  return apiClient.get<PagedResult<TicketAssignment>>(`${BASE}/tickets/${ticketId}/assignments`);
}

export function assignTicket(ticketId: string, assigneeRef: string, assignedBy: string): Promise<TicketAssignment> {
  return apiClient.post<TicketAssignment>(`${BASE}/tickets/${ticketId}/assign`, { assigneeRef, assignedBy });
}

// ── Escalation ──

export function escalateTicket(ticketId: string, escalatedBy: string, targetLevel: number): Promise<Ticket> {
  return apiClient.post<Ticket>(`${BASE}/tickets/${ticketId}/escalate`, { escalatedBy, targetLevel });
}

// ── Messages ──

export function listMessages(ticketId: string): Promise<PagedResult<SupportMessage>> {
  return apiClient.get<PagedResult<SupportMessage>>(`${BASE}/tickets/${ticketId}/messages`);
}

export function sendMessage(
  ticketId: string,
  senderRef: string,
  senderType: "AGENT" | "REQUESTER" | "SYSTEM",
  body: string,
): Promise<SupportMessage> {
  return apiClient.post<SupportMessage>(`${BASE}/tickets/${ticketId}/messages`, { senderRef, senderType, body });
}

// ── Knowledge Articles ──

export function listArticles(category?: string, status?: string): Promise<PagedResult<KnowledgeArticle>> {
  const qs = buildQuery({ category, status, cursor: 0, limit: 50 });
  return apiClient.get<PagedResult<KnowledgeArticle>>(`${BASE}/articles${qs}`);
}

export function getArticle(articleId: string): Promise<KnowledgeArticle> {
  return apiClient.get<KnowledgeArticle>(`${BASE}/articles/${articleId}`);
}

export function createArticle(params: {
  title: string;
  body: string;
  authorRef: string;
  category?: string;
  tags?: string;
}): Promise<KnowledgeArticle> {
  return apiClient.post<KnowledgeArticle>(`${BASE}/articles`, params);
}

export function updateArticle(articleId: string, params: {
  title?: string;
  body?: string;
  category?: string;
  tags?: string;
  status?: string;
}): Promise<KnowledgeArticle> {
  return apiClient.patch<KnowledgeArticle>(`${BASE}/articles/${articleId}`, params);
}

// ── Dashboard ──

export function getDashboardStats(): Promise<DashboardStats> {
  return apiClient.get<DashboardStats>(`${BASE}/dashboard/stats`);
}
