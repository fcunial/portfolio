import java.util.Arrays;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;


/**
 *
 */
public class Scanner2 {

	public static void main(String[] args) throws IOException {
		int i, length, max;
		int[] vector;
		String str, path;
		BufferedReader br;

		// Input
		path="hits_reshuffled.txt";

		// Finding the maximum value
		br = new BufferedReader(new FileReader(path),1000);
		max=0;
		str=br.readLine();  // Skipping the first line
		str=br.readLine();
		while (str!=null) {
			try {length=Integer.parseInt(str); }
			catch(NumberFormatException e) { break; }
			if (length>max) max=length;
			str=br.readLine();
		}
		br.close();
		vector = new int[max+1];

		// Building the histogram
		br = new BufferedReader(new FileReader(path),1000);
		str=br.readLine();  // Skipping the first line
		str=br.readLine();
		while (str!=null) {
			try {length=Integer.parseInt(str); }
			catch(NumberFormatException e) { break; }
			vector[length]++;
			str=br.readLine();
		}
		br.close();

		// Reporting
		for (i=0; i<=max; i++) System.out.println(vector[i]);
	}

}
