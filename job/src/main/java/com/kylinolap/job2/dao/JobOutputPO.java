package com.kylinolap.job2.dao;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Maps;
import com.kylinolap.common.persistence.RootPersistentEntity;
import com.kylinolap.job2.execution.ExecutableState;

import java.util.List;
import java.util.Map;

/**
 * Created by qianzhou on 12/15/14.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
public class JobOutputPO extends RootPersistentEntity {

    @JsonProperty("content")
    private String content;

    @JsonProperty("status")
    private String status = ExecutableState.READY.toString();

    @JsonProperty("info")
    private Map<String, String> info = Maps.newHashMap();

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, String> getInfo() {
        return info;
    }

    public void setInfo(Map<String, String> info) {
        this.info = info;
    }
}