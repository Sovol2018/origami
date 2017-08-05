package blue.origami.transpiler.code;

import blue.origami.nez.ast.Tree;
import blue.origami.transpiler.TCodeSection;
import blue.origami.transpiler.TEnv;
import blue.origami.transpiler.TLog;
import blue.origami.transpiler.Template;
import blue.origami.util.StringCombinator;

public class TLogCode extends Code1 {
	protected TLog log;

	public TLogCode(TLog log, TCode inner) {
		super(AutoType, inner);
		this.log = log;
	}

	public TLog getLog() {
		return this.log;
	}

	@Override
	public TCode setSource(Tree<?> t) {
		this.inner.setSource(t);
		this.log.setSourcePosition(t);
		return this;
	}

	@Override
	public Template getTemplate(TEnv env) {
		return env.getTemplate("%s", "%s");
	}

	@Override
	public String strOut(TEnv env) {
		env.reportLog(this.log);
		return this.inner.strOut(env);
	}

	@Override
	public void strOut(StringBuilder sb) {
		StringCombinator.append(sb, this.getInner());
	}

	@Override
	public void emitCode(TEnv env, TCodeSection sec) {
		sec.pushLog(env, this);
	}

}