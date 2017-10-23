package blue.origami.parser.peg;

/**
 * The Expression.Fail represents a failure expression, denoted !'' in
 * Expression.
 * 
 * @author kiki
 *
 */

public class PFail extends PTerm {
	public PFail() {
	}

	@Override
	public final <V, A> V visit(ExpressionVisitor<V, A> v, A a) {
		return v.visitFail(this, a);
	}

	@Override
	public void strOut(StringBuilder sb) {
		sb.append("!''");
	}

}