package org.sunbird.common.dto;

import org.sunbird.common.exception.ResponseCode;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 
 * @author rayulu
 * 
 */
public class Response implements Serializable {

    private static final long serialVersionUID = -3773253896160786443L;

    public Response() {

    }


    private String id;
    private String ver = "3.0";
    private String ts;
    private ResponseParams params;
    private ResponseCode responseCode = ResponseCode.OK;
    private Map<String, Object> result = new HashMap<String, Object>();

    public String getId() {
        return id;
    }

    public Response setId(String id) {
        this.id = id;
        return this;
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

    /**
     * @return the responseValueObjects
     */
    public Map<String, Object> getResult() {
        return result;
    }

    public Object get(String key) {
        return result.get(key);
    }

    public Response put(String key, Object vo) {
        result.put(key, vo);
        return this;
    }

    public void putAll(Map<String, Object> resultMap) {
        result.putAll(resultMap);
    }

    public ResponseParams getParams() {
        return params;
    }

    public void setParams(ResponseParams params) {
        this.params = params;
    }

    public void setResponseCode(ResponseCode code) {
        this.responseCode = code;
    }

    public ResponseCode getResponseCode() {
        return this.responseCode;
    }

}
