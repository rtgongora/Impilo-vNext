package zw.gov.mohcc.impilo.nhume.integration.autonomous;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Registry that resolves the right {@link AutonomousDeliveryAdapter} for a
 * provider code. Falls back to the simulation adapter when no other adapter
 * supports the request, which keeps demos and tests deterministic.
 */
@Component
public class AutonomousAdapterRegistry {

    private final List<AutonomousDeliveryAdapter> adapters;
    private final SimulatedAutonomousAdapter simulated;

    public AutonomousAdapterRegistry(List<AutonomousDeliveryAdapter> adapters,
                                     SimulatedAutonomousAdapter simulated) {
        this.adapters = adapters;
        this.simulated = simulated;
    }

    public AutonomousDeliveryAdapter resolve(String providerCode) {
        return adapters.stream()
                .filter(a -> a.supports(providerCode))
                .findFirst()
                .orElse(simulated);
    }

    public Optional<AutonomousDeliveryAdapter> tryResolve(String providerCode) {
        return adapters.stream().filter(a -> a.supports(providerCode)).findFirst();
    }
}
