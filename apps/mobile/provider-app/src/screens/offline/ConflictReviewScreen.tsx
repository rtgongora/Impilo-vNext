/**
 * ConflictReviewScreen — Review and resolve sync conflicts.
 */

import React, { useState, useCallback } from "react";
import { Screen, Header, Card, CardBody, Button, Badge, EmptyState, ErrorState } from "@impilo/mobile-design-system";
import { useConflicts } from "@impilo/mobile-offline";
import type { ConflictItem } from "../../types";

export function ConflictReviewScreen() {
  const { conflicts: rawConflicts, resolveConflict } = useConflicts();
  const [error, setError] = useState<string | null>(null);

  const conflicts: ConflictItem[] = (rawConflicts ?? []).map((c: { id: string; resourceType: string; resourceId: string; localVersion: Record<string, unknown>; serverVersion: Record<string, unknown>; conflictFields: string[]; detectedAt: string }) => ({
    id: c.id,
    resourceType: c.resourceType,
    resourceId: c.resourceId,
    localVersion: c.localVersion,
    serverVersion: c.serverVersion,
    conflictFields: c.conflictFields,
    detectedAt: c.detectedAt,
  }));

  const handleResolve = useCallback(async (id: string, resolution: "LOCAL_WINS" | "SERVER_WINS") => {
    setError(null);
    try {
      await resolveConflict(id, resolution);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to resolve conflict");
    }
  }, [resolveConflict]);

  return React.createElement(
    Screen, null,
    React.createElement(Header, { title: "Conflict Review" }),
    React.createElement("div", { "data-testid": "conflict-review-screen", style: { padding: "16px" } },
      error ? React.createElement(ErrorState, { title: "Error", message: error }) : null,
      conflicts.length === 0
        ? React.createElement(EmptyState, { title: "No conflicts", message: "All data is synchronized" })
        : conflicts.map((conflict) =>
            React.createElement(Card, { key: conflict.id },
              React.createElement(CardBody, null,
                React.createElement("div", { "data-testid": `conflict-${conflict.id}` },
                  React.createElement("div", { style: { display: "flex", justifyContent: "space-between", marginBottom: "8px" } },
                    React.createElement("strong", null, `${conflict.resourceType} Conflict`),
                    React.createElement(Badge, { variant: "destructive", children: `${conflict.conflictFields.length} fields` })
                  ),
                  React.createElement("p", { style: { fontSize: "12px", color: "#6B7280" } }, `Resource: ${conflict.resourceId}`),
                  React.createElement("p", { style: { fontSize: "12px", color: "#9CA3AF" } }, `Detected: ${new Date(conflict.detectedAt).toLocaleString()}`),

                  // Conflict diff
                  React.createElement("div", { style: { margin: "12px 0" } },
                    React.createElement("table", { style: { width: "100%", borderCollapse: "collapse", fontSize: "12px" } },
                      React.createElement("thead", null,
                        React.createElement("tr", null,
                          React.createElement("th", { style: { textAlign: "left", padding: "4px", borderBottom: "1px solid #E5E7EB" } }, "Field"),
                          React.createElement("th", { style: { textAlign: "left", padding: "4px", borderBottom: "1px solid #E5E7EB" } }, "Local"),
                          React.createElement("th", { style: { textAlign: "left", padding: "4px", borderBottom: "1px solid #E5E7EB" } }, "Server")
                        )
                      ),
                      React.createElement("tbody", null,
                        conflict.conflictFields.map((field) =>
                          React.createElement("tr", { key: field },
                            React.createElement("td", { style: { padding: "4px", fontWeight: "600" } }, field),
                            React.createElement("td", { style: { padding: "4px", color: "#3B82F6" } }, String(conflict.localVersion[field] ?? "—")),
                            React.createElement("td", { style: { padding: "4px", color: "#F59E0B" } }, String(conflict.serverVersion[field] ?? "—"))
                          )
                        )
                      )
                    )
                  ),

                  // Resolution buttons
                  React.createElement("div", { style: { display: "flex", gap: "8px" } },
                    React.createElement(Button, {
                      title: "Keep Local",
                      variant: "primary",
                      size: "sm",
                      onPress: () => handleResolve(conflict.id, "LOCAL_WINS"),
                      testID: `local-wins-${conflict.id}`,
                    }),
                    React.createElement(Button, {
                      title: "Use Server",
                      variant: "outline",
                      size: "sm",
                      onPress: () => handleResolve(conflict.id, "SERVER_WINS"),
                      testID: `server-wins-${conflict.id}`,
                    })
                  )
                )
              )
            )
          )
    )
  );
}
