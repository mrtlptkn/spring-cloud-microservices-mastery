package com.mertalptekin.orderservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TracingInterceptor implements RequestInterceptor {

    private final Tracer tracer;

    public TracingInterceptor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void apply(RequestTemplate requestTemplate) {

        log.info("Adding tracing headers to request: {}", requestTemplate.url());

        var currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            requestTemplate.header("b3", currentSpan.context().traceId() + "-" + currentSpan.context().spanId() + "-1");
        }
    }
}