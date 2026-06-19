"use client";

import { useState } from "react";
import type { CreateAssignmentRequest } from "@/lib/vashandi/types";
import { useCreateAssignment } from "@/hooks/useVashandi";
import { VashandiFriendlyBlockedState } from "./VashandiFriendlyBlockedState";

interface AssignmentCreateFormProps {
  defaultProfileId?: string;
  onCreated?: () => void;
}

export function AssignmentCreateForm({ defaultProfileId = "", onCreated }: AssignmentCreateFormProps) {
  const mutation = useCreateAssignment();
  const [form, setForm] = useState<CreateAssignmentRequest>({
    workforceProfileId: defaultProfileId,
    assignmentType: "facility_assignment",
    roleTemplateId: "",
    departmentId: "",
    startDate: "",
  });

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const result = await mutation.mutateAsync(form);
    if (result.success) onCreated?.();
  }

  if (mutation.data && !mutation.data.success) {
    return (
      <VashandiFriendlyBlockedState
        state={mutation.data.blockedReason}
        title={mutation.data.friendlyTitle ?? "Assignment not created"}
        description={mutation.data.friendlyMessage ?? mutation.data.integrationMessage ?? "Policy or upstream blocked this action."}
      />
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4 rounded-2xl border border-border bg-card p-5">
      <h3 className="font-medium text-foreground">Create assignment</h3>
      <label className="block text-sm">
        <span className="text-muted-foreground">Workforce profile ID</span>
        <input
          required
          value={form.workforceProfileId}
          onChange={(e) => setForm((prev) => ({ ...prev, workforceProfileId: e.target.value }))}
          className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
        />
      </label>
      <label className="block text-sm">
        <span className="text-muted-foreground">Assignment type</span>
        <input
          required
          value={form.assignmentType}
          onChange={(e) => setForm((prev) => ({ ...prev, assignmentType: e.target.value }))}
          className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
        />
      </label>
      <label className="block text-sm">
        <span className="text-muted-foreground">Role template</span>
        <input
          value={form.roleTemplateId ?? ""}
          onChange={(e) => setForm((prev) => ({ ...prev, roleTemplateId: e.target.value }))}
          className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
        />
      </label>
      <label className="block text-sm">
        <span className="text-muted-foreground">Department</span>
        <input
          value={form.departmentId ?? ""}
          onChange={(e) => setForm((prev) => ({ ...prev, departmentId: e.target.value }))}
          className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
        />
      </label>
      <button
        type="submit"
        disabled={mutation.isPending}
        className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
      >
        {mutation.isPending ? "Submitting…" : "Create assignment"}
      </button>
      {mutation.data?.success ? (
        <p className="text-sm text-success-foreground">Assignment submitted — status: {mutation.data.status}</p>
      ) : null}
    </form>
  );
}
