/**
 * CartScreen — Marketplace shopping cart, checkout flow.
 */
import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Card, CardBody, Button, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import { fetchCart, removeFromCart, checkout } from "../../services/cartService";
import type { Cart, CartItem } from "../../types";

export function CartScreen() {
  const [cart, setCart] = useState<Cart | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);
  const [checkingOut, setCheckingOut] = useState(false);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchCart();
      setCart(result);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleRemove = useCallback(
    async (itemId: string) => {
      setRemovingId(itemId);
      try {
        await removeFromCart(itemId);
        await load();
      } catch (err) {
        setError(err instanceof Error ? err : new Error(String(err)));
      } finally {
        setRemovingId(null);
      }
    },
    [load],
  );

  const handleCheckout = useCallback(async () => {
    if (!cart) return;
    setCheckingOut(true);
    try {
      await checkout(cart.id);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setCheckingOut(false);
    }
  }, [cart, load]);

  if (isLoading) return <LoadingSpinner size="md" />;
  if (error) return <ErrorState title="Error" message={error.message} onRetry={load} />;

  const items = cart?.items ?? [];
  const isEmpty = items.length === 0;

  return (
    <ScrollView
      testID="cart-screen"
      style={styles.scrollView}
      contentContainerStyle={styles.container}
    >
      <Text style={styles.screenTitle}>Shopping Cart</Text>

      {isEmpty ? (
        <EmptyState
          title="Cart is empty"
          message="Browse the marketplace to add items to your cart"
        />
      ) : (
        <>
          {/* Cart items */}
          {items.map((item) => (
            <Card key={item.id}>
              <CardBody>
                <View testID={`cart-item-${item.id}`} style={styles.itemRow}>
                  <View style={styles.itemInfo}>
                    <Text style={styles.itemName}>{item.productName}</Text>
                    <Text style={styles.itemDetails}>
                      Qty: {item.quantity} x {item.currency}{" "}
                      {item.unitPrice.toFixed(2)}
                    </Text>
                    <Text style={styles.itemSubtotal}>
                      Subtotal: {item.currency}{" "}
                      {(item.quantity * item.unitPrice).toFixed(2)}
                    </Text>
                  </View>
                  <Button
                    title={removingId === item.id ? "..." : "Remove"}
                    variant="ghost"
                    size="sm"
                    onPress={() => handleRemove(item.id)}
                    disabled={removingId === item.id}
                    testID={`remove-${item.id}`}
                  />
                </View>
              </CardBody>
            </Card>
          ))}

          {/* Cart summary */}
          <Card>
            <CardBody>
              <View testID="cart-summary" style={styles.summaryContainer}>
                <View style={styles.summaryRow}>
                  <Text style={styles.summaryLabel}>Total Items</Text>
                  <Text style={styles.summaryValue}>{cart?.totalItems ?? 0}</Text>
                </View>
                <View style={styles.divider} />
                <View style={styles.summaryRow}>
                  <Text style={styles.totalLabel}>Total Amount</Text>
                  <Text style={styles.totalValue}>
                    {cart?.currency ?? "ZAR"}{" "}
                    {(cart?.totalAmount ?? 0).toFixed(2)}
                  </Text>
                </View>
              </View>
            </CardBody>
          </Card>

          {/* Checkout button */}
          <Button
            title={checkingOut ? "Processing..." : "Checkout"}
            variant="primary"
            onPress={handleCheckout}
            disabled={isEmpty || checkingOut}
            testID="checkout-button"
          />
        </>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  container: {
    gap: 12,
  },
  screenTitle: {
    fontSize: 18,
    fontWeight: "700",
    color: colors.gray[900],
  },
  itemRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  itemInfo: {
    flex: 1,
  },
  itemName: {
    fontSize: 15,
    fontWeight: "600",
    color: colors.gray[900],
  },
  itemDetails: {
    fontSize: 13,
    color: colors.gray[500],
    marginTop: 2,
  },
  itemSubtotal: {
    fontSize: 14,
    fontWeight: "600",
    color: colors.gray[700],
    marginTop: 4,
  },
  summaryContainer: {
    gap: 8,
  },
  summaryRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  summaryLabel: {
    fontSize: 14,
    color: colors.gray[500],
  },
  summaryValue: {
    fontSize: 14,
    fontWeight: "500",
    color: colors.gray[700],
  },
  divider: {
    height: 1,
    backgroundColor: colors.gray[200],
  },
  totalLabel: {
    fontSize: 16,
    fontWeight: "700",
    color: colors.gray[900],
  },
  totalValue: {
    fontSize: 16,
    fontWeight: "700",
    color: colors.gray[900],
  },
});
