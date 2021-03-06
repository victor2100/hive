package org.apache.hadoop.hive.ql.cs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.hadoop.hive.ql.exec.ColumnInfo;
import org.apache.hadoop.hive.ql.exec.Operator;
import org.apache.hadoop.hive.ql.exec.RowSchema;
import org.apache.hadoop.hive.ql.parse.OpParseContext;
import org.apache.hadoop.hive.ql.plan.AggregationDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeDesc;
import org.apache.hadoop.hive.ql.plan.GroupByDesc;
import org.apache.hadoop.hive.ql.plan.OperatorDesc;
import org.apache.hadoop.hive.ql.exec.GroupByOperator;

/**
 * Group By Operator will probably generate new columns, if there are "having" after it
 * Group By column will use column name such as "key" "value" constructed from the previous operators,
 * without considering the table name
 * 
 * Having is represented as a filter
 * 
 * @author victor
 *
 */
public class SGroupByOperator extends SOperator {

	static HashSet<String> specialAggrs = new HashSet<String>();

	static
	{
		specialAggrs.add("min");
		specialAggrs.add("max");
		specialAggrs.add("percentile");
		specialAggrs.add("percentile_approx");
	}

	ArrayList<SAbstractColumn> keys;
	ArrayList<SAggregate> aggregators;

	boolean isDeduplication = false;

	public SGroupByOperator(GroupByOperator op, List<SOperator> parents, LinkedHashMap<Operator<? extends OperatorDesc>, OpParseContext> ctx) {
		super(op, parents, ctx);

		keys = new ArrayList<SAbstractColumn>(columns);
		columns.addAll(aggregators);
		System.out.println("AGGREGATORS " + aggregators + " COLUMNS: " + columns);

		isDeduplication = op.getConf().getAggregators().isEmpty();
	}

	@Override
	protected void buildColumns() {
		super.buildColumns();

		aggregators = new ArrayList<SAggregate>();

		ArrayList<ColumnInfo> colInfos = op.getSchema().getSignature();
		ArrayList<AggregationDesc> descs = ((GroupByDesc)op.getConf()).getAggregators();
		for (int i = 0; i <descs.size() ; i++) {
			AggregationDesc aggr = descs.get(i);
			ColumnInfo info = colInfos.get(i + columns.size());
			aggregators.add(new SAggregate(info.getInternalName(), info.getTabAlias(), this, aggr));
		}
		
	}

	public SAggregate getAggregateAt(int i) {
		return aggregators.get(i-keys.size());
	}

	@Override
	public String prettyString() {
		String s = super.prettyString() + " Group By: ";
		if (((GroupByDesc)op.getConf()).getAggregators() != null) {
			s += "Aggregators: ";
			for (AggregationDesc desc: ((GroupByDesc)op.getConf()).getAggregators()) {
				s += desc.getExprString() + " ";
			}
		}

		if (((GroupByDesc)op.getConf()).getKeys() != null) {
			s += "Keys: ";
			for (ExprNodeDesc key: ((GroupByDesc)op.getConf()).getKeys()) {
				s += key.getExprString() + " ";
			}
		}

		return s;
	}

	@Override
	public boolean hasNestedAggregates() {
		if (super.hasNestedAggregates()) {
			return true;
		}

		for (SAbstractColumn k : keys) {
			if (k.isGeneratedByAggregate()) {
				return true;
			}
		}

		for (SAggregate aggr : aggregators) {
			for (SAbstractColumn param : aggr.getParams()) {
				if (param.isGeneratedByAggregate()) {
					return true;
				}
			}
		}

		return false;
	}
	
	@Override
	public boolean hasCorrelatedAggregates() {
		if (super.hasCorrelatedAggregates()) {
			return true;
		}

		
		
		for (SAggregate aggr : aggregators) {
			System.out.println("KKK Here" + aggr.getParams());
			for (SAbstractColumn param : aggr.getParams()) {
				if (param.isCorrelatedWithAggregate()) {
					return true;
				}
			}
		}
		
		return false;
	}

	@Override
	public boolean hasSpecialAggregates() {
		for (SAggregate aggr : aggregators) {
			if (specialAggrs.contains(aggr.desc.getGenericUDAFName().toLowerCase())) {
				return true;
			}
		}

		return false;
	}
	
	@Override
	public boolean isComplex(boolean hasComplexOnTheWay) {
		if (hasComplexOnTheWay) {
			return true;
		}
		
		System.out.println("YYY HERE!!!");
		return super.isComplex(true);
	}
	
	@Override
	public boolean hasAggregates() {
		System.out.println("***DEDUP " + isDeduplication);
		if (isDeduplication) {
			return super.hasAggregates();
		} else {
			return true;
		}
	}
	
	@Override
	public boolean hasDeduplication() {
		return true;
	}
	
//	@Override
//	public boolean isEligible(HashSet<FD> rules, HashSet<SBaseColumn> bases) {
//		// TODO: test
//		
//		if (isDeduplication) {
//			bases.clear();
//		}
//		
//		return true;
//	}
}