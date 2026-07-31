package zw.gov.mohcc.impilo.tshepo.audit.keycloak;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class KeycloakEventMapperTest {
 @Test void excludesSecretsAndCodesFromAuditDetail() throws Exception {var event=new ObjectMapper().readTree("{\"id\":\"e1\",\"time\":1,\"type\":\"LOGIN_ERROR\",\"userId\":\"u1\",\"error\":\"invalid_user_credentials\",\"details\":{\"credential_type\":\"otp\",\"otp\":\"123456\",\"recovery_code\":\"SECRET\"}}");var mapped=KeycloakEventMapper.map(UUID.randomUUID(),"user",event);String text=mapped.detail().toString();assertTrue(text.contains("credential_type"));assertFalse(text.contains("123456"));assertFalse(text.contains("SECRET"));}
 @Test void stableIdDeduplicatesReplay(){var event=new ObjectMapper().createObjectNode().put("id","abc");assertEquals(KeycloakEventMapper.eventKey("user",event),KeycloakEventMapper.eventKey("user",event));}
}
