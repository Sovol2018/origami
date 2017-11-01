package blue.origami.transpiler;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import blue.origami.asm.AsmMapper;
import blue.origami.common.OConsole;
import blue.origami.common.ODebug;
import blue.origami.common.OFactory;
import blue.origami.common.OOption;
import blue.origami.common.OSource;
import blue.origami.common.OStrings;
import blue.origami.main.MainOption;
import blue.origami.parser.Parser;
import blue.origami.parser.ParserCode.ParserErrorException;
import blue.origami.parser.ParserSource;
import blue.origami.parser.peg.Grammar;
import blue.origami.parser.peg.SourceGrammar;
import blue.origami.transpiler.code.Code;
import blue.origami.transpiler.code.ErrorCode;
import blue.origami.transpiler.target.SourceMapper;
import blue.origami.transpiler.target.SourceTypeMapper;
import blue.origami.transpiler.type.Ty;
import blue.origami.transpiler.type.VarDomain;

public class Transpiler extends Env implements OFactory<Transpiler> {
	private CodeLoader cloader;
	private CodeMapper cmapper;

	boolean isFriendly = true;

	public Transpiler(Grammar g, Parser p) {
		super(null);
		this.initMe(g, p, new Language());
	}

	public void initMe(Grammar g, Parser p, Language lang) {
		this.lang = lang;
		this.add(Grammar.class, g);
		this.add(Parser.class, p);
		this.lang.initMe(this);
		this.cloader = new CodeLoader(this);
		this.cmapper = this.getCodeMapper();
		this.cloader.load(lang.getLangName() + ".codemap");
		this.cmapper.init();
	}

	public Transpiler() {
		super(null);
	}

	@Override
	public final Class<?> keyClass() {
		return Transpiler.class;
	}

	@Override
	public final Transpiler clone() {
		return this.newClone();
	}

	@Override
	public void init(OOption options) {
		try {
			String file = options.stringValue(MainOption.GrammarFile, "konoha5.opeg");
			Grammar g = SourceGrammar.loadFile(file, options.stringList(MainOption.GrammarPath));
			Parser p = g.newParser(options);
			this.initMe(g, p, options.newInstance(Language.class));
		} catch (IOException e) {
			OConsole.exit(1, e);
		}

	}

	public String getTargetName() {
		Class<?> c = this.getClass();
		return (c == Transpiler.class) ? "jvm" : c.getSimpleName();
	}

	public CodeMapper getCodeMapper() {
		Class<?> c = this.getClass();
		return (c == Transpiler.class) ? new AsmMapper(this) : new SourceMapper(this, new SourceTypeMapper(this));
	}

	public String getPath(String file) {
		return this.cloader.getPath(file);
	}

	public boolean loadScriptFile(String path) throws IOException {
		return this.loadScriptFile(ParserSource.newFileSource(path, null));
	}

	public void eval(String script) {
		this.eval("<unknown>", 1, script);
	}

	public void eval(String source, int line, String script) {
		this.loadScriptFile(ParserSource.newStringSource(source, line, script));
	}

	public boolean loadScriptFile(OSource sc) {
		try {
			this.emitCode(this, sc);
			return true;
		} catch (Throwable e) {
			this.showThrowable(e);
			return false;
		}
	}

	public void testScriptFile(OSource sc) throws Throwable {
		this.emitCode(this, sc);
	}

	public void verboseError(String msg, Runnable p) {
		OConsole.beginColor(OConsole.Red);
		OConsole.print("[" + msg + "] ");
		p.run();
		OConsole.endColor();
	}

	void showThrowable(Throwable e) {
		if (e instanceof Error) {
			OConsole.exit(1, e);
			return;
		}
		if (e instanceof ParserErrorException) {
			this.verboseError(TFmt.ParserError.toString(), () -> {
				OConsole.println(e);
			});
			return;
		}
		if (e instanceof InvocationTargetException) {
			this.showThrowable(((InvocationTargetException) e).getTargetException());
			return;
		}
		if (e instanceof ErrorCode) {
			this.verboseError("Error", () -> {
				OConsole.println(((ErrorCode) e).getLog());
			});
		} else {
			this.verboseError("RuntimeException", () -> {
				e.printStackTrace();
			});
		}
	}

	void emitCode(Env env0, OSource sc) throws Throwable {
		Parser p = env0.get(Parser.class);
		AST t = (AST) p.parse(sc, 0, AST.TreeFunc, AST.TreeFunc);
		ODebug.showBlue(TFmt.Syntax_Tree, () -> {
			OConsole.println(t);
		});
		this.cmapper.setup();
		FuncEnv env = env0.newFuncEnv(); //
		Code code = env.parseCode(env, t).asType(env, Ty.tUntyped());
		if (code.getType().isAmbigous()) {
			code = new ErrorCode(code, TFmt.ambiguous_type__S, code.getType());
		}
		if (code.showError(env)) {
			return;
		}
		this.cmapper.emitTopLevel(env, code);
		Object result = this.cmapper.wrapUp();
		if (this.cmapper.isExecutable() && code.getType() != Ty.tVoid) {
			StringBuilder sb = new StringBuilder();
			sb.append("(");
			sb.append(OConsole.t(code.getType().memoed().toString()));
			sb.append(") ");
			OConsole.beginBold(sb);
			OStrings.appendQuoted(sb, result);
			OConsole.endBold(sb);
			OConsole.println(sb.toString());
		}
	}

	public Code testCode(String text) throws Throwable {
		OSource sc = ParserSource.newStringSource("<test>", 1, text);
		Parser p = this.get(Parser.class);
		AST t = (AST) p.parse(sc, 0, AST.TreeFunc, AST.TreeFunc);
		this.cmapper.setup();
		FuncEnv env = this.newFuncEnv(); //
		return this.parseCode(env, t).asType(env, Ty.tUntyped());
	}

	public Ty testType(String s) throws Throwable {
		return VarDomain.eliminateVar(this.testCode(s).getType().memoed());
	}

	public Object testEval(String s) throws Throwable {
		Code code = this.testCode(s);
		this.cmapper.emitTopLevel(this, code);
		return this.cmapper.wrapUp();
	}

	// Buffering

	public void addFunction(Env env, String name, FuncMap f) {
		this.add(name, f);
		if (f.isPublic()) {
			this.cmapper.addFunction(name, f);
		}
	}

	public void addExample(String name, AST tree) {
		if (tree.is("MultiExpr")) {
			for (AST t : tree) {
				this.cmapper.addExample(name, t);
			}
		} else {
			this.cmapper.addExample(name, tree);
		}
	}

	// ConstDecl

	int functionId = 1000;

	public CodeMap defineConst(boolean isPublic, String name, Ty type, Code expr) {
		Env env = this.newEnv();
		String lname = isPublic ? name : this.getLocalName(name);
		CodeMap tp = this.cmapper.newConstMap(env, lname, type);
		this.add(name, tp);
		this.cmapper.defineConst(this, isPublic, lname, type, expr);
		return tp;
	}

	private String getLocalName(String name) {
		String prefix = "v" + (this.functionId++); // this.getSymbol(name);
		return prefix + NameHint.safeName(name);
	}

	// FuncDecl

	public CodeMap newCodeMap(String name, Ty returnType, Ty... paramTypes) {
		final String lname = this.cmapper.safeName(name);
		return this.cmapper.newCodeMap(this, name, lname, returnType, paramTypes);
	}

	public CodeMap defineFunction2(boolean isPublic, String name, String nameId, AST[] paramNames, Ty[] paramTypes,
			Ty returnType, Code body) {
		final CodeMap cmap = this.cmapper.newCodeMap(this, name, nameId, returnType, paramTypes);
		this.addCodeMap(name, cmap);
		final FuncEnv env = this.newFuncEnv(nameId, paramNames, paramTypes, returnType);
		Code code = env.typeCheck(body);
		if (code.showError(this)) {
			return cmap; // FIXME
		}
		this.cmapper.defineFunction(this, isPublic, nameId, AST.names(paramNames), cmap.getParamTypes(),
				cmap.getReturnType(), code);
		return cmap;
	}

	// public CodeMap defineFunction(String name, AST[] paramNames, Ty[] paramTypes,
	// Ty returnType, Code body) {
	// final Env env = this.newEnv();
	// final String lname = this.cmapper.safeName(name);
	// final CodeMap tp = this.cmapper.newCodeMap(env, name, lname, returnType,
	// paramTypes);
	// this.add(name, tp);
	// FunctionContext fcx = new FunctionContext(null);
	// FuncUnit fu = FuncUnit.wrap(null, paramNames, tp);
	// Code code = fu.typeCheck(env, fcx, null, body);
	// this.cmapper.defineFunction(this, false, lname, AST.names(paramNames),
	// tp.getParamTypes(), tp.getReturnType(),
	// code);
	// return tp;
	// }
	//
	// public CodeMap defineFunction(boolean isPublic, AST aname, int seq, AST[]
	// paramNames, Ty[] paramTypes,
	// Ty returnType, VarDomain dom, Code code0) {
	// final String name = aname.getString();
	// final String lname = isPublic ? name : this.getLocalName(name);
	// final CodeMap tp = this.cmapper.newCodeMap(this, name, lname, returnType,
	// paramTypes);
	// this.add(name, tp);
	// FunctionContext fcx = new FunctionContext(null);
	// FuncUnit fu = FuncUnit.wrap(aname, paramNames, tp);
	//
	// Code code = fu.typeCheck(this, fcx, dom, code0);
	// if (code.showError(this)) {
	// return tp;
	// }
	// this.cmapper.defineFunction(this, isPublic, lname, AST.names(paramNames),
	// tp.getParamTypes(),
	// tp.getReturnType(), code);
	// return tp;
	// }
	//
	// public CodeMap defineFunction(boolean isPublic, AST name, int seq, AST[]
	// paramNames, Ty[] paramTypes, Ty returnType,
	// VarDomain dom, AST body) {
	// return this.defineFunction(isPublic, name, seq, paramNames, paramTypes,
	// returnType, dom,
	// this.parseCode(this, body));
	// }

	private boolean shellMode = false;

	public boolean isShellMode() {
		return this.shellMode;
	}

	public void setShellMode(boolean shellMode) {
		this.shellMode = shellMode;
	}

	public void defineSyntax(String key, String value) {
		this.cmapper.defineSyntax(key, value);
	}

}
