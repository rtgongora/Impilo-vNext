package zw.gov.mohcc.impilo.tshepo.contracts.enums;

/**
 * Type of authenticated principal making a request — the vocabulary of the {@code X-Actor-Type}
 * trust header and of {@code policy_rule.actor_type}.
 *
 * <p><b>This is a declaration, not an enforcement point.</b> Unlike {@link PurposeOfUse}, which
 * {@code PolicyEngine.parsePurpose} parses and rejects, actor type flows through the PDP as a raw
 * string and is matched against {@code policy_rule.actor_type} by string equality. Nothing at
 * runtime will reject an unknown value — a seed naming an actor type no client emits simply never
 * matches, silently.
 *
 * <p>So the set below has to mean "what clients actually put on the wire", not "what seemed
 * reasonable". It previously carried {@code ADMIN}, which nothing in the estate has ever emitted,
 * and omitted {@code OPERATOR}, which every admin console does emit — while this enum was imported
 * by zero files, so nothing kept it honest. It is now checked against the TypeScript client
 * mirrors by {@code ContractMirrorsTest}, and seeds are checked against it by
 * {@code PolicyRuleSeedVocabularyTest}.
 */
public enum ActorType {
    /** A regulated professional acting in clinical capacity. */
    PROVIDER,
    /** A person acting on their own behalf. */
    CITIZEN,
    /**
     * Administrative / operational staff — facility admins, finance, system admins, support.
     * Emitted by the admin consoles and by experience-bff's session resolution.
     */
    OPERATOR,
    /** A person acting for another (guardian, next of kin, assisted access). */
    CAREGIVER,
    /** Machine-to-machine: schedulers, outbox publishers, write-back gateways. */
    SYSTEM,
    /** A named platform service calling another service under its own identity. */
    SERVICE
}
