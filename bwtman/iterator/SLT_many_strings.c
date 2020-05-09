/**
 * @author Djamal Belazzougui, Fabio Cunial
 */
#include <stdio.h>
#include <string.h>
#include <math.h>
#include "SLT_many_strings.h"
#include "../io/bits.h"


/**
 * Initial size of the iterator stack (in stack frames).
 */
#ifndef MIN_SLT_STACK_SIZE
#define MIN_SLT_STACK_SIZE 16
#endif

/**
 * The parallel iterator creates a number of workpackages equal to 
 * $nThreads*N_WORKPACKAGES_RATE$. Increasing $N_WORKPACKAGES_RATE$ might improve load
 * balancing.
 */
#ifndef N_WORKPACKAGES_RATE
#define N_WORKPACKAGES_RATE 2
#endif




// ---------------------- CREATION, DESTRUCTION, CLONING, MERGING ------------------------

/** 
 * To assign distinct IDs to iterator instances.
 */
static uint8_t idGenerator = 0;


/**
 * A frame in the iterator's stack.
 */
typedef struct {
	uint64_t length;
	uint8_t firstCharacter;
	uint64_t frequency[N_ITERATOR_STRINGS];  // If $frequency[i]=0$, then the values in $bwtStart[i]$ and $frequency_right[i]$ are undefined.
	uint64_t bwtStart[N_ITERATOR_STRINGS];
	uint64_t frequency_right[N_ITERATOR_STRINGS][6];  // 0=#, 1=A, 2=C, 3=G, 4=T, 5=N.
} StackFrame_t;


/**
 * Instance of an iterator of all right-maximal substrings of the concatenation of 
 * $N_ITERATOR_STRINGS$ input strings. 
 */
typedef struct {
	// Unique ID of this instance
	uint8_t id;
	
	// BWT
	BwtIndex_t *BBWT[N_ITERATOR_STRINGS];
	
	// Stack
	StackFrame_t *stack;
	uint64_t stackSize;  // In frames
	uint64_t stackPointer;  // Pointer to the first free frame on the stack
	uint64_t minStackPointer;  // Iteration stops when $stackPointer<minStackPointer$.
	
	// Input parameters
	uint64_t minLength;  // Minimum length of a substring to be enumerated
	uint64_t maxLength;  // Maximum length of a substring to be enumerated
	uint64_t minFrequency[N_ITERATOR_STRINGS];  // Minimum frequency, in each input string, of a substring to be enumerated.
	uint64_t maxFrequency[N_ITERATOR_STRINGS];  // Maximum frequency, in each input string, of a substring to be enumerated.
	uint8_t traversalOrder;
	uint8_t traversalMaximality;
	
	// Output values
	uint64_t nTraversedNodes;  // Total number of traversed nodes in the generalized ST
	
	// Application state
	SLT_callback_t SLT_callback;  // Callback function
	CloneState_t cloneState;
	MergeState_t mergeState;
	FinalizeState_t finalizeState;
	void *applicationData;  // Memory area managed by the callback function
	uint64_t applicationDataSize;  // In bytes
} GeneralizedIterator_t;


GeneralizedIterator_t newIterator( BwtIndex_t *BBWT[], 
							       uint64_t minLength, uint64_t maxLength, uint64_t minFrequency[], uint64_t maxFrequency[], uint8_t traversalOrder, uint8_t traversalMaximality,
                                   SLT_callback_t SLT_callback, CloneState_t cloneState, MergeState_t mergeState, FinalizeState_t finalizeState, void *applicationData, uint64_t applicationDataSize
						         ) {
	uint8_t i;
	GeneralizedIterator_t iterator;
	
	// Unique ID of this instance
	iterator.id=idGenerator++;
	
	// BWT
	for (i=0; i<N_ITERATOR_STRINGS; i++) iterator.BBWT[i]=BBWT[i];
	
	// Stack
	iterator.stack=(StackFrame_t *)malloc((1+MIN_SLT_STACK_SIZE)*sizeof(StackFrame_t));
	iterator.stackSize=MIN_SLT_STACK_SIZE;
	iterator.stackPointer=0;
	iterator.minStackPointer=0;
	
	// Input parameters
	iterator.minLength=minLength;
	iterator.maxLength=maxLength;
	for (i=0; i<N_ITERATOR_STRINGS; i++) iterator.minFrequency[i]=minFrequency[i];
	for (i=0; i<N_ITERATOR_STRINGS; i++) iterator.maxFrequency[i]=maxFrequency[i];
	iterator.traversalOrder=traversalOrder;
	iterator.traversalMaximality=traversalMaximality;
	
	// Output values
	iterator.nTraversedNodes=0;
	
	// Application state
	iterator.SLT_callback=SLT_callback;
	iterator.cloneState=cloneState;
	iterator.mergeState=mergeState;
	iterator.finalizeState=finalizeState;
	iterator.applicationData=applicationData;
	iterator.applicationDataSize=applicationDataSize;
	
	return iterator;
}


/**
 * Sets $to$ to be a copy of $from$ (except for output values, which are reset to zero).
 * A new stack is allocated for $to$, which is identical to the one in $from$. 
 * The $id$ field of $to$ is not changed. 
 *
 * At the end, the procedure notifies the application by issuing the $cloneState()$
 * callback.
 */
static inline void cloneIterator(GeneralizedIterator_t *from, GeneralizedIterator_t *to) {
	uint8_t i;
	
	// Unique ID of this instance
	// Unchanged
	
	// BWT
	for (i=0; i<N_ITERATOR_STRINGS; i++) to->BBWT[i]=from->BBWT[i];
	
	// Stack
	const uint64_t N_BYTES = (from->stackSize)*sizeof(StackFrame_t);
	to->stack=(StackFrame_t *)malloc(N_BYTES);
	memcpy(to->stack,from->stack,N_BYTES);
	to->stackSize=from->stackSize;
	to->stackPointer=from->stackPointer;
	to->minStackPointer=from->minStackPointer;
	
	// Input parameters
	to->minLength=from->minLength;
	to->maxLength=from->maxLength;
	for (i=0; i<N_ITERATOR_STRINGS; i++) to->minFrequency[i]=from->minFrequency[i];
	for (i=0; i<N_ITERATOR_STRINGS; i++) to->maxFrequency[i]=from->maxFrequency[i];
	to->traversalOrder=from->traversalOrder;
	to->traversalMaximality=from->traversalMaximality;
	
	// Output values
	to->nTraversedNodes=0;
	
	// Application state
	to->SLT_callback=from->SLT_callback;
	to->cloneState=from->cloneState;
	to->mergeState=from->mergeState;
	to->finalizeState=from->finalizeState;
	to->applicationDataSize=from->applicationDataSize;
	to->applicationData=malloc(to->applicationDataSize);
	to->cloneState(from->applicationData,to->applicationData,to->id);
}


/**
 * Merges just the output values of $from$ into those of $to$, and notifies the 
 * application by issuing the $mergeState()$ callback.
 */
static inline void mergeIterator(GeneralizedIterator_t *from, GeneralizedIterator_t *to) {
	// Output values
	to->nTraversedNodes+=from->nTraversedNodes;
	
	// Application state
	to->mergeState(from->applicationData,to->applicationData);
}


/**
 * Frees the memory owned by $iterator$, sets to NULL all pointers, and notifies the 
 * application by issuing the $finalizeState()$ callback.
 */
void iterator_finalize(GeneralizedIterator_t *iterator) {
	uint8_t i;
	
	// Application state
	iterator->finalizeState(iterator->applicationData);
	iterator->applicationData=NULL;
	iterator->SLT_callback=NULL;
	iterator->cloneState=NULL;
	iterator->mergeState=NULL;
	iterator->finalizeState=NULL;
	
	// BWT
	for (i=0; i<N_ITERATOR_STRINGS; i++) iterator->BBWT[i]=NULL;
	
	// Stack
	free(iterator->stack);
	iterator->stack=NULL;
}




// ------------------------------------ ITERATION ----------------------------------------

static inline void swapStackFrames(StackFrame_t *SLT_stack_item1, StackFrame_t *SLT_stack_item2) {
	uint8_t i;
	
	SLT_stack_item1->length^=SLT_stack_item2->length;
	for (i=0; i<N_ITERATOR_STRINGS; i++) SLT_stack_item1->bwtStart[i]^=SLT_stack_item2->bwtStart[i];
	for (i=0; i<N_ITERATOR_STRINGS; i++) SLT_stack_item1->frequency[i]^=SLT_stack_item2->frequency[i];
	SLT_stack_item1->firstCharacter^=SLT_stack_item2->firstCharacter;

	SLT_stack_item2->length^=SLT_stack_item1->length;
	for (i=0; i<N_ITERATOR_STRINGS; i++) SLT_stack_item2->bwtStart[i]^=SLT_stack_item1->bwtStart[i];
	for (i=0; i<N_ITERATOR_STRINGS; i++) SLT_stack_item2->frequency[i]^=SLT_stack_item1->frequency[i];
	SLT_stack_item2->firstCharacter^=SLT_stack_item1->firstCharacter;

	SLT_stack_item1->length^=SLT_stack_item2->length;
	for (i=0; i<N_ITERATOR_STRINGS; i++) SLT_stack_item1->bwtStart[i]^=SLT_stack_item2->bwtStart[i];
	for (i=0; i<N_ITERATOR_STRINGS; i++) SLT_stack_item1->frequency[i]^=SLT_stack_item2->frequency[i];
	SLT_stack_item1->firstCharacter^=SLT_stack_item2->firstCharacter;

	for (i=0; i<N_ITERATOR_STRINGS; i++) {
		SLT_stack_item1->frequency_right[0][i]^=SLT_stack_item2->frequency_right[0][i];
		SLT_stack_item1->frequency_right[1][i]^=SLT_stack_item2->frequency_right[1][i];
		SLT_stack_item1->frequency_right[2][i]^=SLT_stack_item2->frequency_right[2][i];
		SLT_stack_item1->frequency_right[3][i]^=SLT_stack_item2->frequency_right[3][i];
		SLT_stack_item1->frequency_right[4][i]^=SLT_stack_item2->frequency_right[4][i];
		SLT_stack_item1->frequency_right[5][i]^=SLT_stack_item2->frequency_right[5][i];

		SLT_stack_item2->frequency_right[0][i]^=SLT_stack_item1->frequency_right[0][i];
		SLT_stack_item2->frequency_right[1][i]^=SLT_stack_item1->frequency_right[1][i];
		SLT_stack_item2->frequency_right[2][i]^=SLT_stack_item1->frequency_right[2][i];
		SLT_stack_item2->frequency_right[3][i]^=SLT_stack_item1->frequency_right[3][i];
		SLT_stack_item2->frequency_right[4][i]^=SLT_stack_item1->frequency_right[4][i];
		SLT_stack_item2->frequency_right[5][i]^=SLT_stack_item1->frequency_right[5][i];

		SLT_stack_item1->frequency_right[0][i]^=SLT_stack_item2->frequency_right[0][i];
		SLT_stack_item1->frequency_right[1][i]^=SLT_stack_item2->frequency_right[1][i];
		SLT_stack_item1->frequency_right[2][i]^=SLT_stack_item2->frequency_right[2][i];
		SLT_stack_item1->frequency_right[3][i]^=SLT_stack_item2->frequency_right[3][i];
		SLT_stack_item1->frequency_right[4][i]^=SLT_stack_item2->frequency_right[4][i];
		SLT_stack_item1->frequency_right[5][i]^=SLT_stack_item2->frequency_right[5][i];
	}
}


/**
 * Computes all distinct right-extensions $Wa$ of the string $W$ encoded in $stackFrame$,
 * as well as all their ranks, in the BWT of the $stringID$-th string of $stackFrame$. The 
 * results are written in the data structures given in input. If $W$ does not occur in the
 * $stringID$-th string, output values are undefined.
 *  
 * @param rightExtensionBitmap Output value. The $i$-th LSB is set to one iff character 
 * $i$ (0=#, 1=A, 2=C, 3=G, 4=T, 5=N) is a right-extension of $W$ in the $stringID$-th  
 * string.
 *
 * @param rankPoints Output array of length at least 7. Let $[i..j]$ be the 
 * BWT interval of $W$ in the $stringID$-th string. The array contains the sorted list of 
 * positions $i-1,e_1,e_2,...,e_k$, where $e_p$ is the last position of every sub-interval 
 * of $[i..j]$ induced by a right-extension of $W$, and $k<=7$ is returned inside 
 * $npref_query_points$.
 *
 * @param rankValues Output array of length at least 28. It consists of at most 7 
 * blocks of 4 elements each. The $j$-th element of block $i$ contains the number of 
 * occurrences of character $j$ (0=A, 1=C, 2=G, 3=T) up to the $i$-th element of 
 * $rankPoints$ (included).
 *
 * @param rankValuesN Output array of length at least 7. Position $i$ contains 
 * the number of occurrences of character N, up to the $i$-th element of 
 * $rankPoints$ (included).
 *
 * @param containsSharp Output value. True iff the BWT interval of $W$ contains the sharp.
 */
static void getRanksOfRightExtensions(const StackFrame_t *stackFrame, uint8_t stringID, const BwtIndex_t *bwt, uint8_t *rightExtensionBitmap, uint64_t rankPoints[7], uint8_t *npref_query_points, uint64_t rankValues[28], uint64_t rankValuesN[7], uint8_t *containsSharp) {
	uint8_t i, j;
	uint64_t count;
	if (stackFrame->frequency[stringID]==0) return;
	
	*rightExtensionBitmap=0;
	j=0;
	rankPoints[j]=stackFrame->bwtStart[stringID]-1;
	for (i=0; i<=5; i++) {
		count=stackFrame->frequency_right[stringID][i];
		if (count>0) {
			*rightExtensionBitmap|=1<<i;
			j++;
			rankPoints[j]=rankPoints[j-1]+count;
		}
	}
	*containsSharp=(bwt->sharpPosition>=rankPoints[0]+1)&&(bwt->sharpPosition<=rankPoints[j]);
	*npref_query_points=j+1;
	if (rankPoints[0]+1==0) {
		for (i=0; i<4; i++) rankValues[i]=0;
		DNA5_multipe_char_pref_counts(bwt->indexedBWT,&rankPoints[1],*npref_query_points-1,&rankValues[4]);
	}
	else {
		DNA5_multipe_char_pref_counts(bwt->indexedBWT,rankPoints,*npref_query_points,rankValues);
	}
	for (i=0; i<*npref_query_points; i++) {
		count=rankPoints[i]+1;
		for (j=0; j<4; j++) count-=rankValues[(i<<2)+j];
		rankValuesN[i]=count;
	}
}


/**
 * Sets all fields of $rightMaximalString$ based on $stackFrame$.
 * See function $getRanksOfRightExtensions()$ for details on the input parameters.
 * If $rightMaximalString$ does not occur in an input string, the corresponding values are
 * undefined.
 *
 * Remark: the procedure assumes that $rightMaximalString->frequency_leftRight$ contains
 * only zeros.
 *
 * @param nRightExtensions cell $a \in [0..5]$ contains the number of (at most 6) distinct 
 * right-extensions of string $aW$; the array is assumed to be initialized to all zeros.
 *
 * @param intervalSize cell $a \in [0..5]$ contains the size of the BWT interval of $aW$;
 * the array is assumed to be initialized to all zeros.
 */
static void buildCallbackState(RightMaximalString_t *rightMaximalString, const StackFrame_t *stackFrame, BwtIndex_t *bwt[N_ITERATOR_STRINGS], const uint8_t rightExtensionBitmap[N_ITERATOR_STRINGS], const uint64_t rankPoints[N_ITERATOR_STRINGS][7], const uint8_t npref_query_points[N_ITERATOR_STRINGS], const uint64_t rankValues[N_ITERATOR_STRINGS][28], const uint64_t rankValuesN[N_ITERATOR_STRINGS][7], uint8_t nRightExtensions[N_ITERATOR_STRINGS][6], uint64_t intervalSize[N_ITERATOR_STRINGS][6]) {
	uint8_t i, j, k;
	uint8_t containsSharpTmp, extensionExists, leftExtensionBitmap, stringID;
	
	rightMaximalString->length=stackFrame->length;
	rightMaximalString->firstCharacter=stackFrame->firstCharacter;
	for (stringID=0; stringID<N_ITERATOR_STRINGS; stringID++) {
		rightMaximalString->frequency[stringID]=stackFrame->frequency[stringID];
		if (stackFrame->frequency[stringID]==0) continue;
		rightMaximalString->bwtStart[stringID]=stackFrame->bwtStart[stringID];
		rightMaximalString->nRightExtensions[stringID]=npref_query_points[stringID]-1;
		rightMaximalString->rightExtensionBitmap[stringID]=rightExtensionBitmap[stringID];
		for (i=0; i<=3; i++) rightMaximalString->bwtStart_left[stringID][i]=bwt[stringID]->cArray[i]+rankValues[stringID][i]+1;
		if (bwt[stringID]->sharpPosition<(rankPoints[stringID][0]+1)) {
			// We subtract one because character A, and not the actual sharp, is assigned
			// to position $sharpPosition$ in the BWT.
			rightMaximalString->bwtStart_left[stringID][0]--;
		}
		rightMaximalString->bwtStart_left[stringID][4]=bwt[stringID]->cArray[4]+rankValuesN[stringID][0]+1;
	
		// Computing the frequencies of all combinations of left and right extensions
		j=0; leftExtensionBitmap=0; nRightExtensions[stringID][0]=1; intervalSize[stringID][0]=1;
		for (i=0; i<=5; i++) {  // For every right-extension
			if ((rightExtensionBitmap[stringID]&(1<<i))==0) continue;
			j++;
			// Left-extension by #
			containsSharpTmp=((bwt[stringID]->sharpPosition>=(rankPoints[stringID][j-1]+1))&&(bwt[stringID]->sharpPosition<=rankPoints[stringID][j]));
			rightMaximalString->frequency_leftRight[stringID][0][i]=containsSharpTmp;
			leftExtensionBitmap|=containsSharpTmp;
			// Left-extension by A
			rightMaximalString->frequency_leftRight[stringID][1][i]=rankValues[stringID][j<<2]-rankValues[stringID][(j-1)<<2]-containsSharpTmp;  // We subtract $containsSharpTmp$ because character A, and not the actual sharp, is assigned to position $sharpPosition$ in the BWT.
			extensionExists=!!rightMaximalString->frequency_leftRight[stringID][1][i];
			leftExtensionBitmap|=extensionExists<<1;
			nRightExtensions[stringID][1]+=extensionExists;
			intervalSize[stringID][1]+=rightMaximalString->frequency_leftRight[stringID][1][i];
			// Left-extension by C,G,T.
			for (k=1; k<=3; k++) {
				rightMaximalString->frequency_leftRight[stringID][k+1][i]=rankValues[stringID][(j<<2)+k]-rankValues[stringID][((j-1)<<2)+k];
				extensionExists=!!rightMaximalString->frequency_leftRight[stringID][k+1][i];
				leftExtensionBitmap|=extensionExists<<(k+1);
				nRightExtensions[stringID][k+1]+=extensionExists;
				intervalSize[stringID][k+1]+=rightMaximalString->frequency_leftRight[stringID][k+1][i];
			}
			// Left-extension by N
			rightMaximalString->frequency_leftRight[stringID][5][i]=rankValuesN[stringID][j]-rankValuesN[stringID][j-1];
			extensionExists=!!rightMaximalString->frequency_leftRight[stringID][5][i];
			leftExtensionBitmap|=extensionExists<<5;
			nRightExtensions[stringID][5]+=extensionExists;
			intervalSize[stringID][5]+=rightMaximalString->frequency_leftRight[stringID][5][i];
		}
		rightMaximalString->leftExtensionBitmap[stringID]=leftExtensionBitmap;
		rightMaximalString->nLeftExtensions[stringID]=0;
		for (i=0; i<=5; i++) rightMaximalString->nLeftExtensions[stringID]+=(leftExtensionBitmap&(1<<i))!=0;
	}
}


/**
 * @return 1 iff the left-extension of $RightMaximalString_t$ by character $b$ is 
 * right-maximal by the current definition, zero otherwise.
 */
static uint8_t isLeftExtensionRightMaximal(uint8_t b, const RightMaximalString_t *rightMaximalString, const uint8_t nRightExtensionsOfLeft[N_ITERATOR_STRINGS][6], uint8_t traversalMaximality) {
	uint8_t i, stringID;
	uint8_t previousChar, previousSharp, previousN;
	
	if (traversalMaximality==0) {
		previousChar=0; previousSharp=0;
		for (stringID=0; stringID<N_ITERATOR_STRINGS; stringID++) {
			if (rightMaximalString->frequency[stringID]==0 || nRightExtensionsOfLeft[stringID][b]==0) continue;
			if (nRightExtensionsOfLeft[stringID][b]>=2) return 1;
			if (rightMaximalString->frequency_leftRight[stringID][b][0]!=0) {
				if (previousChar!=0 || previousSharp!=0) return 1;
				previousSharp=stringID+1;
				break;
			}
			for (i=1; i<=5; i++) {
				if (rightMaximalString->frequency_leftRight[stringID][b][i]!=0) {
					if ((previousChar!=0 && previousChar!=i+1) || previousSharp!=0) return 1;
					previousChar=i+1;
					break;
				}
			}
		}
	}
	else if (traversalMaximality==1) {
		previousChar=0; previousSharp=0; previousN=0;
		for (stringID=0; stringID<N_ITERATOR_STRINGS; stringID++) {
			if (rightMaximalString->frequency[stringID]==0 || nRightExtensionsOfLeft[stringID][b]==0) continue;
			if (nRightExtensionsOfLeft[stringID][b]>=2 || rightMaximalString->frequency_leftRight[stringID][b][5]>=2) return 1;
			if (rightMaximalString->frequency_leftRight[stringID][b][0]!=0) {
				if (previousChar!=0 || previousSharp!=0 || previousN!=0) return 1;
				previousSharp=stringID+1;
				break;
			}
			for (i=1; i<=4; i++) {
				if (rightMaximalString->frequency_leftRight[stringID][b][i]!=0) {
					if ((previousChar!=0 && previousChar!=i+1) || previousSharp!=0 || previousN!=0) return 1;
					previousChar=i+1;
					break;
				}
			}
			if (rightMaximalString->frequency_leftRight[stringID][b][5]!=0) {
				if (previousChar!=0 || previousSharp!=0 || previousN!=0) return 1;
				previousN=stringID+1;
			}
		}
	}
	else if (traversalMaximality==2) {
		previousChar=0;
		for (stringID=0; stringID<N_ITERATOR_STRINGS; stringID++) {
			if (rightMaximalString->frequency[stringID]==0 || nRightExtensionsOfLeft[stringID][b]==0) continue;
			for (i=1; i<=4; i++) {
				if (rightMaximalString->frequency_leftRight[stringID][b][i]!=0) {
					if (previousChar!=0 && previousChar!=i+1) return 1;
					previousChar=i+1;
				}
			}
		}
	}
	return 0;
}


/**
 * Tries to push $aW$ onto $stack$, where a=A has character ID equal to one.
 * 
 * @param stackPointer pointer to the first free frame in the stack; the procedure 
 * increments $stackPointer$ at the end;
 * @return 0 if $AW$ was not pushed on the stack; otherwise, the size of the interval of  
 * $AW$ in the BWT of the concatenation of $N_ITERATOR_STRINGS$ input strings.
 */
static inline uint64_t pushA(const RightMaximalString_t *rightMaximalString, BwtIndex_t *bwt[N_ITERATOR_STRINGS], StackFrame_t **stack, uint64_t *stackSize, uint64_t *stackPointer, const uint64_t length, const uint64_t rankPoints[N_ITERATOR_STRINGS][7], const uint64_t rankValues[N_ITERATOR_STRINGS][28], const uint8_t nRightExtensionsOfLeft[N_ITERATOR_STRINGS][6], const uint64_t intervalSizeOfLeft[N_ITERATOR_STRINGS][6], uint8_t traversalMaximality) {
	uint8_t i, stringID;
	uint8_t containsSharp;
	uint64_t out;
	
	if (!isLeftExtensionRightMaximal(1,rightMaximalString,nRightExtensionsOfLeft,traversalMaximality)) return 0;
	if (*stackPointer>=*stackSize) {
		*stackSize=(*stackSize)<<1;
		*stack=(StackFrame_t *)realloc(*stack,sizeof(StackFrame_t)*(*stackSize));
	}
	(*stack)[*stackPointer].firstCharacter=1;
	(*stack)[*stackPointer].length=length;
	for (stringID=0; stringID<N_ITERATOR_STRINGS; stringID++) {
		containsSharp=bwt[stringID]->sharpPosition<(rankPoints[stringID][0]+1);
		(*stack)[*stackPointer].bwtStart[stringID]=bwt[stringID]->cArray[0]+rankValues[stringID][0]+1-containsSharp;
		(*stack)[*stackPointer].frequency[stringID]=intervalSizeOfLeft[stringID][1];
		for (i=0; i<=5; i++) (*stack)[*stackPointer].frequency_right[stringID][i]=rightMaximalString->frequency_leftRight[stringID][1][i];
	}
	*stackPointer=*stackPointer+1;
	out=0;
	for (stringID=0; stringID<N_ITERATOR_STRINGS; stringID++) out+=intervalSizeOfLeft[stringID][1];
	return out;
}


/**
 * Tries to push $bW$ onto $stack$, where $b \in {C,G,T,N}$.
 *
 * @param b the character ID >=2 of the character to push;
 * @param stackPointer pointer to the first free frame in the stack; the procedure 
 * increments $stackPointer$ at the end;
 * @return 0 if $bW$ was not pushed on the stack; otherwise, the size of the interval of 
 * $bW$ in the BWT of the concatenation of $N_ITERATOR_STRINGS$ input strings.
 */
static inline uint64_t pushNonA(uint8_t b, const RightMaximalString_t *rightMaximalString, BwtIndex_t *bwt[N_ITERATOR_STRINGS], StackFrame_t **stack, uint64_t *stackSize, uint64_t *stackPointer, const uint64_t length, const uint64_t rankPoints[N_ITERATOR_STRINGS][7], const uint64_t rankValues[N_ITERATOR_STRINGS][28], const uint8_t nRightExtensionsOfLeft[N_ITERATOR_STRINGS][6], const uint64_t intervalSizeOfLeft[N_ITERATOR_STRINGS][6], uint8_t traversalMaximality) {
	uint8_t i, stringID;
	uint64_t out;

	if (!isLeftExtensionRightMaximal(b,rightMaximalString,nRightExtensionsOfLeft,traversalMaximality)) return 0;
	if (*stackPointer>=*stackSize) {
		*stackSize=(*stackSize)<<1;
		*stack=(StackFrame_t *)realloc(*stack,sizeof(StackFrame_t)*(*stackSize));
	}
	(*stack)[*stackPointer].firstCharacter=b;
	(*stack)[*stackPointer].length=length;
	for (stringID=0; stringID<N_ITERATOR_STRINGS; stringID++) {
		(*stack)[*stackPointer].bwtStart[stringID]=bwt[stringID]->cArray[b-1]+rankValues[stringID][b-1]+1;
		(*stack)[*stackPointer].frequency[stringID]=intervalSizeOfLeft[stringID][b];
		for (i=0; i<=5; i++) (*stack)[*stackPointer].frequency_right[stringID][i]=rightMaximalString->frequency_leftRight[stringID][b][i];
	}
	*stackPointer=*stackPointer+1;
	out=0;
	for (stringID=0; stringID<N_ITERATOR_STRINGS; stringID++) out+=intervalSizeOfLeft[stringID][b];
	return out;
}


/**
 * Variables for workpackage construction
 */
static GeneralizedIterator_t *workpackages;
static uint8_t workpackageCapacity, nWorkpackages;
static uint8_t workpackageLength;  // String length of a workpackage


/** 
 * Remark: the procedure assumes $iterator->stackPointer$ to be greater than zero.
 */
static void iterate(GeneralizedIterator_t *iterator) {
	const uint64_t MAX_LENGTH = iterator->maxLength;
	uint8_t issueCallback, issuePush;
	uint8_t i;
	uint8_t maxIntervalID, nExplicitWL, stringID;
	uint64_t length, intervalSize, maxIntervalSize;
	RightMaximalString_t rightMaximalString = {0};
	uint64_t rankPoints[N_ITERATOR_STRINGS][7];
	uint64_t rankValues[N_ITERATOR_STRINGS][28];
	uint64_t rankValuesN[N_ITERATOR_STRINGS][7];
	uint8_t nRightExtensionsOfLeft[N_ITERATOR_STRINGS][6];
	uint64_t intervalSizeOfLeft[N_ITERATOR_STRINGS][6];
	uint8_t rightExtensionBitmap[N_ITERATOR_STRINGS];
	uint8_t npref_query_points[N_ITERATOR_STRINGS];
	uint8_t containsSharp[N_ITERATOR_STRINGS];
	
	do {
		// Building workpackages, if needed.
		if (workpackageLength>0 && iterator->stack[iterator->stackPointer-1].length==workpackageLength) {
			if (nWorkpackages==workpackageCapacity) {
				workpackageCapacity+=MY_CEIL(workpackageCapacity*ALLOC_GROWTH_NUM,ALLOC_GROWTH_DENOM);
				workpackages=(GeneralizedIterator_t *)realloc(workpackages,workpackageCapacity*sizeof(GeneralizedIterator_t));
			}
			workpackages[nWorkpackages].id=idGenerator++;
			cloneIterator(iterator,&(workpackages[nWorkpackages]));
			// Stack
			workpackages[nWorkpackages].minStackPointer=iterator->stackPointer;
			// Output values
			workpackages[nWorkpackages].nTraversedNodes=0;
			nWorkpackages++;
			iterator->stackPointer--;
			continue;
		}
		
		iterator->nTraversedNodes++;
		iterator->stackPointer--;
		
		// Computing ranks
		for (stringID=0; stringID<N_ITERATOR_STRINGS; stringID++) getRanksOfRightExtensions(&iterator->stack[iterator->stackPointer],stringID,iterator->BBWT[stringID],&rightExtensionBitmap[stringID],rankPoints[stringID],&npref_query_points[stringID],rankValues[stringID],rankValuesN[stringID],&containsSharp[stringID]);

		// Issuing the callback function on the top of the stack
		memset(rightMaximalString.frequency_leftRight,0,sizeof(rightMaximalString.frequency_leftRight));
		memset(nRightExtensionsOfLeft,0,sizeof(nRightExtensionsOfLeft));
		memset(intervalSizeOfLeft,0,sizeof(intervalSizeOfLeft));
		buildCallbackState(&rightMaximalString,&iterator->stack[iterator->stackPointer],iterator->BBWT,rightExtensionBitmap,rankPoints,npref_query_points,rankValues,rankValuesN,nRightExtensionsOfLeft,intervalSizeOfLeft);
		if (rightMaximalString.length>=iterator->minLength) {
			issueCallback=1;
			for (stringID=0; stringID<N_ITERATOR_STRINGS; stringID++) {
				if (rightMaximalString.frequency[stringID]>iterator->maxFrequency[stringID]) {
					issueCallback=0;
					break;
				}
			}
			if (issueCallback) iterator->SLT_callback(rightMaximalString,iterator->applicationData);
		}
				
		// Pushing $aW$ for $a \in {A,C,G,T}$ only, if it exists and it is right-maximal.
		length=rightMaximalString.length+1;
		if (length>MAX_LENGTH) continue;
		issuePush=1;
		for (stringID=0; stringID<N_ITERATOR_STRINGS; stringID++) {
			if (intervalSizeOfLeft[stringID][1]<iterator->minFrequency[stringID]) {
				issuePush=0;
				break;
			}
		}
		if (issuePush) {
			maxIntervalSize=pushA(&rightMaximalString,iterator->BBWT,&iterator->stack,&iterator->stackSize,&iterator->stackPointer,length,rankPoints,rankValues,nRightExtensionsOfLeft,intervalSizeOfLeft,iterator->traversalMaximality);
			maxIntervalID=0;
			nExplicitWL=!!maxIntervalSize;
		}
		else {
			maxIntervalSize=0;
			maxIntervalID=0;
			nExplicitWL=0;
		}
		for (i=2; i<=4; i++) {
			issuePush=1;
			for (stringID=0; stringID<N_ITERATOR_STRINGS; stringID++) {
				if (intervalSizeOfLeft[stringID][i]<iterator->minFrequency[stringID]) {
					issuePush=0;
					break;
				}
			}
			if (!issuePush) continue;
		    intervalSize=pushNonA(i,&rightMaximalString,iterator->BBWT,&iterator->stack,&iterator->stackSize,&iterator->stackPointer,length,rankPoints,rankValues,nRightExtensionsOfLeft,intervalSizeOfLeft,iterator->traversalMaximality);
			if (!intervalSize) continue;
			if (intervalSize>maxIntervalSize) {
				maxIntervalSize=intervalSize;
				maxIntervalID=nExplicitWL;
			}
			nExplicitWL++;
		}
		if (!nExplicitWL) continue;
		
		// Sorting the new left-extensions, if required.
		if (iterator->traversalOrder==1) {
			if (maxIntervalID) swapStackFrames(&iterator->stack[iterator->stackPointer-nExplicitWL],&iterator->stack[iterator->stackPointer-nExplicitWL+maxIntervalID]);
		}
		else if (iterator->traversalOrder==2) {
			for (i=0; i<nExplicitWL>>1; i++) swapStackFrames(&iterator->stack[iterator->stackPointer-nExplicitWL+i],&iterator->stack[iterator->stackPointer-1-i]);
		}
	} while (iterator->stackPointer>=iterator->minStackPointer);
}


uint64_t iterate_sequential( BwtIndex_t *BWT[], uint64_t minLength, uint64_t maxLength, uint64_t minFrequency[], uint64_t maxFrequency[], uint8_t traversalOrder, uint8_t traversalMaximality,
                             SLT_callback_t SLT_callback, CloneState_t cloneState, MergeState_t mergeState, FinalizeState_t finalizeState, void *applicationData, uint64_t applicationDataSize
				           ) {
   	uint8_t i, stringID;
   	GeneralizedIterator_t iterator;
	
	// Initializing the iterator			   
	iterator=newIterator( BWT,minLength,maxLength,minFrequency,maxFrequency,traversalOrder,traversalMaximality,  
	                      SLT_callback,cloneState,mergeState,finalizeState,applicationData,applicationDataSize
						);
	iterator.stack[0].firstCharacter=0;
	iterator.stack[0].length=0;
	for (stringID=0; stringID<N_ITERATOR_STRINGS; stringID++) {
		iterator.stack[0].bwtStart[stringID]=0;
		iterator.stack[0].frequency_right[stringID][0]=1;
		for (i=1; i<=4; i++) iterator.stack[0].frequency_right[stringID][i]=BWT[stringID]->cArray[i]-BWT[stringID]->cArray[i-1];
		iterator.stack[0].frequency_right[stringID][5]=BWT[stringID]->textLength-BWT[stringID]->cArray[4];
		iterator.stack[0].frequency[stringID]=BWT[stringID]->textLength+1;
	}
	
	// Iterating
	workpackageLength=0;
	iterator.stackPointer=1; iterator.minStackPointer=1;
	iterate(&iterator);
	
	// Deallocating
	iterator_finalize(&iterator);
	return iterator.nTraversedNodes;
}


/**
 * Remark: the procedure uses as workpackages all right-maximal strings of a given length.
 * Using the frequency of strings rather than their length makes the code more complex,
 * without a clear advantage in work balancing.
 */
uint64_t iterate_parallel( BwtIndex_t *BWT[], uint64_t minLength, uint64_t maxLength, uint64_t minFrequency[], uint64_t maxFrequency[], uint8_t traversalOrder, uint8_t traversalMaximality, uint8_t nThreads,
                           SLT_callback_t SLT_callback, CloneState_t cloneState, MergeState_t mergeState, FinalizeState_t finalizeState, void *applicationData, uint64_t applicationDataSize
 				         ) {
	const uint8_t N_WORKPACKAGES = nThreads*N_WORKPACKAGES_RATE;
	uint8_t i, stringID;
	GeneralizedIterator_t iterator;
	
	workpackageCapacity=N_WORKPACKAGES;
	workpackages=(GeneralizedIterator_t *)malloc(workpackageCapacity*sizeof(GeneralizedIterator_t));
	workpackageLength=(uint8_t)ceil(log2(N_WORKPACKAGES)/log2(DNA5_alphabet_size));
	nWorkpackages=0;
	
	// First traversal (sequential): building workpackages.
	iterator=newIterator( BWT,minLength,maxLength,minFrequency,maxFrequency,traversalOrder,traversalMaximality,
	                      SLT_callback,cloneState,mergeState,finalizeState,applicationData,applicationDataSize
						);
	iterator.stack[0].firstCharacter=0;
	iterator.stack[0].length=0;
	for (stringID=0; stringID<N_ITERATOR_STRINGS; stringID++) {
		iterator.stack[0].bwtStart[stringID]=0;
		iterator.stack[0].frequency_right[stringID][0]=1;
		for (i=1; i<=4; i++) iterator.stack[0].frequency_right[stringID][i]=BWT[stringID]->cArray[i]-BWT[stringID]->cArray[i-1];
		iterator.stack[0].frequency_right[stringID][5]=BWT[stringID]->textLength-BWT[stringID]->cArray[4];
		iterator.stack[0].frequency[stringID]=BWT[stringID]->textLength+1;
	}
	iterator.stackPointer=1; iterator.minStackPointer=1;
	iterate(&iterator);
	if (iterator.maxLength<workpackageLength) return iterator.nTraversedNodes;

	// Second traversal (parallel): main traversal.
	workpackageLength=0;
#pragma omp parallel num_threads(nThreads)
#pragma omp for schedule(dynamic)
	for (i=0; i<nWorkpackages; i++) iterate(&workpackages[i]);
	
	// Merging partial results
	for (i=0; i<nWorkpackages; i++) mergeIterator(&workpackages[i],&iterator);
	
	// Finalizing
	for (i=0; i<nWorkpackages; i++) iterator_finalize(&workpackages[i]);
	iterator_finalize(&iterator);
	free(workpackages);
	return iterator.nTraversedNodes;
}
