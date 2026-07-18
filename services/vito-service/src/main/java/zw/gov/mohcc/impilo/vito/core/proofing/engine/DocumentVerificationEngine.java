package zw.gov.mohcc.impilo.vito.core.proofing.engine;

/**
 * Pluggable document-integrity capability (doctrine §6, distinct from facial-compare,
 * liveness, and biometric matching). Confirms that a presented identity document is
 * internally consistent and well-formed — it does <b>not</b> confirm the document is
 * genuine against an authoritative register (that is the CRVS/RG link, X2).
 *
 * <p>The default {@link NativeDocumentVerificationEngine} provides a real native
 * capability — ICAO 9303 MRZ check-digit validation — so document proofing works
 * before any vendor forensics SDK is integrated. A vendor engine (hologram/UV/tamper
 * forensics) registers as a {@code @Primary} bean to supersede it.</p>
 */
public interface DocumentVerificationEngine {

    String CAPABILITY = "DOCUMENT_INTEGRITY";

    ProofingSignal verifyDocument(DocumentEvidence evidence);
}
