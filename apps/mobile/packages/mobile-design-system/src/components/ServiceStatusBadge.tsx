import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { Badge } from "./Badge";

export type ServiceWiringBadgeStatus =
  | "fullyWired"
  | "partiallyWired"
  | "deepLinked"
  | "backendMissing"
  | "blocked"
  | "planned";

const STATUS_LABELS: Record<ServiceWiringBadgeStatus, string> = {
  fullyWired: "Live",
  partiallyWired: "Partial",
  deepLinked: "Web",
  backendMissing: "Pending",
  blocked: "Unavailable",
  planned: "Planned",
};

const STATUS_VARIANT: Record<ServiceWiringBadgeStatus, "success" | "warning" | "secondary" | "outline" | "destructive"> = {
  fullyWired: "success",
  partiallyWired: "warning",
  deepLinked: "secondary",
  backendMissing: "warning",
  blocked: "destructive",
  planned: "outline",
};

export interface ServiceStatusBadgeProps {
  status: ServiceWiringBadgeStatus;
  compact?: boolean;
}

export function ServiceStatusBadge({ status, compact }: ServiceStatusBadgeProps) {
  return (
    <Badge variant={STATUS_VARIANT[status]} size={compact ? "sm" : "md"}>
      {STATUS_LABELS[status]}
    </Badge>
  );
}

export interface ServiceStatusLegendProps {
  statuses: ServiceWiringBadgeStatus[];
}

export function ServiceStatusLegend({ statuses }: ServiceStatusLegendProps) {
  return (
    <View style={styles.legend}>
      {statuses.map((status) => (
        <View key={status} style={styles.legendItem}>
          <ServiceStatusBadge status={status} compact />
          <Text style={styles.legendText}>{STATUS_LABELS[status]}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  legend: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  legendItem: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  legendText: {
    fontSize: 11,
    color: "#6B7280",
  },
});
