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

package com.kylinolap.cube.project;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.kylinolap.cube.CubeInstance;

/**
 * @author xduo
 * 
 */
public class ProjectTable {

    private final String name;

    private Multiset<String> columns = HashMultiset.create();

    private Multiset<CubeInstance> cubes = HashMultiset.create();

    /**
     * @param name
     */
    public ProjectTable(String name) {
        super();
        this.name = name.toUpperCase();
    }

    public String getName() {
        return name;
    }

    public Multiset<String> getColumns() {
        return columns;
    }

    public void setColumns(Multiset<String> columns) {
        this.columns = columns;
    }

    public Multiset<CubeInstance> getCubes() {
        return cubes;
    }

    public void setCubes(Multiset<CubeInstance> cubes) {
        this.cubes = cubes;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ProjectTable other = (ProjectTable) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equalsIgnoreCase(other.name))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "ProjectTable [name=" + name + ", columns=" + columns + "]";
    }

}
