package de.m3y3r.fuzzer.generator.common;

import java.nio.ByteBuffer;
import java.util.StringTokenizer;
import java.util.function.BiConsumer;

import de.m3y3r.fuzzer.Script;

public class ConstString implements BiConsumer<StringTokenizer, ByteBuffer> {

	private java.lang.String str;

	public ConstString(Script script, java.lang.String string) {
		this.str = string;
	}

	@Override
	public void accept(StringTokenizer t, ByteBuffer u) {
		u.put(str.getBytes());
	}
}
