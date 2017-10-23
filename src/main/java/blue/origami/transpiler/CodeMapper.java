package blue.origami.transpiler;

import java.util.ArrayList;
import java.util.HashMap;

import blue.origami.transpiler.code.Code;
import blue.origami.transpiler.code.CodeBuilder;
import blue.origami.transpiler.code.ExprCode;
import blue.origami.transpiler.code.MultiCode;
import blue.origami.transpiler.type.Ty;

public abstract class CodeMapper implements CodeBuilder {
	// protected boolean isVerbose = false;
	//
	// public void setVerbose(boolean debug) {
	// this.isVerbose = debug;
	// }
	//
	// public boolean isVerbose() {
	// return this.isVerbose;
	// }

	protected ArrayList<AST> exampleList = null;
	protected ArrayList<FuncMap> funcList = null;

	public abstract void init();

	protected void setup() {
		this.funcList = null;
		this.exampleList = null;
	}

	protected abstract Object wrapUp();

	public abstract void emitTopLevel(TEnv env, Code code);

	public String getUniqueConstName(String name) {
		return name;
	}

	public abstract CodeMap newConstMap(TEnv env, String lname, Ty ret);

	public abstract void defineConst(Transpiler env, boolean isPublic, String name, Ty type, Code expr);

	HashMap<String, Integer> arrowMap = new HashMap<>();

	public final String safeName(String name) {
		if (name.indexOf("->") > 0) {
			Integer n = this.arrowMap.get(name);
			if (n == null) {
				n = this.arrowMap.size();
				this.arrowMap.put(name, n);
			}
			return "c0nv" + n;
		}
		return name;
	}

	public String getUniqueFuncName(String name, Ty... paramTypes) {
		return name;
	}

	public abstract CodeMap newCodeMap(TEnv env, String sname, String lname, Ty returnType, Ty... paramTypes);

	public abstract void defineFunction(TEnv env, boolean isPublic, String name, String[] paramNames, Ty[] paramTypes,
			Ty returnType, Code code);

	public void addFunction(String name, FuncMap f) {
		if (f.isPublic) {
			if (this.funcList == null) {
				this.funcList = new ArrayList<>(0);
			}
			this.funcList.add(f);
		}
	}

	public void addExample(String name, AST t) {
		if (this.exampleList == null) {
			this.exampleList = new ArrayList<>(0);
		}
		this.exampleList.add(t);
	}

	public Code emitHeader(TEnv env, Code code) {
		if (this.funcList != null) {
			for (FuncMap f : this.funcList) {
				if (f.isExpired()) {
					continue;
				}
				f.generate(env);
			}
			this.funcList = null;
		}
		if (!code.isGenerative() && this.exampleList != null) {
			ArrayList<Code> asserts = new ArrayList<>();
			for (AST t : this.exampleList) {
				Code body = env.parseCode(env, t).asType(env, Ty.tBool);
				asserts.add(new ExprCode("assert", body));
			}
			code = new MultiCode(asserts.toArray(new Code[asserts.size()])).asType(env, Ty.tVoid);
			this.exampleList = null;
		}
		return code;
	}

	// public CodeMap genTestBinFunc(TEnv env, String op, Code right) {
	// Transpiler tr = env.getTranspiler();
	// Ty ty = right.getType();
	// String[] names = { "a", "b" };
	// Ty[] params = { ty, ty };
	// Code name = new NameCode("a", 0, ty, 0);
	// MultiCode body = new MultiCode(new ExprCode("assert", new BinaryCode(op,
	// name, new NameCode("b", 1, ty, 0))),
	// name);
	// return tr.defineFunction("test" + op + ty, names, params, ty, body);
	// }
	//
	// public abstract String arrowName(Ty fromTy, Ty toTy);

	// public Template genFuncConvFunc(TEnv env, FuncTy fromTy, FuncTy toTy) {
	// Transpiler tr = env.getTranspiler();
	// String[] names = { "_f" };
	// Ty[] params = { fromTy };
	//
	// Ty[] fromTypes = fromTy.getParamTypes();
	// Ty[] toTypes = toTy.getParamTypes();
	// String[] fnames = TArrays.names(toTypes.length);
	// List<Code> l = new ArrayList<>();
	// l.add(new NameCode("_f"));
	// for (int c = 0; c < toTy.getParamSize(); c++) {
	// l.add(new CastCode(fromTypes[c], new NameCode(String.valueOf((char)
	// c))));
	// }
	// Code body = new CastCode(toTy.getReturnType(), new ApplyCode(l));
	// body = new FuncCode(fnames, fromTypes, toTy.getReturnType(), body);
	// return tr.defineFunction(FuncTy.mapKey(fromTy, toTy), names, params,
	// toTy, body);
	// }

}