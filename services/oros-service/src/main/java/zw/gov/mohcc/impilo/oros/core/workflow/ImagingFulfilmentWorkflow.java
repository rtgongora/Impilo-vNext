package zw.gov.mohcc.impilo.oros.core.workflow;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.oros.core.ImagingWorkflow;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.domain.OrderType;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link FulfilmentWorkflow} adapter for IMAGING orders. Delegates to the canonical static
 * {@link ImagingWorkflow} guard so the imaging transition graph remains defined in exactly one
 * place (and the existing {@code ImagingWorkflowService} keeps using it directly).
 */
@Component
public class ImagingFulfilmentWorkflow implements FulfilmentWorkflow {

    @Override
    public OrderType orderType() {
        return OrderType.IMAGING;
    }

    @Override
    public String entryState() {
        return ImagingWorkflowState.RECEIVED.name();
    }

    @Override
    public Set<String> states() {
        return Arrays.stream(ImagingWorkflowState.values()).map(Enum::name).collect(Collectors.toSet());
    }

    @Override
    public boolean canTransition(String from, String to) {
        ImagingWorkflowState target = parse(to);
        if (target == null) {
            return false;
        }
        return ImagingWorkflow.canTransition(parse(from), target);
    }

    @Override
    public Set<String> allowedFrom(String from) {
        return ImagingWorkflow.allowedFrom(parse(from)).stream()
                .map(Enum::name).collect(Collectors.toSet());
    }

    private ImagingWorkflowState parse(String name) {
        if (name == null) {
            return null;
        }
        try {
            return ImagingWorkflowState.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
