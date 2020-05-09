/**
 * Unary-iterator callback for computing the minimal absent and the minimal rare words of
 * a single string.
 *
 * @author Fabio Cunial
 */
#ifndef MAWs_single_h
#define MAWs_single_h


#include "../iterator/SLT_single_string.h"
#include "../io/bufferedFileWriter.h"
#include "../scores.h"


typedef struct {
	// Input parameters
	uint64_t textLength;
	uint64_t minLength;  // Minimum length of a MAW to be reported
	uint64_t lowFreq, highFreq;  // Minimal rare words
	
	// Character stack
	uint64_t *char_stack;  // We push numbers in [0..3] of two bits each.
	uint64_t char_stack_capacity;  // Number of characters that can fit in the stack
	
	// Output buffer
	char *outputPath;
	BufferedFileWriter_t *outputFile;
	
	// Scores
	uint64_t *leftFreqs, *rightFreqs;  // Frequency of each left/right extension. Only for ${A,C,G,T}$, indexed from zero.
	ScoreState_t *scoreState;
	
	// Histograms
	uint64_t lengthHistogramMin, lengthHistogramMax, lengthHistogramSize;
	uint64_t *lengthHistogram;
	
	// Compressed output
	uint8_t compressOutput;  // 0 iff MAWs should not be represented in compressed form in the output.
	uint64_t *compressionBuffers[4][4][4];  // Bits inside each long in the buffer are assumed to be stored from LSB to MSB.
	uint64_t compressionBuffersLength[4][4][4];
	uint64_t compressionBuffersCapacity[4][4][4];
	uint64_t *runs_stack;  // Tells whether a node of the ST is a run of a single character or not (1/0)
	
	// Output values
	uint64_t nMAWs;  // Total number of reported MAWs
	uint64_t minObservedLength;  // Minimum observed length of a MAW
	uint64_t maxObservedLength;  // Maximum observed length of a MAW
	uint64_t nMaxreps;  // Number of visited maximal repeats
	uint64_t nMAWMaxreps;  // N. of visited maxreps that are the infix of a MAW
} MAWs_callback_state_t;


void MAWs_callback(const RightMaximalString_t RightMaximalString, void *applicationData);


/**
 * @param minLength (>=2) considers only MAWs of length at least $minLength$;
 *
 * @param lengthHistogramMin,lengthHistogramMax computes the number of MAWs with length
 * $i$ for all $i \in [lengthHistogramMin..lengthHistogramMax]$; the first (respectively,
 * last) cell of the histogram contains the number of MAWs with length at most (at least)
 * equal to the corresponding length; no histogram is computed if $lengthHistogramMin==0$;
 *
 * @param outputPath NULL iff MAWs should not be written to the output; otherwise, MAWs 
 * are appended to $file$, whose previous content is destroyed.
 */
void MAWs_initialize( MAWs_callback_state_t *state,
			    	  uint64_t textLength, 
					  uint64_t minLength, 
					  uint64_t lengthHistogramMin,
					  uint64_t lengthHistogramMax,
					  char *outputPath,
					  uint8_t compressOutput );


/**
 * Flushes the output buffers one more time, if any, and frees up space.
 */
void MAWs_finalize(void *applicationData);


void printLengthHistogram(MAWs_callback_state_t *state);


/**
 * Remark: $RightMaximalString$ is assumed to have frequency at least equal to
 * $MAWs_callback_state_t->highFreq$.
 */
void MRWs_callback(const RightMaximalString_t RightMaximalString, void *applicationData);


/**
 * Detects minimal rare words $W$ such that $lowFreq \leq f(W) < highFreq$ and 
 * $f(V) \geq highFreq$ for every substring $V$ of $W$.
 * See $MAWs_initialize$ for details on the input arguments.
 */
void MRWs_initialize( MAWs_callback_state_t *state,
			    	  uint64_t textLength, 
					  uint64_t minLength, 
					  uint64_t lowFreq, 
					  uint64_t highFreq, 
					  uint64_t lengthHistogramMin,
					  uint64_t lengthHistogramMax,
					  char *outputPath,
					  uint8_t compressOutput );


/**
 * Creates a clone of the MAW state in $from$ (except for output values, which are reset 
 * to zero).
 */
void cloneMAWState(void *from, void *to, uint8_t toID);


/**
 * Merges the statistics of the MAW state of $from$ into those of the MAW state of $to$.
 */
void mergeMAWState(void *from, void *to);


/**
 * Flushes the output buffers one more time, if any, and frees up space.
 */
void MRWs_finalize(void *applicationData);


#endif