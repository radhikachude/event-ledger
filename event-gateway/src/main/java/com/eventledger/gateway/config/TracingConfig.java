package com.eventledger.gateway.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.UUID;

@Configuration
public class TracingConfig {

    private static final Logger log = LoggerFactory.getLogger(TracingConfig.class);
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_KEY = "traceId";

    @Component
    public static class TraceFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            
            if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
                String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
                if (traceId == null || traceId.isBlank()) {
                    traceId = UUID.randomUUID().toString().replace("-", "");
                }
                
                MDC.put(MDC_TRACE_KEY, traceId);
                httpResponse.setHeader(TRACE_ID_HEADER, traceId);
            }

            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove(MDC_TRACE_KEY);
            }
        }
    }

    @Bean
    public RestClientCustomizer restClientCustomizer() {
        return restClientBuilder -> {
            log.info("Registering trace propagation interceptor on RestClient.Builder");
            ClientHttpRequestInterceptor tracePropagationInterceptor = (request, body, execution) -> {
                String traceId = MDC.get(MDC_TRACE_KEY);
                if (traceId != null) {
                    request.getHeaders().add(TRACE_ID_HEADER, traceId);
                }
                return execution.execute(request, body);
            };
            restClientBuilder.requestInterceptor(tracePropagationInterceptor);
        };
    }
}
