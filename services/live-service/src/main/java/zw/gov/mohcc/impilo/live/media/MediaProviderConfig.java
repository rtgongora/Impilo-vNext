package zw.gov.mohcc.impilo.live.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.live.integration.DocumentServiceClient;
import zw.gov.mohcc.impilo.live.integration.RtcGatewayClient;

@Configuration
public class MediaProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(MediaProviderConfig.class);

    @Bean
    public LiveMediaProvider liveMediaProvider(
            @Value("${live.media.provider:local-dev}") String provider,
            RtcGatewayClient rtcGatewayClient,
            DocumentServiceClient documentServiceClient) {
        if ("rtc-gateway".equalsIgnoreCase(provider)) {
            return new RtcGatewayMediaProvider(rtcGatewayClient, documentServiceClient);
        }
        // Loud, not silent: the local-dev provider issues a non-production token
        // ("DEV-TOKEN-NOT-FOR-PRODUCTION"). Set live.media.provider=rtc-gateway in real envs.
        log.warn("LIVE MEDIA using LocalDevMediaProvider (dev-only, NON-PRODUCTION token). "
                + "Set live.media.provider=rtc-gateway for real media sessions.");
        return new LocalDevMediaProvider();
    }
}
