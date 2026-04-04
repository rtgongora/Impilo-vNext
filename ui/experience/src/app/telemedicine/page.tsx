"use client";

/**
 * Telemedicine Hub — Lists scheduled, active, and completed telemedicine sessions.
 * Route: /telemedicine | pageTitle: "Telemedicine"
 */

import { useState } from "react";
import Link from "next/link";
import {
  Video,
  Loader2,
  Phone,
  Clock,
  CheckCircle2,
  Calendar,
  User,
  AlertCircle,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useTelemedicineSessions,
  useJoinTelemedicineSession,
  type TelemedicineSession,
} from "@/hooks/queries/useTelemedicine";

const STATUS_STYLES: Record<string, { label: string; className: string }> = {
  SCHEDULED: { label: "Scheduled", className: "bg-blue-100 text-blue-700" },
  IN_PROGRESS: { label: "In Progress", className: "bg-green-100 text-green-700" },
  COMPLETED: { label: "Completed", className: "bg-gray-100 text-gray-600" },
  CANCELLED: { label: "Cancelled", className: "bg-red-100 text-red-600" },
};

type TabFilter = "all" | "SCHEDULED" | "IN_PROGRESS" | "COMPLETED";

export default function TelemedicinePage() {
  const [activeTab, setActiveTab] = useState<TabFilter>("all");
  const statusParam = activeTab === "all" ? undefined : activeTab;
  const { data, isLoading } = useTelemedicineSessions({ status: statusParam });
  const joinSession = useJoinTelemedicineSession();

  const sessions: TelemedicineSession[] = data?.data ?? [];

  const tabs: { key: TabFilter; label: string }[] = [
    { key: "all", label: "All Sessions" },
    { key: "SCHEDULED", label: "Upcoming" },
    { key: "IN_PROGRESS", label: "Active" },
    { key: "COMPLETED", label: "Completed" },
  ];

  function handleJoin(sessionId: string) {
    joinSession.mutate({ id: sessionId });
  }

  return (
    <AppLayout>
      <PageShell title="Telemedicine Hub" subtitle="Manage telemedicine and teleconsult sessions">
        {/* Tabs */}
        <div className="flex gap-1 mb-6 border-b border-gray-200">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                activeTab === tab.key
                  ? "border-blue-600 text-blue-600"
                  : "border-transparent text-gray-500 hover:text-gray-700"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading sessions...</span>
          </div>
        ) : sessions.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Video className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No telemedicine sessions found</p>
          </div>
        ) : (
          <div className="grid gap-4">
            {sessions.map((session) => {
              const attrs = session.attributes;
              const status = STATUS_STYLES[attrs.status] ?? {
                label: attrs.status,
                className: "bg-gray-100 text-gray-600",
              };
              const isJoinable = attrs.status === "SCHEDULED" || attrs.status === "IN_PROGRESS";

              return (
                <div
                  key={session.id}
                  className="bg-white rounded-lg border border-gray-200 p-5 hover:border-gray-300 transition-colors"
                >
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <div className="flex items-center gap-3 mb-2">
                        <div className="w-10 h-10 rounded-full bg-blue-50 flex items-center justify-center">
                          <Video className="w-5 h-5 text-blue-600" />
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-medium text-gray-900 text-sm">
                              {attrs.session_type} Session
                            </span>
                            <span
                              className={`px-2 py-0.5 text-xs font-medium rounded-full ${status.className}`}
                            >
                              {status.label}
                            </span>
                          </div>
                          <div className="flex items-center gap-3 mt-0.5 text-xs text-gray-500">
                            {attrs.patient_id && (
                              <span className="flex items-center gap-1">
                                <User className="w-3 h-3" />
                                Patient: {attrs.patient_id.substring(0, 8)}...
                              </span>
                            )}
                            {attrs.provider_id && (
                              <span className="flex items-center gap-1">
                                Provider: {attrs.provider_id.substring(0, 8)}...
                              </span>
                            )}
                          </div>
                        </div>
                      </div>

                      <div className="ml-13 flex items-center gap-4 text-xs text-gray-500">
                        {attrs.scheduled_at && (
                          <span className="flex items-center gap-1">
                            <Calendar className="w-3 h-3" />
                            Scheduled: {new Date(attrs.scheduled_at).toLocaleString()}
                          </span>
                        )}
                        {attrs.started_at && (
                          <span className="flex items-center gap-1">
                            <Clock className="w-3 h-3" />
                            Started: {new Date(attrs.started_at).toLocaleTimeString()}
                          </span>
                        )}
                        {attrs.duration_seconds != null && attrs.duration_seconds > 0 && (
                          <span className="flex items-center gap-1">
                            <CheckCircle2 className="w-3 h-3" />
                            Duration: {Math.round(attrs.duration_seconds / 60)} min
                          </span>
                        )}
                      </div>

                      {attrs.notes && (
                        <p className="ml-13 mt-2 text-xs text-gray-500 line-clamp-2">
                          {attrs.notes}
                        </p>
                      )}
                    </div>

                    <div className="flex gap-2 ml-4">
                      {isJoinable && (
                        <button
                          onClick={() => handleJoin(session.id)}
                          disabled={joinSession.isPending}
                          className="px-4 py-2 bg-green-600 text-white text-sm font-medium rounded-lg hover:bg-green-700 disabled:opacity-50 transition-colors flex items-center gap-2"
                        >
                          <Phone className="w-4 h-4" />
                          {attrs.status === "IN_PROGRESS" ? "Rejoin" : "Join"}
                        </button>
                      )}
                      <Link
                        href={`/telemedicine/session/${session.id}`}
                        className="px-4 py-2 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors"
                      >
                        Details
                      </Link>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Join feedback */}
        {joinSession.isSuccess && joinSession.data?.data && (
          <div className="fixed bottom-4 right-4 bg-green-600 text-white px-6 py-3 rounded-lg shadow-lg flex items-center gap-2 z-50">
            <CheckCircle2 className="w-5 h-5" />
            <span className="text-sm font-medium">Session joined successfully</span>
            {joinSession.data.data.attributes?.channel && (
              <span className="text-xs opacity-80 ml-2">
                Channel: {joinSession.data.data.attributes.channel}
              </span>
            )}
          </div>
        )}

        {joinSession.isError && (
          <div className="fixed bottom-4 right-4 bg-red-600 text-white px-6 py-3 rounded-lg shadow-lg flex items-center gap-2 z-50">
            <AlertCircle className="w-5 h-5" />
            <span className="text-sm font-medium">Failed to join session</span>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
