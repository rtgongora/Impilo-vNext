/**
 * Notification Service — Manages push registration, notification feed, and preferences.
 *
 * Backend: notification-service (/internal/v1/notifications/*)
 */

import { apiClient, fetchPage } from "@impilo/mobile-api-client";
import type { PagedResponse } from "@impilo/mobile-trust";
import type {
  Notification,
  NotificationPreference,
  PushRegistration,
} from "./types";

const V1 = "/internal/v1/notifications";

/**
 * Register device for push notifications (FCM/APNs).
 */
export async function registerDevice(registration: PushRegistration): Promise<void> {
  await apiClient.post(`${V1}/push/register`, registration);
}

/**
 * Unregister device from push notifications.
 */
export async function unregisterDevice(deviceId: string): Promise<void> {
  await apiClient.delete(`${V1}/push/devices/${encodeURIComponent(deviceId)}`);
}

/**
 * Fetch the in-app notification feed with pagination.
 */
export async function fetchNotifications(
  params: { page?: number; size?: number; unreadOnly?: boolean } = {}
): Promise<PagedResponse<Notification>> {
  const query: string[] = [];
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  if (params.unreadOnly) query.push("unreadOnly=true");
  const qs = query.length > 0 ? `?${query.join("&")}` : "";
  const response = await apiClient.get<PagedResponse<Notification>>(`${V1}/inbox${qs}`);
  return response.data;
}

/**
 * Mark a single notification as read.
 */
export async function markRead(notificationId: string): Promise<void> {
  await apiClient.patch(`${V1}/inbox/${encodeURIComponent(notificationId)}/read`);
}

/**
 * Mark all notifications as read.
 */
export async function markAllRead(): Promise<void> {
  await apiClient.post(`${V1}/inbox/read-all`);
}

/**
 * Get the unread notification count.
 */
export async function getUnreadCount(): Promise<number> {
  const response = await apiClient.get<{ count: number }>(`${V1}/inbox/unread-count`);
  return response.data.count;
}

/**
 * Get notification preferences.
 */
export async function getPreferences(): Promise<NotificationPreference[]> {
  const response = await apiClient.get<NotificationPreference[]>(`${V1}/preferences`);
  return response.data;
}

/**
 * Update a notification preference.
 */
export async function updatePreference(
  preference: Pick<NotificationPreference, "category"> & Partial<Omit<NotificationPreference, "category">>
): Promise<void> {
  await apiClient.put(`${V1}/preferences/${encodeURIComponent(preference.category)}`, preference);
}
