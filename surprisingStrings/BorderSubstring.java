import java.util.Arrays;

/**
 * A right-maximal substring that computes its longest border from its suffix. See
 * \cite{apostolico2000efficient} for algorithms. This class provides subclasses with a
 * $Substring$ object that represents its longest border: loading this object is necessary
 * for the procedures inside this class, so it's not an overhead.
 */
public class BorderSubstring extends MaximalRepeat {
	/**
	 * A representation of set $right_v=\{(a,a|v) : a \in \Sigma, a|v \neq 0\}$,
	 * sorted by increasing $a$, where $a|v$ is the length of the longest string $y$ in
	 * the stack such that $v=xay$ and $v=yz$, where $x,y,z$ are strings.
	 *
	 * Remark: such borders depend only on the characters that compose $v$, and do not
	 * depend on the left-extensions of $v$ in the text.
	 */
	protected int[] rightCharacters;
	protected long[] rightLengths;
	protected int nRight;  // Number of elements in $right_v$

	/**
	 * A representation of set $left_v=\{(a,v|a) : a \in \Sigma, v|a \neq 0\}$, sorted by
	 * increasing $a$, where $v|a$ is the length of the longest string $y$ in the stack
	 * such that $v=yax$, $v=zy$, \emph{and $va$ occurs in the text}, where $x,y,z$ are
	 * strings.
	 *
	 * Remark: such borders depend on the right-extensions of $v$ in the text, since we
	 * only need to compute the borders of minimal rare words that occur in the text. To
	 * compute the borders of \emph{minimal absent words}, we should make $left_v$
	 * store all characters $a$ such that $v|a \neq 0$, regardless of whether $va$ occurs
	 * in the text or not.
	 */
	protected int[] leftCharacters;
	protected long[] leftLengths;
	protected int nLeft;  // Number of elements in $left_v$

	/**
	 * Pointer to a $BorderSubstring$ representation of the longest border $y$ of $v$
	 */
	protected BorderSubstring longestBorder;
	protected long longestBorderLength, shortestPeriodLength;

	/**
	 * Maximum possible number of occurrences of $v$ in a string of length $textLength$,
	 * i.e. $\lceil (n-m+1)/p \rceil$, where $n=textLength$, $m=length$,
	 * $p=shortestPeriodLength$.
	 */
	protected long maxPossibleOccurrences;

	/**
	 * The character $d$ such that $v=xdy$, where $y$ is the longest border of $v$ and
	 * $x$ is a string.
	 */
	protected int longestBorderRightCharacter;

	/**
	 * The character $d$ such that $v=ydx$, where $y$ is the longest border of $v$ and
	 * $x$ is a string.
	 */
	protected int longestBorderLeftCharacter;

	/**
	 * Scratch space, allocated at most once.
	 */
	private BorderSubstring tmpString;


	/**
	 * Artificial no-argument constructor, used just to avoid compile-time errors.
	 * See the no-argument constructor of $Substring$ for details.
	 */
	protected BorderSubstring() { }


	public BorderSubstring(int alphabetLength, int log2alphabetLength, int bitsToEncodeAlphabetLength, long bwtLength, int log2BWTLength, int bitsToEncodeBWTLength) {
		super(alphabetLength,log2alphabetLength,bitsToEncodeAlphabetLength,bwtLength,log2BWTLength,bitsToEncodeBWTLength);
		rightCharacters = new int[alphabetLength];
		rightLengths = new long[alphabetLength];
		leftCharacters = new int[alphabetLength];
		leftLengths = new long[alphabetLength];
	}


	protected void clone(Substring other) {
		super.clone(other);
		int i;
		BorderSubstring bs = (BorderSubstring)other;
		bs.leftContext=leftContext;
		bs.isLeftExtensionOfMaximalRepeat=isLeftExtensionOfMaximalRepeat;
		bs.nRight=nRight;
		System.arraycopy(rightCharacters,0,bs.rightCharacters,0,nRight);
		System.arraycopy(rightLengths,0,bs.rightLengths,0,nRight);
		bs.nLeft=nLeft;
		System.arraycopy(leftCharacters,0,bs.leftCharacters,0,nLeft);
		System.arraycopy(leftLengths,0,bs.leftLengths,0,nLeft);
		bs.longestBorderLength=longestBorderLength;
		bs.shortestPeriodLength=shortestPeriodLength;
		bs.maxPossibleOccurrences=maxPossibleOccurrences;
		bs.longestBorderRightCharacter=longestBorderRightCharacter;
		bs.longestBorderLeftCharacter=longestBorderLeftCharacter;
	}


	protected void deallocate() {
		super.deallocate();
		rightCharacters=null;
		rightLengths=null;
		leftCharacters=null;
		leftLengths=null;
		if (tmpString!=null) {
			tmpString.deallocate();
			tmpString=null;
		}
		longestBorder=null;  // This is just a pointer: it doesn't need to be deallocated.
	}


	protected Substring getInstance() {
		return new BorderSubstring(alphabetLength,log2alphabetLength,bitsToEncodeAlphabetLength,bwtLength,log2BWTLength,bitsToEncodeBWTLength);
	}


	protected Substring getEpsilon(long[] C) {
		BorderSubstring out = (BorderSubstring)getInstance();

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
		out.nRight=0;
		out.nLeft=0;
		out.longestBorderLength=0;
		out.shortestPeriodLength=0;
		out.maxPossibleOccurrences=textLength+1;
		out.longestBorderRightCharacter=-1;
		out.longestBorderLeftCharacter=-1;

		return out;
	}


	public String toString() {
		String out = super.toString()+" | ";
		out+="longestBorderLength="+longestBorderLength+" shortestPeriodLength="+shortestPeriodLength+" maxPossibleOccurrences="+maxPossibleOccurrences+" ";
		out+="longestBorderRightCharacter="+longestBorderRightCharacter+" longestBorderLeftCharacter="+longestBorderLeftCharacter+" ";
		out+="nRight="+nRight+" ";
		out+="nLeft="+nLeft+" ";
		out+="rightArray: ";
		for (int i=0; i<nRight; i++) out+="("+rightCharacters[i]+","+rightLengths[i]+") ";
		out+="leftArray: ";
		for (int i=0; i<nLeft; i++) out+="("+leftCharacters[i]+","+leftLengths[i]+") ";
		return out;
	}



/*                            _____ _             _
                             /  ___| |           | |
                             \ `--.| |_ __ _  ___| | __
                              `--. \ __/ _` |/ __| |/ /
                             /\__/ / || (_| | (__|   <
                             \____/ \__\__,_|\___|_|\_\


HEAD' has the following format:
1. isLeftExtensionOfMaximalRepeat
2. longestBorderLength, if length>1.

APPENDIX has the following format:
1. nRight
2. nLeft
3. rightCharacters
4. rightLengths
5. leftCharacters
6. leftLengths
It is pushed in the stack only if longestBorderLength>0.
*/

	protected void pushHeadPrime(Stream stack, Substring[] cache) {
		super.pushHeadPrime(stack,cache);
		stack.push(isLeftExtensionOfMaximalRepeat?1:0,1);
		if (length>1) stack.push(longestBorderLength,log2length);
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|+|b|"+( 1+(length>1?log2length:0) ));
	}


	protected void readHeadPrime(Stream stack, Substring[] cache, boolean fast) {
		super.readHeadPrime(stack,cache,fast);
		isLeftExtensionOfMaximalRepeat=stack.read(1)==1?true:false;
		longestBorderLength=length>1?stack.read(log2length):0;

		shortestPeriodLength=length-longestBorderLength;
		maxPossibleOccurrences=length==0?textLength+1:(long)Math.ceil((textLength-length+1D)/shortestPeriodLength);
 		if (longestBorderLength==0) {
 			nRight=0;
 			nLeft=0;
 			longestBorderRightCharacter=-1;
 			longestBorderLeftCharacter=-1;
 		}
	}


	protected void popHeadPrime(Stream stack, Substring[] cache) {
		stack.pop( (length>1?log2length:0) +
				   1 );
		super.popHeadPrime(stack,cache);
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|-|b|"+( (length>1?log2length:0) +
				   										1 ));
	}


	/**
	 * Does not push right and left array to $stack$ if string $v$ is longer than
	 * $Constants.BORDER_THRESHOLD_2$: we assume that such arrays will never be used by a
	 * left-extension of $v$. Pushes right and left array to $cache$ rather than to
	 * $stack$ if string $v$ has length at most $Constants.BORDER_THRESHOLD_1$: we assume
	 * that such arrays will be highly accessed.
	 */
	protected void pushAppendix(Stream stack, Substring[] cache) {
		super.pushAppendix(stack,cache);
		if (longestBorderLength==0) return;

		if (length>Constants.BORDER_THRESHOLD_2) {
			// Large regime
			return;
		}
		boolean pushInStack = false;
		if (length>0 && length<=Constants.BORDER_THRESHOLD_1) {
			// Small regime
			if (cache.length>0) clone(cache[(int)length-1]);
			else pushInStack=true;
		}
		else {
			// Medium regime
			pushInStack=true;
		}
		if (pushInStack) {
			stack.push(nRight,bitsToEncodeAlphabetLength);
			stack.push(nLeft,bitsToEncodeAlphabetLength);
			int i;
			for (i=0; i<nRight; i++) stack.push(rightCharacters[i],log2alphabetLength);
			for (i=0; i<nRight; i++) stack.push(rightLengths[i],log2length);
			for (i=0; i<nLeft; i++) stack.push(leftCharacters[i],log2alphabetLength);
			for (i=0; i<nLeft; i++) stack.push(leftLengths[i],log2length);
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|+|b|"+( bitsToEncodeAlphabetLength*2+(nRight+nLeft)*(log2alphabetLength+log2length) ));
		}
	}


	/**
	 * @param fast skips reading $rightCharacters$, $rightLengths$, $leftCharacters$,
	 * $leftLengths$.
	 */
	protected void readAppendix(Stream stack, Substring[] cache, boolean fast) {
		super.readAppendix(stack,cache,fast);
		if (longestBorderLength==0) return;

		if (length>Constants.BORDER_THRESHOLD_2) {
			// Large regime
			return;
		}
		boolean readFromStack = false;
		if (length>0 && length<=Constants.BORDER_THRESHOLD_1) {
			// Small regime
			if (cache!=null && cache.length>0) {
				BorderSubstring bs = (BorderSubstring)cache[(int)length-1];
				int i;
				nRight=bs.nRight;  // $nRight>0$ necessarily at this point
				System.arraycopy(bs.rightCharacters,0,rightCharacters,0,nRight);
				System.arraycopy(bs.rightLengths,0,rightLengths,0,nRight);
				nLeft=bs.nLeft;  // $nLeft>0$ necessarily at this point
				System.arraycopy(bs.leftCharacters,0,leftCharacters,0,nLeft);
				System.arraycopy(bs.leftLengths,0,leftLengths,0,nLeft);
			}
			else readFromStack=true;
		}
		else {
			// Medium regime
			readFromStack=true;
		}
		if (readFromStack) {
			nRight=(int)stack.read(bitsToEncodeAlphabetLength);
			nLeft=(int)stack.read(bitsToEncodeAlphabetLength);
			if (fast) stack.setPosition( stack.getPosition()+
										 nRight*log2alphabetLength+
										 nRight*log2length+
										 nLeft*log2alphabetLength+
										 nLeft*log2length);
			else {
				int i;
				for (i=0; i<nRight; i++) rightCharacters[i]=(int)stack.read(log2alphabetLength);
				for (i=0; i<nRight; i++) rightLengths[i]=(int)stack.read(log2length);
				for (i=0; i<nLeft; i++) leftCharacters[i]=(int)stack.read(log2alphabetLength);
				for (i=0; i<nLeft; i++) leftLengths[i]=(int)stack.read(log2length);
			}
		}
	}


	protected void popAppendix(Stream stack, Substring[] cache) {
		if (longestBorderLength>0) {
			if (length>Constants.BORDER_THRESHOLD_2) {
				// Large regime
				return;
			}
			boolean popFromStack = false;
			if (length>0 && length<=Constants.BORDER_THRESHOLD_1) {
				// Small regime
				if (cache.length==0) popFromStack=true;
			}
			else {
				// Medium regime
				popFromStack=true;
			}
			if (popFromStack) {
				stack.pop( bitsToEncodeAlphabetLength+
						   bitsToEncodeAlphabetLength+
						   nRight*log2alphabetLength+
						   nRight*log2length+
						   nLeft*log2alphabetLength+
						   nLeft*log2length );
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|-|b|"+(bitsToEncodeAlphabetLength+
													   bitsToEncodeAlphabetLength+
													   nRight*log2alphabetLength+
													   nRight*log2length+
													   nLeft*log2alphabetLength+
													   nLeft*log2length));
			}
		}
		super.popAppendix(stack,cache);
	}



/*                       ______               _
                         | ___ \             | |
                         | |_/ / ___  _ __ __| | ___ _ __ ___
                         | ___ \/ _ \| '__/ _` |/ _ \ '__/ __|
                         | |_/ / (_) | | | (_| |  __/ |  \__ \
                         \____/ \___/|_|  \__,_|\___|_|  |___/                          */

	/**
	 * Computes $longestBorderLength$ from $suf(v)$
	 */
	protected void initAfterExtending(Substring suffix, int firstCharacter, RigidStream characterStack, int[] buffer) {
		super.initAfterExtending(suffix,firstCharacter,characterStack,buffer);
		leftContext=-1;
		isLeftExtensionOfMaximalRepeat=false;
		nRight=0;
		nLeft=0;
		longestBorderLength=0;
		if (length>1 && firstCharacter!=-1 && rightContext>1) {  // We don't compute borders for left-extensions that are not right-maximal
			int pos = buffer[firstCharacter];
			if (pos>=0) longestBorderLength=((BorderSubstring)suffix).rightLengths[pos]+1;
			else {
				int lastCharacter;
				if (length==2) lastCharacter=suffix.firstCharacter;
				else lastCharacter = (int)(characterStack.getElementAt(0));
				if (lastCharacter==firstCharacter) longestBorderLength=1;
			}
		}
		shortestPeriodLength=length-longestBorderLength;
		maxPossibleOccurrences=length==0?textLength+1:(long)Math.ceil((textLength-length+1D)/shortestPeriodLength);
		longestBorderRightCharacter=-1;
		longestBorderLeftCharacter=-1;
	}


	/**
	 * Builds right and left arrays from $longestBorderLength$
	 */
	protected void initAfterReading(Stream stack, RigidStream characterStack, SimpleStream pointerStack, Substring[] cache) {
		super.initAfterReading(stack,characterStack,pointerStack,cache);
		nRight=0;
		nLeft=0;
		longestBorderRightCharacter=-1;
		longestBorderLeftCharacter=-1;
		if (longestBorderLength==0) return;

		loadLongestBorder(stack,pointerStack,cache);
		buildRightArray(characterStack);
		if (isLeftExtensionOfMaximalRepeat) {
			// The longest border of $aw$, where $w$ is a maximal repeat, is itself a
			// string $az$ where $z$ is a maximal repeat. Thus, we only need to push on
			// the stack the left array of left-extensions of maximal repeats.
			buildLeftArrayOfRightExtensions(characterStack);
		}
	}


	/**
	 * Loads a $BorderSubstring$ representation of the longest border of $v$, using either
	 * $stack$ or $cache$.
	 */
	private final void loadLongestBorder(Stream stack, SimpleStream pointerStack, Substring[] cache) {
		if (longestBorderLength<=Constants.BORDER_THRESHOLD_1) longestBorder=(BorderSubstring)cache[(int)longestBorderLength-1];
		else {
			long backupPointer = stack.getPosition();
			stack.setPosition(pointerStack.getElementAt(longestBorderLength-1));
			if (tmpString==null) tmpString=(BorderSubstring)getInstance();  // Executed at most once
			tmpString.read(stack,cache,false,false,false);
if (Constants.TRACK_HITS) System.out.println(tmpString.length);
			longestBorder=tmpString;
			stack.setPosition(backupPointer);
		}
	}


	/**
	 * Builds the right array of $v$ from the right array of the longest border of $v$.
	 * The running time of this procedure is linear in the length of the right array of
	 * the longest border of $v$, and it does not depend on $alphabetLength$: this makes
	 * the sum of the building times of all right-maximal substrings of a text $s$
	 * linear in the length of $s$.
	 */
	private final void buildRightArray(RigidStream characterStack) {
		longestBorderRightCharacter=(int)characterStack.getElementAt(longestBorderLength);
		if (longestBorder.nRight==0) {
			nRight=1;
			rightCharacters[0]=longestBorderRightCharacter;
			rightLengths[0]=longestBorderLength;
			return;
		}
		int k = Arrays.binarySearch(longestBorder.rightCharacters,0,longestBorder.nRight,longestBorderRightCharacter);
		if (k>=0) {
			nRight=longestBorder.nRight;
			System.arraycopy(longestBorder.rightCharacters,0,rightCharacters,0,nRight);
			System.arraycopy(longestBorder.rightLengths,0,rightLengths,0,nRight);
			rightLengths[k]=longestBorderLength;
		}
		else {
			nRight=longestBorder.nRight+1;
			k=-k-1;
			System.arraycopy(longestBorder.rightCharacters,0,rightCharacters,0,k);
			rightCharacters[k]=longestBorderRightCharacter;
			System.arraycopy(longestBorder.rightCharacters,k,rightCharacters,k+1,nRight-k-1);
			System.arraycopy(longestBorder.rightLengths,0,rightLengths,0,k);
			rightLengths[k]=longestBorderLength;
			System.arraycopy(longestBorder.rightLengths,k,rightLengths,k+1,nRight-k-1);
		}
	}


	/**
	 * Builds the left array of $v$ by copying the entire left array of the longest border
	 * of $v$. Contrary to $buildRightArray$, the sum of the building times of all
	 * right-maximal substrings of a text $s$ is not necessarily linear in the length of
	 * $s$.
	 */
	private final void buildLeftArray(RigidStream characterStack) {
		longestBorderLeftCharacter=(int)(characterStack.getElementAt(length-longestBorderLength-1));
		if (longestBorder.nLeft==0) {
			nLeft=1;
			leftCharacters[0]=longestBorderLeftCharacter;
			leftLengths[0]=longestBorderLength;
			return;
		}
		int k = Arrays.binarySearch(longestBorder.leftCharacters,0,longestBorder.nLeft,longestBorderLeftCharacter);
		if (k>=0) {
			nLeft=longestBorder.nLeft;
			System.arraycopy(longestBorder.leftCharacters,0,leftCharacters,0,nLeft);
			System.arraycopy(longestBorder.leftLengths,0,leftLengths,0,nLeft);
			leftLengths[k]=longestBorderLength;
		}
		else {
			nLeft=longestBorder.nLeft+1;
			k=-k-1;
			System.arraycopy(longestBorder.leftCharacters,0,leftCharacters,0,k);
			leftCharacters[k]=longestBorderLeftCharacter;
			System.arraycopy(longestBorder.leftCharacters,k,leftCharacters,k+1,nLeft-k-1);
			System.arraycopy(longestBorder.leftLengths,0,leftLengths,0,k);
			leftLengths[k]=longestBorderLength;
			System.arraycopy(longestBorder.leftLengths,k,leftLengths,k+1,nLeft-k-1);
		}
	}


	/**
	 * Builds the left array of $v$ \emph{limited to the right-extensions of $v$}, from
	 * the left array of the longest border of $v$. The sum of the building times of all
	 * right-maximal substrings of a text $s$ is $O(|s|\log\{\sigma})$. Guaranteeing
	 * linear time is easy using $\sigma$ stacks.
	 */
	private final void buildLeftArrayOfRightExtensions(RigidStream characterStack) {
		int c, k;
		longestBorderLeftCharacter=(int)(characterStack.getElementAt(length-longestBorderLength-1));
		nLeft=0;
		for (c=1; c<longestBorderLeftCharacter+1; c++) {
			if (bwtIntervals[c][1]-bwtIntervals[c][0]<0) continue;
			k=Arrays.binarySearch(longestBorder.leftCharacters,0,longestBorder.nLeft,c-1);
			if (k>=0) {
				leftCharacters[nLeft]=longestBorder.leftCharacters[k];
				leftLengths[nLeft]=longestBorder.leftLengths[k];
				nLeft++;
			}
		}
		if (bwtIntervals[longestBorderLeftCharacter+1][1]-bwtIntervals[longestBorderLeftCharacter+1][0]>=0) {
			leftCharacters[nLeft]=longestBorderLeftCharacter;
			leftLengths[nLeft]=longestBorderLength;
			nLeft++;
		}
		for (c=longestBorderLeftCharacter+2; c<=alphabetLength; c++) {
			if (bwtIntervals[c][1]-bwtIntervals[c][0]<0) continue;
			k=Arrays.binarySearch(longestBorder.leftCharacters,0,longestBorder.nLeft,c-1);
			if (k>=0) {
				leftCharacters[nLeft]=longestBorder.leftCharacters[k];
				leftLengths[nLeft]=longestBorder.leftLengths[k];
				nLeft++;
			}
		}
	}


	/**
	 * $buffer$ is used to map a character of the alphabet $[0..\alphabetLength-1]$ to its
	 * position in $rightCharacters$ (if $right=TRUE$) or in $leftCharacters$ (if
	 * $right=FALSE$).
	 */
	protected void fillBuffer(int[] buffer, boolean right) {
		if (right) {
			for (int i=0; i<nRight; i++) buffer[rightCharacters[i]]=i;
		}
		else {
			for (int i=0; i<nLeft; i++) buffer[leftCharacters[i]]=i;
		}
	}


	protected void emptyBuffer(int[] buffer, boolean right) {
		if (right) {
			for (int i=0; i<nRight; i++) buffer[rightCharacters[i]]=-1;
		}
		else {
			for (int i=0; i<nLeft; i++) buffer[leftCharacters[i]]=-1;
		}
	}

}





























/*
    protected void readFast2(Stream stack) {
		super.readFast2(stack);
		rightLength=(int)stack.read(log2alphabetLength);
		leftLength=(int)stack.read(log2alphabetLength);
		skipBorderSubstring(stack);
	}
	protected long serializedSize() {
		return super.serializedSize()+
			   log2alphabetLength+
			   log2alphabetLength+
			   log2alphabetLength+
			   alphabetLength*log2alphabetLength*2;
	}
*/
/*
protected void skip(Stream stack) {
		super.skip(stack);
		rightLength=(int)stack.read(log2alphabetLength);
		leftLength=(int)stack.read(log2alphabetLength);
		skipBorderSubstring(stack);
	}
*/