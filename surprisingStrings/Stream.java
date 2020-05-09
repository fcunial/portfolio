/**
 * Expanding and contracting stack of bits with random access.
 *
 * Remark: tradeoffs between occupied space and access time could be achieved by
 * transparently using ad hoc encodings of integers. Such developments are left to the
 * future.
 */
public class Stream {

	protected final int LONGS_PER_REGION;
	private final int LOG2_LONGS_PER_REGION;
	private final int LOG2_LONGS_PER_REGION_PLUS_SIX;
	private final int SIXTYFOUR_MINUS_LOG2_LONGS_PER_REGION;

	protected long[][] regions;
	private long nBits;  // Total number of bits in the stream
	protected int topRegion, topCell, topOffset;  // Top of the stack
	protected int pointerRegion, pointerCell, pointerOffset;  // Pointer to a bit in the stream


	/**
	 * @param longsPerRegion must be a power of two.
	 */
	public Stream(int longsPerRegion) {
		LONGS_PER_REGION=longsPerRegion;
		LOG2_LONGS_PER_REGION=Utils.log2(longsPerRegion);
		LOG2_LONGS_PER_REGION_PLUS_SIX=LOG2_LONGS_PER_REGION+6;
		SIXTYFOUR_MINUS_LOG2_LONGS_PER_REGION=64-LOG2_LONGS_PER_REGION;
		regions = new long[1][longsPerRegion];
	}


	public void clear(boolean deallocate) {
		if (deallocate) regions = new long[1][LONGS_PER_REGION];
		topRegion=0; topCell=0; topOffset=0;
		nBits=0;
	}


	public void deallocate() {
		int nRegions = regions.length;
		for (int i=0; i<nRegions; i++) regions[i]=null;
		regions=null;
	}


	public final long nBits() {
		return nBits;
	}




	// ------------------------------- STACK INTERFACE -----------------------------------

	/**
	 * Appends the $n$ least significant bits of $bits$ to the stack, possibly expanding
	 * it. Expansion implies: (1) doubling the size of $regions$; (2) copying
	 * $regions.length$ pointers (using the internalized procedure $System.arraycopy$);
	 * (3) increasing the number of occupied bits by one region.
	 */
	public final void push(long bits, int n) {
		final long lBits = bits&Utils.shiftOnesRight[64-n];
		int tmp = 64-topOffset;
		final int nRegions;
		long[] array = regions[topRegion];

		array[topCell]&=Utils.shiftOnesLeft[tmp];
		if (tmp>n) {
			array[topCell]|=lBits<<(tmp-n);
			topOffset+=n;
		}
		else {
			tmp=n-tmp;
			array[topCell]|=lBits>>>tmp;
			if (topCell+1<array.length) topCell++;
			else {
				nRegions=regions.length;
				if (topRegion+1==nRegions) {
					long[][] newRegions = new long[nRegions<<1][0];
					System.arraycopy(regions,0,newRegions,0,nRegions);
					regions=newRegions;
				}
				topRegion++;
				regions[topRegion] = new long[LONGS_PER_REGION];
				array=regions[topRegion];
				topCell=0;
			}
			array[topCell]=0L;
			array[topCell]|=lBits<<(64-tmp);
			topOffset=tmp;
		}
		nBits+=n;
	}


	/**
	 * Removes an arbitrary number of bits from the top of the stack, possibly contracting
	 * it. The last unused regions are immediately deallocated.
	 *
	 * Remark: the random access pointer could be in an invalid position after $pop$.
	 * This case is not explicitly checked.
	 */
	public final void pop(long n) {
		nBits-=n;
		int newTopRegion = (int)(nBits>>>(LOG2_LONGS_PER_REGION+6));
		for (int i=newTopRegion+1; i<=topRegion; i++) regions[i]=null;
		topRegion=newTopRegion;
		topCell=(int)((nBits>>>6)-(topRegion<<LOG2_LONGS_PER_REGION));
		topOffset=(int)(nBits&Utils.LAST_6_BITS_LONG);
	}





	// ------------------------------- STREAM INTERFACE ----------------------------------
	/**
	 * Positions the pointer to bit $bit<nBits$
	 */
	public final void setPosition(long bit) {
		pointerOffset=(int)(bit&Utils.shiftOnesRight[64-6]);
		bit>>>=6;
		pointerCell=(int)(bit&Utils.shiftOnesRight[SIXTYFOUR_MINUS_LOG2_LONGS_PER_REGION]);
		bit>>>=LOG2_LONGS_PER_REGION;
		pointerRegion=(int)bit;
	}


	private final void setPosition(int region, int cell, int offset) {
		pointerRegion=region;
		pointerCell=cell;
		pointerOffset=offset;
	}


	public final long getPosition() {
		final long lPointerRegion = pointerRegion<<LOG2_LONGS_PER_REGION_PLUS_SIX;
		final long lPointerCell = pointerCell<<6;
		return lPointerRegion|lPointerCell|pointerOffset;
	}


	/**
	 * Reads $n \leq 64$ bits and advances the pointer.
	 * Remark: The procedure assumes that there are at least $n$ bits to the right of the
	 * stream pointer: this is not explicitly checked.
	 */
	public final long read(int n) {
		int tmp = 64-pointerOffset;
		final int SIXTYFOUR_MINUS_N = 64-n;
		long out;
		long[] array = regions[pointerRegion];

		if (n<tmp) {
			out=array[pointerCell]>>>(tmp-n);
			pointerOffset+=n;
		}
		else if (n==tmp) {
			out=array[pointerCell];
			if (pointerCell+1<array.length) pointerCell++;
			else {
				pointerRegion++;
				array=regions[pointerRegion];
				pointerCell=0;
			}
			pointerOffset=0;
		}
		else {
			tmp=n-tmp;
			out=array[pointerCell]<<tmp;
			if (pointerCell+1<array.length) pointerCell++;
			else {
				pointerRegion++;
				array=regions[pointerRegion];
				pointerCell=0;
			}
			out|=array[pointerCell]>>>(64-tmp);
			pointerOffset=tmp;
		}
		return out&Utils.shiftOnesRight[SIXTYFOUR_MINUS_N];
	}


	/**
	 * Moves the pointer to $address$ and forces the corresponding bit to one
	 */
	public final void setBit(long address) {
		setPosition(address);
		regions[pointerRegion][pointerCell]|=Utils.oneSelectors1[64-pointerOffset-1];
	}


	public String toString() {
		String out = "";
		String str;
		int i, j;
		for (i=0; i<topRegion; i++) {
			for (j=0; j<LONGS_PER_REGION; j++) {
				str=Long.toBinaryString(regions[i][j]);
				while (str.length()<64) str="0"+str;
				out+=str;
			}
		}
		for (j=0; j<topCell; j++) {
			str=Long.toBinaryString(regions[topRegion][j]);
			while (str.length()<64) str="0"+str;
			out+=str;
		}
		str=Long.toBinaryString(regions[topRegion][topCell]);
		while (str.length()<64) str="0"+str;
		str=str.substring(0,topOffset);
		out+=str;
		return out;
	}


}




// ----------------------------------- Appendix ------------------------------------------

/**
	 * Assumes that the stream is the concatenation of $nBits$-bit, distinct integers, in
	 * increasing order. After the search, the pointer is restored to its initial state.
	 * Remark: assumes that $toIndex>=fromIndex$. No explicit check is performed.
	 *
	 * @param fromIndex rank of the first integer in the sequence of integers; the search
	 * includes this integer. This is not a position in the stream.
	 * @param toIndex rank of the last integer in the sequence of integers; the search
	 * does not include this integer. This is not a position in the stream.
	 * @return the position of $key$ in the stream, or -1 if $key$ does not occur in the
	 * stream. This is not the rank of $key$ in the sequence of integers.
	 */
/*	public final long binarySearch(long fromIndex, long toIndex, long key, int nBits, int log2NBits) {
		long a, z, m, value;
		long backupPosition = getPosition();

		a=fromIndex;
		z=toIndex-1;
		do {
			m=(z+a)>>1;
			setPosition(m<<log2NBits);
			value=read(nBits);
			if (key==value) {
				z=a=m;
				break;
			}
			else if (key<value) z=m-1;
			else a=m+1;
		}
		while (z>a);

		setPosition(backupPosition);
		if (a==z) return a<<log2NBits;
		else return -1;
	}
*/

	/**
	 * Advances the pointer by $n \leq 64$ bits.
	 * Remark: the procedure assumes that there are at least $n$ bits to the right of the
	 * pointer.
	 */
/*	public final void skip(int n) {
		final int tmp = 64-pointerOffset;
		final int SIXTYFOUR_MINUS_N = 64-n;

		if (n<tmp) pointerOffset+=n;
		else {
			if (pointerCell+1<regions[pointerRegion].length) pointerCell++;
			else {
				pointerRegion++;
				pointerCell=0;
			}
			pointerOffset=n-tmp;
		}
	}*/


	/**
	 * Overwrites the $n$ least significant bits of $bits$ at the pointer, and advances
	 * the pointer.
	 */
	/*public final void overwrite(long bits, int n) {
		final int TMP = 64-pointerOffset;
		final int N_MINUS_TMP = n-TMP;
		final long LBITS = bits&Utils.shiftOnesRight[64-n];
		long mask;
		long[] array = regions[pointerRegion];

		mask=Utils.shiftOnesLeft[TMP];
		if (TMP>=n) mask|=Utils.shiftOnesRight[pointerOffset+n];
		array[pointerCell]&=mask;
		if (TMP>=n) array[pointerCell]|=LBITS<<(-N_MINUS_TMP);
		else {
			array[pointerCell]|=LBITS>>>N_MINUS_TMP;
			if (pointerCell+1<array.length) pointerCell++;
			else {
				pointerRegion++; pointerCell=0; pointerOffset=0;
				array=regions[pointerRegion];
			}
			array[pointerCell]&=Utils.shiftOnesRight[N_MINUS_TMP];
			array[pointerCell]|=LBITS<<(64-N_MINUS_TMP);
		}
	}*/