package org.dissect.rdf.spark.model

trait RDFDSL[Rdf <: RDF] { this: RDFNodeOps[Rdf] with RDFGraphOps[Rdf] =>

  // graph

  object Graph {
    def apply(elems: Rdf#Triple*): Rdf#Graph = makeGraph(elems.toIterable)
    def apply(it: Iterable[Rdf#Triple]): Rdf#Graph = makeGraph(it)
  }

  // triple

  object Triple {
    def apply(s: Rdf#Node, p: Rdf#URI, o: Rdf#Node): Rdf#Triple = makeTriple(s, p, o)
    def unapply(triple: Rdf#Triple): Some[(Rdf#Node, Rdf#URI, Rdf#Node)] =
      Some(fromTriple(triple))
  }

  // URI

  object URI {
    def apply(s: String): Rdf#URI = makeUri(s)
    def unapply(uri: Rdf#URI): Some[String] = Some(fromUri(uri))
    def unapply(node: Rdf#Node): Option[String] =
      foldNode(node)(unapply, bnode => None, lit => None)
  }

  // bnode

  object BNode {
    def apply(): Rdf#BNode = makeBNode()
    def apply(s: String): Rdf#BNode = makeBNodeLabel(s)
    def unapply(bn: Rdf#BNode): Some[String] = Some(fromBNode(bn))
    def unapply(node: Rdf#Node): Option[String] =
      foldNode(node)(uri => None, unapply, lit => None)
  }

  def bnode(): Rdf#BNode = BNode()
  def bnode(s: String): Rdf#BNode = BNode(s)

  // typed literal

  object Literal {
    val xsdString = makeUri("http://www.w3.org/2001/XMLSchema#string")
    val rdfLangString = makeUri("http://www.w3.org/1999/02/22-rdf-syntax-ns#langString")
    def unapply(literal: Rdf#Literal): Some[(String, Rdf#URI, Option[Rdf#Lang])] = Some(fromLiteral(literal))
    def unapply(node: Rdf#Node): Option[(String, Rdf#URI, Option[Rdf#Lang])] =
      foldNode(node)(uri => None, bnode => None, unapply)
    def apply(lexicalForm: String): Rdf#Literal = makeLiteral(lexicalForm, xsdString)
    def apply(lexicalForm: String, datatype: Rdf#URI): Rdf#Literal = makeLiteral(lexicalForm, datatype)
    def tagged(lexicalForm: String, lang: Rdf#Lang): Rdf#Literal = makeLangTaggedLiteral(lexicalForm, lang)
  }

  // lang

  object Lang {
    def apply(s: String): Rdf#Lang = makeLang(s)
    def unapply(l: Rdf#Lang): Some[String] = Some(fromLang(l))
  }
}
