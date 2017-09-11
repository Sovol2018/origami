package blue.origami.nez.peg;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import blue.origami.nez.peg.expression.PAnd;
import blue.origami.nez.peg.expression.PAny;
import blue.origami.nez.peg.expression.PByte;
import blue.origami.nez.peg.expression.PByteSet;
import blue.origami.nez.peg.expression.PChoice;
import blue.origami.nez.peg.expression.PDetree;
import blue.origami.nez.peg.expression.PDispatch;
import blue.origami.nez.peg.expression.PEmpty;
import blue.origami.nez.peg.expression.PFail;
import blue.origami.nez.peg.expression.PIf;
import blue.origami.nez.peg.expression.PLinkTree;
import blue.origami.nez.peg.expression.PMany;
import blue.origami.nez.peg.expression.PNonTerminal;
import blue.origami.nez.peg.expression.PNot;
import blue.origami.nez.peg.expression.POn;
import blue.origami.nez.peg.expression.POption;
import blue.origami.nez.peg.expression.PPair;
import blue.origami.nez.peg.expression.PSymbolAction;
import blue.origami.nez.peg.expression.PSymbolPredicate;
import blue.origami.nez.peg.expression.PSymbolScope;
import blue.origami.nez.peg.expression.PTag;
import blue.origami.nez.peg.expression.PTrap;
import blue.origami.nez.peg.expression.PTree;
import blue.origami.nez.peg.expression.PValue;
import blue.origami.util.OOption;
import blue.origami.util.OptionalFactory;

public class PEGPackratInterpreter
		extends ExpressionVisitor<Optional<PEGPackratInterpreter.ParseResult>, PEGPackratInterpreter.ParserContext>
		implements OptionalFactory<PEGPackratInterpreter> {

	public class ParserContext {
		final public String input;
		final public int length;
		public int pos;

		public int backtrack;

		ParserContext(String input) {
			this.input = input;
			this.length = this.input.length();
			this.pos = 0;
			this.backtrack = 0;
		}

		public boolean isEOF() {
			return this.pos >= this.length;
		}
	}

	public class ParseResult {
		final public String match;
		final public String rest;

		ParseResult(String match, String rest) {
			this.match = match;
			this.rest = rest;
		}
	}

	Map<String, Map<Integer, ParseResult>> memo;

	public int parse(Grammar g, String input) {
		ParserContext context = new ParserContext(input);
		this.memo = new HashMap<>();

		Optional<ParseResult> res = g.getStartProduction().getExpression().visit(this, context);
		if (res.isPresent()) {
			if (res.get().rest.isEmpty()) {
				System.out.println("Parse Successful");
			} else {
				System.out.println("Parse Parially Successful: (" + res.get().match + "," + res.get().rest + ")");
			}
			System.out.println("Backtrack: " + String.valueOf(context.backtrack));
			return context.backtrack;
		} else {
			System.out.println("Parse Error");
		}
		return 0;
	}

	@Override
	public Optional<ParseResult> visitNonTerminal(PNonTerminal e, ParserContext context) {
		System.out.println("visitNonTerminal: " + e.getLocalName());

		String exp = e.getLocalName();
		Integer pos = Integer.valueOf(context.pos);
		if (this.memo.containsKey(exp) && this.memo.get(exp).containsKey(pos)) {
			ParseResult res = this.memo.get(exp).get(pos);
			context.pos += res.match.length();
			return Optional.of(res);
		}

		Optional<ParseResult> res = e.getExpression().visit(this, context);
		if (res.isPresent()) {
			System.out.println("Match: " + e.getLocalName() + "(" + res.get().match + "," + res.get().rest + ")");

			if (!this.memo.containsKey(exp)) {
				this.memo.put(exp, new HashMap<Integer, ParseResult>());
			}
			Map<Integer, ParseResult> submemo = this.memo.get(exp);
			submemo.put(pos, res.get());
			this.memo.put(exp, submemo);
		}

		return res;
	}

	@Override
	public Optional<ParseResult> visitEmpty(PEmpty e, ParserContext context) {
		// System.out.println("visitEmpty");
		return Optional.of(new ParseResult("", context.input));
	}

	@Override
	public Optional<ParseResult> visitValue(PValue e, ParserContext context) {
		// System.out.println("visitValue: " + e.toString());
		String sub = context.input.substring(context.pos);

		if (sub.startsWith(e.value)) {
			context.pos += e.value.length();
			return Optional.of(new ParseResult(e.value, context.input.substring(context.pos)));
		} else {
			return Optional.empty();
		}
	}

	@Override
	public Optional<ParseResult> visitByte(PByte e, ParserContext context) {
		// System.out.println("visitByte: " + e.toString());

		if (context.isEOF()) {
			return Optional.empty();
		}

		char c = context.input.charAt(context.pos);
		return (e.byteChar() == c)
				? Optional.of(new ParseResult(String.valueOf(c), context.input.substring(++context.pos)))
				: Optional.empty();
	}

	@Override
	public Optional<ParseResult> visitByteSet(PByteSet e, ParserContext context) {
		// System.out.println("visitByteSet:" + e.toString());

		if (context.isEOF()) {
			return Optional.empty();
		}

		char c = context.input.charAt(context.pos);
		return (e.is(c)) ? Optional.of(new ParseResult(String.valueOf(c), context.input.substring(++context.pos)))
				: Optional.empty();
	}

	@Override
	public Optional<ParseResult> visitAny(PAny e, ParserContext context) {
		// System.out.println("visitAny");

		if (context.isEOF()) {
			return Optional.empty();
		}

		char c = context.input.charAt(context.pos++);
		return Optional.of(new ParseResult(String.valueOf(c), context.input.substring(context.pos)));
	}

	@Override
	public Optional<ParseResult> visitPair(PPair e, ParserContext context) {
		// System.out.println("visitPair: " + e.toString());

		Optional<ParseResult> leftResult = e.left.visit(this, context);
		if (!leftResult.isPresent()) {
			return Optional.empty();
		}

		Optional<ParseResult> rightResult = e.right.visit(this, context);
		if (!rightResult.isPresent()) {
			return Optional.empty();
		}

		String match = leftResult.get().match + rightResult.get().match;
		String rest = rightResult.get().rest;
		return Optional.of(new ParseResult(match, rest));
	}

	@Override
	public Optional<ParseResult> visitChoice(PChoice e, ParserContext context) {
		// System.out.println("visitChoice: " + e.toString());

		int start = context.pos;
		for (Expression ce : e.inners) {
			Optional<ParseResult> res = ce.visit(this, context);
			if (res.isPresent()) {
				return res;
			} else if (start != context.pos) {
				System.out.println("Backtrack: " + context.pos + "->" + start);
				context.pos = start;
				context.backtrack++;
			}
		}
		return Optional.empty();
	}

	@Override
	public Optional<ParseResult> visitOption(POption e, ParserContext context) {
		// System.out.println("visitOption: " + e.toString());

		Optional<ParseResult> res = e.get(0).visit(this, context);
		return (res.isPresent()) ? res : Optional.of(new ParseResult("", context.input.substring(context.pos)));
	}

	@Override
	public Optional<ParseResult> visitMany(PMany e, ParserContext context) {
		// System.out.println("visitMany: " + e.toString());

		Expression elem = e.get(0);
		StringBuilder match = new StringBuilder();
		String rest = context.input.substring(context.pos);

		int success = 0;
		Optional<ParseResult> res;
		while ((res = elem.visit(this, context)).isPresent()) {
			++success;
			match.append(res.get().match);
			rest = res.get().rest;
		}
		return (success >= e.min) ? Optional.of(new ParseResult(match.toString(), rest)) : Optional.empty();
	}

	@Override
	public Optional<ParseResult> visitAnd(PAnd e, ParserContext context) {
		// System.out.println("visitAnd: " + e.toString());

		int start = context.pos;
		Optional<ParseResult> res = e.get(0).visit(this, context);

		context.pos = start;
		return (res.isPresent()) ? Optional.of(new ParseResult("", context.input.substring(start))) : Optional.empty();
	}

	@Override
	public Optional<ParseResult> visitNot(PNot e, ParserContext context) {
		// System.out.println("visitNot: " + e.toString());

		int start = context.pos;
		Optional<ParseResult> res = e.get(0).visit(this, context);

		context.pos = start;
		return (!res.isPresent()) ? Optional.of(new ParseResult("", context.input.substring(start))) : Optional.empty();
	}

	//

	@Override
	public Optional<ParseResult> visitTree(PTree e, ParserContext context) {
		System.out.println("visitTree: " + e.toString());
		// if e.floding is true, then left folding
		return null;
	}

	@Override
	public Optional<ParseResult> visitLinkTree(PLinkTree e, ParserContext context) {
		System.out.println("visitLinkTree");
		return null;
	}

	@Override
	public Optional<ParseResult> visitTag(PTag e, ParserContext context) {
		System.out.println("visitTag");
		return null;
	}

	//

	@Override
	public Class<?> keyClass() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PEGPackratInterpreter clone() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void init(OOption options) {
		// TODO Auto-generated method stub
	}

	@Override
	public Optional<ParseResult> visitDetree(PDetree e, ParserContext context) {
		System.out.println("visitDetree");
		return null;
	}

	@Override
	public Optional<ParseResult> visitDispatch(PDispatch e, ParserContext context) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Optional<ParseResult> visitFail(PFail e, ParserContext context) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Optional<ParseResult> visitSymbolScope(PSymbolScope e, ParserContext context) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Optional<ParseResult> visitSymbolAction(PSymbolAction e, ParserContext context) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Optional<ParseResult> visitSymbolPredicate(PSymbolPredicate e, ParserContext context) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Optional<ParseResult> visitTrap(PTrap e, ParserContext context) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Optional<ParseResult> visitIf(PIf e, ParserContext context) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Optional<ParseResult> visitOn(POn e, ParserContext context) {
		// TODO Auto-generated method stub
		return null;
	}
}