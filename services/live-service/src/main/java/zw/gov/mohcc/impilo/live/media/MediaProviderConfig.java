package zw.gov.mohcc.impilo.live.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.live.integration.RtcGatewayClient;

@Configuration
public class MediaProviderConfig {

    @Bean
    public LiveMediaProvider liveMediaProvider(
            @Value("${live.media.provider:local-dev}") String provider,
            RtcGatewayClient rtcGatewayClient) {
        if ("rtc-gateway".equalsIgnoreCase(provider)) {
            return new RtcGatewayMediaProvider(rtcGatewayClient);
        }
        return new LocalDevMediaProvider();
    }
}
