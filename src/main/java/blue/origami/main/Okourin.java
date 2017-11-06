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
		System.out.println("Hello, world");
		Grammar g = this.getGrammar(options, "chibi.opeg");

		StreamCombinator combinator = new StreamCombinator(g);
		String res = combinator.of("a").take("4").map("\\x x + 1").filter("\\x x<3").take("5").filter("\\x x<5")
				.forEach("\\x println(x + 1)");
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

			final String varName = vars.iterator().next();
			final CommonTree oldTree = this._values.get(this._values.size() - 1);
			final CommonTree newTree = this.embedTree(body, varName, oldTree);
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
			final String varName = vars.iterator().next();
			final String tempVarName = this.getValueVar(String.valueOf(level));
			final CommonTree tempVar = this.parseTree(tempVarName, this.NT_IDENTIFIER);

			if (!this._isFilterContinue) {
				CommonTree oldTree = this._values.get(level);
				oldTree = this.embedTree(oldTree, this.getValueVar(String.valueOf(level - 1)), tempVar);
				this._values.set(level, oldTree);

				this._values.add(tempVar);
			}
			final CommonTree cond = this.embedTree(body, varName, tempVar);
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

			final int depth = this._values.size();

			StringBuilder sb = new StringBuilder();
			sb.append(this.getLoopLimit());
			String loopVar = this.getLoopVar("0");
			sb.append(this.getForLoop(loopVar + " = 0", loopVar + " < " + this.getConstVar("0"), "++" + loopVar));
			sb.append("{\n");

			sb.append(this.getBody());

			String tag = tree.getTag().toString();
			if (tag.equals("FuncExpr")) {
				final HashSet<String> boundVars = this.getLambdaArgs(tree);
				final String bound = boundVars.iterator().next();

				final String lastVarName = this.getValueVar(String.valueOf(depth - 2));
				final CommonTree lastVar = this.parseTree(lastVarName, this.NT_IDENTIFIER);
				tree = this.embedTree(tree.get(1), bound, lastVar);
				sb.append(this.evalTree(tree));
			} else {
				sb.append(expr);
				sb.append("(");
				sb.append(this.getValueVar(String.valueOf(depth - 2)));
				sb.append(")");
			}
			sb.append(";");

			for (int i = 0; i < depth; ++i) {
				sb.append("\n}");
			}
			return sb.toString();
		}

		public String reduce(final String lambda, final String init) {
			String pre = this.getLoopLimit();
			pre += this.getResVar("0") + " = " + init + "\n";
			return pre;
		}

		private String getLoopLimit() {
			String limVar = this.getConstVar("0");
			Iterator<String> ite = this._limits.get(0).iterator();
			String limVal = ite.next();
			while (ite.hasNext()) {
				limVal = this.getMinFunc(limVal, ite.next());
			}
			return limVar + " = " + limVal + "\n";
		}

		private String getBody() {
			StringBuilder sb = new StringBuilder();

			final int depth = this._values.size() - 1;
			this.setCond(depth, null);
			this.setLimit(depth + 1, null);

			int k = 1;
			String termination = "";
			for (int i = 0; i < depth; ++i) {
				CommonTree valueTree = this._values.get(i);
				String value = this.removeOuterBracket(this.evalTree(valueTree));
				String varName = this.getValueVar(String.valueOf(i));

				sb.append(varName);
				sb.append(" = ");
				sb.append(value);
				sb.append(";\n");

				if (termination.length() > 0) {
					sb.append(termination);
					termination = "";
				}

				// Limit (take)
				Set<String> nextLimit = this._limits.get(i + 1);
				if (!nextLimit.isEmpty()) {
					Iterator<String> ite = nextLimit.iterator();
					String limVal = ite.next();
					while (ite.hasNext()) {
						limVal = this.getMinFunc(limVal, ite.next());
					}
					final String limVarName = this.getLoopVar(String.valueOf(k++));
					sb.append(limVarName);
					sb.append(" = ");
					sb.append(limVal);
					sb.append(";\n");

					String limExpr = limVarName + " > 0";
					CommonTree limCond = this.parseTree(limExpr, this.NT_EXPRESSION);
					this.setCond(i, limCond);
					termination += limVarName + "--;\n";
				}

				List<CommonTree> conds = this._conditions.get(i);
				if (conds != null && conds.size() > 0) {
					String comb = this.getConditionCombinate(conds);
					sb.append("if( ");
					sb.append(comb);
					sb.append(" ){\n");
				}
			}

			return sb.toString();
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

		private CommonTree embedTree(final CommonTree parent, final String name, final CommonTree child) {
			Deque<CommonTree> st = new ArrayDeque();
			st.addFirst(parent);

			while (!st.isEmpty()) {
				final CommonTree tree = st.removeFirst();

				for (int i = 0; i < tree.size(); ++i) {
					CommonTree sub = tree.get(i);
					if (sub.getTag() != null && sub.getTag().toString().equals("NameExpr")
							&& sub.getString().equals(name)) {
						tree.set(i, child);
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

		private String getForLoop(String pre, String cond, String inc) {
			return String.format("for( %s; %s; %s )", pre, cond, inc);
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
}
