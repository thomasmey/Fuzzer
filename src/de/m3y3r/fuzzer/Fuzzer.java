package de.m3y3r.fuzzer;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.math3.genetics.AbstractListChromosome;
import org.apache.commons.math3.genetics.Chromosome;
import org.apache.commons.math3.genetics.CrossoverPolicy;
import org.apache.commons.math3.genetics.ElitisticListPopulation;
import org.apache.commons.math3.genetics.GeneticAlgorithm;
import org.apache.commons.math3.genetics.InvalidRepresentationException;
import org.apache.commons.math3.genetics.MutationPolicy;
import org.apache.commons.math3.genetics.NPointCrossover;
import org.apache.commons.math3.genetics.Population;
import org.apache.commons.math3.genetics.SelectionPolicy;
import org.apache.commons.math3.genetics.TournamentSelection;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.tools.ExecDumpClient;
import org.jacoco.core.tools.ExecFileLoader;

class ByteArrayList extends AbstractList<Byte> {

	private byte[] array;

	public ByteArrayList(byte[] array) {
		this.array = array;
	}

	@Override
	public Byte get(int index) {
		return array[index];
	}

	@Override
	public int size() {
		return array.length;
	}

	public byte[] getArray() {
		return array;
	}
}

public class Fuzzer implements Runnable {

	class Request extends AbstractListChromosome<Byte> {
		private final byte[] requestContent;

		public Request(byte[] representation) throws InvalidRepresentationException {
			super(new ByteArrayList(representation), false);
			this.requestContent = representation;
		}

		@Override
		public double fitness() {
			try {
				resetCoverage();
				sendRequest(requestContent);
				ExecutionDataStore codeCoverage = getCoverage();

				int fitness = 0;
				for (ExecutionData ed : codeCoverage.getContents()) {
					for (int i = 0, n = ed.getProbes().length; i < n; i++) {
						if (ed.getProbes()[i])
							fitness++;
					}
				}

				// calculate fitness from code coverage
				return fitness;
			} catch (IOException e) {
				e.printStackTrace();
			}

			throw new IllegalArgumentException();
		}

		@Override
		protected void checkValidity(List<Byte> chromosomeRepresentation) throws InvalidRepresentationException {
		}

		@Override
		public AbstractListChromosome<Byte> newFixedLengthChromosome(List<Byte> chromosomeRepresentation) {
			byte[] ba = new byte[chromosomeRepresentation.size()];
			for (int i = 0, n = chromosomeRepresentation.size(); i < n; i++) {
				ba[i] = chromosomeRepresentation.get(i);
			}
			return new Request(ba);
		}

		public byte[] getRequestContent() {
			return requestContent;
		}
	}

	public static void main(String[] args) {
		Fuzzer fuzzer = new Fuzzer();
		fuzzer.run();
	}

	private Map<String, Object> config = new HashMap<>();
	private Random rnd = new Random();

	@Override
	public void run() {
		config.put("targetServer", new InetSocketAddress((InetAddress) null, 9090));
		config.put("targetCoverage", new InetSocketAddress("localhost", 6300));
		config.put("maxRequestSize", Integer.valueOf(3000));

		try {
			CrossoverPolicy crossoverPolicy = new NPointCrossover<>(10);
			double crossoverRate = 0.3d;

			MutationPolicy mutationPolicy = i -> {
				Request ir = (Request) i;
				byte[] newContent = Arrays.copyOf(ir.getRequestContent(), ir.getRequestContent().length);
				int maxChanges = (int) (ir.getRequestContent().length / 100f);
				for (int x = 0, n = rnd.nextInt(maxChanges); x < n; x++) {
					int pos = rnd.nextInt(ir.getRequestContent().length);
					newContent[pos] = (byte) rnd.nextInt();
				}
				return new Request(newContent);
			};
			double mutationRate = 0.6d;

			SelectionPolicy selectionPolicy = new TournamentSelection(5);

			GeneticAlgorithm geneticAlgorithm = new GeneticAlgorithm(crossoverPolicy, crossoverRate, mutationPolicy,
					mutationRate, selectionPolicy);

			int maxPop = 100;
			List<Chromosome> requests = new ArrayList<>(maxPop);
			fill(requests, maxPop);

			Population population = new ElitisticListPopulation(requests, maxPop, 0.8);

			for (int i = 0; i < Integer.MAX_VALUE; i++) {
				population = geneticAlgorithm.nextGeneration(population);
				if (i % 10 == 0) {
					System.out.format("round=%d, pop maxFit=%s\n", i, population.getFittestChromosome().getFitness());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void fill(List<Chromosome> requests, int maxPop) throws IOException {
		int maxReqSize = (int) config.getOrDefault("maxRequestSize", Integer.valueOf(3000));
		int len = rnd.nextInt(maxReqSize);

		for (int i = 0; i < maxPop; i++) {
			requests.add(generate(len));
		}
	}

	private Chromosome generate(int size) throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(size);
		Script script = new Script(new File("scripts\\httpRequest.script"));
		script.run(bb);
		//byte[] content = generateRandomRequest(size);
		Request request = new Request(bb.array());
		return request;
	}

	private void sendRequest(byte[] request) throws IOException {
		InetSocketAddress target = (InetSocketAddress) config.get("targetServer");
		// ByteBuffer response = ByteBuffer.allocate(8192);
		ByteBuffer bb = ByteBuffer.wrap(request);
		try (SocketChannel sc = SocketChannel.open(target)) {
			while (bb.hasRemaining()) {
				int lenRequest = sc.write(bb);
				// int lenResponse = sc.read(response);
				// System.out.println("req len=" + lenRequest);
			}
		}
	}

	private byte[] generateRandomRequest(int len) {
		byte[] ba = new byte[len];
		for (int i = 0; i < len;) {
			for (int rnd = this.rnd.nextInt(),
					n = Math.min(len - i, Integer.SIZE / Byte.SIZE); n-- > 0; rnd >>= Byte.SIZE) {
				ba[i++] = (byte) rnd;
			}
		}
		return ba;
	}

	private void resetCoverage() throws IOException {
		InetSocketAddress targetServer = (InetSocketAddress) config.get("targetCoverage");

		ExecDumpClient edc = new ExecDumpClient();
		edc.setRetryCount(3);
		edc.setDump(false);
		edc.setReset(true);
		edc.dump(targetServer.getAddress(), targetServer.getPort());
	}

	private ExecutionDataStore getCoverage() throws IOException {
		InetSocketAddress targetServer = (InetSocketAddress) config.get("targetCoverage");

		ExecDumpClient edc = new ExecDumpClient();
		edc.setRetryCount(3);
		edc.setDump(true);
		edc.setReset(false);
		ExecFileLoader efl = edc.dump(targetServer.getAddress(), targetServer.getPort());
		ExecutionDataStore eds = efl.getExecutionDataStore();
		return eds;
	}
}
