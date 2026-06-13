"use client";

/**
 * Privacy Policy — Public legal page.
 * Route: /privacy
 *
 * Accessible without authentication. Uses a minimal layout
 * with Impilo branding and navigation back to login/home.
 */

import Link from "next/link";
import { ArrowLeft, Shield } from "lucide-react";

export default function PrivacyPolicyPage() {
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
            <Shield className="w-3.5 h-3.5" />
            <span>Privacy Policy</span>
          </div>
        </div>
      </header>

      {/* Content */}
      <main className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-12">
        <div className="bg-card rounded-xl shadow-sm border border-border p-6 sm:p-10">
          <div className="mb-8">
            <h1 className="text-2xl sm:text-3xl font-bold text-foreground mb-2">
              Impilo Privacy Policy
            </h1>
            <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm text-muted-foreground">
              <span>Effective Date: 11 April 2026</span>
              <span>Last Updated: 11 April 2026</span>
            </div>
            <p className="text-sm text-muted-foreground mt-1">
              Entity: Impilo Technologies Private Limited (&ldquo;Impilo,&rdquo; &ldquo;we,&rdquo; &ldquo;us,&rdquo; or &ldquo;our&rdquo;)
            </p>
          </div>

          <div className="prose prose-gray max-w-none prose-headings:text-foreground prose-p:text-muted-foreground prose-li:text-muted-foreground prose-a:text-primary">
            <Section number="1" title="Introduction">
              <p>
                Impilo is a digital health platform designed to support health service delivery, health system operations,
                coordinated care, communication, and related digital services.
              </p>
              <p>
                This Privacy Policy explains how Impilo handles personal data and other information in connection with the
                Impilo platform, websites, mobile applications, portals, interfaces, support services, and related products
                and services (collectively, the &ldquo;Platform&rdquo;).
              </p>
              <p>
                Impilo is committed to privacy by design, confidentiality, data minimisation, appropriate transparency, and
                respect for applicable data protection, health information, data residency, and data sovereignty requirements.
              </p>
              <p>
                By accessing or using the Platform, you acknowledge that you have read this Privacy Policy.
              </p>
            </Section>

            <Section number="2" title="Privacy Summary">
              <p>
                Impilo Technologies Private Limited is committed to protecting personal data and respecting applicable
                privacy, data residency, sovereignty, and health information governance requirements. Depending on the
                deployment and the features used, Impilo may process account information, contact details, device and
                technical data, usage information, location data where enabled, communications, and certain health or
                service-related information.
              </p>
              <p>
                Impilo is designed to apply privacy-by-design principles, including the separation of personally
                identifiable information from health or clinical information, controlled access, audit capability, and
                minimum-necessary access safeguards. Impilo does not sell personal data or health-related data.
              </p>
              <p>
                Where health or clinical information is stored, managed, or retained, this is ordinarily done by authorized
                healthcare providers, healthcare organizations, or other legally mandated entities for lawful purposes such
                as care delivery, continuity of care, quality assurance, health administration, legal compliance, and public
                health. Those providers and organizations remain independently responsible for their own compliance with
                applicable privacy, health information, and public health laws in relation to the data they control or process.
              </p>
              <p>
                Impilo may use and disclose information for purposes including account administration, authentication,
                service delivery, care coordination, platform operations, security, fraud prevention, support, legal
                compliance, quality improvement, and other lawful and legitimate purposes described in this Privacy Policy.
                Information may be shared with authorized providers, institutions, service providers acting on Impilo&rsquo;s
                behalf, regulators or public authorities where required by law, and other parties where lawfully authorized
                or instructed.
              </p>
              <p>
                Users may have rights, subject to applicable law, to access personal data, request correction, request
                deletion of certain data, object to or restrict certain processing, withdraw consent where consent is the
                legal basis, and lodge complaints with a competent authority. Requests relating to privacy, data protection,
                or account deletion may be directed to{" "}
                <a href="mailto:support@impilo.io">support@impilo.io</a> or through{" "}
                <a href="https://www.impilo.io" target="_blank" rel="noopener noreferrer">www.impilo.io</a>.
              </p>
              <p>
                <strong>Account Deletion Summary:</strong> Users may request and initiate account deletion directly within
                Impilo vNext, through other available account settings, through{" "}
                <a href="https://www.impilo.io" target="_blank" rel="noopener noreferrer">www.impilo.io</a>, or by emailing{" "}
                <a href="mailto:support@impilo.io">support@impilo.io</a>. Some information may be retained where required
                by law or for lawful purposes such as security, audit, fraud prevention, public health, continuity of care,
                and recordkeeping obligations.
              </p>
            </Section>

            <Section number="3" title="Scope">
              <p>
                This Privacy Policy applies to personal data and other information processed through the Platform in
                connection with individual users, including patients, clients, caregivers, and members of the public;
                healthcare professionals and other authorized users; healthcare providers, healthcare organizations,
                employers, payers, and institutional users; and visitors to our websites, portals, and support channels.
              </p>
              <p>
                This Privacy Policy does not replace any provider-specific, employer-specific, insurer-specific, or
                institution-specific privacy notices that may also apply to a particular implementation of Impilo.
              </p>
            </Section>

            <Section number="4" title="Roles and Responsibilities">
              <p>
                Depending on the deployment model, Impilo may act as a platform provider, technology provider, processor,
                service provider, or similar role, while a healthcare provider, healthcare organization, employer, payer,
                public authority, or other institution may act as the controller, custodian, or legally responsible entity
                for certain personal data.
              </p>
              <p>
                Where a provider or organization determines the purposes and means of processing personal data, health
                information, or related records, that provider or organization remains independently responsible for its
                own legal compliance, notices, permissions, retention obligations, and governance decisions.
              </p>
              <p>
                Impilo provides privacy-supporting technical architecture and controls, but does not assume responsibility
                for the independent acts, omissions, decisions, policies, disclosures, or unlawful processing of third-party
                providers, institutions, or users beyond Impilo&rsquo;s own role and legal obligations.
              </p>
            </Section>

            <Section number="5" title="Information We May Collect">
              <p>
                Depending on the services used and the deployment context, Impilo may process the following categories of
                information.
              </p>

              <h4>5.1 Account and Identity Information</h4>
              <p>
                This may include name, username, password credentials, phone number, email address, organization name,
                role, staff identifier, and account preferences.
              </p>

              <h4>5.2 Contact and Support Information</h4>
              <p>
                This may include information you provide in support requests, feedback, complaints, contact forms, emails,
                or customer-service interactions.
              </p>

              <h4>5.3 Device and Technical Information</h4>
              <p>
                This may include IP address, device type, device identifiers, operating system, browser type, app version,
                crash logs, diagnostics, and technical usage data.
              </p>

              <h4>5.4 Usage Information</h4>
              <p>
                This may include how you use the Platform, viewed pages or features, session data, clicks, interactions,
                dates and times of access, and related activity logs.
              </p>

              <h4>5.5 Location Information</h4>
              <p>
                Where enabled and legally appropriate, the Platform may process approximate or precise location information
                for functionality, safety, routing, service optimization, or other lawful operational purposes.
              </p>

              <h4>5.6 Communications and User-Submitted Content</h4>
              <p>
                This may include content you choose to submit, upload, send, or store through the Platform, including forms,
                messages, attachments, images, and other materials.
              </p>

              <h4>5.7 Health and Service-Related Information</h4>
              <p>
                Depending on the deployment, authorized provider users or connected systems may process health, service,
                clinical, operational, or wellness-related information through the Platform.
              </p>
              <p>
                Impilo is architected so that personally identifiable information and health or clinical information are
                separated and not unnecessarily stored together in directly identifiable form. Impilo does not operate as a
                general-purpose central repository of directly identifiable health records unless a specific lawful
                deployment, implementation, or approved operating arrangement requires otherwise.
              </p>

              <h4>5.8 Cookies and Similar Technologies</h4>
              <p>
                Where applicable, we may use cookies, tokens, local storage, or similar technologies for authentication,
                security, analytics, session management, and performance.
              </p>
            </Section>

            <Section number="6" title="How We Collect Information">
              <p>
                We may collect information directly from you, from your device or browser, from healthcare providers,
                employers, institutions, or organizations that authorize your use of the Platform, from integrated systems,
                applications, or trusted partners, from app permissions you grant, and automatically through your use of the
                Platform.
              </p>
            </Section>

            <Section number="7" title="How We Use Information">
              <p>We may use information for purposes including:</p>
              <ul>
                <li>Creating and administering accounts</li>
                <li>Authenticating users and maintaining secure access</li>
                <li>Providing the Platform and its features</li>
                <li>Enabling care delivery, care coordination, referrals, communication, scheduling, support, and related services</li>
                <li>Facilitating continuity of care and health system functions where applicable</li>
                <li>Operating, maintaining, troubleshooting, and improving the Platform</li>
                <li>Securing systems, detecting misuse, preventing fraud, protecting users</li>
                <li>Responding to support requests, communicating with users</li>
                <li>Complying with law, regulation, professional obligations, public health requirements, or lawful requests</li>
                <li>Enforcing our Terms of Use and protecting our rights</li>
                <li>Conducting analytics, service monitoring, and quality improvement</li>
              </ul>
            </Section>

            <Section number="8" title="Legal Bases for Processing">
              <p>Where applicable under law, we may process personal data on one or more of the following grounds:</p>
              <ul>
                <li>Your consent</li>
                <li>Performance of a contract</li>
                <li>Compliance with legal obligations</li>
                <li>Protection of vital interests</li>
                <li>Performance of tasks carried out in the public interest</li>
                <li>Provision and administration of health or care services where permitted by law</li>
                <li>Legitimate interests, where those interests are not overridden by your rights and interests</li>
              </ul>
              <p>
                Where consent is required, we will seek it in an appropriate manner. Where consent is not the controlling
                legal basis, processing may still occur where permitted or required for care delivery, legal compliance,
                patient safety, security, fraud prevention, or public health.
              </p>
            </Section>

            <Section number="9" title="Data Residency, Sovereignty, and Privacy by Design">
              <p>
                Impilo is designed to respect applicable data residency, data sovereignty, privacy, and health information
                governance requirements in the jurisdictions where it is used.
              </p>
              <p>
                Impilo applies privacy-by-design principles, including separation of personally identifiable information
                from health or clinical information, role-based and purpose-based controls where applicable, minimum-necessary
                access, audit capability, and other safeguards intended to reduce the risk of unnecessary identification or
                unauthorized linkage.
              </p>
              <p>
                Where health or clinical data is stored, managed, or retained, this is ordinarily done by authorized
                healthcare providers, healthcare organizations, or other legally mandated entities for lawful purposes
                such as care delivery, continuity of care, quality assurance, health administration, legal compliance,
                and public health.
              </p>
            </Section>

            <Section number="10" title="Sharing and Disclosure">
              <p>We may share information only where lawful and appropriate, including with:</p>
              <ul>
                <li>Healthcare providers, healthcare organizations, and authorized institutional users</li>
                <li>Service providers, hosting providers, infrastructure providers, support providers, and security providers acting on our behalf</li>
                <li>Analytics or technical partners where permitted and appropriately governed</li>
                <li>Regulators, courts, law-enforcement agencies, or public authorities where required by law</li>
                <li>Successor entities in connection with a merger, acquisition, restructuring, or transfer of assets</li>
                <li>Other parties where you or the responsible institution has authorized or instructed us to do so</li>
              </ul>
              <p>We do not sell personal data or health-related data.</p>
            </Section>

            <Section number="11" title="Provider Responsibility">
              <p>
                Each provider, organization, payer, employer, or institution using Impilo remains independently responsible
                for its own compliance with applicable privacy, health information, professional, confidentiality, retention,
                and public health laws in relation to the data it collects, accesses, uses, discloses, stores, retains, or
                otherwise processes through its own operations.
              </p>
              <p>
                Impilo is not responsible for the independent acts, omissions, decisions, configurations, policies,
                disclosures, instructions, misuse, or unlawful processing of third-party providers or organizations beyond
                Impilo&rsquo;s own role and legal obligations.
              </p>
            </Section>

            <Section number="12" title="Data Retention">
              <p>
                We retain personal data and related records only for as long as necessary for the lawful purposes for which
                they are processed, including:
              </p>
              <ul>
                <li>Providing services</li>
                <li>Maintaining continuity of care where applicable</li>
                <li>Complying with legal, regulatory, audit, and recordkeeping obligations</li>
                <li>Resolving disputes</li>
                <li>Preventing fraud and misuse</li>
                <li>Maintaining security</li>
                <li>Supporting legitimate operational and public health functions</li>
              </ul>
              <p>
                Retention periods may vary depending on the type of data, the deployment context, and applicable law.
              </p>
            </Section>

            <Section number="13" title="Account Deletion">
              <p>
                Users may request and initiate deletion of their Impilo account directly within Impilo vNext, through any
                other available in-app account settings, through the deletion request mechanism made available on{" "}
                <a href="https://www.impilo.io" target="_blank" rel="noopener noreferrer">www.impilo.io</a>, or by
                contacting <a href="mailto:support@impilo.io">support@impilo.io</a>.
              </p>
              <p>
                Upon receipt and verification of a valid deletion request, Impilo Technologies Private Limited will take
                reasonable steps to delete or de-identify personal data associated with the account that it controls,
                within a reasonable period and subject to applicable law, regulatory obligations, technical constraints,
                and legitimate operational requirements.
              </p>
              <p>
                Deletion of an account does not necessarily result in the immediate deletion of all related information.
                Certain data may be retained where necessary or permitted for lawful reasons, including security, fraud
                prevention, dispute resolution, audit requirements, compliance with legal or regulatory obligations,
                enforcement of contractual rights, public health obligations, continuity-of-care requirements, or
                recordkeeping obligations imposed on authorized healthcare providers, healthcare organizations, employers,
                payers, or other institutions using the Platform.
              </p>
              <p>
                Where health, clinical, or service records are held by authorized providers, healthcare organizations, or
                other legally responsible entities, those records may continue to be retained and governed by those entities
                in accordance with their own legal, clinical, professional, and public health obligations. In such cases,
                Impilo Technologies Private Limited is not responsible for deleting records that are not under its direct
                control.
              </p>
              <p>
                Where immediate deletion is not technically possible, relevant data may first be restricted from active use
                and thereafter securely deleted, anonymized, or de-identified in accordance with applicable retention
                schedules, system constraints, and backup-cycle requirements.
              </p>
              <p>
                Users who are unable to access the in-app deletion function in Impilo vNext or other available account
                settings may submit an account deletion request through{" "}
                <a href="https://www.impilo.io" target="_blank" rel="noopener noreferrer">www.impilo.io</a> or by emailing{" "}
                <a href="mailto:support@impilo.io">support@impilo.io</a>. Impilo Technologies Private Limited may request
                reasonable information necessary to verify identity and prevent unauthorized deletion requests.
              </p>
            </Section>

            <Section number="14" title="Security">
              <p>
                We use reasonable technical, administrative, and organizational safeguards designed to protect information
                against unauthorized access, loss, misuse, alteration, or disclosure. These measures may include access
                controls, authentication, encryption in transit and at rest where appropriate, audit logging, secure
                hosting, segmentation, and incident response procedures.
              </p>
              <p>No method of transmission or storage is completely secure, and we do not guarantee absolute security.</p>
            </Section>

            <Section number="15" title="International Transfers">
              <p>
                Where information is transferred across borders, we will take steps appropriate to the applicable legal
                and regulatory framework, which may include contractual safeguards, access controls, jurisdiction-specific
                hosting choices, and other appropriate measures.
              </p>
            </Section>

            <Section number="16" title="Your Rights">
              <p>Subject to applicable law, you may have rights to:</p>
              <ul>
                <li>Access personal data</li>
                <li>Request correction of inaccurate data</li>
                <li>Request deletion of certain data</li>
                <li>Restrict or object to certain processing</li>
                <li>Withdraw consent where consent applies</li>
                <li>Receive information about processing</li>
                <li>Request portability where applicable</li>
                <li>Lodge a complaint with a regulator or supervisory authority</li>
              </ul>
              <p>
                These rights may be limited where data must be retained or processed for care delivery, legal obligations,
                patient safety, security, public interest, or public health purposes.
              </p>
            </Section>

            <Section number="17" title="Children and Age-Appropriate Use">
              <p>
                Where the Platform is intended for or used by minors, additional safeguards may apply. Where required by
                law, consent or authorization from a parent, guardian, institution, or other lawful authority may be required.
              </p>
              <p>We do not knowingly permit unlawful collection or misuse of children&rsquo;s data.</p>
            </Section>

            <Section number="18" title="Third-Party Services">
              <p>
                The Platform may link to or integrate with third-party systems, applications, devices, or services. We are
                not responsible for the privacy practices of third parties except to the extent required by law or contract
                in relation to our own role.
              </p>
            </Section>

            <Section number="19" title="Changes to This Policy">
              <p>
                We may update this Privacy Policy from time to time. Where required, we will provide notice through the
                Platform, by email, by publication on our website, or by other appropriate means.
              </p>
            </Section>

            <Section number="20" title="Contact Us">
              <p>
                For privacy questions, requests, complaints, or account deletion requests, contact:
              </p>
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
            </Section>
          </div>
        </div>

        {/* Footer */}
        <footer className="mt-8 text-center text-xs text-muted-foreground space-y-2">
          <div className="flex items-center justify-center gap-4 flex-wrap">
            <Link href="/terms" className="hover:text-muted-foreground transition-colors">
              Terms of Use
            </Link>
            <span>&middot;</span>
            <span className="text-muted-foreground">Privacy Policy</span>
            <span>&middot;</span>
            <Link href="/account-deletion" className="hover:text-muted-foreground transition-colors">
              Account Deletion
            </Link>
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
      <h3 className="text-lg font-semibold text-foreground mb-3">
        {number}. {title}
      </h3>
      {children}
    </section>
  );
}
