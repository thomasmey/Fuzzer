package de.m3y3r.fuzzer.generator.common;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.function.BiConsumer;

import org.apache.commons.text.RandomStringGenerator;

import de.m3y3r.fuzzer.Script;

public class RandomString implements BiConsumer<StringTokenizer, ByteBuffer> {

//	private byte[] ba;
	private Random rnd = new Random();
	private int maxLen;

	public RandomString(Script script, String string) {
		maxLen = Integer.parseInt(string);
	}

	@Override
	public void accept(StringTokenizer t, ByteBuffer u) {
		RandomStringGenerator generator = new RandomStringGenerator.Builder()
				.withinRange(0x21, 0x7e).build();
		int len = rnd.nextInt(maxLen);
		String string = generator.generate(len);
		u.put(string.getBytes());
//		rnd.nextBytes(ba);
//		u.put(ba, 0, len);
	}
}
