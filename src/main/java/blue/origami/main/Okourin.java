package blue.origami.main;

import java.util.Random;

import blue.origami.nez.parser.Parser;
import blue.origami.nez.parser.ParserOption;
import blue.origami.nez.peg.Grammar;
import blue.origami.nez.peg.PEGPackratInterpreter;
import blue.origami.nez.peg.PEGParserInterpreter;
import blue.origami.nez.peg.SourceGrammar;
import blue.origami.util.OOption;

public class Okourin extends OCommand {

	public String makeExpression(int length) {
		Random rand = new Random();
		StringBuilder sb = new StringBuilder();

		char[] pattern = { '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '+', '+', '+', '-', '-', '-', '-', '*',
				'*', '*', '*', '/', '/', '/', '/' };
		int t = pattern.length;

		boolean opFlag = true;
		for (int i = 0; i < length; ++i) {
			char c;
			while (true) {
				c = pattern[rand.nextInt(t)];

				boolean f = false;
				if (c == '+' || c == '-' || c == '*' || c == '/') {
					f = true;
				}
				if (opFlag && f) {
					continue;
				}

				opFlag = f;
				sb.append(c);
				break;
			}
		}
		sb.append(rand.nextInt(10));
		return sb.toString();
	}

	@Override
	public void exec(OOption options) throws Throwable {
		String[] files = options.stringList(ParserOption.InputFiles);
		Grammar g = SourceGrammar.loadFile(files[0]);
		System.out.println("=== original grammar ===");
		g.dump();
		System.out.println();

		String text = this.makeExpression(100000000);

		long t1 = System.currentTimeMillis();

		PEGParserInterpreter PEGParser = options.newInstance(PEGParserInterpreter.class);
		// int cnt1 = PEGParser.parse(g, text);

		long t2 = System.currentTimeMillis();

		PEGPackratInterpreter packratParser = options.newInstance(PEGPackratInterpreter.class);
		// int cnt2 = packratParser.parse(g, text);

		long t3 = System.currentTimeMillis();

		Parser parser = new Parser(g.getStartProduction(), options);
		parser.compile();

		long t4 = System.currentTimeMillis();
		parser.match(text);
		long t5 = System.currentTimeMillis();

		// System.out.println(text);
		// System.out.println("Backtrack: " + cnt1 + " -> " + cnt2);
		// System.out.println("ParseTime: " + (t2 - t1) + " -> " + (t3 - t2) + " [ms]");

		System.out.println("NezTime: " + (t5 - t4) + "[ms]");
		/*
		 * 
		 * LeftRecursionEliminator eliminator =
		 * options.newInstance(LeftRecursionEliminator.class); eliminator.compute(g);
		 * System.out.println("=== converted grammar ==="); g.dump();
		 * System.out.println();
		 * 
		 * Parser parser = new Parser(g.getStartProduction(), options);
		 * parser.compile(); System.out.println("=== compiled grammar ===");
		 * parser.getParserGrammar().dump(); System.out.println();
		 * 
		 * Tree<?> res = parser.parse("1-2-3"); System.out.println(res.toString());
		 */
	}
}
