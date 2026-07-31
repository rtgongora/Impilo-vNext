package zw.gov.mohcc.impilo.tshepo.audit.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class KeycloakEventIngestor {
 private final RestTemplate rest; private final KeycloakEventRecorder recorder;
 @Value("${impilo.keycloak.events.enabled:false}") boolean enabled;
 @Value("${impilo.keycloak.base-url:http://keycloak:8080}") String baseUrl;
 @Value("${impilo.keycloak.realm:impilo}") String realm;
 @Value("${impilo.keycloak.events.client-id:impilo-event-reader}") String clientId;
 @Value("${impilo.keycloak.events.client-secret:}") String secret;
 @Value("${impilo.keycloak.events.overlap-seconds:120}") int overlapSeconds;
 @Value("${impilo.keycloak.events.batch-size:200}") int batchSize;
 @Value("${impilo.keycloak.events.tenant-id:00000000-0000-0000-0000-000000000001}") UUID tenantId;
 public KeycloakEventIngestor(RestTemplate rest,KeycloakEventRecorder recorder){this.rest=rest;this.recorder=recorder;}
 @Scheduled(fixedDelayString="${impilo.keycloak.events.poll-ms:30000}") public void poll(){if(!enabled)return;String token=token();if(token==null)throw new IllegalStateException("Keycloak event-reader credentials unavailable");pollSource("user","/events",token);pollSource("admin","/admin-events",token);}
 void pollSource(String source,String path,String token){HttpHeaders h=new HttpHeaders();h.setBearerAuth(token);String since=Instant.now().minusSeconds(overlapSeconds).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();JsonNode events=rest.exchange(baseUrl+"/admin/realms/"+realm+path+"?dateFrom="+since+"&max="+batchSize,HttpMethod.GET,new HttpEntity<>(h),JsonNode.class).getBody();if(events!=null&&events.isArray())events.forEach(e->ingest(source,e));}
 void ingest(String source,JsonNode event){try{recorder.record(tenantId,source,event);}catch(DataIntegrityViolationException duplicate){/* Another replica already claimed this Keycloak event. */}}
 private String token(){if(secret==null||secret.isBlank())return null;var form=new LinkedMultiValueMap<String,String>();form.add("grant_type","client_credentials");form.add("client_id",clientId);form.add("client_secret",secret);HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);JsonNode body=rest.postForEntity(baseUrl+"/realms/"+realm+"/protocol/openid-connect/token",new HttpEntity<>(form,h),JsonNode.class).getBody();return body==null?null:body.path("access_token").asText(null);}
}
