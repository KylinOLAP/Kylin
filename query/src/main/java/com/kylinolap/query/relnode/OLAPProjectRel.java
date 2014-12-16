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
package com.kylinolap.query.relnode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.hydromatic.optiq.rules.java.EnumerableConvention;
import net.hydromatic.optiq.rules.java.EnumerableRel;
import net.hydromatic.optiq.rules.java.EnumerableRelImplementor;
import net.hydromatic.optiq.rules.java.JavaRules.EnumerableCalcRel;

import org.eigenbase.rel.ProjectRelBase;
import org.eigenbase.rel.RelCollation;
import org.eigenbase.rel.RelNode;
import org.eigenbase.relopt.RelOptCluster;
import org.eigenbase.relopt.RelOptCost;
import org.eigenbase.relopt.RelOptPlanner;
import org.eigenbase.relopt.RelTrait;
import org.eigenbase.relopt.RelTraitSet;
import org.eigenbase.reltype.RelDataType;
import org.eigenbase.reltype.RelDataTypeFactory.FieldInfoBuilder;
import org.eigenbase.reltype.RelDataTypeField;
import org.eigenbase.reltype.RelDataTypeFieldImpl;
import org.eigenbase.rex.RexCall;
import org.eigenbase.rex.RexInputRef;
import org.eigenbase.rex.RexLiteral;
import org.eigenbase.rex.RexNode;
import org.eigenbase.rex.RexProgram;
import org.eigenbase.sql.SqlOperator;
import org.eigenbase.sql.fun.SqlCaseOperator;
import org.eigenbase.sql.fun.SqlStdOperatorTable;
import org.eigenbase.sql.validate.SqlUserDefinedFunction;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.kylinolap.metadata.model.realization.TblColRef;
import com.kylinolap.metadata.model.realization.TblColRef.InnerDataTypeEnum;

/**
 * 
 * @author xjiang
 * 
 */
public class OLAPProjectRel extends ProjectRelBase implements OLAPRel, EnumerableRel {

    private OLAPContext context;
    private List<RexNode> rewriteProjects;
    private boolean rewriting;
    private ColumnRowType columnRowType;
    private boolean hasJoin;
    private boolean afterJoin;
    private boolean afterAggregate;

    public OLAPProjectRel(RelOptCluster cluster, RelTraitSet traitSet, RelNode child, List<RexNode> exps, RelDataType rowType, int flags) {
        super(cluster, traitSet, child, exps, rowType, flags);
        Preconditions.checkArgument(getConvention() == OLAPRel.CONVENTION);
        Preconditions.checkArgument(child.getConvention() == OLAPRel.CONVENTION);
        this.rewriteProjects = exps;
        this.hasJoin = false;
        this.afterJoin = false;
        this.rowType = getRowType();
    }

    @Override
    public List<RexNode> getChildExps() {
        return rewriteProjects;
    }

    @Override
    public List<RexNode> getProjects() {
        return rewriteProjects;
    }

    @Override
    public RelOptCost computeSelfCost(RelOptPlanner planner) {
        return super.computeSelfCost(planner).multiplyBy(.05);
    }

    @Override
    public ProjectRelBase copy(RelTraitSet traitSet, RelNode child, List<RexNode> exps, RelDataType rowType) {
        return new OLAPProjectRel(getCluster(), traitSet, child, exps, rowType, this.flags);
    }

    @Override
    public void implementOLAP(OLAPImplementor implementor) {
        implementor.visitChild(getChild(), this);

        this.context = implementor.getContext();
        this.hasJoin = context.hasJoin;
        this.afterJoin = context.afterJoin;
        this.afterAggregate = context.afterAggregate;

        this.columnRowType = buildColumnRowType();
    }

    private ColumnRowType buildColumnRowType() {
        List<TblColRef> columns = new ArrayList<TblColRef>();
        List<Set<TblColRef>> sourceColumns = new ArrayList<Set<TblColRef>>();
        OLAPRel olapChild = (OLAPRel) getChild();
        ColumnRowType inputColumnRowType = olapChild.getColumnRowType();
        for (int i = 0; i < this.rewriteProjects.size(); i++) {
            RexNode rex = this.rewriteProjects.get(i);
            RelDataTypeField columnField = this.rowType.getFieldList().get(i);
            String fieldName = columnField.getName();
            Set<TblColRef> sourceCollector = new HashSet<TblColRef>();
            TblColRef column = translateRexNode(rex, inputColumnRowType, fieldName, sourceCollector);
            columns.add(column);
            sourceColumns.add(sourceCollector);
        }
        return new ColumnRowType(columns, sourceColumns);
    }

    private TblColRef translateRexNode(RexNode rexNode, ColumnRowType inputColumnRowType, String fieldName, Set<TblColRef> sourceCollector) {
        TblColRef column = null;
        if (rexNode instanceof RexInputRef) {
            RexInputRef inputRef = (RexInputRef) rexNode;
            column = translateRexInputRef(inputRef, inputColumnRowType, fieldName, sourceCollector);
        } else if (rexNode instanceof RexLiteral) {
            RexLiteral literal = (RexLiteral) rexNode;
            column = translateRexLiteral(literal);
        } else if (rexNode instanceof RexCall) {
            RexCall call = (RexCall) rexNode;
            column = translateRexCall(call, inputColumnRowType, fieldName, sourceCollector);
        } else {
            throw new IllegalStateException("Unsupport RexNode " + rexNode);
        }
        return column;
    }

    private TblColRef translateRexInputRef(RexInputRef inputRef, ColumnRowType inputColumnRowType, String fieldName, Set<TblColRef> sourceCollector) {
        int index = inputRef.getIndex();
        // check it for rewrite count
        if (index < inputColumnRowType.size()) {
            TblColRef column = inputColumnRowType.getColumnByIndex(index);
            if (!column.isInnerColumn() && !this.rewriting && !this.afterAggregate) {
                context.allColumns.add(column);
                sourceCollector.add(column);
            }
            return column;
        } else {
            throw new IllegalStateException("Can't find " + inputRef + " from child columnrowtype " + inputColumnRowType + " with fieldname " + fieldName);
        }
    }

    private TblColRef translateRexLiteral(RexLiteral literal) {
        if (RexLiteral.isNullLiteral(literal)) {
            return TblColRef.newInnerColumn("null", InnerDataTypeEnum.LITERAL);
        } else {
            return TblColRef.newInnerColumn(literal.getValue().toString(), InnerDataTypeEnum.LITERAL);
        }

    }

    private TblColRef translateRexCall(RexCall call, ColumnRowType inputColumnRowType, String fieldName, Set<TblColRef> sourceCollector) {
        SqlOperator operator = call.getOperator();
        if (operator == SqlStdOperatorTable.EXTRACT_DATE) {
            List<RexNode> extractDateOps = call.getOperands();
            RexCall reinterpret = (RexCall) extractDateOps.get(1);
            List<RexNode> reinterpretOps = reinterpret.getOperands();
            RexInputRef inputRef = (RexInputRef) reinterpretOps.get(0);
            return translateRexInputRef(inputRef, inputColumnRowType, fieldName, sourceCollector);
        } else if (operator instanceof SqlUserDefinedFunction) {
            if (operator.getName().equals("QUARTER")) {
                List<RexNode> quaterOps = call.getOperands();
                RexInputRef inputRef = (RexInputRef) quaterOps.get(0);
                return translateRexInputRef(inputRef, inputColumnRowType, fieldName, sourceCollector);
            }
        } else if (operator instanceof SqlCaseOperator) {
            for (RexNode operand : call.getOperands()) {
                if (operand instanceof RexInputRef) {
                    RexInputRef inputRef = (RexInputRef) operand;
                    return translateRexInputRef(inputRef, inputColumnRowType, fieldName, sourceCollector);
                }
            }
        }

        for (RexNode operand : call.getOperands()) {
            translateRexNode(operand, inputColumnRowType, fieldName, sourceCollector);
        }
        return TblColRef.newInnerColumn(fieldName, InnerDataTypeEnum.LITERAL);
    }

    @Override
    public Result implement(EnumerableRelImplementor implementor, Prefer pref) {
        EnumerableCalcRel enumCalcRel;

        RelNode child = getChild();
        if (child instanceof OLAPFilterRel) {
            // merge project & filter
            OLAPFilterRel filter = (OLAPFilterRel) getChild();
            RexProgram program = RexProgram.create(filter.getChild().getRowType(), this.rewriteProjects, filter.getCondition(), this.rowType, getCluster().getRexBuilder());

            enumCalcRel = new EnumerableCalcRel(getCluster(), getCluster().traitSetOf(EnumerableConvention.INSTANCE), filter.getChild(), this.rowType, program, ImmutableList.<RelCollation> of());
        } else {
            // keep project for tablescan
            RexProgram program = RexProgram.create(child.getRowType(), this.rewriteProjects, null, this.rowType, getCluster().getRexBuilder());

            enumCalcRel = new EnumerableCalcRel(getCluster(), getCluster().traitSetOf(EnumerableConvention.INSTANCE), child, this.rowType, program, ImmutableList.<RelCollation> of());
        }

        return enumCalcRel.implement(implementor, pref);
    }

    @Override
    public ColumnRowType getColumnRowType() {
        return columnRowType;
    }

    @Override
    public void implementRewrite(RewriteImplementor implementor) {
        implementor.visitChild(this, getChild());

        this.rewriting = true;

        // project before join or is just after OLAPToEnumerableConverter
        if (!RewriteImplementor.needRewrite(this.context) || (this.hasJoin && !this.afterJoin) || this.afterAggregate) {
            this.columnRowType = this.buildColumnRowType();
            return;
        }

        // find missed rewrite fields
        int paramIndex = this.rowType.getFieldList().size();
        List<RelDataTypeField> newFieldList = new LinkedList<RelDataTypeField>();
        List<RexNode> newExpList = new LinkedList<RexNode>();
        ColumnRowType inputColumnRowType = ((OLAPRel) getChild()).getColumnRowType();

        for (Map.Entry<String, RelDataType> rewriteField : this.context.rewriteFields.entrySet()) {
            String rewriteFieldName = rewriteField.getKey();
            int rowIndex = this.columnRowType.getIndexByName(rewriteFieldName);
            if (rowIndex < 0) {
                int inputIndex = inputColumnRowType.getIndexByName(rewriteFieldName);
                if (inputIndex >= 0) {
                    // new field
                    RelDataType fieldType = rewriteField.getValue();
                    RelDataTypeField newField = new RelDataTypeFieldImpl(rewriteFieldName, paramIndex++, fieldType);
                    newFieldList.add(newField);
                    // new project
                    RelDataTypeField inputField = getChild().getRowType().getFieldList().get(inputIndex);
                    RexInputRef newFieldRef = new RexInputRef(inputField.getIndex(), inputField.getType());
                    newExpList.add(newFieldRef);
                }
            }
        }

        if (!newFieldList.isEmpty()) {
            // rebuild projects
            List<RexNode> newProjects = new ArrayList<RexNode>(this.rewriteProjects);
            newProjects.addAll(newExpList);
            this.rewriteProjects = newProjects;

            // rebuild row type
            FieldInfoBuilder fieldInfo = getCluster().getTypeFactory().builder();
            fieldInfo.addAll(this.rowType.getFieldList());
            fieldInfo.addAll(newFieldList);
            this.rowType = getCluster().getTypeFactory().createStructType(fieldInfo);
        }

        // rebuild columns
        this.columnRowType = this.buildColumnRowType();

        this.rewriting = false;
    }

    @Override
    public OLAPContext getContext() {
        return context;
    }

    @Override
    public boolean hasSubQuery() {
        OLAPRel olapChild = (OLAPRel) getChild();
        return olapChild.hasSubQuery();
    }

    @Override
    public RelTraitSet replaceTraitSet(RelTrait trait) {
        RelTraitSet oldTraitSet = this.traitSet;
        this.traitSet = this.traitSet.replace(trait);
        return oldTraitSet;
    }
}
