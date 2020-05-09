/**
 * Representation of a substring $v$ of a text $s$ as understood by $SubstringIterator$.
 * For $SubstringIterator$, a substring $v$ of $s$ has the following features:
 *
 * (1) A set of substring intervals in $BWT_s$ (for example the interval of $v$). Such
 * intervals are maintained by $SubstringIterator.extendLeft$. $Substring$ fixes the
 * maximum number of intervals, the intervals of $\epsilon$, and whether the starting and
 * ending positions of the intervals are stored in sorted order. Different instances of
 * the same $Substring$ class could have a different number of intervals.
 *
 * (2) An additional set of variables, unknown to $SubstringIterator$, that depend only on
 * $suf(v)$, i.e. on the string that satisfies $v = a \cdot suf(v), a \in \Sigma$. It is
 * responsibility of $v$ to update these variables given $suf(v)$, inside method
 * $initAfterExtending$.
 *
 * (3) The ability to be pushed to and popped from the stack of $SubstringIterator$.
 *
 * (4) The ability to receive a number of signals from $SubstringIterator$.
 *
 * Remark: This object and its subclasses are designed to be employed as reusable data
 * containers, i.e. just as facilities to load data from a bit stream, manipulate it, and
 * push it back to the stream, similar to JavaBeans. A typical program needs only a
 * limited number of instances of this object at any given time, it allocates the memory
 * for each such instance exactly once (inside its constructor), and such memory is the
 * largest possible to accommodate any instance in the program.
 */
public class Substring {

	protected final int MAX_BITS_PER_POINTER = 64;  // Maximum number of bits to encode a stack pointer in $serialized(v)$
	protected final int MAX_BITS_PER_LENGTH = 64;  // Maximum number of bits to encode a substring length in $serialized(v)$
	protected int MAX_INTERVALS;  // Maximum number of rows in $bwtIntervals$. To be set by each descendant class.
	protected int BITS_TO_ENCODE_MAX_INTERVALS;
	protected boolean BWT_INTERVALS_ARE_SORTED;  // TRUE iff the sequence $bwtIntervals[0][0],bwtIntervals[0][1],bwtIntervals[1][0],bwtIntervals[0][1],...$ is increasing. Avoids one sorting operation in $extendLeft$. To be set by each descendant class.
	protected int alphabetLength, log2alphabetLength, bitsToEncodeAlphabetLength;
	protected int nIntervals;  // Number of rows in $bwtIntervals$
	protected long bwtLength, textLength;
	protected int log2BWTLength, bitsToEncodeBWTLength;
	protected double oneOverLogTextLength;

	/**
	 * Intervals of substrings (possibly different from $v$) in $BWT_s$, used to implement
	 * functions on $v$. This base class uses just $bwtIntervals[0]=(i_v,j_v)_s$.
	 */
	protected long[][] bwtIntervals;

	/**
	 * Index of the first bit of $serialized(v)$ in the stack
	 */
	protected long address;
	protected int log2address;  // $Utils.log2(address)$

	/**
	 * Index of the first bit of the previous serialized substring in the stack
	 */
	protected long previousAddress;

	/**
	 * $|v|$
	 */
	protected long length;
	protected int log2length, bitsToEncodeLength;

	/**
	 * The first character of $v$
	 */
	protected int firstCharacter;

	/**
	 * TRUE iff $a \cdot v$ has been already visited by $SubstringIterator$, for all
	 * $a \in \Sigma$.
	 */
	protected boolean hasBeenExtended;

	/**
	 * TRUE iff $v$ has been stolen by a $SubstringIteratorThread$
	 */
	protected boolean hasBeenStolen;


	/**
	 * Artificial no-argument constructor, used just to avoid compile-time errors.
	 * Every subclass of $Substring$ must provide a full reimplementation of the
	 * constructor with arguments.
	 */
	protected Substring() { }


	/**
	 * Initializes the container
	 *
	 * @param bwtLength $|s|+1$, where $s$ is the input text.
	 */
	protected Substring(int alphabetLength, int log2alphabetLength, int bitsToEncodeAlphabetLength, long bwtLength, int log2BWTLength, int bitsToEncodeBWTLength) {
		this.alphabetLength=alphabetLength;
		this.log2alphabetLength=log2alphabetLength;
		this.bitsToEncodeAlphabetLength=bitsToEncodeAlphabetLength;
		this.bwtLength=bwtLength;
		this.log2BWTLength=log2BWTLength;
		this.bitsToEncodeBWTLength=bitsToEncodeBWTLength;
		textLength=bwtLength-1;
		oneOverLogTextLength=1D/Math.log(textLength);
		MAX_INTERVALS=1;
		BITS_TO_ENCODE_MAX_INTERVALS=Utils.bitsToEncode(MAX_INTERVALS);
		BWT_INTERVALS_ARE_SORTED=true;
		bwtIntervals = new long[MAX_INTERVALS][2];
	}


	protected void deallocate() {
		for (int i=0; i<nIntervals; i++) bwtIntervals[i]=null;
		bwtIntervals=null;
	}


	/**
	 * Set the state of $other$ to be identical to the state of $this$
	 */
	protected void clone(Substring other) {
		other.nIntervals=nIntervals;
		for (int i=0; i<nIntervals; i++) {
			other.bwtIntervals[i][0]=bwtIntervals[i][0];
			other.bwtIntervals[i][1]=bwtIntervals[i][1];
		}
		other.address=-1;  // Cloning the address of this string is potentially wrong in multithreading
		other.log2address=-1;
		other.previousAddress=-1;
		other.length=length;
		other.log2length=log2length;
		other.bitsToEncodeLength=bitsToEncodeLength;
		other.firstCharacter=firstCharacter;
		other.hasBeenExtended=hasBeenExtended;
		other.hasBeenStolen=hasBeenStolen;
	}


	public boolean equals(Object other) {
		int i;
		Substring otherSubstring = (Substring)other;
		if (nIntervals!=otherSubstring.nIntervals) return false;
		for (i=0; i<nIntervals; i++) {
			if (bwtIntervals[i][0]!=otherSubstring.bwtIntervals[i][0] || bwtIntervals[i][1]!=otherSubstring.bwtIntervals[i][1]) return false;
		}
		// We don't compare $address$ and $previousAddress$, since they are not portable.
		if (length!=otherSubstring.length) return false;
		if (firstCharacter!=otherSubstring.firstCharacter) return false;
		// We don't compare $hasBeenExtended$ and $hasBeenStolen$, since they are
		// temporary flags.
		return true;
	}


	/**
	 * Factory of new $Substring$ objects and of $Substring$'s subclasses. Used to
	 * implement basic polymorphism in $SubstringIterator$ without using the
	 * $java.lang.reflect$ apparatus.
	 */
	protected Substring getInstance() {
		return new Substring(alphabetLength,log2alphabetLength,bitsToEncodeAlphabetLength,bwtLength,log2BWTLength,bitsToEncodeBWTLength);
	}


	/**
	 * Returns a representation of the empty string, which is pushed on the stack first.
	 *
	 * @param C the $C$ array of backward search, assumed to exclude $#$.
	 */
	protected Substring getEpsilon(long[] C) {
		Substring out = getInstance();
		out.nIntervals=1;
		out.bwtIntervals[0][0]=0;
		out.bwtIntervals[0][1]=bwtLength-1;
		out.address=-1;
		out.log2address=-1;
		out.previousAddress=-1;
		out.length=0;
		out.log2length=-1;
		out.bitsToEncodeLength=1;
		out.firstCharacter=-1;
		out.hasBeenExtended=false;
		out.hasBeenStolen=false;
		return out;
	}


	/**
	 * Pushes to $sequence$ the sequence of characters of $substring$, in left-to-right
	 * order, which is assumed to be stored in $characterStack$ in right-to-left order.
	 * $substring$ is assumed to have already been read from the stack; $sequence$ is
	 * assumed to be large enough to contain $substring.length$ elements.
	 *
	 * @return TRUE iff $substring.firstCharacter==-1$. This character is not appended to
	 * $sequence$.
	 */
	public final boolean getSequence(RigidStream characterStack, IntArray sequence) {
		boolean out = false;
		sequence.clear();
		if (length==0) return false;
		if (firstCharacter==-1) out=true;
		else sequence.push(firstCharacter);
		for (long i=length-2; i>=0; i--) sequence.push(characterStack.getElementAt(i));
		return out;
	}


	public String toString() {
		String out = "["+(hasBeenExtended?"*":"")+(hasBeenStolen?"o":"")+"] address="+address+
				     " previousAddress="+previousAddress+
				     " length="+length+
				     " firstCharacter="+firstCharacter+
				     " nIntervals="+nIntervals+
				     " intervals: ";
		for (int i=0; i<nIntervals; i++) out+="["+bwtIntervals[i][0]+".."+bwtIntervals[i][1]+"] ";
		return out;
	}


	/**
	 * Initializes string $v$ from $suf(v)$ and from the current setting of $bwtIntervals$
	 * immediately after $v$ has been created by left-extending $suf(v)$.
	 *
	 * @param firstCharacter first character of $v$; -1 indicates $#$;
	 * @param buffer reusable memory area in which $suffix$ has stored additional
	 * information for the initialization of $v$. We assume
	 * $buffer.length>=alphabetLength+1$.
	 */
	protected void initAfterExtending(Substring suffix, int firstCharacter, RigidStream characterStack, int[] buffer) {
		address=-1;
		log2address=-1;
		previousAddress=-1;
		length=suffix.length+1;
		log2length=length==0?-1:Utils.log2(length);
		bitsToEncodeLength=length==0?1:Utils.bitsToEncode(length);
		this.firstCharacter=firstCharacter;
		hasBeenExtended=false;
		hasBeenStolen=false;
	}


	/**
	 * Initializes string $v$ immediately after it is loaded from the stack.
	 */
	protected void initAfterReading(Stream stack, RigidStream characterStack, SimpleStream pointerStack, Substring[] cache) { }


	/**
	 * Fills $buffer$ with messages for initializing the left extensions of $v$.
	 * The procedure assumes that $buffer$ is empty before invocation, i.e. that
	 * $buffer[i]=-1$ for all $i \in [0..buffer.length]$, and that
	 * $buffer.length>=alphabetLength+1$.
	 */
	protected void fillBuffer(int[] buffer, boolean flag) { }


	/**
	 * Returns $buffer$ to the empty state by undoing what has been done by $fillBuffer$.
	 */
	protected void emptyBuffer(int[] buffer, boolean flag) { }


	/**
	 * @return true iff the left-extensions of $v$ should be explored by
	 * $SubstringIterator$. Invoked immediately after $v$ has been created by
	 * left-extending $suf(v)$.
	 */
	protected boolean shouldBeExtendedLeft() {
		return firstCharacter>-1;  // Not extending to the left substrings that start by $#$
	}


	/**
	 * Signal produced by $SubstringIterator$ after it has initialized $v$ and after it
	 * has extended it to the left. This signal is launched only for strings that have
	 * been pushed to the stack, i.e. only for strings such that $shouldBeExtendedLeft$ is
	 * true.
	 */
	protected void visited(Stream stack, RigidStream characterStack, SimpleStream pointerStack, Substring[] cache, Substring[] leftExtensions) { }


	/**
	 * @return the number of occurrences of $v$ in $s$
	 */
	protected long frequency() {
		// We assume that the rest of the code is correct and that $bwtIntervals[0][0]$
		// and $bwtIntervals[0][1]$ are valid.
		return bwtIntervals[0][1]>=bwtIntervals[0][0]?bwtIntervals[0][1]-bwtIntervals[0][0]+1:0;
	}



/*                            _____ _             _
                             /  ___| |           | |
                             \ `--.| |_ __ _  ___| | __
                              `--. \ __/ _` |/ __| |/ /
                             /\__/ / || (_| | (__|   <
                             \____/ \__\__,_|\___|_|\_\

Format of a string that has not been extended:  HEAD | HEAD' || TAIL | TAIL'
Format of a string that has been extended:      HEAD | HEAD' || APPENDIX

HEAD is a header that is common both to substrings that have been extended and to
substrings that have not been extended. It contains the following fields:
1. previousAddress
2. hasBeenExtended
3. hasBeenStolen
4. length

TAIL is stored only for strings that have not been extended, and it is popped out when a
string is extended. It contains the following fields:
1. firstCharacter
2. nIntervals
3. bwtIntervals[0..nIntervals-1]

APPENDIX is stored only for strings that have been extended, it is pushed when a string is
extended, and it is never popped out in isolation. Its fields are defined by subclasses of
$Substring$.

HEAD' and TAIL' are homologous regions defined by subclasses of $Substring$.
*/

	protected final void push(Stream stack, Substring[] cache) {
		pushHead(stack,cache);
		pushHeadPrime(stack,cache);
		if (hasBeenExtended) pushAppendix(stack,cache);
		else {
			pushTail(stack,cache);
			pushTailPrime(stack,cache);
		}
	}


	/**
	 * Pushes just the appendix of a string, assuming that HEAD and HEAD' are at the top
	 * of $stack$.
	 */
	protected void pushAppendix(Stream stack, Substring[] cache) { }


	/**
	 * Remark: this procedure overwrites $address$
	 */
	private final void pushHead(Stream stack, Substring[] cache) {
		address=stack.nBits();
		log2address=address==0?MAX_BITS_PER_POINTER:Utils.log2(address);
		stack.push(previousAddress,log2address);
		stack.push(hasBeenExtended?1:0,1);
		stack.push(hasBeenStolen?1:0,1);
		stack.push(length,log2BWTLength);
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|+|i|"+(log2address+1+1+log2BWTLength));
	}


	protected void pushHeadPrime(Stream stack, Substring[] cache) { }


	private final void pushTail(Stream stack, Substring[] cache) {
		stack.push(firstCharacter,log2alphabetLength);
		stack.push(nIntervals,BITS_TO_ENCODE_MAX_INTERVALS);
		for (int i=0; i<nIntervals; i++) {
			stack.push(bwtIntervals[i][0],bitsToEncodeBWTLength);
			stack.push(bwtIntervals[i][1],bitsToEncodeBWTLength);
		}
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|+|i|"+(log2alphabetLength+BITS_TO_ENCODE_MAX_INTERVALS+nIntervals*2*bitsToEncodeBWTLength));
	}


	protected void pushTailPrime(Stream stack, Substring[] cache) { }


	/**
	 * Reads $v$ from $stack$ starting from $stack.getPosition()$. At the end of the
	 * process, the pointer of $stack$ is located at the first bit that follows
	 * $serialized(v)$. The procedure assumes that the pointer of $stack$ is indeed
	 * positioned at the beginning of $serialized(v)$: no explicit check is performed.
	 *
	 * @param fastHead skips information in HEAD';
	 * @param fastTail skips information in TAIL and TAIL' if $hasBeenStolen=TRUE$;
	 * @param fastAppendix skips information in APPENDIX.
	 */
	protected /*final*/ void read(Stream stack, Substring[] cache, boolean fastHead, boolean fastTail, boolean fastAppendix) {
		readHead(stack,cache);
		readHeadPrime(stack,cache,fastHead);
		firstCharacter=-1;
		nIntervals=0;
		if (hasBeenExtended) {
			readAppendix(stack,cache,fastAppendix);
		}
		else {
			readTail(stack,cache,fastTail);
			readTailPrime(stack,cache,fastTail);
		}
	}


	private final void readHead(Stream stack, Substring[] cache) {
		address=stack.getPosition();
		log2address=address==0?MAX_BITS_PER_POINTER:Utils.log2(address);
		previousAddress=stack.read(log2address);
		hasBeenExtended=stack.read(1)==1?true:false;
		hasBeenStolen=stack.read(1)==1?true:false;
		length=stack.read(log2BWTLength);
		log2length=length==0?-1:Utils.log2(length);
		bitsToEncodeLength=length==0?1:Utils.bitsToEncode(length);
	}


	protected void readHeadPrime(Stream stack, Substring[] cache, boolean fast) { }


	/**
	 * @param fast skips $bwtIntervals$ if $hasBeenStolen=TRUE$.
	 */
	private final void readTail(Stream stack, Substring[] cache, boolean fast) {
		firstCharacter=(int)stack.read(log2alphabetLength);
		nIntervals=(int)stack.read(BITS_TO_ENCODE_MAX_INTERVALS);
		if (fast && hasBeenStolen) stack.setPosition( stack.getPosition()+
			                   						  nIntervals*bitsToEncodeBWTLength*2 );
		else {
			for (int i=0; i<nIntervals; i++) {
				bwtIntervals[i][0]=stack.read(bitsToEncodeBWTLength);
				bwtIntervals[i][1]=stack.read(bitsToEncodeBWTLength);
			}
		}
	}


	protected void readTailPrime(Stream stack, Substring[] cache, boolean fast) { }


	protected void readAppendix(Stream stack, Substring[] cache, boolean fast) { }


	/**
	 * Removes a substring from the top of the stack, assuming that $v$ has already been
	 * deserialized and that $serialized(v)$ is indeed at the top of $stack$.
	 */
	protected final void pop(Stream stack, Substring[] cache) {
		if (hasBeenExtended) popAppendix(stack,cache);
		else popTails(stack,cache);
		popHeadPrime(stack,cache);
		popHead(stack,cache);
	}


	protected void popAppendix(Stream stack, Substring[] cache) { }


	protected void popHeadPrime(Stream stack, Substring[] cache) { }


	private final void popHead(Stream stack, Substring[] cache) {
		stack.pop( log2BWTLength+
		           1+
		           1+
		           log2address );
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|-|i|"+(log2BWTLength+1+1+log2address));
	}


	/**
	 * Removes just TAIL and TAIL' of a nonextended substring
	 */
	protected final void popTails(Stream stack, Substring[] cache) {
		popTailPrime(stack,cache);
		stack.pop( nIntervals*bitsToEncodeBWTLength*2+
				   BITS_TO_ENCODE_MAX_INTERVALS+
		           log2alphabetLength );
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|-|i|"+( nIntervals*bitsToEncodeBWTLength*2+
													    BITS_TO_ENCODE_MAX_INTERVALS+
													    log2alphabetLength ));
	}


	protected void popTailPrime(Stream stack, Substring[] cache) { }


	/**
	 * Sets $hasBeenExtended=true$ in the serialized representation of $v$ in $stack$,
	 * but not in this object. The pointer of $stack$ is then restored to its initial
	 * state.
	 */
	protected final void markAsExtended(Stream stack) {
		long backupPointer = stack.getPosition();
		stack.setBit(address+log2address);
		stack.setPosition(backupPointer);
	}


	/**
	 * Sets $hasBeenStolen=true$ in the serialized representation of $v$ in $stack$,
	 * but not in this object. Then, the pointer of $stack$ is restored to its original
	 * state.
	 */
	protected final void markAsStolen(Stream stack) {
		long backupPointer = stack.getPosition();
		stack.setBit(address+log2address+1);
		stack.setPosition(backupPointer);
	}

}


















/**
	 * Same as $read$, but the procedure halts after reading the serialized substring up
	 * to $nIntervals$, and it leaves the stack position immediately after the end of the
	 * serialized substring.
	 */
/*	protected void readFast2(Stream stack) {
		address=stack.getPosition();
		log2address=address==0?MAX_BITS_PER_POINTER:Utils.bitsToEncode(address);
		previousAddress=stack.read(log2address);
		hasBeenExtended=stack.read(1)==1?true:false;
		hasBeenStolen=stack.read(1)==1?true:false;
		length=stack.read(bitsToEncodeBWTLength);
		firstCharacter=(int)stack.read(log2alphabetLength);
		nPointers=(int)stack.read(BITS_TO_ENCODE_MAX_POINTERS);
		nIntervals=(int)stack.read(BITS_TO_ENCODE_MAX_INTERVALS);
		stack.setPosition(stack.getPosition()+
						  (nPointers-MIN_POINTERS)*log2address+
						  nIntervals*bitsToEncodeBWTLength*2);
	}
*/


/**
	 * @return an \emph{upper bound} on the number of bits required to serialize any
	 * instance of this class.
	 */
/*	protected long serializedSize() {
		return MAX_BITS_PER_POINTER+
			   1+1+
			   bitsToEncodeBWTLength+
			   log2alphabetLength+
			   BITS_TO_ENCODE_MAX_POINTERS+
			   BITS_TO_ENCODE_MAX_INTERVALS+
			   (nPointers-MIN_POINTERS+1)*MAX_BITS_PER_POINTER+
			   (nIntervals<<1)*bitsToEncodeBWTLength;
	}
*/

/**
	 * Assume that the pointer in $stack$ is currently at the beginning of this substring
	 * $v$. The procedure advances the pointer to the beginning of the following substring
	 * while reading the minimum possible amount of information.
	 */
/*	protected void skip(Stream stack) {
		address=stack.getPosition();
		log2address=address==0?MAX_BITS_PER_POINTER:Utils.bitsToEncode(address);
		stack.setPosition(stack.getPosition()+
						  log2address+
						  1+1+
						  bitsToEncodeBWTLength+
						  log2alphabetLength);
		nPointers=(int)stack.read(BITS_TO_ENCODE_MAX_POINTERS);
		nIntervals=(int)stack.read(BITS_TO_ENCODE_MAX_INTERVALS);
		stack.setPosition(stack.getPosition()+
						  (nPointers-MIN_POINTERS)*log2address+
						  nIntervals*bitsToEncodeBWTLength*2);
	}
*/

