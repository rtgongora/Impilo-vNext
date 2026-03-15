export type TicketStatus = "OPEN" | "IN_PROGRESS" | "WAITING" | "RESOLVED" | "CLOSED";
export type TicketPriority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type TicketCategory = "GENERAL" | "INCIDENT" | "BUG" | "FEATURE_REQUEST" | "ACCESS" | "CONFIGURATION" | "DATA";
export type SenderType = "AGENT" | "REQUESTER" | "SYSTEM";
export type ArticleStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export interface Ticket {
  ticketId: string;
  tenantId: string;
  title: string;
  description: string;
  category: TicketCategory;
  priority: TicketPriority;
  status: TicketStatus;
  reporterRef: string;
  assigneeRef: string | null;
  facilityRef: string | null;
  resolution: string | null;
  requestId: string | null;
  correlationId: string | null;
  escalationLevel: number;
  escalatedAt: string | null;
  escalatedBy: string | null;
  slaBreachedAt: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
}

export interface TicketComment {
  commentId: string;
  ticketId: string;
  authorRef: string;
  body: string;
  createdAt: string;
}

export interface TicketAssignment {
  assignmentId: string;
  ticketId: string;
  assigneeRef: string;
  assignedBy: string;
  assignedAt: string;
}

export interface SupportMessage {
  messageId: string;
  ticketId: string;
  senderRef: string;
  senderType: SenderType;
  body: string;
  createdAt: string;
}

export interface KnowledgeArticle {
  articleId: string;
  tenantId: string;
  title: string;
  body: string;
  category: string;
  status: ArticleStatus;
  authorRef: string;
  tags: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
}

export interface DashboardStats {
  openCount: number;
  inProgressCount: number;
  resolvedCount: number;
  closedCount: number;
  criticalCount: number;
  highCount: number;
  escalatedCount: number;
  avgResolutionHours: number;
}

export interface PagedResult<T> {
  items: T[];
  cursor: string | null;
  limit: number;
  total_elements: number;
  has_more: boolean;
}

export interface CreateTicketParams {
  title: string;
  description: string;
  reporterRef: string;
  category?: TicketCategory;
  priority?: TicketPriority;
  facilityRef?: string;
}

export interface UpdateTicketParams {
  title?: string;
  description?: string;
  category?: TicketCategory;
  priority?: TicketPriority;
  status?: TicketStatus;
  assigneeRef?: string;
  resolution?: string;
}

export interface TicketFilters {
  status?: TicketStatus;
  priority?: TicketPriority;
  category?: TicketCategory;
  assigneeRef?: string;
  cursor?: number;
  limit?: number;
}
