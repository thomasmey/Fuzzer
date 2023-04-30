package de.m3y3r.fuzzer.generator.common;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.function.BiConsumer;

import de.m3y3r.fuzzer.Script;

public class OneOf implements BiConsumer<StringTokenizer, ByteBuffer>{

	private Random rnd = new Random();
	private Script script;

	public OneOf(Script script) {
		this.script = script;
	}

	@Override
	public void accept(StringTokenizer t, ByteBuffer u) {
		String[] oneOf = new String[t.countTokens()];
		int i = 0;
		while(t.hasMoreTokens()) {
			oneOf[i++] = t.nextToken();
		}

		String v = oneOf[rnd.nextInt(oneOf.length)];
		script.processParameter(v, t, u);
	}

}
