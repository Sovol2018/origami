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

package blue.origami.parser.peg;

public enum NonEmpty {
	True, False, Unsure;

	public final static boolean isAlwaysConsumed(Expression e) {
		return minLenFunc.check(e, null) == NonEmpty.True;
	}

	public final static boolean isAlwaysConsumed(Production p) {
		return isConsumedImpl(p.getGrammar(), p.getLocalName(), p.getExpression()) == NonEmpty.True;
	}

	static NonEmpty isConsumedImpl(Grammar g, String name, Expression e) {
		NonEmpty r = g.getProperty(name, NonEmpty.class);
		if (r == null) {
			g.setProperty(name, NonEmpty.Unsure);
			r = minLenFunc.check(e, null);
			g.setProperty(name, r);
		}
		return r;
	}

	private static EmptyCheck minLenFunc = new EmptyCheck();

	private static class EmptyCheck extends ExpressionVisitor<NonEmpty, Void> {
		NonEmpty check(Expression e, Void a) {
			return e.visit(this, a);
		}

		@Override
		public NonEmpty visitNonTerminal(PNonTerminal e, Void a) {
			if (e.getGrammar() == null || e.getProduction() == null) {
				return NonEmpty.False;
			}
			return NonEmpty.isConsumedImpl(e.getGrammar(), e.getLocalName(), e.getExpression());
		}

		@Override
		public NonEmpty visitEmpty(PEmpty e, Void a) {
			return NonEmpty.False;
		}

		@Override
		public NonEmpty visitFail(PFail e, Void a) {
			return NonEmpty.False;
		}

		@Override
		public NonEmpty visitByte(PByte e, Void a) {
			return NonEmpty.True;
		}

		@Override
		public NonEmpty visitByteSet(PByteSet e, Void a) {
			return NonEmpty.True;
		}

		@Override
		public NonEmpty visitAny(PAny e, Void a) {
			return NonEmpty.True;
		}

		@Override
		public NonEmpty visitPair(PPair e, Void a) {
			if (this.check(e.get(0), a) == NonEmpty.True) {
				return NonEmpty.True;
			}
			return this.check(e.get(1), a);
		}

		@Override
		public NonEmpty visitChoice(PChoice e, Void a) {
			boolean unconsumed = false;
			boolean undecided = false;
			for (Expression sub : e) {
				NonEmpty c = this.check(sub, a);
				if (c == NonEmpty.True) {
					continue;
				}
				unconsumed = true;
				if (c == NonEmpty.Unsure) {
					undecided = true;
				}
			}
			if (!unconsumed) {
				return NonEmpty.True;
			}
			return undecided ? NonEmpty.Unsure : NonEmpty.False;
		}

		@Override
		public NonEmpty visitDispatch(PDispatch e, Void a) {
			boolean unconsumed = false;
			boolean undecided = false;
			for (Expression sub : e) {
				NonEmpty c = this.check(sub, a);
				if (c == NonEmpty.True) {
					continue;
				}
				unconsumed = true;
				if (c == NonEmpty.Unsure) {
					undecided = true;
				}
			}
			if (!unconsumed) {
				return NonEmpty.True;
			}
			return undecided ? NonEmpty.Unsure : NonEmpty.False;
		}

		@Override
		public NonEmpty visitOption(POption e, Void a) {
			return NonEmpty.False;
		}

		@Override
		public NonEmpty visitMany(PMany e, Void a) {
			if (e.isOneMore()) {
				return this.check(e.get(0), a);
			}
			return NonEmpty.False;
		}

		@Override
		public NonEmpty visitAnd(PAnd e, Void a) {
			return NonEmpty.False;
		}

		@Override
		public NonEmpty visitNot(PNot e, Void a) {
			return NonEmpty.False;
		}

		@Override
		public NonEmpty visitTree(PTree e, Void a) {
			return this.check(e.get(0), a);
		}

		@Override
		public NonEmpty visitLinkTree(PLinkTree e, Void a) {
			return this.check(e.get(0), a);
		}

		@Override
		public NonEmpty visitTag(PTag e, Void a) {
			return NonEmpty.False;
		}

		@Override
		public NonEmpty visitValue(PValue e, Void a) {
			return NonEmpty.False;
		}

		@Override
		public NonEmpty visitDetree(PDetree e, Void a) {
			return this.check(e.get(0), a);
		}

		@Override
		public NonEmpty visitSymbolScope(PSymbolScope e, Void a) {
			return this.check(e.get(0), a);
		}

		@Override
		public NonEmpty visitSymbolAction(PSymbolAction e, Void a) {
			return this.check(e.get(0), a);
		}

		@Override
		public NonEmpty visitSymbolPredicate(PSymbolPredicate e, Void a) {
			return this.check(e.get(0), a);
		}

		@Override
		public NonEmpty visitIf(PIf e, Void a) {
			return NonEmpty.False;
		}

		@Override
		public NonEmpty visitOn(POn e, Void a) {
			return this.check(e.get(0), a);
		}

		@Override
		public NonEmpty visitTrap(PTrap e, Void a) {
			return NonEmpty.False;
		}
	}

}
