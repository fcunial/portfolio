import java.util.Arrays;

/**
 * Simplistic implementation of a Huffman-shaped wavelet tree with pointers, using
 * Sebastiano Vigna's $Rank9$ data structure \cite{vigna2008broadword} to support rank
 * operations inside a node.
 *
 * Remark: We try to optimize time rather than space. We allow construction to use
 * $|s|(H_0(s)+1)$ bits of additional space, where $s$ is the input string, i.e. we don't
 * implement in-place algorithms like \cite{tischler2011wavelet}.
 *
 * Remark: We assume that the alphabet is small. In this case, the Huffman-shaped wavelet
 * tree with pointers is among the best solutions both in space and in time for general
 * strings \cite{claude2009practical}, and it should behave even better for blocks of the
 * BWT. We don't implement the wavelet matrix \cite{claude2012wavelet} because it is
 * useful in practice only for large alphabets.
 */
public class HuffmanWaveletTree {

	/*
	 * The symbols that occur in $string$, sorted lexicographically.
	 */
	private final int[] alphabet;
	private final int alphabetLength, log2AlphabetLength;

	/**
	 * Tree topology and rank data structures. A nonnegative value in $leftChild[i]$,
	 * $rightChild[i]$ and $*Parent[i]$ is the position of the corresponding internal node
	 * in the arrays. A negative value $c$ identifies position $-1-c$ in $alphabet$.
	 */
	private int[] leftChild, rightChild, nodeParent, leafParent;
	protected Rank9[] rankDataStructures;

	/**
	 * Maximum number of bits in the Huffman code
	 */
	public final int maxCodeLength;


	/**
	 * @param alphabet only the distinct symbols that occur in $string$, sorted
	 * lexicographically;
	 * @param counts number of occurrences in $string$ of each symbol in $alphabet$.
	 */
	public HuffmanWaveletTree(IntArray string, int[] alphabet, IntArray counts) {
		final long stringLength, suffixArrayLength;
		int i, j, k, node, code, length;
		long il;
		boolean[][] codes;
		int[] codeLengths;
		long[] nBits;
		float[] frequencies;
		IntArray[] bitVectors;

		// Building tree topology and bit vectors
		stringLength=string.length();
		this.alphabet=alphabet;
		alphabetLength=alphabet.length;
		if (alphabetLength==1) {  // No need for a wavelet tree in this case
			log2AlphabetLength=0;
			codes=null;
			codeLengths=null;
			frequencies=null;
			maxCodeLength=0;
			bitVectors=null;
			nBits=null;
			return;
		}
		log2AlphabetLength=Utils.log2(alphabetLength);
		codes = new boolean[alphabetLength][alphabetLength-1];
		codeLengths = new int[alphabetLength];
		frequencies = new float[alphabetLength];
		for (i=0; i<alphabetLength; i++) frequencies[i]=((float)counts.getElementAt(i))/stringLength;
		maxCodeLength=buildHuffmanCodes(frequencies,codes,codeLengths);
		frequencies=null;
		bitVectors = new IntArray[alphabetLength-1];
		nBits = new long[alphabetLength-1];
		for (i=0; i<alphabetLength; i++) {
			node=leafParent[i];
			while (node!=-1) {
				nBits[node]+=counts.getElementAt(i);
				node=nodeParent[node];
			}
		}
		for (i=0; i<alphabetLength-1; i++) bitVectors[i] = new IntArray(nBits[i],1);
		nBits=null;

		// Pushing bits from $string$
		for (il=0; il<stringLength; il++) {
			j=Arrays.binarySearch(alphabet,(int)( string.getElementAt(il) ));
			length=codeLengths[j];
			node=alphabetLength-2;
			for (k=length-1; k>=0; k--) {
				if (codes[j][k]) {
					bitVectors[node].pushFromRight(1);  // $Rank9$ stores bits from right to left
					node=rightChild[node];
				}
				else {
					bitVectors[node].pushFromRight(0);  // $Rank9$ stores bits from right to left
					node=leftChild[node];
				}
			}
		}
		codes=null; codeLengths=null;
		rankDataStructures = new Rank9[alphabetLength-1];
		for (i=0; i<alphabetLength-1; i++) {
			rankDataStructures[i] = new Rank9(bitVectors[i]);
			bitVectors[i]=null;
		}
		bitVectors=null;
	}


	/**
	 * @param frequencies relative frequency of each symbol in $alphabet$; they are
	 * assumed to sum to one;
	 * @param codes output array that stores the Huffman code corresponding to each
	 * element of $alphabet$ at the end of the procedure, encoded as a *reversed* sequence
	 * of booleans; all cells of $codes$ are assumed to be FALSE at the beginning;
	 * representing a code as a sequence of booleans increases space (which is irrelevant
	 * under the assumption that $alphabet$ is small), but makes access to bits faster;
	 * @param codeLengths output array that stores the length in bits of each element
	 * of $codes$ at the end of the procedure; all cells of $codeLengths$ are assumed to
	 * be zero at the beginning;
	 * @return the maximum number in $codeLengths$.
	 */
	private final int buildHuffmanCodes(float[] frequencies, boolean[][] codes, int[] codeLengths) {
		int i, j, leafQueueFront, nodeQueueFront, nodeQueueBack, address, node, child, maxCodeLength;
		float nodeFrequency;
		int[] stack, sortedAlphabet;
		float[] sortedFrequencies, nodeFrequencies;
		long[] tmpArray;

		// Sorting alphabet by frequency
		tmpArray = new long[alphabetLength];
		for (i=0; i<alphabetLength; i++) tmpArray[i]=(((long)Float.floatToIntBits(frequencies[i]))<<32)|alphabet[i];
		Arrays.sort(tmpArray);
		sortedAlphabet = new int[alphabetLength];
		sortedFrequencies = new float[alphabetLength];
		for (i=0; i<alphabetLength; i++) {
			sortedFrequencies[i]=Float.intBitsToFloat((int)((tmpArray[i]&Utils.shiftOnesLeft[32])>>>32));
			sortedAlphabet[i]=(int)(tmpArray[i]&Utils.shiftOnesRight[32]);
		}
		tmpArray=null;

		// Building tree topology
		leftChild = new int[alphabetLength-1];
		rightChild = new int[alphabetLength-1];
		nodeParent = new int[alphabetLength-1];
		for (i=0; i<alphabetLength-1; i++) nodeParent[i]=-1;
		leafParent = new int[alphabetLength];
		for (i=0; i<alphabetLength; i++) leafParent[i]=-1;
		nodeFrequencies = new float[alphabetLength-1];
		for (i=0; i<alphabetLength-1; i++) nodeFrequencies[i]=1f;
		nodeQueueFront=0; nodeQueueBack=0; leafQueueFront=0;
		while (alphabetLength-leafQueueFront+nodeQueueBack-nodeQueueFront>1) {
			nodeFrequency=0f;
			if (leafQueueFront<alphabetLength && sortedFrequencies[leafQueueFront]<nodeFrequencies[nodeQueueFront]) {
				address=Arrays.binarySearch(alphabet,sortedAlphabet[leafQueueFront]);
				leftChild[nodeQueueBack]=-1-address;
				leafParent[address]=nodeQueueBack;
				nodeFrequency=sortedFrequencies[leafQueueFront];
				leafQueueFront++;
			}
			else {
				address=nodeQueueFront;
				leftChild[nodeQueueBack]=address;
				nodeParent[address]=nodeQueueBack;
				nodeFrequency=nodeFrequencies[nodeQueueFront];
				nodeQueueFront++;
			}
			if (leafQueueFront<alphabetLength && sortedFrequencies[leafQueueFront]<nodeFrequencies[nodeQueueFront]) {
				address=Arrays.binarySearch(alphabet,sortedAlphabet[leafQueueFront]);
				rightChild[nodeQueueBack]=-1-address;
				leafParent[address]=nodeQueueBack;
				nodeFrequency+=sortedFrequencies[leafQueueFront];
				leafQueueFront++;
			}
			else {
				address=nodeQueueFront;
				rightChild[nodeQueueBack]=address;
				nodeParent[address]=nodeQueueBack;
				nodeFrequency+=nodeFrequencies[nodeQueueFront];
				nodeQueueFront++;
			}
			nodeFrequencies[nodeQueueBack]=nodeFrequency;
			nodeQueueBack++;
		}
		sortedAlphabet=null;
		sortedFrequencies=null;
		nodeFrequencies=null;

		// Assigning codes
		for (i=0; i<alphabetLength; i++) {
			codeLengths[i]=0;
			node=leafParent[i];
			child=-1-i;
			while (node!=-1) {
				if (child==rightChild[node]) codes[i][codeLengths[i]]=true;
				codeLengths[i]++;
				child=node;
				node=nodeParent[node];
			}
		}

		// Returning the length of the longest code
		maxCodeLength=0;
		for (i=0; i<alphabetLength; i++) {
			if (codeLengths[i]>maxCodeLength) maxCodeLength=codeLengths[i];
		}
		return maxCodeLength;
	}


	/**
	 * @param position a valid position in $string$.
	 * @return the character in $alphabet$ that equals $string[position]$.
	 */
	public final int access(long position) {
		if (alphabetLength==1) return alphabet[0];
		int node = alphabetLength-2;
		long effectivePosition;
		while (node>=0) {
			effectivePosition=(position/64)*64 + (64-(position%64)-1);
			if (rankDataStructures[node].bitVector.getElementAt(effectivePosition)==0) {
				position-=rankDataStructures[node].rank(position);
				node=leftChild[node];
				if (node<0) return alphabet[-1-node];
			}
			else {
				position=rankDataStructures[node].rank(position);
				node=rightChild[node];
				if (node<0) return alphabet[-1-node];
			}
		}
		return -1;
	}


	/**
	 * Computes the number of occurrences of every symbol in $[0..fullAlphabetLength]$
	 * before each position of a list of distinct positions relative to $string$.
	 * The procedure touches all nodes of the wavelet tree. At each node, all input
	 * positions are ranked, to limit cache misses.
	 *
	 * Remark: $[0..fullAlphabetLength]$ is a superset of the alphabet of $string$ (stored
	 * in $alphabet$), since $string$ could be a substring of a longer string, with
	 * reduced alphabet.
	 *
	 * @param nPositions number of positions in $string$ to rank, assumed to be at most
	 * $Integer.MAX_VALUE-1$;
	 * @param stack a temporary matrix with at least $fullAlphabetLength-1$ rows and
	 * $1+nPositions$ columns; the procedure assumes that the positions to be ranked are
	 * written in increasing order in row 0, starting from index 1 (one, not zero); the
	 * content of this matrix is altered by the procedure;
	 * @param output output matrix with at least $fullAlphabetLength$ rows and
	 * $nPositions$ columns; the alphabet is assumed to be sorted lexicographically; all
	 * cell of $output$ are assumed to be zero;
	 * @param ones a temporary array with at least $nPositions$ cells; the
	 * content of this array is altered by the procedure.
	 */
	public final void multirank(int fullAlphabetLength, int nPositions, long[][] stack, long[][] output, long[] ones) {
		int i, j, currentBlock, lastBlock, node, address;

		// Characters that do not belong to $alphabet$ are already handled by the
		// assumption that all cells of $output$ are zero.

		// Handling characters that belong to $alphabet$
		if (alphabetLength==1) {
			for (j=0; j<nPositions; j++) output[alphabet[0]][j]=stack[0][j+1];
			return;
		}
		stack[0][0]=alphabetLength-2;
		currentBlock=0; lastBlock=0;
		while (currentBlock<=lastBlock) {
			node=(int)stack[currentBlock][0];
			for (i=0; i<nPositions; i++) ones[i]=rankDataStructures[node].rank(stack[currentBlock][1+i]);
			address=leftChild[node];
			if (address<0) {
				for (i=0; i<nPositions; i++) output[alphabet[-1-address]][i]=stack[currentBlock][1+i]-ones[i];
			}
			else {
				lastBlock++;
				stack[lastBlock][0]=address;
				for (i=0; i<nPositions; i++) stack[lastBlock][1+i]=stack[currentBlock][1+i]-ones[i];
			}
			address=rightChild[node];
			if (address<0) System.arraycopy(ones,0,output[alphabet[-1-address]],0,nPositions);
			else {
				lastBlock++;
				stack[lastBlock][0]=address;
				System.arraycopy(ones,0,stack[lastBlock],1,nPositions);
			}
			currentBlock++;
		}
	}


	public void print() {
		System.out.print("leftChild: ");
		for (int i=0; i<alphabetLength-1; i++) System.out.print(leftChild[i]+" ");
		System.out.println();

		System.out.print("rightChild: ");
		for (int i=0; i<alphabetLength-1; i++) System.out.print(rightChild[i]+" ");
		System.out.println();

		System.out.print("nodeParent: ");
		for (int i=0; i<alphabetLength-1; i++) System.out.print(nodeParent[i]+" ");
		System.out.println();

		System.out.print("leafParent: ");
		for (int i=0; i<alphabetLength; i++) System.out.print(leafParent[i]+" ");
		System.out.println();

		System.out.println("bitvectors: ");
		for (int i=0; i<alphabetLength-1; i++) {
			rankDataStructures[i].bitVector.printBits(); System.out.println();
		}
	}

}