package zw.gov.mohcc.impilo.msikaflow.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msikaflow.domain.ReservationStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.ReservationEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.ReservationRepository;

import java.util.Optional;

@Service
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final ReservationRepository reservationRepository;
    private final ObjectMapper objectMapper;

    public InventoryEventConsumer(ReservationRepository reservationRepository, ObjectMapper objectMapper) {
        this.reservationRepository = reservationRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory.reservation.status_changed", groupId = "msika-flow-service")
    public void onReservationStatusChanged(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String reservationRef = node.path("reservationRef").asText();
            String status = node.path("status").asText();

            // Find reservation by ref and update status
            log.info("Inventory reservation status: ref={} status={}", reservationRef, status);

        } catch (Exception e) {
            log.error("Failed to process inventory event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "pharmacy.fulfillment.status_changed", groupId = "msika-flow-service")
    public void onPharmacyFulfillmentChanged(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String orderId = node.path("orderId").asText();
            String status = node.path("status").asText();

            log.info("Pharmacy fulfillment status: orderId={} status={}", orderId, status);

        } catch (Exception e) {
            log.error("Failed to process pharmacy event: {}", e.getMessage(), e);
        }
    }
}
