package blue.origami.transpiler.rule;

import blue.origami.nez.ast.Tree;
import blue.origami.rule.OFmt;
import blue.origami.transpiler.TEnv;
import blue.origami.transpiler.code.TCode;
import blue.origami.transpiler.code.TErrorCode;

public class NameExpr implements TTypeRule {

	@Override
	public TCode apply(TEnv env, Tree<?> t) {
		String name = t.getString();
		TNameRef ref = env.get(name, TNameRef.class, (e, c) -> e.isNameRef(env) ? e : null);
		if (ref == null) {
			throw new TErrorCode(t, OFmt.undefined_name__YY0, name);
		}
		return ref.nameCode(env, name);
	}

	public interface TNameRef {
		public boolean isNameRef(TEnv env);

		public TCode nameCode(TEnv env, String name);
	}

}
