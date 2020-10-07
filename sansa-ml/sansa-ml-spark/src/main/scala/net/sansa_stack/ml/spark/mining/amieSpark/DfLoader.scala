package net.sansa_stack.ml.spark.mining.amieSpark

import org.apache.spark.SparkContext
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.slf4j.LoggerFactory

object DfLoader {

  private val logger = LoggerFactory.getLogger(this.getClass.getName)

  case class Atom(rdf: RDFTriple)

  def loadFromFileDF(path: String, sc: SparkContext, sqlContext: SQLContext, minPartitions: Int = 2): DataFrame = {
    logger.info("loading triples from disk...")
    val startTime = System.currentTimeMillis()
    import sqlContext.implicits._

    /*   var y = StructType(StructField("sub", StringType,false)::
                        StructField("rel", StringType, false)::
                        StructField("ob", StringType, false):: Nil) */

    val triples =
      sc.textFile(path, minPartitions)
        .map(line => line.replace("<", "").replace(">", "").split("\\s+")) // line to tokens
        .map(tokens => Atom(RDFTriple(tokens(0), tokens(1), tokens(2).stripSuffix(".")))) // tokens to triple
        .toDF()
    // val triples = sqlContext.createDataFrame(x, y)
    logger.info("finished loading DF " + triples.count() + " triples in " + (System.currentTimeMillis() - startTime) + "ms.")
    return triples
  }
}
