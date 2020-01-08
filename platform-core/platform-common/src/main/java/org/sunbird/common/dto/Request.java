package org.sunbird.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.commons.collections4.MapUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * This contains data (value objects) to be passed to middleware command
 * 
 * @author rayulu
 * 
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Request implements Serializable {

    private static final long serialVersionUID = -2362783406031347676L;

    protected Map<String, Object> context;
    private String id;
    private String ver;
    private String ts;
    private RequestParams params;

	private Map<String, Object> request = new HashMap<String, Object>();

    private String operation;
    private String objectType;

    public Request() {
    	this.params = new RequestParams();
    }

    public Request(Map<String, Object> context, Map<String, Object> request, String operation, String objectType) {
        this.context = context;
        this.request = request;
        this.operation = operation;
        this.objectType = objectType;
    }
    
    public Request(Request request) {
    	this.params = request.getParams();
    	if (null == this.params)
    	    this.params = new RequestParams();
        this.context = new HashMap<>();
    	if (MapUtils.isNotEmpty(request.getContext()))
            this.context.putAll(request.getContext());
    }

    public Request(Request request, String objectType) {
        this(request);
        this.objectType = objectType;
    }

    public String getRequestId() {
        return null;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    /**
     * @return the requestValueObjects
     */
    public Map<String, Object> getRequest() {
        return request;
    }

    public void setRequest(Map<String, Object> request) {
        this.request = request;
    }

    public Object get(String key) {
        return request.get(key);
    }


	public void put(String key, Object vo) {
        request.put(key, vo);
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public void copyRequestValueObjects(Map<String, Object> map) {
        if (MapUtils.isNotEmpty(map)) {
            this.request.putAll(map);
        }
    }

    @Override
    public String toString() {
        return "Request [" + (context != null ? "context=" + context + ", " : "")
                + (request != null ? "requestValueObjects=" + request : "") + "]";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVer() {
        return ver;
    }

    public void setVer(String ver) {
        this.ver = ver;
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }

    public RequestParams getParams() {
        return params;
    }

    public void setParams(RequestParams params) {
        this.params = params;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }
}
