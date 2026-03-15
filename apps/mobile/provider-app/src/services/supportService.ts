/**
 * Support Service — Support ticket management for supervisor mode.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/support/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { SupportTicket, Escalation } from "../types";

interface TicketResource {
  id: string;
  type: "SupportTicket";
  attributes: {
    title: string;
    description: string;
    category: string;
    status: string;
    priority: string;
    reported_by: string;
    assigned_to?: string;
    facility_id: string;
    created_at: string;
    updated_at: string;
  };
}

function mapTicket(r: TicketResource): SupportTicket {
  return {
    id: r.id,
    title: r.attributes.title,
    description: r.attributes.description,
    category: r.attributes.category,
    status: r.attributes.status as SupportTicket["status"],
    priority: r.attributes.priority as SupportTicket["priority"],
    reportedBy: r.attributes.reported_by,
    assignedTo: r.attributes.assigned_to,
    facilityId: r.attributes.facility_id,
    createdAt: r.attributes.created_at,
    updatedAt: r.attributes.updated_at,
  };
}

export async function getSupportTickets(facilityId: string): Promise<SupportTicket[]> {
  const response = await apiClient.get<{ data: TicketResource[] }>(
    `/internal/v1/mobile/provider/support/tickets?facility_id=${facilityId}`
  );
  return response.data.data.map(mapTicket);
}

export async function createSupportTicket(input: {
  title: string;
  description: string;
  category: string;
  priority: string;
  facilityId: string;
}): Promise<SupportTicket> {
  const response = await apiClient.post<{ data: TicketResource }>(
    "/internal/v1/mobile/provider/support/tickets",
    {
      title: input.title,
      description: input.description,
      category: input.category,
      priority: input.priority,
      facility_id: input.facilityId,
    }
  );
  return mapTicket(response.data.data);
}

interface EscalationResource {
  id: string;
  type: "Escalation";
  attributes: {
    escalation_type: string;
    title: string;
    description: string;
    severity: string;
    status: string;
    raised_by: string;
    raised_by_name: string;
    assigned_to?: string;
    assigned_to_name?: string;
    facility_id: string;
    created_at: string;
    resolved_at?: string;
  };
}

function mapEscalation(r: EscalationResource): Escalation {
  return {
    id: r.id,
    type: r.attributes.escalation_type as Escalation["type"],
    title: r.attributes.title,
    description: r.attributes.description,
    severity: r.attributes.severity as Escalation["severity"],
    status: r.attributes.status as Escalation["status"],
    raisedBy: r.attributes.raised_by,
    raisedByName: r.attributes.raised_by_name,
    assignedTo: r.attributes.assigned_to,
    assignedToName: r.attributes.assigned_to_name,
    facilityId: r.attributes.facility_id,
    createdAt: r.attributes.created_at,
    resolvedAt: r.attributes.resolved_at,
  };
}

export async function getEscalations(facilityId: string): Promise<Escalation[]> {
  const response = await apiClient.get<{ data: EscalationResource[] }>(
    `/internal/v1/mobile/provider/support/escalations?facility_id=${facilityId}`
  );
  return response.data.data.map(mapEscalation);
}

export async function acknowledgeEscalation(id: string): Promise<Escalation> {
  const response = await apiClient.post<{ data: EscalationResource }>(
    `/internal/v1/mobile/provider/support/escalations/${id}/acknowledge`
  );
  return mapEscalation(response.data.data);
}

export async function resolveEscalation(id: string, resolution: string): Promise<Escalation> {
  const response = await apiClient.post<{ data: EscalationResource }>(
    `/internal/v1/mobile/provider/support/escalations/${id}/resolve`,
    { resolution }
  );
  return mapEscalation(response.data.data);
}

export async function getFacilityMetrics(facilityId: string) {
  const response = await apiClient.get<{
    data: {
      facility_id: string;
      facility_name: string;
      date: string;
      patients_seen_today: number;
      encounters_open: number;
      encounters_closed: number;
      average_wait_time_minutes: number;
      pending_tasks: number;
      overdue_escalations: number;
      stock_alerts: number;
    };
  }>(`/internal/v1/mobile/provider/supervisor/metrics?facility_id=${facilityId}`);
  const d = response.data.data;
  return {
    facilityId: d.facility_id,
    facilityName: d.facility_name,
    date: d.date,
    patientsSeenToday: d.patients_seen_today,
    encountersOpen: d.encounters_open,
    encountersClosed: d.encounters_closed,
    averageWaitTimeMinutes: d.average_wait_time_minutes,
    pendingTasks: d.pending_tasks,
    overdueEscalations: d.overdue_escalations,
    stockAlerts: d.stock_alerts,
  };
}

export async function getTeamMembers(facilityId: string) {
  const response = await apiClient.get<{
    data: {
      id: string;
      attributes: {
        name: string;
        role: string;
        facility_id: string;
        status: string;
        active_tasks: number;
        completed_today: number;
        last_active: string;
      };
    }[];
  }>(`/internal/v1/mobile/provider/supervisor/team?facility_id=${facilityId}`);
  return response.data.data.map((r) => ({
    id: r.id,
    name: r.attributes.name,
    role: r.attributes.role,
    facilityId: r.attributes.facility_id,
    status: r.attributes.status as "ACTIVE" | "ON_LEAVE" | "OFFLINE",
    activeTasks: r.attributes.active_tasks,
    completedToday: r.attributes.completed_today,
    lastActive: r.attributes.last_active,
  }));
}
