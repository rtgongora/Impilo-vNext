"use client";

import { AppLayout } from "@/components/AppLayout";
import { LearningDataTable, type LearningTableColumn, type LearningTableFilter } from "@/components/learning/LearningDataTable";
import { PageShell } from "@/components/PageShell";
import { useFundoPathways } from "@/hooks/queries/useFundoLms";

export default function PathwaysPage() {
  const { data, isLoading } = useFundoPathways();
  const items = (((data?.data as Record<string, unknown>)?.items as Array<Record<string, unknown>>) ?? []).filter(Boolean);
  const columns: Array<LearningTableColumn<Record<string, unknown>>> = [
    {
      key: "title",
      label: "Pathway",
      render: (pathway) => <span className="font-medium text-gray-900">{String(pathway.title ?? pathway.code ?? "Pathway")}</span>,
      searchText: (pathway) => `${String(pathway.title ?? "")} ${String(pathway.code ?? "")} ${String(pathway.description ?? "")}`,
    },
    { key: "code", label: "Code", render: (pathway) => String(pathway.code ?? "-") },
    { key: "status", label: "Status", render: (pathway) => String(pathway.status ?? "PUBLISHED") },
    { key: "roles", label: "Roles", render: (pathway) => String(pathway.targetRoles ?? "-") },
  ];
  const filters: Array<LearningTableFilter<Record<string, unknown>>> = [
    {
      key: "status",
      label: "Status",
      valueFor: (pathway) => String(pathway.status ?? "PUBLISHED"),
      options: ["DRAFT", "PUBLISHED", "ARCHIVED"].map((value) => ({ label: value, value })),
    },
  ];

  return (
    <AppLayout>
      <PageShell title="Pathways" subtitle="Available and assigned pathways with ordered learning sequences.">
        {isLoading ? <p className="text-sm text-gray-600">Loading…</p> : null}
        <LearningDataTable
          rows={items}
          columns={columns}
          filters={filters}
          emptyText="No pathways are available yet."
          searchPlaceholder="Search pathways"
          getRowKey={(pathway, index) => String(pathway.id ?? `pathway-${index}`)}
          getRowHref={(pathway) => {
            const id = String(pathway.id ?? "");
            return id ? `/learning/pathways/${id}` : undefined;
          }}
          actionLabel="Open pathway"
        />
      </PageShell>
    </AppLayout>
  );
}
