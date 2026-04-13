"use client";

/**
 * Register — New user registration with role selection.
 * Route: /auth/register | pageTitle: "Create Account"
 *
 * Supports self-registration for: Citizen, Clinician, Nurse, Pharmacist.
 * Admin roles require admin-created accounts.
 * On success: auto-login and redirect to /home.
 */

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  UserPlus,
  Mail,
  Lock,
  User,
  Loader2,
  Eye,
  EyeOff,
  Heart,
  Stethoscope,
  Syringe,
  Pill,
} from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { useAuthStore } from "@/hooks/useAuthStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";

const ROLES = [
  { value: "CITIZEN", label: "Citizen / Patient", icon: Heart, description: "Access your health records" },
  { value: "CLINICIAN", label: "Clinician / Doctor", icon: Stethoscope, description: "Clinical provider account" },
  { value: "NURSE", label: "Nurse", icon: Syringe, description: "Nursing provider account" },
  { value: "PHARMACIST", label: "Pharmacist", icon: Pill, description: "Pharmacy provider account" },
];

export default function RegisterPage() {
  const router = useRouter();
  const { setAuth } = useAuthStore();

  const [step, setStep] = useState<"role" | "details">("role");
  const [selectedRole, setSelectedRole] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function handleRoleSelect(role: string) {
    setSelectedRole(role);
    setStep("details");
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (!firstName.trim() || !lastName.trim()) {
      setError("Please enter your full name.");
      return;
    }
    if (!email.trim()) {
      setError("Please enter your email address.");
      return;
    }
    if (password.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }
    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setSubmitting(true);
    try {
      const res = await apiClient.post<ApiResponse<{
        id: string;
        type: string;
        attributes: {
          token?: string;
          refreshToken?: string;
          expiresAt?: string;
          user?: { id: string; email: string; displayName: string; roles: string[]; actorType: string };
          status?: string;
          message?: string;
        };
      }>>("/internal/v1/auth/register", {
        email: email.trim(),
        password,
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        role: selectedRole,
      });

      const attrs = res.data.attributes;

      if (attrs.token && attrs.user) {
        // Auto-login succeeded
        setAuth(
          {
            id: attrs.user.id,
            email: attrs.user.email,
            displayName: attrs.user.displayName,
            roles: attrs.user.roles,
            actorType: attrs.user.actorType as "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM",
          },
          attrs.token,
          attrs.refreshToken,
          attrs.expiresAt
        );
        router.push("/home");
      } else {
        // Registration succeeded but auto-login failed
        router.push("/auth/login?registered=true");
      }
    } catch (err: unknown) {
      const apiErr = err as { error?: { message?: string; code?: string } };
      if (apiErr?.error?.code === "USER_EXISTS") {
        setError("An account with this email already exists. Please sign in instead.");
      } else {
        setError(apiErr?.error?.message ?? "Registration failed. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout>
      {step === "role" ? (
        <div>
          <h2 className="text-xl font-semibold text-gray-900 mb-1">Create Account</h2>
          <p className="text-sm text-gray-500 mb-6">Choose your account type</p>

          <div className="space-y-3">
            {ROLES.map((role) => {
              const Icon = role.icon;
              return (
                <button
                  key={role.value}
                  onClick={() => handleRoleSelect(role.value)}
                  className="w-full flex items-center gap-4 p-4 border border-gray-200 rounded-lg text-left hover:border-impilo-200 hover:bg-impilo-50 transition-colors"
                >
                  <div className="w-11 h-11 rounded-lg bg-impilo-50 flex items-center justify-center shrink-0">
                    <Icon className="w-5 h-5 text-impilo-500" />
                  </div>
                  <div>
                    <p className="text-sm font-medium text-gray-900">{role.label}</p>
                    <p className="text-xs text-gray-500">{role.description}</p>
                  </div>
                </button>
              );
            })}
          </div>

          <div className="mt-6 text-center">
            <p className="text-sm text-gray-500">
              Already have an account?{" "}
              <Link href="/auth/login" className="text-impilo-500 hover:text-impilo-700 font-medium">
                Sign in
              </Link>
            </p>
          </div>
        </div>
      ) : (
        <div>
          <div className="flex items-center gap-2 mb-1">
            <button onClick={() => setStep("role")} className="text-gray-400 hover:text-gray-600 transition-colors">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" /></svg>
            </button>
            <h2 className="text-xl font-semibold text-gray-900">Create Account</h2>
          </div>
          <p className="text-sm text-gray-500 mb-6">
            Registering as {ROLES.find((r) => r.value === selectedRole)?.label}
          </p>

          {error && (
            <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">First Name</label>
                <div className="relative">
                  <User className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                  <input type="text" required value={firstName} onChange={(e) => setFirstName(e.target.value)}
                    placeholder="First name" autoComplete="given-name"
                    className="w-full pl-10 pr-3 py-3 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400" />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Last Name</label>
                <input type="text" required value={lastName} onChange={(e) => setLastName(e.target.value)}
                  placeholder="Last name" autoComplete="family-name"
                  className="w-full px-3 py-3 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400" />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Email Address</label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com" autoComplete="email"
                  className="w-full pl-10 pr-3 py-3 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400" />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input type={showPassword ? "text" : "password"} required value={password} onChange={(e) => setPassword(e.target.value)}
                  placeholder="At least 8 characters" autoComplete="new-password" minLength={8}
                  className="w-full pl-10 pr-12 py-3 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400" />
                <button type="button" onClick={() => setShowPassword((v) => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Confirm Password</label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input type={showPassword ? "text" : "password"} required value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Repeat password" autoComplete="new-password"
                  className="w-full pl-10 pr-3 py-3 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400" />
              </div>
            </div>

            <button type="submit" disabled={submitting}
              className="w-full py-3 bg-impilo-500 text-white text-sm font-medium rounded-lg hover:bg-impilo-600 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors">
              {submitting ? <><Loader2 className="w-4 h-4 animate-spin" /> Creating account...</> : <><UserPlus className="w-4 h-4" /> Create Account</>}
            </button>
          </form>

          <div className="mt-4 text-center">
            <p className="text-sm text-gray-500">
              Already have an account?{" "}
              <Link href="/auth/login" className="text-impilo-500 hover:text-impilo-700 font-medium">
                Sign in
              </Link>
            </p>
          </div>
        </div>
      )}
    </AuthLayout>
  );
}
