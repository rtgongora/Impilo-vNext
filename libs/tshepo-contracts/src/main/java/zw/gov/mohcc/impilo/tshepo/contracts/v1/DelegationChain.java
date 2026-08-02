package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/** Ordered delegation hops. Each hop must be ≤ privileged than its predecessor. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DelegationChain(
        String contractVersion,
        List<DelegatedHumanContext> hops,
        int maxDepth
) {
    public DelegationChain {
        TrustContractVersion.requireV1(contractVersion);
        hops = hops == null ? List.of() : List.copyOf(hops);
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be >= 0");
        }
        if (hops.size() > maxDepth) {
            throw new IllegalArgumentException("delegation chain exceeds maxDepth");
        }
        for (int i = 1; i < hops.size(); i++) {
            if (!hops.get(i - 1).permitsDownstream(hops.get(i))) {
                throw new IllegalArgumentException(
                        "delegation hop " + i + " increases privilege relative to predecessor");
            }
        }
    }

    public static DelegationChain of(List<DelegatedHumanContext> hops, int maxDepth) {
        return new DelegationChain(TrustContractVersion.V1, hops, maxDepth);
    }

    public static DelegationChain empty(int maxDepth) {
        return of(List.of(), maxDepth);
    }

    public DelegationChain append(DelegatedHumanContext next) {
        List<DelegatedHumanContext> copy = new ArrayList<>(hops);
        copy.add(next);
        return of(copy, maxDepth);
    }
}
