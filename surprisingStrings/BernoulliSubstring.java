import java.util.Arrays;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;

/**
 * A right-maximal substring that can compute the exact expectation and variance of the
 * number of its occurrences in a string generated by a given IID source, as well as
 * scores of statistical surprise, using its longest border.
 * See \cite{apostolico2000efficient} for algorithms, and \cite{apostolico2003monotony}
 * for statistics.
 */
public class BernoulliSubstring extends BorderSubstring {
	/**
	 * Number of scores computed by $getScores$
	 */
	protected static final int N_SCORES = 10;

	/**
	 * $\bar{p}$, $\bar{p}^2$ and $\log_{e}(\bar{p})$, where
	 * $\bar{p} = \prod_{i=0}^{|v|-1}\mathbb{P}(v[i])$.
	 */
	protected double barP, barPSquare, logBarP;

	/**
	 * $f(v) = \sum_{b \in borders(v)}(|s|-2|v|+b+1)\prod_{i=b}^{|v|-1}\mathbb{P}(v[i])$,
	 * where $s$ is the text and $borders(v)$ is the set of all border lengths of $v$.
	 */
	protected double f;

	/**
	 * $g(v) = \sum_{b \in borders(v)}\prod_{i=b}^{|v|-1}\mathbb{P}(v[i])$
	 */
	protected double g;

	/**
	 * $f(va)$ and $g(va)$ for all characters $a$ in $leftCharacters$.
	 */
	protected double[] leftF, leftG;

	/**
	 * Temporary scratch space, allocated at most once.
	 */
	private BernoulliSubstring tmpString1, tmpString2;
	protected double[] out, outPrime;  // Stores the output of $getScores$ and $getExpectationAndVariance$


	/**
	 * Artificial no-argument constructor, used just to avoid compile-time errors.
	 * See the no-argument constructor of $Substring$ for details.
	 */
	protected BernoulliSubstring() { }


	public BernoulliSubstring(int alphabetLength, int log2alphabetLength, int bitsToEncodeAlphabetLength, long bwtLength, int log2BWTLength, int bitsToEncodeBWTLength) {
		super(alphabetLength,log2alphabetLength,bitsToEncodeAlphabetLength,bwtLength,log2BWTLength,bitsToEncodeBWTLength);
		leftF = new double[alphabetLength];
		leftG = new double[alphabetLength];
		out = new double[N_SCORES];
		outPrime = new double[N_SCORES];
	}


	protected void clone(Substring other) {
		super.clone(other);
		BernoulliSubstring bs = (BernoulliSubstring)other;
		bs.barP=barP;
		bs.barPSquare=barPSquare;
		bs.logBarP=logBarP;
		bs.f=f;
		bs.g=g;
		if (nLeft>0) {
			System.arraycopy(leftF,0,bs.leftF,0,nLeft);
			System.arraycopy(leftG,0,bs.leftG,0,nLeft);
		}
	}


	protected void deallocate() {
		super.deallocate();
		leftF=null;
		leftG=null;
		if (tmpString1!=null) {
			tmpString1.deallocate();
			tmpString1=null;
		}
		if (tmpString2!=null) {
			tmpString2.deallocate();
			tmpString2=null;
		}
		out=null;
	}


	protected Substring getInstance() {
		return new BernoulliSubstring(alphabetLength,log2alphabetLength,bitsToEncodeAlphabetLength,bwtLength,log2BWTLength,bitsToEncodeBWTLength);
	}


	/**
	 * Size of $\epsilon$ after having being extended: about 198 bits.
	 *
	 * // Substring
	 * MAX_BITS_PER_POINTER+
	 * 2+
	 * log2BWTLength+
	 * // BorderSubstring
	 * 1+
	 * // BernoulliSubstring
	 * 64+
	 * bitsToEncodeAlphabetLength
	 */
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
		barP=1;
		barPSquare=1;
		logBarP=0;
		f=0;
		g=0;

		return out;
	}


	public String toString() {
		String out = super.toString()+" | ";
		int i;
		out+="barP="+barP+" f="+f+" g="+g+" ";
		out+="leftF: ";
		for (i=0; i<nLeft; i++) out+=leftF[i]+" ";
		out+="leftG: ";
		for (i=0; i<nLeft; i++) out+=leftG[i]+" ";
		return out;
	}



/*                            _____ _             _
                             /  ___| |           | |
                             \ `--.| |_ __ _  ___| | __
                              `--. \ __/ _` |/ __| |/ /
                             /\__/ / || (_| | (__|   <
                             \____/ \__\__,_|\___|_|\_\

HEAD' has the following format:
1. logBarP

APPENDIX has the following format:
1. leftContext
2. f, if $v$ is a maximal repeat.
3. g, if $v$ is a maximal repeat.
4. firstCharacter, if $isLeftExtensionOfMaximalRepeat$.
5. leftF, if $isLeftExtensionOfMaximalRepeat$.
6. leftG, if $isLeftExtensionOfMaximalRepeat$.
*/

	protected void pushHeadPrime(Stream stack, Substring[] cache) {
		super.pushHeadPrime(stack,cache);
		stack.push(Double.doubleToLongBits(logBarP),64);
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|+|s|"+64);
	}


	protected void readHeadPrime(Stream stack, Substring[] cache, boolean fast) {
		super.readHeadPrime(stack,cache,fast);
		logBarP=Double.longBitsToDouble(stack.read(64));
		barP=Math.exp(logBarP);
		barPSquare=barP*barP;
	}


	protected void popHeadPrime(Stream stack, Substring[] cache) {
		stack.pop(64);
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|-|s|"+64);
		super.popHeadPrime(stack,cache);
	}


	protected final void pushAppendix(Stream stack, Substring[] cache) {
		super.pushAppendix(stack,cache);

		stack.push(leftContext,bitsToEncodeAlphabetLength);
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|+|s|"+(bitsToEncodeAlphabetLength));
		if (leftContext>1) {
			// To compute $f$ and $g$ for a maximal repeat, we need $f$ and $g$ for its
			// longest border, which is itself a maximal repeat. We can thus push on the
			// stack just the $f$ and $g$ of maximal repeats.
			stack.push(Double.doubleToLongBits(f),64);
			stack.push(Double.doubleToLongBits(g),64);
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|+|s|"+(64*2));
		}
		if (isLeftExtensionOfMaximalRepeat) {
			// To compute the arrays $leftF$ and $leftG$ of a left-extension $aw$ of a
			// maximal repeat $w$, we need the arrays $leftF$ and $leftG$ of its longest
			// border, which is itself the left-extension of a maximal repeat. Thus, we
			// only need to push on the stack the arrays $leftF$ and $leftG$ of
			// left-extensions of maximal repeats.
			stack.push(firstCharacter,log2alphabetLength);
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|+|s|"+log2alphabetLength);
			int i;
			for (i=0; i<nLeft; i++) stack.push(Double.doubleToLongBits(leftF[i]),64);
			for (i=0; i<nLeft; i++) stack.push(Double.doubleToLongBits(leftG[i]),64);
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|+|s|"+(nLeft*64*2));
		}
	}


	protected void readAppendix(Stream stack, Substring[] cache, boolean fast) {
		super.readAppendix(stack,cache,fast);

		leftContext=(int)stack.read(bitsToEncodeAlphabetLength);
		f=0; g=0;
		if (leftContext>1) {
			if (fast) stack.setPosition(stack.getPosition()+64*2);
			else {
				f=Double.longBitsToDouble(stack.read(64));
				g=Double.longBitsToDouble(stack.read(64));
			}
		}
		firstCharacter=-1;
		if (isLeftExtensionOfMaximalRepeat) {
			if (fast) stack.setPosition( stack.getPosition()+
										 log2alphabetLength+
										 64*2*nLeft );
			else {
				firstCharacter=(int)stack.read(log2alphabetLength);
				int i;
				for (i=0; i<nLeft; i++) leftF[i]=Double.longBitsToDouble(stack.read(64));
				for (i=0; i<nLeft; i++) leftG[i]=Double.longBitsToDouble(stack.read(64));
			}
		}
	}


	protected void popAppendix(Stream stack, Substring[] cache) {
		if (isLeftExtensionOfMaximalRepeat) {
			stack.pop(log2alphabetLength+64*2*nLeft);
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|-|s|"+( log2alphabetLength+64*2*nLeft ));
		}
		if (leftContext>1) {
			stack.pop(64*2);
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|-|s|"+( 64*2 ));
		}
		stack.pop(bitsToEncodeAlphabetLength);
if (Constants.TRACK_STACK) System.out.println(System.currentTimeMillis()+"|-|s|"+( bitsToEncodeAlphabetLength ));
		super.popAppendix(stack,cache);
	}



/*                 _   _            _
                  | | | |          (_)
                  | | | | __ _ _ __ _  __ _ _ __   ___ ___
                  | | | |/ _` | '__| |/ _` | '_ \ / __/ _ \
                  \ \_/ / (_| | |  | | (_| | | | | (_|  __/
                   \___/ \__,_|_|  |_|\__,_|_| |_|\___\___|                             */


	/**
	 * Computes $logBarP$ from $suffix$
	 */
	protected void initAfterExtending(Substring suffix, int firstCharacter, RigidStream characterStack, int[] buffer) {
		super.initAfterExtending(suffix,firstCharacter,characterStack,buffer);

		// $\bar{p}$
		if (firstCharacter!=-1) {
			logBarP=((BernoulliSubstring)suffix).logBarP+Constants.logProbabilities[firstCharacter];
			barP=Math.exp(logBarP);
			barPSquare=barP*barP;
		}
		else {
			logBarP=0;
			barP=1;
			barPSquare=1;
		}		
	}


	/**
	 * Computes $f$, $g$, $leftF$, $leftG$ from $longestBorderLength$.
	 */
	protected void initAfterReading(Stream stack, RigidStream characterStack, SimpleStream pointerStack, Substring[] cache) {
		super.initAfterReading(stack,characterStack,pointerStack,cache);

		// Computing $f(v)$ and $g(v)$ only for maximal repeats
		f=0; g=0;
		if (leftContext>1 && longestBorderLength>0) {
			long backupPointer;
			double x;
			BernoulliSubstring lb = (BernoulliSubstring)longestBorder;
			backupPointer=stack.getPosition();
			stack.setPosition(pointerStack.getElementAt(length-longestBorderLength-1));
			if (tmpString1==null) tmpString1=(BernoulliSubstring)getInstance();  // Executed at most once
			tmpString1.read(stack,cache,true,true,true);
if (Constants.TRACK_HITS) System.out.println(tmpString1.length);
			stack.setPosition(backupPointer);
			x=Math.exp(tmpString1.logBarP);
			f = x*( bwtLength-(length<<1)+longestBorderLength +
					lb.f-((length-longestBorderLength)<<1)*lb.g );
			g = x*(1+lb.g);
		}

		// Computing $leftF$ and $leftG$ only for strings $v=aw$ where $a$ is a character
		// and $w$ is a maximal repeat.
		if (isLeftExtensionOfMaximalRepeat) {
			int b, k;
			long backupPointer;
			double x, y, lbF, lbG;
			BernoulliSubstring B;
			for (int i=0; i<nLeft; i++) {
				b=leftCharacters[i];

				// Loading $B$, the longest border of $v=aw$ that is followed by $b$ as a
				// prefix.
				backupPointer=stack.getPosition();
				stack.setPosition(pointerStack.getElementAt(leftLengths[i]-1));
				if (tmpString1==null) tmpString1=(BernoulliSubstring)getInstance();  // Executed at most once
				tmpString1.read(stack,cache,true,true,true);
if (Constants.TRACK_HITS) System.out.println(tmpString1.length);
				stack.setPosition(backupPointer);
				B=tmpString1;

				// Loading $x = \prod_{z=|B|+1}^{|v|-1}\mathbb{P}[v[z]] \cdot \mathbb{P}[b]$
				backupPointer=stack.getPosition();
				y=0;
				if (length-leftLengths[i]-1>0) {
					stack.setPosition(pointerStack.getElementAt(length-leftLengths[i]-2));
					if (tmpString2==null) tmpString2=(BernoulliSubstring)getInstance();  // Executed at most once
					tmpString2.read(stack,cache,true,true,true);
if (Constants.TRACK_HITS) System.out.println(tmpString2.length);
					stack.setPosition(backupPointer);
					y=tmpString2.logBarP;
				}
				x=Math.exp(y+Constants.logProbabilities[b]);

				k=Arrays.binarySearch(B.leftCharacters,0,B.nLeft,b);
				if (k>=0) {
					lbF=B.leftF[k];
					lbG=B.leftG[k];
				}
				else {
					// $B$ can be surely extended with $b$ to the right, but no border of
					// $B$ is followed by $b$.
					if (B.firstCharacter==b) {
						backupPointer=stack.getPosition();
						stack.setPosition(pointerStack.getElementAt(B.length-2));
						if (tmpString2==null) tmpString2=(BernoulliSubstring)getInstance();  // Executed at most once
						tmpString2.read(stack,cache,true,true,true);
if (Constants.TRACK_HITS) System.out.println(tmpString2.length);
						stack.setPosition(backupPointer);
						lbG=Math.exp(tmpString2.logBarP+Constants.logProbabilities[b]);
						lbF=(textLength-((B.length+1)<<1)+2)*lbG;
					}
					else {
						lbF=0;
						lbG=0;
					}
				}
				leftF[i] = x*( textLength-(length<<1)+leftLengths[i] +
							   lbF-((length-leftLengths[i])<<1)*lbG );
				leftG[i] = x*(1+lbG);
			}
		}
	}


	/**
	 * Being dependent on $SubstringIterator$ and on $RightMaximalSubstring$, this
	 * procedure must be adapted to the case of large alphabet.
	 */
	protected void visited(Stream stack, RigidStream characterStack, SimpleStream pointerStack, Substring[] cache, Substring[] leftExtensions) {
		super.visited(stack,characterStack,pointerStack,cache,leftExtensions);

		if (leftContext>1) {
			getExpectationAndVariance(length,barP,barPSquare,f,frequency(),longestBorderLength,out);
			getScores(frequency(),out[0],out[1],barP,out);
		}
		if (isLeftExtensionOfMaximalRepeat) {
			boolean found;
			int i, j, b;
			long freqPrime, lbPrime;
			double barPPrime, fPrime;
			j=0;
			for (i=1; i<alphabetLength; i++) {  // Disregarding $#$
				freqPrime=bwtIntervals[i][1]-bwtIntervals[i][0]+1;
				if (freqPrime<=0) continue;  // We do not consider absent words
				b=i-1;
				barPPrime=Math.exp(logBarP+Constants.logProbabilities[b]);
				while (j<nLeft && leftCharacters[j]<b) j++;
				found=j<nLeft&&leftCharacters[j]==b;
				if (found) {
					fPrime=leftF[j];
					lbPrime=leftLengths[j]+1;
				}
				else {
					// $leftCharacters$ stores only the right-extensions $c$ of $v=aw$
					// such that $aw$ has a nonzero border followed by $c$. When $a$ is
					// not in $leftCharacters$, $awa$ has still a border of length one.
					if (firstCharacter==b) {
						lbPrime=1;
						fPrime=(textLength-((length+1)<<1)+2)*Math.exp(logBarP-Constants.logProbabilities[firstCharacter]+Constants.logProbabilities[b]);
					}
					else {
						fPrime=0;
						lbPrime=0;
					}
				}
				getExpectationAndVariance(length+1,barPPrime,barPPrime*barPPrime,fPrime,freqPrime,lbPrime,outPrime);
				getScores(freqPrime,out[0],out[1],barPPrime,outPrime);
			}
		}
	}


	/**
	 * @param out 0=expectation; 1=variance; 2=probability of observing $frequency$ or
	 * more occurrences in a random string (uses the Chen-Stein method: see Section 6 of
	 * \cite{apostolico2000efficient}); 3=error in $out[2]$ from the Chen-Stein method if
	 * a Poisson distribution was used, or -1 if a normal distribution was used.
	 *
	 * Remark: because of limitations in $PoissonDistribution$, the Poisson estimation is
	 * performed only if $frequency$ can be represented as an integer.
	 */
	private final void getExpectationAndVariance(long length, double barP, double barPSquare, double f, long frequency, long longestBorderLength, double[] out) {
		double expectation, variance, b1, b2, pValue, pValueError;
		
		expectation=(bwtLength-length)*barP;
		variance=expectation*(1-barP);  // First term of the variance
		variance-=barPSquare*(((bwtLength-1)<<1)-3*length+2)*(length-1);  // Second term of the variance
		if (longestBorderLength>0) variance+=2*barP*f;
		// It's likely that the trick of \cite{sinha2000statistical}, mentioned in
		// \cite{apostolico2003monotony} on page 299, does not give any major speedup here.

		if (frequency<=Integer.MAX_VALUE && (length-longestBorderLength)/(double)length>Constants.GG*oneOverLogTextLength && textLength>Constants.GG*length) {
			b1 = barPSquare*( ((length*textLength)<<1) - textLength -3*length*length + (length<<2) - 1);
			b2 = variance-expectation+b1;
			pValueError=b1+b2;
			if (Constants.TIGHT_POISSON_ERROR) pValueError*=-StrictMath.expm1(0D-expectation)/expectation;  // $StrictMath.expm1$ is faster than $Math.expm1$ from experiments: see $FastMathTestPerformance.txt$.
			pValue=1D-(new PoissonDistribution(expectation)).cumulativeProbability((int)f);
		}
		else if (variance>0) {
			pValue=1D-(new NormalDistribution(expectation,Math.sqrt(variance))).cumulativeProbability(f);
			pValueError=-1;
		}
		else {
			pValue=-1; pValueError=-1;
		}

		out[0]=expectation; out[1]=variance; out[2]=pValue; out[3]=pValueError;	
	}


	/**
	 * Saves in $out$ the measures of surprise described in \cite{apostolico2003monotony},
	 * Table 3. Remark: some of these measures are not always monotonic inside an
	 * equivalence class, so iterating just over maximal repeats and over strings that
	 * have a maximal repeat as an infix does not guarantee to find all the significant
	 * over- and under-represented substrings.
	 */
	private static final void getScores(double frequency, double expectation, double variance, double barP, double[] out) {
		out[0]=frequency-expectation;
		out[1]=frequency/expectation;
		out[2]=(frequency-expectation)/expectation;
		out[3]=(frequency-expectation)/Math.sqrt(expectation);
		out[4]=Math.abs(frequency-expectation)/Math.sqrt(expectation);  // Not always monotonic
		out[5]=(frequency-expectation)*(frequency-expectation)/expectation;  // Not always monotonic
		out[6]=(frequency-expectation)/Math.sqrt(expectation*(1-barP));  // Not always monotonic
		out[7]=expectation/Math.sqrt(variance);  // Not always monotonic
		out[8]=(frequency-expectation)/Math.sqrt(variance);  // Not always monotonic
		out[9]=Math.abs((frequency-expectation)/Math.sqrt(variance));  // Not always monotonic
	}

}