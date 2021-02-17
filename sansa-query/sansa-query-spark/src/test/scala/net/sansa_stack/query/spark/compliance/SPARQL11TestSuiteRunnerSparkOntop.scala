package net.sansa_stack.query.spark.compliance

import java.util.Objects

import org.apache.jena.query.Query
import org.apache.jena.rdf.model.Model
import org.apache.jena.sparql.resultset.SPARQLResult
import org.apache.spark.SparkConf
import org.scalatest.DoNotDiscover
import org.scalatest.tags.Slow

import net.sansa_stack.query.spark.api.domain.QueryEngineFactory
import net.sansa_stack.query.spark.ontop.KryoUtils.enableLoggingToFile
import net.sansa_stack.query.spark.ontop.{KryoUtils, QueryEngineFactoryOntop}
import net.sansa_stack.query.tests.SPARQLQueryEvaluationTest


/**
 * SPARQL 1.1 test suite runner for Ontop-based SPARQL-to-SQL implementation on Apache Spark.
 *
 *
 * @author Lorenz Buehmann
 */
@DoNotDiscover
@Slow
class SPARQL11TestSuiteRunnerSparkOntop
  extends SPARQL11TestSuiteRunnerSpark {

//  KryoUtils.enableLoggingToFile("/tmp/kryo.log")

  override lazy val IGNORE = Set(
    /* AGGREGATES */
    aggregatesManifest + "agg-err-02",
    /* BINDINGS */
    // TODO: fix it (UNDEF involves the notion of COMPATIBILITY when joining)
    bindingsManifest + "values8", bindingsManifest + "values5",
    /* FUNCTIONS */
    // bnode not supported in SPARQL transformation
    functionsManifest + "bnode01", functionsManifest + "bnode02", // the SI does not preserve the original timezone
//    functionsManifest + "hours", functionsManifest + "day", // not supported in SPARQL transformation
    functionsManifest + "if01", functionsManifest + "if02",
    functionsManifest + "in01", functionsManifest + "in02",
    functionsManifest + "iri01",
    // not supported in H2 transformation
//    functionsManifest + "md5-01", functionsManifest + "md5-02",
    // The SI does not support IRIs as ORDER BY conditions
    functionsManifest + "plus-1", functionsManifest + "plus-2",
    functionsManifest + "strdt01", functionsManifest + "strdt02", functionsManifest + "strdt03",
    functionsManifest + "strlang01", functionsManifest + "strlang02", functionsManifest + "strlang03",
//    functionsManifest + "timezone", // TZ is not supported in H2
//    functionsManifest + "tz",
    /* CONSTRUCT not supported yet */
    // Projection cannot be cast to Reduced in rdf4j
    constructManifest + "constructwhere01", constructManifest + "constructwhere02", constructManifest + "constructwhere03", // problem importing dataset
    constructManifest + "constructwhere04", /* CSV */
    // Sorting by IRI is not supported by the SI
    csvTscResManifest + "tsv01", csvTscResManifest + "tsv02", // different format for number and not supported custom datatype
    csvTscResManifest + "tsv03",
    /* GROUPING */
    // Multi-typed COALESCE as grouping condition TODO: support it
    groupingManifest + "group04",
    /* NEGATION not supported yet */
    negationManifest + "subset-by-exclusion-nex-1", negationManifest + "temporal-proximity-by-exclusion-nex-1", negationManifest + "subset-01", negationManifest + "subset-02", negationManifest + "set-equals-1", negationManifest + "subset-03", negationManifest + "exists-01", negationManifest + "exists-02", // DISABLED DUE TO ORDER OVER IRI
    negationManifest + "full-minuend", negationManifest + "partial-minuend", // TODO: enable it
    negationManifest + "full-minuend-modified", negationManifest + "partial-minuend-modified",
    /* EXISTS not supported yet */
    existsManifest + "exists01", existsManifest + "exists02", existsManifest + "exists03", existsManifest + "exists04", existsManifest + "exists05",
    /* PROPERTY PATH */
    // Not supported: ArbitraryLengthPath
    propertyPathManifest + "pp02", // wrong result, unexpected binding
    propertyPathManifest + "pp06", propertyPathManifest + "pp12", propertyPathManifest + "pp14", propertyPathManifest + "pp16", propertyPathManifest + "pp21", propertyPathManifest + "pp23", propertyPathManifest + "pp25", // Not supported: ZeroLengthPath
    propertyPathManifest + "pp28a", propertyPathManifest + "pp34", propertyPathManifest + "pp35", propertyPathManifest + "pp36", propertyPathManifest + "pp37",
    /* SERVICE not supported yet */
    serviceManifest + "service1", // no loading of the dataset
    serviceManifest + "service2", serviceManifest + "service3", serviceManifest + "service4a", serviceManifest + "service5", serviceManifest + "service6", serviceManifest + "service7",
    /* SUBQUERY */
    // Quad translated as a triple. TODO: fix it
    subqueryManifest + "subquery02", subqueryManifest + "subquery04", // EXISTS is not supported yet
    subqueryManifest + "subquery10", // ORDER BY IRI (for supported by the SI)
    subqueryManifest + "subquery11", // unbound variable: Var TODO: fix it
    subqueryManifest + "subquery12", subqueryManifest + "subquery13", // missing results (TODO: fix)
    subqueryManifest + "subquery14",


    // some tests that work on an empty model which we do not support in Spark query as the mappings would be empty (could be handled but
    // most likely will never happen)
    functionsManifest + "struuid01", functionsManifest + "uuid01",
    // timezone
    functionsManifest + "tz", functionsManifest + "timezone",
  )

  override def conf: SparkConf = {
    super.conf
      .set("spark.sql.crossJoin.enabled", "true")
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .set("spark.kryo.registrator", String.join(
        ", ",
        "net.sansa_stack.rdf.spark.io.JenaKryoRegistrator",
        "net.sansa_stack.query.spark.ontop.OntopKryoRegistrator"))
  }

//  override lazy val IGNORE_FILTER = t => t.dataFile.contains("/aggregates") && !t.name.startsWith("CONCAT") // && t.name.startsWith("SUM")
//  override lazy val IGNORE_FILTER = t => t.queryFile.contains("function") || t.queryFile.contains("project") // && t.name.contains("STR")

  override def getEngineFactory: QueryEngineFactory = new QueryEngineFactoryOntop(spark)

//  KryoUtils.kryoLoggingEnabled = true

  override def runTest(testCase: SPARQLQueryEvaluationTest, data: Model): Unit = {
//    enableLoggingToFile(s"/tmp/kryo/kryo-trace-${testCase.name}.log")
    super.runTest(testCase, data)
  }

  override def runQuery(query: Query, data: Model): SPARQLResult = {
//    com.esotericsoftware.minlog.Log.info(s"******** RUNNING QUERY *********\n$query")
    super.runQuery(query, data)
  }
}

import org.scalatest.Tag

object ExperimentalTest extends Tag("net.sansa_stack.tests.ExperimentalTest")
