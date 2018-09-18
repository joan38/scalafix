package scalafix.v1
// scalafmt: { maxColumn = 120 }

import scalafix.internal.util.Pretty

/**
 * Encoding of synthetic trees that are generated by inferred type parameters, implicits and <code>.apply</code>.
 */
sealed abstract class SyntheticTree extends Product with Serializable {
  final override def toString: String = Pretty.pretty(this).render(80)
  final def isEmpty: Boolean = this == NoTree
  final def nonEmpty: Boolean = !isEmpty
}
final case class IdTree(symbol: Symbol) extends SyntheticTree
final case class SelectTree(qualifier: SyntheticTree, id: IdTree) extends SyntheticTree
final case class ApplyTree(function: SyntheticTree, arguments: List[SyntheticTree]) extends SyntheticTree
final case class TypeApplyTree(function: SyntheticTree, typeArguments: List[ScalaType]) extends SyntheticTree
final case class FunctionTree(parameters: List[IdTree], body: SyntheticTree) extends SyntheticTree
final case class LiteralTree(constant: Constant) extends SyntheticTree
final case class MacroExpansionTree(beforeExpansion: SyntheticTree, tpe: ScalaType) extends SyntheticTree
final case class OriginalSubTree(tree: scala.meta.Tree) extends SyntheticTree
final case class OriginalTree(tree: scala.meta.Tree) extends SyntheticTree
case object NoTree extends SyntheticTree