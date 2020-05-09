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
 * 3: min length of a MAW;
 * 4: max length of a MAW;
 * 5: min histogram length;
 * 6: max histogram length;
 *
 * 7: compute the score of each MAW (1/0);
 * 8: ID of the score used for selecting specific MAWs;
 * 9: min absolute value of a score for a MAW to be selected;
 * 
 * 10: write MAWs to a file (1/0);
 * 11: output file path; if the file already exists, its content is overwritten;
 * 12: compresses output (1/0); used only if MAWs are written to a file and scores are not
 *     computed.
 */
int main(int argc, char **argv) {
	char *INPUT_FILE_PATH = argv[1];
	const uint8_t N_THREADS = atoi(argv[2]);
	
	const uint64_t MIN_MAW_LENGTH = atoi(argv[3]);
	const uint64_t MAX_MAW_LENGTH = atoi(argv[4]);
	const uint64_t MIN_HISTOGRAM_LENGTH = atoi(argv[5]);
	const uint64_t MAX_HISTOGRAM_LENGTH = atoi(argv[6]);
	
	const uint8_t COMPUTE_SCORES = atoi(argv[7]);
	SELECTED_SCORE=atoi(argv[8]);
	SELECTED_SCORE_THRESHOLD=atof(argv[9]);
	
	const uint8_t WRITE_MAWS = atoi(argv[10]);
	char *OUTPUT_FILE_PATH = NULL;
	uint8_t COMPRESS_OUTPUT = 0;
	if (WRITE_MAWS==1) {
		OUTPUT_FILE_PATH=argv[11];
		if (COMPUTE_SCORES==0) COMPRESS_OUTPUT=atoi(argv[12]);
	}
	
	uint64_t nBytes;
	double t, loadingTime, processingTime;
	BwtIndex_t *bbwt;
	MAWs_callback_state_t MAWs_state;
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
	MAWs_initialize(&MAWs_state,bbwt->textLength,MIN_MAW_LENGTH,MIN_HISTOGRAM_LENGTH,MAX_HISTOGRAM_LENGTH,WRITE_MAWS==0?NULL:OUTPUT_FILE_PATH,COMPRESS_OUTPUT);
	if (COMPUTE_SCORES!=0) {
		scoreInitialize(&scoreState,bbwt->dnaProbabilities,bbwt->logDnaProbabilities);
		MAWs_state.scoreState=&scoreState;
	}
	
	// Running the iterator
	t=getTime();
	if (N_THREADS==1) iterate_sequential( bbwt, 
	                                      MIN_MAW_LENGTH>=2?MIN_MAW_LENGTH-2:MIN_MAW_LENGTH,MAX_MAW_LENGTH-2,0,ULLONG_MAX,1,0,
                             			  MAWs_callback,cloneMAWState,mergeMAWState,MAWs_finalize,&MAWs_state,sizeof(MAWs_callback_state_t)
				                        );
	else iterate_parallel( bbwt,
				           MIN_MAW_LENGTH>=2?MIN_MAW_LENGTH-2:MIN_MAW_LENGTH,MAX_MAW_LENGTH-2,0,ULLONG_MAX,1,0,
						   N_THREADS,
					       MAWs_callback,cloneMAWState,mergeMAWState,MAWs_finalize,&MAWs_state,sizeof(MAWs_callback_state_t)
					     );
	processingTime=getTime()-t;
	printf( "%llu,%llu,%llu|%lf,%lf|%llu|%llu,%llu,%llu,%lf \n", 
	        (long long unsigned int)(bbwt->textLength),
			(long long unsigned int)(MIN_MAW_LENGTH),
			(long long unsigned int)(MAX_MAW_LENGTH),
			
			loadingTime,
			processingTime,
			
			(long long unsigned int)malloc_count_peak(),
			
			(long long unsigned int)(MAWs_state.nMAWs),
			(long long unsigned int)(MAWs_state.minObservedLength),
			(long long unsigned int)(MAWs_state.maxObservedLength),
			((double)MAWs_state.nMAWMaxreps)/MAWs_state.nMaxreps
	      );
	if (MIN_HISTOGRAM_LENGTH>0) printLengthHistogram(&MAWs_state);

	// Finalizing application state
	if (COMPUTE_SCORES!=0) scoreFinalize(&scoreState);
	freeBwtIndex(bbwt);
	
	return 0;
}