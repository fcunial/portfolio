/**
 * Instructs $SubstringIterator$ to visit only the maximal repeats of a string.
 * Being dependent on $SubstringIterator$ and on $RightMaximalSubstring$, this class must
 * be adapted to the case of large alphabet.
 */
public class MaximalRepeat extends RightMaximalSubstring {

	protected int leftContext;

	/**
	 * TRUE iff $v=aw$ where $w$ is a maximal repeat and $a$ is a character.
	 */
	protected boolean isLeftExtensionOfMaximalRepeat;


	/**
	 * Artificial no-argument constructor, used just to avoid compile-time errors.
	 * See the no-argument constructor of $Substring$ for details.
	 */
	protected MaximalRepeat() { }


	protected MaximalRepeat(int alphabetLength, int log2alphabetLength, int bitsToEncodeAlphabetLength, long bwtLength, int log2BWTLength, int bitsToEncodeBWTLength) {
		super(alphabetLength,log2alphabetLength,bitsToEncodeAlphabetLength,bwtLength,log2BWTLength,bitsToEncodeBWTLength);
	}


	protected void clone(Substring other) {
		super.clone(other);
		MaximalRepeat mr = (MaximalRepeat)other;
		mr.leftContext=leftContext;
		mr.isLeftExtensionOfMaximalRepeat=isLeftExtensionOfMaximalRepeat;
	}


	protected Substring getInstance() {
		return new MaximalRepeat(alphabetLength,log2alphabetLength,bitsToEncodeAlphabetLength,bwtLength,log2BWTLength,bitsToEncodeBWTLength);
	}


	protected Substring getEpsilon(long[] C) {
		MaximalRepeat out = (MaximalRepeat)getInstance();

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
		out.leftContext=-1;
		out.isLeftExtensionOfMaximalRepeat=false;

		return out;
	}


	public String toString() {
		String out = super.toString()+" | ";
		out+="leftContext="+leftContext+" ";
		out+="isLeftExtensionOfMaximalRepeat="+isLeftExtensionOfMaximalRepeat+" ";
		return out;
	}


	protected final void computeLeftContext(Substring[] leftExtensions) {
		leftContext=0;
		for (int i=0; i<alphabetLength+1; i++) {
			if (leftExtensions[i].frequency()>0) leftContext++;
		}
	}


	protected void visited(Stream stack, RigidStream characterStack, SimpleStream pointerStack, Substring[] cache, Substring[] leftExtensions) {
		super.visited(stack,characterStack,pointerStack,cache,leftExtensions);

		// Maximal repeat
		computeLeftContext(leftExtensions);

		// Left-extensions of maximal repeats
		if (leftContext>1) {
			MaximalRepeat mr;
			for (int i=1; i<alphabetLength+1; i++) {  // Disregarding $#$
				mr=(MaximalRepeat)leftExtensions[i];
				if (leftExtensions[i].frequency()>0) mr.isLeftExtensionOfMaximalRepeat=true;
			}
		}
	}

}