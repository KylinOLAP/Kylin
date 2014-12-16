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

import com.kylinolap.common.KylinConfig;
import com.kylinolap.cube.model.CubeDesc;
import com.kylinolap.cube.model.validation.IValidatorRule;
import com.kylinolap.cube.model.validation.ResultLevel;
import com.kylinolap.cube.model.validation.ValidateContext;

/**
 * Rule to validate: 1. The aggregationGroup size must be less than 20
 * 
 * @author jianliu
 * 
 */
public class AggregationGroupSizeRule implements IValidatorRule<CubeDesc> {

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.kylinolap.metadata.validation.IValidatorRule#validate(java.lang.Object
     * , com.kylinolap.metadata.validation.ValidateContext)
     */
    @Override
    public void validate(CubeDesc cube, ValidateContext context) {
        innerValidateMaxSize(cube, context);
    }

    /**
     * @param cube
     * @param context
     */
    private void innerValidateMaxSize(CubeDesc cube, ValidateContext context) {
        int maxSize = getMaxAgrGroupSize();
        String[][] groups = cube.getRowkey().getAggregationGroups();
        for (int i = 0; i < groups.length; i++) {
            String[] group = groups[i];
            if (group.length >= maxSize) {
                context.addResult(ResultLevel.ERROR, "Length of the number " + i + " aggregation group's length should be less than " + maxSize);
            }
        }
    }

    protected int getMaxAgrGroupSize() {
        String size = KylinConfig.getInstanceFromEnv().getProperty(KEY_MAX_AGR_GROUP_SIZE, String.valueOf(DEFAULT_MAX_AGR_GROUP_SIZE));
        return Integer.parseInt(size);
    }
}
