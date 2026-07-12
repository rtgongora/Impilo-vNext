"use client";

/**
 * Shared add-to-cart action for the buyer lane (M7).
 *
 * Ensures the caller's OPEN cart exists (get-or-create via the BFF), then posts the
 * line. Accepts either a canonical msikaCoreCode (registry/catalog lane) or a
 * storefront listingId — msika-flow resolves the listing's price server-side.
 */

import { useState } from "react";
import Link from "next/link";
import { Check, Loader2, ShoppingCart } from "lucide-react";
import { useAddCartItem } from "@/hooks/queries/useCommerceFlow";

function errorMessage(error: unknown): string {
  const err = error as { error?: { message?: string }; message?: string } | null;
  return err?.error?.message ?? err?.message ?? "Could not add this item to your cart.";
}

export interface AddToCartButtonProps {
  msikaCoreCode?: string;
  listingId?: string;
  qty?: number;
  label?: string;
  /** Compact style for dense cards/rows. */
  compact?: boolean;
  className?: string;
}

export function AddToCartButton({ msikaCoreCode, listingId, qty = 1, label = "Add to cart", compact = false, className }: AddToCartButtonProps) {
  const addItem = useAddCartItem();
  const [added, setAdded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const disabled = addItem.isPending || (!msikaCoreCode && !listingId);

  const base = compact
    ? "inline-flex items-center gap-1 rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-medium text-foreground hover:bg-background disabled:opacity-50"
    : "inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground disabled:opacity-50";

  return (
    <span className="inline-flex flex-col items-start gap-1">
      <button
        type="button"
        disabled={disabled}
        className={className ?? base}
        onClick={() => {
          setError(null);
          setAdded(false);
          addItem.mutate(
            { msikaCoreCode, listingId, qty },
            {
              onSuccess: () => setAdded(true),
              onError: (err) => setError(errorMessage(err)),
            },
          );
        }}
      >
        {addItem.isPending ? (
          <Loader2 className={compact ? "h-3.5 w-3.5 animate-spin" : "h-4 w-4 animate-spin"} />
        ) : added ? (
          <Check className={compact ? "h-3.5 w-3.5" : "h-4 w-4"} />
        ) : (
          <ShoppingCart className={compact ? "h-3.5 w-3.5" : "h-4 w-4"} />
        )}
        {added ? "Added" : label}
      </button>
      {added ? (
        <Link href="/marketplace/cart" className="text-xs font-medium text-primary hover:underline">
          View cart
        </Link>
      ) : null}
      {error ? <span className="text-xs text-danger">{error}</span> : null}
    </span>
  );
}
