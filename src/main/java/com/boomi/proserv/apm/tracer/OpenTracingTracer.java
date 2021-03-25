package com.boomi.proserv.apm.tracer;

import com.boomi.connector.api.PayloadMetadata;
import com.boomi.proserv.apm.BoomiContext;

import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;

import java.util.Map;
import java.util.logging.Logger;

public class OpenTracingTracer extends Tracer {

    @Override
    public void start(Logger logger, BoomiContext context, String rtProcess, String document, Map<String, String> dynProps, Map<String, String> properties, PayloadMetadata metadata) {
        try {
            logger.info("Adding OpenTracing trace ...");
            Span span = getSpan();
            if(!isValid(span)) {
                io.opentracing.Tracer tracer = getTracer(logger);
                span = tracer.buildSpan(context.getProcessName()).withTag("service", context.getServiceName()).start();
                tracer.activateSpan(span);
            }
            setTraceId(logger, span.context().toTraceId(), metadata);
            span.setOperationName(context.getProcessName());
            span.setTag("boomi.executionID", context.getExecutionId());
            span.setTag("boomi.processName", context.getProcessName());
            span.setTag("boomi.processID", context.getProcessId());
            logger.info("OpenTracing trace added");
        } catch (Exception e) {
            logger.severe("OpenTracing trace not added " + e);
        }
        super.start(logger, context, rtProcess, document, dynProps, properties, metadata);
    }

    @Override
    public void stop(Logger logger, BoomiContext context, String rtProcess, String document, Map<String, String> dynProps, Map<String, String> properties, PayloadMetadata metadata) {
        try {
            logger.info("Closing OpenTracing trace ...");
            Span span = getSpan();
            if(isValid(span)) {
                setTraceId(logger, span.context().toTraceId(), metadata);
                span.finish();
                logger.info("OpenTracing trace closed");
            } else {
                logger.severe("OpenTracing trace not found");
            }
        } catch (Exception e) {
            logger.severe("OpenTracing trace not closed " + e);
        }
        super.stop(logger, context, rtProcess, document, dynProps, properties, metadata);
    }

    @Override
    public void error(Logger logger, BoomiContext context, String rtProcess, String document, Map<String, String> dynProps, Map<String, String> properties, PayloadMetadata metadata) {
        try {
            logger.info("Closing OpenTracing trace ...");
            Span span = getSpan();
            if(isValid(span)) {
                setTraceId(logger, span.context().toTraceId(), metadata);
                span.setTag(io.opentracing.tag.Tags.ERROR, true);
                span.finish();
                logger.info("OpenTracing trace closed with Error");
            } else {
                logger.severe("OpenTracing trace not found");
            }
        } catch (Exception e) {
            logger.severe("OpenTracing trace not closed " + e);
        }
        super.error(logger, context, rtProcess, document, dynProps, properties, metadata);
    }

    @Override
    protected void addTags(Map<String, String> dynProps) {
        Map<String, String> tags = getTags(dynProps);
        if(tags.size()>0) {
            Span span = getSpan();
            for (Map.Entry<String, String> entry : tags.entrySet()) {
                span.setTag(entry.getKey(), entry.getValue());
            }
        }
    }

    protected Span getSpan() {
        return GlobalTracer.get().activeSpan();
    }

    protected io.opentracing.Tracer getTracer(Logger logger) {
        logger.info("Getting OpenTracing tracer ...");
        return GlobalTracer.get();
    }

    protected boolean isValid(Span span) {
        return span != null;
    }
}
