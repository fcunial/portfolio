/**
 * Expanding and contracting stack of fixed-length integers packed into longs, with random
 * access.
 */
public class RigidStream {

	private final int LONGS_PER_REGION;
	private final int LOG2_LONGS_PER_REGION;
	private final int LOG2_LONGS_PER_REGION_PLUS_SIX;
	private final int SIXTYFOUR_MINUS_LOG2_LONGS_PER_REGION;
	private final int bitsPerInt, log2BitsPerInt, sixtyFourMinusBitsPerInt;
	private final long[] oneSelectors;

	private long[][] regions;
	private int topRegion, topCell, topOffset;  // Top of the stack
	private long nBits;  // Total number of bits in the stream
	private long nElements;  // Total number of elements in the stream


	/**
	 * @param longsPerRegion must be a power of two.
	 */
	public RigidStream(int bpi, int longsPerRegion) {
		LONGS_PER_REGION=longsPerRegion;
		LOG2_LONGS_PER_REGION=Utils.log2(longsPerRegion);
		LOG2_LONGS_PER_REGION_PLUS_SIX=LOG2_LONGS_PER_REGION+6;
		SIXTYFOUR_MINUS_LOG2_LONGS_PER_REGION=64-LOG2_LONGS_PER_REGION;
		bitsPerInt=Utils.closestPowerOfTwo(bpi);
		sixtyFourMinusBitsPerInt=64-bitsPerInt;
		switch(bitsPerInt) {
			case 1: log2BitsPerInt=0; oneSelectors=Utils.oneSelectors1; break;
			case 2: log2BitsPerInt=1; oneSelectors=Utils.oneSelectors2; break;
			case 4: log2BitsPerInt=2; oneSelectors=Utils.oneSelectors4; break;
			case 8: log2BitsPerInt=3; oneSelectors=Utils.oneSelectors8; break;
			case 16: log2BitsPerInt=4; oneSelectors=Utils.oneSelectors16; break;
			case 32: log2BitsPerInt=5; oneSelectors=Utils.oneSelectors32; break;
			case 64: log2BitsPerInt=6; oneSelectors=Utils.oneSelectors64; break;
			default: log2BitsPerInt=0; oneSelectors=Utils.oneSelectors1; break;
		}
		regions = new long[1][longsPerRegion];
		topRegion=0; topCell=0; topOffset=0;
		nBits=0; nElements=0;
	}


	public void clear(boolean reallocate) {
		topRegion=0; topCell=0; topOffset=0;
		nBits=0; nElements=0;
		if (reallocate) regions = new long[1][LONGS_PER_REGION];
	}


	public void deallocate() {
		int nRegions = regions.length;
		for (int i=0; i<nRegions; i++) regions[i]=null;
		regions=null;
	}


	/**
	 * @return the number of bits in the stream
	 */
	public final long nBits() {
		return nBits;
	}


	public final long nElements() {
		return nElements;
	}


	/**
	 * Appends the $bitsPerInt$ least significant bits of $bits$ to the stack, possibly
	 * expanding it. Expansion implies: (1) doubling the size of $regions$; (2) copying
	 * $regions.length$ pointers (using the internalized procedure $System.arraycopy$);
	 * (3) increasing the number of allocated bits by one region.
	 */
	public final void push(long bits) {
		final long lBits = bits&Utils.shiftOnesRight[sixtyFourMinusBitsPerInt];
		int tmp = 64-topOffset;
		final int nRegions;
		long[] array = regions[topRegion];
		nElements++;
		nBits+=bitsPerInt;
//System.out.println("topRegion="+topRegion+" regions="+regions+" regions[topRegion]="+regions[topRegion]);
		if (tmp>0) {
			array[topCell]&=Utils.shiftOnesLeft[tmp];
			array[topCell]|=lBits<<(tmp-bitsPerInt);
			topOffset+=bitsPerInt;
		}
		else {
			if (topCell+1<array.length) topCell++;
			else {
				nRegions=regions.length;
				if (topRegion==nRegions-1) {
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
			array[topCell]|=lBits<<sixtyFourMinusBitsPerInt;
			topOffset=bitsPerInt;
		}
	}


	/**
	 * Removes the last element from the top of the stack, possibly contracting the stack.
	 * The last unused region (if any) is immediately deallocated.
	 */
	public final void pop() {
if (nBits==0) System.out.println("popping from an empty stack!!!!");
		nElements--;
		nBits-=bitsPerInt;

boolean probe = topRegion==0 && topCell==0 && topOffset==0;

		int newTopRegion = (int)(nBits>>>(LOG2_LONGS_PER_REGION+6));
		for (int i=newTopRegion+1; i<=topRegion; i++) regions[i]=null;
		topRegion=newTopRegion;
		topCell=(int)((nBits>>>6)-(topRegion<<LOG2_LONGS_PER_REGION));
		topOffset=(int)(nBits&Utils.LAST_6_BITS_LONG);

if (probe) System.out.println("after popping the last element: nBits="+nBits+" topRegion="+topRegion+" topCell="+topCell+" topOffset="+topOffset);

/*
		if (topOffset!=0) {
			topOffset-=bitsPerInt;
			return;
		}
		if (topCell!=0) {
			topCell--;
			topOffset=sixtyFourMinusBitsPerInt;
			return;
		}
		if (topRegion!=0) {
			regions[topRegion]=null;
			topRegion--;
			topCell=LONGS_PER_REGION-1;
			topOffset=sixtyFourMinusBitsPerInt;
			return;
		}
*/
	}


	/**
	 * Reads the $i$th element in the stack. The procedure assumes that $i$ is a valid
	 * index: no explicit check is performed.
	 */
	public final long getElementAt(long i) {
		i<<=log2BitsPerInt;
		int offset = (int)(i&Utils.shiftOnesRight[64-6]);
		i>>>=6;
		int cell = (int)(i&Utils.shiftOnesRight[SIXTYFOUR_MINUS_LOG2_LONGS_PER_REGION]);
		i>>>=LOG2_LONGS_PER_REGION;
		return (regions[(int)i][cell]>>>sixtyFourMinusBitsPerInt-offset)&oneSelectors[0];
	}

}