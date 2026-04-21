/**
 * MarketplaceScreen — Service discovery, booking, and order tracking.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  Screen,
  Header,
  Card,
  CardHeader,
  CardBody,
  Button,
  Badge,
  TextField,
  Select,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import {
  fetchServices,
  fetchServiceRequests,
  requestService,
  cancelServiceRequest,
  type MarketplaceService,
  type ServiceRequest,
} from "../../services/marketplaceService";
import type { MarketplaceService as MktService, ServiceRequest as SvcReq } from "../../types";
import { CartScreen } from "./CartScreen";

type MarketplaceTab = "browse" | "requests" | "cart";

const CATEGORIES = [
  { label: "All", value: "" },
  { label: "Consultation", value: "CONSULTATION" },
  { label: "Lab Tests", value: "LAB_TESTS" },
  { label: "Pharmacy", value: "PHARMACY" },
  { label: "Imaging", value: "IMAGING" },
  { label: "Wellness", value: "WELLNESS" },
  { label: "Home Care", value: "HOME_CARE" },
];

const REQUEST_STATUS_COLORS: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  PENDING: "outline",
  CONFIRMED: "default",
  IN_PROGRESS: "secondary",
  COMPLETED: "secondary",
  CANCELLED: "destructive",
};

const TABS: { id: MarketplaceTab; label: string; icon: React.ComponentProps<typeof Ionicons>["name"] }[] = [
  { id: "browse", label: "Browse", icon: "search-outline" },
  { id: "requests", label: "Requests", icon: "receipt-outline" },
  { id: "cart", label: "Cart", icon: "cart-outline" },
];

export function MarketplaceScreen() {
  const [tab, setTab] = useState<MarketplaceTab>("browse");
  const [services, setServices] = useState<MktService[]>([]);
  const [requests, setRequests] = useState<SvcReq[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [selectedService, setSelectedService] = useState<MktService | null>(null);
  const [preferredDate, setPreferredDate] = useState("");
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const loadServices = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchServices({
        category: category || undefined,
        search: search || undefined,
        size: 50,
      });
      setServices(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, [category, search]);

  const loadRequests = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchServiceRequests({ size: 50 });
      setRequests(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (tab === "browse") loadServices();
    else loadRequests();
  }, [tab, loadServices, loadRequests]);

  const handleRequest = useCallback(async () => {
    if (!selectedService) return;
    setSubmitting(true);
    try {
      await requestService({
        serviceId: selectedService.id,
        preferredDate: preferredDate || undefined,
        notes: notes || undefined,
      });
      setSelectedService(null);
      setPreferredDate("");
      setNotes("");
      setTab("requests");
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setSubmitting(false);
    }
  }, [selectedService, preferredDate, notes]);

  const handleCancelRequest = useCallback(async (id: string) => {
    try {
      await cancelServiceRequest(id);
      loadRequests();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, [loadRequests]);

  return (
    <Screen>
      <Header title="Health Services" />
      <ScrollView testID="marketplace-screen" style={styles.scrollView} contentContainerStyle={styles.container}>
        <View style={styles.tabBar}>
          {TABS.map((t) => (
            <TouchableOpacity
              key={t.id}
              onPress={() => setTab(t.id)}
              style={[styles.tabPill, tab === t.id && styles.tabPillActive]}
              testID={`marketplace-tab-${t.id}`}
            >
              <Ionicons
                name={t.icon}
                size={15}
                color={tab === t.id ? "#059669" : "#6B7280"}
              />
              <Text style={[styles.tabPillText, tab === t.id && styles.tabPillTextActive]}>
                {t.label}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {error ? (
          <ErrorState title="Error" message={error.message} onRetry={() => tab === "browse" ? loadServices() : loadRequests()} />
        ) : null}

        {selectedService ? (
          <View style={styles.bookingCard}>
            <Text style={styles.bookingTitle}>{`Book: ${selectedService.name}`}</Text>
            <Text style={styles.bookingDescription}>{selectedService.description}</Text>
            {selectedService.price !== undefined ? (
              <View style={styles.priceBadgeRow}>
                <View style={styles.priceBadge}>
                  <Ionicons name="pricetag-outline" size={12} color="#059669" />
                  <Text style={styles.priceBadgeText}>{`${selectedService.currency} ${selectedService.price}`}</Text>
                </View>
              </View>
            ) : null}
            <View style={styles.formContainer}>
              <TextField
                label="Preferred Date"
                value={preferredDate}
                onChange={setPreferredDate}
                placeholder="YYYY-MM-DD"
                testID="booking-preferred-date"
              />
              <TextField
                label="Notes (optional)"
                value={notes}
                onChange={setNotes}
                placeholder="Any special requirements"
                testID="booking-notes"
              />
              <View style={styles.bookingBtnRow}>
                <TouchableOpacity
                  onPress={handleRequest}
                  disabled={submitting}
                  style={[styles.confirmBtn, submitting && styles.confirmBtnDisabled]}
                  testID="confirm-booking"
                >
                  <Text style={styles.confirmBtnText}>{submitting ? "Submitting..." : "Confirm Booking"}</Text>
                </TouchableOpacity>
                <TouchableOpacity onPress={() => setSelectedService(null)} style={styles.cancelBtn}>
                  <Text style={styles.cancelBtnText}>Cancel</Text>
                </TouchableOpacity>
              </View>
            </View>
          </View>
        ) : null}

        {tab === "browse" ? (
          <>
            <View style={styles.searchRow}>
              <Ionicons name="search-outline" size={18} color="#9CA3AF" style={styles.searchIcon} />
              <TextField
                value={search}
                onChange={setSearch}
                placeholder="Search services..."
                testID="marketplace-search"
              />
            </View>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.categoryScroll} contentContainerStyle={styles.categoryContent}>
              {CATEGORIES.map((cat) => (
                <TouchableOpacity
                  key={cat.value}
                  onPress={() => setCategory(cat.value)}
                  style={[styles.categoryChip, category === cat.value && styles.categoryChipActive]}
                  testID={`category-${cat.value || "all"}`}
                >
                  <Text style={[styles.categoryChipText, category === cat.value && styles.categoryChipTextActive]}>
                    {cat.label}
                  </Text>
                </TouchableOpacity>
              ))}
            </ScrollView>
            {isLoading ? (
              <View style={styles.centered}>
                <LoadingSpinner size="md" />
              </View>
            ) : services.length === 0 ? (
              <View style={styles.emptyContainer}>
                <Ionicons name="storefront-outline" size={48} color="#D1D5DB" />
                <Text style={styles.emptyTitle}>No services found</Text>
                <Text style={styles.emptyMessage}>Try adjusting your search or category filter</Text>
              </View>
            ) : (
              services.map((svc) => (
                <View key={svc.id} style={styles.serviceCard} testID={`service-${svc.id}`}>
                  <View style={styles.serviceCardTop}>
                    <View style={styles.serviceInfo}>
                      <Text style={styles.serviceName}>{svc.name}</Text>
                      <View style={styles.serviceBadgeRow}>
                        <View style={styles.categoryBadge}>
                          <Text style={styles.categoryBadgeText}>{svc.category}</Text>
                        </View>
                        {svc.price !== undefined ? (
                          <View style={styles.servicePriceBadge}>
                            <Text style={styles.servicePriceBadgeText}>{`${svc.currency} ${svc.price}`}</Text>
                          </View>
                        ) : null}
                      </View>
                    </View>
                    {svc.available ? (
                      <TouchableOpacity
                        style={styles.bookBtn}
                        onPress={() => setSelectedService(svc)}
                        testID={`book-${svc.id}`}
                      >
                        <Text style={styles.bookBtnText}>Book</Text>
                      </TouchableOpacity>
                    ) : (
                      <Badge variant="outline">Unavailable</Badge>
                    )}
                  </View>
                  <Text style={styles.serviceDescText} numberOfLines={2}>{svc.description}</Text>
                  <View style={styles.serviceMeta}>
                    <Ionicons name="business-outline" size={12} color="#9CA3AF" />
                    <Text style={styles.facilityText}>{svc.facilityName}</Text>
                    {svc.rating !== undefined ? (
                      <>
                        <Ionicons name="star" size={12} color="#D97706" />
                        <Text style={styles.ratingText}>{svc.rating.toFixed(1)}</Text>
                      </>
                    ) : null}
                  </View>
                </View>
              ))
            )}
          </>
        ) : null}

        {tab === "requests" ? (
          <>
            {isLoading ? (
              <View style={styles.centered}>
                <LoadingSpinner size="md" />
              </View>
            ) : requests.length === 0 ? (
              <View style={styles.emptyContainer}>
                <Ionicons name="receipt-outline" size={48} color="#D1D5DB" />
                <Text style={styles.emptyTitle}>No requests yet</Text>
                <Text style={styles.emptyMessage}>Browse services and make your first booking</Text>
              </View>
            ) : (
              requests.map((req) => (
                <View key={req.id} style={styles.requestCard} testID={`request-${req.id}`}>
                  <View style={styles.requestTop}>
                    <View style={styles.requestInfo}>
                      <Text style={styles.requestServiceName}>{req.serviceName}</Text>
                      <Text style={styles.requestFacility}>{req.facilityName}</Text>
                      <View style={styles.requestMetaRow}>
                        <Ionicons name="calendar-outline" size={12} color="#9CA3AF" />
                        <Text style={styles.requestDate}>
                          {new Date(req.requestedAt).toLocaleDateString()}
                        </Text>
                      </View>
                      {req.scheduledAt ? (
                        <View style={styles.requestMetaRow}>
                          <Ionicons name="time-outline" size={12} color="#9CA3AF" />
                          <Text style={styles.scheduledText}>
                            {new Date(req.scheduledAt).toLocaleString()}
                          </Text>
                        </View>
                      ) : null}
                      {req.trackingNumber ? (
                        <View style={styles.requestMetaRow}>
                          <Ionicons name="barcode-outline" size={12} color="#9CA3AF" />
                          <Text style={styles.trackingText}>{req.trackingNumber}</Text>
                        </View>
                      ) : null}
                    </View>
                    <View style={styles.requestActions}>
                      <Badge variant={REQUEST_STATUS_COLORS[req.status] ?? "outline"}>
                        {req.status}
                      </Badge>
                      {req.status === "PENDING" || req.status === "CONFIRMED" ? (
                        <TouchableOpacity
                          style={styles.cancelRequestBtn}
                          onPress={() => handleCancelRequest(req.id)}
                          testID={`cancel-request-${req.id}`}
                        >
                          <Text style={styles.cancelRequestBtnText}>Cancel</Text>
                        </TouchableOpacity>
                      ) : null}
                    </View>
                  </View>
                </View>
              ))
            )}
          </>
        ) : null}

        {tab === "cart" ? <CartScreen /> : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
    backgroundColor: "#F8FAFC",
  },
  container: {
    padding: 16,
    gap: 12,
  },
  tabBar: {
    flexDirection: "row",
    gap: 8,
    marginBottom: 4,
  },
  tabPill: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 5,
    paddingVertical: 9,
    borderRadius: 20,
    backgroundColor: "#F3F4F6",
  },
  tabPillActive: {
    backgroundColor: "#D1FAE5",
  },
  tabPillText: {
    fontSize: 13,
    fontWeight: "500",
    color: "#6B7280",
  },
  tabPillTextActive: {
    color: "#059669",
    fontWeight: "600",
  },
  bookingCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 8,
    elevation: 3,
    gap: 10,
  },
  bookingTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: "#111827",
  },
  bookingDescription: {
    fontSize: 14,
    color: "#374151",
  },
  priceBadgeRow: {
    flexDirection: "row",
  },
  priceBadge: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: "#D1FAE5",
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 10,
  },
  priceBadgeText: {
    fontSize: 13,
    fontWeight: "600",
    color: "#059669",
  },
  formContainer: {
    gap: 12,
  },
  bookingBtnRow: {
    flexDirection: "row",
    gap: 8,
  },
  confirmBtn: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#059669",
    paddingVertical: 11,
    borderRadius: 12,
  },
  confirmBtnDisabled: {
    opacity: 0.5,
  },
  confirmBtnText: {
    fontSize: 14,
    fontWeight: "600",
    color: "#FFFFFF",
  },
  cancelBtn: {
    paddingHorizontal: 16,
    paddingVertical: 11,
    borderRadius: 12,
    backgroundColor: "#F3F4F6",
    alignItems: "center",
    justifyContent: "center",
  },
  cancelBtnText: {
    fontSize: 14,
    fontWeight: "500",
    color: "#6B7280",
  },
  searchRow: {
    position: "relative",
  },
  searchIcon: {
    position: "absolute",
    left: 12,
    top: 12,
    zIndex: 1,
  },
  categoryScroll: {
    flexGrow: 0,
  },
  categoryContent: {
    gap: 8,
    paddingBottom: 4,
  },
  categoryChip: {
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 20,
    backgroundColor: "#F3F4F6",
  },
  categoryChipActive: {
    backgroundColor: "#D1FAE5",
  },
  categoryChipText: {
    fontSize: 13,
    fontWeight: "500",
    color: "#6B7280",
  },
  categoryChipTextActive: {
    color: "#059669",
    fontWeight: "600",
  },
  centered: {
    alignItems: "center",
    paddingVertical: 40,
  },
  emptyContainer: {
    alignItems: "center",
    paddingVertical: 56,
    gap: 10,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: "600",
    color: "#374151",
  },
  emptyMessage: {
    fontSize: 14,
    color: "#9CA3AF",
    textAlign: "center",
    paddingHorizontal: 24,
  },
  serviceCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 14,
    padding: 14,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
    gap: 8,
  },
  serviceCardTop: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    gap: 10,
  },
  serviceInfo: {
    flex: 1,
    gap: 6,
  },
  serviceName: {
    fontSize: 15,
    fontWeight: "700",
    color: "#111827",
  },
  serviceBadgeRow: {
    flexDirection: "row",
    gap: 6,
    flexWrap: "wrap",
  },
  categoryBadge: {
    backgroundColor: "#EFF6FF",
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 10,
  },
  categoryBadgeText: {
    fontSize: 11,
    fontWeight: "600",
    color: "#1E40AF",
  },
  servicePriceBadge: {
    backgroundColor: "#D1FAE5",
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 10,
  },
  servicePriceBadgeText: {
    fontSize: 11,
    fontWeight: "600",
    color: "#059669",
  },
  bookBtn: {
    backgroundColor: "#059669",
    paddingHorizontal: 16,
    paddingVertical: 7,
    borderRadius: 12,
  },
  bookBtnText: {
    fontSize: 13,
    fontWeight: "600",
    color: "#FFFFFF",
  },
  serviceDescText: {
    fontSize: 13,
    color: "#6B7280",
    lineHeight: 18,
  },
  serviceMeta: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
  },
  facilityText: {
    fontSize: 12,
    color: "#9CA3AF",
    flex: 1,
  },
  ratingText: {
    fontSize: 12,
    color: "#D97706",
    fontWeight: "600",
  },
  requestCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 14,
    padding: 14,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  requestTop: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    gap: 10,
  },
  requestInfo: {
    flex: 1,
    gap: 4,
  },
  requestServiceName: {
    fontSize: 14,
    fontWeight: "700",
    color: "#111827",
    marginBottom: 2,
  },
  requestFacility: {
    fontSize: 13,
    color: "#6B7280",
  },
  requestMetaRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  requestDate: {
    fontSize: 12,
    color: "#9CA3AF",
  },
  scheduledText: {
    fontSize: 12,
    color: "#374151",
  },
  trackingText: {
    fontSize: 12,
    color: "#6B7280",
  },
  requestActions: {
    alignItems: "flex-end",
    gap: 8,
  },
  cancelRequestBtn: {
    backgroundColor: "#F3F4F6",
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: 10,
  },
  cancelRequestBtnText: {
    fontSize: 12,
    fontWeight: "500",
    color: "#6B7280",
  },
});
