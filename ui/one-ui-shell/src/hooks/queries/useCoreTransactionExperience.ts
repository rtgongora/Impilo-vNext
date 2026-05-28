"use client";

import { useMemo } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { CoreTransactionBffView } from "../../../../../contracts/core-transaction";
import type {
  CoreTransactionJourneyView,
  CoreTransactionLifecycleStage,
  CoreTransactionTimelineEntry,
  CoreTransactionView,
} from "@/features/core-transaction/types";

interface CoreTransactionListData {
  items?: CoreTransactionBffView[];
}

type AnyRecord = Record<string, unknown>;

function asArray(value: unknown): AnyRecord[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is AnyRecord => typeof item === "object" && item !== null);
  }
  return [];
}

function toLifecycleStage(stage: CoreTransactionBffView["transaction"]["lifecycleStage"]): CoreTransactionLifecycleStage {
  if (stage === "NEED_OR_TRIGGER") return "ENTRY";
  return stage;
}

function toTimelineEntry(
  entry: CoreTransactionBffView["timeline"][number],
  index: number,
  total: number,
): CoreTransactionTimelineEntry {
  return {
    at: entry.at,
    eventName: entry.eventName,
    stateBefore: entry.stateBefore,
    stateAfter: entry.stateAfter,
    actorId: entry.actorId,
    journeyType: entry.journeyType,
    journeyStage: entry.journeyStage,
    status: index === total - 1 ? "CURRENT" : "COMPLETED",
  };
}

function toJourney(journey: CoreTransactionBffView["journeys"]["person"]): CoreTransactionJourneyView {
  return {
    journeyType: journey.journeyType,
    currentStage: journey.currentStage,
    stages: journey.stages,
    visibleToActor: journey.visibleToActor,
    nextActions: journey.nextActions.map((action) => ({ code: action.code, label: action.label })),
    blockers: journey.blockers,
    timelineEntries: journey.timelineEntries.map((entry, index) =>
      toTimelineEntry(entry, index, journey.timelineEntries.length),
    ),
    completionStatus: journey.completionStatus,
  };
}

function toCoreTransactionView(input: CoreTransactionBffView): CoreTransactionView {
  const serviceCode = input.transaction.context.registryContext.serviceCode;
  const timeline = input.timeline.map((entry, index) =>
    toTimelineEntry(entry, index, input.timeline.length),
  );
  return {
    fixture: false,
    transaction: {
      id: input.transaction.id,
      type: input.transaction.type,
      currentState: input.transaction.currentState,
      lifecycleStage: toLifecycleStage(input.transaction.lifecycleStage),
      serviceCode,
    },
    journeys: {
      person: toJourney(input.journeys.person),
      provider: toJourney(input.journeys.provider),
      platform: toJourney(input.journeys.platform),
    },
    clientSummary: {
      clientRef: input.clientSummary.clientRef,
      clientAlias: input.clientSummary.clientAlias,
      identityStatus: input.clientSummary.provisionalIdentity ? "PROVISIONAL" : "RESOLVED",
    },
    providerContext: {
      providerRef: input.providerContext?.providerRef,
      actorId: input.transaction.actor.actorId,
      roleCode: input.providerContext?.roleCode,
    },
    facilityContext: {
      facilityId: input.facilityContext.facilityId,
      workspaceId: input.facilityContext.workspaceId,
    },
    trustContext: {
      purposeOfUse: input.trustContext.purposeOfUse,
      consentStatus: input.trustContext.consentGranted === true ? "GRANTED" : "NOT_GRANTED",
      accessStatus: input.trustContext.accessDecision ?? "ALLOW",
    },
    clinicalContext: {
      encounterId: input.clinicalContext?.encounterId,
      ordersPending: input.orders.length,
      resultsPending: input.clinicalContext?.resultRefs?.length ?? 0,
    },
    financialContext: {
      costingStatus: input.financialContext?.costingStatus ?? "NOT_APPLICABLE",
      paymentStatus: input.financialContext?.paymentStatus ?? "NOT_APPLICABLE",
      claimStatus: input.financialContext?.claimStatus ?? "NOT_APPLICABLE",
    },
    followUp: {
      required: input.followUp?.followUpRequired ?? false,
      note: input.followUp?.nextFollowUpDate,
    },
    timeline,
    nextActions: input.nextActions.map((action) => ({ code: action.code, label: action.label })),
    permissions: input.permissions.map((permission) => ({ code: permission.code, allowed: permission.allowed })),
    auditSummary: {
      correlationId: input.auditSummary.correlationId,
      sourceSystem: input.auditSummary.sourceSystem,
      auditRequired: input.auditSummary.auditRequired,
    },
    offlineSyncStatus: input.offlineSyncStatus,
    failureModes: input.failureModes,
    nompilo: input.nompilo,
    nompiloCommand: input.nompiloCommand,
  };
}

function normalizeCollection(raw: unknown): AnyRecord[] {
  if (Array.isArray(raw)) {
    return raw;
  }
  if (raw && typeof raw === "object") {
    const record = raw as AnyRecord;
    if (Array.isArray(record.items)) return record.items as AnyRecord[];
    if (Array.isArray(record.data)) return record.data as AnyRecord[];
    if (Array.isArray(record.content)) return record.content as AnyRecord[];
    if (Array.isArray(record.tasks)) return record.tasks as AnyRecord[];
  }
  return [];
}

export function useCoreTransactionList(filters?: { state?: string; type?: string }) {
  return useQuery({
    queryKey: ["core-transaction", "list", filters?.state ?? "", filters?.type ?? ""],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (filters?.state) params.set("state", filters.state);
      if (filters?.type) params.set("type", filters.type);
      const query = params.toString();
      const url = query
        ? `/internal/v1/core-transactions?${query}`
        : "/internal/v1/core-transactions";
      return apiClient.get<ApiResponse<CoreTransactionListData>>(url);
    },
  });
}

export function useCoreTransactionFeed(filters?: { state?: string; type?: string }) {
  const query = useCoreTransactionList(filters);
  const items = useMemo(
    () =>
      asArray(query.data?.data.items)
        .map((item) => item as unknown as CoreTransactionBffView)
        .map(toCoreTransactionView),
    [query.data?.data.items],
  );
  return { ...query, items };
}

export function useWorkflowOperatorFeed(
  filtersOrStatus?: string | { status?: string; type?: string },
) {
  const filters =
    typeof filtersOrStatus === "string"
      ? { status: filtersOrStatus }
      : filtersOrStatus;
  const query = useQuery({
    queryKey: [
      "operations",
      "workflows",
      filters?.status ?? "ALL",
      filters?.type ?? "ALL",
    ],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (filters?.status) params.set("status", filters.status);
      if (filters?.type) params.set("type", filters.type);
      const query = params.toString();
      const qs = query ? `?${query}` : "";
      return apiClient.get<ApiResponse<unknown>>(`/internal/v1/workflows${qs}`);
    },
  });

  const items = useMemo(
    () => normalizeCollection(query.data?.data),
    [query.data?.data],
  );

  return { ...query, items };
}

export function useDispatchOperatorFeed(
  filtersOrStatus?: string | { status?: string; assignee?: string },
) {
  const filters =
    typeof filtersOrStatus === "string"
      ? { status: filtersOrStatus }
      : filtersOrStatus;
  const query = useQuery({
    queryKey: [
      "operations",
      "dispatch",
      filters?.status ?? "ALL",
      filters?.assignee ?? "ALL",
    ],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (filters?.status) params.set("status", filters.status);
      if (filters?.assignee) params.set("assigneeId", filters.assignee);
      const query = params.toString();
      const qs = query ? `?${query}` : "";
      return apiClient.get<ApiResponse<unknown>>(`/internal/v1/dispatch/tasks${qs}`);
    },
  });

  const items = useMemo(
    () => normalizeCollection(query.data?.data),
    [query.data?.data],
  );

  return { ...query, items };
}

export function useWorkflowDefinitions() {
  const query = useQuery({
    queryKey: ["operations", "workflows", "definitions"],
    queryFn: async () => apiClient.get<ApiResponse<unknown>>("/internal/v1/workflows/definitions"),
  });

  const items = useMemo(
    () => normalizeCollection(query.data?.data),
    [query.data?.data],
  );

  return { ...query, items };
}

export function useWorkflowInstances(filters?: { status?: string }) {
  const query = useQuery({
    queryKey: ["operations", "workflows", "instances", filters?.status ?? "ALL"],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (filters?.status) params.set("status", filters.status);
      const query = params.toString();
      const qs = query ? `?${query}` : "";
      return apiClient.get<ApiResponse<unknown>>(`/internal/v1/workflows/instances${qs}`);
    },
  });

  const items = useMemo(
    () => normalizeCollection(query.data?.data),
    [query.data?.data],
  );

  return { ...query, items };
}

export function useStartWorkflowInstance() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: AnyRecord) =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/workflows/instances", payload),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["operations", "workflows"] });
    },
  });
}

export function useTransitionWorkflowInstance() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ instanceId, payload }: { instanceId: string; payload: AnyRecord }) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/workflows/instances/${encodeURIComponent(instanceId)}/transition`,
        payload,
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["operations", "workflows"] });
    },
  });
}
