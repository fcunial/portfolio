/**
 * A rigid array of integers, encoded each in a fixed number of bits that equals a power
 * of two. In what follows, we denote with $v$ the bit string that results from the
 * concatenation of all integers in the array. Since Java allows only $int$ to index an
 * array, this structure can store a DNA string of length at most 68.719.476.704 (68Gbp)
 * and a string on alphabet of size $\sigma \in [5..16]$ of length at most 34.359.738.352
 * (34Gbp). This is not enough, for example, to store the largest metagenome available in
 * MG-RAST on April 2, 2015, which has length 56.396.775.865.
 *
 * Remark: mutual exclusion must be ensured by the caller. In particular, the current
 * implementation does not allow to lock specific substrings of the array, thus requiring
 * each thread to lock the whole array even when it acts on a small region.
 *
 * Remark: there might be the need to re-implement all $IntArray$ to store from right
 * to left inside a long, since this could waive some subtractions.
 */
public class IntArray {

	private final long[] oneSelectors, zeroSelectors;
	public final int bitsPerInt, log2BitsPerInt, sixtyFourMinusBitsPerInt, intsPerLong;
	public long totalBits;
	protected long[] array;  // Access is $protected$ to allow cloning
	protected int lastCell;  // First cell available for insertion. Access is protected to allow pasting.
	protected int lastOffset;  // First offset available for insertion. Access is protected to allow pasting.
	private long nElements;
	protected int pointerCell, pointerOffset;  // Pointer used by the stream interface. Access is $protected$ to allow global shifting.


	/**
	 * @param maxLength maximum number of elements the array can contain;
	 * @param bpi number of bits to represent an integer, $1 \leq bpi \leq 64$. To avoid
	 * multiplications/divisions, the actual number of bits per integer used by the array
	 * is $Utils.closestPowerOfTwo(bpi)$.
	 * @param fillWithZeros inserts $maxLength$ zeros in the array.
	 */
	public IntArray(long maxLength, int bpi, boolean fillWithZeros) {
		int nCells;
		bitsPerInt=Utils.closestPowerOfTwo(bpi);
		sixtyFourMinusBitsPerInt=64-bitsPerInt;
		switch(bitsPerInt) {
			case 1: log2BitsPerInt=0; intsPerLong=64; oneSelectors=Utils.oneSelectors1; zeroSelectors=Utils.zeroSelectors1; break;
			case 2: log2BitsPerInt=1; intsPerLong=32; oneSelectors=Utils.oneSelectors2; zeroSelectors=Utils.zeroSelectors2; break;
			case 4: log2BitsPerInt=2; intsPerLong=16; oneSelectors=Utils.oneSelectors4; zeroSelectors=Utils.zeroSelectors4; break;
			case 8: log2BitsPerInt=3; intsPerLong=8; oneSelectors=Utils.oneSelectors8; zeroSelectors=Utils.zeroSelectors8; break;
			case 16: log2BitsPerInt=4; intsPerLong=4; oneSelectors=Utils.oneSelectors16; zeroSelectors=Utils.zeroSelectors16; break;
			case 32: log2BitsPerInt=5; intsPerLong=2; oneSelectors=Utils.oneSelectors32; zeroSelectors=Utils.zeroSelectors32; break;
			case 64: log2BitsPerInt=6; intsPerLong=1; oneSelectors=Utils.oneSelectors64; zeroSelectors=Utils.zeroSelectors64; break;
			default: log2BitsPerInt=0; intsPerLong=64; oneSelectors=Utils.oneSelectors1; zeroSelectors=Utils.zeroSelectors1; break;
		}
		nCells=(int)( ((maxLength<<log2BitsPerInt)>>6)+1 );
		array = new long[nCells];
		if (fillWithZeros) {
			nElements=maxLength;
			totalBits=maxLength<<log2BitsPerInt;
			lastCell=nCells-1;
			lastOffset=(int)( (maxLength<<log2BitsPerInt)&Utils.LAST_6_BITS_LONG );
		}
		else {
			nElements=0;
			totalBits=0;
			lastCell=0;
			lastOffset=0;
		}
		pointerCell=0; pointerOffset=0;
	}


	/**
	 * @param bitsPerInt $1 \leq bitsPerInt \leq 64$, not necessarily a power of two.
	 */
	public IntArray(long maxLength, int bitsPerInt) {
		this(maxLength,bitsPerInt,false);
	}


	/**
	 * Builds a tight copy of this object, which can store at most $nElements$ elements.
	 */
	public final IntArray clone() {
		IntArray out = new IntArray(nElements,bitsPerInt,true);
		long[] outArray = out.array;
		for (int i=0; i<=lastCell; i++) outArray[i]=array[i];
		return out;
	}


	/**
	 * Remark: bits in the array are not explicitly set to zero.
	 *
	 * @param forceLastOffset allows to set the initial value of $lastOffset$, in order to
	 * give a shift of $< 64$ bits to the whole array. $forceLastOffset$ is assumed to be a
	 * multiple of $bitsPerInt$.
	 */
	public final void clear(int forceLastOffset) {
		lastCell=0;
		lastOffset=forceLastOffset;
		nElements=0;
	}


	/**
	 * Remark: bits in the array are not explicitly set to zero.
	 */
	public final void clear() {
		lastCell=0;
		lastOffset=0;
		nElements=0;
	}


	/**
	 * Reverses the order of the bits in each cell of $array$ separately
	 */
	public final void reverse() {
		for (int i=0; i<=lastCell; i++) array[i]=Long.reverse(array[i]);
	}


	public final void deallocate() {
		array=null;
	}


	public final long length() {
		return nElements;
	}


	public final void print() {
		//System.out.println("bitsPerInt="+bitsPerInt+" nElements="+nElements);//+" lastCell="+lastCell+" lastOffset="+lastOffset);
		//for (int i=0; i<nElements; i++) System.out.println(i+": "+getElementAt(i));
		for (long i=0; i<nElements; i++) System.out.print(getElementAt(i)+" ");
		System.out.println();
	}


	public final void printBits() {
		//System.out.println("bitsPerInt="+bitsPerInt+" nElements="+nElements+" lastCell="+lastCell+" lastOffset="+lastOffset);
		for (int i=0; i<=lastCell; i++) System.out.print(Long.toBinaryString(array[i])+"|");
		System.out.println();
	}


	/**
	 * All numbers outside $[0..3]$ are interpreted as "n".
	 */
	public static final void printAsDNASuffixes(IntArray string) {
		char d = 'n';
		long i, j, c, stringLength, suffix;
		String label;
		stringLength=string.length();
		for (i=0; i<stringLength; i++) {
			suffix=string.getElementAt(i);
			label=suffix+"";
			while (label.length()<3) label=" "+label;
			System.out.print(label+": ");
			for (j=0; j<stringLength; j++) {
				if (suffix+j==stringLength) System.out.print("$");
				else {
					c=string.getElementAt((suffix+j)%stringLength);
					switch ((int)c) {
						case 0: d='a'; break;
						case 1: d='c'; break;
						case 2: d='g'; break;
						case 3: d='t'; break;
					}
					System.out.print(d+"");
				}
			}
			System.out.println();
		}
	}
	
	
	public static final void printAsDNA(IntArray string) {
		char d = 'n';
		long i, c, stringLength;
		stringLength=string.length();
		for (i=0; i<stringLength; i++) {
			c=string.getElementAt(i);
			switch ((int)c) {
				case 0: d='a'; break;
				case 1: d='c'; break;
				case 2: d='g'; break;
				case 3: d='t'; break;
			}
			System.out.print(d+"");
		}
	}


	/**
	 * Positions the pointer at the beginning of element $i$
	 */
	public final void setPointer(long i) {
		i<<=log2BitsPerInt;
		pointerCell=(int)( i>>>6 );
		pointerOffset=(int)( i&Utils.LAST_6_BITS_LONG );
	}


	/**
	 * Pastes $block$ into $array$ starting from the current position of the pointer.
	 * All values in $array$ covered by corresponding values in $block$ are overwritten.
	 *
	 * @param block the first $pointerOffset$ bits are assumed to be zero, i.e. $block$
	 * is assumed to be globally right-shifted by $pointerOffset$ bits.
	 */
	public final void pasteAtPointer(IntArray block) {
		final int blockLastCell, blockLastOffset, pointerCellPlusBlockLastCell;
		long source;

		// First cell
		source=block.array[0]&Utils.shiftOnesRight[pointerOffset];
		blockLastCell=block.lastCell;
		blockLastOffset=block.lastOffset;
		if (blockLastCell==0) {
			source&=Utils.shiftOnesLeft[64-blockLastOffset];
			array[pointerCell]&=Utils.shiftOnesLeft[64-pointerOffset]|Utils.shiftOnesRight[blockLastOffset];
			array[pointerCell]|=source;
			return;
		}
		else {
			array[pointerCell]&=Utils.shiftOnesLeft[64-pointerOffset];
			array[pointerCell]|=source;
		}
		// Intermediate cells
		if (blockLastCell>1) System.arraycopy(block.array,1,array,pointerCell+1,blockLastCell-1);
		// Last cell
		pointerCellPlusBlockLastCell=pointerCell+blockLastCell;
		source=block.array[blockLastCell]&Utils.shiftOnesLeft[64-blockLastOffset];
		array[pointerCellPlusBlockLastCell]&=Utils.shiftOnesRight[blockLastOffset];
		array[pointerCellPlusBlockLastCell]|=source;
	}


	public final void push(long value) {
		int tmp;
		setElementAt(lastCell,lastOffset,value);
		if (lastOffset==sixtyFourMinusBitsPerInt) {
			lastCell++;
			lastOffset=0;
		}
		else lastOffset+=bitsPerInt;
		nElements++;
		totalBits+=bitsPerInt;
	}


	public final void pushFromRight(long value) {
		setElementFromRightAt(lastCell,lastOffset,value);
		if (lastOffset==sixtyFourMinusBitsPerInt) {
			lastCell++;
			lastOffset=0;
		}
		else lastOffset+=bitsPerInt;
		nElements++;
		totalBits+=bitsPerInt;
	}


	public final long pop() {
		if (lastOffset<bitsPerInt) {
			lastCell--;
			lastOffset=sixtyFourMinusBitsPerInt+lastOffset;
		}
		else lastOffset-=bitsPerInt;
		nElements--;
		return getElementAt(lastCell,lastOffset);
	}


	/**
	 * Remark: The procedure avoids loading the most significant bit because it is
	 * interpreted as a sign by Java: removing it allows to implement lexicographic
	 * comparisons of binary strings with the numerical operators $>$ and $<$.
	 *
	 * @return $0 \cdot v[i..i+63)$, assuming that $v[j]=0$ for all
	 * $j \geq bitsPerInt*nElements$. This assumption simplifies cached LCP computations,
	 * but it induces an infinite loop when sorting suffixes $(0^{bitsPerInt})^{x}$ and
	 * $(0^{bitsPerInt})^{y}$ if we don't take into account the starting position of a
	 * suffix.
	 */
	public final long load63(long i) {
		int cell = (int)( i>>>6 );
		int offset = (int)( i&Utils.LAST_6_BITS );
		if (cell>lastCell || (cell==lastCell&&offset>=lastOffset)) return 0x0L;
		if (offset==0) {
			long out = array[cell];
			if (cell==lastCell) out&=Utils.shiftOnesLeft[64-lastOffset];
			return out>>>1;
		}
		if (offset==1) {
			long out = array[cell]&Utils.zeroSelectors1[63];
			if (cell==lastCell) out&=Utils.shiftOnesLeft[64-lastOffset];
			return out;
		}
		int sixtyFourMinusOffset = 64-offset;
		long out = (array[cell]<<(offset-1))&Utils.zeroSelectors1[63];
		if (cell<lastCell) {
			out|=array[cell+1]>>>(sixtyFourMinusOffset+1);
			if (cell==lastCell-1) {
				int measure = 1+sixtyFourMinusOffset+lastOffset;
				if (measure<64) out&=Utils.shiftOnesLeft[64-measure];
			}
		}
		return out;
	}


	public final long getElementAt(long i) {
		i<<=log2BitsPerInt;
		return (array[(int)(i>>>6)]>>>64-(int)(i&Utils.LAST_6_BITS_LONG)-bitsPerInt)&oneSelectors[0];
	}


	private final long getElementAt(int cell, int offset) {
		return (array[cell]>>>64-offset-bitsPerInt)&oneSelectors[0];
	}


	public final void setElementAt(long i, long value) {
		final int cell, offset;
		int tmp;
		i<<=log2BitsPerInt;
		cell=(int)(i>>>6);
		offset=(int)(i&Utils.LAST_6_BITS_LONG);
		value&=oneSelectors[0];
		tmp=64-offset;
		if (tmp==bitsPerInt) {
			array[cell]&=zeroSelectors[0];
			array[cell]|=value;
		}
		else {
			tmp=sixtyFourMinusBitsPerInt-offset;
//System.out.println("cell="+cell+" offset="+offset+" bitsPerInt="+bitsPerInt+" tmp="+tmp);
			array[cell]&=zeroSelectors[tmp];
			value<<=tmp;
			array[cell]|=value;
		}
	}


	private final void setElementAt(int cell, int offset, long value) {
		int tmp;
		value&=oneSelectors[0];
		tmp=64-offset;
		if (tmp==bitsPerInt) {
			array[cell]&=zeroSelectors[0];
			array[cell]|=value;
		}
		else {
			tmp=sixtyFourMinusBitsPerInt-offset;
			array[cell]&=zeroSelectors[tmp];
			value<<=tmp;
			array[cell]|=value;
		}
	}


	public final void setElementFromRightAt(long i, long value) {
		final int cell, offset;
		int tmp;
		i<<=log2BitsPerInt;
		cell=(int)(i>>>6);
		offset=(int)(i&Utils.LAST_6_BITS_LONG);
		value&=oneSelectors[0];
		array[cell]&=zeroSelectors[offset];
		value<<=offset;
		array[cell]|=value;
	}


	/**
	 * Stores the last $bitsPerInt$ bits of $value$ in a cell of $array$, from right to
	 * left. This procedure has no counterpart from the point of view of access.
	 */
	private final void setElementFromRightAt(int cell, int offset, long value) {
		value&=oneSelectors[0];
		array[cell]&=zeroSelectors[offset];
		value<<=offset;
		array[cell]|=value;
	}


	/**
	 * Assumes that the number after the increment still fits $bitsPerInt$ bits
	 */
	public final void incrementElementAt(long i) {
		final int cell, offset;
		int tmp;
		long value;
		i<<=log2BitsPerInt;
		cell=(int)( i>>>6 );
		offset=(int)( i&Utils.LAST_6_BITS_LONG );
		tmp=64-offset;
		if (tmp==bitsPerInt) {
			value=(array[cell]&oneSelectors[0])+1;
			array[cell]&=zeroSelectors[0];
			array[cell]|=value;
		}
		else {
			tmp=sixtyFourMinusBitsPerInt-offset;
			value=((((array[cell]&oneSelectors[tmp])>>>tmp)+1)<<tmp)&oneSelectors[tmp];
			array[cell]&=zeroSelectors[tmp];
			array[cell]|=value;
		}
	}


	/**
	 * Assumes that the number after the increment still fits $bitsPerInt$ bits
	 */
	private final void incrementElementAt(int cell, int offset) {
		int tmp;
		long value;
		tmp=64-offset;
		if (tmp==bitsPerInt) {
			value=(array[cell]&oneSelectors[0])+1;
			array[cell]&=zeroSelectors[0];
			array[cell]|=value;
		}
		else {
			tmp=sixtyFourMinusBitsPerInt-offset;
			value=((((array[cell]&oneSelectors[tmp])>>>tmp)+1)<<tmp)&oneSelectors[tmp];
			array[cell]&=zeroSelectors[tmp];
			array[cell]|=value;
		}
	}


	/**
	 * Swaps the numbers at positions $i$ and $j$
	 */
	public final void swap(long i, long j) {
		final int iCell, jCell, iOffset, jOffset;
		final int sixtyFourMinusBitsPerIntMinusIOffset, sixtyFourMinusBitsPerIntMinusJOffset;
		long iValue, jValue;
		i<<=log2BitsPerInt;
		iCell=(int)( i>>>6 );
		iOffset=(int)( i&Utils.LAST_6_BITS );
		j<<=log2BitsPerInt;
		jCell=(int)( j>>>6 );
		jOffset=(int)( j&Utils.LAST_6_BITS );
		sixtyFourMinusBitsPerIntMinusIOffset=sixtyFourMinusBitsPerInt-iOffset;
		sixtyFourMinusBitsPerIntMinusJOffset=sixtyFourMinusBitsPerInt-jOffset;

		iValue=(array[iCell]&oneSelectors[sixtyFourMinusBitsPerIntMinusIOffset])>>>sixtyFourMinusBitsPerIntMinusIOffset;
		array[iCell]&=zeroSelectors[sixtyFourMinusBitsPerIntMinusIOffset];
		jValue=(array[jCell]&oneSelectors[sixtyFourMinusBitsPerIntMinusJOffset])>>>sixtyFourMinusBitsPerIntMinusJOffset;
		array[jCell]&=zeroSelectors[sixtyFourMinusBitsPerIntMinusJOffset];
		array[jCell]|=iValue<<sixtyFourMinusBitsPerIntMinusJOffset;
		array[iCell]|=jValue<<sixtyFourMinusBitsPerIntMinusIOffset;
	}


	/**
	 * Bit-parallel swap of disjoint intervals $[i..i+n-1]$ and $[j..j+n-1]$.
	 */
	public final void vecswap(long i, long j, long n) {
		final int iCell, jCell, iOffset, jOffset, lastXCell, xOffset, yOffset, diff;
		final int sixtyFourMinusDiff, sixtyFourMinusXOffset, sixtyFourMinusYOffset;
		int k, sixtyFourMinusK, xCell, yCell;
		long xBuffer, yBuffer, mask, nBits, swappedBits;
		nBits=n<<log2BitsPerInt;
		i<<=log2BitsPerInt;
		iCell=(int)( i>>>6 );
		iOffset=(int)( i&Utils.LAST_6_BITS_LONG );
		j<<=log2BitsPerInt;
		jCell=(int)( j>>>6 );
		jOffset=(int)( j&Utils.LAST_6_BITS );
		if (jOffset>iOffset) {
			xCell=jCell;
			xOffset=jOffset;
			yCell=iCell;
			yOffset=iOffset;
			diff=jOffset-iOffset;
			lastXCell=(int)( (j+nBits)>>6 );
		}
		else {
			xCell=iCell;
			xOffset=iOffset;
			yCell=jCell;
			yOffset=jOffset;
			diff=iOffset-jOffset;
			lastXCell=(int)( (i+nBits)>>6 );
		}
		sixtyFourMinusDiff=64-diff;
		sixtyFourMinusXOffset=64-xOffset;
		sixtyFourMinusYOffset=64-yOffset;
		swappedBits=0;

		// First $xCell$
		if (xOffset!=0) {
			if (xCell==lastXCell) {
				mask=Utils.shiftOnesRight[xOffset]&Utils.shiftOnesLeft[sixtyFourMinusXOffset-(int)nBits];
				xBuffer=array[xCell]&mask;
				mask=Utils.shiftOnesLeft[sixtyFourMinusXOffset]|Utils.shiftOnesRight[xOffset+(int)nBits];
				array[xCell]&=mask;
				mask=Utils.shiftOnesRight[yOffset]&Utils.shiftOnesLeft[sixtyFourMinusYOffset-(int)nBits];
				yBuffer=array[yCell]&mask;
				mask=Utils.shiftOnesLeft[sixtyFourMinusYOffset]|Utils.shiftOnesRight[yOffset+(int)nBits];
				array[yCell]&=mask;
				array[yCell]|=xBuffer<<diff;
				array[xCell]|=yBuffer>>>diff;
				return;
			}
			else {
				xBuffer=array[xCell]&Utils.shiftOnesRight[xOffset];
				array[xCell]&=Utils.shiftOnesLeft[sixtyFourMinusXOffset];
				mask=Utils.shiftOnesRight[yOffset]&Utils.shiftOnesLeft[diff];
				yBuffer=array[yCell]&mask;
				mask=Utils.shiftOnesLeft[sixtyFourMinusYOffset]|Utils.shiftOnesRight[sixtyFourMinusDiff];
				array[yCell]&=mask;
				array[yCell]|=xBuffer<<diff;
				array[xCell]|=yBuffer>>>diff;
				swappedBits=sixtyFourMinusXOffset;
				xCell++;
				if (diff==0) yCell++;
			}
		}

		// Middle $xCell$s
		if (diff==0) {
			while (xCell<lastXCell) {
				xBuffer=array[xCell];
				array[xCell]=array[yCell];
				array[yCell]=xBuffer;
				xCell++; yCell++;
				swappedBits+=64;
			}
		}
		else {
			while (xCell<lastXCell) {
				xBuffer=array[xCell];
				yBuffer=array[yCell]<<sixtyFourMinusDiff;
				array[yCell]&=Utils.shiftOnesLeft[diff];
				array[yCell]|=xBuffer>>>sixtyFourMinusDiff;
				yBuffer|=array[yCell+1]>>>diff;
				array[yCell+1]&=Utils.shiftOnesRight[sixtyFourMinusDiff];
				array[yCell+1]|=xBuffer<<diff;
				array[xCell]=yBuffer;
				xCell++; yCell++;
				swappedBits+=64;
			}
		}

		// Last $xCell$
		k=(int)( nBits-swappedBits );
		if (k==0) return;
		sixtyFourMinusK=64-k;
		xBuffer=array[xCell]&Utils.shiftOnesLeft[sixtyFourMinusK];
		array[xCell]&=Utils.shiftOnesRight[k];
		if (diff==0) {
			yBuffer=array[yCell]&Utils.shiftOnesLeft[sixtyFourMinusK];
			array[yCell]&=Utils.shiftOnesRight[k];
			array[yCell]|=xBuffer;
			array[xCell]|=yBuffer;
			return;
		}
		if (k==diff) {
			yBuffer=array[yCell]<<sixtyFourMinusDiff;
			array[yCell]&=Utils.shiftOnesLeft[diff];
			array[yCell]|=xBuffer>>>sixtyFourMinusDiff;
			array[xCell]|=yBuffer;
		}
		else if (k<diff) {
			mask=Utils.shiftOnesRight[sixtyFourMinusDiff]&Utils.shiftOnesLeft[diff-k];
			yBuffer=array[yCell]&mask;
			mask=Utils.shiftOnesLeft[diff]|Utils.shiftOnesRight[sixtyFourMinusDiff+k];
			array[yCell]&=mask;
			array[yCell]|=xBuffer>>>sixtyFourMinusDiff;
			array[xCell]|=yBuffer<<sixtyFourMinusDiff;
		}
		else {
			yBuffer=array[yCell]<<sixtyFourMinusDiff;
			array[yCell]&=Utils.shiftOnesLeft[diff];
			array[yCell]|=xBuffer>>>sixtyFourMinusDiff;
			yBuffer|=array[yCell+1]>>>diff;
			yBuffer&=Utils.shiftOnesLeft[sixtyFourMinusK];
			array[yCell+1]&=Utils.shiftOnesRight[k-diff];
			array[yCell+1]|=xBuffer<<diff;
			array[xCell]|=yBuffer;
		}
	}


	/**
	 * Implements $setElementAt(i+1,getElementAt(i))$. Used by $insertionSort$.
	 */
	public final void copyToRight(long i) {
		final int cell;
		int nextCell, offset, sixtyFourMinusBitsPerIntMinusOffset;
		long value;
		i<<=log2BitsPerInt;
		cell=(int)( i>>>6 );
		offset=(int)( i&Utils.LAST_6_BITS_LONG );

		if (offset==sixtyFourMinusBitsPerInt) {
			value=array[cell]&oneSelectors[0];
			nextCell=cell+1;
			array[nextCell]&=Utils.shiftOnesRight[bitsPerInt];
			array[nextCell]|=value<<sixtyFourMinusBitsPerInt;
		}
		else {
			sixtyFourMinusBitsPerIntMinusOffset=sixtyFourMinusBitsPerInt-offset;
			value=array[cell]&oneSelectors[sixtyFourMinusBitsPerIntMinusOffset];
			offset=offset+bitsPerInt;
			sixtyFourMinusBitsPerIntMinusOffset=sixtyFourMinusBitsPerInt-offset;
			array[cell]&=zeroSelectors[sixtyFourMinusBitsPerIntMinusOffset];
			array[cell]|=value>>>bitsPerInt;
		}
	}


	/**
	 * Basic linear search with limited bit parallelism
	 *
	 * @todo Should be adapted to work on a given interval.
	 *
	 * @return -1 if $value$ does not occur in $array$; otherwise, the first position at
	 * which $value$ occurs.
	 */
	public final long linearSearch(long value) {
		int i, cell, offset, zeros, top;
		long longValue, probe, result;

		// Building XOR mask
		longValue=value&oneSelectors[0];
		probe=0;
		for (i=0; i<64; i+=bitsPerInt) {
			probe|=longValue;
			longValue<<=bitsPerInt;
		}
		// Intermediate cells
		cell=0;
		while (cell<lastCell) {
			result=array[cell]^probe;
			for (i=0; i<=sixtyFourMinusBitsPerInt; i+=bitsPerInt) {
				if ((result&oneSelectors[sixtyFourMinusBitsPerInt-i])==0) return ((((long)cell)<<6)+i)>>>log2BitsPerInt;
			}
			cell++;
		}
		// Last cell
		result=array[cell]^probe;
		top=lastOffset-bitsPerInt;
		for (i=0; i<=top; i+=bitsPerInt) {
			if ((result&oneSelectors[sixtyFourMinusBitsPerInt-i])==0) return ((((long)cell)<<6)+i)>>>log2BitsPerInt;
		}
		return -1;
	}


	/**
	 * Basic binary search with no bit parallelism
	 *
	 * @return -1 if $value$ does not occur in $array[first..last]$; otherwise, the first
	 * position at which $value$ occurs.
	 */
	public final long binarySearch(long value, long first, long last) {
		long mid, midValue;
	  	while (last>=first) {
			mid=(last+first)>>1;
			midValue=getElementAt(mid);
			if (value>midValue) first=mid+1;
			else if (value<midValue) last=mid-1;
			else return mid;
		}
		return -1;
	}


	/**
	 * Longest common prefix between suffix $v[x..]$ and suffix $v[y..]$.
	 *
	 * @param order TRUE=the most significant bit of the output is set to 1 if
	 * $v[x..]>v[y..]$ in the lexicographic order, to 0 if $v[x..]< v[y..]$; the length of
	 * the LCP is encoded in the remaining bits.
	 */
	public final long lcp(long x, long y, boolean order) {
		final boolean xSmallerThanY = x<y;
		x<<=log2BitsPerInt;
		final int xCell = (int)( x>>>6 );
		final int xOffset = (int)( x&Utils.LAST_6_BITS );
		y<<=log2BitsPerInt;
		final int yCell = (int)( y>>>6 );
		final int yOffset = (int)( y&Utils.LAST_6_BITS );
		long xBuffer, yBuffer;
		if (xOffset==0) xBuffer=array[xCell];
		else {
			xBuffer=array[xCell]<<xOffset;
			if (xCell<lastCell) xBuffer|=array[xCell+1]>>>(64-xOffset);
		}
		if (yOffset==0) yBuffer=array[yCell];
		else {
			yBuffer=array[yCell]<<yOffset;
			if (yCell<lastCell) yBuffer|=array[yCell+1]>>>(64-yOffset);
		}
		return lcp(xCell,xOffset,yCell,yOffset,xSmallerThanY,order,xBuffer,yBuffer,false);
	}


	/**
	 * Longest common prefix between suffix $v[x..]$ and suffix $v[y..]$ starting from
	 * pre-loaded, 63-bit buffers.
	 *
	 * @param order TRUE=the most significant bit of the output is set to 1 if
	 * $v[x..]>v[y..]$ in the lexicographic order, to 0 if $v[x..]< v[y..]$; the length of
	 * the LCP is encoded in the remaining bits;
	 * @param bufferX $0 \cdot v[x..x+63)$ ($v$ is assumed to be padded with an infinite
	 * number of ones);
	 * @param bufferY $0 \cdot v[y..y+63)$ ($v$ is assumed to be padded with an infinite
	 * number of ones).
	 */
	public final long lcp63(long x, long y, boolean order, long bufferX, long bufferY) {
		final boolean xSmallerThanY = x<y;
		x<<=log2BitsPerInt; y<<=log2BitsPerInt;
		return lcp((int)(x>>>6),(int)(x&Utils.LAST_6_BITS_LONG),(int)(y>>>6),(int)(y&Utils.LAST_6_BITS_LONG),xSmallerThanY,order,bufferX,bufferY,true);
	}


	/**
	 * Bit-parallel LCP starting from pre-loaded buffers. Assumes that the LCP is at most
	 * $2^{31}-1$. Could be improved using e.g. a difference cover sample to handle long
	 * LCPs \cite{karkkainen2007fast}.
	 *
	 * @param xSmallerThanY $x< y$;
	 * @param order TRUE=the most significant bit of the output is set to 1 if
	 * $v[x..]>v[y..]$ in the lexicographic order, to 0 if $v[x..]< v[y..]$; the length of
	 * the LCP is encoded in the remaining bits;
	 * @param sixtyThreeBitBuffers TRUE=$bufferX=0 \cdot v[x..x+63)$,
	 * $bufferY=0 \cdot v[y..y+63)$ ($v$ is assumed to be padded with an infinite number
	 * of zeros); FALSE=buffers contain 64 bits.
	 * @return the number of common \emph{bits} between $v[x..]$ and $v[y..]$.
	 */
	private final long lcp(int xCell, int xOffset, int yCell, int yOffset, boolean xSmallerThanY, boolean order, long bufferX, long bufferY, boolean sixtyThreeBitBuffers) {
		boolean xLexGreaterThanY = xSmallerThanY;
		int tmp, bitsToCompare, leadingZeros;
		long xBuffer=bufferX, yBuffer=bufferY;
		long lcpBits;

		// First iteration: buffers could contain 63 bits.
		lcpBits=0;
		if (sixtyThreeBitBuffers) {
			// Deciding the number of bits to compare in the buffers
			bitsToCompare=63;
			if (xCell==lastCell-1 && xOffset>=lastOffset+2) bitsToCompare=64-xOffset+lastOffset;
			else if (xCell==lastCell) bitsToCompare=lastOffset-xOffset;
			if (yCell==lastCell-1 && yOffset>=lastOffset+2) {
				tmp=64-yOffset+lastOffset;
				if (tmp<bitsToCompare) bitsToCompare=tmp;
			}
			else if (yCell==lastCell) {
				tmp=lastOffset-yOffset;
				if (tmp<bitsToCompare) bitsToCompare=tmp;
			}

			// Comparing buffers
			leadingZeros=Long.numberOfLeadingZeros(xBuffer^yBuffer)-1;
			if (leadingZeros<bitsToCompare) {
				lcpBits=leadingZeros;
				xLexGreaterThanY=(yBuffer&Utils.oneSelectors1[64-leadingZeros-2])==0x0L?true:false;
				lcpBits>>>=log2BitsPerInt;
				if (order&&xLexGreaterThanY) lcpBits|=Utils.MSB_LONG_ONE;
				return lcpBits;
			}
			else {
				lcpBits=bitsToCompare;
				if (bitsToCompare<63) {
					lcpBits>>>=log2BitsPerInt;
					if (order&&xLexGreaterThanY) lcpBits|=Utils.MSB_LONG_ONE;
					return lcpBits;
				}
			}

			// Reloading 64-bit buffers
			if (xOffset==0) xOffset=63;
			else { xCell++; xOffset--; }
			if (yOffset==0) yOffset=63;
			else { yCell++; yOffset--; }
			if (xCell>lastCell || (xCell==lastCell&&xOffset>=lastOffset) || yCell>lastCell || (yCell==lastCell&&yOffset>=lastOffset)) {
				lcpBits>>>=log2BitsPerInt;
				if (order&&xLexGreaterThanY) lcpBits|=Utils.MSB_LONG_ONE;
				return lcpBits;
			}
			if (xOffset==0) xBuffer=array[xCell];
			else {
				xBuffer=array[xCell]<<xOffset;
				if (xCell<lastCell) xBuffer|=array[xCell+1]>>>(64-xOffset);
			}
			if (yOffset==0) yBuffer=array[yCell];
			else {
				yBuffer=array[yCell]<<yOffset;
				if (yCell<lastCell) yBuffer|=array[yCell+1]>>>(64-yOffset);
			}
		}

		// Normal iterations: 64-bit buffers.
		while (true) {
			// Deciding the number of bits to compare in the buffers
			bitsToCompare=64;
			if (xCell==lastCell-1 && xOffset>=lastOffset+1) bitsToCompare=64-xOffset+lastOffset;
			else if (xCell==lastCell) bitsToCompare=lastOffset-xOffset;
			if (yCell==lastCell-1 && yOffset>=lastOffset+1) {
				tmp=64-yOffset+lastOffset;
				if (tmp<bitsToCompare) bitsToCompare=tmp;
			}
			else if (yCell==lastCell) {
				tmp=lastOffset-yOffset;
				if (tmp<bitsToCompare) bitsToCompare=tmp;
			}

			// Comparing buffers
			leadingZeros=Long.numberOfLeadingZeros(xBuffer^yBuffer);
			if (leadingZeros<bitsToCompare) {
				lcpBits+=leadingZeros;
				xLexGreaterThanY=(yBuffer&Utils.oneSelectors1[64-leadingZeros-1])==0x0L?true:false;
				break;
			}
			else {
				lcpBits+=bitsToCompare;
				if (bitsToCompare<64) break;
			}

			// Reloading buffers
			xCell++; yCell++;
			if (xCell>lastCell || (xCell==lastCell&&xOffset>=lastOffset)) break;
			if (yCell>lastCell || (yCell==lastCell&&yOffset>=lastOffset)) break;
			if (xOffset==0) xBuffer=array[xCell];
			else {
				xBuffer=array[xCell]<<xOffset;
				if (xCell<lastCell) xBuffer|=array[xCell+1]>>>(64-xOffset);
			}
			if (yOffset==0) yBuffer=array[yCell];
			else {
				yBuffer=array[yCell]<<yOffset;
				if (yCell<lastCell) yBuffer|=array[yCell+1]>>>(64-yOffset);
			}
		}
		lcpBits>>>=log2BitsPerInt;
		if (order&&xLexGreaterThanY) lcpBits|=Utils.MSB_LONG_ONE;
		return lcpBits;
	}


	/**
	 * Sorts the substring of $array$ that starts at $first$ and ends at $first+n-1$.
	 * This procedure is sequential and bit-parallel, and it sorts in place: no additional
	 * space is used.
	 */
	public final void heapSort(long first, long n) {
		long i;
		for (i=(n>>1)-1; i>=0; i--) heapify(first,n,i);
		for (i=n-1; i>0; i--) {
			swap(first+i,first);
			n--;
			heapify(first,n,0);
		}
	}


	/**
	 * Heapifies the relative position $position$ in the heap $array[first..first+n-1]$.
	 */
	private final void heapify(long first, long n, long position) {
		long i, firstPlusI, iValue, child, firstPlusChild, childValue, largest, firstPlusLargest, largestValue;

		i=position;
		while ((i<<1)+1<n) {
			firstPlusI=first+i;
			iValue=getElementAt(firstPlusI);
			child=(i<<1)+1;
			firstPlusChild=first+child;
			childValue=getElementAt(firstPlusChild);
			largest=childValue>iValue?child:i;
			firstPlusLargest=first+largest;
			child++;
			if (child<n) {
				largestValue=getElementAt(firstPlusLargest);
				firstPlusChild=first+child;
				childValue=getElementAt(firstPlusChild);
				if (largestValue<childValue) {
					largest=child;
					firstPlusLargest=first+largest;
				}
			}
			if (largest!=i) {
				swap(firstPlusI,firstPlusLargest);
				i=largest;
			}
			else return;
		}
	}


}




// ----------------------------------- Appendix ------------------------------------------
/**
 * Pushes $value$ at the pointer, and advances the pointer.
 * The old value is overwritten.
 */
/*public final void pushAtPointer(int value) {
	int tmp;
	setElementAt(pointerCell,pointerOffset,value);
	if (pointerOffset==sixtyFourMinusBitsPerInt) {
		pointerCell++;
		pointerOffset=0;
	}
	else pointerOffset+=bitsPerInt;
}*/