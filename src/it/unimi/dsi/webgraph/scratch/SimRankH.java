package it.unimi.dsi.webgraph.scratch;
import java.io.IOException;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.fastutil.Arrays;
import it.unimi.dsi.fastutil.Swapper;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.NodeIterator;

public class SimRankH {

	public static void simRank(final int[] source, final int[] target, final int[] indegree, double c, float[][] previous, float[][] next) {
		
		for(int i = indegree.length; i-- != 0;) java.util.Arrays.fill(next[i],  0);
		
		for(int i = source.length; i-- != 0;) 
			for(int j = i; j-- != 0;)
				next[target[i]][target[j]] += previous[source[i]][source[j]];

		for (int i = indegree.length; i-- != 0;) {
			final float[] t = next[i];
			final double cd = c / indegree[i];
			t[i] = 1;
			for (int j = i; j-- != 0;) 
				next[j][i] = t[j] = (float)((t[j] + next[j][i]) * cd / indegree[j]);
		}
	}

	@SuppressWarnings("boxing")
	static void printMatrix(float[][] a) {
		int i, j;
		for (i = 0; i < a.length; i++) {
			for (j = 0; j < a.length; j++) {
				System.out.printf("%.3f  ", a[Math.max(i, j)][Math.min(i, j)]);
			}
			System.out.println();
		}

	}

	// convert (x,y) to d
	private final static long xy2d(final int u, int x, int y) {
		long d = 0;
		int s = u, rx, ry; 
		for (s = u / 2; s > 0; s /= 2) {
			rx = (x & s) > 0 ? 1 : 0;
			ry = (y & s) > 0 ? 1 : 0;
			d += (long)s * s * ((3 * rx) ^ ry);

			if (ry == 0) {
				if (rx == 1) {
					assert s < Integer.MAX_VALUE;
					x = s - 1 - x;
					y = s - 1 - y;
				}

				// Swap x and y
				final int t = x;
				x = y;
				y = t;
			}
		}
		return d;
	}
	 
	public static void main(String[] args) throws NumberFormatException,
			IOException, JSAPException {

		SimpleJSAP jsap = new SimpleJSAP(
				SimRankH.class.getName(),
				"Compute SimRank.",
				new Parameter[] {
						new UnflaggedOption("basename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The basename of the graph."),
						new FlaggedOption("iterations", JSAP.INTEGER_PARSER, "5", JSAP.NOT_REQUIRED, 'i', null, "Number of iterations."),
						new FlaggedOption("c", JSAP.DOUBLE_PARSER,	String.valueOf(0.8f), JSAP.NOT_REQUIRED, 'c', null, "Attenuation factor."),
				});

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);

		String baseName = jsapResult.getString("basename");
		final int iterations = jsapResult.getInt("iterations");
		final double c = jsapResult.getDouble("c");

		ImmutableGraph immutableGraph = ImmutableGraph.load(baseName);
		final int numberOfNodes = immutableGraph.numNodes();
		final int numberOfArcs = (int)immutableGraph.numArcs();

		final int[] source = new int[numberOfArcs];
		final int[] target = new int[numberOfArcs];
		final int[] indegree = new int[numberOfNodes];

		final NodeIterator nodeIterator = immutableGraph.nodeIterator();
		for(int i = 0, a = 0; i < numberOfNodes; i++) {
			nodeIterator.nextInt();
			LazyIntIterator successors = nodeIterator.successors();
			for(int s; (s = successors.nextInt()) != -1;) {
				source[a] = i;
				target[a++] = s;
				indegree[s]++;
			}
		}

		final int n = 1 << Fast.ceilLog2(numberOfNodes);
		
		// Z-curve order
		Arrays.quickSort(0, numberOfArcs, new IntComparator() {
			@Override
			public int compare(int a, int b) {
				return Long.compare(xy2d(n, source[a], target[a]), xy2d(n, source[b], target[b]));
			}
		}, new Swapper() {
			@Override
			public void swap(int a, int b) {
				int t = source[a];
				source[a] = source[b];
				source[b] = t;
				t = target[a];
				target[a] = target[b];
				target[b] = t;
			}
		});
		
		float[][] m0 = new float[numberOfNodes][numberOfNodes], m1 = new float[numberOfNodes][numberOfNodes];
		for (int i = numberOfNodes; i-- != 0;) m0[i][i] = 1;

		ProgressLogger pl = new ProgressLogger();
		pl.start("Computing...");
		for (int i = 0; i < iterations; i++) {
			simRank(source, target, indegree, c, m0, m1);
			final float[][] t = m0;
			m0 = m1;
			m1 = t;
			pl.updateAndDisplay();
//			printMatrix(m0);
		}
		pl.done();
	}

}
