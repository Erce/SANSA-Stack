package net.sansa_stack.rdf.common.partition.core

import net.sansa_stack.rdf.common.partition.layout.{TripleLayout, TripleLayoutDouble, TripleLayoutLong, TripleLayoutString, TripleLayoutStringDate, TripleLayoutStringLang}
import org.apache.jena.datatypes.TypeMapper
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.vocabulary.{RDF, XSD}

object RdfPartitionerDefault
  extends RdfPartitioner[RdfPartitionDefault] with Serializable {
  def getUriOrBNodeString(node: Node): String = {
    val termType = getRdfTermType(node)
    termType match {
      case 0 => node.getBlankNodeId.getLabelString
      case 1 => node.getURI
      case _ => throw new RuntimeException("Neither Uri nor blank node: " + node)
    }
  }

  def getRdfTermType(node: Node): Byte = {
    val result =
      if (node.isURI()) 1.toByte else if (node.isLiteral()) 2.toByte else if (node.isBlank()) 0.toByte else {
        throw new RuntimeException("Unknown RDF term type: " + node)
      } // -1
    result
  }

  def isPlainLiteralDatatype(dtypeIri: String): Boolean = {
    val result = dtypeIri == null || dtypeIri == "" || dtypeIri == XSD.xstring.getURI || dtypeIri == RDF.langString.getURI
    result
  }

  def isPlainLiteral(node: Node): Boolean = {
    val result = node.isLiteral() && isPlainLiteralDatatype(node.getLiteralDatatypeURI) // NodeUtils.isSimpleString(node) || NodeUtils.isLangString(node))
    result
  }

  def isTypedLiteral(node: Node): Boolean = {
    val result = node.isLiteral() && !isPlainLiteral(node)
    result
  }

  def fromTriple(t: Triple): RdfPartitionDefault = {
    val s = t.getSubject
    val o = t.getObject

    val subjectType = getRdfTermType(s)
    val objectType = getRdfTermType(o)
    // val predicateType =

    val predicate = t.getPredicate.getURI

    // In the case of plain literals, we replace the datatype langString with string
    // in order to group them all into the same partition
    val datatype = if (o.isLiteral()) (if (isPlainLiteral(o)) XSD.xstring.getURI else o.getLiteralDatatypeURI) else ""
    val langTagPresent = isPlainLiteral(o)

    RdfPartitionDefault(subjectType, predicate, objectType, datatype, langTagPresent)
  }

  /**
   * Lay a triple out based on the partition
   * Does not (re-)check the matches condition
   */
  def determineLayout(t: RdfPartitionDefault): TripleLayout = {
    val oType = t.objectType

    val layout = oType match {
      case 0 => TripleLayoutString
      case 1 => TripleLayoutString
      case 2 => if (isPlainLiteralDatatype(t.datatype)) TripleLayoutStringLang else determineLayoutDatatype(t.datatype)
      // if(!t.langTagPresent)
      // TripleLayoutString else TripleLayoutStringLang
      case _ => throw new RuntimeException("Unsupported object type: " + t)
    }
    layout
  }

  private val intDTypeURIs = Set(XSDDatatype.XSDnegativeInteger, XSDDatatype.XSDpositiveInteger,
                        XSDDatatype.XSDnonNegativeInteger, XSDDatatype.XSDnonPositiveInteger,
                        XSDDatatype.XSDinteger, XSDDatatype.XSDint)
                      .map(_.getURI)

  def determineLayoutDatatype(dtypeIri: String): TripleLayout = {
    val dti = if (dtypeIri == "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString") {
      XSD.xstring.getURI
    } else dtypeIri

    var v = TypeMapper.getInstance.getSafeTypeByName(dti).getJavaClass

    // type mapper returns null for some integer types
    if (v == null && intDTypeURIs.contains(dtypeIri)) v = classOf[Integer]

    // val v = node.getLiteralValue
    v match {
      case w if (w == classOf[java.lang.Byte] || w == classOf[java.lang.Short] || w == classOf[java.lang.Integer] || w == classOf[java.lang.Long]) => TripleLayoutLong
      case w if (w == classOf[java.lang.Float] || w == classOf[java.lang.Double]) => TripleLayoutDouble
      case w if dtypeIri == XSD.date.getURI => TripleLayoutStringDate()
      // case w if(w == classOf[String]) => TripleLayoutString
      case w => TripleLayoutString
      // case _ => TripleLayoutStringDatatype

      // case _ => throw new RuntimeException("Unsupported object type: " + dtypeIri)
    }
  }
}
