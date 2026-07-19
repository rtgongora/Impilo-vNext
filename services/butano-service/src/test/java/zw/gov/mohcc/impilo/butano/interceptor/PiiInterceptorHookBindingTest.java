package zw.gov.mohcc.impilo.butano.interceptor;

import ca.uhn.fhir.interceptor.api.HookParams;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.interceptor.executor.InterceptorService;
import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.storage.TransactionDetails;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.butano.config.ButanoProperties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the PII interceptor is actually invoked by HAPI's interceptor
 * dispatcher — not just when its methods are called directly.
 *
 * <p>HAPI binds {@code @Hook} method parameters by the pointcut's declared
 * types. {@code STORAGE_PRESTORAGE_RESOURCE_CREATED} declares
 * {@code IBaseResource}; a hook parameter declared as the concrete R4
 * {@code Resource} matches nothing and HAPI silently passes {@code null},
 * disabling the interceptor at runtime while direct-call unit tests stay
 * green. That exact failure shipped: every live FHIR create either bypassed
 * PII validation or 500'd in provenance stamping. This test dispatches
 * through a real {@link InterceptorService} so a signature regression fails
 * the build instead of the estate.</p>
 */
@ExtendWith(MockitoExtension.class)
class PiiInterceptorHookBindingTest {

    @Mock
    private RequestDetails requestDetails;

    private InterceptorService interceptorService;

    @BeforeEach
    void setUp() {
        ButanoProperties properties = new ButanoProperties();
        properties.getPii().setEnforce(true);
        interceptorService = new InterceptorService();
        interceptorService.registerInterceptor(new PiiPreventionInterceptor(properties));
    }

    private HookParams createdParams(Patient patient) {
        return new HookParams()
                .add(IBaseResource.class, patient)
                .add(RequestDetails.class, requestDetails)
                .add(ServletRequestDetails.class, null)
                .add(TransactionDetails.class, new TransactionDetails())
                .add(RequestPartitionId.class, null);
    }

    private HookParams updatedParams(Patient oldPatient, Patient newPatient) {
        return new HookParams()
                .add(IBaseResource.class, oldPatient)
                .add(IBaseResource.class, newPatient)
                .add(RequestDetails.class, requestDetails)
                .add(ServletRequestDetails.class, null)
                .add(TransactionDetails.class, new TransactionDetails());
    }

    private Patient namedPatient() {
        Patient patient = new Patient();
        patient.addIdentifier()
                .setSystem(PiiPreventionInterceptor.CPID_SYSTEM)
                .setValue("CPID-BINDING-1");
        patient.addName().setFamily("Leak").addGiven("Via-Dispatcher");
        return patient;
    }

    private Patient cleanPatient() {
        Patient patient = new Patient();
        patient.addIdentifier()
                .setSystem(PiiPreventionInterceptor.CPID_SYSTEM)
                .setValue("CPID-BINDING-2");
        return patient;
    }

    @Test
    @DisplayName("dispatched STORAGE_PRESTORAGE_RESOURCE_CREATED rejects a named Patient (binding intact)")
    void createdHookReceivesResourceThroughDispatch() {
        assertThrows(UnprocessableEntityException.class,
                () -> interceptorService.callHooks(
                        Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED, createdParams(namedPatient())),
                "PII interceptor must receive the resource via HAPI dispatch and reject it");
    }

    @Test
    @DisplayName("dispatched create accepts a CPID-only Patient")
    void createdHookAcceptsCleanPatient() {
        assertDoesNotThrow(() -> interceptorService.callHooks(
                Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED, createdParams(cleanPatient())));
    }

    @Test
    @DisplayName("dispatched STORAGE_PRESTORAGE_RESOURCE_UPDATED validates the NEW resource")
    void updatedHookReceivesNewResourceThroughDispatch() {
        assertThrows(UnprocessableEntityException.class,
                () -> interceptorService.callHooks(
                        Pointcut.STORAGE_PRESTORAGE_RESOURCE_UPDATED, updatedParams(cleanPatient(), namedPatient())),
                "PII interceptor must validate the updated (new) resource via HAPI dispatch");
    }
}
