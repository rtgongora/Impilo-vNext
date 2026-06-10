"use client";

/**
 * Context Chooser — How are you working today?
 * Route: /auth/context-chooser
 *
 * Shown when a provider has multiple authorised work contexts.
 */

import { useRouter, useSearchParams } from "next/navigation";
import {
  ArrowRight,
  Building2,
  GraduationCap,
  Heart,
  LayoutDashboard,
  Radio,
  Stethoscope,
  Users,
} from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { NompiloHint } from "@/components/intelligent/NompiloHint";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useIdentityContext } from "@/hooks/useIdentityContext";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import type { ContextChooserOptionId } from "@/lib/identity-context";

const OPTION_ICONS: Record<ContextChooserOptionId, typeof Building2> = {
  facility_work: Building2,
  telemedicine_pool: Radio,
  professional_profile: Stethoscope,
  programme_workspace: Users,
  above_site_dashboard: LayoutDashboard,
  fundo_learning: GraduationCap,
  personal_health: Heart,
};

export default function ContextChooserPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo");
  const user = useAuthStore((s) => s.user);
  const { contextChooserOptions, isLoading } = useIdentityContext();

  function handleSelect(href: string, operationalMode?: string) {
    if (operationalMode) {
      useOperationalContextStore.getState().setOperationalMode(
        operationalMode as ReturnType<typeof useOperationalContextStore.getState>["operationalMode"],
      );
    }
    if (returnTo && href === "/facility") {
      router.push(`${href}?returnTo=${encodeURIComponent(returnTo)}`);
      return;
    }
    router.push(href);
  }

  return (
    <AuthLayout>
      <div className="max-w-lg mx-auto">
        <h2 className="text-xl font-semibold text-gray-900 mb-1">
          Welcome{user?.displayName ? `, ${user.displayName.split(" ")[0]}` : ""}
        </h2>
        <p className="text-sm text-gray-500 mb-6">How are you working today?</p>

        {isLoading ? (
          <p className="text-sm text-gray-400">Loading your authorised contexts...</p>
        ) : (
          <div className="space-y-3">
            {contextChooserOptions.map((option) => {
              const Icon = OPTION_ICONS[option.id];
              return (
                <button
                  key={option.id}
                  type="button"
                  onClick={() => handleSelect(option.href, option.operationalMode)}
                  className="w-full flex items-center gap-4 rounded-xl border border-gray-200 bg-white p-4 text-left hover:border-impilo-400 hover:ring-1 hover:ring-impilo-200 transition-all"
                >
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-impilo-50 text-impilo-600">
                    <Icon className="h-5 w-5" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-gray-900">{option.label}</p>
                    <p className="text-xs text-gray-500 mt-0.5">{option.description}</p>
                  </div>
                  <ArrowRight className="h-4 w-4 text-gray-400 shrink-0" />
                </button>
              );
            })}
          </div>
        )}
      </div>

      <NompiloHint message="Choose the context that matches how you intend to work. You can switch later from your profile menu." />
    </AuthLayout>
  );
}
