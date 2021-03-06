package scalafix.test

import scala.meta._
import scalafix.{LintCategory, LintMessage, Rule, RuleCtx}

object NoNull extends Rule("NoNull") {
  val error = LintCategory.error("Nulls are not allowed.")

  override def check(ctx: RuleCtx): List[LintMessage] = ctx.tree.collect {
    case nil @ q"null" => error.at(nil.pos)
  }
}
