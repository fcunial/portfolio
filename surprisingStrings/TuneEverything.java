
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;
import java.text.SimpleDateFormat;


/**
 *
 */
public class TuneEverything {

	public static void main(String[] args) throws IOException {
		int l, stringLength, iterations, minIndex, END, END1, END2, STEP2, nThreads;
		long time;
		double min;
		double[] measurements;
		double[][] matrix;
		String path;
		IntArray string=null;
		BernoulliSubstring w;
		SubstringIterator iterator;
		nThreads=Integer.parseInt(args[0]);
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File("TUNE_EVERYTHING_OUT_"+nThreads+".txt")));
		SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

		// Parsing input
		path="dna.50MB";  // (file,length,nLines): (dna.50MB,52427710,?), (NC_021658.fna,14782125,211174)
		stringLength=52427710;
		iterations=1;
		bw.write("availableProcessors="+Runtime.getRuntime().availableProcessors()+" (but using "+nThreads+")\n");
		bw.flush();

		// Building $SubstringIterator$
		int[] alphabet = new int[] {0,1,2,3};
		try { string=Utils.loadDNA(path,stringLength,1000); }
		catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		w = new BernoulliSubstring(4,Utils.log2(4),Utils.bitsToEncode(4),string.length()+1,Utils.log2(string.length()+1),Utils.bitsToEncode(string.length()+1));
		time=System.currentTimeMillis();
		iterator = new SubstringIterator(string,alphabet,alphabet.length,w);
		bw.write("construction time: "+(System.currentTimeMillis()-time)+"\n");
		bw.flush();
		Constants.N_THREADS=nThreads;

/*
		// LONGS_PER_REGION
		END=10;
		measurements = new double[END+1];
		for (l=0; l<=END; l++) {
			Constants.LONGS_PER_REGION=1<<l;
			time=System.currentTimeMillis();
			for (int i=0; i<iterations; i++) iterator.run();
			measurements[l]=(double)(System.currentTimeMillis()-time)/iterations;
			bw.write(".");bw.flush();
		}
		bw.write("\nLONGS_PER_REGION\n");
		min=Double.MAX_VALUE;
		minIndex=-1;
		for (l=0; l<=END; l++) {
			bw.write(measurements[l]+"\n");
			if (measurements[l]<min) {
				min=measurements[l];
				minIndex=l;
			}
		}
		bw.flush();
		Constants.LONGS_PER_REGION=1<<minIndex;


		// LONGS_PER_REGION_CHARACTERSTACK
		END=10;
		measurements = new double[END+1];
		for (l=0; l<=END; l++) {
			Constants.LONGS_PER_REGION_CHARACTERSTACK=1<<l;
			time=System.currentTimeMillis();
			for (int i=0; i<iterations; i++) iterator.run();
			measurements[l]=(double)(System.currentTimeMillis()-time)/iterations;
			bw.write(".");bw.flush();
		}
		bw.write("\nLONGS_PER_REGION_CHARACTERSTACK\n");
		min=Double.MAX_VALUE;
		minIndex=-1;
		for (l=0; l<=END; l++) {
			bw.write(measurements[l]+"\n");
			if (measurements[l]<min) {
				min=measurements[l];
				minIndex=l;
			}
		}
		bw.flush();
		Constants.LONGS_PER_REGION_CHARACTERSTACK=1<<minIndex;


		// LONGS_PER_REGION_POINTERSTACK
		END=10;
		measurements = new double[END+1];
		for (l=0; l<=END; l++) {
			Constants.LONGS_PER_REGION_POINTERSTACK=1<<l;
			time=System.currentTimeMillis();
			for (int i=0; i<iterations; i++) iterator.run();
			measurements[l]=(double)(System.currentTimeMillis()-time)/iterations;
			bw.write(".");bw.flush();
		}
		bw.write("\nLONGS_PER_REGION_POINTERSTACK\n");
		min=Double.MAX_VALUE;
		minIndex=-1;
		for (l=0; l<=END; l++) {
			bw.write(measurements[l]+"\n");
			if (measurements[l]<min) {
				min=measurements[l];
				minIndex=l;
			}
		}
		bw.flush();
		Constants.LONGS_PER_REGION_POINTERSTACK=1<<minIndex;


		// N_STEALING_ATTEMPTS
		END1 = 10;
		STEP2 = 10;
		END2 = 100;
		measurements = new double[1+END1+(END2-END1)/STEP2];
		for (l=1; l<=END1; l++) {
			Constants.N_STEALING_ATTEMPTS=l;
			time=System.currentTimeMillis();
			for (int i=0; i<iterations; i++) iterator.run();
			measurements[l]=(double)(System.currentTimeMillis()-time)/iterations;
			bw.write(".");bw.flush();
		}
		for (l=END1+1; l<measurements.length; l++) {
			Constants.N_STEALING_ATTEMPTS=END1+(l-END1)*STEP2;
			time=System.currentTimeMillis();
			for (int i=0; i<iterations; i++) iterator.run();
			measurements[l]=(double)(System.currentTimeMillis()-time)/iterations;
			bw.write(".");bw.flush();
		}
		bw.write("\nN_STEALING_ATTEMPTS\n");
		min=Double.MAX_VALUE;
		minIndex=-1;
		for (l=1; l<measurements.length; l++) {
			bw.write(measurements[l]+"\n");
			if (measurements[l]<min) {
				min=measurements[l];
				minIndex=l;
			}
		}
		bw.flush();
		Constants.N_STEALING_ATTEMPTS=minIndex<=END1?minIndex:END1+(minIndex-END1)*STEP2;
*/

		// MAX_STRING_LENGTH_FOR_SPLIT, DONOR_STACK_LOWERBOUND
		END1=10;
		END2=10;
		matrix = new double[END1+1][END2+1];
		bw.write("\nMAX_STRING_LENGTH_FOR_SPLIT, DONOR_STACK_LOWERBOUND\n");
		for (int m=END1; m>=1; m--) {
			Constants.MAX_STRING_LENGTH_FOR_SPLIT=m;
			for (int d=END2; d>=2; d--) {
				Constants.DONOR_STACK_LOWERBOUND=d;
				time=System.currentTimeMillis();
				for (int i=0; i<iterations; i++) iterator.run();
				matrix[m][d]=(double)(System.currentTimeMillis()-time)/iterations;
				bw.write(matrix[m][d]+" "); bw.flush();
				System.out.println(sdf.format(new java.util.Date())+"> finished processing pair ("+m+","+d+")");
			}
			bw.write("\n"); bw.flush();
		}
		min=Double.MAX_VALUE;
		int minIndex1 = -1;
		int minIndex2 = -1;
		for (int m=1; m<=END1; m++) {
			for (int d=2; d<=END2; d++) {
				if (matrix[m][d]<min) {
					min=matrix[m][d];
					minIndex1=m;
					minIndex2=d;
				}
			}
		}
		Constants.MAX_STRING_LENGTH_FOR_SPLIT=minIndex1;
		Constants.DONOR_STACK_LOWERBOUND=minIndex2;



		bw.close();
	}

}