"use client";

/**
 * App Store Privacy Summaries — Google Play & Apple App Store.
 * Route: /privacy/app-stores
 *
 * Contains the privacy disclosure summaries required by Google Play
 * Data Safety and Apple App Privacy. These must match the actual
 * SDKs, permissions, analytics setup, and data collection in the app.
 *
 * Accessible without authentication.
 */

import Link from "next/link";
import { ArrowLeft, Shield, Smartphone } from "lucide-react";

export default function AppStorePrivacyPage() {
  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="bg-card border-b border-border sticky top-0 z-10">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link
              href="/privacy"
              className="text-muted-foreground hover:text-foreground transition-colors"
            >
              <ArrowLeft className="w-5 h-5" />
            </Link>
            <div>
              <h1 className="text-lg font-semibold text-foreground">Impilo</h1>
              <p className="text-xs text-muted-foreground">Health Operating System</p>
            </div>
          </div>
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <Smartphone className="w-3.5 h-3.5" />
            <span>App Store Privacy</span>
          </div>
        </div>
      </header>

      {/* Content */}
      <main className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-12">
        {/* Compliance notice */}
        <div className="mb-6 p-4 bg-warning-soft border border-warning/35 rounded-lg">
          <p className="text-xs text-warning-foreground">
            <strong>Implementation note:</strong> The final store declarations must exactly
            match your real SDKs, permissions, analytics setup, and whether any data is
            actually &ldquo;linked to the user&rdquo; or used for &ldquo;tracking.&rdquo;
            Apple requires you to declare data collected by your app and integrated
            third-party partners. Google requires the Data Safety form to accurately
            reflect actual app behavior.
          </p>
        </div>

        {/* ── Google Play ──────────────────────────────────── */}
        <div className="bg-card rounded-xl shadow-sm border border-border p-6 sm:p-10 mb-6">
          <div className="flex items-center gap-3 mb-6">
            <div className="w-10 h-10 rounded-lg bg-green-100 flex items-center justify-center">
              <svg className="w-5 h-5 text-green-700" viewBox="0 0 24 24" fill="currentColor">
                <path d="M3.609 1.814L13.792 12 3.61 22.186a.996.996 0 01-.61-.92V2.734a1 1 0 01.609-.92zm10.89 10.893l2.302 2.302-10.937 6.333 8.635-8.635zm3.199-3.199l2.807 1.626a1 1 0 010 1.732l-2.807 1.626L15.206 12l2.492-2.492zM5.864 2.658L16.8 8.99l-2.302 2.302-8.635-8.635z" />
              </svg>
            </div>
            <div>
              <h2 className="text-xl font-bold text-foreground">Google Play</h2>
              <p className="text-sm text-muted-foreground">Data Safety / Privacy Summary</p>
            </div>
          </div>

          <div className="prose prose-gray max-w-none prose-p:text-muted-foreground">
            <h3 className="text-base font-semibold text-foreground mb-3">Privacy Summary</h3>
            <p>
              Impilo Technologies Private Limited respects applicable privacy, data residency,
              data sovereignty, and health information governance requirements. Depending on the
              deployment and features used, Impilo may process account information, contact
              details, device and technical information, usage information, location data where
              enabled, communications, and health- or service-related information.
            </p>
            <p>
              Impilo is designed using privacy-by-design principles, including separation of
              personally identifiable information from health or clinical information, controlled
              access, audit capability, and minimum-necessary access safeguards. Impilo does not
              sell personal data or health-related data.
            </p>
            <p>
              Where health or clinical information is stored, managed, or retained, this is
              ordinarily done by authorized healthcare providers, healthcare organizations, or
              other legally mandated entities for lawful purposes such as care delivery,
              continuity of care, quality assurance, health administration, legal compliance,
              and public health. Those providers and organizations remain independently
              responsible for their own compliance with applicable privacy, health information,
              and public health laws in relation to the data they control or process.
            </p>
            <p>
              Impilo may use and disclose information for purposes including account
              administration, authentication, service delivery, care coordination, platform
              operations, security, fraud prevention, support, legal compliance, quality
              improvement, and other lawful and legitimate purposes described in the Privacy
              Policy. Information may be shared with authorized providers, institutions, service
              providers acting on Impilo&rsquo;s behalf, regulators or public authorities where
              required by law, and other parties where lawfully authorized or instructed.
            </p>
            <p>
              Users may request and initiate account deletion directly within Impilo vNext,
              through other available account settings, through{" "}
              <a href="https://www.impilo.io" target="_blank" rel="noopener noreferrer">
                www.impilo.io
              </a>
              , or by emailing{" "}
              <a href="mailto:support@impilo.io">support@impilo.io</a>. Some information may
              be retained where required by law or for lawful purposes such as security, audit,
              fraud prevention, public health, continuity of care, and recordkeeping obligations.
            </p>

            <div className="mt-6 p-4 bg-background border border-border rounded-lg">
              <h4 className="text-sm font-semibold text-foreground mb-2">Short Version (for Data Safety form)</h4>
              <p className="text-sm text-muted-foreground">
                Impilo processes only the data needed to provide, secure, support, and improve
                the platform and related services. Depending on the deployment and features
                used, this may include account details, contact information, technical and
                usage data, location data where enabled, communications, and health- or
                service-related information. Impilo applies privacy-by-design principles,
                including separation of personally identifiable information from health or
                clinical information, controlled access, and audit capability. Users can
                initiate account deletion in Impilo vNext, through{" "}
                <a href="https://www.impilo.io" target="_blank" rel="noopener noreferrer">
                  www.impilo.io
                </a>
                , or by emailing{" "}
                <a href="mailto:support@impilo.io">support@impilo.io</a>.
              </p>
            </div>
          </div>
        </div>

        {/* ── Apple ────────────────────────────────────────── */}
        <div className="bg-card rounded-xl shadow-sm border border-border p-6 sm:p-10 mb-6">
          <div className="flex items-center gap-3 mb-6">
            <div className="w-10 h-10 rounded-lg bg-neutral-100 flex items-center justify-center">
              <svg className="w-5 h-5 text-foreground" viewBox="0 0 24 24" fill="currentColor">
                <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.8-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z" />
              </svg>
            </div>
            <div>
              <h2 className="text-xl font-bold text-foreground">Apple App Store</h2>
              <p className="text-sm text-muted-foreground">App Privacy Summary</p>
            </div>
          </div>

          <div className="prose prose-gray max-w-none prose-p:text-muted-foreground">
            <h3 className="text-base font-semibold text-foreground mb-3">App Privacy Summary</h3>
            <p>
              Impilo Technologies Private Limited may collect and process data such as account
              information, contact details, device identifiers, diagnostics, usage data,
              location data where enabled, communications, and health- or service-related
              information, depending on the deployment and features used.
            </p>
            <p>
              This information is used for app functionality, account management,
              authentication, care coordination, communications, security, fraud prevention,
              support, compliance, and service improvement. Impilo does not sell personal data
              or health-related data and is not designed for cross-app or third-party
              advertising tracking.
            </p>
            <p>
              Impilo applies privacy-by-design principles, including separation of personally
              identifiable information from health or clinical information, controlled access,
              audit capability, and minimum-necessary access safeguards. Where health or
              clinical records are stored or retained, this is ordinarily done by authorized
              healthcare providers, healthcare organizations, or other legally mandated entities
              for lawful purposes including continuity of care, legal compliance, and public
              health.
            </p>
            <p>
              Users can review the Privacy Policy, contact support, and initiate account
              deletion directly within Impilo vNext, through other available account settings,
              through{" "}
              <a href="https://www.impilo.io" target="_blank" rel="noopener noreferrer">
                www.impilo.io
              </a>
              , or by emailing{" "}
              <a href="mailto:support@impilo.io">support@impilo.io</a>. Some information may
              be retained where required by law or for lawful purposes such as security, audit,
              fraud prevention, continuity of care, public health, and recordkeeping obligations.
            </p>

            <div className="mt-6 p-4 bg-background border border-border rounded-lg">
              <h4 className="text-sm font-semibold text-foreground mb-2">Short Version (for App Privacy labels)</h4>
              <p className="text-sm text-muted-foreground">
                Impilo may collect account, contact, device, diagnostics, usage, location,
                communication, and health- or service-related data depending on the features
                used. This data is used for app functionality, account management, care
                coordination, communications, security, support, compliance, and service
                improvement. Users can review the Privacy Policy and initiate account deletion
                in Impilo vNext or through{" "}
                <a href="https://www.impilo.io" target="_blank" rel="noopener noreferrer">
                  www.impilo.io
                </a>
                .
              </p>
            </div>
          </div>
        </div>

        {/* ── Data Collection Quick-Reference ──────────────── */}
        <div className="bg-card rounded-xl shadow-sm border border-border p-6 sm:p-10 mb-6">
          <h2 className="text-lg font-bold text-foreground mb-4 flex items-center gap-2">
            <Shield className="w-5 h-5 text-primary" />
            Data Collection Quick-Reference
          </h2>
          <p className="text-sm text-muted-foreground mb-4">
            Categories of data that Impilo may process, depending on deployment and features.
            Use this to inform store Data Safety / App Privacy declarations.
          </p>

          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border">
                  <th className="text-left py-2 pr-4 font-medium text-foreground">Category</th>
                  <th className="text-left py-2 pr-4 font-medium text-foreground">Examples</th>
                  <th className="text-left py-2 font-medium text-foreground">Purpose</th>
                </tr>
              </thead>
              <tbody className="text-muted-foreground">
                <DataRow
                  category="Account &amp; Identity"
                  examples="Name, email, phone, role, staff ID"
                  purpose="Account management, authentication"
                />
                <DataRow
                  category="Contact &amp; Support"
                  examples="Support requests, feedback, emails"
                  purpose="Support, service delivery"
                />
                <DataRow
                  category="Device &amp; Technical"
                  examples="IP, device type, OS, browser, crash logs"
                  purpose="Security, diagnostics, compatibility"
                />
                <DataRow
                  category="Usage"
                  examples="Pages viewed, session data, timestamps"
                  purpose="Service improvement, analytics"
                />
                <DataRow
                  category="Location"
                  examples="Approximate or precise (when enabled)"
                  purpose="Functionality, safety, routing"
                />
                <DataRow
                  category="Communications"
                  examples="Messages, forms, attachments, images"
                  purpose="Care coordination, service delivery"
                />
                <DataRow
                  category="Health &amp; Service"
                  examples="Clinical data (via authorized providers)"
                  purpose="Care delivery, continuity of care"
                />
                <DataRow
                  category="Cookies &amp; Tokens"
                  examples="Auth tokens, session cookies"
                  purpose="Authentication, session management"
                />
              </tbody>
            </table>
          </div>

          <p className="mt-4 text-xs text-muted-foreground">
            Impilo separates personally identifiable information from health or clinical
            information by design. Data is not sold or used for advertising tracking.
          </p>
        </div>

        {/* Footer */}
        <footer className="mt-8 text-center text-xs text-muted-foreground space-y-2">
          <div className="flex items-center justify-center gap-4 flex-wrap">
            <Link href="/terms" className="hover:text-muted-foreground transition-colors">
              Terms of Use
            </Link>
            <span>&middot;</span>
            <Link href="/privacy" className="hover:text-muted-foreground transition-colors">
              Privacy Policy
            </Link>
            <span>&middot;</span>
            <Link href="/account-deletion" className="hover:text-muted-foreground transition-colors">
              Account Deletion
            </Link>
            <span>&middot;</span>
            <span className="text-muted-foreground">App Store Privacy</span>
            <span>&middot;</span>
            <Link href="/auth/login" className="hover:text-muted-foreground transition-colors">
              Sign In
            </Link>
          </div>
          <p>Impilo Technologies Private Limited</p>
        </footer>
      </main>
    </div>
  );
}

function DataRow({
  category,
  examples,
  purpose,
}: {
  category: string;
  examples: string;
  purpose: string;
}) {
  return (
    <tr className="border-b border-border">
      <td className="py-2 pr-4 font-medium text-foreground" dangerouslySetInnerHTML={{ __html: category }} />
      <td className="py-2 pr-4">{examples}</td>
      <td className="py-2">{purpose}</td>
    </tr>
  );
}
