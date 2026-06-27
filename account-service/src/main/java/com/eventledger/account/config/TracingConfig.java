package com.eventledger.account.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Configuration
public class TracingConfig {

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
}
