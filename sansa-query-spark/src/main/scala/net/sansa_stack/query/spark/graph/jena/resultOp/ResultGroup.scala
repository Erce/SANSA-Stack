package net.sansa_stack.query.spark.graph.jena.resultOp

import net.sansa_stack.query.spark.graph.jena.model.{IntermediateResult, SparkExecutionModel}
import net.sansa_stack.query.spark.graph.jena.util.Result
import org.apache.jena.graph.{Node, NodeFactory}
import org.apache.jena.sparql.algebra.Op
import org.apache.jena.sparql.algebra.op.OpGroup
import org.apache.jena.sparql.expr.ExprAggregator
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD

import scala.collection.JavaConversions._

/**
  * Class that execute SPARQL GROUP BY operation.
  * @param op Group By operator
  */
class ResultGroup(op: OpGroup) extends ResultOp {

  private val tag = "GROUP BY"
  private val id = op.hashCode()

  override def execute(input: Array[Map[Node, Node]]): Array[Map[Node, Node]] = {
    val vars = op.getGroupVars.getVars.toList   // e.g. List(?user)
    val aggregates = op.getAggregators.toList   // e.g. List((AGG ?.0 AVG(?age)), (AGG ?.1 MAX(?age)), (AGG ?.2 MIN(?age)))
    var intermediate = input
    vars.length match {
      case 1 =>
        aggregates.foreach{ aggr =>
          input.groupBy(map => map(vars.head)).foreach{ case(node, array) =>
            intermediate = intermediate.map{ mapping =>
              if(mapping(vars.head).equals(node)){
                val c = mapping ++ ResultGroup.aggregateOp(array, aggr)
                c
              } else{ mapping }
            }
          }
        }
    }
    val output = intermediate
    output
  }

  override def execute(): Unit = {
    val vars = op.getGroupVars.getVars.toList.map(v=>v.asNode())    // List of variables, e.g. List(?user)
    val aggregates = op.getAggregators.toList
    val oldResult = IntermediateResult.getResult(op.getSubOp.hashCode()).cache()
    var newResult: RDD[Result[Node]] = null
    /*aggregates.foreach(aggr => group.foreach{ case(_, iter) =>
      aggregateOp(iter, aggr)
    })*/

    newResult = SparkExecutionModel.group(oldResult, vars, aggregates)
    IntermediateResult.putResult(id, newResult)
    IntermediateResult.removeResult(op.getSubOp.hashCode())
  }

  override def getTag: String = { tag }

  override def getId: Int = { id }

  def getOp: Op = { op }

}

object ResultGroup {

  def aggregateOp(input: Array[Map[Node, Node]], aggr: ExprAggregator): Map[Node, Node] = {
    val key = aggr.getAggregator.getExprList.head.getExprVar.getAsNode    // e.g. ?age
    val seq = input.map(_(key).getLiteralValue.toString.toDouble)
    val result = seq.aggregate((0.0, 0))(
      (acc, value) => (acc._1 + value, acc._2 + 1),
      (acc1, acc2) => (acc1._1 + acc2._1, acc1._2 + acc2._2))
    if(aggr.getAggregator.key().contains("sum")) {                          // e.g. (sum, ?age)
      Map(aggr.getVar.asNode() -> NodeFactory.createLiteral(result._1.toString))
    }
    else if(aggr.getAggregator.key().contains("avg")) {                    // e.g. (avg, ?age)
      Map(aggr.getVar.asNode() -> NodeFactory.createLiteral((result._1 / result._2).toString))
    }
    else if(aggr.getAggregator.key().contains("count")) {                 // e.g. (count, ?age)
      Map(aggr.getVar.asNode() -> NodeFactory.createLiteral(result._2.toString))
    }
    else if(aggr.getAggregator.key().contains("max")) {                   // e.g. (max, ?age)
      Map(aggr.getVar.asNode() -> NodeFactory.createLiteral(seq.max.toString))
    }
    else if(aggr.getAggregator.key().contains("min")) {                   // e.g. (min, ?age)
      Map(aggr.getVar.asNode() -> NodeFactory.createLiteral(seq.min.toString))
    }
    else {
      Map(aggr.getVar.asNode() -> NodeFactory.createBlankNode())
    }
  }

  def aggregateOp(input: Iterable[Result[Node]], aggr: Broadcast[ExprAggregator]): Unit ={
    val key = aggr.value.getAggregator.getExprList.head.getExprVar.getAsNode    // e.g. ?age
    val seq = input.map(_.getValue(key).getLiteralValue.toString.toDouble)
    seq.foreach(println(_))
  }

}
