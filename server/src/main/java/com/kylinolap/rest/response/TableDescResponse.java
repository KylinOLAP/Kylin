/*
 * Copyright 2013-2014 eBay Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kylinolap.rest.response;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kylinolap.metadata.model.TableDesc;

/**
 * A response class to wrap TableDesc
 * 
 * @author jianliu
 * 
 */
public class TableDescResponse extends TableDesc {
    @JsonProperty("exd")
    Map<String, String> descExd = new HashMap<String, String>();
    @JsonProperty("cardinality")
    Map<String, Long> cardinality = new HashMap<String, Long>();

    /**
     * @return the cardinality
     */
    public Map<String, Long> getCardinality() {
        return cardinality;
    }

    /**
     * @param cardinality
     *            the cardinality to set
     */
    public void setCardinality(Map<String, Long> cardinality) {
        this.cardinality = cardinality;
    }

    /**
     * @return the descExd
     */
    public Map<String, String> getDescExd() {
        return descExd;
    }

    /**
     * @param descExd
     *            the descExd to set
     */
    public void setDescExd(Map<String, String> descExd) {
        this.descExd = descExd;
    }

    /**
     * @param table
     */
    public TableDescResponse(TableDesc table) {
        this.setColumns(table.getColumns());
        this.setDatabase(table.getDatabase());
        this.setName(table.getName());
        this.setUuid(table.getUuid());
    }

}
