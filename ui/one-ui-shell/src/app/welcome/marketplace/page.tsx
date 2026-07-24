import { PublicShell } from "@/components/public/PublicShell";
import { PublicMarketplaceBrowse } from "@/components/public/PublicMarketplaceBrowse";
import { ReasonedSignInPrompt } from "@/components/public/ReasonedSignInPrompt";

export const metadata = {
  title: "Health products & suppliers — Impilo",
  description:
    "Browse medicines, health products, services and approved suppliers with indicative prices and verification badges. Read freely — sign in only to order, pay or submit a prescription.",
};

/**
 * Public marketplace browse (PF-W3 / gateway pillar: Products & suppliers). Browse/search
 * listings and approved vendors WITHOUT sign-in. Ordering, paying and prescriptions require
 * sign-in; the basket is held client-side and claimed at sign-in (PD-7).
 */
export default function MarketplacePage() {
  return (
    <PublicShell>
      <h1 className="text-3xl font-bold text-slate-900">Health products &amp; suppliers</h1>
      <p className="mt-3 max-w-2xl text-slate-600">
        Find medicines, health products, services and approved suppliers — with indicative
        prices and verification badges. Browse freely; you only need to sign in to order, pay or
        submit a prescription.
      </p>
      <div className="mt-8">
        <PublicMarketplaceBrowse />
      </div>
      <ReasonedSignInPrompt
        pillar="products-suppliers"
        goal="place-order"
        destination="/my/orders"
        publicReturn="/welcome/marketplace"
        reason="Sign in only when you are ready to place an order, pay, submit a prescription or track delivery. Those actions create a secure record linked to you."
      />
    </PublicShell>
  );
}
