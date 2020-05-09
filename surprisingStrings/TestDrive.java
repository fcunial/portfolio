import java.text.DecimalFormat;
import java.util.Locale;
import java.io.IOException;


/**
 *
 */
public class TestDrive {

	static int MAX_LENGTH = 10000;
	static int[] READ_QUERIES_PER_LENGTH = new int[MAX_LENGTH];


	public static void main(String[] args) {
		int i, j, stringLength, maxLength;
		int l, iterations;
		long time;
		String path;
		IntArray string = null;
		IntArray outputString = new IntArray(1000,2);
		TestBernoulliSubstring w;
		SubstringIterator iterator;
		Runtime runtime = Runtime.getRuntime();

		// Parsing input
		path="NC_021658.fna";
		stringLength=14782125;  // (file,length,nLines): (dna.50MB,52427710,?), (NC_021658.fna,14782125,211174)

		// Initializing
		int[] alphabet = new int[] {0,1,2,3};
		try { string=Utils.loadDNA(path,stringLength,1000); }
		catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		stringLength=(int)string.length();
//Utils.shuffle(string);
		w = new TestBernoulliSubstring(alphabet.length,Utils.log2(alphabet.length),Utils.bitsToEncode(alphabet.length),stringLength+1,Utils.log2(stringLength+1),Utils.bitsToEncode(stringLength+1),outputString);
		time=System.currentTimeMillis();
		iterator = new SubstringIterator(string,alphabet,alphabet.length,w);
		System.out.println("construction time: "+((double)(System.currentTimeMillis()-time))/1000+"s, length="+stringLength);

		// Running
		Constants.N_THREADS=Integer.parseInt(args[0]);
		System.out.println("String length: "+stringLength+" nThreads: "+Constants.N_THREADS);
		time=System.currentTimeMillis();
		iterator.run();
		System.out.println("traversal time: "+((double)(System.currentTimeMillis()-time))/1000);

/*		// Reporting
		System.out.println("READ_QUERIES_PER_LENGTH:");
		for (i=0; i<MAX_LENGTH; i++) System.out.println(READ_QUERIES_PER_LENGTH[i]+"");
*/	}


	private static class TestBernoulliSubstring extends BernoulliSubstring {
		private IntArray sequence;
		private DecimalFormat formatter;
		
		public TestBernoulliSubstring(int alphabetLength, int log2alphabetLength, int bitsToEncodeAlphabetLength, long bwtLength, int log2BWTLength, int bitsToEncodeBWTLength, IntArray sequence) {
			super(alphabetLength,log2alphabetLength,bitsToEncodeAlphabetLength,bwtLength,log2BWTLength,bitsToEncodeBWTLength);
			this.sequence=sequence;
			formatter = new DecimalFormat("##0.####E0");
			//formatter.setMaximumFractionDigits(4);
			//formatter.setMaximumIntegerDigits(4);
			//formatter.setGroupingUsed(false);
		}
		
		protected Substring getInstance() {
			return new TestBernoulliSubstring(alphabetLength,log2alphabetLength,bitsToEncodeAlphabetLength,bwtLength,log2BWTLength,bitsToEncodeBWTLength,sequence);
		}

		protected void read(Stream stack, Substring[] cache, boolean fastHead, boolean fastTail, boolean fastAppendix) {
			super.read(stack,cache,fastHead,fastTail,fastAppendix);
			if (length<MAX_LENGTH) READ_QUERIES_PER_LENGTH[(int)length]++;
		}
		
		protected void visited(Stream stack, RigidStream characterStack, SimpleStream pointerStack, Substring[] cache, Substring[] leftExtensions) {
			super.visited(stack,characterStack,pointerStack,cache,leftExtensions);
			
			if (leftContext>1 && frequency()>=3 && out[2]>=100) {
				synchronized(sequence) {
					getSequence(characterStack,sequence);
					IntArray.printAsDNA(sequence);
					System.out.print(" f="+frequency()+" ");
					for (int i=0; i<N_SCORES; i++) System.out.print(formatter.format(out[i])+" ");
					System.out.println();
				}
			}
		}
		
	}

}


/*
protected void visited(Stream stack, RigidStream characterStack, SimpleStream pointerStack, Substring[] cache, Substring[] leftExtensions) {
			super.visited(stack,characterStack,pointerStack,cache,leftExtensions);
		}
*/