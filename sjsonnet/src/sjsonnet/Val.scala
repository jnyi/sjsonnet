package sjsonnet

import sjsonnet.Expr.Member.Visibility

object Ref{
  def apply(calc0: => Val) = new Ref(calc0)
}
class Ref(calc0: => Val){
  lazy val calc = calc0
  def force(self: Option[Val.Obj]): Val = calc
}
sealed trait Val
object Val{
  def bool(b: Boolean) = if (b) True else False
  case object True extends Val
  case object False extends Val
  case object Null extends Val
  case class Str(value: String) extends Val
  case class Num(value: Double) extends Val
  case class Arr(value: Seq[Ref]) extends Val
  object Obj{

    case class Member(add: Boolean, visibility: Visibility, invoke: (Obj, Option[Obj]) => Ref)
  }
  case class Obj(value0: Map[String, Obj.Member],
                 `super`: Option[Obj]) extends Val{

    def getVisibleKeys() = {
      def rec(current: Val.Obj): Seq[(String, Visibility)] = {
        current.`super`.toSeq.flatMap(rec) ++
        current.value0.map{case (k, m) => (k, m.visibility)}.toSeq
      }

      val mapping = collection.mutable.LinkedHashMap.empty[String, Boolean]
      for ((k, sep) <- rec(this)){
        (mapping.get(k), sep) match{
          case (None, Visibility.Hidden) => mapping(k) = true
          case (None, _)    => mapping(k) = false

          case (Some(false), Visibility.Hidden) => mapping(k) = true
          case (Some(true), Visibility.Unhide) => mapping(k) = false
          case (Some(x), _) => mapping(k) = x
        }
      }
      mapping
    }
    val valueCache = collection.mutable.Map.empty[Any, Val]
    def value(k: String, self: Obj = this) = {
      Ref(
        valueCache.getOrElseUpdate(
          // It is very rare that self != this, so fast-path the common case
          // where they are the same by avoiding tuple construction and hashing
          if(self == this) k else (k, self),
          valueRaw(k, self).getOrElse(throw new Exception("Unknown key: " + k))
        )
      )
    }

    def mergeMember(l: Val, r: Val) = (l, r) match{
      case (Val.Str(l), Val.Str(r)) => Val.Str(l + r)
      case (Val.Num(l), Val.Num(r)) => Val.Num(l + r)
      case (l: Val.Obj, r: Val.Obj) => Evaluator.mergeObjects(l, r)
    }

    def valueRaw(k: String, self: Obj): Option[Val] = this.value0.get(k) match{
      case Some(m) =>
        val localResult = m.invoke(self, this.`super`).calc
        this.`super` match{
          case Some(s) if m.add =>
            Some(s.valueRaw(k, self).fold(localResult)(mergeMember(_, localResult)))
          case _ => Some(localResult)
        }

      case None => this.`super`.flatMap(_.valueRaw(k, self))
    }
  }

  case class Func(length: Int, value: Seq[(Option[String], Ref)] => Val) extends Val
}