//package it.unimi.dsi.util;

/*
 * DSI utilities
 *
 * Copyright (C) 2011-2013 Sebastiano Vigna
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 *
 * Adapted by Fabio Cunial to work in the current package.
 *
 */


//import it.unimi.dsi.Util;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

//import org.apache.commons.math3.random.RandomGenerator;

/** An unbelievably fast, high-quality 64-bit {@linkplain Random pseudorandom number generator} that combines George Marsaglia's Xorshift
 * generators (described in <a href="http://www.jstatsoft.org/v08/i14/paper/">&ldquo;Xorshift RNGs&rdquo;</a>,
 * <i>Journal of Statistical Software</i>, 8:1&minus;6, 2003) with a multiplication.
 * Note that this is <strong>not</strong> a cryptographic-strength
 * pseudorandom number generator, but its quality is preposterously higher than {@link Random}'s
 * (and its cycle length is 2<sup>64</sup>&nbsp;&minus;&nbsp;1, more than enough for 99.9% applications).
 *
 * <p>On an Intel i7 at 2.6 GHz, all methods of this class except for {@link #nextInt()} are faster than
 * those of <a href="http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ThreadLocalRandom.html"><code>ThreadLocalRandom</code></a>.
 * Timings are orders of magnitude faster than {@link Random}'s, but {@link Random} is slowed down by the
 * fact of being thread safe, so the comparison is not fair. The following table reports timings
 * in nanoseconds for some type of calls:
 *
 * <CENTER><TABLE BORDER=1>
 * <TR><TH><TH><a href="http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ThreadLocalRandom.html"><code>ThreadLocalRandom</code></a>
 * <TH>{@link XorShiftStarRandom}
 * <TR><TD>	nextInt()	<TD>1.52<TD>2.00
 * <TR><TD>	nextLong()	<TD>3.16<TD>1.99
 * <TR><TD>	nextDouble()	<TD>3.27<TD>2.27
 * <TR><TD>	nextInt(1000000)	<TD>3.54<TD>2.91
 * <TR><TD>	nextInt(2^29+2^28)	<TD>7.29<TD>2.94
 * <TR><TD>	nextInt(2^30)	<TD>3.29<TD>2.15
 * <TR><TD>	nextInt(2^30+1)	<TD>14.03<TD>3.32
 * <TR><TD>	nextInt(2^30+2^29-1)	<TD>7.28<TD>2.87
 * <TR><TD>	nextInt(2^30+2^29)	<TD>7.25<TD>2.93
 * <TR><TD>	nextLong(1000000000000)	<TD>81.30<TD>3.80
 * <TR><TD>	nextLong(2^62+1)	<TD>251.89<TD>14.69<TD>
 * </TABLE></CENTER>
 *
 * <p>The quality of this generator is high: for instance, it performs significantly better than <samp>WELL1024a</samp>
 * or <samp>MT19937</samp> in suites like
 * <a href="http://www.iro.umontreal.ca/~simardr/testu01/tu01.html">TestU01</a> and
 * <a href="http://www.phy.duke.edu/~rgb/General/dieharder.php">Dieharder</a>. More precisely, over 100 runs of the BigCrush test suite
 * starting from equispaced points of the state space:
 *
 * <ul>
 *
 * <li>this generator and its reverse fail 368 tests;
 *
 * <li><samp>WELL1024a</samp> and its reverse fail 882 tests (the only test failed at all points is MatrixRank);
 *
 * <li><samp>WELL19937</samp> and its reverse fail
 * <li>{@link Random} and its reverse fail 13564 tests of all kind.
 *
 * </ul>
 *
 * Moreover, the memory usage is the smallest possible: a single long.
 *
 * <p>This class extends {@link Random}, overriding (as usual) the {@link Random#next(int)} method. Nonetheless,
 * since the generator is inherently 64-bit also {@link Random#nextInt()}, {@link Random#nextInt(int)},
 * {@link Random#nextLong()} and {@link Random#nextDouble()} have been overridden for speed (preserving, of course, {@link Random}'s semantics).
 * See in particular the comments in the documentation of {@link #nextInt(int)}, which is tailored for speed at the price of an essentially undetectable bias.
 *
 * <p>If you do not need an instance of {@link Random}, or if you need a {@link RandomGenerator} to use
 * with <a href="http://commons.apache.org/math/">Commons Math</a>, you might be wanting
 * {@link XorShiftStarRandomGenerator} instead of this class.
 *
 * <h3>Parameter choice</h3>
 *
 * <p>There are five parameters to choose in a pseudorandom number generator of this kind: the three shift values,
 * the type of shift, and the multiplier. <i>Numerical Recipes</i> (third edition, Cambridge University Press, 2007)
 * suggests a choice of parameters which gives a generator of lower quality than that implemented by this class (in the same
 * settings as above, it fails 463 tests including BirthdaySpacing at all points).
 * We have experimented with different multipliers proposed by Pierre L'Ecuyer
 * (&ldquo;Tables of linear congruential generators of different sizes and good lattice structure&rdquo;,
 * <i>Math. Comput.</i>, 68(225):249&minus;260, 1999), and set the other parameters
 * by extensive experimentation on the 2200 possible choices using
 * <a href="http://www.iro.umontreal.ca/~simardr/testu01/tu01.html">TestU01</a> and
 * <a href="http://www.phy.duke.edu/~rgb/General/dieharder.php">Dieharder</a>. More details
 * will appear in a forthcoming paper.
 *
 * <p><strong>Warning</strong>: this class is still experimental, and different parameters might
 * be used in the future.
 *
 * <h3>Notes</h3>
 *
 * <p>The <em>lower bits</em> of this generator are of slightly better quality than the higher bits. Thus, masking the lower
 * bits is a safe and effective way to obtain small random numbers. The code in this class usually extracts
 * lower bits, rather than upper bits, whenever a subset of bits is necessary (when extracting 63 bits
 * we use a right shift for performance reasons, though).
 */
public class XorShiftStarRandom /*extends Random*/ {
	private static final long serialVersionUID = 1L;

	/** 2<sup>53</sup> &minus; 1. */
	private static final long DOUBLE_MASK = ( 1L << 53 ) - 1;
	/** 2<sup>-53</sup>. */
	private static final double NORM_53 = 1. / ( 1L << 53 );
	/** 2<sup>24</sup> &minus; 1. */
	private static final long FLOAT_MASK = ( 1L << 24 ) - 1;
	/** 2<sup>-24</sup>. */
	private static final double NORM_24 = 1. / ( 1L << 24 );

	/** The internal state of the algorithm. */
	private long x;

	/** Creates a new generator seeded using {@link Util#randomSeed()}. */
	public XorShiftStarRandom() {
		this( randomSeed() );
	}


	private static final AtomicLong seedUniquifier = new AtomicLong();

	/** Returns a random seed generated by taking a unique increasing long, adding
	 * {@link System#nanoTime()} and scrambling the result using the finalisation step of Austin
	 * Appleby's <a href="http://sites.google.com/site/murmurhash/">MurmurHash3</a>.
	 *
	 * @return a reasonably good random seed.
	 */
	public static long randomSeed() {
		long seed = seedUniquifier.incrementAndGet() + System.nanoTime();

		seed ^= seed >>> 33;
		seed *= 0xff51afd7ed558ccdL;
		seed ^= seed >>> 33;
		seed *= 0xc4ceb9fe1a85ec53L;
		seed ^= seed >>> 33;

		return seed;
	}


	/** Creates a new generator using a given seed.
	 *
	 * @param seed a nonzero seed for the generator (if zero, the generator will be seeded with -1).
	 */
	public XorShiftStarRandom( final long seed ) {
		setSeed( seed );
	}

	//@Override
	protected int next( int bits ) {
		return (int)( nextLong() & ( 1L << bits ) - 1 );
	}

	//@Override
	public long nextLong() {
		x ^= x << 23;
		x ^= x >>> 52;
		return 2685821657736338717L * ( x ^= ( x >>> 17 ) );
	}

	//@Override
	public int nextInt() {
		return (int)nextLong();
	}

	/** Returns a pseudorandom, approximately uniformly distributed {@code int} value
     * between 0 (inclusive) and the specified value (exclusive), drawn from
     * this random number generator's sequence.
     *
     * <p>The hedge &ldquo;approximately&rdquo; is due to the fact that to be always
     * faster than <a href="http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ThreadLocalRandom.html"><code>ThreadLocalRandom</code></a>
     * we return
     * the upper 63 bits of {@link #nextLong()} modulo {@code n} instead of using
     * {@link Random}'s fancy algorithm (which {@link #nextLong(long)} uses though).
     * This choice introduces a bias: the numbers from 0 to 2<sup>63</sup> mod {@code n}
     * are slightly more likely than the other ones. In the worst case, &ldquo;more likely&rdquo;
     * means 1.00000000023 times more likely, which is in practice undetectable (actually,
     * due to the abysmally low quality of {@link Random}'s generator, the result is statistically
     * better in any case than {@link Random#nextInt(int)}'s) .
     *
     * <p>If for some reason you need truly uniform generation, just use {@link #nextLong(long)}.
     *
     * @param n the positive bound on the random number to be returned.
     * @return the next pseudorandom {@code int} value between {@code 0} (inclusive) and {@code n} (exclusive).
     */
	//@Override
	public int nextInt( final int n ) {
        //if ( n <= 0 ) throw new IllegalArgumentException();
		// No special provision for n power of two: all our bits are good.
        return (int)( ( nextLong() >>> 1 ) % n );
	}

	/** Returns a pseudorandom uniformly distributed {@code long} value
     * between 0 (inclusive) and the specified value (exclusive), drawn from
     * this random number generator's sequence. The algorithm used to generate
     * the value guarantees that the result is uniform, provided that the
     * sequence of 64-bit values produced by this generator is.
     *
     * @param n the positive bound on the random number to be returned.
     * @return the next pseudorandom {@code long} value between {@code 0} (inclusive) and {@code n} (exclusive).
     */
	public long nextLong( final long n ) {
        //if ( n <= 0 ) throw new IllegalArgumentException();
		// No special provision for n power of two: all our bits are good.
		for(;;) {
			final long bits = nextLong() >>> 1;
			final long value = bits % n;
			if ( bits - value + ( n - 1 ) >= 0 ) return value;
		}
	}

	//@Override
	 public double nextDouble() {
		return ( nextLong() & DOUBLE_MASK ) * NORM_53;
	}

	//@Override
	public float nextFloat() {
		return (float)( ( nextLong() & FLOAT_MASK ) * NORM_24 );
	}

	//@Override
	public boolean nextBoolean() {
		return ( nextLong() & 1 ) != 0;
	}

	//@Override
	public void nextBytes( final byte[] bytes ) {
		int i = bytes.length, n = 0;
		while( i != 0 ) {
			n = Math.min( i, 8 );
			for ( long bits = nextLong(); n-- != 0; bits >>= 8 ) bytes[ --i ] = (byte)bits;
		}
	}

	//@Override
	public void setSeed( final long seed ) {
		x = seed == 0 ? -1 : seed;
		nextLong(); // Warmup.
	}
}
