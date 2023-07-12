package com.boomi.proserv.apm.tracer;

import com.boomi.connector.api.PayloadMetadata;
import com.boomi.proserv.apm.BoomiContext;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.util.GlobalTracer;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class OpenTracingTracer extends Tracer {

    protected static ThreadLocal<Scope> scope = new ThreadLocal<Scope>();

    @Override
    public void start(Logger logger, BoomiContext context, String rtProcess, String document, Map<String, String> dynProps, Map<String, String> properties, PayloadMetadata metadata) {
        try {
            logger.info("Looking for OpenTracing trace ...");
            Span span                               = getSpan();
            RealTimeProcessing realTimeProcessing   = RealTimeProcessing.getValue(rtProcess);
            io.opentracing.Tracer tracer            = getOpenTracingTracer(logger);

            if(RealTimeProcessing.w3c.equals(realTimeProcessing)) {
                logger.info("Continuing transaction using w3c header ...");
                String traceparent = getTraceparent(properties);
                if(traceparent!=null && !traceparent.equals("")) {
                    Format format;
                    switch (getComponentType()) {
                        case JMS:
                            format = Format.Builtin.TEXT_MAP;
                            break;
                        case HTTP:
                        default:
                            format = Format.Builtin.HTTP_HEADERS;
                            break;
                    }
                    Map<String, String> map = new HashMap<String, String>();
                    map.put(this.getTraceparentKey(), traceparent);
                    enrich(map, properties);
                    TextMapAdapter textMapAdapter = new TextMapAdapter(map);
                    SpanContext spanContext = tracer.extract(format, textMapAdapter);
                    io.opentracing.Tracer.SpanBuilder spanBuilder;
                    if (spanContext == null) {
                        if(!isValid(span)) {
                            logger.info("Building Span as new");
                            spanBuilder = tracer.buildSpan(context.getProcessName());
                        } else {
                            logger.info("Unable to continue transaction with header, using existing span");
                            spanBuilder = null;
                        }
                    } else {
                        logger.info("Building Span as child");
                        spanBuilder = tracer.buildSpan(context.getProcessName()).asChildOf(spanContext);
                    }
                    if(spanBuilder != null){
                        span = spanBuilder.start();
                        tracer.activateSpan(span);
                    }
                } else {
                    logger.warning("w3c header not found");
                }
            } else if(realTimeProcessing.equals(RealTimeProcessing.ignore) || !isValid(span)) { //FIXME: remove else
                if(buildNewSpanWhenIgnoreTag() || !isValid(span)) {
                    logger.info("Trace not found/ignored. Creating OpenTracing trace ...");
                    span = tracer.buildSpan(context.getProcessName()).ignoreActiveSpan().withTag("service", context.getServiceName()).start();
                    tracer.activateSpan(span);
                } else {
                    logger.info("Trace found/reused, setting tags ...");
                }
            } else {
                logger.info("Trace found, setting tags ...");
            }
            SpanContext spanContext = span.context();
            if(spanContext != null) {
                setTraceId (logger, spanContext.toTraceId(), metadata);
                setSpanId  (logger, spanContext.toSpanId(), metadata);
                setParentId(logger, getParentId(spanContext), metadata);
            } else {
                logger.warning("OpenTracing SpanContext is null");
            }
            span.setOperationName(context.getProcessName());
            span.setTag(getBoomiExecutionIdKey(), context.getExecutionId());
            span.setTag(getBoomiProcessNameKey(), context.getProcessName());
            span.setTag(getBoomiProcessIdKey(), context.getProcessId());
            logger.info("OpenTracing trace added");
        } catch (Exception e) {
            logger.severe("OpenTracing trace not added " + e);
        }
        super.start(logger, context, rtProcess, document, dynProps, properties, metadata);
    }

    protected String getTraceparentKey() {
        return TRACEPARENT;
    }

    protected boolean getDefaultValueBuildNewSpanWhenIgnoreTag() {
        return true;
    }

    protected void enrich(Map<String, String> map, Map<String, String> properties) {
    }

    @Override
    public void stop(Logger logger, BoomiContext context, String rtProcess, String document, Map<String, String> dynProps, Map<String, String> properties, PayloadMetadata metadata) {
        super.stop(logger, context, rtProcess, document, dynProps, properties, metadata);
        try {
            logger.info("Closing OpenTracing trace ...");
            Span span = getSpan();
            if(isValid(span)) {
                SpanContext spanContext = span.context();
                if(spanContext != null) {
                    setTraceId (logger, spanContext.toTraceId(), metadata);
                    setSpanId  (logger, spanContext.toSpanId(), metadata);
                    setParentId(logger, getParentId(spanContext), metadata);
                } else {
                    logger.warning("OpenTracing SpanContext is null");
                }
                span.finish();
                //closeScope();
                logger.info("OpenTracing trace closed");
            } else {
                logger.severe("OpenTracing trace not found");
            }
        } catch (Exception e) {
            logger.severe("OpenTracing trace not closed " + e);
        }
    }

    @Override
    public void error(Logger logger, BoomiContext context, String rtProcess, String document, Map<String, String> dynProps, Map<String, String> properties, PayloadMetadata metadata) {
        super.error(logger, context, rtProcess, document, dynProps, properties, metadata);
        try {
            logger.info("Closing OpenTracing trace with Error...");
            Span span = getSpan();
            if(isValid(span)) {
                SpanContext spanContext = span.context();
                if(spanContext != null) {
                    setTraceId (logger, spanContext.toTraceId(), metadata);
                    setSpanId  (logger, spanContext.toSpanId(), metadata);
                    setParentId(logger, getParentId(spanContext), metadata);
                } else {
                    logger.warning("OpenTracing SpanContext is null");
                }
                span.setTag(io.opentracing.tag.Tags.ERROR, true);
                span.setTag(getBoomiErrorMessageKey(), getErrorMessage());
                span.finish();
                //closeScope();
                logger.info("OpenTracing trace closed with Error");
            } else {
                logger.severe("OpenTracing trace not found");
            }
        } catch (Exception e) {
            logger.severe("OpenTracing trace not closed " + e);
        }
    }

    /**
     * Forcing the current Scope to close
     */
    protected void closeScope() {
        Scope realScope = scope.get();
        realScope.close();
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

    protected io.opentracing.Tracer getOpenTracingTracer(Logger logger) {
        logger.info("Getting OpenTracing tracer ...");
        return GlobalTracer.get();
    }

    protected boolean isValid(Span span) {
        return span != null;
    }
}
