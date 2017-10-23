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

public class Icall extends PAsmInst {
	public PAsmInst jump = null;
	public String uname;

	public Icall(String uname, PAsmInst next) {
		super(next);
		this.uname = uname;
	}

	public final String getNonTerminalName() {
		return this.uname;
	}

	@Override
	public PAsmInst exec(PAsmContext px) throws PAsmTerminationException {
		pushRet(px, this.next);
		return this.jump;
	}

	@Override
	public PAsmInst[] branch() {
		return new PAsmInst[] { this.jump };
	}

}