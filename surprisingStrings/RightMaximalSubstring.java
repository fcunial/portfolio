/**
 * Instructs $SubstringIterator$ to visit only the right-maximal substrings $w$ of a
 * string. $intervals[c]$ contains the interval of $wc$ for all $c \in \Sigma \cup \{#\}$,
 * in lexicographic order, following the approach described in \cite{belazzougui2014linear}.
 * This choice is not suitable for large alphabets.
 */
public class RightMaximalSubstring extends Substring {

	protected int rightContext;


	/**
	 * Artificial no-argument constructor, used just to avoid compile-time errors.
	 * See the no-argument constructor of $Substring$ for details.
	 */
	protected RightMaximalSubstring() { }


	protected RightMaximalSubstring(int alphabetLength, int log2alphabetLength, int bitsToEncodeAlphabetLength, long bwtLength, int log2BWTLength, int bitsToEncodeBWTLength) {
		this.alphabetLength=alphabetLength;
		this.log2alphabetLength=log2alphabetLength;
		this.bitsToEncodeAlphabetLength=bitsToEncodeAlphabetLength;
		this.bwtLength=bwtLength;
		this.log2BWTLength=log2BWTLength;
		this.bitsToEncodeBWTLength=bitsToEncodeBWTLength;
		textLength=bwtLength-1;
		oneOverLogTextLength=1D/Math.log(textLength);
		MAX_INTERVALS=alphabetLength+1;
		BITS_TO_ENCODE_MAX_INTERVALS=Utils.bitsToEncode(MAX_INTERVALS);
		BWT_INTERVALS_ARE_SORTED=true;
		bwtIntervals = new long[MAX_INTERVALS][2];
	}


	protected Substring getInstance() {
		return new RightMaximalSubstring(alphabetLength,log2alphabetLength,bitsToEncodeAlphabetLength,bwtLength,log2BWTLength,bitsToEncodeBWTLength);
	}


	protected Substring getEpsilon(long[] C) {
		RightMaximalSubstring out = (RightMaximalSubstring)getInstance();

		// $bwtIntervals$
		out.nIntervals=alphabetLength+1;
		out.bwtIntervals[0][0]=0;  // $#$
		out.bwtIntervals[0][1]=0;
		for (int i=0; i<alphabetLength-1; i++) {  // Other characters
			out.bwtIntervals[i+1][0]=C[i];
			out.bwtIntervals[i+1][1]=C[i+1]-1;
		}
		out.bwtIntervals[alphabetLength][0]=C[alphabetLength-1];
		out.bwtIntervals[alphabetLength][1]=bwtLength-1;

		// Other variables
		out.address=-1;
		out.log2address=-1;
		out.previousAddress=-1;
		out.length=0;
		out.log2length=-1;
		out.bitsToEncodeLength=1;
		out.firstCharacter=-1;
		out.hasBeenExtended=false;
		out.hasBeenStolen=false;
		out.computeRightContext();

		return out;
	}


	protected final void computeRightContext() {
		rightContext=0;
		for (int c=0; c<nIntervals; c++) {
			if (bwtIntervals[c][1]>=bwtIntervals[c][0]) rightContext++;
		}
	}


	protected void initAfterExtending(Substring suffix, int firstCharacter, RigidStream characterStack, int[] buffer) {
		super.initAfterExtending(suffix,firstCharacter,characterStack,buffer);
		computeRightContext();
	}


	/**
	 * Pushes just right-maximal substrings on the stack
	 */
	protected boolean shouldBeExtendedLeft() {
		return rightContext>1;
	}


	protected long frequency() {
		return bwtIntervals[alphabetLength][1]>=bwtIntervals[0][0]?bwtIntervals[alphabetLength][1]-bwtIntervals[0][0]+1:0;
	}


	public String toString() {
		String out = super.toString()+" | ";
		out+="rightContext="+rightContext+" ";
		return out;
	}

}