/**
 * Expanding and contracting stack of longs
 */
public class SimpleStream {

	private final int LONGS_PER_REGION;
	private final int LOG2_LONGS_PER_REGION;
	private final int SIXTYFOUR_MINUS_LOG2_LONGS_PER_REGION;

	private long[][] regions;
	private int topRegion, topPointer;  // Last used element in the stack
	private long nElements;  // Total number of elements in the stream


	/**
	 * @param longsPerRegion must be a power of two.
	 */
	public SimpleStream(int longsPerRegion) {
		LONGS_PER_REGION=longsPerRegion;
		LOG2_LONGS_PER_REGION=Utils.log2(longsPerRegion);
		SIXTYFOUR_MINUS_LOG2_LONGS_PER_REGION=64-LOG2_LONGS_PER_REGION;
		regions = new long[1][longsPerRegion];
		topRegion=0; topPointer=-1;
		nElements=0;
	}


	public void clear(boolean deallocate) {
		if (deallocate) regions = new long[1][LONGS_PER_REGION];
		topRegion=0; topPointer=-1;
		nElements=0;
	}


	public void deallocate() {
		int nRegions = regions.length;
		for (int i=0; i<nRegions; i++) regions[i]=null;
		regions=null;
	}


	public final long nElements() {
		return nElements;
	}


	/**
	 * Appends $value$ to the stack, possibly expanding the stack. Expansion implies:
	 * (1) doubling the size of $regions$; (2) copying $regions.length$ pointers (using
	 * the internalized procedure $System.arraycopy$); (3) increasing the number of
	 * allocated bits by one region.
	 */
	public final void push(long value) {
		if (topPointer==LONGS_PER_REGION-1) {
			int nRegions = regions.length;
			if (topRegion==nRegions-1) {
				long[][] newRegions = new long[nRegions<<1][0];
				System.arraycopy(regions,0,newRegions,0,nRegions);
				regions=newRegions;
			}
			topRegion++;
			regions[topRegion] = new long[LONGS_PER_REGION];
			topPointer=0;
		}
		else topPointer++;
		regions[topRegion][topPointer]=value;
		nElements++;
	}


	/**
	 * Removes the last element from the top of the stack, possibly contracting the stack.
	 * The last unused region (if any) is immediately deallocated.
	 */
	public final void pop() {
		nElements--;
		if (topPointer==0) {
			regions[topRegion]=null;
			topRegion--;
			topPointer=LONGS_PER_REGION-1;
		}
		else topPointer--;
	}


	/**
	 * Reads the $i$th element in the stack. The procedure assumes that $i$ is a valid
	 * index: no explicit check is performed.
	 */
	public final long getElementAt(long i) {
		int pointer = (int)(i&Utils.shiftOnesRight[SIXTYFOUR_MINUS_LOG2_LONGS_PER_REGION]);
		i>>>=LOG2_LONGS_PER_REGION;
		return regions[(int)i][pointer];
	}


	/**
	 * Remark: assumes that $toIndex>=fromIndex$. No explicit check is performed.
	 *
	 * @param fromIndex rank of the first integer in the sequence of integers; the search
	 * includes this integer;
	 * @param toIndex rank of the last integer in the sequence of integers; the search
	 * does not include this integer;
	 * @return the rank of $key$ in the sequence of integers, or -1 if $key$ does not
	 * occur in the stream.
	 */
	public final long binarySearch(long fromIndex, long toIndex, long key) {
		long a, z, m, value;

		a=fromIndex;
		z=toIndex-1;
		do {
			m=(z+a)>>1;
			value=getElementAt(m);
			if (key==value) {
				z=a=m;
				break;
			}
			else if (key<value) z=m-1;
			else a=m+1;
		}
		while (z>a);

		if (a==z) return a;
		else return -1;
	}

}