package blue.origami.transpiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

import blue.origami.transpiler.code.TCode;
import blue.origami.util.OConsole;

public class TGenerator {
	protected SourceSection head;
	protected SourceSection data;
	protected SourceSection eval;
	// private SourceSection body = this.head;

	protected void setup() {
		this.head = new SourceSection();
		this.data = new SourceSection();
		this.eval = new SourceSection();
		this.secList = new ArrayList<>();
		this.secMap = new HashMap<>();
	}

	protected Object wrapUp() {
		System.out.println(this.head.toString());
		System.out.println(this.data.toString());
		for (SourceSection sec : this.secList) {
			System.out.println(sec);
		}
		return this.eval.toString();
	}

	public void emit(TEnv env, TCode code) {
		code.emitCode(env, this.eval);
	}

	public TCodeTemplate newConstTemplate(TEnv env, String lname, TType returnType) {
		String template = env.format("constname", "name", "%s", lname);
		return new TConstTemplate(lname, returnType, template);
	}

	public void defineConst(Transpiler env, boolean isPublic, String name, TType type, TCode expr) {
		this.data.pushIndentLine(env.format("const", "%1$s %2$s = %3$s", type.strOut(env), name, expr.strOut(env)));
	}

	private String currentFuncName = null;

	protected ArrayList<SourceSection> secList = new ArrayList<>();
	protected HashMap<String, SourceSection> secMap = new HashMap<>();

	public boolean isDefinedSection(String funcName) {
		return this.secMap.containsKey(funcName);
	}

	String getCurrentFuncName() {
		return this.currentFuncName;
	}

	public TCodeTemplate newFuncTemplate(TEnv env, String lname, TType returnType, TType... paramTypes) {
		String param = "";
		if (paramTypes.length > 0) {
			String delim = env.getSymbolOrElse(",", ",");
			StringBuilder sb = new StringBuilder();
			sb.append("%s");
			for (int i = 1; i < paramTypes.length; i++) {
				sb.append(delim);
				sb.append("%s");
			}
			param = sb.toString();
		}
		String template = env.format("funccall", "%s(%s)", lname, param);
		return new TCodeTemplate(lname, returnType, paramTypes, template);
	}

	public void defineFunction(TEnv env, boolean isPublic, String name, String[] paramNames, TType[] paramTypes,
			TType returnType, TCode code) {
		String params = "";
		if (paramTypes.length > 0) {
			String delim = env.getSymbolOrElse(",", ",");
			StringBuilder sb = new StringBuilder();
			sb.append(env.format("param", "%1$s %2$s", paramTypes[0].strOut(env), paramNames[0] + 0));
			for (int i = 1; i < paramTypes.length; i++) {
				sb.append(delim);
				sb.append(env.format("param", "%1$s %2$s", paramTypes[i].strOut(env), paramNames[i] + i));
			}
			params = sb.toString();
		}
		SourceSection sec = new SourceSection();
		env.setCurrentSourceSection(sec);
		this.secList.add(sec);
		this.secMap.put(name, sec);
		this.currentFuncName = name;
		sec.pushIndentLine(env.format("function", "%1$s %2$s(%3$s) {", returnType.strOut(env), name, params));
		sec.incIndent();
		sec.pushIndentLine(code.addReturn().strOut(env));
		sec.decIndent();
		sec.pushIndentLine(env.getSymbol("end function", "end", "}"));
		env.setCurrentSourceSection(null);
	}

	HashSet<String> crossRefNames = new HashSet<>();
	HashMap<String, HashSet<String>> depsMap = new HashMap<>();

	protected final void addFunctionDependency(String sour, String dest) {
		if (sour != null) {
			HashSet<String> set = this.depsMap.get(sour);
			if (set == null) {
				set = new HashSet<>();
				this.depsMap.put(sour, set);
			}
			set.add(dest);
		}
	}

	ArrayList<String> sortFuncList(String start) {
		class TopologicalSorter {
			private final HashMap<String, HashSet<String>> nodes;
			private final LinkedList<String> result;
			private final HashMap<String, Short> visited;
			private final Short Visiting = 1;
			private final Short Visited = 2;

			TopologicalSorter(HashMap<String, HashSet<String>> nodes) {
				this.nodes = nodes;
				this.result = new LinkedList<>();
				this.visited = new HashMap<>();
				for (Map.Entry<String, HashSet<String>> e : this.nodes.entrySet()) {
					if (this.visited.get(e.getKey()) == null) {
						this.visit(e.getKey(), e.getValue());
					}
				}
			}

			private void visit(String key, HashSet<String> nextNodes) {
				this.visited.put(key, this.Visiting);
				if (nextNodes != null) {
					for (String nextNode : nextNodes) {
						Short v = this.visited.get(nextNode);
						if (v == null) {
							this.visit(nextNode, this.nodes.get(nextNode));
						} else if (v == this.Visiting) {
							if (!key.equals(nextNode)) {
								// System.out.println("Cyclic " + key + " => " +
								// nextNode);
								TGenerator.this.crossRefNames.add(nextNode);
							}
						}
					}
				}
				this.visited.put(key, this.Visited);
				this.result.add(key);
			}

			public ArrayList<String> getResult() {
				return new ArrayList<>(this.result);
			}
		}
		TopologicalSorter sorter = new TopologicalSorter(this.depsMap);
		ArrayList<String> funcList = sorter.getResult();
		if (!funcList.contains(start)) {
			funcList.add(start);
		}
		// this.depsMap.clear();
		return funcList;
	}

	protected void log(String line, Object... args) {
		OConsole.println(line, args);
	}

}
