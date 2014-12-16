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
package com.kylinolap.query.optrule;

import org.eigenbase.rel.ProjectRel;
import org.eigenbase.relopt.RelOptRule;
import org.eigenbase.relopt.RelOptRuleCall;
import org.eigenbase.relopt.RelTraitSet;

import com.kylinolap.query.relnode.OLAPProjectRel;
import com.kylinolap.query.relnode.OLAPRel;

/**
 * 
 * @author xjiang
 * 
 */
public class OLAPProjectRule extends RelOptRule {

    public static final RelOptRule INSTANCE = new OLAPProjectRule();

    public OLAPProjectRule() {
        super(operand(ProjectRel.class, any()));
    }

    @Override
    public void onMatch(RelOptRuleCall call) {
        ProjectRel project = call.rel(0);

        RelTraitSet traitSet = project.getTraitSet().replace(OLAPRel.CONVENTION);
        OLAPProjectRel olapProj = new OLAPProjectRel(project.getCluster(), traitSet, convert(project.getChild(), traitSet), project.getProjects(), project.getRowType(), project.getFlags());
        call.transformTo(olapProj);
    }

}
