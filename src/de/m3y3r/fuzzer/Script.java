package de.m3y3r.fuzzer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.function.BiConsumer;

import org.apache.commons.text.StringEscapeUtils;

class BetterStringTokenizer extends StringTokenizer {

	private String str;

	public BetterStringTokenizer(String str) {
		super(str);
		this.str = str;
	}

	public String getStr() {
		return str;
	}
}
class Scope {
	Map<String, BiConsumer<StringTokenizer, ByteBuffer>> functions = new HashMap<>();

	List<String> lines;
	ListIterator<String> lineIterator; //only used by root scope, scope at level 0, covers whole script

	StringTokenizer start;
}

interface TriConsumer<K, V, S> {
	void accept(K k, V v, S s);
}

public class Script {

	private Random rnd = new Random();
	private List<Scope> scopes = new ArrayList<>();
	private Map<String, TriConsumer<Scope, StringTokenizer, ByteBuffer>> scopeFunctions = new HashMap<String, TriConsumer<Scope, StringTokenizer, ByteBuffer>>();

	public Script(File file) throws IOException {
		addScopeFunctions();
		Scope rootScope = new Scope();
		rootScope.lines = Files.readAllLines(file.toPath());
		rootScope.lineIterator = rootScope.lines.listIterator();

		addBuildInFunctions(rootScope.functions);
		scopes.add(rootScope);
	}

	private void addScopeFunctions() {
		scopeFunctions.put("*", (scope, tokenizer, buffer) -> {
			Object pp = processParameter(tokenizer.nextToken());
			int maxRepeat = Integer.parseInt((String)pp);
			int repeats = rnd.nextInt(maxRepeat);
			for(int i = 0; i < repeats; i++) {
				processScope(scope, null, buffer);
			}
		});
	}

	private void addBuildInFunctions(Map<String, BiConsumer<StringTokenizer, ByteBuffer>> functions) {
		// comment -> NOP
		functions.put("#", (tokenizer, buffer) -> {});

		// scope function
		functions.put("(", this::addNewScope);
		functions.put("processNestedScope", (tokenizer, buffer) -> {
			Scope nestedScope = scopes.get(++scopeLevel);
			processScope(nestedScope, nestedScope.start, buffer);
			scopeLevel--;
		});

		// define an function in the current scope
		functions.put("var", (tokenizer, buffer) -> {
			String varName = tokenizer.nextToken();
			String className = tokenizer.nextToken();
			try {
				Class<?> clazz = Class.forName(className);
				Constructor<?> con = null;
				int paramCount = tokenizer.countTokens() + 1;
				for(Constructor<?> constructor: clazz.getConstructors()) {
					if(constructor.getParameterCount() == paramCount) {
						con = constructor;
						break;
					}
				}
				if(con == null) {
					// failed to find matching constructor
					throw new IllegalArgumentException();
				}

				Object[] args = new Object[paramCount];
				int i = 0;
				args[i++] = this;
				while(tokenizer.hasMoreTokens()) {
					args[i++] = processParameter(tokenizer.nextToken());
				}
				BiConsumer<StringTokenizer, ByteBuffer> function = (BiConsumer<StringTokenizer, ByteBuffer>) con.newInstance(args);
				functions.put(varName, function);
			} catch (ReflectiveOperationException | IllegalArgumentException e) {
				e.printStackTrace();
			}
		});
	}

	private int scopeLevel = 0;
	public void addNewScope(StringTokenizer tokenizer, ByteBuffer buffer) {

		// TODO: only use listIterator from root scope in parsing scope content
		Scope parentScope = null;
		ListIterator<Scope> iterator = scopes.listIterator(scopes.size());
		while(iterator.hasPrevious()) {
			parentScope = iterator.previous();
			if(parentScope.lineIterator != null) {
				break;
			}
		}
		if(parentScope == null) {
			throw new IllegalArgumentException();
		}

		Scope scope = new Scope();
		scope.start = (BetterStringTokenizer) tokenizer;
		scopes.add(scope);
		scopeLevel++;

		//search nested scopes/end of scope
		List<String> lines = new ArrayList<>();
		while(parentScope.lineIterator.hasNext()) {
			String line = parentScope.lineIterator.next();
			if(line.startsWith("(")) {
				// nested scope
				BetterStringTokenizer st = new BetterStringTokenizer(line);
				st.nextToken(); // consume (
				addNewScope(st, buffer);
				lines.add("processNestedScope");
				continue;
			}

			if(line.equals(")")) {
				break;
			}

			lines.add(line);
		}
		scope.lines = lines;
		scopeLevel--;

		if(scopeLevel > 0) {
			return;
		}

		processScope(scopes.get(++scopeLevel), tokenizer, buffer);

		// clear scopes again, but root scope
		for(int i = scopes.size() - 1; i > 0; i--) {
			scopes.remove(i);
		}
	}

	public Object processParameter(String token) {
		// parse as string
		if(token.startsWith("\"") && token.endsWith("\"")) {
			return StringEscapeUtils.unescapeJava(token.substring(1, token.length() - 1));
		}

		Object func = findFunction(token);
		if(func != null) {
			return func;
		}

		return token;
	}

	public void processParameter(String token, StringTokenizer t, ByteBuffer u) {
		Object sof = processParameter(token);
		if(sof instanceof String) {
			u.put(((String) sof).getBytes());
		} else if(sof instanceof BiConsumer) {
			((BiConsumer<StringTokenizer, ByteBuffer>) sof).accept(t,u);
		}
	}

	private BiConsumer<StringTokenizer, ByteBuffer> findFunction(String token) {
		ListIterator<Scope> iterator = scopes.listIterator(scopes.size());

		while(iterator.hasPrevious()) {
			Scope scope = iterator.previous();
			// is it a function?
			if(scope.functions.containsKey(token)) {
				return scope.functions.get(token);
			}
		}

		return null;
	}


	private TriConsumer<Scope, StringTokenizer, ByteBuffer> findScopeFunction(String token) {
		if(scopeFunctions.containsKey(token)) {
			return scopeFunctions.get(token);
		}
		return this::processScope;
	}

	public void run(ByteBuffer out) {
		try {
			processScope(scopes.get(scopeLevel), null, out);
		} catch(BufferOverflowException e) {
			e.printStackTrace();
		}
	}

	private void processScope(Scope scope, StringTokenizer scopeStart, ByteBuffer out) {
		// only set when called from nested scopes
		if(scopeStart != null) {
			StringTokenizer st = new BetterStringTokenizer(((BetterStringTokenizer)scopeStart).getStr());
			st.nextToken(); // consume (
			TriConsumer<Scope, StringTokenizer, ByteBuffer> sf = findScopeFunction(st.nextToken());;
			sf.accept(scope, st, out);
			return;
		}

		ListIterator<String> lineIterator;
		// set line iterator for nested scopes
		if(scope.lineIterator == null) {
			lineIterator = scope.lines.listIterator();
		} else {
			lineIterator = scope.lineIterator;
		}

		while(lineIterator.hasNext()) {
			String line = lineIterator.next();
			processLine(out, line);
		}
	}

	private void processLine(ByteBuffer out, String line) {
		StringTokenizer tokenizer = new BetterStringTokenizer(line);
		if(tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken();
			BiConsumer<StringTokenizer, ByteBuffer> function = findFunction(token);
			if(function == null) {
				throw new IllegalArgumentException("missing function " + token);
			}
			function.accept(tokenizer, out);
		}
	}

	public static void main(String[] args) throws IOException {
		Script script = new Script(new File("scripts\\httpRequest.script"));
		ByteBuffer out = ByteBuffer.allocate(4096);
		script.run(out);
		System.out.println(new String(Arrays.copyOfRange(out.array(), 0, out.position())));
	}
}
