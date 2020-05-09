/**
 * Instructs $SubstringIterator$ to visit only the minimal absent words of a string.
 * Being dependent on $SubstringIterator$ and on $RightMaximalSubstring$, this class must
 * be adapted to the case of large alphabet.
 */
public class MinimalAbsentWord extends MaximalRepeat {

	protected int[][] minimalAbsent;
	protected int lastMinimalAbsent;

	/**
	 * Scratch space, allocated at most once.
	 */
	private MinimalAbsentWord tmpString;


	/**
	 * Artificial no-argument constructor, used just to avoid compile-time errors.
	 * See the no-argument constructor of $Substring$ for details.
	 */
	protected MinimalAbsentWord() { }


	protected MinimalAbsentWord(int alphabetLength, int log2alphabetLength, int bitsToEncodeAlphabetLength, long bwtLength, int log2BWTLength, int bitsToEncodeBWTLength) {
		super(alphabetLength,log2alphabetLength,bitsToEncodeAlphabetLength,bwtLength,log2BWTLength,bitsToEncodeBWTLength);
		minimalAbsent = new int[alphabetLength*alphabetLength][2];
	}


	protected Substring getInstance() {
		return new MinimalAbsentWord(alphabetLength,log2alphabetLength,bitsToEncodeAlphabetLength,bwtLength,log2BWTLength,bitsToEncodeBWTLength);
	}


	protected void visited(Stream stack, RigidStream characterStack, SimpleStream pointerStack, Substring[] cache, Substring[] leftExtensions) {
		super.visited(stack,characterStack,pointerStack,cache,leftExtensions);
		if (leftContext<2) return;

		int i, j;
		lastMinimalAbsent=-1;
		for (i=1; i<alphabetLength+1; i++) {  // Discarding $#$
			if (leftExtensions[i].frequency()==0) continue;
			for (j=1; j<alphabetLength+1; j++) {
				if (bwtIntervals[j][1]<bwtIntervals[j][0]) continue;
				if (leftExtensions[i].bwtIntervals[j][1]>=leftExtensions[i].bwtIntervals[j][0]) continue;
				lastMinimalAbsent++;
				minimalAbsent[lastMinimalAbsent][0]=i-1;
				minimalAbsent[lastMinimalAbsent][1]=j-1;
			}
		}
	}

}