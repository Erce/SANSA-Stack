package net.sansa_stack.query.spark.ontop

import net.sansa_stack.rdf.common.partition.core.RdfPartitionStateDefault
import org.apache.commons.lang3.StringUtils
import org.apache.jena.vocabulary.{RDF, XSD}
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.parameters.Imports

import scala.collection.JavaConverters._

/**
 * @author Lorenz Buehmann
 */
object OntopMappingGenerator {

  val logger = com.typesafe.scalalogging.Logger(OntopMappingGenerator.getClass)

  val blankNodeStrategy: BlankNodeStrategy.Value = BlankNodeStrategy.Table
  val distinguishStringLiterals: Boolean = false

  def createOBDAMappingsForPartitions(partitions: Set[RdfPartitionStateDefault], ontology: Option[OWLOntology] = None): String = {

    // object is URI or bnode
    def createMapping(id: String, tableName: String, partition: RdfPartitionStateDefault): String = {

      val targetSubject = if (partition.subjectType == 0) "_:{s}" else "<{s}>"
      val targetObject = if (partition.objectType == 0) "_:{o}" else "<{o}>"

      if (blankNodeStrategy == BlankNodeStrategy.Table) {
        s"""
           |mappingId     $id
           |source        SELECT "s", "o" FROM ${SQLUtils.escapeTablename(tableName)}
           |target        $targetSubject <${partition.predicate}> $targetObject .
           |""".stripMargin
      } else if (blankNodeStrategy == BlankNodeStrategy.Column) {
        val whereCondition = s"${if (partition.subjectType == 1) "NOT" else ""} s_blank AND" +
          s" ${if (partition.objectType == 1) "NOT" else ""} o_blank"

        s"""
           |mappingId     $id
           |source        SELECT "s", "o" FROM ${SQLUtils.escapeTablename(tableName)} WHERE $whereCondition
           |target        $targetSubject <${partition.predicate}> $targetObject .
           |""".stripMargin
      } else {
        throw new RuntimeException(s"Unsupported Blank Node Strategy:$blankNodeStrategy")
      }
    }

    // object is string literal
    def createMappingStringLit(id: String, tableName: String, partition: RdfPartitionStateDefault): String = {
      val lang = Option(StringUtils.trimToNull(partition.lang.getOrElse("")))
      val targetSubject = if (partition.subjectType == 0) "_:{s}" else "<{s}>"
      val targetObject = if (lang.nonEmpty) s""" "{o}"@${lang.get} """ else "\"{o}\""
      val whereConditionLang = if (lang.nonEmpty) s""" "l" = '${lang.get}' """ else "\"l\" = ''" // IS NULL"

      s"""
         |mappingId     $id
         |source        SELECT "s", "o" FROM ${SQLUtils.escapeTablename(tableName)} WHERE $whereConditionLang
         |target        $targetSubject <${partition.predicate}> $targetObject .
         |""".stripMargin
    }

    // object is other literal
    def createMappingLit(id: String, tableName: String, partition: RdfPartitionStateDefault): String = {
      val targetSubject = if (partition.subjectType == 0) "_:{s}" else "<{s}>"
      s"""
         |mappingId     $id
         |source        SELECT "s", "o" FROM ${SQLUtils.escapeTablename(tableName)}
         |target        $targetSubject <${partition.predicate}> "{o}"^^<${partition.datatype}> .
         |""".stripMargin
    }

    // class assertion mapping
    def createClassMapping(id: String, tableName: String, partition: RdfPartitionStateDefault, cls: String): String = {
      val targetSubject = if (partition.subjectType == 0) "_:{s}" else "<{s}>"
      s"""
         |mappingId     $id
         |source        SELECT "s", "o" FROM ${SQLUtils.escapeTablename(tableName)} WHERE "o" = '$cls'
         |target        $targetSubject <${RDF.`type`.getURI}> <$cls> .
         |""".stripMargin
    }

    def createMappingLiteralWithType(id: String, tableName: String, property: String): String = {
      s"""
         |mappingId     $id
         |source        SELECT "s", "o", "t" FROM ${SQLUtils.escapeTablename(tableName)}
         |target        <{s}> <$property> "{o}"^^<{t}> .
         |""".stripMargin
    }

    val triplesMapping =
      s"""
         |mappingId     triples
         |source        SELECT `s`, `p`, `o` FROM `triples`
         |target        <{s}> <http://sansa.net/ontology/triples> "{o}" .
         |""".stripMargin

    "[MappingDeclaration] @collection [[" +
      partitions
        .map {
          case p@RdfPartitionStateDefault(subjectType, predicate, objectType, datatype, langTagPresent, lang) =>
            val tableName = SQLUtils.createTableName(p, blankNodeStrategy)
            val id = SQLUtils.escapeTablename(tableName + lang.getOrElse(""))

            if (predicate == RDF.`type`.getURI) { // rdf:type mapping expansion here
              if (ontology.nonEmpty) ontology.get.getClassesInSignature(Imports.EXCLUDED).asScala.map(cls => createClassMapping(id, tableName, p, cls.toStringID)).mkString("\n") else ""
            } else {
              objectType match {
                case 0 | 1 => createMapping(id, tableName, p) // o is URI or bnode
                case 2 => if (distinguishStringLiterals) {
                  if (langTagPresent) createMappingStringLit(id, tableName, p) else createMappingLit(id, tableName, p)
                } else {
                  if (langTagPresent || datatype == XSD.xstring.getURI) createMappingStringLit(id, tableName, p) else createMappingLit(id, tableName, p)
                }
                case _ =>
                  logger.error("TODO: bnode Ontop mapping creation")
                  ""
              }
            }
        }
        .mkString("\n\n") + "\n]]"
  }

}
