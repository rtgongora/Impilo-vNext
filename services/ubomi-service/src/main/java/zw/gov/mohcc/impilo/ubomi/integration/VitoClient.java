package zw.gov.mohcc.impilo.ubomi.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import zw.gov.mohcc.impilo.ubomi.config.UbomiProperties;

import java.util.Map;

@Service
public class VitoClient {

    private static final Logger log = LoggerFactory.getLogger(VitoClient.class);

    private final RestClient restClient;

    public VitoClient(UbomiProperties ubomiProperties,
                       RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(ubomiProperties.getVito().getBaseUrl())
                .build();
    }

    public void notifyBirthRegistration(String motherCpid, String notificationNumber, String tenantId) {
        try {
            restClient.post()
                    .uri("/internal/v1/clients/{cpid}/birth-notif", motherCpid)
                    .body(Map.of(
                            "notificationNumber", notificationNumber,
                            "tenantId", tenantId,
                            "action", "BIRTH_REGISTERED"
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Notified VITO of birth registration for motherCpid={}, notificationNumber={}",
                    motherCpid, notificationNumber);
        } catch (RestClientException e) {
            log.warn("Failed to notify VITO of birth registration for motherCpid={}: {}",
                    motherCpid, e.getMessage());
        }
    }

    public void notifyDeath(String deceasedCpid, String notificationNumber, String tenantId) {
        try {
            restClient.post()
                    .uri("/internal/v1/clients/{cpid}/death-notif", deceasedCpid)
                    .body(Map.of(
                            "notificationNumber", notificationNumber,
                            "tenantId", tenantId,
                            "action", "DEATH_REGISTERED"
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Notified VITO of death for deceasedCpid={}, notificationNumber={}",
                    deceasedCpid, notificationNumber);
        } catch (RestClientException e) {
            log.warn("Failed to notify VITO of death for deceasedCpid={}: {}",
                    deceasedCpid, e.getMessage());
        }
    }
}
