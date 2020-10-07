package net.sansa_stack.query.spark.graph.jena.resultOp

import scala.collection.JavaConverters._

import net.sansa_stack.query.spark.graph.jena.model.{ IntermediateResult, SparkExecutionModel }
import net.sansa_stack.query.spark.graph.jena.util.Result
import org.apache.jena.graph.Node
import org.apache.jena.sparql.algebra.Op
import org.apache.jena.sparql.algebra.op.OpProject
import org.apache.jena.sparql.core.Var
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

/**
 * Class that execute the operations of projecting the required variables.
 * @param op Project operator.
 */
class ResultProject(val op: OpProject) extends ResultOp {

  private val tag = "SELECT"
  private val id = op.hashCode()
  private val vars = op.getVars.asScala.toList

  override def execute(input: Array[Map[Node, Node]]): Array[Map[Node, Node]] = {
    input.map { mapping =>
      mapping.filter { case (k, _) => vars.contains(k) }
    }
  }

  override def execute(): Unit = {
    val varSet = vars.map(v => v.asNode()).toSet
    val oldResult = IntermediateResult.getResult(op.getSubOp.hashCode()).cache()
    val newResult = SparkExecutionModel.project(oldResult, varSet)
    IntermediateResult.putResult(id, newResult)
    IntermediateResult.removeResult(op.getSubOp.hashCode())
  }

  override def getTag: String = { tag }

  override def getId: Int = { id }

  def getOp: Op = { op }

  def getVars: List[Var] = { vars }
}
