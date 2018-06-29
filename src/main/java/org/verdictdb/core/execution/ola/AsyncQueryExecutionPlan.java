package org.verdictdb.core.execution.ola;

import java.util.ArrayList;
import java.util.List;

import org.verdictdb.core.execution.AggExecutionNode;
import org.verdictdb.core.execution.QueryExecutionNode;
import org.verdictdb.core.execution.QueryExecutionPlan;
import org.verdictdb.core.rewriter.ScrambleMeta;
import org.verdictdb.exception.VerdictDBException;
import org.verdictdb.exception.VerdictDBTypeException;

public class AsyncQueryExecutionPlan extends QueryExecutionPlan {

  private AsyncQueryExecutionPlan(String scratchpadSchemaName, ScrambleMeta scrambleMeta)
      throws VerdictDBException {
    super(scratchpadSchemaName, scrambleMeta);
  }

  public static AsyncQueryExecutionPlan create(QueryExecutionPlan plan) throws VerdictDBException {
    if (plan instanceof AsyncQueryExecutionPlan) {
      System.err.println("It is already an asyncronous plan.");
      throw new VerdictDBTypeException(plan);
    }

    AsyncQueryExecutionPlan asyncPlan = new AsyncQueryExecutionPlan(plan.getScratchpadSchemaName(), plan.getScrambleMeta());
    QueryExecutionNode newRoot = makeAsyncronousAggIfAvailable(plan.getRootNode());
    asyncPlan.setRootNode(newRoot);
    return asyncPlan;
  }

  /**
   *
   * @param root The root execution node of ALL nodes (i.e., not just the top agg node)
   * @return
   * @throws VerdictDBException
   */
  static QueryExecutionNode makeAsyncronousAggIfAvailable(QueryExecutionNode root) throws VerdictDBException {
    List<AggExecutionNodeBlock> aggBlocks = identifyTopAggBlocks(root);

    // converted nodes should be used in place of the original nodes.
    for (int i = 0; i < aggBlocks.size(); i++) {
      AggExecutionNodeBlock nodeBlock = aggBlocks.get(i);
      QueryExecutionNode oldNode = nodeBlock.getBlockRootNode();
      QueryExecutionNode newNode = nodeBlock.convertToProgressiveAgg();

      List<QueryExecutionNode> parents = oldNode.getParents();
      for (QueryExecutionNode parent : parents) {
        List<QueryExecutionNode> parentDependants = parent.getDependents();
        int idx = parentDependants.indexOf(oldNode);
        parentDependants.remove(idx);
        parentDependants.add(idx, newNode);
      }
    }

    return root;
  }

  //identify nodes that are (1) aggregates and (2) are not descendants of any other aggregates.
  static List<AggExecutionNodeBlock> identifyTopAggBlocks(QueryExecutionNode root) {
    List<AggExecutionNodeBlock> aggblocks = new ArrayList<>();

    if (root instanceof AggExecutionNode) {
      AggExecutionNodeBlock block = new AggExecutionNodeBlock(root.getPlan(), root);
      aggblocks.add(block);
      return aggblocks;
    }
    for (QueryExecutionNode dep : root.getDependents()) {
      List<AggExecutionNodeBlock> depAggBlocks = identifyTopAggBlocks(dep);
      aggblocks.addAll(depAggBlocks);
    }

    return aggblocks;
  }

}
