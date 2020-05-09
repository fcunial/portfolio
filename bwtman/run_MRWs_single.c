/**
 * @author Fabio Cunial, Filippo Gambarotto
 */
#include <limits.h>
#include "./malloc_count/malloc_count.h"  // For measuring memory usage
#include "./iterator/DNA5_Basic_BWT.h"
#include "./callbacks/MAWs_single.h"
#include "./io/io.h"
#include "./io/bufferedFileWriter.h"
#include "scores.h"

/**
 * For communicating with $scores.c$.
 */
extern unsigned char SELECTED_SCORE;
extern double SELECTED_SCORE_THRESHOLD;


/** 
 * 1: path of the index file;
 * 2: number of threads;
 *
 * 3: min length of a MRW;
 * 4: max length of a MRW;
 * 5: min freqency of a MRW;
 * 6: max frequency of a MRW;
 * 7: min histogram length;
 * 8: max histogram length;
 *
 * 9: compute the score of each MRW (1/0);
 * 10: ID of the score used for selecting specific MRWs;
 * 11: min absolute value of a score for a MRW to be selected;
 * 
 * 12: write MRWs to a file (1/0);
 * 13: output file path; if the file already exists, its content is overwritten;
 * 14: compresses output (1/0); used only if MRWs are written to a file and scores are not
 *     computed.
 */
int main(int argc, char **argv) {
	char *INPUT_FILE_PATH = argv[1];
	const uint8_t N_THREADS = atoi(argv[2]);
	
	const uint64_t MIN_MRW_LENGTH = atoi(argv[3]);
	const uint64_t MAX_MRW_LENGTH = atoi(argv[4]);
	const uint64_t LOW_FREQ = atoi(argv[5]);
	const uint64_t HIGH_FREQ = atoi(argv[6]);
	const uint64_t MIN_HISTOGRAM_LENGTH = atoi(argv[7]);
	const uint64_t MAX_HISTOGRAM_LENGTH = atoi(argv[8]);
	
	const uint8_t COMPUTE_SCORES = atoi(argv[9]);
	SELECTED_SCORE=atoi(argv[10]);
	SELECTED_SCORE_THRESHOLD=atof(argv[11]);
	
	const uint8_t WRITE_MRWS = atoi(argv[12]);
	char *OUTPUT_FILE_PATH = NULL;
	uint8_t COMPRESS_OUTPUT = 0;
	if (WRITE_MRWS==1) {
		OUTPUT_FILE_PATH=argv[13];
		if (COMPUTE_SCORES==0) COMPRESS_OUTPUT=atoi(argv[14]);
	}
	
	uint64_t nBytes;
	double t, loadingTime, processingTime;
	BwtIndex_t *bbwt;
	MAWs_callback_state_t MRWs_state;
	ScoreState_t scoreState;
	
	// Loading the index
	t=getTime();
	bbwt=newBwtIndex();
	nBytes=deserializeBwtIndex(bbwt,INPUT_FILE_PATH);
	if (nBytes==0) {
		printf("ERROR while reading the index \n");
		return 1;
	}
	loadingTime=getTime()-t;
	
	// Initializing application state
	MRWs_initialize(&MRWs_state,bbwt->textLength,MIN_MRW_LENGTH,LOW_FREQ,HIGH_FREQ,MIN_HISTOGRAM_LENGTH,MAX_HISTOGRAM_LENGTH,WRITE_MRWS==0?NULL:OUTPUT_FILE_PATH,COMPRESS_OUTPUT);
	if (COMPUTE_SCORES!=0) {
		scoreInitialize(&scoreState,bbwt->dnaProbabilities,bbwt->logDnaProbabilities);
		MRWs_state.scoreState=&scoreState;
	}
	
	// Running the iterator
	t=getTime();
	if (N_THREADS==1) iterate_sequential( bbwt, 
	                                      MIN_MRW_LENGTH>=2?MIN_MRW_LENGTH-2:MIN_MRW_LENGTH,MAX_MRW_LENGTH-2,HIGH_FREQ,ULLONG_MAX,1,0,
                             			  MRWs_callback,cloneMAWState,mergeMAWState,MRWs_finalize,&MRWs_state,sizeof(MAWs_callback_state_t)
				                        );
	else iterate_parallel( bbwt,
				           MIN_MRW_LENGTH>=2?MIN_MRW_LENGTH-2:MIN_MRW_LENGTH,MAX_MRW_LENGTH-2,HIGH_FREQ,ULLONG_MAX,1,0,
						   N_THREADS,
					       MRWs_callback,cloneMAWState,mergeMAWState,MRWs_finalize,&MRWs_state,sizeof(MAWs_callback_state_t)
					     );
	processingTime=getTime()-t;
	printf( "%llu,%llu,%llu,%llu,%llu|%lf,%lf|%llu|%llu,%llu,%llu,%lf \n", 
		    (long long unsigned int)(bbwt->textLength),
			(long long unsigned int)(MIN_MRW_LENGTH),
			(long long unsigned int)(MAX_MRW_LENGTH),
			(long long unsigned int)(LOW_FREQ),
			(long long unsigned int)(HIGH_FREQ),
			
			loadingTime,
			processingTime,
			
			(long long unsigned int)malloc_count_peak(),
			
			(long long unsigned int)(MRWs_state.nMAWs),
			(long long unsigned int)(MRWs_state.minObservedLength),
			(long long unsigned int)(MRWs_state.maxObservedLength),
			((double)MRWs_state.nMAWMaxreps)/MRWs_state.nMaxreps
	      );
	if (MIN_HISTOGRAM_LENGTH>0) printLengthHistogram(&MRWs_state);
	
	// Finalizing application state
	if (COMPUTE_SCORES!=0) scoreFinalize(&scoreState);
	freeBwtIndex(bbwt);
	
	return 0;
}