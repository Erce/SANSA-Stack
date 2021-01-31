package net.sansa_stack.query.spark.binding.engine

import java.io.{File, FileInputStream}
import java.util.concurrent.TimeUnit

import com.google.common.base.Stopwatch
import com.holdenkarau.spark.testing.DataFrameSuiteBase
import net.sansa_stack.query.spark.api.domain.ResultSetSpark
import net.sansa_stack.query.spark.ops.rdd.RddOfBindingOps
import net.sansa_stack.rdf.common.io.hadoop.TrigRecordReader
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.hadoop.fs.Path
import org.apache.jena.query.{Dataset, DatasetFactory, QueryExecutionFactory, QueryFactory, ResultSetFactory}
import org.apache.jena.riot.resultset.ResultSetLang
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFLanguages, ResultSetMgr}
import org.apache.jena.sparql.resultset.ResultSetCompare
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.scalatest.{FunSuite, Ignore}

@Ignore // Doesn't always find the bz2 file the way its used heer
class BindingEngineTests extends FunSuite with DataFrameSuiteBase {

  // JenaSystem.init

  override def conf(): SparkConf = {
    val conf = super.conf
    conf
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .set("spark.kryo.registrator", String.join(", ",
        "net.sansa_stack.rdf.spark.io.JenaKryoRegistrator"))
    conf
  }


  def createTestRdd(): RDD[Dataset] = {
    import net.sansa_stack.rdf.spark.io._

    val testFile = new File(classOf[TrigRecordReader].getClassLoader.getResource("hobbit-sensor-stream-150k-events-data.trig.bz2").getPath)
    val path = new Path(testFile.getAbsolutePath)

    spark.datasets(Lang.TRIG)(path.toString)
  }


  test("group of RDD[Binding] should match expected result") {
    val referenceFile = new File(getClass.getClassLoader.getResource("hobbit-sensor-stream-150k-events-data.trig.bz2").getPath)

    // read the target dataset
    val sw = Stopwatch.createStarted
    val refDataset = DatasetFactory.create()
    RDFDataMgr.read(refDataset, new BZip2CompressorInputStream(new FileInputStream(referenceFile)), Lang.TRIG)

    val qe = QueryExecutionFactory.create(
      """
        | SELECT ?qec (SUM(?qc_contrib) AS ?qc) {
        |     { SELECT (COUNT(?qe) AS ?qec) (1 as ?qc_contrib) {
        |       GRAPH ?g { ?s <http://www.w3.org/ns/sosa/#featureOfInterest> ?qe }
        |     } GROUP BY ?g }
        | }
        | GROUP BY ?qec ORDER BY ?qec
        |""".stripMargin, refDataset)

    val expectedRs = ResultSetFactory.makeRewindable(qe.execSelect())
    println("Jena took " + sw.elapsed(TimeUnit.SECONDS))

    sw.reset().start()
    val rdd = createTestRdd()

    val resultSetSpark: ResultSetSpark = RddOfBindingOps.selectWithSparql(rdd, QueryFactory.create(
      """
        | SELECT ?qec (SUM(?qc_contrib) AS ?qc) {
        |   SERVICE <rdd:perPartition> {
        |     { SELECT (COUNT(?qe) AS ?qec) (1 as ?qc_contrib) {
        |       GRAPH ?g { ?s <http://www.w3.org/ns/sosa/#featureOfInterest> ?qe }
        |     } GROUP BY ?g }
        |   }
        | }
        | GROUP BY ?qec ORDER BY ?qec
        |""".stripMargin))

    val actualRs = ResultSetFactory.makeRewindable(resultSetSpark.collectToTable.toResultSet)
    println("Spark took " + sw.elapsed(TimeUnit.SECONDS))

    val isEqual = ResultSetCompare.equalsByValue(expectedRs, actualRs)
    assertTrue(isEqual)

    expectedRs.reset()
    actualRs.reset()
    ResultSetMgr.write(System.out, expectedRs, ResultSetLang.SPARQLResultSetText)
    ResultSetMgr.write(System.out, actualRs, ResultSetLang.SPARQLResultSetText)
  }


}