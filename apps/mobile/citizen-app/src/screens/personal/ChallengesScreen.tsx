/**
 * ChallengesScreen — Wellness challenges and personal goals.
 */
import React, { useState, useEffect, useCallback } from "react";
import { View, Text, TextInput, ScrollView, StyleSheet } from "react-native";
import {
  Card,
  CardHeader,
  CardBody,
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import {
  fetchChallenges,
  joinChallenge,
  fetchMyGoals,
  createGoal,
} from "../../services/challengeService";
import type { WellnessChallenge, WellnessGoal } from "../../types";

export function ChallengesScreen() {
  const [challenges, setChallenges] = useState<WellnessChallenge[]>([]);
  const [goals, setGoals] = useState<WellnessGoal[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [showGoalForm, setShowGoalForm] = useState(false);
  const [joiningId, setJoiningId] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [goalForm, setGoalForm] = useState({
    title: "",
    description: "",
    targetValue: "",
    targetDate: "",
  });

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [ch, gl] = await Promise.all([fetchChallenges(), fetchMyGoals()]);
      setChallenges(ch);
      setGoals(gl);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleJoin = useCallback(
    async (id: string) => {
      setJoiningId(id);
      try {
        await joinChallenge(id);
        await load();
      } catch (err) {
        setError(err instanceof Error ? err : new Error(String(err)));
      } finally {
        setJoiningId(null);
      }
    },
    [load],
  );

  const handleCreateGoal = useCallback(async () => {
    if (!goalForm.title || !goalForm.targetValue || !goalForm.targetDate) return;
    setSubmitting(true);
    try {
      await createGoal({
        title: goalForm.title,
        description: goalForm.description || undefined,
        targetValue: Number(goalForm.targetValue),
        targetDate: goalForm.targetDate,
      });
      setGoalForm({ title: "", description: "", targetValue: "", targetDate: "" });
      setShowGoalForm(false);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setSubmitting(false);
    }
  }, [goalForm, load]);

  if (isLoading) return <LoadingSpinner size="md" />;
  if (error) return <ErrorState title="Error" message={error.message} onRetry={load} />;

  return (
    <ScrollView
      testID="challenges-screen"
      style={styles.scrollView}
      contentContainerStyle={styles.container}
    >
      {/* Active Challenges */}
      <Text style={styles.sectionTitle}>Active Challenges</Text>
      {challenges.length === 0 ? (
        <EmptyState
          title="No challenges"
          message="Check back later for new wellness challenges"
        />
      ) : (
        challenges.map((challenge) => {
          const progress =
            challenge.targetValue > 0
              ? Math.min(
                  ((challenge.participantCount / challenge.targetValue) * 100),
                  100,
                )
              : 0;

          return (
            <Card key={challenge.id}>
              <CardBody>
                <View testID={`challenge-${challenge.id}`} style={styles.challengeContent}>
                  <View style={styles.challengeHeaderRow}>
                    <Text style={styles.challengeTitle}>{challenge.title}</Text>
                    <Badge variant="secondary">{challenge.challengeType}</Badge>
                  </View>
                  {challenge.description ? (
                    <Text style={styles.challengeDesc}>{challenge.description}</Text>
                  ) : null}
                  <Text style={styles.challengeMeta}>
                    {challenge.participantCount} participants
                  </Text>
                  <Text style={styles.challengeMeta}>
                    Goal: {challenge.targetValue.toLocaleString()} {challenge.targetUnit}
                  </Text>
                  <Text style={styles.challengeMeta}>
                    {new Date(challenge.startDate).toLocaleDateString()} —{" "}
                    {new Date(challenge.endDate).toLocaleDateString()}
                  </Text>
                  {/* Progress bar */}
                  <View style={styles.progressBar}>
                    <View
                      style={[styles.progressFill, { width: `${progress}%` }]}
                    />
                  </View>
                  <Button
                    title={joiningId === challenge.id ? "Joining..." : "Join"}
                    variant="primary"
                    size="sm"
                    onPress={() => handleJoin(challenge.id)}
                    disabled={joiningId === challenge.id}
                    testID={`join-challenge-${challenge.id}`}
                  />
                </View>
              </CardBody>
            </Card>
          );
        })
      )}

      {/* My Goals */}
      <View style={styles.goalsSectionHeader}>
        <Text style={styles.sectionTitle}>My Goals</Text>
        <Button
          title={showGoalForm ? "Cancel" : "Create Goal"}
          variant={showGoalForm ? "ghost" : "primary"}
          size="sm"
          onPress={() => setShowGoalForm(!showGoalForm)}
          testID="toggle-goal-form"
        />
      </View>

      {/* Create Goal form */}
      {showGoalForm ? (
        <Card>
          <CardHeader title="Create Goal" />
          <CardBody>
            <View style={styles.formContainer}>
              <TextInput
                style={styles.input}
                placeholder="Goal title"
                value={goalForm.title}
                onChangeText={(v) => setGoalForm({ ...goalForm, title: v })}
                testID="goal-title"
              />
              <TextInput
                style={[styles.input, styles.multilineInput]}
                placeholder="Description (optional)"
                value={goalForm.description}
                onChangeText={(v) =>
                  setGoalForm({ ...goalForm, description: v })
                }
                multiline
                testID="goal-description"
              />
              <TextInput
                style={styles.input}
                placeholder="Target value"
                value={goalForm.targetValue}
                onChangeText={(v) =>
                  setGoalForm({ ...goalForm, targetValue: v })
                }
                keyboardType="numeric"
                testID="goal-target-value"
              />
              <TextInput
                style={styles.input}
                placeholder="Target date (YYYY-MM-DD)"
                value={goalForm.targetDate}
                onChangeText={(v) =>
                  setGoalForm({ ...goalForm, targetDate: v })
                }
                testID="goal-target-date"
              />
              <Button
                title={submitting ? "Creating..." : "Save Goal"}
                variant="primary"
                onPress={handleCreateGoal}
                disabled={
                  submitting ||
                  !goalForm.title ||
                  !goalForm.targetValue ||
                  !goalForm.targetDate
                }
                testID="save-goal"
              />
            </View>
          </CardBody>
        </Card>
      ) : null}

      {goals.length === 0 && !showGoalForm ? (
        <EmptyState
          title="No goals yet"
          message="Create a personal wellness goal to get started"
        />
      ) : (
        goals.map((goal) => {
          const progressPct =
            goal.targetValue > 0
              ? Math.min((goal.currentValue / goal.targetValue) * 100, 100)
              : 0;
          const priorityVariant =
            goal.priority === "HIGH"
              ? "destructive"
              : goal.priority === "MEDIUM"
                ? "secondary"
                : "outline";

          return (
            <Card key={goal.id}>
              <CardBody>
                <View testID={`goal-${goal.id}`} style={styles.goalContent}>
                  <View style={styles.goalHeaderRow}>
                    <Text style={styles.goalTitle}>{goal.title}</Text>
                    <Badge variant={priorityVariant}>{goal.priority}</Badge>
                  </View>
                  {goal.description ? (
                    <Text style={styles.goalDesc}>{goal.description}</Text>
                  ) : null}
                  <Text style={styles.goalProgress}>
                    {Math.round(progressPct)}% complete — {goal.currentValue}/
                    {goal.targetValue} {goal.unit ?? ""}
                  </Text>
                  <View style={styles.progressBar}>
                    <View
                      style={[styles.progressFill, { width: `${progressPct}%` }]}
                    />
                  </View>
                  <Text style={styles.goalDate}>
                    Target: {new Date(goal.targetDate).toLocaleDateString()}
                  </Text>
                </View>
              </CardBody>
            </Card>
          );
        })
      )}
    </ScrollView>
  );
}

const PRIORITY_COLORS: Record<string, string> = {
  HIGH: "#DC2626",
  MEDIUM: "#D97706",
  LOW: "#6B7280",
};

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  container: {
    gap: 12,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: "#111827",
  },
  goalsSectionHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginTop: 8,
  },
  challengeContent: {
    gap: 6,
  },
  challengeHeaderRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  challengeTitle: {
    fontSize: 15,
    fontWeight: "600",
    color: "#111827",
    flex: 1,
  },
  challengeDesc: {
    fontSize: 13,
    color: "#6B7280",
  },
  challengeMeta: {
    fontSize: 12,
    color: "#9CA3AF",
  },
  progressBar: {
    height: 6,
    backgroundColor: "#E5E7EB",
    borderRadius: 3,
    marginVertical: 4,
  },
  progressFill: {
    height: 6,
    backgroundColor: "#2563EB",
    borderRadius: 3,
  },
  formContainer: {
    gap: 12,
  },
  input: {
    borderWidth: 1,
    borderColor: "#D1D5DB",
    borderRadius: 8,
    padding: 12,
    fontSize: 14,
  },
  multilineInput: {
    minHeight: 60,
  },
  goalContent: {
    gap: 6,
  },
  goalHeaderRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  goalTitle: {
    fontSize: 15,
    fontWeight: "600",
    color: "#111827",
    flex: 1,
  },
  goalDesc: {
    fontSize: 13,
    color: "#6B7280",
  },
  goalProgress: {
    fontSize: 13,
    fontWeight: "500",
    color: "#374151",
  },
  goalDate: {
    fontSize: 12,
    color: "#9CA3AF",
  },
});
