/**
 * MarketplaceScreen — Service discovery, booking, and order tracking.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
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
// re-import the frontend types (not the service fn return types)
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
        {/* Tab toggle */}
        <View style={styles.tabRow}>
          <Button
            title="Browse Services"
            variant={tab === "browse" ? "primary" : "ghost"}
            onPress={() => setTab("browse")}
            testID="marketplace-tab-browse"
          />
          <Button
            title="My Requests"
            variant={tab === "requests" ? "primary" : "ghost"}
            onPress={() => setTab("requests")}
            testID="marketplace-tab-requests"
          />
          <Button
            title="Cart"
            variant={tab === "cart" ? "primary" : "ghost"}
            onPress={() => setTab("cart")}
            testID="marketplace-tab-cart"
          />
        </View>

        {error ? (
          <ErrorState title="Error" message={error.message} onRetry={() => tab === "browse" ? loadServices() : loadRequests()} />
        ) : null}

        {/* Service booking modal */}
        {selectedService ? (
          <Card>
            <CardHeader title={`Book: ${selectedService.name}`} />
            <CardBody>
              <Text style={styles.serviceDescription}>{selectedService.description}</Text>
              {selectedService.price !== undefined ? (
                <Text style={styles.servicePrice}>
                  {`Price: ${selectedService.currency} ${selectedService.price}`}
                </Text>
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
                <View style={styles.buttonRow}>
                  <Button
                    title={submitting ? "Submitting..." : "Confirm Booking"}
                    variant="primary"
                    onPress={handleRequest}
                    disabled={submitting}
                    testID="confirm-booking"
                  />
                  <Button
                    title="Cancel"
                    variant="ghost"
                    onPress={() => setSelectedService(null)}
                  />
                </View>
              </View>
            </CardBody>
          </Card>
        ) : null}

        {/* Browse tab */}
        {tab === "browse" ? (
          <>
            <TextField
              value={search}
              onChange={setSearch}
              placeholder="Search services..."
              testID="marketplace-search"
            />
            <ScrollView horizontal style={styles.categoryScroll} contentContainerStyle={styles.categoryContent}>
              {CATEGORIES.map((cat) => (
                <Button
                  key={cat.value}
                  title={cat.label}
                  variant={category === cat.value ? "primary" : "ghost"}
                  size="sm"
                  onPress={() => setCategory(cat.value)}
                  testID={`category-${cat.value || "all"}`}
                />
              ))}
            </ScrollView>
            {isLoading ? (
              <LoadingSpinner size="md" />
            ) : services.length === 0 ? (
              <EmptyState
                title="No services found"
                message="Try adjusting your search or category filter"
              />
            ) : (
              services.map((svc) => (
                <Card key={svc.id}>
                  <CardBody>
                    <View testID={`service-${svc.id}`} style={styles.serviceRow}>
                      <View style={styles.serviceInfo}>
                        <Text style={styles.serviceName}>{svc.name}</Text>
                        <Text style={styles.serviceDescText}>{svc.description}</Text>
                        <Text style={styles.facilityText}>
                          {`${svc.facilityName} \u2022 ${svc.category}`}
                        </Text>
                        {svc.price !== undefined ? (
                          <Text style={styles.priceText}>
                            {`${svc.currency} ${svc.price}`}
                          </Text>
                        ) : null}
                        {svc.rating !== undefined ? (
                          <Text style={styles.ratingText}>
                            {`\u2605 ${svc.rating.toFixed(1)}`}
                          </Text>
                        ) : null}
                      </View>
                      {svc.available ? (
                        <Button
                          title="Book"
                          variant="primary"
                          size="sm"
                          onPress={() => setSelectedService(svc)}
                          testID={`book-${svc.id}`}
                        />
                      ) : (
                        <Badge variant="outline">Unavailable</Badge>
                      )}
                    </View>
                  </CardBody>
                </Card>
              ))
            )}
          </>
        ) : null}

        {/* Requests tab */}
        {tab === "requests" ? (
          <>
            {isLoading ? (
              <LoadingSpinner size="md" />
            ) : requests.length === 0 ? (
              <EmptyState
                title="No requests yet"
                message="Browse services and make your first booking"
              />
            ) : (
              requests.map((req) => (
                <Card key={req.id}>
                  <CardBody>
                    <View testID={`request-${req.id}`} style={styles.requestRow}>
                      <View>
                        <View style={styles.badgeRow}>
                          <Text style={styles.boldText}>{req.serviceName}</Text>
                          <Badge variant={REQUEST_STATUS_COLORS[req.status] ?? "outline"}>
                            {req.status}
                          </Badge>
                        </View>
                        <Text style={styles.secondaryText}>{req.facilityName}</Text>
                        <Text style={styles.tertiaryText}>
                          {`Requested: ${new Date(req.requestedAt).toLocaleDateString()}`}
                        </Text>
                        {req.scheduledAt ? (
                          <Text style={styles.scheduledText}>
                            {`Scheduled: ${new Date(req.scheduledAt).toLocaleString()}`}
                          </Text>
                        ) : null}
                        {req.trackingNumber ? (
                          <Text style={styles.trackingText}>
                            {`Tracking: ${req.trackingNumber}`}
                          </Text>
                        ) : null}
                      </View>
                      {req.status === "PENDING" || req.status === "CONFIRMED" ? (
                        <Button
                          title="Cancel"
                          variant="ghost"
                          size="sm"
                          onPress={() => handleCancelRequest(req.id)}
                          testID={`cancel-request-${req.id}`}
                        />
                      ) : null}
                    </View>
                  </CardBody>
                </Card>
              ))
            )}
          </>
        ) : null}

        {/* Cart tab */}
        {tab === "cart" ? <CartScreen /> : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  container: {
    padding: 16,
    gap: 16,
  },
  tabRow: {
    flexDirection: "row",
    gap: 8,
  },
  serviceDescription: {
    fontSize: 14,
    color: "#374151",
    marginBottom: 12,
  },
  servicePrice: {
    fontSize: 14,
    fontWeight: "600",
    marginBottom: 12,
  },
  formContainer: {
    gap: 12,
  },
  buttonRow: {
    flexDirection: "row",
    gap: 8,
  },
  categoryScroll: {
    flexGrow: 0,
  },
  categoryContent: {
    gap: 8,
    paddingBottom: 4,
  },
  serviceRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  serviceInfo: {
    flex: 1,
  },
  serviceName: {
    fontWeight: "700",
    fontSize: 15,
  },
  serviceDescText: {
    fontSize: 14,
    color: "#374151",
    marginVertical: 4,
  },
  facilityText: {
    fontSize: 13,
    color: "#6B7280",
    marginVertical: 2,
  },
  priceText: {
    fontSize: 14,
    fontWeight: "600",
    marginVertical: 4,
  },
  ratingText: {
    fontSize: 13,
    color: "#D97706",
  },
  requestRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    marginBottom: 4,
  },
  boldText: {
    fontWeight: "700",
  },
  secondaryText: {
    fontSize: 13,
    color: "#6B7280",
    marginVertical: 2,
  },
  tertiaryText: {
    fontSize: 13,
    color: "#9CA3AF",
    marginVertical: 2,
  },
  scheduledText: {
    fontSize: 13,
    color: "#374151",
    marginVertical: 2,
  },
  trackingText: {
    fontSize: 12,
    color: "#6B7280",
    marginVertical: 2,
  },
});
