
import java.io.IOException;


/**
 *
 */
public class ParallelTestDrive {

	public static void main(String[] args) {
		int stringLength;
		long time;
		String path;
		IntArray string=null;
		BernoulliSubstring w;
		SubstringIterator iterator;
		Runtime runtime = Runtime.getRuntime();

		// Parsing input
		path="NC_021658.fna";
		stringLength=14782125;  // (file,length,nLines): (dna.50MB,52427710,?), (NC_021658.fna,14782125,211174)
		final int MAX_THREADS = 2;

		// Initializing
		int[] alphabet = new int[] {0,1,2,3};
		try { string=Utils.loadDNA(path,stringLength,1000); }
		catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		stringLength=(int)string.length();
		w = new BernoulliSubstring(alphabet.length,Utils.log2(alphabet.length),Utils.bitsToEncode(alphabet.length),stringLength+1,Utils.log2(stringLength+1),Utils.bitsToEncode(stringLength+1));
		Constants.N_THREADS=MAX_THREADS;
		time=System.currentTimeMillis();
		iterator = new SubstringIterator(string,alphabet,alphabet.length,w);
		System.out.print("construction time with "+Constants.N_THREADS+" threads: "+((double)(System.currentTimeMillis()-time))/1000+"s, ");
		System.out.println("String length: "+stringLength);

		// Running
		for (Constants.N_THREADS=MAX_THREADS; Constants.N_THREADS>=1; Constants.N_THREADS--) {
			java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
			System.out.println(sdf.format(new java.util.Date())+"> run with "+Constants.N_THREADS+" threads started... ");
			time=System.currentTimeMillis();
			iterator.run();
			System.out.println(sdf.format(new java.util.Date())+"> run with "+Constants.N_THREADS+" ended in "+((double)(System.currentTimeMillis()-time))/1000+" sec");
		}
	}

}