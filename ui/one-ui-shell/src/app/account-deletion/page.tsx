"use client";

/**
 * Account Deletion Notice — Standalone public legal page.
 * Route: /account-deletion
 *
 * Required by Google Play and Apple App Store guidelines for apps
 * that offer account creation. Accessible without authentication.
 */

import Link from "next/link";
import { ArrowLeft, Trash2 } from "lucide-react";

export default function AccountDeletionPage() {
  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="bg-card border-b border-border sticky top-0 z-10">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link
              href="/auth/login"
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
            <Trash2 className="w-3.5 h-3.5" />
            <span>Account Deletion</span>
          </div>
        </div>
      </header>

      {/* Content */}
      <main className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-12">
        <div className="bg-card rounded-xl shadow-sm border border-border p-6 sm:p-10">
          <div className="mb-8">
            <h1 className="text-2xl sm:text-3xl font-bold text-foreground mb-2">
              Account Deletion Notice
            </h1>
            <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm text-muted-foreground">
              <span>Effective Date: 11 April 2026</span>
              <span>Last Updated: 11 April 2026</span>
            </div>
            <p className="text-sm text-muted-foreground mt-1">
              Entity: Impilo Technologies Private Limited
            </p>
          </div>

          <div className="prose prose-gray max-w-none prose-headings:text-foreground prose-p:text-muted-foreground prose-li:text-muted-foreground prose-a:text-primary">
            {/* How to request deletion */}
            <section className="mb-8">
              <h3 className="text-lg font-semibold text-foreground mb-3">
                How to Delete Your Account
              </h3>
              <p>
                Impilo Technologies Private Limited allows users to request and initiate
                account deletion directly within Impilo vNext. Where available, users may
                also initiate deletion through other in-app account settings, through the
                deletion request mechanism made available on{" "}
                <a href="https://www.impilo.io" target="_blank" rel="noopener noreferrer">
                  www.impilo.io
                </a>
                , or by contacting{" "}
                <a href="mailto:support@impilo.io">support@impilo.io</a>.
              </p>

              {/* Step-by-step in-app */}
              <div className="mt-4 p-4 bg-info-soft border border-info/25 rounded-lg">
                <h4 className="text-sm font-semibold text-blue-900 mb-2">
                  In-App Deletion (Recommended)
                </h4>
                <ol className="text-sm text-blue-800 space-y-1 list-decimal list-inside">
                  <li>Sign in to Impilo vNext</li>
                  <li>
                    Go to <strong>Settings</strong> &rarr;{" "}
                    <strong>Privacy &amp; Data</strong>
                  </li>
                  <li>
                    Under <strong>Delete Account</strong>, select{" "}
                    <strong>Request Account Deletion</strong>
                  </li>
                  <li>Confirm by typing DELETE and submit your request</li>
                </ol>
              </div>
            </section>

            {/* What happens */}
            <section className="mb-8">
              <h3 className="text-lg font-semibold text-foreground mb-3">
                What Happens After a Deletion Request
              </h3>
              <p>
                Upon receipt and verification of a valid deletion request, Impilo
                Technologies Private Limited will take reasonable steps to delete or
                de-identify personal data associated with the account that it controls,
                within a reasonable period and subject to applicable law, regulatory
                obligations, technical constraints, and legitimate operational requirements.
              </p>
            </section>

            {/* Data retention */}
            <section className="mb-8">
              <h3 className="text-lg font-semibold text-foreground mb-3">
                Data That May Be Retained
              </h3>
              <p>
                Deletion of an account does not necessarily result in the immediate deletion
                of all related information. Certain data may be retained where necessary or
                permitted for lawful reasons, including:
              </p>
              <ul>
                <li>Security and fraud prevention</li>
                <li>Dispute resolution</li>
                <li>Audit requirements</li>
                <li>Compliance with legal or regulatory obligations</li>
                <li>Enforcement of contractual rights</li>
                <li>Public health obligations</li>
                <li>Continuity-of-care requirements</li>
                <li>
                  Recordkeeping obligations imposed on authorized healthcare providers,
                  healthcare organizations, employers, payers, or other institutions using
                  the Platform
                </li>
              </ul>
            </section>

            {/* Provider-held records */}
            <section className="mb-8">
              <h3 className="text-lg font-semibold text-foreground mb-3">
                Provider-Held Records
              </h3>
              <p>
                Where health, clinical, or service records are held by authorized providers,
                healthcare organizations, or other legally responsible entities, those records
                may continue to be retained and governed by those entities in accordance with
                their own legal, clinical, professional, and public health obligations. In
                such cases, Impilo Technologies Private Limited is not responsible for deleting
                records that are not under its direct control.
              </p>
            </section>

            {/* Technical process */}
            <section className="mb-8">
              <h3 className="text-lg font-semibold text-foreground mb-3">
                Deletion Process
              </h3>
              <p>
                Where immediate deletion is not technically possible, relevant data may first
                be restricted from active use and thereafter securely deleted, anonymized, or
                de-identified in accordance with applicable retention schedules, system
                constraints, and backup-cycle requirements.
              </p>
            </section>

            {/* Alternative methods */}
            <section className="mb-8">
              <h3 className="text-lg font-semibold text-foreground mb-3">
                Alternative Deletion Methods
              </h3>
              <p>
                Users who are unable to access the in-app deletion function in Impilo vNext or
                other available account settings may submit an account deletion request through{" "}
                <a href="https://www.impilo.io" target="_blank" rel="noopener noreferrer">
                  www.impilo.io
                </a>{" "}
                or by emailing{" "}
                <a href="mailto:support@impilo.io">support@impilo.io</a>. Impilo Technologies
                Private Limited may request reasonable information necessary to verify identity
                and prevent unauthorized deletion requests.
              </p>
            </section>

            {/* Contact */}
            <section className="mb-4">
              <h3 className="text-lg font-semibold text-foreground mb-3">
                Contact Information
              </h3>
              <address className="not-italic text-muted-foreground">
                <strong>Impilo Technologies Private Limited</strong>
                <br />
                Suite 45, 18th Floor, Kaguvi Building
                <br />
                Cnr Central Avenue and 4th Street
                <br />
                Harare, Zimbabwe
                <br />
                <br />
                Email:{" "}
                <a href="mailto:support@impilo.io">support@impilo.io</a>
                <br />
                Phone: +263 242 798537-70 / +263 4 290 1210
                <br />
                Website:{" "}
                <a href="https://www.impilo.io" target="_blank" rel="noopener noreferrer">
                  www.impilo.io
                </a>
              </address>
            </section>

            <div className="mt-6 p-4 bg-background border border-border rounded-lg">
              <p className="text-xs text-muted-foreground">
                This standalone notice matches the account-deletion expectations reflected
                in Google Play and Apple guidance for apps with account creation. For the
                full privacy policy, see the{" "}
                <Link href="/privacy" className="text-primary hover:text-blue-800 underline">
                  Impilo Privacy Policy
                </Link>
                .
              </p>
            </div>
          </div>
        </div>

        {/* Footer */}
        <footer className="mt-8 text-center text-xs text-muted-foreground space-y-2">
          <div className="flex items-center justify-center gap-4">
            <Link href="/terms" className="hover:text-muted-foreground transition-colors">
              Terms of Use
            </Link>
            <span>&middot;</span>
            <Link href="/privacy" className="hover:text-muted-foreground transition-colors">
              Privacy Policy
            </Link>
            <span>&middot;</span>
            <span className="text-muted-foreground">Account Deletion</span>
            <span>&middot;</span>
            <Link href="/privacy/app-stores" className="hover:text-muted-foreground transition-colors">
              App Store Privacy
            </Link>
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
