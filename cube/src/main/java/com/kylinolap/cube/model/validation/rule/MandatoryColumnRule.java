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

package com.kylinolap.cube.model.validation.rule;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;

import com.kylinolap.cube.model.CubeDesc;
import com.kylinolap.cube.model.RowKeyColDesc;
import com.kylinolap.cube.model.validation.IValidatorRule;
import com.kylinolap.cube.model.validation.ResultLevel;
import com.kylinolap.cube.model.validation.ValidateContext;

/**
 * Validate that mandatory column must NOT appear in aggregation group.
 * 
 * @author jianliu
 * 
 */
public class MandatoryColumnRule implements IValidatorRule<CubeDesc> {

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.kylinolap.metadata.validation.IValidatorRule#validate(java.lang.Object
     * , com.kylinolap.metadata.validation.ValidateContext)
     */
    @Override
    public void validate(CubeDesc cube, ValidateContext context) {
        Set<String> mands = new HashSet<String>();
        RowKeyColDesc[] cols = cube.getRowkey().getRowKeyColumns();
        if (cols == null || cols.length == 0) {
            return;
        }
        for (int i = 0; i < cols.length; i++) {
            RowKeyColDesc rowKeyColDesc = cols[i];
            if (rowKeyColDesc.isMandatory()) {
                mands.add(rowKeyColDesc.getColumn());
            }
        }
        if (mands.isEmpty()) {
            return;
        }
        String[][] groups = cube.getRowkey().getAggregationGroups();
        for (int i = 0; i < groups.length; i++) {
            String[] group = groups[i];
            for (int j = 0; j < group.length; j++) {
                String col = group[j];
                if (mands.contains(col)) {
                    context.addResult(ResultLevel.ERROR, "mandatory column " + col + " must not be in aggregation group [" + ArrayUtils.toString(group) + "]");
                }
            }
        }

    }

}
