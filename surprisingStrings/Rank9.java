/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2013 Sebastiano Vigna
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
 */


/** A <code>rank9</code> implementation.
 *
 * <p><code>rank9</code> is a
 * ranking structure using just 25% additional space
 * and providing exceptionally fast ranking (on an Opteron at 2800 MHz this class
 * ranks a million-bit array in less than 8 nanoseconds). */

public class Rank9 {
	//Commented by FC> private static final boolean ASSERTS = false;
	//Commented by FC> private static final long serialVersionUID = 1L;

	protected transient long[] bits;
	final protected IntArray bitVector;
	final protected long[] count;
	final protected int numWords;
	final protected long numOnes;
	final protected long lastOne;


	public Rank9( final IntArray bitVector ) {
		this.bitVector = bitVector;
		this.bits = bitVector.array;
		final long length = bitVector.length();
		numWords = (int)( ( length + Long.SIZE - 1 ) / Long.SIZE );

		final int numCounts = (int)( ( length + 8 * Long.SIZE - 1 ) / ( 8 * Long.SIZE ) ) * 2;
		// Init rank/select structure
		count = new long[ numCounts + 1 ];

		long c = 0, l = -1;
		int pos = 0;
		for( int i = 0; i < numWords; i += 8, pos += 2 ) {
			count[ pos ] = c;
			c += Long.bitCount( bits[ i ] );
			if ( bits[ i ] != 0 ) l = i * 64L + Utils.mostSignificantBit( bits[ i ] );
			for( int j = 1;  j < 8; j++ ) {
				count[ pos + 1 ] |= ( i + j <= numWords ? c - count[ pos ] : 0x1FFL ) << 9 * ( j - 1 );
				if ( i + j < numWords ) {
					c += Long.bitCount( bits[ i + j ] );
					if ( bits[ i + j ] != 0 ) l = ( i + j ) * 64L + Utils.mostSignificantBit( bits[ i + j ] );
				}
			}
		}

		numOnes = c;
		lastOne = l;
		count[ numCounts ] = c;
	}


	public long rank( long pos ) {
		//Commented by FC> if ( ASSERTS ) assert pos >= 0;
		//Commented by FC> if ( ASSERTS ) assert pos <= bitVector.length();
		// This test can be eliminated if there is always an additional word at the end of the bit array.
		if ( pos > lastOne ) return numOnes;

		final int word = (int)( pos / 64 );
		final int block = word / 4 & ~1;
		final int offset = word % 8 - 1;
		return count[ block ] + ( count[ block + 1 ] >>> ( offset + ( offset >>> 32 - 4 & 0x8 ) ) * 9 & 0x1FF ) + Long.bitCount( bits[ word ] & ( ( 1L << pos % 64 ) - 1 ) );
	}

	public long numBits() {
		return count.length * (long)Long.SIZE;
	}

	public long count() {
		return numOnes;
	}

	public long rank( long from, long to ) {
		return rank( to ) - rank( from );
	}

	public long lastOne() {
		return lastOne;
	}

}