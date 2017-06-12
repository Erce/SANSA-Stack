package org.sansa_stack.query.sparqlify

import org.scalatest._

import scala.reflect.runtime.universe._
import org.apache.jena.graph.Triple
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import java.io.ByteArrayInputStream
import scala.collection.JavaConverters._
import net.sansa_stack.query.spark.sparqlify.SparqlifyUtils3;
import net.sansa_stack.rdf.spark.partition.core.RdfPartitionUtilsSpark
import net.sansa_stack.query.spark.sparqlify.QueryExecutionFactorySparqlifySpark
import org.apache.jena.query.ResultSetFormatter
import net.sansa_stack.rdf.spark.io.NTripleReader
import net.sansa_stack.rdf.partition.schema.SchemaStringString

class TestRdfPartition extends FlatSpec {

  "A partitioner" should "support custom datatypes" in {

/*
    val t = typeOf[SchemaStringString]
    val attrNames = t.members.sorted.collect({ case m: MethodSymbol if m.isCaseAccessor => m.name }).toList
    println(attrNames)

    System.exit(0)
*/
    val sparkSession = SparkSession.builder
      .master("local[*]")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .config("spark.kryo.registrator", String.join(", ",
        "net.sansa_stack.rdf.spark.io.JenaKryoRegistrator",
        "net.sansa_stack.query.spark.sparqlify.KryoRegistratorSparqlify"))
      .appName("Partitioner test")
      .getOrCreate()

    //val rdfStr: String = """<http://ex.org/Nile> <http://ex.org/length> "6800"^^<http://ex.org/km> ."""
    val triples: List[Triple] = RDFDataMgr.createIteratorTriples(getClass.getResourceAsStream("/dbpedia-01.nt"), Lang.NTRIPLES, null).asScala.toList
    val graphRdd = sparkSession.sparkContext.parallelize(triples)

    //val graphRdd = NTripleReader.load(sparkSession, "classpath:dbpedia-01.nt")



    val partitions = RdfPartitionUtilsSpark.partitionGraph(graphRdd)
    val rewriter = SparqlifyUtils3.createSparqlSqlRewriter(sparkSession, partitions)
    val qef = new QueryExecutionFactorySparqlifySpark(sparkSession, rewriter)
    println(ResultSetFormatter.asText(qef.createQueryExecution("SELECT * { ?s ?p ?o }").execSelect()))

    sparkSession.stop

    // TODO Validate result - right now its already a success if no exception is thrown

//    val stack = new Stack[Int]
//    stack.push(1)
//    stack.push(2)
//    assert(stack.pop() === 2)
//    assert(stack.pop() === 1)
  }
}