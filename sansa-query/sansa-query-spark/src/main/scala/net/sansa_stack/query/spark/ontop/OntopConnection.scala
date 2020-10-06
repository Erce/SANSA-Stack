package net.sansa_stack.query.spark.ontop

import java.io.StringReader
import java.sql.{Connection, DriverManager, SQLException}
import java.util.Properties

import it.unibz.inf.ontop.injection.{OntopMappingSQLAllConfiguration, OntopMappingSQLAllOWLAPIConfiguration, OntopReformulationSQLConfiguration, OntopSQLOWLAPIConfiguration}
import org.semanticweb.owlapi.model.OWLOntology

import net.sansa_stack.rdf.common.partition.core.RdfPartitionComplex

/**
 * Used to keep expensive resource per executor alive.
 *
 * @author Lorenz Buehmann
 */
object OntopConnection {

  // create the tmp DB needed for Ontop
  private val JDBC_URL = "jdbc:h2:mem:sansaontopdb;DATABASE_TO_UPPER=FALSE"
  private val JDBC_USER = "sa"
  private val JDBC_PASSWORD = ""

  lazy val connection: Connection = try {
//    println("creating DB connection ")
    val conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)
//    println("created DB connection")
    conn
  } catch {
    case e: SQLException =>
      throw e
  }
  sys.addShutdownHook {
    connection.close()
  }

  var configs = Map[Set[RdfPartitionComplex], OntopReformulationSQLConfiguration]()

  def apply(obdaMappings: String, properties: Properties, partitions: Set[RdfPartitionComplex], ontology: Option[OWLOntology]): OntopReformulationSQLConfiguration = {
    val conf = configs.getOrElse(partitions, {
      println("creating reformulation config")
      val reformulationConfiguration = {
        JDBCDatabaseGenerator.generateTables(connection, partitions)

        OntopUtils.createReformulationConfig(obdaMappings, properties, ontology)
      }

      configs += partitions -> reformulationConfiguration

//      println("done")
      reformulationConfiguration
    })
    conf
  }

}
