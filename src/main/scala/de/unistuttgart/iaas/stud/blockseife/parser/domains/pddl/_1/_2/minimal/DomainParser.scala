package de.unistuttgart.iaas.stud.blockseife.parser.domains.pddl._1._2.minimal

import scala.util.parsing.combinator.{Parsers, RegexParsers}
import scala.util.parsing.input.{NoPosition, Position, Positional, Reader}

/*
Highly inspired by: https://enear.github.io/2016/03/31/parser-combinators/
and the corresponding github repo: https://github.com/enear/parser-combinators-tutorial
 */

/*
Tries to implement only :strips as defined in:
PDDL - The Planning Domain Language Version 1.2
by the AIPS-98 Planning Competition Committee

with the following restrictions (at the moment; pls update this comment when the implementation changed!!):
- No support for comments
- Fixed order for *-def 's in the domain definition according to EBNF
 */

sealed trait DomainCompilationError
case class DomainLexerError(location: Location, msg: String)  extends DomainCompilationError
case class DomainParserError(location: Location, msg: String) extends DomainCompilationError

case class Location(line: Int, column: Int) {
  override def toString = s"$line:$column"
}

sealed trait DomainToken           extends Positional
final case class LEFTBRACKET()     extends DomainToken
final case class RIGHTBRACKET()    extends DomainToken
final case class DEFINE()          extends DomainToken
final case class DOMAIN()          extends DomainToken
final case class EXTENDS()         extends DomainToken
final case class REQUIREMENTS()    extends DomainToken
final case class TYPES()           extends DomainToken
final case class CONSTANTS()       extends DomainToken
final case class DOMAINVARIABLES() extends DomainToken
final case class PREDICATES()      extends DomainToken
final case class QUESTIONMARK()    extends DomainToken
final case class TIMELESS()        extends DomainToken
final case class NOT()             extends DomainToken
final case class SAFETY()          extends DomainToken
final case class AND()             extends DomainToken
final case class ACTION()          extends DomainToken
final case class PARAMETERS()      extends DomainToken
final case class PRECONDITION()    extends DomainToken
final case class EFFECT()          extends DomainToken

final case class COHERENTSTRING(str: String) extends DomainToken

object DomainLexer extends RegexParsers {

  def apply(code: String): Either[DomainLexerError, List[DomainToken]] = {
    parse(tokens, code) match {
      case NoSuccess(msg, next) => Left(DomainLexerError(Location(next.pos.line, next.pos.column), msg))
      case Success(result, _)   => Right(result)
    }
  }

  def tokens: Parser[List[DomainToken]] = {
    phrase(
      rep1(
        leftBracket | rightBracket | define | domain | `extends` | requirements | types | constants | domainVariables | predicates | questionMark | timeless | not | safety | and | action | parameters | precondition | effect | coherentString
      )
    ) ^^ identity
  }

  def leftBracket: DomainLexer.Parser[LEFTBRACKET]   = positioned { "(" ^^ (_ => LEFTBRACKET()) }
  def rightBracket: DomainLexer.Parser[RIGHTBRACKET] = positioned { ")" ^^ (_ => RIGHTBRACKET()) }
  def define: DomainLexer.Parser[DEFINE]             = positioned { "define" ^^ (_ => DEFINE()) }
  def domain: DomainLexer.Parser[DOMAIN]             = positioned { "domain" ^^ (_ => DOMAIN()) }
  def `extends`: DomainLexer.Parser[EXTENDS]         = positioned { ":extends" ^^ (_ => EXTENDS()) }
  def requirements: DomainLexer.Parser[REQUIREMENTS] = positioned { ":requirements" ^^ (_ => REQUIREMENTS()) }
  def types: DomainLexer.Parser[TYPES]               = positioned { ":types" ^^ (_ => TYPES()) }
  def constants: DomainLexer.Parser[CONSTANTS]       = positioned { ":constants" ^^ (_ => CONSTANTS()) }
  def domainVariables: DomainLexer.Parser[DOMAINVARIABLES] = positioned {
    ":domain-variables" ^^ (_ => DOMAINVARIABLES())
  }
  def predicates: DomainLexer.Parser[PREDICATES]     = positioned { ":predicates" ^^ (_ => PREDICATES()) }
  def questionMark: DomainLexer.Parser[QUESTIONMARK] = positioned { "?" ^^ (_ => QUESTIONMARK()) }
  def timeless: DomainLexer.Parser[TIMELESS]         = positioned { ":timeless" ^^ (_ => TIMELESS()) }
  def not: DomainLexer.Parser[NOT]                   = positioned { "not" ^^ (_ => NOT()) }
  def safety: DomainLexer.Parser[SAFETY]             = positioned { ":safety" ^^ (_ => SAFETY()) }
  def and: DomainLexer.Parser[AND]                   = positioned { "and" ^^ (_ => AND()) }
  def action: DomainLexer.Parser[ACTION]             = positioned { ":action" ^^ (_ => ACTION()) }
  def parameters: DomainLexer.Parser[PARAMETERS]     = positioned { ":parameters" ^^ (_ => PARAMETERS()) }
  def precondition: DomainLexer.Parser[PRECONDITION] = positioned { ":precondition" ^^ (_ => PRECONDITION()) }
  def effect: DomainLexer.Parser[EFFECT]             = positioned { ":effect" ^^ (_ => EFFECT()) }

  def coherentString: DomainLexer.Parser[COHERENTSTRING] = positioned {
    // TODO: Create final regex for coherent strings
    """[-_:a-zA-Z0-9.]+""".r ^^ (s => COHERENTSTRING(s.toUpperCase()))
  }
}

sealed trait DomainAST extends Positional

final case class DomainRoot(
    name: Name,
    requireDef: Option[RequireDef],
    typesDef: Option[TypesDef],
    constantsDef: Option[ConstantsDef],
    domainVarsDef: Option[DomainVarsDef],
    predicatesDef: Option[PredicatesDef],
    timelessDef: Option[TimelessDef],
    safetyDef: Option[GoalDescription],
    structureDefs: Seq[StructureDef]
) extends DomainAST

final case class RequireDef(requireKeys: Seq[String])                                       extends DomainAST
final case class TypesDef(typedListOfNames: Seq[Name])                                      extends DomainAST // No ':typing' supported: only getting the list of names (without type or anything)
final case class ConstantsDef(typedListOfNames: Seq[Name])                                  extends DomainAST // No ':typing' supported: only getting the list of names (without type or anything)
final case class DomainVarsDef(typedListOfDomainVarDeclarations: Seq[DomainVarDeclaration]) extends DomainAST
final case class TimelessDef(literalNames: Seq[AFN])                                        extends DomainAST

/** @param variableDeclaration either the name or a tuple with (name, constant) */
final case class DomainVarDeclaration(variableDeclaration: Either[Name, (Name, String)]) extends DomainAST // No ':typing' supported: only getting the list of names (without type or anything)

final case class PredicatesDef(atomicFormulaSkeletons: Seq[AtomicFormulaSkeleton])             extends DomainAST
final case class AtomicFormulaSkeleton(predicate: String, typedListOfVariables: Seq[Variable]) extends DomainAST

sealed trait AFN                                                                extends DomainAST
final case class AtomicFormulaNames(predicate: String, names: Seq[Name])        extends AFN
final case class NegatedAtomicFormulaNames(predicate: String, names: Seq[Name]) extends AFN

sealed trait GoalDescription                                                extends DomainAST
final case class GoalDescriptionAnd(goalDescriptions: Seq[GoalDescription]) extends GoalDescription

sealed trait EffectRoot                              extends DomainAST
final case class EffectAnd(effects: Seq[EffectRoot]) extends EffectRoot

sealed trait AFT extends DomainAST
final case class AtomicFormulaTerms(predicate: String, terms: Seq[Term])
    extends AFT
    with GoalDescription
    with EffectRoot
final case class NegatedAtomicFormulaTerms(predicate: String, terms: Seq[Term])
    extends AFT
    with GoalDescription
    with EffectRoot

final case class ActionDefBody(preconditionGoalDescription: Option[GoalDescription], effect: Option[EffectRoot])
    extends DomainAST

sealed trait StructureDef extends DomainAST
final case class ActionDef(actionFunctor: String, typedListVariable: Seq[Variable], body: ActionDefBody)
    extends StructureDef

sealed trait Term                           extends DomainAST
final case class Name(name: String)         extends Term
final case class Variable(variable: String) extends Term

// Case classes not supported in the AST at this point
final case class ExtensionDef(domainNames: Seq[String]) extends DomainAST

// TMP case classes
final case class PositionalDomainVarDeclarationList(list: Seq[DomainVarDeclaration]) extends Positional
final case class NameList(list: Seq[Name])                                           extends Positional
final case class VariableList(list: Seq[Variable])                                   extends Positional

object DomainParser extends Parsers {
  override type Elem = DomainToken

  class DomainTokenReader(tokens: Seq[DomainToken]) extends Reader[DomainToken] {
    override def first: DomainToken        = tokens.head
    override def atEnd: Boolean            = tokens.isEmpty
    override def pos: Position             = tokens.headOption.map(_.pos).getOrElse(NoPosition)
    override def rest: Reader[DomainToken] = new DomainTokenReader(tokens.tail)
  }

  def apply(tokens: Seq[DomainToken]): Either[DomainParserError, DomainAST] = {
    val reader = new DomainTokenReader(tokens)
    domainFile(reader) match {
      case NoSuccess(msg, next) => Left(DomainParserError(Location(next.pos.line, next.pos.column), msg))
      case Success(result, _)   => Right(result)
    }
  }

  def domainFile: Parser[DomainAST] = {
    phrase(domainRoot)
  }

  /** In EBNF it is called "domain". It ws renamed because of a naming conflict between DOMAIN and Domain (for some
    * reason... the case in files gets ignored)
    */
  def domainRoot: Parser[DomainAST] = positioned {
    LEFTBRACKET() ~ DEFINE() ~ LEFTBRACKET() ~ DOMAIN() ~ name ~ RIGHTBRACKET() ~
      opt(extensionDef) ~
      opt(requireDef) ~
      opt(typesDef) ~
      opt(constantsDef) ~
      opt(domainVarsDef) ~
      opt(predicatesDef) ~
      opt(timelessDef) ~
      opt(safetyDef) ~
      rep(structureDef) ~ RIGHTBRACKET() ^^ {
      case _ ~ _ ~ _ ~ _ ~ name ~ _ ~ _ ~ requireDef ~ typesDef ~ constantsDef ~ domainVarsDef ~ predicatesDef ~ timelessDef ~ safetyDef ~ structureDefs ~ _ =>
        DomainRoot(name,
                   requireDef,
                   typesDef,
                   constantsDef,
                   domainVarsDef,
                   predicatesDef,
                   timelessDef,
                   safetyDef,
                   structureDefs)
    }
  }

  def extensionDef: DomainParser.Parser[ExtensionDef] = positioned {
    LEFTBRACKET() ~ EXTENDS() ~ rep1(domainName) ~ RIGHTBRACKET() ^^ {
      case _ ~ _ ~ domainNames ~ _ =>
        ExtensionDef(domainNames.map(_.str))
    }
  }

  def domainName: DomainParser.Parser[COHERENTSTRING] = positioned {
    // TODO: Create final regex for filenames of domains / domain names
    val domainNameRegex = """([-_:a-zA-Z0-9.])+""".r
    accept("domain name", { case n @ COHERENTSTRING(domainNameRegex(_*)) => n })
  }

  def requireDef: DomainParser.Parser[RequireDef] = positioned {
    LEFTBRACKET() ~ REQUIREMENTS() ~ rep1(requireKey) ~ RIGHTBRACKET() ^^ {
      case _ ~ _ ~ requireKeys ~ _ =>
        RequireDef(requireKeys.map(_.str))
    }
  }

  def requireKey: DomainParser.Parser[COHERENTSTRING] = positioned {
    val requireKeyRegex = """:[a-zA-Z]([-a-zA-Z0-9])*""".r
    accept("require key", { case n @ COHERENTSTRING(requireKeyRegex(_*)) => n })
  }

  def typesDef: DomainParser.Parser[TypesDef] = positioned {
    LEFTBRACKET() ~ TYPES() ~ typedListName ~ RIGHTBRACKET() ^^ {
      // No ':typing' supported: only getting the list of names (without type or anything)
      case _ ~ _ ~ names ~ _ =>
        TypesDef(names.list)
    }
  }

  def typedListName: DomainParser.Parser[NameList] = positioned {
    rep(name) ^^ (names => NameList(names))
  }

  def constantsDef: DomainParser.Parser[ConstantsDef] = positioned {
    LEFTBRACKET() ~ CONSTANTS() ~ typedListName ~ RIGHTBRACKET() ^^ {
      // No ':typing' supported: only getting the list of names (without type or anything)
      case _ ~ _ ~ names ~ _ =>
        ConstantsDef(names.list)
    }
  }

  def domainVarsDef: DomainParser.Parser[DomainVarsDef] = positioned {
    LEFTBRACKET() ~ DOMAINVARIABLES() ~ typedListDomainVarDeclaration ~ RIGHTBRACKET() ^^ {
      case _ ~ _ ~ typedListDomainVarDeclarations ~ _ => DomainVarsDef(typedListDomainVarDeclarations.list)
    }
  }

  def typedListDomainVarDeclaration: DomainParser.Parser[PositionalDomainVarDeclarationList] = positioned {
    rep(domainVarDeclaration) ^^ (list => PositionalDomainVarDeclarationList(list))
  }

  def domainVarDeclaration: DomainParser.Parser[DomainVarDeclaration] = positioned {
    // No ':typing' supported: only getting the list of names (without type or anything)

    val onlyName = name ^^ (n => DomainVarDeclaration(Left(n)))

    val nameAndConstant = (LEFTBRACKET() ~ name ~ constant ~ RIGHTBRACKET()) ^^ {
      case _ ~ name ~ constant ~ _ => DomainVarDeclaration(Right(name, constant.str))
    }

    onlyName | nameAndConstant
  }

  def constant: DomainParser.Parser[COHERENTSTRING] = positioned {
    // TODO: What is really allowed???
    val nameRegex = """[-_a-zA-Z0-9]+""".r
    accept("constant", { case n @ COHERENTSTRING(nameRegex(_*)) => n })
  }

  def predicatesDef: DomainParser.Parser[PredicatesDef] = positioned {
    LEFTBRACKET() ~ PREDICATES() ~ rep1(atomicFormulaSkeleton) ~ RIGHTBRACKET() ^^ {
      case _ ~ _ ~ atomicFormulaSkeletons ~ _ => PredicatesDef(atomicFormulaSkeletons)
    }
  }

  def atomicFormulaSkeleton: DomainParser.Parser[AtomicFormulaSkeleton] = positioned {
    LEFTBRACKET() ~ predicate ~ typedListVariable ~ RIGHTBRACKET() ^^ {
      case _ ~ predicateName ~ variables ~ _ => AtomicFormulaSkeleton(predicateName.name, variables.list)
    }
  }

  def typedListVariable: DomainParser.Parser[VariableList] = positioned {
    rep(variable) ^^ (variables => VariableList(variables))
  }

  def predicate: DomainParser.Parser[Name] = positioned {
    name ^^ identity
  }

  def variable: DomainParser.Parser[Variable] = positioned {
    // No ':typing' supported: only getting the list of variables (without type or anything)
    QUESTIONMARK() ~ name ^^ {
      case _ ~ name => Variable(name.name)
    }
  }

  def timelessDef: DomainParser.Parser[TimelessDef] = positioned {
    LEFTBRACKET() ~ TIMELESS() ~ rep1(literalName) ~ RIGHTBRACKET() ^^ {
      case _ ~ _ ~ literalNames ~ _ => TimelessDef(literalNames)
    }
  }

  def literalName: DomainParser.Parser[AFN] = positioned {
    val af = atomicFormulaNames ^^ identity

    val naf = LEFTBRACKET() ~ NOT() ~ atomicFormulaNames ~ RIGHTBRACKET() ^^ {
      case _ ~ _ ~ atomicFormulaNames ~ _ =>
        NegatedAtomicFormulaNames(atomicFormulaNames.predicate, atomicFormulaNames.names)
    }

    af | naf
  }

  def atomicFormulaNames: DomainParser.Parser[AtomicFormulaNames] = positioned {
    LEFTBRACKET() ~ predicate ~ rep(name) ~ RIGHTBRACKET() ^^ {
      case _ ~ predicateName ~ names ~ _ => AtomicFormulaNames(predicateName.name, names)
    }
  }

  def safetyDef: DomainParser.Parser[GoalDescription] = positioned {
    LEFTBRACKET() ~ SAFETY() ~ goalDescription ~ RIGHTBRACKET() ^^ {
      case _ ~ _ ~ goalDescription ~ _ => goalDescription
    }
  }

  def goalDescription: DomainParser.Parser[GoalDescription] = positioned {
    // atomic formula not needed; it is implicitly matched against in literal term

    val gd1: DomainParser.Parser[GoalDescription] = LEFTBRACKET() ~ AND() ~ rep(goalDescription) ~ RIGHTBRACKET() ^^ {
      case _ ~ _ ~ goalDescriptions ~ _ => GoalDescriptionAnd(goalDescriptions)
    }

    val gd2: DomainParser.Parser[GoalDescription] = literalTerm ^^ identity

    gd2 | gd1
  }

  def atomicFormulaTerms: DomainParser.Parser[AtomicFormulaTerms] = positioned {
    LEFTBRACKET() ~ predicate ~ rep(term) ~ RIGHTBRACKET() ^^ {
      case _ ~ predicateName ~ terms ~ _ => AtomicFormulaTerms(predicateName.name, terms)
    }
  }

  def term: DomainParser.Parser[Term] = positioned {
    val n = name ^^ identity
    val v = variable ^^ identity

    n | v
  }

  def literalTerm: DomainParser.Parser[AFT with GoalDescription] = positioned {
    val af = atomicFormulaTerms ^^ identity

    val naf = LEFTBRACKET() ~ NOT() ~ atomicFormulaTerms ~ RIGHTBRACKET() ^^ {
      case _ ~ _ ~ atomicFormulaTerms ~ _ =>
        NegatedAtomicFormulaTerms(atomicFormulaTerms.predicate, atomicFormulaTerms.terms)
    }

    af | naf
  }

  def structureDef: DomainParser.Parser[ActionDef] = positioned {
    actionDef ^^ identity
  }

  def actionDef: DomainParser.Parser[ActionDef] = positioned {
    LEFTBRACKET() ~ ACTION() ~ actionFunctor ~ PARAMETERS() ~ LEFTBRACKET() ~ typedListVariable ~ RIGHTBRACKET() ~ actionDefBody ~ RIGHTBRACKET() ^^ {
      case _ ~ _ ~ actionFunctorName ~ _ ~ _ ~ typedListVariable ~ _ ~ body ~ _ =>
        ActionDef(actionFunctorName.name, typedListVariable.list, body)
    }
  }

  def actionFunctor: DomainParser.Parser[Name] = positioned {
    name ^^ identity
  }

  def actionDefBody: DomainParser.Parser[ActionDefBody] = positioned {
    // does not implement any extension
    opt(PRECONDITION() ~ goalDescription) ~ opt(EFFECT() ~ effect) ^^ {
      case Some(_ ~ preconditionGoalDescription) ~ Some(_ ~ effect) =>
        ActionDefBody(Some(preconditionGoalDescription), Some(effect))

      case Some(_ ~ preconditionGoalDescription) ~ _ => ActionDefBody(Some(preconditionGoalDescription), None)
      case _ ~ Some(_ ~ effect)                      => ActionDefBody(None, Some(effect))
      case _                                         => ActionDefBody(None, None)
    }
  }

  def effect: DomainParser.Parser[EffectRoot] = positioned {
    val e1 = LEFTBRACKET() ~ AND() ~ rep(effect) ~ RIGHTBRACKET() ^^ {
      case _ ~ _ ~ effects ~ _ => EffectAnd(effects)
    }

    val e2 = LEFTBRACKET() ~ NOT() ~ atomicFormulaTerms ~ RIGHTBRACKET() ^^ {
      case _ ~ _ ~ atomicFormulaTerms ~ _ =>
        NegatedAtomicFormulaTerms(atomicFormulaTerms.predicate, atomicFormulaTerms.terms)
    }

    val e3 = atomicFormulaTerms ^^ identity

    e1 | e2 | e3
  }

  private def name = positioned {
    val nameRegex = """[a-zA-Z]([-_a-zA-Z0-9])*""".r
    accept("name", { case n @ COHERENTSTRING(nameRegex(_*)) => Name(n.str) })
  }

}

object DomainCompiler {
  def apply(code: String): Either[DomainCompilationError, DomainAST] = {
    for {
      tokens <- DomainLexer(code).right
      ast    <- DomainParser(tokens).right
    } yield ast
  }
}
