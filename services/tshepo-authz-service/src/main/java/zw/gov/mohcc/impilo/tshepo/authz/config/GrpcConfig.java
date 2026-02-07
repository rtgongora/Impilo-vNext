package zw.gov.mohcc.impilo.tshepo.authz.config;

import io.grpc.ServerInterceptor;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC server configuration.
 *
 * <p>Registers global interceptors for logging and metrics on the gRPC
 * ext_authz endpoint (port 9090).</p>
 */
@Configuration
public class GrpcConfig {

    private static final Logger log = LoggerFactory.getLogger(GrpcConfig.class);

    /**
     * Global interceptor that logs every gRPC call for observability.
     * Measures request duration and logs errors.
     */
    @GrpcGlobalServerInterceptor
    public ServerInterceptor loggingInterceptor() {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {

                long startTime = System.nanoTime();
                String methodName = call.getMethodDescriptor().getFullMethodName();

                log.debug("gRPC call started: {}", methodName);

                ServerCall.Listener<ReqT> listener = next.startCall(call, headers);

                return new SimpleForwardingServerCallListener<>(listener) {
                    @Override
                    public void onComplete() {
                        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                        log.debug("gRPC call completed: {} in {}ms", methodName, durationMs);
                        super.onComplete();
                    }

                    @Override
                    public void onCancel() {
                        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                        log.warn("gRPC call cancelled: {} after {}ms", methodName, durationMs);
                        super.onCancel();
                    }
                };
            }
        };
    }
}
