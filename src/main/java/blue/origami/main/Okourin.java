package blue.origami.main;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import blue.origami.common.CommonTree;
import blue.origami.common.OOption;
import blue.origami.parser.Parser;
import blue.origami.parser.peg.Grammar;

public class Okourin extends Main {
	@Override
	public void exec(OOption options) throws Throwable {
		Grammar g = this.getGrammar(options, "chibi.opeg");

		StreamCombinator combinator = new StreamCombinator(g);
		String res = combinator.of("ary").take("100").filter("\\x x<5").map("\\x x+1").take("10")
				.reduce("\\x \\y x + y", "10");
		System.out.println(res);
	}

	class StreamCombinator {
		private final String NT_LAMBDA = "FuncExpr";
		private final String NT_ARRAY_ACCESS = "PostExpr";
		private final String NT_IDENTIFIER = "Identifier";
		private final String NT_EXPRESSION = "Expression";

		private final Grammar _g;
		private final String _prefix;
		private final String _loopVar;
		private final String _valueVar;
		private final String _constVar;
		private final String _resVar;

		private String _sourceName;
		private boolean _isFilterContinue = false;
		private List<CommonTree> _values;
		private List<List<CommonTree>> _conditions;
		private List<Set<String>> _limits;

		private StringBuilderWrapper _builder;

		StreamCombinator(Grammar g, String prefix, String loopVar, String valueVar, String constVar, String resVar) {
			this._g = g;
			this._prefix = prefix;
			this._loopVar = loopVar;
			this._valueVar = valueVar;
			this._constVar = constVar;
			this._resVar = resVar;
		}

		StreamCombinator(Grammar g) {
			this(g, "_", "i", "x", "c", "r");
		}

		private void init() {
			this._sourceName = null;
			this._values = new ArrayList();
			this._conditions = new ArrayList();
			this._limits = new ArrayList();
			this._isFilterContinue = false;
		}

		public StreamCombinator of(String ide) throws Exception {
			this.init();
			this._sourceName = ide;

			final String aryAccess = this.getArrayAccess(this._sourceName, this.getLoopVar(String.valueOf(0)));
			final CommonTree tree = this.parseTree(aryAccess, this.NT_ARRAY_ACCESS);
			if (tree == null || !tree.getTag().toString().equals("IndexExpr")) {
				throw new IllegalArgumentException("Parse Error ( Not Array Access ) : " + aryAccess);
			}
			this._values.add(tree);

			final String arySize = this.getArraySize(this._sourceName);
			this.setLimit(0, arySize);

			this._isFilterContinue = false;
			return this;
		}

		public StreamCombinator map(final String lambda) throws Exception {
			CommonTree tree = this.parseTree(lambda, this.NT_LAMBDA);
			if (tree == null) {
				throw new IllegalArgumentException("Parse Error ( Not Lambda ) : " + lambda);
			}
			final HashSet<String> vars = this.getLambdaArgs(tree);
			final CommonTree body = this.getLambdaBody(tree);
			if (vars.size() != 1) {
				throw new IllegalArgumentException("Bound Variables ara too much : " + vars.toString());
			}

			final CommonTree boundVar = this.parseTree(vars.iterator().next(), this.NT_IDENTIFIER);
			final CommonTree oldTree = this._values.get(this._values.size() - 1);
			final CommonTree newTree = this.embedTree(body, boundVar, oldTree);
			this._values.set(this._values.size() - 1, newTree);

			this._isFilterContinue = false;
			return this;
		}

		public StreamCombinator filter(final String lambda) throws Exception {
			final CommonTree tree = this.parseTree(lambda, this.NT_LAMBDA);
			if (tree == null) {
				throw new IllegalArgumentException("Parse Error ( Not Lambda ) : " + lambda);
			}
			final HashSet<String> vars = this.getLambdaArgs(tree);
			final CommonTree body = this.getLambdaBody(tree);
			if (vars.size() != 1) {
				throw new IllegalArgumentException("Bound Variables ara too much : " + vars.toString());
			}

			final int level = this._isFilterContinue ? this._values.size() - 2 : this._values.size() - 1;
			final String boundVarName = vars.iterator().next();
			final CommonTree boundVar = this.parseTree(boundVarName, this.NT_IDENTIFIER);
			final String tempVarName = this.getValueVar(String.valueOf(level));
			final CommonTree tempVar = this.parseTree(tempVarName, this.NT_IDENTIFIER);

			if (!this._isFilterContinue) {
				CommonTree nextVar = this.parseTree(this.getValueVar(String.valueOf(level - 1)), this.NT_IDENTIFIER);
				CommonTree oldTree = this._values.get(level);
				oldTree = this.embedTree(oldTree, nextVar, tempVar);
				this._values.set(level, oldTree);

				this._values.add(tempVar);
			}
			final CommonTree cond = this.embedTree(body, boundVar, tempVar);
			this.setCond(level, body);

			this._isFilterContinue = true;
			return this;
		}

		public StreamCombinator take(final String expr) {
			CommonTree tree = this.parseTree(expr, this.NT_EXPRESSION);
			if (tree == null) {
				throw new IllegalArgumentException("Parse Error ( Not Expression ) : " + expr);
			}
			this.setLimit(this._conditions.size(), expr);

			this._isFilterContinue = false;
			return this;
		}

		public String forEach(final String expr) throws Exception {
			CommonTree tree = this.parseTree(expr, this.NT_LAMBDA);
			tree = (tree != null) ? tree : this.parseTree(expr, this.NT_IDENTIFIER);
			if (tree == null) {
				throw new IllegalArgumentException("Parse Error ( Not Lambda and Not Identifier ) : " + expr);
			}

			int depth = this._values.size();

			this._builder = new StringBuilderWrapper();

			this.genPre();
			this.genBody();

			final String tag = tree.getTag().toString();
			final CommonTree lastValue = this._values.get(this._values.size() - 1);

			if (tag.equals("FuncExpr")) {
				final HashSet<String> boundVars = this.getLambdaArgs(tree);
				if (boundVars.size() != 1) {
					throw new IllegalArgumentException("Too Much Bound Vars : " + expr);
				}
				final String boundVarName = boundVars.iterator().next();
				final CommonTree boundVar = this.parseTree(boundVarName, this.NT_IDENTIFIER);
				tree = this.embedTree(tree.get(1), boundVar, lastValue);

				final String forEachBody = this.evalTree(tree);
				this._builder.appendLineWithTabs(depth, forEachBody);
			} else {
				final String funcCall = expr + this.evalTree(lastValue) + ";";
				this._builder.appendLineWithTabs(depth, funcCall);
			}

			while (depth-- > 0) {
				this._builder.appendLineWithTabs(depth, "}");
			}

			return this._builder.toString();
		}

		public String reduce(final String lambda, final String init) {
			final CommonTree tree = this.parseTree(lambda, this.NT_LAMBDA);
			if (tree == null) {
				throw new IllegalArgumentException("Parse Error ( Not Lambda ) : " + lambda);
			}

			int depth = this._values.size();

			this._builder = new StringBuilderWrapper();

			final String r0 = this.getResVar("0");
			final String initResVar = r0 + " = " + init + ";";
			this._builder.appendLineWithTabs(0, initResVar);

			this.genPre();
			this.genBody();

			final HashSet<String> boundVars = this.getLambdaArgs(tree);
			CommonTree body = this.getLambdaBody(tree);
			if (boundVars.size() != 2) {
				throw new IllegalArgumentException("Need 2 bound variabls: " + lambda);
			}

			Iterator<String> ite = boundVars.iterator();
			final CommonTree firstVar = this.parseTree(ite.next(), this.NT_IDENTIFIER);
			final CommonTree secondVar = this.parseTree(ite.next(), this.NT_IDENTIFIER);

			final CommonTree resVar = this.parseTree(r0, this.NT_IDENTIFIER);
			final CommonTree lastValue = this._values.get(this._values.size() - 1);

			body = this.embedTree(body, firstVar, resVar);
			body = this.embedTree(body, secondVar, lastValue);

			final String updateResVar = r0 + " = " + this.removeOuterBracket(this.evalTree(body)) + ";";
			this._builder.appendLineWithTabs(depth, updateResVar);

			while (depth-- > 0) {
				this._builder.appendLineWithTabs(depth, "}");
			}

			return this._builder.toString();
		}

		private String getLoopLimit(String varName) {
			Iterator<String> ite = this._limits.get(0).iterator();
			String limVal = ite.next();
			while (ite.hasNext()) {
				limVal = this.getMinFunc(limVal, ite.next());
			}
			return varName + " = " + limVal + ";";
		}

		private void genPre() {
			final String c0 = this.getConstVar("0");
			final String i0 = this.getLoopVar("0");
			this._builder.appendLineWithTabs(0, this.getLoopLimit(c0));
			this._builder.appendLineWithTabs(0, this.getForLoop(i0, "0", c0) + "{");
		}

		private void genBody() {
			final int depth = this._values.size() - 1;
			this.setCond(depth, null);
			this.setLimit(depth + 1, null);

			int k = 1;
			String termination = "";
			for (int i = 0; i < depth; ++i) {
				CommonTree valueTree = this._values.get(i);
				String value = this.removeOuterBracket(this.evalTree(valueTree));
				String varName = this.getValueVar(String.valueOf(i));

				// assign
				final String assignExpr = varName + " = " + value + ";";
				this._builder.appendLineWithTabs(i + 1, assignExpr);

				// termination
				if (termination != null && termination.length() > 0) {
					this._builder.appendLineWithTabs(i + 1, termination);
					termination = null;
				}

				// next termination
				final Set<String> nextLimit = this._limits.get(i + 1);
				if (!nextLimit.isEmpty()) {
					Iterator<String> ite = nextLimit.iterator();
					String limVal = ite.next();
					while (ite.hasNext()) {
						limVal = this.getMinFunc(limVal, ite.next());
					}
					final String limVarName = this.getLoopVar(String.valueOf(k++));

					final String nextControlInit = limVarName + " = " + limVal + ";";
					this._builder.appendLineWithTabs(i + 1, nextControlInit);

					final String nextLimitExpr = limVarName + " > 0";
					CommonTree nextLimitCond = this.parseTree(nextLimitExpr, this.NT_EXPRESSION);
					this.setCond(i, nextLimitCond);

					termination += limVarName + "--;";
				}

				// if
				final List<CommonTree> conds = this._conditions.get(i);
				if (conds != null && conds.size() > 0) {
					String comb = this.getConditionCombinate(conds);
					final String ifexpr = "if( " + comb + " ){";
					this._builder.appendLineWithTabs(i + 1, ifexpr);
				}
			}
			// termination
			if (termination != null && termination.length() > 0) {
				this._builder.appendLineWithTabs(depth + 1, termination);
			}
		}

		// Utility

		private CommonTree parseTree(String str, String nonterminal) {
			final Parser parser = this._g.newParser(nonterminal);
			CommonTree tree = null;
			try {
				tree = (CommonTree) parser.parse(str);
			} catch (IOException e) {
			}
			return tree;
		}

		private HashSet<String> getLambdaArgs(CommonTree tree) {
			HashSet<String> args = new HashSet();
			for (int i = 0; i < tree.size(); ++i) {
				CommonTree subTree = tree.get(i);
				if (tree.getLabel(i).toString().equals("param")) {
					CommonTree subtree = tree.get(i);
					for (int j = 0; j < subtree.size(); ++j) {
						String varName = subtree.get(j).get(0).getString();
						if (varName != null && varName.length() > 0) {
							args.add(varName);
						}
					}
				}
			}
			return args;
		}

		private CommonTree getLambdaBody(CommonTree tree) {
			for (int i = 0; i < tree.size(); ++i) {
				CommonTree subTree = tree.get(i);
				if (tree.getLabel(i).toString().equals("body")) {
					return tree.get(i);
				}
			}
			return null;
		}

		// private CommonTree embedTree(final CommonTree parent, final String name,
		// final CommonTree child) {
		// Deque<CommonTree> st = new ArrayDeque();
		// st.addFirst(parent);
		//
		// while (!st.isEmpty()) {
		// final CommonTree tree = st.removeFirst();
		//
		// for (int i = 0; i < tree.size(); ++i) {
		// CommonTree sub = tree.get(i);
		// if (sub.getTag() != null && sub.getTag().toString().equals("NameExpr")
		// && sub.getString().equals(name)) {
		// tree.set(i, child);
		// } else {
		// st.addFirst(sub);
		// }
		// }
		// }
		// return parent;
		// }

		private CommonTree embedTree(final CommonTree parent, final CommonTree from, final CommonTree to) {
			Deque<CommonTree> st = new ArrayDeque();
			st.addFirst(parent);

			while (!st.isEmpty()) {
				final CommonTree tree = st.removeFirst();

				for (int i = 0; i < tree.size(); ++i) {
					CommonTree sub = tree.get(i);
					if (sub.getTag() == from.getTag() && sub.getString().equals(from.getString())) {
						tree.set(i, to);
					} else {
						st.addFirst(sub);
					}
				}
			}
			return parent;
		}

		private String evalTree(final CommonTree tree) {
			if (tree.getTag() == null) {
				return this.evalTree(tree.get(0));
			}
			switch (tree.getTag().toString()) {
			case "AddExpr":
				return "(" + this.evalTree(tree.get(0)) + " + " + this.evalTree(tree.get(1)) + ")";
			case "SubExpr":
				return "(" + this.evalTree(tree.get(0)) + " - " + this.evalTree(tree.get(1)) + ")";
			case "MulExpr":
				return "(" + this.evalTree(tree.get(0)) + " * " + this.evalTree(tree.get(1)) + ")";
			case "DivExpr":
				return "(" + this.evalTree(tree.get(0)) + " / " + this.evalTree(tree.get(1)) + ")";
			case "InfixExpr":
				return "(" + this.evalTree(tree.get(0)) + " " + this.evalTree(tree.get(1)) + " "
						+ this.evalTree(tree.get(2)) + ")";
			case "NameExpr":
				return tree.getString();
			case "IntExpr":
				return tree.getString();
			case "IndexExpr":
				final String ary = this.evalTree(tree.get(0));
				final String ind = this.evalTree(tree.get(1));
				return this.getArrayAccess(ary, ind);
			case "ApplyExpr":
				final String name = this.evalTree(tree.get(0));
				final String args = this.evalTree(tree.get(1));
				return this.getFuncCall(name, args);
			default:
				return this.evalTree(tree.get(0));
			}
		}

		private String getConditionCombinate(List<CommonTree> list) {
			StringBuilder sb = new StringBuilder();
			int len = list.size();
			for (int i = 0; i < len; ++i) {
				if (i != 0) {
					sb.append(" && ");
				}
				sb.append(this.evalTree(list.get(i)));
			}
			return sb.toString();
		}

		private void setLimit(int level, String str) {
			for (int i = this._limits.size(); i <= level; ++i) {
				this._limits.add(new HashSet());
			}
			if (str != null) {
				Set<String> limit = this._limits.get(level);
				limit.add(str);
				this._limits.set(level, limit);
			}
		}

		private void setCond(int level, CommonTree tree) {
			for (int i = this._conditions.size(); i <= level; ++i) {
				this._conditions.add(new ArrayList());
			}
			if (tree != null) {
				List<CommonTree> list = this._conditions.get(level);
				list.add(tree);
				this._conditions.set(level, list);
			}
		}

		// template
		private String getLoopVar(String s) {
			return String.format("%s%s%s", this._prefix, this._loopVar, s);
		}

		private String getValueVar(String s) {
			return String.format("%s%s%s", this._prefix, this._valueVar, s);
		}

		private String getConstVar(String s) {
			return String.format("%s%s%s", this._prefix, this._constVar, s);
		}

		private String getResVar(String s) {
			return String.format("%s%s%s", this._prefix, this._resVar, s);
		}

		private String getMinFunc(String l, String r) {
			return String.format("std::min(%s,%s)", l, r);
		}

		private String getForLoop(String var, String init, String limit) {
			return String.format("for( %s = %s; %s < %s; %s++ )", var, init, var, limit, var);
		}

		private String getArrayAccess(String ary, String var) {
			return String.format("%s[%s]", ary, var);
		}

		private String getArraySize(String ary) {
			return String.format("%s.size()", ary);
		}

		private String getFuncCall(String name, String args) {
			return String.format("%s(%s)", name, args);
		}

		private String removeOuterBracket(String str) {
			if (str.startsWith("(") && str.endsWith(")")) {
				str = str.substring(1, str.length() - 1);
			}
			return str;
		}
	}

	public class StringBuilderWrapper {
		private StringBuilder _builder;
		private final String _EOL;

		StringBuilderWrapper() {
			this._builder = new StringBuilder();
			this._EOL = System.getProperty("line.separator");
		}

		@Override
		public String toString() {
			return this._builder.toString();
		}

		public void append(Object obj) {
			this._builder.append(obj.toString());
		}

		public void appendLine(Object obj) {
			this.append(obj);
			this._builder.append(this._EOL);
		}

		public void appendLineWithTabs(int n, Object obj) {
			while (n-- > 0) {
				this._builder.append("\t");
			}
			this.appendLine(obj);
		}

	}
}
