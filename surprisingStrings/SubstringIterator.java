import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Iterates in parallel over all the distinct substrings of positive length of a given
 * string $s$ using a BWT index, or equivalently following a depth-first search over the
 * trie of the reverse of $s$. The depth-first search logic is programmable, i.e. subclasses
 * of $Substring$ define which left-extensions of the current string should be explored.
 * In a shared-memory machine the search is automatically parallelized, and the user has
 * no control over work distribution -- this resembles general-purpose parallel frameworks
 * like e.g. \cite{finkel1987dib}. The original string $s$ can be safely deallocated after
 * this class has been constructed.
 *
 * Remark: The BWT is implemented as a sequence of Huffman-shaped wavelet trees, one per
 * BWT block. All BWT blocks have approximately the same size (determined by the memory
 * available during construction), so the total size of all wavelet trees is approximately
 * $|s|*H_k(s)+blockSize*alphabetLength^k$ bits for any given $k$, where $H_k(s)$ is the
 * $k$-th order entropy of $s$ \cite{karkkainen2011fixed}. This can be significantly
 * smaller than $|s|*\log_2(alphabetLength)$ if $s$ is compressible. The number of blocks
 * is assumed to be at most $Integer.MAX_VALUE$.
 *
 * Remark: In practice this fixed-block approach should be approximately as fast as a
 * single Huffman-shaped wavelet tree if $s$ is incompressible, and possibly faster if $s$
 * is compressible \cite{karkkainen2011fixed}.
 */
public class SubstringIterator {
	/**
	 * Constants
	 */
	private final int alphabetLength, log2alphabetLength;
	private int nBlocks;
	private Substring SUBSTRING_CLASS;  // Subclass of $Substring$ to be used during navigation

	/**
	 * BWT index
	 */
	private HuffmanWaveletTree[] waveletTrees;
	private IntArray[] blockCounts;  // Number of occurrences of each character before the beginning of each block, excluding $#$.
	private IntArray blockStarts;  // Starting position of each block
	private Rank9 blockBoundaries;  // Dense representation of a sparse bitvector, for speed.
	private long[] C;  // The $C$ array in backward search (excludes $#$).

	/**
	 * $sharp[0]$: position of the sharp character in the BWT;
	 * $sharp[1]$: BWT block containing the sharp character;
	 * $sharp[2]$: position of the sharp character inside the BWT block $sharp[1]$.
	 */
	private long[] sharp;


	/**
	 * @param substringClass subclass of $Substring$ to be used during navigation.
	 */
	public SubstringIterator(IntArray string, int[] alphabet, int alphabetLength, Substring substringClass) {
		final long stringLength = string.length();
		final int log2stringLength = Utils.log2(stringLength);
		final int log2stringLengthPlusOne = Utils.log2(stringLength+1);
		this.alphabetLength=alphabetLength;
		log2alphabetLength=Utils.log2(alphabetLength);
		long blockSize = Suffixes.blockwiseBWT_getBlockSize(stringLength,log2stringLength,log2alphabetLength);
//blockSize=10;
		long nb = Utils.divideAndRoundUp(stringLength,blockSize);  // This value is just an upper bound: $Suffixes.blockwiseBWT$ will set the effective number of blocks.
		if (nb<4) nBlocks=4;
		else if (nb>Integer.MAX_VALUE) nBlocks=Integer.MAX_VALUE;
		else nBlocks=(int)nb;
		waveletTrees = new HuffmanWaveletTree[nBlocks];
		blockStarts = new IntArray(nBlocks,log2stringLengthPlusOne,false);
		sharp = new long[3];
		IntArray[] localBlockCounts = new IntArray[nBlocks];
		IntArray bitVector = new IntArray(stringLength+1,1,true);
		Suffixes.blockwiseBWT(string,alphabet,alphabetLength,log2alphabetLength,blockSize,null,waveletTrees,blockStarts,bitVector,localBlockCounts,sharp);
		blockBoundaries = new Rank9(bitVector);
		bitVector.deallocate(); bitVector=null;
		nBlocks=(int)( blockStarts.length() );  // Setting the effective number of blocks
		SUBSTRING_CLASS=substringClass;

		// Building $blockCounts$ and $C$
		int i, j;
		long max;
		long[] characterCounts = new long[alphabetLength];
		blockCounts = new IntArray[nBlocks];
		blockCounts[0] = new IntArray(alphabetLength,1,true);
		for (i=1; i<nBlocks; i++) {
			max=0;
			for (j=0; j<alphabetLength; j++) {
				characterCounts[j]+=localBlockCounts[i-1].getElementAt(j);
				if (characterCounts[j]>max) max=characterCounts[j];
			}
			blockCounts[i] = new IntArray(alphabetLength,Utils.bitsToEncode(max));
			for (j=0; j<alphabetLength; j++) blockCounts[i].setElementAt(j,characterCounts[j]);
		}
		for (j=0; j<alphabetLength; j++) characterCounts[j]+=localBlockCounts[nBlocks-1].getElementAt(j);
		C = new long[alphabetLength];
		C[0]=1;
		for (j=1; j<alphabetLength; j++) C[j]=C[j-1]+characterCounts[j-1];
	}


	/**
	 * Extends to the left the first substring $w$ from the top of $stack$ that has not
	 * been extended yet, popping out of $stack$ all the substrings met before $w$ that
	 * have already been extended. Extensions $aw$, $a \in \{\Sigma \cup #\}$, such that
	 * their method $occurs$ returns true, are notified by calling their method $visited$,
	 * and they are pushed onto $stack$ if their method $shouldBeExtendedLeft$ returns
	 * true.
	 *
	 * @param stack the stream pointer is assumed to be at the first bit of the serialized
	 * substring at the top of $stack$;
	 * @param w non-null temporary, reused container representing the string at the top of
	 * $stack$;
	 * @param leftExtensions $alphabetLength+1$ non-null temporary, reused containers
	 * representing $aw$ for all $a \in \Sigma$; $#$ is assigned element 0, and all other
	 * characters are shifted forward by one;
	 * @param positions $w.nIntervals*2$ non-null temporary, reused containers of the
	 * interval positions of $w$;
	 * @param multirankStack temporary, reused space with $1+w.nIntervals*2$ columns and
	 * $alphabetLength-1$ rows used by $HuffmanWaveletTree.multirank$;
	 * @param multirankOutput temporary, reused space with $alphabetLength+1$ rows and
	 * $w.nIntervals*2$ columns used by $HuffmanWaveletTree.multirank$;
	 * @param multirankOnes temporary, reused space with $w.nIntervals*2$ cells used by
	 * $HuffmanWaveletTree.multirank$;
	 * @param out cell 0: the (possibly negative) variation in the total number of strings
	 * present in $stack$, induced by this call to $extendLeft$;
	 * cell 1: the variation $-1 \leq \delta < alphabetLength+1$ in the number of
	 * \emph{non-extended} strings $v$ in $stack$, induced by this call to $extendLeft$;
	 * cell 2: as in cell 1, but only for strings with $|v| \leq maxStringLengthToReport$.
	 * @param extensionBuffer reused memory area that contains messages for initializing
	 * the left extensions of $w$. We assume $buffer[i]=-1$ for all $i$. The procedure
	 * restores $buffer$ to its input state before terminating.
	 * @param shouldBeExtendedLeft reused memory area with at least $alphabetLength+1$
	 * cells, initialized to FALSE. This procedure restores the vector to its input
	 * state before terminating.
	 */
	private final void extendLeft(Stream stack, RigidStream characterStack, SimpleStream pointerStack, Substring w, Substring[] leftExtensions, Position[] positions, long[][] multirankStack, long[][] multirankOutput, long[] multirankOnes, int maxStringLengthToReport, long[] out, int[] extensionBuffer, boolean[] shouldBeExtendedLeft, Substring[] cache) {
		final boolean isShort;
		boolean pushed;
		int i, j, c, p, windowFirst, windowSize, block, previousBlock, nPositions, maxExtension;
		long pos, previous, frequency, maxFrequency;
		Substring extension;

		// Reading the top of $stack$
		out[0]=0; out[1]=0; out[2]=0;
		w.read(stack,cache,true,true,true);
		while (w.hasBeenExtended || w.hasBeenStolen) {
			previous=w.previousAddress;
			w.pop(stack,cache);
			if (w.hasBeenExtended) {
				characterStack.pop();
				pointerStack.pop();
				if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|-|i|"+( log2alphabetLength+64 ));
			}
			out[0]--;
			stack.setPosition(previous);
			if (previous==0) return;
			w.read(stack,cache,true,true,true);
		}

		// Putting the positions of $w.bwtIntervals$ in block order, and sequentially
		// inside each block. Since this iterator is generic, we do not assume the
		// positions in $w.bwtIntervals$ to be already sorted.
		for (i=0; i<w.nIntervals; i++) {
			p=i<<1; pos=w.bwtIntervals[i][0];
			positions[p].position=pos; positions[p].row=i; positions[p].column=0;
			positions[p].block=(int)blockBoundaries.rank(pos+1)-1;
			p=(i<<1)+1; pos=w.bwtIntervals[i][1]+1;
			positions[p].position=pos; positions[p].row=i; positions[p].column=1;
			positions[p].block=(int)blockBoundaries.rank(pos+1)-1;
		}
		if (!w.BWT_INTERVALS_ARE_SORTED) Arrays.sort(positions);
		nPositions=w.nIntervals<<1;

		// Ranking all positions in the same block using exactly one $multirank$ call
		for (i=0; i<=alphabetLength; i++) leftExtensions[i].nIntervals=w.nIntervals;
		windowFirst=0; windowSize=1;
		previousBlock=positions[windowFirst].block;
		multirankStack[0][1]=positions[windowFirst].position-blockStarts.getElementAt(previousBlock);
		for (p=1; p<nPositions; p++) {
			block=positions[p].block;
			if (block==previousBlock) {
				windowSize++;
				multirankStack[0][windowSize]=positions[p].position-blockStarts.getElementAt(block);
			}
			else {
				for (i=0; i<multirankOutput.length; i++) {
					for (j=0; j<multirankOutput[i].length; j++) multirankOutput[i][j]=0;
				}
				handleLeftExtensionsBySharp(positions,windowFirst,windowSize,previousBlock,leftExtensions,multirankStack);
				if (waveletTrees[previousBlock]!=null) {
					// There can be exactly one block with null elements in $waveletTrees$:
					// it corresponds to a splitter at the position of $#$ in the BWT,
					// preceded by another splitter.
					waveletTrees[previousBlock].multirank(alphabetLength,windowSize,multirankStack,multirankOutput,multirankOnes);
				}
				for (c=0; c<alphabetLength; c++) {
					for (i=0; i<windowSize; i++) leftExtensions[c+1].bwtIntervals[positions[windowFirst+i].row][positions[windowFirst+i].column]=C[c]+(blockCounts[previousBlock].getElementAt(c)+multirankOutput[c][i])+(positions[windowFirst+i].column==0?0:-1);
				}
				windowFirst=p; windowSize=1; previousBlock=block;
				multirankStack[0][1]=positions[p].position-blockStarts.getElementAt(block);
			}
		}
		// Last block
		handleLeftExtensionsBySharp(positions,windowFirst,windowSize,previousBlock,leftExtensions,multirankStack);
		for (i=0; i<multirankOutput.length; i++) {
			for (j=0; j<multirankOutput[i].length; j++) multirankOutput[i][j]=0;
		}
		if (waveletTrees[previousBlock]!=null) waveletTrees[previousBlock].multirank(alphabetLength,windowSize,multirankStack,multirankOutput,multirankOnes);
		for (c=0; c<alphabetLength; c++) {
			for (i=0; i<windowSize; i++) leftExtensions[c+1].bwtIntervals[positions[windowFirst+i].row][positions[windowFirst+i].column]=C[c]+(blockCounts[previousBlock].getElementAt(c)+multirankOutput[c][i])+(positions[windowFirst+i].column==0?0:-1);
		}

		// Initializing $w$
		if (w.length>0) {
			characterStack.push(w.firstCharacter);
			pointerStack.push(w.address);
			if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|+|i|"+( log2alphabetLength+64 ));
		}
		w.initAfterReading(stack,characterStack,pointerStack,cache);

		// Initializing the left-extensions of $w$
		extension=null; pushed=false;
		w.fillBuffer(extensionBuffer,true);
		maxFrequency=0; maxExtension=-1;
		for (c=0; c<alphabetLength+1; c++) {
			extension=leftExtensions[c];
			frequency=extension.frequency();
			if (frequency>0) {
				extension.initAfterExtending(w,c-1,characterStack,extensionBuffer);
				if (extension.shouldBeExtendedLeft()) {
					pushed=true;
					shouldBeExtendedLeft[c]=true;
					if (frequency>maxFrequency) {
						maxFrequency=frequency;
						maxExtension=c;
					}
				}
			}
		}
		w.emptyBuffer(extensionBuffer,true);

		// Visiting $w$, popping TAIL and TAIL', and pushing APPENDIX.
		w.visited(stack,characterStack,pointerStack,cache,leftExtensions);
		w.markAsExtended(stack);
		w.popTails(stack,cache);
		w.pushAppendix(stack,cache);
		out[1]--;
		if (w.length<=maxStringLengthToReport) out[2]--;

		// Pushing the left-extensions of $w$ onto $stack$ using the stack trick described
		// in \cite{belazzougui2013versatile}.
		previous=w.address;
		isShort=w.length+1<=maxStringLengthToReport;
		if (pushed) {
			// Pushing the most frequent left-extension first
			extension=leftExtensions[maxExtension];
			extension.previousAddress=previous;
			extension.push(stack,cache);
			previous=extension.address;
			out[0]++; out[1]++;
			if (isShort) out[2]++;
			// Pushing all other left-extensions
			for (c=0; c<alphabetLength+1; c++) {
				if (shouldBeExtendedLeft[c]) {
					shouldBeExtendedLeft[c]=false;  // Cleaning up $shouldBeExtendedLeft$
					if (c==maxExtension) continue;
					extension=leftExtensions[c];
					extension.previousAddress=previous;
					extension.push(stack,cache);
					previous=extension.address;
					out[0]++; out[1]++;
					if (isShort) out[2]++;
				}
			}
		}

		// Resetting the top of the stack
		stack.setPosition(pushed?previous:w.address);
	}


	private static class Position implements Comparable {
		protected long position;
		protected int block, row, column;

		public int compareTo(Object other) {
			Position otherPosition = (Position)other;
			if (block<otherPosition.block) return -1;
			if (block>otherPosition.block) return 1;
			if (position<otherPosition.position) return -1;
			if (position>otherPosition.position) return 1;
			return 0;
		}
	}


	/**
	 * Handles the left-extension by $#$ in $extendLeft$.
	 */
	private final void handleLeftExtensionsBySharp(Position[] positions, int firstPosition, int nPositions, int block, Substring[] leftExtensions, long[][] multirankStack) {
		int i;
		if (block>sharp[1]) {
			for (i=1; i<=nPositions; i++) {
				if (positions[firstPosition+i-1].column==0) leftExtensions[0].bwtIntervals[positions[firstPosition+i-1].row][positions[firstPosition+i-1].column]=1;
				else leftExtensions[0].bwtIntervals[positions[firstPosition+i-1].row][positions[firstPosition+i-1].column]=0;
			}
		}
		else if (block<sharp[1]) {
			for (i=1; i<=nPositions; i++) {
				if (positions[firstPosition+i-1].column==0) leftExtensions[0].bwtIntervals[positions[firstPosition+i-1].row][positions[firstPosition+i-1].column]=0;
				else leftExtensions[0].bwtIntervals[positions[firstPosition+i-1].row][positions[firstPosition+i-1].column]=-1;
			}
		}
		else {
			for (i=1; i<=nPositions && positions[firstPosition+i-1].position<=sharp[0]; i++) {
				if (positions[firstPosition+i-1].column==0) leftExtensions[0].bwtIntervals[positions[firstPosition+i-1].row][positions[firstPosition+i-1].column]=0;
				else leftExtensions[0].bwtIntervals[positions[firstPosition+i-1].row][positions[firstPosition+i-1].column]=-1;
			}
			for (; i<=nPositions; i++) {
				if (positions[firstPosition+i-1].column==0) leftExtensions[0].bwtIntervals[positions[firstPosition+i-1].row][positions[firstPosition+i-1].column]=1;
				else leftExtensions[0].bwtIntervals[positions[firstPosition+i-1].row][positions[firstPosition+i-1].column]=0;
				multirankStack[0][i]--;  // This wavelet tree does not contain the position of $#$
			}
		}
	}



/*                      _____ _                        _
                       |_   _| |                      | |
                         | | | |__  _ __ ___  __ _  __| |___
                         | | | '_ \| '__/ _ \/ _` |/ _` / __|
                         | | | | | | | |  __/ (_| | (_| \__ \
                         \_/ |_| |_|_|  \___|\__,_|\__,_|___/                           */
	/**
	 * @param nThreads maximum number of threads to be used during traversal.
	 */
	public void run() {
		int i, nCharacters;
		long previous;
		SubstringIteratorThread[] threads = new SubstringIteratorThread[Constants.N_THREADS];
		AtomicInteger donorGenerator = new AtomicInteger();
		CountDownLatch latch = new CountDownLatch(Constants.N_THREADS);
		for (i=0; i<Constants.N_THREADS; i++) threads[i] = new SubstringIteratorThread(threads,i,donorGenerator,latch);

		// Initializing the stack of $threads[0]$ with an artificial substring followed by
		// $\epsilon$. The artificial substring is pushed in order to detect when the
		// stack becomes empty by issuing $stack.getPosition()>0$, since we cannot store
		// negative numbers in the stack. Thus, a stack always contains at least the
		// artificial string, except for the stacks of threads different from $threads[0]$
		// immediately after their creation.
		Substring epsilon = SUBSTRING_CLASS.getEpsilon(C);
		epsilon.push(threads[0].stack,null);
		threads[0].stack.setPosition(0);
		epsilon.deallocate(); epsilon=null;
		threads[0].nStrings=1;
		threads[0].nStringsNotExtended=1;
		threads[0].nShortStringsNotExtended=0;

		// Launching all threads
		for (i=0; i<Constants.N_THREADS; i++) threads[i].start();
		try { latch.await(); }
		catch(InterruptedException e) {
			e.printStackTrace();
			System.exit(1);
		}
		for (i=0; i<Constants.N_THREADS; i++) threads[i].deallocate();
	}


	/**
	 * Explores a partition of the trie of the reverse of $s$ in depth-first order, by
	 * repeatedly invoking $extendLeft$. For load-balancing, the thread uses the
	 * work-stealing strategy described in \cite{rao1987parallel}.
	 *
	 * Remark: A thread with empty stack scans the list of threads for a donor
	 * sequentially, using an atomic donor pointer shared by all threads as described in
	 * \cite{kumar1987parallel}.
	 *
	 * Remark: The current work-stealing strategy requires splitting a prefix of the
	 * stack of the donor. This allows to trade load balancing (which, in the general case
	 * of an irregular trie, improves with the length of the prefix) with the speed of the
	 * work-balancing code (which degrades with the length of the prefix).
	 *
	 * Remark: A simple lower bound on the size of the donor stack up to the prefix
	 * threshold tries to limit cases in which splitting the stack is slower than having
	 * it processed by the donor itself. We use this simple strategy because this engine
	 * is general-purpose, so we cannot estimate exploration time (we might add a
	 * corresponding method to $Substring$ and force subclasses to extend it: we leave
	 * this to future extensions).
	 *
	 * Remark: Another possible improvement described in \cite{rao1987parallel} consists
	 * in avoiding the synchronization with the donor thread if the donor is working
	 * deeply enough with respect to the split prefix. Once again, we can't implement this
	 * approach in a general-purpose engine, because we cannot estimate expansion time.
	 *
	 * Remark: We choose not to precompute a large, static set of fine-grained, fixed-size
	 * workpackets, as described in \cite{reinefeld1994work}, because we want to use as
	 * little space as possible -- voluntarily paying the smaller space with the workload
	 * imbalance and communication overheads that come from dynamic workpackets of
	 * variable size.
	 */
	protected class SubstringIteratorThread extends Thread {
		/**
		 * Thread variables
		 */
		protected Stream stack;
		protected RigidStream characterStack;
		protected SimpleStream pointerStack;
		protected Substring[] cache;
		protected long nStrings;  // Total number of strings in $stack$
		protected long nStringsNotExtended;  // Number of strings in $stack$ that have not been extended
		protected long nShortStringsNotExtended;  // Number of strings in $stack$ that have not been extended, and that have length in $[1..MAX_STRING_LENGTH_FOR_SPLIT]$.
		private boolean isAlive;  // Flags a dead thread
		private SubstringIteratorThread[] threads;  // Pointers to all threads
		private final int nThreads;  // Number of threads in $threads$
		private final int threadID;  // Position of this thread in $threads$
		private AtomicInteger donorGenerator;  // Global generator of donor pointers
		private CountDownLatch latch;  // Global barrier
		private XorShiftStarRandom random;

		/*
		 * $stealWork$-related variables
		 */
		private SubstringIteratorThread donor;
		private Stream donorStack;
		private RigidStream donorCharacterStack;
		private long donorStackLength;  // In bits
		private long newStack_previousSubstringAddress;


		public SubstringIteratorThread(SubstringIteratorThread[] threads, int threadID, AtomicInteger donorGenerator, CountDownLatch latch) {
			this.threads=threads;
			nThreads=threads.length;
			this.threadID=threadID;
			this.donorGenerator=donorGenerator;
			this.latch=latch;
			stack = new Stream(Constants.LONGS_PER_REGION);
			characterStack = new RigidStream(log2alphabetLength,Constants.LONGS_PER_REGION_CHARACTERSTACK);
			pointerStack = new SimpleStream(Constants.LONGS_PER_REGION_POINTERSTACK);
			cache = new Substring[Constants.CACHE_SIZE];
			for (int i=0; i<Constants.CACHE_SIZE; i++) cache[i]=SUBSTRING_CLASS.getInstance();
			random = new XorShiftStarRandom();
		}


		private final void deallocate() {
			stack.deallocate(); stack=null;
			characterStack.deallocate(); characterStack=null;
			pointerStack.deallocate(); pointerStack=null;
			for (int i=0; i<Constants.CACHE_SIZE; i++) {
				cache[i].deallocate();
				cache[i]=null;
			}
			cache=null;
			threads=null;
			donor=null;
			donorStack=null;
		}


		public void run() {
			int i;
			Substring[] leftExtensions = new Substring[alphabetLength+1];
			for (i=0; i<alphabetLength+1; i++) leftExtensions[i]=SUBSTRING_CLASS.getInstance();
			final int maxPositions = SUBSTRING_CLASS.MAX_INTERVALS<<1;
			Position[] positions = new Position[maxPositions];
			for (i=0; i<maxPositions; i++) positions[i] = new Position();
			long[][] multirankStack = new long[alphabetLength-1][1+maxPositions];
			long[][] multirankOutput = new long[alphabetLength][maxPositions];
			long[] multirankOnes = new long[maxPositions];
			long[] extendLeftOutput = new long[3];
			int[] extensionBuffer = new int[alphabetLength+1];
			for (i=0; i<=alphabetLength; i++) extensionBuffer[i]=-1;
			boolean[] shouldBeExtendedLeft = new boolean[alphabetLength+1];
			for (i=0; i<=alphabetLength; i++) shouldBeExtendedLeft[i]=false;
			Substring w = SUBSTRING_CLASS.getInstance();

			isAlive=true;
			if (Constants.N_THREADS>1 && nStringsNotExtended==0) stealWork();
			while (nStringsNotExtended>0) {
				// Exhausting the current stack
				while (true) {
					synchronized(this) {
						if (nStringsNotExtended>0) {
							extendLeft(stack,characterStack,pointerStack,w,leftExtensions,positions,multirankStack,multirankOutput,multirankOnes,Constants.MAX_STRING_LENGTH_FOR_SPLIT,extendLeftOutput,extensionBuffer,shouldBeExtendedLeft,cache);
							nStrings+=extendLeftOutput[0];
							nStringsNotExtended+=extendLeftOutput[1];
							nShortStringsNotExtended+=extendLeftOutput[2];
						}
						else break;
					}
				}
				// Trying to get a new stack
				if (Constants.N_THREADS>1) stealWork();
			}
			// Terminating if unable to steal work
			isAlive=false;
			latch.countDown();

java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
System.out.println(sdf.format(new java.util.Date())+"> thread "+this+" ends");
		}


		/**
		 * Remark: we need to get a lock on $this$ while running $stealWork$, since
		 * otherwise another thread could start using $this$ as a donor before copying is
		 * complete.
		 * Remark: the procedure avoids reallocating memory.
		 */
		private final void stealWork() {
			int i, j, d, tmp;
			long copied, toBeCopied, backupPointer, cumulativeSize, maxSize, value;
			int[] threadIDs = new int[nThreads];
			long[] threadSize = new long[nThreads];
			long[] tmpArray = new long[nThreads];
			Substring w = SUBSTRING_CLASS.getInstance();
			for (i=0; i<nThreads; i++) threadIDs[i]=i;
			threadIDs[0]=threadID; threadIDs[threadID]=0;

			// Trying $N_STEALING_ATTEMPTS$ times before giving up
			for (i=0; i<Constants.N_STEALING_ATTEMPTS; i++) {
				for (d=1; d<nThreads; d++) {
					// Measuring the size of each thread outside mutual exclusion:
					// this is just an approximation.
					cumulativeSize=0;
					maxSize=0;
					for (j=0; j<nThreads; j++) {
						threadSize[j]=threads[j].nShortStringsNotExtended;
						if (threadSize[j]>maxSize) maxSize=threadSize[j];
						cumulativeSize+=threadSize[j];
					}
					if (cumulativeSize>=nThreads) {
						System.arraycopy(threadSize,0,tmpArray,0,nThreads);
						Arrays.sort(tmpArray);
						do {
							value=tmpArray[nThreads-1-(nThreads>>2>0?random.nextInt(nThreads>>2):0)];  // Randomly sampling among the top $nThreads/4$ threads
							for (j=0; j<nThreads; j++) {
								if (threadSize[j]==value) break;
							}
						}
						while (j==threadID);
					}
					else {
						// Randomly choosing a thread
						j=d+random.nextInt(nThreads-d);
						if (j!=d) {
							tmp=threadIDs[d]; threadIDs[d]=threadIDs[j]; threadIDs[j]=tmp;
						}
						j=threadIDs[d];
					}
					donor=threads[j];
					synchronized(donor) {
						if (donor.isAlive && donor.nShortStringsNotExtended>=Constants.DONOR_STACK_LOWERBOUND) {
							synchronized(this) {
								donorStack=donor.stack;
								donorStackLength=donorStack.nBits();
								donorCharacterStack=donor.characterStack;
								stack.clear(false);  // Avoids reallocation
								characterStack.clear(false);
								pointerStack.clear(false);
								nStrings=0;
								nStringsNotExtended=0;
								nShortStringsNotExtended=0;
								newStack_previousSubstringAddress=0;
								toBeCopied=donor.nShortStringsNotExtended>>1;
								copied=0;
								backupPointer=donorStack.getPosition();
								donorStack.setPosition(0);
								while (copied<toBeCopied) {
									w.read(donorStack,null,false,true,false);
									if (!w.hasBeenExtended && !w.hasBeenStolen) copied++;
									if (!w.hasBeenStolen) copy(w);
								}
								stack.setPosition(newStack_previousSubstringAddress);
								donorStack.setPosition(backupPointer);
								for (j=0; j<Constants.CACHE_SIZE; j++) donor.cache[j].clone(cache[j]);
								return;
							}
						}
					}
				}
				try { Thread.sleep(random.nextLong(Constants.MAX_WAITING_TIME)); }
				catch(InterruptedException e) {
					e.printStackTrace();
					System.exit(1);
				}
			}
		}


		/**
		 * Extended strings in the donor stack are copied to the new receiver stack.
		 * Non-extended strings in the donor stack are copied to the new receiver stack
		 * and marked as stolen in the donor stack, so they will be automatically popped
		 * out and discarded by $extendLeft$.
		 */
		private final void copy(Substring w) {
			if (!w.hasBeenExtended) {
				w.markAsStolen(donorStack);
				donor.nStringsNotExtended--;
				donor.nShortStringsNotExtended--;
			}
			w.previousAddress=newStack_previousSubstringAddress;
			w.push(stack,null);  // Not altering the cache
			nStrings++;
			if (w.hasBeenExtended) {
				if (w.length>0) {
					characterStack.push(donorCharacterStack.getElementAt(w.length-1));
					pointerStack.push(w.address);
				}
			}
			else {
				nStringsNotExtended++;
				nShortStringsNotExtended++;
			}
			newStack_previousSubstringAddress=w.address;
		}

	}  // SubstringIteratorThread

}