"use client";

/**
 * Terms of Use — Public legal page.
 * Route: /terms
 *
 * Accessible without authentication. Uses a minimal layout
 * with Impilo branding and navigation back to login/home.
 */

import Link from "next/link";
import { ArrowLeft, FileText } from "lucide-react";

export default function TermsOfUsePage() {
  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white border-b border-gray-200 sticky top-0 z-10">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link
              href="/auth/login"
              className="text-gray-500 hover:text-gray-700 transition-colors"
            >
              <ArrowLeft className="w-5 h-5" />
            </Link>
            <div>
              <h1 className="text-lg font-semibold text-gray-900">Impilo</h1>
              <p className="text-xs text-gray-500">Health Operating System</p>
            </div>
          </div>
          <div className="flex items-center gap-2 text-xs text-gray-400">
            <FileText className="w-3.5 h-3.5" />
            <span>Terms of Use</span>
          </div>
        </div>
      </header>

      {/* Content */}
      <main className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-12">
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 sm:p-10">
          <div className="mb-8">
            <h1 className="text-2xl sm:text-3xl font-bold text-gray-900 mb-2">
              Impilo Terms of Use
            </h1>
            <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm text-gray-500">
              <span>Effective Date: 11 April 2026</span>
              <span>Last Updated: 11 April 2026</span>
            </div>
            <p className="text-sm text-gray-500 mt-1">
              Entity: Impilo Technologies Private Limited (&ldquo;Impilo,&rdquo; &ldquo;we,&rdquo; &ldquo;us,&rdquo; or &ldquo;our&rdquo;)
            </p>
          </div>

          <div className="prose prose-gray max-w-none prose-headings:text-gray-900 prose-p:text-gray-600 prose-li:text-gray-600 prose-a:text-blue-600">
            <Section number="1" title="Acceptance of Terms">
              <p>
                These Terms of Use (&ldquo;Terms&rdquo;) govern your access to and use of the Impilo platform, websites,
                mobile applications, portals, interfaces, APIs, support services, and related services (collectively, the
                &ldquo;Services&rdquo;).
              </p>
              <p>
                By accessing or using the Services, you agree to be bound by these Terms. If you do not agree, do not use
                the Services.
              </p>
              <p>
                If you use the Services on behalf of an organization, you represent that you are authorized to bind that
                organization.
              </p>
            </Section>

            <Section number="2" title="Eligibility">
              <p>
                You may use the Services only if you are legally capable of entering into a binding agreement or are
                otherwise authorized by a parent, guardian, employer, institution, healthcare provider, or other lawful
                authority.
              </p>
            </Section>

            <Section number="3" title="Nature of the Services">
              <p>
                Impilo provides a digital platform and related technical tools to support healthcare delivery, care
                coordination, digital interactions, communications, workflows, referrals, records access, and related
                health system and organizational functions.
              </p>
              <p>
                Unless expressly stated otherwise in writing, Impilo does not itself provide medical diagnosis, treatment,
                emergency response, nursing care, pharmacy services, or professional clinical advice.
              </p>
              <p>
                The Services are not a substitute for emergency services. In an emergency, users should contact the
                appropriate emergency provider or local emergency number.
              </p>
            </Section>

            <Section number="4" title="Accounts and Access">
              <p>
                To use certain features, you may need to create an account or be assigned access by a provider or
                institution.
              </p>
              <p>You agree to:</p>
              <ul>
                <li>Provide accurate information</li>
                <li>Keep your credentials confidential</li>
                <li>Promptly update inaccurate information</li>
                <li>Use only accounts assigned to you or lawfully created for you</li>
                <li>Notify us promptly of unauthorized access or suspected security incidents</li>
              </ul>
              <p>
                You are responsible for activity conducted through your account unless caused by our breach of duty or
                security failure.
              </p>
            </Section>

            <Section number="5" title="Acceptable Use">
              <p>You agree not to:</p>
              <ul>
                <li>Use the Services unlawfully or fraudulently</li>
                <li>Impersonate another person or entity</li>
                <li>Access data or accounts without authorization</li>
                <li>Interfere with the operation or security of the Services</li>
                <li>Upload malicious code, harmful content, or unlawful material</li>
                <li>Scrape, reverse engineer, or exploit the Services except as permitted by law or written agreement</li>
                <li>Use the Services to harass, abuse, deceive, or harm others</li>
                <li>Violate any law, professional obligation, confidentiality duty, or rights of another person</li>
              </ul>
            </Section>

            <Section number="6" title="Provider and Institutional Responsibility">
              <p>
                Each healthcare professional, provider, organization, payer, employer, or other institution using the
                Services remains independently responsible for:
              </p>
              <ul>
                <li>Compliance with applicable privacy, data protection, health-information, and public-health laws</li>
                <li>Obtaining any required consents, authorizations, or lawful bases</li>
                <li>Providing required notices to users</li>
                <li>Configuring and managing access appropriately</li>
                <li>Maintaining clinical, professional, and recordkeeping standards</li>
                <li>Ensuring lawful and appropriate use of information in its own operations</li>
              </ul>
              <p>
                Impilo provides the Platform and related safeguards, but is not responsible for the independent acts,
                omissions, decisions, misconduct, or unlawful processing of third-party providers, organizations, or users
                beyond Impilo&rsquo;s own role and legal obligations.
              </p>
            </Section>

            <Section number="7" title="Consent to Electronic Communications">
              <p>
                By using the Services, you consent to receive electronic communications from us relating to the Services,
                your account, security matters, support, policies, and operational updates.
              </p>
            </Section>

            <Section number="8" title="Privacy">
              <p>
                Your use of the Services is also governed by the{" "}
                <Link href="/privacy" className="text-blue-600 hover:text-blue-800">
                  Impilo Privacy Policy
                </Link>
                , which forms part of these Terms.
              </p>
            </Section>

            <Section number="9" title="User Content">
              <p>
                You may submit, upload, transmit, or store content through the Services (&ldquo;User Content&rdquo;). You retain
                rights you may have in your User Content, subject to the rights necessary for us to operate the Services.
              </p>
              <p>
                You grant us a limited, non-exclusive, revocable, and lawful right to host, process, transmit, display,
                and otherwise use User Content solely as necessary to provide, secure, maintain, support, and improve the
                Services and to comply with law.
              </p>
              <p>
                You are responsible for ensuring that you have the rights and authority to provide any User Content you
                submit.
              </p>
            </Section>

            <Section number="10" title="Intellectual Property">
              <p>
                The Services, including software, interfaces, branding, text, graphics, design, and underlying technology,
                are owned by or licensed to Impilo and are protected by intellectual-property and other laws.
              </p>
              <p>
                Except as expressly permitted, these Terms do not grant you any right, title, or interest in the Services
                or our intellectual property.
              </p>
            </Section>

            <Section number="11" title="Third-Party Services and Integrations">
              <p>
                The Services may interoperate with or link to third-party applications, systems, devices, content, or
                services. Impilo does not control and is not responsible for third-party offerings except to the extent
                required by law or contract in relation to our own role.
              </p>
              <p>
                Your use of third-party services may also be subject to separate terms and privacy policies.
              </p>
            </Section>

            <Section number="12" title="Availability and Changes">
              <p>
                We may modify, update, suspend, or discontinue any part of the Services at any time, with or without
                notice, subject to applicable law and contractual commitments.
              </p>
              <p>
                We do not guarantee that the Services will always be available, uninterrupted, or error-free.
              </p>
            </Section>

            <Section number="13" title="Disclaimers">
              <p>
                The Services are provided on an &ldquo;as is&rdquo; and &ldquo;as available&rdquo; basis to the maximum extent permitted by law.
              </p>
              <p>
                To the fullest extent permitted by law, we disclaim warranties of any kind, whether express, implied,
                statutory, or otherwise, including implied warranties of merchantability, fitness for a particular purpose,
                non-infringement, availability, accuracy, or uninterrupted operation.
              </p>
              <p>
                We do not warrant that the Services will meet every user requirement, that every error will be corrected,
                or that outputs generated through the Services will always be complete, clinically appropriate, or legally
                sufficient without human review where such review is required.
              </p>
            </Section>

            <Section number="14" title="Limitation of Liability">
              <p>
                To the fullest extent permitted by law, Impilo and its affiliates, officers, employees, licensors,
                partners, and agents shall not be liable for any indirect, incidental, special, consequential, exemplary,
                or punitive damages, or for loss of profits, revenue, goodwill, data, or business opportunity arising out
                of or relating to the Services.
              </p>
              <p>
                To the fullest extent permitted by law, Impilo shall not be responsible for the independent acts,
                omissions, policies, disclosures, instructions, clinical decisions, professional judgments, data governance
                failures, unlawful processing, or misconduct of third-party providers, organizations, institutions, or
                users.
              </p>
              <p>
                Nothing in these Terms excludes or limits liability that cannot lawfully be excluded or limited.
              </p>
            </Section>

            <Section number="15" title="Indemnity">
              <p>
                To the extent permitted by law, you agree to indemnify and hold harmless Impilo and its affiliates,
                officers, employees, licensors, and agents from claims, liabilities, damages, losses, and expenses arising
                from your misuse of the Services, your breach of these Terms, your violation of law or third-party rights,
                or your User Content or unauthorized use of data.
              </p>
              <p>
                This clause does not apply to the extent a claim arises from our own unlawful conduct or non-excludable
                liability.
              </p>
            </Section>

            <Section number="16" title="Suspension and Termination">
              <p>We may suspend, restrict, or terminate access to the Services if:</p>
              <ul>
                <li>You breach these Terms</li>
                <li>We reasonably suspect fraud, abuse, or unauthorized access</li>
                <li>We are required to do so by law, regulation, court order, or public authority</li>
                <li>Such action is necessary to protect the security, integrity, or lawful operation of the Services</li>
              </ul>
              <p>
                You may stop using the Services at any time. Account deletion and data deletion are addressed in the{" "}
                <Link href="/privacy" className="text-blue-600 hover:text-blue-800">
                  Privacy Policy
                </Link>{" "}
                and applicable implementation rules.
              </p>
            </Section>

            <Section number="17" title="Governing Law and Dispute Resolution">
              <p>
                These Terms shall be governed by the laws of Zimbabwe, without regard to conflict-of-laws principles,
                unless another mandatory legal framework applies.
              </p>
              <p>
                Any dispute arising from these Terms or the Services shall be resolved in the courts or tribunals of
                Zimbabwe, unless applicable law requires another forum or unless the parties agree otherwise in writing.
              </p>
            </Section>

            <Section number="18" title="Changes to These Terms">
              <p>
                We may update these Terms from time to time. Where required, we will provide notice by publication,
                in-app notice, email, or other reasonable means. Continued use of the Services after the effective date of
                revised Terms constitutes acceptance of the revised Terms, except where law requires additional consent.
              </p>
            </Section>

            <Section number="19" title="Contact Information">
              <p>
                For legal notices, support, or questions about these Terms, contact:
              </p>
              <address className="not-italic text-gray-600">
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
            </Section>
          </div>
        </div>

        {/* Footer */}
        <footer className="mt-8 text-center text-xs text-gray-400 space-y-2">
          <div className="flex items-center justify-center gap-4 flex-wrap">
            <span className="text-gray-300">Terms of Use</span>
            <span>&middot;</span>
            <Link href="/privacy" className="hover:text-gray-600 transition-colors">
              Privacy Policy
            </Link>
            <span>&middot;</span>
            <Link href="/account-deletion" className="hover:text-gray-600 transition-colors">
              Account Deletion
            </Link>
            <span>&middot;</span>
            <Link href="/privacy/app-stores" className="hover:text-gray-600 transition-colors">
              App Store Privacy
            </Link>
            <span>&middot;</span>
            <Link href="/auth/login" className="hover:text-gray-600 transition-colors">
              Sign In
            </Link>
          </div>
          <p>Impilo Technologies Private Limited</p>
        </footer>
      </main>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Helper component for numbered sections                             */
/* ------------------------------------------------------------------ */

function Section({
  number,
  title,
  children,
}: {
  number: string;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className="mb-8">
      <h3 className="text-lg font-semibold text-gray-900 mb-3">
        {number}. {title}
      </h3>
      {children}
    </section>
  );
}
