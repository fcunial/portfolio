import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;


public class Suffixes {

	/**
	 * Basic bit-parallel introsort with a cache on $string$. Assumes that the sum of
	 * the lengths of the distinguishing prefixes of the suffixes in $array$ is small,
	 * i.e. that $string$ is not highly repetitive.
	 * Bits used in addition to the input: $64*array.length()$.
	 *
	 * Remark: this procedure builds a cache of 64-bit words per element in $array$, and
	 * this cache is implemented with the current version of $IntArray$ which uses a
	 * single array of $Long$s. Thus, this procedure is in practice limited to $array$
	 * objects that contain at most $Integer.MAX_VALUE$ elements. To make it scale to
	 * longer $array$s, it would be enough to replace $IntArray$ with a more general
	 * version based on a \emph{matrix} of $Long$s, like $Stream$.
	 */
	public static final void sort(IntArray array, IntArray string, XorShiftStarRandom random) {
		final long nElements = array.length();
		long i, j;
		IntArray cache;

		cache = new IntArray(nElements,64,true);
		for (i=0; i<nElements; i++) cache.setElementAt(i,string.load63(array.getElementAt(i)<<string.log2BitsPerInt));
		quicksort(array,0,nElements,0,0,string,cache,Constants.QUICKSORT_HEAPSORT_SCALE*Utils.log2(nElements),Constants.STOP_QUICKSORT_AT_SIZE,random);
		cache=null;
	}


	/**
	 * Sorts the interval of $array$ that starts at $firstSuffix$ and ends at
	 * $firstSuffix+nSuffixes-1$ in lexicographic order, assuming that its elements are
	 * suffixes of $string \cdot #$ smaller than $string.length()$. This procedure is
	 * sequential and recursive, and it sorts in place: no additional space is used except
	 * for the recursion stack. Thanks to the bit-parallel implementation, recursion on
	 * elements equal to the pivot is expected to have small depth in practice.
	 *
	 * @param bitDepth all suffixes in $array[firstSuffix..firstSuffix+nSuffixes-1]$ are
	 * assumed to start with the same sequence of $bitDepth$ bits;
	 * @param recursionDepth depth in the recursion tree; switches to $heapSort$ when
	 * this value is $quicksortHeapsortDepth$;
	 * @param cache $cache[i]$ stores the bits in
	 * $string[(i<<string.log2BitsPerInt)+bitDepth+1 .. (i<<string.log2BitsPerInt)+bitDepth+62]$
	 * for every suffix $i$ of $string$; the values of $cache$ are read, permuted and
	 * altered by this procedure;
	 * @param stopQuicksortAtSize switches to $insertionSort$ for intervals of this size
	 * or smaller. $insertionSort$ is invoked immediately on each interval (rather than
	 * in a single, final pass over the whole $array$ as in Sedgewick's delayed small
	 * sorting), because: (1) it likely reduces cache misses \cite{musser1997introspective};
	 * (2) it does not increase the number of function calls: $insertionSort$ should be
	 * called inside each interval even in the final pass, because the values in $cache$
	 * that belong to different intervals are not comparable, thus $insertionSort$ run on
	 * the whole $array$ could move entries across interval boundaries. Of course we want
	 * to avoid reloading $cache$ from scratch, both because of reload time, and because
	 * it's beneficial for $insertionSort$ to start from the cache values left by
	 * $quickSort$.
	 * @param random random number generator.
	 */
	public static final void quicksort(IntArray array, long firstSuffix, long nSuffixes, long bitDepth, int recursionDepth, IntArray string, IntArray cache, int quicksortHeapsortDepth, int stopQuicksortAtSize, XorShiftStarRandom random) {
		boolean pivotSuffixOut;
		int r, rankSmaller, rankLarger;
		long a, b, c, d, i, diff, pivotIndex, pivotSuffix, otherSuffix, size;
		long tmp, pivot, delta, stringLengthInBits;
		long stringLength = string.length();

		while (nSuffixes>stopQuicksortAtSize) {
			if (recursionDepth==quicksortHeapsortDepth) {
				heapSort(array,firstSuffix,nSuffixes,string,cache);
				return;
			}
			pivotIndex=choosePivot(firstSuffix,nSuffixes,cache,random);
			array.swap(firstSuffix,firstSuffix+pivotIndex);
			tmp=cache.getElementAt(firstSuffix);
			cache.setElementAt(firstSuffix,cache.getElementAt(firstSuffix+pivotIndex));
			cache.setElementAt(firstSuffix+pivotIndex,tmp);
			pivot=cache.getElementAt(firstSuffix);
			pivotSuffix=array.getElementAt(firstSuffix);
			stringLengthInBits=string.totalBits;
			pivotSuffixOut=(pivotSuffix<<string.log2BitsPerInt)+bitDepth+63>=stringLengthInBits;

			// Moving pointers
			a=b=1;
			c=d=nSuffixes-1;
			while (true) {
				// Moving $b$
				while (true) {
					if (b>c) break;
					delta=cache.getElementAt(firstSuffix+b)-pivot;
					if (delta>0) break;
					else if (delta==0) {
						otherSuffix=array.getElementAt(firstSuffix+b);
						if (pivotSuffixOut || (otherSuffix<<string.log2BitsPerInt)+bitDepth+63>=stringLengthInBits) {
							if (otherSuffix<pivotSuffix) break;
						}
						else {
							array.swap(firstSuffix+a,firstSuffix+b);
							cache.setElementAt(firstSuffix+b,cache.getElementAt(firstSuffix+a));
							a++;
						}
					}
					b++;
				}
				// Moving $c$
				while (true) {
					if (b>c) break;
					delta=cache.getElementAt(firstSuffix+c)-pivot;
					if (delta<0) break;
					else if (delta==0) {
						otherSuffix=array.getElementAt(firstSuffix+c);
						if (pivotSuffixOut || (otherSuffix<<string.log2BitsPerInt)+bitDepth+63>=stringLengthInBits) {
							if (otherSuffix>pivotSuffix) break;
						}
						else {
							array.swap(firstSuffix+c,firstSuffix+d);
							cache.setElementAt(firstSuffix+c,cache.getElementAt(firstSuffix+d));
							d--;
						}
					}
					c--;
				}
				if (b>c) break;
				array.swap(firstSuffix+b,firstSuffix+c);
				tmp=cache.getElementAt(firstSuffix+b);
				cache.setElementAt(firstSuffix+b,cache.getElementAt(firstSuffix+c));
				cache.setElementAt(firstSuffix+c,tmp);
				b++; c--;
			}

			// Smaller-half trick: recurring on the smaller subtree first to limit the
			// size of the stack.
			if (b-a<d-c) { rankSmaller=0; rankLarger=1; }
			else { rankSmaller=1; rankLarger=0; }
			for (r=0; r!=2; r++) {
				if (r==rankSmaller) {  // Smaller elements
					size=b-a;
					if (size==1) {
						array.swap(firstSuffix,firstSuffix+b-1);
						cache.setElementAt(firstSuffix,cache.getElementAt(firstSuffix+b-1));
					}
					else if (size>1) {
						diff=a<size?a:size;
						array.vecswap(firstSuffix,firstSuffix+b-diff,diff);
						for (i=0; i<diff; i++) cache.setElementAt(firstSuffix+i,cache.getElementAt(firstSuffix+b-diff+i));
						if (size>stopQuicksortAtSize) {
							if (recursionDepth+1==quicksortHeapsortDepth) heapSort(array,firstSuffix,size,string,cache);
							else {
								quicksort(array,firstSuffix,size,bitDepth,recursionDepth+1,string,cache,quicksortHeapsortDepth,stopQuicksortAtSize,random);
							}
						}
						else insertionSort(array,firstSuffix,size,string,cache);
					}
				}
				else if (r==rankLarger) {  // Larger elements
					size=d-c;
					if (size==1) {
						array.swap(firstSuffix+c+1,firstSuffix+nSuffixes-1);
						cache.setElementAt(firstSuffix+nSuffixes-1,cache.getElementAt(firstSuffix+c+1));
					}
					else if (size>1) {
						diff=size<nSuffixes-d-1?size:nSuffixes-d-1;
						array.vecswap(firstSuffix+b,firstSuffix+nSuffixes-diff,diff);
						for (i=0; i<diff; i++) cache.setElementAt(firstSuffix+nSuffixes-diff+i,cache.getElementAt(firstSuffix+b+i));
						if (size>stopQuicksortAtSize) {
							if (recursionDepth+1==quicksortHeapsortDepth) heapSort(array,firstSuffix+nSuffixes-size,size,string,cache);
							else {
								quicksort(array,firstSuffix+nSuffixes-size,size,bitDepth,recursionDepth+1,string,cache,quicksortHeapsortDepth,stopQuicksortAtSize,random);
							}
						}
						else insertionSort(array,firstSuffix+nSuffixes-size,size,string,cache);
					}
				}
			}
			// Equal elements: looping, to avoid one procedure call.
			size=a+nSuffixes-d-1;
			if (size>1) {
				bitDepth+=63;
				for (i=0; i<size; i++) cache.setElementAt(firstSuffix+b-a+i,string.load63((array.getElementAt(firstSuffix+b-a+i)<<string.log2BitsPerInt)+bitDepth));
			}
			firstSuffix+=b-a;
			nSuffixes=size;
			recursionDepth++;
		}
		if (nSuffixes>1) insertionSort(array,firstSuffix,nSuffixes,string,cache);
	}


	/**
	 * Let $array$ be an array of suffixes in $string$, and let $cache$ collect the first
	 * 63 bits that start at each such suffix in $string$. The procedure computes a number
	 * in $[0..nSuffixes)$ for splitting $array[firstSuffix..firstSuffix+nSuffixes-1]$ in
	 * two equal parts according to the lexicographic order of the suffixes in $string$.
	 * Borrowed from Juha K\"{a}rkk\"{a}inen's $dcs-bwt-compressor$.
	 */
	private static final long choosePivot(long firstSuffix, long nSuffixes, IntArray cache, XorShiftStarRandom random) {
		if (nSuffixes<100) return random.nextLong(nSuffixes);
		else if (nSuffixes<1000) {
			// Median of 3 samples
			return median(random.nextLong(nSuffixes),random.nextLong(nSuffixes),random.nextLong(nSuffixes),cache,firstSuffix);
		}
 		else {
 			// Pseudo-median of 9 samples
 			return median(median(random.nextLong(nSuffixes),random.nextLong(nSuffixes),random.nextLong(nSuffixes),cache,firstSuffix),
 						  median(random.nextLong(nSuffixes),random.nextLong(nSuffixes),random.nextLong(nSuffixes),cache,firstSuffix),
 						  median(random.nextLong(nSuffixes),random.nextLong(nSuffixes),random.nextLong(nSuffixes),cache,firstSuffix),
 						  cache,firstSuffix);
 		}
	}


	/**
	 * @param a,b,c positions to the right of $start$ in $array$;
	 * @return the median of $a,b,c$ based on $cache[start+a]$, $cache[start+b]$,
	 * $cache[start+c]$.
	 */
	private static final long median(long a, long b, long c, IntArray cache, long start) {
		final long aKey=cache.getElementAt(start+a);
		final long bKey=cache.getElementAt(start+b);
		final long cKey=cache.getElementAt(start+c);
		if (aKey<bKey) {
			if (bKey<cKey) return b;
			else if (aKey<cKey) return c;
			else return a;
		}
		else {
			if (aKey<cKey) return a;
			else if (bKey<cKey) return c;
			else return b;
	  	}
	}


	/**
	 * Sorts the substring of $array$ that starts at $firstSuffix$ and ends at
	 * $firstSuffix+nSuffixes-1$. The order is lexicographic: the elements of the array
	 * are considered as suffixes of $string \cdot #$. This procedure is sequential and
	 * it sorts in place: no additional space is used.
	 *
	 * @param cache for each suffix in the array, string $0 \cdot w$, where $w$ is the
	 * string containing the first 63 bits that start at that suffix in $string$.
	 * The values in $cache$ are just read and permuted, but not altered.
	 */
	public static final void insertionSort(IntArray array, long firstSuffix, long nSuffixes, IntArray string, IntArray cache) {
		long i, j=0, hole, toInsertSuffix, tmpSuffix, lcp, toInsertCache;

		for (i=1; i<nSuffixes; i++) {
			toInsertSuffix=array.getElementAt(firstSuffix+i);
			toInsertCache=cache.getElementAt(firstSuffix+i);
			hole=i;
     		while (true) {
     			if (hole==0) break;
     			j=firstSuffix+hole-1;
     			tmpSuffix=array.getElementAt(j);
     			lcp=string.lcp63(toInsertSuffix,tmpSuffix,true,toInsertCache,cache.getElementAt(j));
     			if ((lcp&Utils.MSB_LONG_ONE)==0L) {
					array.copyToRight(j);
					cache.setElementAt(j+1,cache.getElementAt(j));
					hole--;
				}
				else break;
       		}
       		if (hole!=i) {
				array.setElementAt(firstSuffix+hole,toInsertSuffix);
				cache.setElementAt(firstSuffix+hole,toInsertCache);
			}
		}
	}


	/**
	 * Sorts the substring of $array$ that starts at $firstSuffix$ and ends at
	 * $firstSuffix+nSuffixes-1$. The order is lexicographic: the elements of the array
	 * are considered as suffixes of $string \cdot #$. This procedure is sequential and
	 * bit-parallel, and it sorts in place: no additional space is used.
	 *
	 * @param cache for each suffix in the array, string $0 \cdot w$, where $w$ is the
	 * string containing the first 63 bits that start at that suffix in $string$.
	 * The values in $cache$ are just read and permuted, but not altered.
	 */
	public static final void heapSort(IntArray array, long firstSuffix, long nSuffixes, IntArray string, IntArray cache) {
		long i, tmp;
		for (i=firstSuffix+(nSuffixes>>1)-1; i>=firstSuffix; i--) heapify(array,firstSuffix,nSuffixes,i,string,cache);
		for (i=nSuffixes-1; i>0; i--) {
			array.swap(firstSuffix+i,firstSuffix);
			tmp=cache.getElementAt(firstSuffix+i);
			cache.setElementAt(firstSuffix+i,cache.getElementAt(firstSuffix));
			cache.setElementAt(firstSuffix,tmp);
			nSuffixes--;
			heapify(array,firstSuffix,nSuffixes,0,string,cache);
		}
	}


	/**
	 * Heapifies the relative position $position$ in the heap $array[firstSuffix..
	 * firstSuffix+nSuffixes-1]$. Comparisons are bit-parallel thanks to $cache$.
	 * The values in $cache$ are just read and permuted, but not altered.
	 */
	private static final void heapify(IntArray array, long firstSuffix, long nSuffixes, long position, IntArray string, IntArray cache) {
		long i, child, largest, firstSuffixPlusI, firstSuffixPlusChild, firstSuffixPlusLargest;
		long parentSuffix, childSuffix, largestSuffix;
		long parentCache, childCache, largestCache, tmp, lcp;

		i=position;
		while ((i<<1)+1<nSuffixes) {
			firstSuffixPlusI=firstSuffix+i;
			parentSuffix=array.getElementAt(firstSuffixPlusI);
			parentCache=cache.getElementAt(firstSuffixPlusI);
			child=(i<<1)+1;
			firstSuffixPlusChild=firstSuffix+child;
			childSuffix=array.getElementAt(firstSuffixPlusChild);
			childCache=cache.getElementAt(firstSuffixPlusChild);
			lcp=string.lcp63(parentSuffix,childSuffix,true,parentCache,childCache);
			largest=(lcp&Utils.MSB_INT_ONE)==0L?child:i;
			firstSuffixPlusLargest=firstSuffix+largest;
			child++;
			if (child<nSuffixes) {
				largestSuffix=array.getElementAt(firstSuffixPlusLargest);
				largestCache=cache.getElementAt(firstSuffixPlusLargest);
				firstSuffixPlusChild=firstSuffix+child;
				childSuffix=array.getElementAt(firstSuffixPlusChild);
				childCache=cache.getElementAt(firstSuffixPlusChild);
				lcp=string.lcp63(largestSuffix,childSuffix,true,largestCache,childCache);
				if ((lcp&Utils.MSB_LONG_ONE)==0L) {
					largest=child;
					firstSuffixPlusLargest=firstSuffix+largest;
				}
			}
			if (largest!=i) {
				array.swap(firstSuffixPlusI,firstSuffixPlusLargest);
				tmp=cache.getElementAt(firstSuffixPlusI);
				cache.setElementAt(firstSuffixPlusI,cache.getElementAt(firstSuffixPlusLargest));
				cache.setElementAt(firstSuffixPlusLargest,tmp);
				i=largest;
			}
			else return;
		}
	}


	/**
	 * Stores in $out$ the suffixes of $string$ that are lexicographically larger
	 * than $string[low..]$ and smaller than $string[high..]$. The setting $low<0$ is
	 * interpreted as $string[low..]=\epsilon$, and the setting $high<0$ is interpreted as
	 * $string[high..]=x^\infty$, where $x>a \forall a \in \Sigma$. $out$ is not sorted
	 * lexicographically, and it does not contain $#$.
	 *
	 * The procedure is sequential: the comparisons of each suffix with
	 * $string[low..]$ and with $string[high..]$ are not parallelized, since each such
	 * comparison amounts to just one or two LCP computations: parallelization should
	 * instead be performed ad the interval level, i.e. for distinct $(low,high)$ pairs.
	 * The code for $low$ and $high$ is identical: it is replicated inside a single
	 * procedure to avoid two function calls per suffix of $string$, and to possibly
	 * reuse cached portions of $string$.
	 *
	 * @param lcpLow LCP array of $string[low..]$, computed by $buildLCPArray$. We assume
	 * that $lcpLow.bitsPerInt=lcpHigh.bitsPerInt$.
	 * @param lcpHigh LCP array of $string[high..]$, computed by $buildLCPArray$. We
	 * assume that $lcpLow.bitsPerInt=lcpHigh.bitsPerInt$.
	 */
	public static final void intervalOfSuffixes(long low, long high, IntArray lcpLow, IntArray lcpHigh, IntArray string, IntArray out) {
		final int BITS_PER_INT = lcpLow==null?lcpHigh.bitsPerInt:lcpLow.bitsPerInt;
		final long SELECT_SIGN = Utils.MSB_LONG_ONE>>>(64-BITS_PER_INT);
		final long SELECT_LENGTH = 0xFFFFFFFFFFFFFFFFL>>>(64-BITS_PER_INT+1);
		final long stringLength, lcpLowLength, lcpHighLength;
		boolean smallerThanLow, smallerThanHigh;
		long i, lcp, intervalFirst_low, intervalFirst_high, intervalLast_low, intervalLast_high, l_low, l_high;
		long iPlusLLow, lowPlusLLow, iPlusLHigh, highPlusLHigh, iMinusIntervalFirstLow, iMinusIntervalFirstHigh;
		long intervalSign_low, intervalSign_high, sign, signPrime;
		stringLength=string.length();
		lcpLowLength=lcpLow==null?-1:lcpLow.length();
		lcpHighLength=lcpHigh==null?-1:lcpHigh.length();
		out.clear();

		intervalFirst_low=-1; intervalLast_low=-1; intervalSign_low=0x0000000000000000L;
		intervalFirst_high=-1; intervalLast_high=-1; intervalSign_high=0x0000000000000000L;
		l_low=0; l_high=0;
		sign=0x0000000000000000L; signPrime=0x0000000000000000L;
		for (i=0; i<stringLength; i++) {
			if (i==low||i==high) continue;
			if (low>=0) {
				if (i>=intervalLast_low) { intervalFirst_low=i; intervalLast_low=i; l_low=0; }
				else {
					iMinusIntervalFirstLow=i-intervalFirst_low;
					if (iMinusIntervalFirstLow<lcpLowLength) {
						l_low=lcpLow.getElementAt(iMinusIntervalFirstLow);
						signPrime=l_low&SELECT_SIGN;
						l_low&=SELECT_LENGTH;
					}
					else {
						l_low=string.lcp(low,low+iMinusIntervalFirstLow,true);
						signPrime=l_low&Utils.MSB_LONG_ONE;
						l_low&=Utils.MSB_LONG_ZERO;
					}
				}
				iPlusLLow=i+l_low;
				lowPlusLLow=low+l_low;
				if (iPlusLLow==intervalLast_low) {
					lcp=string.lcp(lowPlusLLow,intervalLast_low,true);
					sign=lcp&Utils.MSB_LONG_ONE;
					lcp&=Utils.MSB_LONG_ZERO;
					l_low+=lcp;
					intervalFirst_low=i; intervalLast_low+=lcp; intervalSign_low=sign;
				}
				else if (iPlusLLow>intervalLast_low) {
					l_low=intervalLast_low-i; sign=intervalSign_low;
					intervalFirst_low=i;  // $intervalLast_low$ and $intervalLast_sign$ not altered
				}
				else sign=signPrime;
				smallerThanLow=sign!=0x0000000000000000L;
				// If $string[i..]$ is lexicographically smaller than $string[low..]$ we
				// can't quit the iteration here, because we need to update the temporary
				// variables related to $high$.
			}
			else smallerThanLow=false;
			if (high>=0) {
				if (i>=intervalLast_high) { intervalFirst_high=i; intervalLast_high=i; l_high=0; }
				else {
					iMinusIntervalFirstHigh=i-intervalFirst_high;
					if (iMinusIntervalFirstHigh<lcpHighLength) {
						l_high=lcpHigh.getElementAt(iMinusIntervalFirstHigh);
						signPrime=l_high&SELECT_SIGN;
						l_high&=SELECT_LENGTH;
					}
					else {
						l_high=string.lcp(high,high+iMinusIntervalFirstHigh,true);
						signPrime=l_high&Utils.MSB_LONG_ONE;
						l_high&=Utils.MSB_LONG_ZERO;
					}
				}
				iPlusLHigh=i+l_high;
				highPlusLHigh=high+l_high;
				if (iPlusLHigh==intervalLast_high) {
					lcp=string.lcp(highPlusLHigh,intervalLast_high,true);
					sign=lcp&Utils.MSB_LONG_ONE;
					lcp&=Utils.MSB_LONG_ZERO;
					l_high+=lcp;
					intervalFirst_high=i; intervalLast_high+=lcp; intervalSign_high=sign;
				}
				else if (iPlusLHigh>intervalLast_high) {
					l_high=intervalLast_high-i; sign=intervalSign_high;
					intervalFirst_high=i;  // $intervalLast_high$ and $intervalSign_high$ not altered
				}
				else sign=signPrime;
				smallerThanHigh=sign!=0x0000000000000000L;
			}
			else smallerThanHigh=true;
			if (!smallerThanLow&&smallerThanHigh) out.push(i);
		}
	}


	/**
	 * Stores in $out[i]$ the length of the longest common prefix between
	 * $string[suffix..]$ and $string[i..]$, for every
	 * $suffix \leq i \leq suffix+distinguishingPrefix$, where $distinguishingPrefix=\min
	 * \{DISTINGUISHING_PREFIX,string.length()-suffix\}$. The first bit of $out[i]$ is one
	 * iff $string[suffix..]$ is lexicographically larger than $string[i..]$.
	 *
	 * @param out we assume that any LCP length can be encoded in $out.bitsPerInt-1$ bits,
	 * and that $out.bitsPerInt \leq 64$.
	 */
	public static final void buildLCPArray(long suffix, IntArray string, IntArray out) {
		final long SELECT_SIGN = Utils.MSB_LONG_ONE>>>(64-out.bitsPerInt);
		final long SELECT_LENGTH = 0xFFFFFFFFFFFFFFFFL>>>(64-out.bitsPerInt+1);
		long i, l, lcp, sign, signPrime, selectSign, selectLength, intervalFirst, intervalLast, intervalSign, stringLengthMinusSuffix, distinguishingPrefix;
		stringLengthMinusSuffix=string.length()-suffix;
		if (Constants.DISTINGUISHING_PREFIX>stringLengthMinusSuffix) distinguishingPrefix=stringLengthMinusSuffix;
		else distinguishingPrefix=Constants.DISTINGUISHING_PREFIX;
		out.clear();

		out.push(stringLengthMinusSuffix);  // The first entry is never used
		intervalFirst=-1; intervalLast=-1; intervalSign=0x0000000000000000L;
		l=0;  sign=0x0000000000000000L; signPrime=0x0000000000000000L;  // Related to the current $i$
		for (i=1; i<=distinguishingPrefix; i++) {
			if (i>=intervalLast) { intervalFirst=i; intervalLast=i; l=0; }
			else {
				l=out.getElementAt(i-intervalFirst);
				signPrime=l&SELECT_SIGN;
				l&=SELECT_LENGTH;
			}
			if (i+l==intervalLast) {
				lcp=string.lcp(suffix+l,suffix+intervalLast,true);
				sign=lcp&Utils.MSB_LONG_ONE;
				lcp&=Utils.MSB_LONG_ZERO;
				l+=lcp;
				intervalFirst=i; intervalLast+=lcp; intervalSign=sign;
			}
			else if (i+l>intervalLast) {
				l=intervalLast-i; sign=intervalSign;
				intervalFirst=i;  // $intervalLast$ and $intervalSign$ not altered
			}
			else sign=signPrime;
			out.push(sign==0x0000000000000000L?l:l|SELECT_SIGN);
		}
	}


	/**
	 * Decides which of the blocks of suffixes defined by $splitters$ contains $suffix$.
	 * Block 0 contains suffixes that are less than $splitters[0]$; block
	 * $splitters.length()$ contains suffixes that are larger than
	 * $splitters[splitters.length()-1]$. $suffix$ is assumed not to be in $splitters$.
	 * $splitters$ is assumed to contain a number of elements that can be addressed with
	 * an $int$.
	 *
	 * @param splitters suffixes of $string$, sorted lexicographically;
	 * @param binarySearchCache the precomputed cache built by $buildBinarySearchCache$ on
	 * $splitters$.
	 */
	public static final int assignSuffixToBlock(long suffix, IntArray splitters, IntArray binarySearchCache, IntArray string) {
		final int nSplitters;
		int mid, left, right;
		long lcp, lcpPrime, midLeftLCP, midRightLCP, suffixLeftLCP, suffixRightLCP, splitter, sign, suffixLeftSign, suffixRightSign;
		nSplitters=(int)( splitters.length() );

		// First and last blocks
		suffixLeftLCP=string.lcp(suffix,splitters.getElementAt(0),true);
		suffixLeftSign=suffixLeftLCP&Utils.MSB_LONG_ONE;
		if (suffixLeftSign==0x0000000000000000L) return 0;
		suffixLeftLCP&=Utils.MSB_LONG_ZERO;
		suffixRightLCP=string.lcp(suffix,splitters.getElementAt(nSplitters-1),true);
		suffixRightSign=suffixRightLCP&Utils.MSB_LONG_ONE;
		if (suffixRightSign!=0x0000000000000000L) return nSplitters;
		suffixRightLCP&=Utils.MSB_LONG_ZERO;

		// Binary search on the other blocks
		left=0;
		right=nSplitters-1;
		while (right>left+1) {
			mid=(left+right)>>>1;
			midLeftLCP=binarySearchCache.getElementAt((mid-1)<<1);
			midRightLCP=binarySearchCache.getElementAt(((mid-1)<<1)+1);
			if (midLeftLCP>midRightLCP) {
				if (suffixLeftLCP>midLeftLCP) {
					right=mid;
					suffixRightLCP=midLeftLCP;
					continue;
				}
				else if (suffixLeftLCP<midLeftLCP) {
					left=mid;
					continue;
				}
				else lcp=midLeftLCP;
			}
			else {
				if (suffixRightLCP>midRightLCP) {
					left=mid;
					suffixLeftLCP=midRightLCP;
					continue;
				}
				else if (suffixRightLCP<midRightLCP) {
					right=mid;
					continue;
				}
				else lcp=midRightLCP;
			}
			splitter=splitters.getElementAt(mid);
			lcpPrime=string.lcp(suffix+lcp,splitter+lcp,true);
			sign=lcpPrime&Utils.MSB_LONG_ONE;
			lcp=lcp+lcpPrime&Utils.MSB_LONG_ZERO;
			if (sign!=0x0000000000000000L) {
				left=mid;
				suffixLeftLCP=lcp;
			}
			else {
				right=mid;
				suffixRightLCP=lcp;
			}
		}
		return right;
	}


	/**
	 * Builds the cache for binary searches over $splitters$ used by $assignSuffixToBlock$
	 * as described in \cite{manber1993suffix}. The procedure is currently sequential, but
	 * it could be easily parallelized if $splitters$ is large. $splitters$ is assumed to
	 * contain a number of elements that can be represented with an $int$.
	 *
	 * @param splitters three or more suffixes of $string$, sorted lexicographically;
	 * @return an array of size $2*(splitters.length()-2)$ that stores at position
	 * $2*(mid-1)$ the LCP between suffix $mid$ and suffix $left$, and at position
	 * $2*(mid-1)+1$ the LCP between suffix $mid$ and suffix $right$, for every $mid$,
	 * $left$, $right$ in a binary search over $splitters$. The most significant bit of
	 * LCPs does not encode order information.
	 */
	public static final IntArray buildBinarySearchCache(IntArray splitters, IntArray string) {
		int log2stringLength, nSplitters;
		int left, right, mid;
		final long stringLength;
		long midSuffix, lcp, leftRightLCP;
		IntArray out, stack;
		stringLength=string.length();
		log2stringLength=Utils.log2(stringLength);
		nSplitters=(int)( splitters.length() );
		out = new IntArray((nSplitters-2)<<1,log2stringLength,true);
		stack = new IntArray((nSplitters-2)*3,log2stringLength);
		left=0;
		right=nSplitters-1;
		stack.push(left);
		stack.push(right);
		stack.push(string.lcp(splitters.getElementAt(left),splitters.getElementAt(right),false));

		while (stack.length()!=0) {
			leftRightLCP=stack.pop();
			right=(int)( stack.pop() );
			left=(int)( stack.pop() );
			mid=(left+right)>>1;
			midSuffix=splitters.getElementAt(mid);
			lcp=leftRightLCP+string.lcp(splitters.getElementAt(left)+leftRightLCP,midSuffix+leftRightLCP,false);
			out.setElementAt((mid-1)<<1,lcp);
			if (mid-left>1) { stack.push(left); stack.push(mid); stack.push(lcp); }
			lcp=leftRightLCP+string.lcp(splitters.getElementAt(right)+leftRightLCP,midSuffix+leftRightLCP,false);
			out.setElementAt(((mid-1)<<1)+1,lcp);
			if (right-mid>1) { stack.push(mid);	stack.push(right); stack.push(lcp); }
		}
		return out;
	}


	/**
	 * Builds a representation of the BWT of $string$ using the blockwise, multithreaded
	 * strategy described in \cite{karkkainen2007fast}.
	 *
	 * If $bwt$ if not null, then the BWT of $string$ is stored in $bwt$ as a sequence of
	 * $string.length()+1$ integers, each represented in $log2alphabetLength$ bits. $bwt$
	 * must have been already filled with $string.length()+1$ zeros, since this procedure
	 * sets its values, rather than pushing them.
	 * Otherwise, the full BWT of $string$ is not kept in memory as an array of integers,
	 * and instead:
	 * 1. the output is stored in array $waveletTrees$ as a sequence of approximately
	 * $string.length()/blockSize$ Huffman-shaped wavelet trees, built on the
	 * corresponding blocks of the BWT; character $#$ is not used to build such wavelet
	 * trees;
	 * 2. $blockStarts$ is filled with the starting position of each block in sorted
	 * order in the BWT;
	 * 3. $blockBoundaries$ is set to a vector of $string.length()+1$ bits which flags
	 * with a one each value $blockStarts[i]$ with $i>0$;
	 * 4. $localBlockCounts$ stores the number of characters in each block, excluding $#$.
	 *
	 * Remark: for a detailed description of the space requirements of this procedure,
	 * see $blockwiseBWT_getBlockSize$.
	 *
	 * Remark: we assume that the number of blocks is at most $Integer.MAX_VALUE$, and
	 * that all input containers related to blocks have space for at least four blocks.
	 *
	 * @param blockSize maximum number of suffixes in a block;
	 * @param sharp used iff $bwt==null$; $sharp[0]$: position of the sharp sign in
	 * the BWT of $string$; $sharp[1]$: block containing the sharp sign;
	 * $sharp[2]$: distance of the sharp sign from the beginning of block $sharp[1]$;
	 * @return the position of the sharp sign in the BWT of $string$.
	 */
	public static final long blockwiseBWT(IntArray string, int[] alphabet, int alphabetLength, int log2alphabetLength, long blockSize, IntArray bwt, HuffmanWaveletTree[] waveletTrees, IntArray blockStarts, IntArray blockBoundaries, IntArray[] localBlockCounts, long[] sharp) {
		final int log2stringLength, log2stringLengthPlusOne;
		int i, j, nSplitters, nBits;
		long splitter, currentBlock, cumulativeSize, blockStart, maxBlockSize;
		final long stringLength;
		IntArray binarySearchCache;
		IntArray splitters_byPosition;  // Initial set of splitters, sorted by position in the string.
		IntArray splitters_bySuffix;  // Initial set of splitters, sorted lexicographically.
		IntArray splitters;  // Final set of splitters, sorted lexicographically.
		IntArray[] lcpArrays;
		AtomicInteger intGenerator;  // Atomic generator of integers
		AtomicLong longGenerator;  // Atomic generator of longs
		AtomicInteger sharpBlock;
		AtomicLong sharpPosition, sharpOffset;
		CountDownLatch latch;  // Barrier
		AtomicLong[] blockSizes;  // Atomic counters used for measuring the size of blocks in parallel
		stringLength=string.length();
		log2stringLength=Utils.log2(stringLength);
		log2stringLengthPlusOne=Utils.log2(stringLength+1);
		XorShiftStarRandom random = new XorShiftStarRandom();

		splitters_byPosition=buildSplitters(stringLength,log2stringLength,blockSize,random);
		nSplitters=(int)( splitters_byPosition.length() );
		splitters_bySuffix=splitters_byPosition.clone();
		sort(splitters_bySuffix,string,random);

		// Measuring the size of the blocks induced by splitters
		binarySearchCache=buildBinarySearchCache(splitters_bySuffix,string);
		blockSizes = new AtomicLong[nSplitters+1];
		for (i=0; i<=nSplitters; i++) blockSizes[i] = new AtomicLong();
		longGenerator = new AtomicLong();
		latch = new CountDownLatch(Constants.N_THREADS);
		for (i=0; i<Constants.N_THREADS; i++) new MeasureBWTBlockThread(blockSizes,longGenerator,latch,splitters_bySuffix,splitters_byPosition,binarySearchCache,string).start();
		try { latch.await(); }
		catch(InterruptedException e) {
			e.printStackTrace();
			System.exit(1);
		}
		splitters_byPosition.deallocate(); splitters_byPosition=null;
		binarySearchCache.deallocate(); binarySearchCache=null; longGenerator=null;
		blockSizes[0].incrementAndGet();  // Adding suffix $#$, which is not counted by $MeasureBWTBlockThread$

		// Merging adjacent blocks greedily and sequentially. Existing blocks larger than
		// $blockSize$ are not refined. At the end of this process, the first block
		// has the form $[..y]$, the last block has the form $(x..$, and all
		// the other blocks have the form $(x..y]$, where $x$ and $y$ are splitters.
		splitters = new IntArray(nSplitters,log2stringLength);
		if (blockStarts==null) blockStarts = new IntArray(nSplitters+1,log2stringLengthPlusOne,false);
		else blockStarts.clear();
		i=1; cumulativeSize=blockSizes[0].get()+1; blockStart=0; maxBlockSize=0;
		while (i<=nSplitters) {
			currentBlock=blockSizes[i].get();
			if (cumulativeSize+currentBlock+(i<nSplitters?1:0)>blockSize) {
				splitters.push(splitters_bySuffix.getElementAt(i-1));
				blockStarts.push(blockStart);
				blockStart+=cumulativeSize;
				if (cumulativeSize>maxBlockSize) maxBlockSize=cumulativeSize;
				cumulativeSize=currentBlock+(i<nSplitters?1:0);
			}
			else cumulativeSize+=currentBlock+(i<nSplitters?1:0);
			i++;
		}
		if (blockStart<=stringLength) {  // Closing last block
			blockStarts.push(blockStart);
			if (cumulativeSize>maxBlockSize) maxBlockSize=cumulativeSize;
		}
		nSplitters=(int)( splitters.length() );
		blockSizes=null;
		splitters_bySuffix.deallocate(); splitters_bySuffix=null;
		if (bwt==null) {
			for (i=0; i<=nSplitters; i++) {
				blockBoundaries.setElementFromRightAt(blockStarts.getElementAt(i),1);  // $Rank9$, used on $blockBoundaries$, stores bits from right to left.
			}
		}

		// Building the BWT block by block
		lcpArrays = new IntArray[nSplitters];
		nBits=log2stringLength<<1;  // The left-shift is necessary to guarantee that there is at least one bit for the sign in each LCP array
		for (i=0; i<nSplitters; i++) {
			splitter=splitters.getElementAt(i);
			lcpArrays[i] = new IntArray(Constants.DISTINGUISHING_PREFIX+1,nBits);
			buildLCPArray(splitter,string,lcpArrays[i]);
		}
		intGenerator = new AtomicInteger();
		latch = new CountDownLatch(nSplitters+1);
		sharpPosition = new AtomicLong();
		sharpBlock=null;
		sharpOffset=null;
		if (bwt!=null) {
			for (i=0; i<Constants.N_THREADS; i++) new SortBWTBlockThread(intGenerator,sharpPosition,splitters,lcpArrays,string,bwt,blockStarts,latch,maxBlockSize,log2alphabetLength).start();
		}
		else {
			sharpBlock = new AtomicInteger();
			sharpOffset = new AtomicLong();
			for (i=0; i<Constants.N_THREADS; i++) new WaveletBWTBlockThread(intGenerator,sharpPosition,sharpBlock,sharpOffset,splitters,lcpArrays,string,blockStarts,waveletTrees,localBlockCounts,latch,maxBlockSize,alphabet,alphabetLength,log2alphabetLength).start();
		}
		try { latch.await(); }
		catch(InterruptedException e) {
			e.printStackTrace();
			System.exit(1);
		}
		for (i=0; i<nSplitters; i++) {
			lcpArrays[i].deallocate();
			lcpArrays[i]=null;
		}
		lcpArrays=null;
		splitters.deallocate(); splitters=null;
		intGenerator=null; latch=null;
		if (bwt==null) {
			sharp[0]=sharpPosition.get();
			sharp[1]=sharpBlock.get();
			sharp[2]=sharpOffset.get();
		}
		return sharpPosition.get();
	}


	/**
	 * @return a set of distinct, $\approx \ceil{stringLength/blockSize}-1$, random
     * splitters of the suffixes of a string $s$ (not of $s \cdot #$). Such splitters
     * are at least 3 (a lower bound required by $buildBinarySearchCache$), at most
     * $Integer.MAX_VALUE$ (an upper bound required to address an array in Java), and they
     * are sorted by position in $s$.
	 */
	private static IntArray buildSplitters(long stringLength, int log2stringLength, long blockSize, XorShiftStarRandom random) {
		int i, nSplitters;
		long ns, splitter, firstSplitter, secondSplitter, lastSplitter;
		IntArray splitters, distinctSplitters;

		ns=Utils.divideAndRoundUp(stringLength,blockSize)-1;
		if (ns<3) nSplitters=3;
		else if (ns>Integer.MAX_VALUE) nSplitters=Integer.MAX_VALUE;
		else nSplitters=(int)ns;
		splitters = new IntArray(nSplitters,log2stringLength);
		for (i=0; i<nSplitters; i++) splitters.push(random.nextLong(stringLength));
		splitters.heapSort(0,nSplitters);

		// Removing duplicates
		distinctSplitters = new IntArray(nSplitters,log2stringLength);
		lastSplitter=splitters.getElementAt(0);
		distinctSplitters.push(lastSplitter);
		for (i=1; i<nSplitters; i++) {
			splitter=splitters.getElementAt(i);
			if (splitter!=lastSplitter) {
				distinctSplitters.push(splitter);
				lastSplitter=splitter;
			}
		}
		splitters.clear();

		// Enforcing at least 3 splitters after duplicate removal
		if (distinctSplitters.length()==1) {
			splitter=distinctSplitters.getElementAt(0);
			if (splitter>0 && splitter<stringLength-1) {
				splitters.push(random.nextLong(splitter));
				splitters.push(splitter);
				splitters.push(splitter+1+random.nextLong(stringLength-splitter-1));
			}
			else if (splitter==stringLength-1) {
				firstSplitter=random.nextLong(splitter);
				do { secondSplitter=random.nextLong(splitter); }
				while (secondSplitter==firstSplitter);
				if (firstSplitter<secondSplitter) {
					splitters.push(firstSplitter);
					splitters.push(secondSplitter);
				}
				else {
					splitters.push(secondSplitter);
					splitters.push(firstSplitter);
				}
				splitters.push(splitter);
			}
			else {
				splitters.push(splitter);
				firstSplitter=1+random.nextLong(stringLength-1);
				do { secondSplitter=1+random.nextLong(stringLength-1); }
				while (secondSplitter==firstSplitter);
				if (firstSplitter<secondSplitter) {
					splitters.push(firstSplitter);
					splitters.push(secondSplitter);
				}
				else {
					splitters.push(secondSplitter);
					splitters.push(firstSplitter);
				}
			}
			distinctSplitters=splitters;
		}
		else if (distinctSplitters.length()==2) {
			firstSplitter=distinctSplitters.getElementAt(0);
			secondSplitter=distinctSplitters.getElementAt(1);
			if (firstSplitter>0) {
				splitters.push(random.nextLong(firstSplitter));
				splitters.push(firstSplitter);
				splitters.push(secondSplitter);
			}
			else if (secondSplitter>firstSplitter+1) {
				splitters.push(firstSplitter);
				splitters.push(firstSplitter+1+random.nextLong(secondSplitter-firstSplitter-1));
				splitters.push(secondSplitter);
			}
			else {
				splitters.push(firstSplitter);
				splitters.push(secondSplitter);
				splitters.push(secondSplitter+1+random.nextLong(stringLength-secondSplitter-1));
			}
			distinctSplitters=splitters;
		}
		return distinctSplitters;
	}



	/**
	 * Thread used by procedure $blockwiseBWT$ to measure in parallel the number of
	 * suffixes between two consecutive splitters in the BWT (splitters excluded,
	 * artificial suffix $#$ excluded).
	 */
	private static class MeasureBWTBlockThread extends Thread {
		private IntArray splitters_byPosition;  // For discarding suffixes that correspond to splitters
		private IntArray splitters_bySuffix;  // For assigning suffixes to blocks
		private IntArray binarySearchCache, string;
		private CountDownLatch latch;
		private AtomicLong suffixGenerator;
		private AtomicLong[] blockSizes;
		private int nSplitters;

		public MeasureBWTBlockThread(AtomicLong[] blockSizes, AtomicLong suffixGenerator, CountDownLatch latch, IntArray splitters_bySuffix, IntArray splitters_byPosition, IntArray binarySearchCache, IntArray string) {
			this.blockSizes=blockSizes;
			this.suffixGenerator=suffixGenerator;
			this.latch=latch;
			this.splitters_bySuffix=splitters_bySuffix;
			this.splitters_byPosition=splitters_byPosition;
			this.binarySearchCache=binarySearchCache;
			this.string=string;
		}

		public void run() {
			int nSplitters;
			long suffix, stringLength;
			stringLength=string.length();
			nSplitters=(int)( splitters_bySuffix.length() );
			while (true) {
				suffix=suffixGenerator.getAndIncrement();
				if (suffix>=stringLength) {
					latch.countDown();
					return;
				}
				if (splitters_byPosition.binarySearch(suffix,0,nSplitters-1)<0) blockSizes[assignSuffixToBlock(suffix,splitters_bySuffix,binarySearchCache,string)].incrementAndGet();
			}
		}
	}


	/**
	 * Thread used by procedure $blockwiseBWT$ to collect and sort the suffixes in BWT
	 * blocks in parallel. To limit the time spent in critical regions, the thread builds
	 * its own BWT block in a local array of $maxBlockSize$ entries, and it copies it to
	 * the global BWT using the bitparallel procedure $IntArray.pasteAtPointer$.
	 */
	private static class SortBWTBlockThread extends Thread {
		private final long stringLength, maxBlockSize;
		private final int log2stringLength, log2alphabetLength;
		private AtomicInteger splitterGenerator;
		private AtomicLong sharpPosition;
		private IntArray splitters, string, bwt, blockStarts;
		private CountDownLatch latch;
		private IntArray[] lcpArrays;

		/**
		 * @param splitters contains a number of splitters that can be represented as an
		 * $int$.
		 */
		public SortBWTBlockThread(AtomicInteger splitterGenerator, AtomicLong sharpPosition, IntArray splitters, IntArray[] lcpArrays, IntArray string, IntArray bwt, IntArray blockStarts, CountDownLatch latch, long maxBlockSize, int log2alphabetLength) {
			this.splitterGenerator=splitterGenerator;
			this.sharpPosition=sharpPosition;
			this.splitters=splitters;
			this.lcpArrays=lcpArrays;
			this.string=string;
			stringLength=string.length();
			log2stringLength=Utils.log2(stringLength);
			this.bwt=bwt;
			this.blockStarts=blockStarts;
			this.latch=latch;
			this.maxBlockSize=maxBlockSize;
			this.log2alphabetLength=log2alphabetLength;
		}

		public void run() {
			int i, rightSplitter, pointerOffset;
			final int nSplitters = (int)( splitters.length() );
			long rightSplitterSuffix=-1, blockLength, blockStart, pointer, suffix;
			IntArray suffixArrayBlock = new IntArray(maxBlockSize,log2stringLength);
			IntArray bwtBlock = new IntArray(maxBlockSize+(64>>Utils.log2(log2alphabetLength)),log2alphabetLength);  // Block size is larger than $maxBlockSize$ to allow for global shifts
			XorShiftStarRandom random = new XorShiftStarRandom();
			while (true) {
				rightSplitter=splitterGenerator.getAndIncrement();
				if (rightSplitter>nSplitters) break;

				// Building suffix array block
				if (rightSplitter!=nSplitters) rightSplitterSuffix=splitters.getElementAt(rightSplitter);
				if (rightSplitter==0) intervalOfSuffixes(-1,rightSplitterSuffix,null,lcpArrays[rightSplitter],string,suffixArrayBlock);
				else if (rightSplitter==nSplitters) intervalOfSuffixes(splitters.getElementAt(rightSplitter-1),-1,lcpArrays[rightSplitter-1],null,string,suffixArrayBlock);
				else intervalOfSuffixes(splitters.getElementAt(rightSplitter-1),rightSplitterSuffix,lcpArrays[rightSplitter-1],lcpArrays[rightSplitter],string,suffixArrayBlock);
				blockLength=suffixArrayBlock.length();
				sort(suffixArrayBlock,string,random);

				// Building local BWT block
				blockStart=blockStarts.getElementAt(rightSplitter);
				if (rightSplitter==0) {  // Making room for suffix $#$
					bwtBlock.push(string.getElementAt(stringLength-1));
					pointer=1;
				}
				else {
					synchronized(bwt) {
						bwt.setPointer(blockStart);
						pointerOffset=bwt.pointerOffset;
					}
					bwtBlock.clear(pointerOffset);
					pointer=blockStart;
				}
				for (i=0; i<blockLength; i++) {
					suffix=suffixArrayBlock.getElementAt(i);
					if (suffix!=0) bwtBlock.push(string.getElementAt(suffix-1));
					else {
						bwtBlock.push(0);
						sharpPosition.set(pointer);
					}
					pointer++;
				}
				if (rightSplitter!=nSplitters) {
					if (rightSplitterSuffix!=0) bwtBlock.push(string.getElementAt(rightSplitterSuffix-1));
					else {
						bwtBlock.push(0);
						sharpPosition.set(pointer);
					}
				}

				// Fast copy of local block to global BWT
				synchronized(bwt) {
					bwt.setPointer(blockStart);
					bwt.pasteAtPointer(bwtBlock);
				}
				latch.countDown();
			}
			suffixArrayBlock.deallocate(); suffixArrayBlock=null;
			bwtBlock.deallocate(); bwtBlock=null;
		}
	}


	/**
	 * Thread used by procedure $blockwiseBWT$ to build the Huffman-shaped wavelet tree of
	 * BWT blocks in parallel. This thread is largely isomorphic to $SortBWTBlockThread$,
	 * with the following exception: character $#$ (or a substitute of it inside the
	 * alphabet) is not explicitly inserted in the block that contains it, which is thus
	 * one character shorter.
	 */
	private static class WaveletBWTBlockThread extends Thread {
		private final long maxBlockSize, stringLength;
		private final int log2stringLength, alphabetLength, log2alphabetLength;
		private int[] alphabet;
		private AtomicLong sharpPosition, sharpOffset;
		private AtomicInteger splitterGenerator, sharpBlock;
		private IntArray splitters, string, bwt, blockStarts;
		private CountDownLatch latch;
		private IntArray[] lcpArrays, localBlockCounts;
		private HuffmanWaveletTree[] waveletTrees;

		/**
		 * @param splitters contains a number of splitters that can be represented as an
		 * $int$.
		 */
		public WaveletBWTBlockThread(AtomicInteger splitterGenerator, AtomicLong sharpPosition, AtomicInteger sharpBlock, AtomicLong sharpOffset, IntArray splitters, IntArray[] lcpArrays, IntArray string, IntArray blockStarts, HuffmanWaveletTree[] waveletTrees, IntArray[] localBlockCounts, CountDownLatch latch, long maxBlockSize, int[] alphabet, int alphabetLength, int log2alphabetLength) {
			this.splitterGenerator=splitterGenerator;
			this.sharpPosition=sharpPosition;
			this.sharpBlock=sharpBlock;
			this.sharpOffset=sharpOffset;
			this.splitters=splitters;
			this.lcpArrays=lcpArrays;
			this.string=string;
			stringLength=string.length();
			log2stringLength=Utils.log2(stringLength);
			this.blockStarts=blockStarts;
			this.waveletTrees=waveletTrees;
			this.localBlockCounts=localBlockCounts;
			this.latch=latch;
			this.maxBlockSize=maxBlockSize;
			this.alphabet=alphabet;
			this.alphabetLength=alphabetLength;
			this.log2alphabetLength=log2alphabetLength;
		}

		public void run() {
			int i, j, rightSplitter, effectiveAlphabetLength;
			long il, c, rightSplitterSuffix=-1, blockLength, bwtBlockLength, pointer, suffix, count;
			final int nSplitters = (int)( splitters.length() );
			int[] effectiveAlphabet;
			long[] counts = new long[alphabetLength];
			IntArray effectiveCounts;
			IntArray suffixArrayBlock = new IntArray(maxBlockSize,log2stringLength);
			IntArray bwtBlock = new IntArray(maxBlockSize+2,log2alphabetLength);  // In the worst case, a suffix array block can be augmented with two additional characters.
			XorShiftStarRandom random = new XorShiftStarRandom();
			while (true) {
				rightSplitter=splitterGenerator.getAndIncrement();
				if (rightSplitter>nSplitters) break;

				// Building the suffix array block
				if (rightSplitter!=nSplitters) rightSplitterSuffix=splitters.getElementAt(rightSplitter);
				if (rightSplitter==0) intervalOfSuffixes(-1,rightSplitterSuffix,null,lcpArrays[rightSplitter],string,suffixArrayBlock);
				else if (rightSplitter==nSplitters) intervalOfSuffixes(splitters.getElementAt(rightSplitter-1),-1,lcpArrays[rightSplitter-1],null,string,suffixArrayBlock);
				else intervalOfSuffixes(splitters.getElementAt(rightSplitter-1),rightSplitterSuffix,lcpArrays[rightSplitter-1],lcpArrays[rightSplitter],string,suffixArrayBlock);
				blockLength=suffixArrayBlock.length();
				if (blockLength>0) sort(suffixArrayBlock,string,random);

				// Building the local BWT block. Not building the BWT
				// block explicitly would produce cache misses both while counting the
				// symbols in the block and while pushing them in the wavelet tree.
				bwtBlock.clear();
				localBlockCounts[rightSplitter] = new IntArray(alphabetLength,Utils.bitsToEncode(blockLength+2),true);  // In the worst case, a suffix array block can be augmented with two additional characters.
				if (rightSplitter==0) {  // Making room for suffix $#$
					c=string.getElementAt(stringLength-1);
					bwtBlock.push(c);
					localBlockCounts[rightSplitter].incrementElementAt(c);
					pointer=1;
					bwtBlockLength=1;
				}
				else {
					pointer=blockStarts.getElementAt(rightSplitter);
					bwtBlockLength=0;
				}
				for (il=0; il<blockLength; il++) {
					suffix=suffixArrayBlock.getElementAt(il);
					if (suffix!=0) {
						c=string.getElementAt(suffix-1);
						bwtBlock.push(c);
						localBlockCounts[rightSplitter].incrementElementAt(c);
						bwtBlockLength++;
					}
					else {
						sharpPosition.set(pointer);
						sharpBlock.set(rightSplitter);
						sharpOffset.set(bwtBlockLength);
					}
					pointer++;
				}
				if (rightSplitter!=nSplitters) {
					if (rightSplitterSuffix!=0) {
						c=string.getElementAt(rightSplitterSuffix-1);
						bwtBlock.push(c);
						localBlockCounts[rightSplitter].incrementElementAt(c);
						bwtBlockLength++;
					}
					else {
						sharpPosition.set(pointer);
						sharpBlock.set(rightSplitter);
						sharpOffset.set(bwtBlockLength);
					}
					pointer++;
				}
				if (bwtBlockLength==0) {  // Skipping blocks with no BWT character
					latch.countDown();
					continue;
				}

				// Building a wavelet tree on the effective alphabet of the BWT block
				effectiveAlphabetLength=0;
				for (i=0; i<alphabetLength; i++) {
					if (localBlockCounts[rightSplitter].getElementAt(i)!=0) effectiveAlphabetLength++;
				}
				effectiveAlphabet = new int[effectiveAlphabetLength];
				effectiveCounts = new IntArray(effectiveAlphabetLength,Utils.bitsToEncode(bwtBlockLength),false);
				j=0;
				for (i=0; i<alphabetLength; i++) {
					count=localBlockCounts[rightSplitter].getElementAt(i);
					if (count!=0) {
						effectiveAlphabet[j++]=i;
						effectiveCounts.push(count);
					}
				}
				waveletTrees[rightSplitter] = new HuffmanWaveletTree(bwtBlock,effectiveAlphabet,effectiveCounts);
				latch.countDown();
			}
			suffixArrayBlock.deallocate(); suffixArrayBlock=null;
			bwtBlock.deallocate(); bwtBlock=null;
		}
	}


	/**
	 * @return the number of suffixes to be put in a block of $blockwiseBWT$, given that
	 * the maximum memory available to $blockwiseBWT$ is $availableMemory$ bits, and that
	 * the maximum number of threads available to $blockwiseBWT$ is $nThreads$.
	 */
	public static final long blockwiseBWT_getBlockSize(long stringLength, int log2stringLength, int log2alphabetLength) {
		/*
		availableMemory = 2*(stringLength/blockSize)*log2stringLength +  // $binarySearchCache$
					        (stringLength/blockSize)*log2stringLength +  // $splitters_byPosition$
					        (stringLength/blockSize)*log2stringLength +  // $splitters_bySuffix$
					        (stringLength/blockSize)*log2stringLength +  // $splitters$
					        (stringLength/blockSize)*log2stringLength +  // $blockStarts$
					        (stringLength/blockSize)*(DISTINGUISHING_PREFIX+1)*log2stringLength +  // $lcpArrays$
					        (stringLength/blockSize)*32 +  // $blockSizes$
						    nThreads*blockSize*log2stringLength +  // $suffixArrayBlock$
						    nThreads*blockSize*log2alphabetLength;  // $bwtBlock$
		*/
		long availableMemory = Constants.MAX_MEMORY<<3;
		long c = 2*stringLength*log2stringLength +
			 	 4*stringLength*log2stringLength +
				 stringLength*(Constants.DISTINGUISHING_PREFIX+1)*log2stringLength +
				 stringLength*32;
		int a = Constants.N_THREADS*(log2stringLength+log2alphabetLength);
		double delta = availableMemory*availableMemory-4*a*c;
		long out = (long)((availableMemory+Math.sqrt(delta))/(2*a));
		return out>2?out:2;  // Putting at least two suffixes in a block
	}

}