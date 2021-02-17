package net.sansa_stack.ml.spark.featureExtraction

import net.sansa_stack.query.spark.ops.rdd.RddToDataFrameMapper
import net.sansa_stack.query.spark.sparqlify.{SparqlifyUtils3}
import net.sansa_stack.rdf.common.partition.core.{RdfPartitionerComplex, RdfPartitionerDefault}
import net.sansa_stack.query.spark._
import net.sansa_stack.rdf.spark.partition.RDFPartition
import org.apache.spark.ml.Transformer
import org.apache.spark.ml.param.ParamMap
import org.apache.spark.ml.util.Identifiable
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types.{BooleanType, DoubleType, FloatType, IntegerType, NullType, StringType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Dataset, Encoders, Row, SparkSession}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

/**
 * This SparqlFrame Transformer creates a dataframe based on a SPARQL query
 * the resulting columns correspond to projection variables
 */
class SparqlFrame extends Transformer{
  var _query: String = _
  var _queryExcecutionEngine: String = "sparqlify"

  override val uid: String = Identifiable.randomUID("sparqlFrame")

  protected val spark = SparkSession.builder().getOrCreate()

  override def copy(extra: ParamMap): Transformer = defaultCopy(extra)

  /**
   * setter for query as string
   *
   * @param queryString a sparql query defining which features you want to have
   * @return the set transformer
   */
  def setSparqlQuery(queryString: String): this.type = {
    _query = queryString
    this
  }

  /**
   * setter to specify which query excecution engine to be used
   *
   * option one is ontop option two sparqlify
   *
   * @param queryExcecutionEngine a string either ontop or sparqlify
   * @return the set transformer
   */
  def setQueryExcecutionEngine(queryExcecutionEngine: String): this.type = {
    if (queryExcecutionEngine == "sparqlify" | queryExcecutionEngine == "ontop" ) {
      _queryExcecutionEngine = queryExcecutionEngine
    }
    else {
      throw new Exception(s"Your set engine: ${_queryExcecutionEngine} not supported. at the moment only ontop and sparqlify")
    }
    this
  }

  override def transformSchema(schema: StructType): StructType =
    throw new NotImplementedError()
  /*
  /**
   * call of repective query laver to get query result as RDD[Bindings]
   * @param dataset the readin KG as Dataset[Triple]
   * @return the aswer from the query as rdd bindings so features are a var binding
   */
  protected def getResultBindings(dataset: Dataset[_]): RDD[Binding] = {
    if (_queryExcecutionEngine == "ontop") {
      println("SparqlFrame: Usage of Ontop for Query Execution")
      implicit val tripleEncoder = Encoders.kryo(classOf[Triple])

      val partitions: Map[RdfPartitionComplex, RDD[Row]] =
        RdfPartitionUtilsSpark.partitionGraph(
          dataset.as[Triple].rdd,
          partitioner = RdfPartitionerComplex(false))

      val sparqlEngine = OntopSPARQLEngine(spark, partitions, Option.empty)
      val bindings: RDD[Binding] = sparqlEngine.execSelect(_query)

      bindings
    }
    else {
      println("SparqlFrame: Usage of Sparqlify for Query Execution")
      val query = QueryFactory.create(_query)
      implicit val tripleEncoder = Encoders.kryo(classOf[Triple])
      val partitions = RdfPartitionUtilsSpark.partitionGraph(dataset.as[Triple].rdd)
      val rewriter = SparqlifyUtils3.createSparqlSqlRewriter(spark, partitions)
      val qef: QueryExecutionFactorySparqlifySpark = new QueryExecutionFactorySparqlifySpark(spark, rewriter)
      val qe: QueryExecutionSparqlifySpark = qef.createQueryExecution(query)

      val sparkResultSet = qe.execSelectSpark() // SparkResultSet is a pair of result vars + rdd
      val resultVars : java.util.List[Var] = sparkResultSet.getResultVars
      val javaRdd: JavaRDD[Binding] = sparkResultSet.getRdd
      val scalaRdd : RDD[Binding] = javaRdd.rdd

      scalaRdd
    }

  } */

  /**
   * creates a natic spark mllib dataframe with columns corresponding to the projection variables
   *
   * columns are implicitly casted to string or if specified in literals to repective integer ect
   *
   * @param dataset the knowledge graph as dataset of jena triple
   * @return a dataframe with columns corresponding to projection variables
   */
  def transform(dataset: Dataset[_]): DataFrame = {
    val graphRdd: RDD[org.apache.jena.graph.Triple] = dataset.rdd.asInstanceOf[RDD[org.apache.jena.graph.Triple]]
    // graphRdd.foreach(println(_))

    val qef = _queryExcecutionEngine match {
      case "sparqlify" =>
        graphRdd.verticalPartition(RdfPartitionerDefault).sparqlify
      case "ontop" =>
        throw new Exception(s"Ontop under develop at the moment")
        // graphRdd.verticalPartition(RdfPartitionerDefault).ontop
      case _ => throw new Exception(s"Your set engine: ${_queryExcecutionEngine} not supported")
    }

    val resultSet = qef.createQueryExecution(_query)
      .execSelectSpark()

    // resultSet.getBindings.foreach(println(_))

    val schemaMapping = RddToDataFrameMapper.createSchemaMapping(resultSet)
    val df = RddToDataFrameMapper.applySchemaMapping(resultSet.getBindings, schemaMapping)

    df
  }
}