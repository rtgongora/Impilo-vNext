package zw.gov.mohcc.impilo.tshepo.authz.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.ActorType;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.PurposeOfUse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Guard: the TypeScript mirrors of the trust-header vocabularies must agree with the Java enums.
 *
 * <p>These two vocabularies fail in different ways, so they are checked differently.
 *
 * <p><b>{@link PurposeOfUse} — exact match.</b> {@code PolicyEngine.parsePurpose} parses the header
 * and rejects anything outside the enum with {@code INVALID_PURPOSE} at Step 2, before any rule is
 * consulted. So a code in TypeScript but not Java is a request the PDP always denies (this is how
 * the nhume write-back came to send {@code LOGISTICS} on every call), and a code in Java but not
 * TypeScript is surface no client can reach. Both directions are defects.
 *
 * <p><b>{@link ActorType} — TypeScript must be a subset.</b> Actor type is never parsed; it flows
 * through the PDP as a raw string matched against {@code policy_rule.actor_type} by equality. So a
 * value a client emits but the vocabulary does not name can never appear in a working seed —
 * that direction is a defect. The reverse is not: {@code SYSTEM} and {@code SERVICE} are emitted
 * only by service-to-service callers in Java, and no browser or mobile union has any business
 * declaring them.
 */
@DisplayName("TypeScript trust-header mirrors agree with the Java enums")
class ContractMirrorsTest {

    @Test
    void purposeOfUseMirrorsMatchTheJavaEnumExactly() {
        Set<String> java = names(PurposeOfUse.values());
        List<String> problems = new ArrayList<>();

        Map<String, Set<String>> mirrors = ContractMirrors.membersByMirror("PurposeOfUse");
        assertThat(mirrors).as("mirrors declaring PurposeOfUse").isNotEmpty();

        mirrors.forEach((mirror, ts) -> {
            Set<String> missing = difference(java, ts);
            Set<String> extra = difference(ts, java);
            if (!missing.isEmpty()) {
                problems.add(mirror + " — missing " + missing
                        + "; these Java codes are unreachable from this client");
            }
            if (!extra.isEmpty()) {
                problems.add(mirror + " — declares " + extra
                        + " which the Java enum does not have; requests carrying these are denied"
                        + " INVALID_PURPOSE before any rule is evaluated");
            }
        });

        if (!problems.isEmpty()) {
            fail("TypeScript PurposeOfUse mirrors have drifted from the Java enum:%n%n%s%n%n"
                    + "The Java enum is the authority — PolicyEngine.parsePurpose enforces it."
                    + " Either add the code there (and give it a visibility envelope in"
                    + " VisibilityObligationComposer.purposeDefaults), or drop it from the union.",
                    String.join("\n", problems));
        }
    }

    @Test
    void everyClientDeclarableActorTypeIsInTheJavaEnum() {
        Set<String> java = names(ActorType.values());
        List<String> problems = new ArrayList<>();

        Map<String, Set<String>> mirrors = ContractMirrors.membersByMirror("ActorType");
        assertThat(mirrors).as("mirrors declaring ActorType").isNotEmpty();

        mirrors.forEach((mirror, ts) -> {
            Set<String> unknown = difference(ts, java);
            if (!unknown.isEmpty()) {
                problems.add(mirror + " — declares " + unknown + ", which "
                        + ActorType.class.getSimpleName() + " does not name");
            }
        });

        if (!problems.isEmpty()) {
            fail("Clients can emit actor types the trust vocabulary does not know:%n%n%s%n%n"
                    + "Nothing rejects these at runtime — actor type is matched by raw string"
                    + " equality — so any policy_rule written for such an actor silently never"
                    + " fires. Add the value to %s, or stop emitting it.",
                    String.join("\n", problems), ActorType.class.getName());
        }
    }

    /**
     * The counterpart to the subset rule: an enum member that no client declares and that is not
     * service-side is unreachable, so a seed naming it would be dead on arrival. This is the check
     * {@code ADMIN} would have failed — it sat in the enum, was emitted by nobody, and was
     * imported by no Java file, so nothing ever contradicted it.
     */
    @Test
    void everyActorTypeIsEmittableBySomeone() {
        Set<String> clientDeclarable = ContractMirrors.declaredByAnyClient("ActorType");
        Set<String> reachable = new LinkedHashSet<>(clientDeclarable);
        reachable.addAll(ContractMirrors.SERVICE_SIDE_ACTOR_TYPES);

        Set<String> orphans = difference(names(ActorType.values()), reachable);

        if (!orphans.isEmpty()) {
            fail("%s declares %s, which no client mirror emits and which is not service-side.%n%n"
                    + "Client-declarable: %s%nService-side: %s%n%n"
                    + "An actor type nobody sends cannot match anything, so a seed written for it"
                    + " would be silently dead. Either add it to a client union because something"
                    + " really does emit it, list it in ContractMirrors.SERVICE_SIDE_ACTOR_TYPES,"
                    + " or remove it from the enum.",
                    ActorType.class.getSimpleName(), orphans,
                    clientDeclarable, ContractMirrors.SERVICE_SIDE_ACTOR_TYPES);
        }
    }

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> difference(Set<String> from, Set<String> remove) {
        Set<String> out = new LinkedHashSet<>(from);
        out.removeAll(remove);
        return out;
    }
}
