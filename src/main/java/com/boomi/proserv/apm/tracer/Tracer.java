package com.boomi.proserv.apm.tracer;

import com.boomi.connector.api.PayloadMetadata;
import com.boomi.proserv.apm.BoomiContext;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public abstract class Tracer {

    private String traceId;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    //Executed before processing the documents
    public void start(Logger logger, BoomiContext context, PayloadMetadata metadata) {}
    public void stop(Logger logger, BoomiContext context, PayloadMetadata metadata) {}
    public void error(Logger logger, BoomiContext context, PayloadMetadata metadata) {}

    //Executed with the documents list, thus have access to document and document properties
    public void start(Logger logger, BoomiContext context, String rtProcess, String document, Map<String, String> dynProps, Map<String, String> properties, PayloadMetadata metadata) {
        addTags(dynProps);
    }
    public void stop(Logger logger, BoomiContext context, String rtProcess, String document, Map<String, String> dynProps, Map<String, String> properties, PayloadMetadata metadata) {
        addTags(dynProps);
    }
    public void error(Logger logger, BoomiContext context, String rtProcess, String document, Map<String, String> dynProps, Map<String, String> properties, PayloadMetadata metadata) {
        addTags(dynProps);
    }

    protected abstract void addTags(Map<String, String> dynProps);

    protected Map<String, String> getTags(Map<String, String> dynProps) {
        Map<String, String> kvMap = new HashMap<String, String>();
        if(dynProps!=null) {
            String kvs = dynProps.get("keyvalueTags");
            if (kvs != null || !"".equals(kvs)) {
                String[] kvArray = kvs.split(";");
                for (int i = 0; i < kvArray.length; i++) {
                    String kv = kvArray[i];
                    String[] kva = kv.split("=");
                    kvMap.put("boomi." + kva[0], kva[1]);
                }
            }
        }
        return kvMap;
    }
}
