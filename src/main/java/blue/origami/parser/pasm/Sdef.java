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

package blue.origami.parser.pasm;

import blue.origami.common.Symbol;

public final class Sdef extends PAsmInst {
	public final Symbol tag;
	public final SymbolFunc action;

	public Sdef(SymbolFunc action, Symbol label, PAsmInst next) {
		super(next);
		this.tag = label;
		this.action = action;
	}

	@Override
	public PAsmInst exec(PAsmContext px) throws PAsmTerminationException {
		this.action.apply(px, px.state, this.tag, popPos(px));
		return this.next;
	}

}