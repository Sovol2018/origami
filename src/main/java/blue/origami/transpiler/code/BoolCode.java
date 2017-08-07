package blue.origami.transpiler.code;

import blue.origami.transpiler.TCodeSection;
import blue.origami.transpiler.TEnv;
import blue.origami.transpiler.Ty;
import blue.origami.transpiler.Template;

public class BoolCode extends CommonCode implements ValueCode {
	private boolean value;

	public BoolCode(boolean value) {
		super(Ty.tBool);
		this.value = value;
	}

	@Override
	public Object getValue() {
		return this.value;
	}

	@Override
	public Template getTemplate(TEnv env) {
		return env.get(this.value ? "true:Bool" : "false:Bool", Template.class);
	}

	@Override
	public void emitCode(TEnv env, TCodeSection sec) {
		sec.pushBool(env, this);
	}

}