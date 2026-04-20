/**
 * CarePlansSection — View and manage care plans.
 * Maps to web: /ehr/[patientId]/care-plans
 * Priority: HIGH (chronic disease management)
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, Pressable, StyleSheet, ScrollView } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardHeader,
  CardBody,
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
  Progress,
} from "@impilo/mobile-design-system";
import type { CarePlan } from "../../types";

const STATUS_VARIANT: Record<string, "default" | "warning" | "success" | "destructive"> = {
  ACTIVE: "default",
  ON_HOLD: "warning",
  COMPLETED: "success",
  CANCELLED: "destructive",
};

interface CarePlansSectionProps {
  patientId?: string;
}

export function CarePlansSection({ patientId }: CarePlansSectionProps) {
  const [carePlans, setCarePlans] = useState<CarePlan[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      // TODO: Wire to backend service
      // const result = await fetchCarePlans({ patientId });
      // setCarePlans(result.items);
      setCarePlans([]);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, [patientId]);

  useEffect(() => {
    load();
  }, [load]);

  const activePlans = carePlans.filter(p => p.status === "ACTIVE");
  const completedPlans = carePlans.filter(p => p.status === "COMPLETED");

  return (
    <Screen>
      <Header
        title="Care Plans"
        rightElement={
          activePlans.length > 0 ? (
            <Badge>{activePlans.length} active</Badge>
          ) : null
        }
      />
      <ScrollView style={styles.content} contentContainerStyle={styles.contentContainer}>
        {/* Active Care Plans */}
        {activePlans.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Active Plans</Text>
            {activePlans.map((plan) => (
              <Card key={plan.id} style={styles.planCard}>
                <CardHeader
                  title={plan.name}
                  rightElement={
                    <Badge variant={STATUS_VARIANT[plan.status] || "default"}>
                      {plan.status}
                    </Badge>
                  }
                />
                <CardBody>
                  {plan.description && (
                    <Text style={styles.planDescription}>{plan.description}</Text>
                  )}
                  
                  {plan.goals && plan.goals.length > 0 && (
                    <View style={styles.goalsSection}>
                      <Text style={styles.goalsTitle}>Goals ({plan.goals.length})</Text>
                      {plan.goals.map((goal, idx) => (
                        <View key={idx} style={styles.goalItem}>
                          <View style={styles.goalHeader}>
                            <Text style={styles.goalText}>{goal.description}</Text>
                            <Text style={styles.goalStatus}>
                              {goal.targetValue ? `${goal.currentValue}/${goal.targetValue}` : ""}
                            </Text>
                          </View>
                          <Progress 
                            value={(goal.currentValue ?? 0) / (goal.targetValue ?? 1)} 
                            max={1} 
                            size="sm"
                            colorScheme={goal.status === "ACHIEVED" ? "success" : "default"}
                          />
                        </View>
                      ))}
                    </View>
                  )}

                  {plan.tasks && plan.tasks.length > 0 && (
                    <View style={styles.tasksSection}>
                      <Text style={styles.tasksTitle}>Tasks</Text>
                      {plan.tasks.slice(0, 5).map((task, idx) => (
                        <View key={idx} style={styles.taskItem}>
                          <Text style={styles.taskText}>{task.description}</Text>
                          <Badge variant={task.completed ? "success" : "default"}>
                            {task.completed ? "Done" : "Pending"}
                          </Badge>
                        </View>
                      ))}
                      {plan.tasks.length > 5 && (
                        <Text style={styles.moreText}>
                          +{plan.tasks.length - 5} more tasks
                        </Text>
                      )}
                    </View>
                  )}

                  <View style={styles.metaRow}>
                    <Text style={styles.metaText}>
                      Started: {new Date(plan.startDate).toLocaleDateString()}
                    </Text>
                    {plan.endDate && (
                      <Text style={styles.metaText}>
                        → {new Date(plan.endDate).toLocaleDateString()}
                      </Text>
                    )}
                  </View>
                </CardBody>
              </Card>
            ))}
          </View>
        )}

        {/* All Plans List */}
        {isLoading ? (
          <LoadingSpinner size="sm" />
        ) : error ? (
          <ErrorState message={error.message} onRetry={load} />
        ) : carePlans.length === 0 ? (
          <EmptyState
            title="No care plans"
            description="Care plans help manage chronic conditions. Your healthcare provider will create a plan with goals and tasks."
          />
        ) : completedPlans.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Completed Plans</Text>
            {completedPlans.map((plan) => (
              <Card key={plan.id}>
                <CardHeader
                  title={plan.name}
                  rightElement={
                    <Badge variant="success">{plan.status}</Badge>
                  }
                />
                <CardBody>
                  <Text style={styles.planDescription}>{plan.description}</Text>
                  <Text style={styles.completedDate}>
                    Completed: {plan.endDate 
                      ? new Date(plan.endDate).toLocaleDateString()
                      : "N/A"}
                  </Text>
                </CardBody>
              </Card>
            ))}
          </View>
        )}

        {/* Request Plan */}
        <Button
          title="Request Care Plan"
          variant="secondary"
          onPress={() => {}}
        />
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: {
    flex: 1,
  },
  contentContainer: {
    padding: 16,
    gap: 16,
  },
  section: {
    gap: 12,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: "600",
    color: "#6B7280",
  },
  planCard: {
    borderLeftWidth: 3,
    borderLeftColor: "#4F46E5",
  },
  planDescription: {
    fontSize: 14,
    color: "#4B5563",
    marginBottom: 12,
  },
  goalsSection: {
    marginTop: 12,
  },
  goalsTitle: {
    fontSize: 13,
    fontWeight: "600",
    color: "#374151",
    marginBottom: 8,
  },
  goalItem: {
    paddingVertical: 8,
  },
  goalHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 4,
  },
  goalText: {
    fontSize: 13,
    color: "#1F2937",
    flex: 1,
  },
  goalStatus: {
    fontSize: 12,
    color: "#6B7280",
  },
  tasksSection: {
    marginTop: 12,
  },
  tasksTitle: {
    fontSize: 13,
    fontWeight: "600",
    color: "#374151",
    marginBottom: 8,
  },
  taskItem: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingVertical: 6,
  },
  taskText: {
    fontSize: 13,
    color: "#1F2937",
    flex: 1,
  },
  moreText: {
    fontSize: 12,
    color: "#6B7280",
    fontStyle: "italic",
    marginTop: 4,
  },
  metaRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginTop: 12,
    paddingTop: 8,
    borderTopWidth: 1,
    borderTopColor: "#E5E7EB",
  },
  metaText: {
    fontSize: 12,
    color: "#6B7280",
  },
  completedDate: {
    fontSize: 12,
    color: "#6B7280",
    marginTop: 4,
  },
});
