/***********************************************************************
 * Copyright 2017 Kimio Kuramitsu and ORIGAMI project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ***********************************************************************/

package blue.origami.nez.parser.pasm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import blue.origami.main.MainOption;
import blue.origami.nez.parser.ParserCompiler;
import blue.origami.nez.parser.ParserGrammar;
import blue.origami.nez.parser.ParserGrammar.MemoPoint;
import blue.origami.nez.parser.pasm.PAsmAPI.SymbolDefFunc;
import blue.origami.nez.parser.pasm.PAsmAPI.SymbolResetFunc;
import blue.origami.nez.peg.Expression;
import blue.origami.nez.peg.ExpressionVisitor;
import blue.origami.nez.peg.Production;
import blue.origami.nez.peg.Typestate;
import blue.origami.nez.peg.expression.ByteSet;
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

public class PAsmCompiler implements ParserCompiler {

	public PAsmCompiler() {

	}

	@Override
	public ParserCompiler clone() {
		return new PAsmCompiler();
	}

	// Local Option
	OOption options = null;

	@Override
	public void init(OOption options) {
		this.options = options;
	}

	@Override
	public PAsmCode compile(ParserGrammar grammar) {
		PAsmCode code = new PAsmCode(grammar, this.options);
		new CompilerVisitor(code, grammar, this.options).compileAll();
		return code;
	}

	class CompilerVisitor extends ExpressionVisitor<PAsmInst, PAsmInst> {

		final PAsmCode code;
		final ParserGrammar grammar;

		boolean TreeConstruction = true;
		boolean binaryGrammar = false;
		boolean Optimization = true;

		CompilerVisitor(PAsmCode code, ParserGrammar grammar, OOption options) {
			this.code = code;
			this.grammar = grammar;
			this.binaryGrammar = grammar.isBinaryGrammar();
			this.TreeConstruction = options.is(MainOption.TreeConstruction, true);
		}

		private PAsmCode compileAll() {
			HashSet<PAsmInst> uniq = new HashSet<>();
			PAsmInst ret = new Iret();
			for (Production p : this.grammar) {
				String uname = p.getUniqueName();
				MemoPoint memoPoint = this.code.getMemoPoint(uname);
				PAsmInst prod = this.compileProductionExpression(memoPoint, p.getExpression(), ret);
				this.code.setInstruction(uname, prod);
				PAsmInst block = new Inop(uname, prod);
				this.layoutCode(uniq, this.code.codeList(), block);
			}
			for (PAsmInst inst : this.code.codeList()) {
				if (inst instanceof Icall) {
					Icall call = (Icall) inst;
					if (call.jump == null) {
						call.jump = this.code.getInstruction(call.uname);
					}
				}
			}
			PAsmCompiler.this.options.verbose("Instructions: %s", this.code.getInstructionSize());
			return this.code;
		}

		private PAsmInst compileProductionExpression(MemoPoint memoPoint, Expression p, final PAsmInst ret) {
			assert (ret instanceof Iret);
			if (memoPoint != null) {
				if (memoPoint.typeState == Typestate.Unit) {
					PAsmInst succMemo = this.compile(p, new Mmemo(memoPoint, ret));
					PAsmInst failMemo = new Mmemof(memoPoint);
					PAsmInst memo = new Ialt(succMemo, failMemo);
					return new Mfindpos(memoPoint, memo, ret);
				} else {
					PAsmInst succMemo = this.compile(p, new Mmemo(memoPoint, ret));
					PAsmInst failMemo = new Mmemof(memoPoint);
					PAsmInst memo = new Ialt(succMemo, failMemo);
					return new Mfindtree(memoPoint, memo, ret);
				}
			}
			return this.compile(p, ret);
		}

		private void layoutCode(Set<PAsmInst> uniq, List<PAsmInst> codeList, PAsmInst inst) {
			if (inst == null) {
				return;
			}
			if (!uniq.contains(inst)) {
				uniq.add(inst);
				codeList.add(inst);
				this.layoutCode(uniq, codeList, inst.next);
				for (PAsmInst br : inst.branch()) {
					this.layoutCode(uniq, codeList, br);
				}
			}
		}

		/* conversion */

		HashMap<String, int[]> boolsMap = new HashMap<>();

		int[] bools(ByteSet bs) {
			String key = bs.toString();
			int[] b = this.boolsMap.get(key);
			if (b == null) {
				b = bs.bits();
				this.boolsMap.put(key, b);
			}
			return b;
		}

		// encoding

		private PAsmInst compile(Expression e, PAsmInst next) {
			return e.visit(this, next);
		}

		@Override
		public PAsmInst visitEmpty(PEmpty p, PAsmInst next) {
			return next;
		}

		private final PAsmInst commonFailure = new Ifail();

		public PAsmInst fail(Expression e) {
			return this.commonFailure;
		}

		@Override
		public PAsmInst visitFail(PFail p, PAsmInst next) {
			return this.commonFailure;
		}

		@Override
		public PAsmInst visitByte(PByte p, PAsmInst next) {
			if (p.byteChar() == 0) {
				return new Neof(new Pbyte(0, next));
			}
			return new Pbyte(p.byteChar(), next);
		}

		@Override
		public PAsmInst visitByteSet(PByteSet p, PAsmInst next) {
			int[] b = this.bools(p.byteSet());
			if (PAsmAPI.bitis(b, 0)) {
				return new Neof(new Pset(b, next));
			}
			return new Pset(b, next);
		}

		@Override
		public PAsmInst visitAny(PAny p, PAsmInst next) {
			return new Pany(next);
		}

		@Override
		public final PAsmInst visitNonTerminal(PNonTerminal n, PAsmInst next) {
			Production p = n.getProduction();
			return new Icall(p.getUniqueName(), next);
		}

		private int byteChar(Expression e) {
			if (e instanceof PByte) {
				return ((PByte) e).byteChar();
			}
			if (e instanceof PByteSet) {
				return ((PByteSet) e).byteSet().getUnsignedByte();
			}
			return -1;
		}

		private ByteSet anyByteSet = null;

		private ByteSet toByteSet(Expression e) {
			if (e instanceof PByte) {
				return ((PByte) e).byteSet();
			}
			if (e instanceof PByteSet) {
				return ((PByteSet) e).byteSet();
			}
			if (e instanceof PAny) {
				if (this.anyByteSet == null) {
					this.anyByteSet = new ByteSet(this.binaryGrammar ? 0 : 1, 255);
				}
				return this.anyByteSet;
			}
			return null;
		}

		private int[] bools(Expression e) {
			ByteSet bs = this.toByteSet(e);
			if (bs != null) {
				return this.bools(bs);
			}
			return null;
		}

		private byte[] toMultiChar(Expression e) {
			ArrayList<Integer> l = new ArrayList<>();
			Expression.extractMultiBytes(e, l);
			byte[] utf8 = new byte[l.size()];
			for (int i = 0; i < l.size(); i++) {
				utf8[i] = (byte) (int) l.get(i);
			}
			return utf8;
		}

		@Override
		public final PAsmInst visitOption(POption p, PAsmInst next) {
			if (this.Optimization) {
				Expression inner = this.getInnerExpression(p);
				int byteChar = this.byteChar(inner);
				if (byteChar > 0) {
					return new Obyte(byteChar, next);
				}
				int[] b = this.bools(inner);
				if (b != null) {
					if (PAsmAPI.bitis(b, 0)) {
						return new Obin(b, next);
					} else {
						return new Oset(b, next);
					}
				}
				if (Expression.isMultiBytes(inner)) {
					byte[] utf8 = this.toMultiChar(inner);
					return new Ostr(utf8, next);
				}
			}
			PAsmInst pop = new Isucc(next);
			return new Ialt(this.compile(p.get(0), pop), next);
		}

		@Override
		public PAsmInst visitMany(PMany p, PAsmInst next) {
			PAsmInst next2 = this.visitMany0(p, next);
			if (p.isOneMore()) {
				next2 = this.compile(p.get(0), next2);
			}
			return next2;
		}

		private PAsmInst visitMany0(PMany p, PAsmInst next) {
			if (this.Optimization) {
				Expression inner = this.getInnerExpression(p);
				int byteChar = this.byteChar(inner);
				if (byteChar > 0) {
					return new Rbyte(byteChar, next);
				}
				int[] b = this.bools(inner);
				if (b != null) {
					if (PAsmAPI.bitis(b, 0)) {
						return new Rbin(b, next);
					} else {
						return new Rset(b, next);
					}
				}
				if (Expression.isMultiBytes(inner)) {
					byte[] utf8 = this.toMultiChar(inner);
					return new Rstr(utf8, next);
				}
			}
			PAsmInst skip = new Iupdate();
			PAsmInst start = this.compile(p.get(0), skip);
			skip.next = start;
			return new Ialt(start, next);
		}

		@Override
		public PAsmInst visitAnd(PAnd p, PAsmInst next) {
			if (this.Optimization) {
				Expression inner = this.getInnerExpression(p);
				ByteSet bs = this.toByteSet(inner);
				if (bs != null) {
					if (bs.is(0)) {
						return new Pbis(this.bools(bs), next);
					} else {
						return new Pis(this.bools(bs), next);
					}
				}
			}
			PAsmInst inner = this.compile(p.get(0), new Ppop(next));
			return new Ppush(inner);
		}

		@Override
		public final PAsmInst visitNot(PNot p, PAsmInst next) {
			if (this.Optimization) {
				Expression inner = this.getInnerExpression(p);
				if (inner instanceof PAny) {
					return new Peof(next);
				}
				ByteSet bs = this.toByteSet(inner);
				if (bs != null) {
					bs = bs.not(this.binaryGrammar);
					if (bs.is(0)) {
						return new Pbis(this.bools(bs), next);
					} else {
						return new Pis(this.bools(bs), next);
					}
				}
				if (Expression.isMultiBytes(inner)) {
					byte[] utf8 = this.toMultiChar(inner);
					return new Nstr(utf8, next);
				}
			}
			PAsmInst fail = new Isucc(new Ifail());
			return new Ialt(this.compile(p.get(0), fail), next);
		}

		@Override
		public PAsmInst visitPair(PPair p, PAsmInst next) {
			if (this.Optimization) {
				ArrayList<Integer> l = new ArrayList<>();
				Expression remain = Expression.extractMultiBytes(p, l);
				if (l.size() > 2) {
					byte[] text = Expression.toMultiBytes(l);
					next = this.compile(remain, next);
					return new Pstr(text, next);
				}
			}
			PAsmInst nextStart = next;
			for (int i = p.size() - 1; i >= 0; i--) {
				Expression e = p.get(i);
				nextStart = this.compile(e, nextStart);
			}
			return nextStart;
		}

		@Override
		public final PAsmInst visitChoice(PChoice p, PAsmInst next) {
			PAsmInst nextChoice = this.compile(p.get(p.size() - 1), next);
			for (int i = p.size() - 2; i >= 0; i--) {
				Expression e = p.get(i);
				nextChoice = new Ialt(this.compile(e, new Isucc(next)), nextChoice);
			}
			return nextChoice;
		}

		@Override
		public final PAsmInst visitDispatch(PDispatch p, PAsmInst next) {
			PAsmInst[] compiled = new PAsmInst[p.size() + 1];
			compiled[0] = this.commonFailure;
			if (this.isAllD(p)) {
				for (int i = 0; i < p.size(); i++) {
					compiled[i + 1] = this.compile(this.nextD(p.get(i)), next);
				}
				return new Idfa(p.indexMap, compiled);
			} else {
				for (int i = 0; i < p.size(); i++) {
					compiled[i + 1] = this.compile(p.get(i), next);
				}
				return new Idispatch(p.indexMap, compiled);
			}
		}

		private boolean isAllD(PDispatch p) {
			for (int i = 0; i < p.size(); i++) {
				if (!this.isD(p.get(i))) {
					return false;
				}
			}
			return true;
		}

		private boolean isD(Expression e) {
			if (e instanceof PPair) {
				if (e.get(0) instanceof PAny) {
					return true;
				}
				return false;
			}
			return (e instanceof PAny);
		}

		private Expression nextD(Expression e) {
			if (e instanceof PPair) {
				return e.get(1);
			}
			return Expression.defaultEmpty;
		}

		@Override
		public PAsmInst visitTree(PTree p, PAsmInst next) {
			if (this.TreeConstruction) {
				next = new Tend(p.tag, p.value, p.endShift, next);
				next = this.compile(p.get(0), next);
				if (p.folding) {
					return new Tfold(p.label, p.beginShift, next);
				} else {
					return new Tbegin(p.beginShift, next);
				}
			}
			return this.compile(p.get(0), next);
		}

		@Override
		public PAsmInst visitTag(PTag p, PAsmInst next) {
			if (this.TreeConstruction) {
				return new Ttag(p.tag, next);
			}
			return next;
		}

		@Override
		public PAsmInst visitValue(PValue p, PAsmInst next) {
			if (this.TreeConstruction) {
				return new Tvalue(p.value, next);
			}
			return next;
		}

		// Tree

		@Override
		public final PAsmInst visitLinkTree(PLinkTree p, PAsmInst next) {
			if (this.TreeConstruction) {
				next = new Tlink(p.label, next);
				next = this.compile(p.get(0), next);
				return new Tpush(next);
			}
			return this.compile(p.get(0), next);
		}

		@Override
		public PAsmInst visitDetree(PDetree p, PAsmInst next) {
			if (this.TreeConstruction) {
				next = new Tpop(next);
				next = this.compile(p.get(0), next);
				return new Tpush(next);
			}
			return this.compile(p.get(0), next);
		}

		/* Symbol */

		@Override
		public PAsmInst visitSymbolScope(PSymbolScope p, PAsmInst next) {
			if (p.label == null) {
				next = new Spop(next);
				next = this.compile(p.get(0), next);
				return new Spush(next);
			} else {
				next = new Spop(next);
				next = this.compile(p.get(0), next);
				next = new Sdefe(new SymbolResetFunc(), p.label, next);
				return new Spush(next);
			}
		}

		@Override
		public PAsmInst visitSymbolAction(PSymbolAction p, PAsmInst next) {
			return new Ppush(this.compile(p.get(0), new Sdef(new SymbolDefFunc(), p.label, next)));
		}

		@Override
		public PAsmInst visitSymbolPredicate(PSymbolPredicate p, PAsmInst next) {
			if (p.isAndPredicate()) {
				return new Ppush(this.compile(p.get(0), new Spred(p.pred, p.label, next)));
			} else {
				return new Sprede(p.pred, p.label, next);
			}
		}

		@Override
		public PAsmInst visitTrap(PTrap p, PAsmInst next) {
			if (p.trapid != -1) {
				return new Itrap(p.trapid, p.uid, next);
			}
			return next;
		}

		/* Optimization */

		private Expression getInnerExpression(Expression p) {
			return Expression.deref(p.get(0));
		}

		// Unused

		@Override
		public PAsmInst visitIf(PIf e, PAsmInst next) {
			PAsmCompiler.this.options.verbose("unremoved if condition", e);
			return next;
		}

		@Override
		public PAsmInst visitOn(POn e, PAsmInst next) {
			PAsmCompiler.this.options.verbose("unremoved on condition", e);
			return this.compile(e.get(0), next);
		}
	}

}
