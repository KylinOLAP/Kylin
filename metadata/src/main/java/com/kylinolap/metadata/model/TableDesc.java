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
package com.kylinolap.metadata.model;

import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kylinolap.common.persistence.ResourceStore;
import com.kylinolap.common.persistence.RootPersistentEntity;
import com.kylinolap.common.util.StringSplitter;

/**
 * Table Metadata from Source. All name should be uppercase.
 * <p/>
 * User: lukhan Date: 10/15/13 Time: 9:06 AM To change this template use File |
 * Settings | File Templates.
 */
@JsonAutoDetect(fieldVisibility = Visibility.NONE, getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
public class TableDesc extends RootPersistentEntity {
    @JsonProperty("name")
    private String name;
    @JsonProperty("columns")
    private ColumnDesc[] columns;

    private DatabaseDesc database;

    public ColumnDesc findColumnByName(String name) {
        //ignore the db name and table name if exists
        int lastIndexOfDot = name.lastIndexOf(".");
        if (lastIndexOfDot >= 0) {
            name = name.substring(lastIndexOfDot + 1);
        }
        
        for (ColumnDesc c : columns) {
            // return first matched column
            if (name.equalsIgnoreCase(c.getName())) {
                return c;
            }
        }
        return null;
    }

    public String getResourcePath() {
        return ResourceStore.TABLE_RESOURCE_ROOT + "/" + getTableIdentity(this) + ".json";
    }

    public String getIdentity() {
        return TableDesc.getTableIdentity(this);
    }

    // ============================================================================

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        if (name != null) {
            String[] splits = StringSplitter.split(name, ".");
            if (splits.length == 2) {
                this.setDatabase(splits[0]);
                this.name = splits[1];
            } else if (splits.length == 1) {
                this.name = splits[0];
            }
        } else {
            this.name = name;
        }
    }

    @JsonProperty("database")
    public String getDatabase() {
        if (database == null) {
            return "DEFAULT";
        }
        return database.getName();
    }

    @JsonProperty("database")
    public void setDatabase(String database) {
        this.database = new DatabaseDesc();
        this.database.setName(database);
    }

    public ColumnDesc[] getColumns() {
        return columns;
    }

    public void setColumns(ColumnDesc[] columns) {
        this.columns = columns;
    }

    public int getMaxColumnIndex() {
        int max = -1;
        for (ColumnDesc col : columns) {
            int idx = col.getZeroBasedIndex();
            max = Math.max(max, idx);
        }
        return max;
    }

    public int getColumnCount() {
        return getMaxColumnIndex() + 1;
    }

    public void init() {
        if (name != null)
            name = name.toUpperCase();

        if (getDatabase() != null)
            setDatabase(getDatabase().toUpperCase());

        if (columns != null) {
            Arrays.sort(columns, new Comparator<ColumnDesc>() {
                @Override
                public int compare(ColumnDesc col1, ColumnDesc col2) {
                    Integer id1 = Integer.parseInt(col1.getId());
                    Integer id2 = Integer.parseInt(col2.getId());
                    return id1.compareTo(id2);
                }
            });

            for (ColumnDesc col : columns) {
                col.init(this);
            }
        }
    }

    @Override
    public String toString() {
        return "TableDesc [database=" + getDatabase() + " name=" + name + "]";
    }

    private static final Pattern TABLE_IDENTITY_PATTERN = Pattern.compile("^\\w+\\.\\w+$");

    public static String getTableIdentity(TableDesc tableDesc) {
        return String.format("%s.%s", tableDesc.getDatabase(), tableDesc.getName()).toUpperCase();
    }

    public static String getTableIdentity(String tableName) {
        if (!tableName.contains(".")) {
            tableName = "DEFAULT." + tableName;
        }
        if (!TABLE_IDENTITY_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("invalid tableName:" + tableName);
        }
        return tableName.toUpperCase();
    }

}
