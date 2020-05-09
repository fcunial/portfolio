/**
 * @author Djamal Belazzougui, Fabio Cunial
 */
#ifndef SLT_many_strings_h
#define SLT_many_strings_h

#include "DNA5_Basic_BWT.h"

/**
 * 
 */
#ifndef N_ITERATOR_STRINGS
#define N_ITERATOR_STRINGS 2
#endif

/** 
 * The representation of a right-maximal string W sent to the callback function.
 * 
 * Remark: if the string does not occur in an input string, all corresponding values 
 * except $frequency$ are undefined.
 */
typedef struct {
	uint64_t length;  // Length of W
	uint8_t firstCharacter;  // The first character of W. Can only be one of the following: 1=A, 2=C, 3=G, 4=T.
	uint64_t frequency[N_ITERATOR_STRINGS];  // Number of occurrences of W in each text. If $frequency[i]=0$, the values at index $i$ in all the following arrays are undefined.
	uint64_t bwtStart[N_ITERATOR_STRINGS];
	
	uint8_t nRightExtensions[N_ITERATOR_STRINGS];  // Number of distinct characters to the right of W, including # and N.
	uint8_t rightExtensionBitmap[N_ITERATOR_STRINGS];  // LSBs: 0=#, 1=A, 2=C, 3=G, 4=T, 5=N.
	uint8_t nLeftExtensions[N_ITERATOR_STRINGS];  // Number of distinct characters to the left of W, including # and N.
	uint8_t leftExtensionBitmap[N_ITERATOR_STRINGS];  // LSBs: 0=#, 1=A, 2=C, 3=G, 4=T, 5=N.
	uint64_t bwtStart_left[N_ITERATOR_STRINGS][5];  // 0=A, 1=C, 2=G, 3=T, 4=N.
	
	// Frequency of every pair of left- (rows) and right- (columns) extension.
	uint64_t frequency_leftRight[N_ITERATOR_STRINGS][6][6];  // 0=#, 1=A, 2=C, 3=G, 4=T, 5=N.
} RightMaximalString_t;


/** 
 * Callback function issued by the iterator on every string it enumerates.
 * 
 * @param applicationData pointer to a memory area maintained by the program that 
 * implements the callback function. The iterator does not touch this area.
 */
typedef void (*SLT_callback_t)(const RightMaximalString_t RightMaximalString, void *applicationData);


/** 
 * Callback function issued by the iterator when its state is cloned.
 * 
 * @param toID unique ID of iterator $to$.
 */
typedef void (*CloneState_t)(void *from, void *to, uint8_t toID);


/** 
 * Callback function issued by the iterator when its state is merged.
 */
typedef void (*MergeState_t)(void *from, void *to);


/** 
 * Callback function issued by the iterator when its state is finalized.
 */
typedef void (*FinalizeState_t)(void *applicationData);


/** 
 * @param traversalOrder order in which nodes are pushed on the iterator stack:
 * 0: no specification; 
 * 1: no specification, but with the stack trick;
 * 2: lexicographic, without the stack trick;
 *
 * @param traversalMaximality a substring is considered right- (respectively, left-) 
 * maximal iff it is followed (respectively, preceded) by:
 * 0: at least two distinct characters in {#_0,...,#_{N_ITERATOR_STRINGS-1},A,C,G,T,N};
 * 1: at least two distinct characters in {#_0,...,#_{N_ITERATOR_STRINGS-1},A,C,G,T,N}, or
 * at least two Ns (i.e. any two occurrences of N are considered as distinct characters);
 * 2: at least two distinct characters in {A,C,G,T};
 *
 * @param applicationDataSize in bytes;
 * @return the number of nodes traversed by the iterator.
 */
uint64_t iterate_sequential( BwtIndex_t *BBWT[], uint64_t minLength, uint64_t maxLength, uint64_t minFrequency[], uint64_t maxFrequency[], uint8_t traversalOrder, uint8_t traversalMaximality,
                             SLT_callback_t SLT_callback, CloneState_t cloneState, MergeState_t mergeState, FinalizeState_t finalizeState, void *applicationData, uint64_t applicationDataSize
				           );


/**
 * @param traversalOrder order in which nodes are pushed on the iterator stack:
 * 0: no specification; 
 * 1: no specification, but with the stack trick;
 * 2: lexicographic, without the stack trick;
 *
 * @param traversalMaximality a substring is considered right- (respectively, left-) 
 * maximal iff it is followed (respectively, preceded) by:
 * 0: at least two distinct characters in {#_0,...,#_{N_ITERATOR_STRINGS-1},A,C,G,T,N};
 * 1: at least two distinct characters in {#_0,...,#_{N_ITERATOR_STRINGS-1},A,C,G,T,N}, or
 * at least two Ns (i.e. any two occurrences of N are considered as distinct characters);
 * 2: at least two distinct characters in {A,C,G,T};
 *
 * @param applicationDataSize in bytes;
 * @return the number of nodes traversed by the iterator.
 */
uint64_t iterate_parallel( BwtIndex_t *BBWT[], uint64_t minLength, uint64_t maxLength, uint64_t minFrequency[], uint64_t maxFrequency[], uint8_t traversalOrder, uint8_t traversalMaximality, uint8_t nThreads,
                           SLT_callback_t SLT_callback, CloneState_t cloneState, MergeState_t mergeState, FinalizeState_t finalizeState, void *applicationData, uint64_t applicationDataSize
 				         );

#endif