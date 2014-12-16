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
package com.kylinolap.cube.model.validation;

import com.kylinolap.cube.model.CubeDesc;
import com.kylinolap.cube.model.validation.ValidateContext.Result;
import com.kylinolap.cube.model.validation.rule.AggregationGroupSizeRule;
import com.kylinolap.cube.model.validation.rule.FunctionRule;
import com.kylinolap.cube.model.validation.rule.MandatoryColumnRule;
import com.kylinolap.cube.model.validation.rule.RowKeyAttrRule;

/**
 * For cube metadata validator
 * 
 * @author jianliu
 * 
 */
public class CubeMetadataValidator {
    @SuppressWarnings("unchecked")
    private IValidatorRule<CubeDesc>[] rules = new IValidatorRule[] { new FunctionRule(), new AggregationGroupSizeRule(), new MandatoryColumnRule(), new RowKeyAttrRule() };

    public ValidateContext validate(CubeDesc cube) {
        return validate(cube, false);
    }

    /**
     * @param cubeDesc
     * @param inject
     *            inject error into cube desc
     * @return
     */
    public ValidateContext validate(CubeDesc cube, boolean inject) {
        ValidateContext context = new ValidateContext();
        for (int i = 0; i < rules.length; i++) {
            IValidatorRule<CubeDesc> rule = rules[i];
            rule.validate(cube, context);
        }
        if (inject) {
            injectResult(cube, context);
        }
        return context;
    }

    /**
     * 
     * Inject errors info into cubeDesc
     * 
     * @param cubeDesc
     * @param context
     */
    public void injectResult(CubeDesc cubeDesc, ValidateContext context) {
        Result[] results = context.getResults();
        for (int i = 0; i < results.length; i++) {
            Result result = results[i];
            cubeDesc.addError(result.getLevel() + " : " + result.getMessage(), true);
        }

    }

}
