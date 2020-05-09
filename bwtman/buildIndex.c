/**
 * @author Fabio Cunial
 */
#include "./malloc_count/malloc_count.h"  // For measuring memory usage
#include "./iterator/DNA5_Basic_BWT.h"
#include "./io/io.h"
#include "./io/bufferedFileWriter.h"


/** 
 * 1: input file path;
 * 2: input file format: 0=plain text; 1=multi-FASTA;
 * 3: append reverse-complement (1/0);
 * 4: output file path. If the file already exists, its content is overwritten.
 */
int main(int argc, char **argv) {
	char *INPUT_FILE_PATH = argv[1];
	const uint8_t IS_FASTA = atoi(argv[2]);
	const uint8_t APPEND_RC = atoi(argv[3]);
	char *OUTPUT_FILE_PATH = argv[4];
	
	uint64_t nBytes;
	double t, loadingTime, indexingTime, serializationTime;
	Concatenation sequence;
	BwtIndex_t *index;
	
	t=getTime();
	sequence=IS_FASTA?loadFASTA(INPUT_FILE_PATH,APPEND_RC):loadPlainText(INPUT_FILE_PATH,APPEND_RC);
	loadingTime=getTime()-t;	
	t=getTime();
	index=buildBwtIndex(sequence.buffer,sequence.length,Basic_bwt_free_text);
	indexingTime=getTime()-t;
	t=getTime();
	nBytes=serializeBwtIndex(index,OUTPUT_FILE_PATH);
	if (nBytes==0) {
		printf("ERROR in serializeBwtIndex().");
		return 1;
	}
	serializationTime=getTime()-t;
	printf( "%llu,%llu,%d|%lf,%lf,%lf|%llu \n",
    		(long long unsigned int)(sequence.inputLength),
    		(long long unsigned int)(sequence.length),
			sequence.hasRC,
			
	        loadingTime,
			indexingTime,
			serializationTime,
			
			(long long unsigned int)malloc_count_peak()
	      );
	freeBwtIndex(index);
	return 0;
}