/**
 * Feed Service — Social feed, campaigns, and health content.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/feed/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { FeedItem } from "../types";

const V1 = "/internal/v1/mobile/citizen/feed";

const MOCK_ITEMS: FeedItem[] = [
  {
    id: "feed-1",
    type: "HEALTH_TIP",
    title: "Keep Hydrated this Summer",
    body: "With temperatures rising across Zimbabwe, staying hydrated is crucial for maintaining energy levels and supporting your body's vital functions. Aim for at least 8 glasses (2 liters) of clean, safe water daily. Add a slice of lemon or cucumber for flavor, and limit sugary drinks.",
    author: "Ministry of Health",
    category: "HEALTH_TIP",
    publishedAt: "2026-06-08T10:00:00Z",
    likesCount: 42,
    commentsCount: 7,
    liked: false,
    imageUrl: "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&q=80&w=600",
  },
  {
    id: "feed-2",
    type: "CAMPAIGN",
    title: "National Immunization Drive",
    body: "Protect your children from preventable diseases. The Ministry of Health and Child Care is conducting a nationwide immunization campaign for children under 5. Visit your nearest healthcare facility or mobile clinic today. Vaccines are safe, effective, and completely free.",
    author: "MoHCC Zimbabwe",
    category: "CAMPAIGN",
    publishedAt: "2026-06-07T08:30:00Z",
    likesCount: 128,
    commentsCount: 15,
    liked: true,
    actionUrl: "https://www.mohcc.gov.zw",
    imageUrl: "https://images.unsplash.com/photo-1584515979956-d9f6e5d09982?auto=format&fit=crop&q=80&w=600",
  },
  {
    id: "feed-3",
    type: "REMINDER",
    title: "Schedule Your Annual Health Check",
    body: "Prevention is better than cure. Regular health screenings can identify potential health issues early, when they are easiest to treat. Speak with your family doctor or visit a local clinic to schedule your routine screening, including blood pressure and sugar levels check.",
    author: "Impilo Health Assistant",
    category: "REMINDER",
    publishedAt: "2026-06-06T12:00:00Z",
    likesCount: 19,
    commentsCount: 2,
    liked: false,
  },
  {
    id: "feed-4",
    type: "COMMUNITY",
    title: "Harare Wellness Club Weekly Run",
    body: "Join us for our weekly neighborhood walk/run this Saturday at 6:30 AM starting from the East Park community center. All fitness levels are welcome! Let's build a healthier, happier community together. Bring a water bottle and comfortable shoes.",
    author: "Harare Wellness Club",
    category: "COMMUNITY",
    publishedAt: "2026-06-05T14:15:00Z",
    likesCount: 56,
    commentsCount: 12,
    liked: false,
    imageUrl: "https://images.unsplash.com/photo-1476480862126-209bfaa8edc8?auto=format&fit=crop&q=80&w=600",
  },
  {
    id: "feed-5",
    type: "ANNOUNCEMENT",
    title: "Holiday Hours at Community Clinics",
    body: "Please note that public holiday hours will be in effect next Monday. Emergency services and inpatient care will remain open 24/7. Outpatient departments and routine clinics will be closed, resuming regular operations on Tuesday morning.",
    author: "MoHCC Zimbabwe",
    category: "ANNOUNCEMENT",
    publishedAt: "2026-06-04T09:00:00Z",
    likesCount: 11,
    commentsCount: 1,
    liked: false,
  },
];

let mutableMockItems = [...MOCK_ITEMS];

interface PagedResult<T> {
  data: T[];
  meta: { page: { number: number; size: number; total_elements: number; total_pages: number } };
}

export async function fetchFeed(params: {
  category?: string;
  page?: number;
  size?: number;
} = {}): Promise<{ items: FeedItem[]; totalElements: number; hasNext: boolean }> {
  if (process.env.EXPO_PUBLIC_DEV_BYPASS_AUTH === "true") {
    let filtered = mutableMockItems;
    if (params.category) {
      filtered = filtered.filter(item => item.category === params.category || item.type === params.category);
    }
    return {
      items: filtered,
      totalElements: filtered.length,
      hasNext: false,
    };
  }

  const query: string[] = [];
  if (params.category) query.push(`category=${params.category}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  const response = await apiClient.get<PagedResult<FeedItem>>(`${V1}${qs}`);
  const result = response.data;
  return {
    items: result.data,
    totalElements: result.meta.page.total_elements,
    hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
  };
}

export async function fetchFeedItem(id: string): Promise<FeedItem> {
  if (process.env.EXPO_PUBLIC_DEV_BYPASS_AUTH === "true") {
    const found = mutableMockItems.find(item => item.id === id);
    if (!found) throw new Error("Feed item not found");
    return found;
  }

  const response = await apiClient.get<{ data: FeedItem }>(`${V1}/${encodeURIComponent(id)}`);
  return response.data.data;
}

export async function likeFeedItem(id: string): Promise<void> {
  if (process.env.EXPO_PUBLIC_DEV_BYPASS_AUTH === "true") {
    mutableMockItems = mutableMockItems.map(item =>
      item.id === id ? { ...item, liked: true, likesCount: item.likesCount + 1 } : item
    );
    return;
  }

  await apiClient.post(`${V1}/${encodeURIComponent(id)}/like`);
}

export async function unlikeFeedItem(id: string): Promise<void> {
  if (process.env.EXPO_PUBLIC_DEV_BYPASS_AUTH === "true") {
    mutableMockItems = mutableMockItems.map(item =>
      item.id === id ? { ...item, liked: false, likesCount: Math.max(0, item.likesCount - 1) } : item
    );
    return;
  }

  await apiClient.delete(`${V1}/${encodeURIComponent(id)}/like`);
}
