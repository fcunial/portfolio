/**
 * @author Djamal Belazzougui, Fabio Cunial
 */
#include <stdio.h>
#include <string.h>
#include <math.h>
#include "SLT_single_string.h"
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
	uint64_t bwtStart;
	uint64_t frequency;
	uint8_t firstCharacter;
	uint64_t frequency_right[6];  // 0=#, 1=A, 2=C, 3=G, 4=T, 5=N.
} StackFrame_t;


/**
 * Instance of an iterator of all right-maximal substrings of one input string. 
 * It is based just on the BWT of the forward string (i.e. it does not use a bidirectional
 * index).
 */
typedef struct {
	// Unique ID of this instance
	uint8_t id;
	
	// BWT
	BwtIndex_t *BBWT;
	
	// Stack
	StackFrame_t *stack;
	uint64_t stackSize;  // In frames
	uint64_t stackPointer;  // Pointer to the first free frame on the stack
	uint64_t minStackPointer;  // Iteration stops when $stackPointer<minStackPointer$.
	
	// Input parameters
	uint64_t minLength;  // Minimum length of a substring to be enumerated
	uint64_t maxLength;  // Maximum length of a substring to be enumerated
	uint64_t minFrequency;  // Minimum frequency of a substring to be enumerated
	uint64_t maxFrequency;  // Maximum frequency of a substring to be enumerated
	uint8_t traversalOrder;
	uint8_t traversalMaximality;
	
	// Output values
	uint64_t nTraversedNodes;  // Total number of ST nodes traversed
	
	// Application state
	SLT_callback_t SLT_callback;  // Callback function
	CloneState_t cloneState;
	MergeState_t mergeState;
	FinalizeState_t finalizeState;
	void *applicationData;  // Memory area managed by the callback function
	uint64_t applicationDataSize;  // In bytes
} UnaryIterator_t;


UnaryIterator_t newIterator( BwtIndex_t *BBWT, 
							 uint64_t minLength, uint64_t maxLength, uint64_t minFrequency, uint64_t maxFrequency, uint8_t traversalOrder, uint8_t traversalMaximality,
                             SLT_callback_t SLT_callback, CloneState_t cloneState, MergeState_t mergeState, FinalizeState_t finalizeState, void *applicationData, uint64_t applicationDataSize
						   ) {
	UnaryIterator_t iterator;
	
	// Unique ID of this instance
	iterator.id=idGenerator++;
	
	// BWT
	iterator.BBWT=BBWT;
	
	// Stack
	iterator.stack=(StackFrame_t *)malloc((1+MIN_SLT_STACK_SIZE)*sizeof(StackFrame_t));
	iterator.stackSize=MIN_SLT_STACK_SIZE;
	iterator.stackPointer=0;
	iterator.minStackPointer=0;
	
	// Input parameters
	iterator.minLength=minLength;
	iterator.maxLength=maxLength;
	iterator.minFrequency=minFrequency;
	iterator.maxFrequency=maxFrequency;
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
static inline void cloneIterator(UnaryIterator_t *from, UnaryIterator_t *to) {
	// Unique ID of this instance
	// Unchanged
	
	// BWT
	to->BBWT=from->BBWT;
	
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
	to->minFrequency=from->minFrequency;
	to->maxFrequency=from->maxFrequency;
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
static inline void mergeIterator(UnaryIterator_t *from, UnaryIterator_t *to) {
	// Output values
	to->nTraversedNodes+=from->nTraversedNodes;
	
	// Application state
	to->mergeState(from->applicationData,to->applicationData);
}


/**
 * Frees the memory owned by $iterator$, sets to NULL all pointers, and notifies the 
 * application by issuing the $finalizeState()$ callback.
 */
void iterator_finalize(UnaryIterator_t *iterator) {
	// Application state
	iterator->finalizeState(iterator->applicationData);
	iterator->applicationData=NULL;
	iterator->SLT_callback=NULL;
	iterator->cloneState=NULL;
	iterator->mergeState=NULL;
	iterator->finalizeState=NULL;
	
	// BWT
	iterator->BBWT=NULL;
	
	// Stack
	free(iterator->stack);
	iterator->stack=NULL;
}




// ------------------------------------ ITERATION ----------------------------------------

static inline void swapStackFrames(StackFrame_t *SLT_stack_item1, StackFrame_t *SLT_stack_item2) {
	SLT_stack_item1->length^=SLT_stack_item2->length;
	SLT_stack_item1->bwtStart^=SLT_stack_item2->bwtStart;
	SLT_stack_item1->frequency^=SLT_stack_item2->frequency;
	SLT_stack_item1->firstCharacter^=SLT_stack_item2->firstCharacter;

	SLT_stack_item2->length^=SLT_stack_item1->length;
	SLT_stack_item2->bwtStart^=SLT_stack_item1->bwtStart;
	SLT_stack_item2->frequency^=SLT_stack_item1->frequency;
	SLT_stack_item2->firstCharacter^=SLT_stack_item1->firstCharacter;

	SLT_stack_item1->length^=SLT_stack_item2->length;
	SLT_stack_item1->bwtStart^=SLT_stack_item2->bwtStart;
	SLT_stack_item1->frequency^=SLT_stack_item2->frequency;
	SLT_stack_item1->firstCharacter^=SLT_stack_item2->firstCharacter;

	SLT_stack_item1->frequency_right[0]^=SLT_stack_item2->frequency_right[0];
	SLT_stack_item1->frequency_right[1]^=SLT_stack_item2->frequency_right[1];
	SLT_stack_item1->frequency_right[2]^=SLT_stack_item2->frequency_right[2];
	SLT_stack_item1->frequency_right[3]^=SLT_stack_item2->frequency_right[3];
	SLT_stack_item1->frequency_right[4]^=SLT_stack_item2->frequency_right[4];
	SLT_stack_item1->frequency_right[5]^=SLT_stack_item2->frequency_right[5];

	SLT_stack_item2->frequency_right[0]^=SLT_stack_item1->frequency_right[0];
	SLT_stack_item2->frequency_right[1]^=SLT_stack_item1->frequency_right[1];
	SLT_stack_item2->frequency_right[2]^=SLT_stack_item1->frequency_right[2];
	SLT_stack_item2->frequency_right[3]^=SLT_stack_item1->frequency_right[3];
	SLT_stack_item2->frequency_right[4]^=SLT_stack_item1->frequency_right[4];
	SLT_stack_item2->frequency_right[5]^=SLT_stack_item1->frequency_right[5];

	SLT_stack_item1->frequency_right[0]^=SLT_stack_item2->frequency_right[0];
	SLT_stack_item1->frequency_right[1]^=SLT_stack_item2->frequency_right[1];
	SLT_stack_item1->frequency_right[2]^=SLT_stack_item2->frequency_right[2];
	SLT_stack_item1->frequency_right[3]^=SLT_stack_item2->frequency_right[3];
	SLT_stack_item1->frequency_right[4]^=SLT_stack_item2->frequency_right[4];
	SLT_stack_item1->frequency_right[5]^=SLT_stack_item2->frequency_right[5];
}


/**
 * Computes all distinct right-extensions $Wa$ of the string $W$ encoded in $stackFrame$,
 * as well as all their ranks. The results are written in the data structures given in 
 * input.
 *  
 * @param rightExtensionBitmap Output value. The $i$-th LSB is set to one iff character 
 * $i$ (0=#, 1=A, 2=C, 3=G, 4=T, 5=N) is a right-extension of $W$.
 *
 * @param rankPoints Output array of length at least 7. Let $[i..j]$ be the 
 * BWT interval of $W$. The array contains the sorted list of positions $i-1,e_1,e_2,...,
 * e_k$, where $e_p$ is the last position of every sub-interval of $[i..j]$ induced by a 
 * right-extension of $W$, and $k<=7$ is returned inside $npref_query_points$.
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
static void getRanksOfRightExtensions(const StackFrame_t *stackFrame, const BwtIndex_t *bwt, uint8_t *rightExtensionBitmap, uint64_t *rankPoints, uint8_t *npref_query_points, uint64_t *rankValues, uint64_t *rankValuesN, uint8_t *containsSharp) {
	uint8_t i, j;
	uint64_t count;
	
	*rightExtensionBitmap=0;
	j=0;
	rankPoints[j]=stackFrame->bwtStart-1;
	for (i=0; i<=5; i++) {
		count=stackFrame->frequency_right[i];
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
static void buildCallbackState(RightMaximalString_t *rightMaximalString, const StackFrame_t *stackFrame, const BwtIndex_t *bwt, const uint8_t rightExtensionBitmap, const uint64_t *rankPoints, const uint8_t npref_query_points, const uint64_t *rankValues, const uint64_t *rankValuesN, const uint8_t containsSharp, uint8_t *nRightExtensions, uint64_t *intervalSize) {
	uint8_t i, j, k;
	uint8_t containsSharpTmp, extensionExists, leftExtensionBitmap;
	
	rightMaximalString->length=stackFrame->length;
	rightMaximalString->bwtStart=stackFrame->bwtStart;
	rightMaximalString->frequency=stackFrame->frequency;
	rightMaximalString->firstCharacter=stackFrame->firstCharacter;
	rightMaximalString->nRightExtensions=npref_query_points-1;
	rightMaximalString->rightExtensionBitmap=rightExtensionBitmap;
	for (i=0; i<=3; i++) rightMaximalString->bwtStart_left[i]=bwt->cArray[i]+rankValues[i]+1;
	if (bwt->sharpPosition<(rankPoints[0]+1)) {
		// We subtract one because character A, and not the actual sharp, is assigned
		// to position $sharpPosition$ in the BWT.
		rightMaximalString->bwtStart_left[0]--;
	}
	rightMaximalString->bwtStart_left[4]=bwt->cArray[4]+rankValuesN[0]+1;
	
	// Computing the frequencies of all combinations of left and right extensions
	j=0; leftExtensionBitmap=0; nRightExtensions[0]=1; intervalSize[0]=1;
	for (i=0; i<=5; i++) {  // For every right-extension
		if ((rightExtensionBitmap&(1<<i))==0) continue;
		j++;
		// Left-extension by #
		containsSharpTmp=((bwt->sharpPosition>=(rankPoints[j-1]+1))&&(bwt->sharpPosition<=rankPoints[j]));
		rightMaximalString->frequency_leftRight[0][i]=containsSharpTmp;
		leftExtensionBitmap|=containsSharpTmp;
		// Left-extension by A
		rightMaximalString->frequency_leftRight[1][i]=rankValues[j<<2]-rankValues[(j-1)<<2]-containsSharpTmp;  // We subtract $containsSharpTmp$ because character A, and not the actual sharp, is assigned to position $sharpPosition$ in the BWT.
		extensionExists=!!rightMaximalString->frequency_leftRight[1][i];
		leftExtensionBitmap|=extensionExists<<1;
		nRightExtensions[1]+=extensionExists;
		intervalSize[1]+=rightMaximalString->frequency_leftRight[1][i];
		// Left-extension by C,G,T.
		for (k=1; k<=3; k++) {
			rightMaximalString->frequency_leftRight[k+1][i]=rankValues[(j<<2)+k]-rankValues[((j-1)<<2)+k];
			extensionExists=!!rightMaximalString->frequency_leftRight[k+1][i];
			leftExtensionBitmap|=extensionExists<<(k+1);
			nRightExtensions[k+1]+=extensionExists;
			intervalSize[k+1]+=rightMaximalString->frequency_leftRight[k+1][i];
		}
		// Left-extension by N
		rightMaximalString->frequency_leftRight[5][i]=rankValuesN[j]-rankValuesN[j-1];
		extensionExists=!!rightMaximalString->frequency_leftRight[5][i];
		leftExtensionBitmap|=extensionExists<<5;
		nRightExtensions[5]+=extensionExists;
		intervalSize[5]+=rightMaximalString->frequency_leftRight[5][i];
	}
	rightMaximalString->leftExtensionBitmap=leftExtensionBitmap;
	rightMaximalString->nLeftExtensions=0;
	for (i=0; i<=5; i++) rightMaximalString->nLeftExtensions+=(leftExtensionBitmap&(1<<i))!=0;
}


/**
 * @return 1 if the left-extension of $RightMaximalString_t$ by character $b$ is 
 * right-maximal by the current definition, zero otherwise.
 */
static inline uint8_t isLeftExtensionRightMaximal(uint8_t b, const RightMaximalString_t *rightMaximalString, const uint8_t *nRightExtensionsOfLeft, uint8_t traversalMaximality) {
	uint8_t i, nRightExtensions;
	
	if (traversalMaximality==0) {
		if (nRightExtensionsOfLeft[b]<2) return 0;
	}
	else if (traversalMaximality==1) {
		if (nRightExtensionsOfLeft[b]<2 && rightMaximalString->frequency_leftRight[b][5]<2) return 0;
	}
	else if (traversalMaximality==2) {
		nRightExtensions=0;
		for (i=1; i<=4; i++) nRightExtensions+=!!rightMaximalString->frequency_leftRight[b][i];
		if (nRightExtensions<2) return 0;
	}
	return 1;
}


/**
 * Tries to push $aW$ onto $stack$, where a=A has character ID equal to one.
 * 
 * @param stackPointer pointer to the first free frame in the stack; the procedure 
 * increments $stackPointer$ at the end;
 * @return 0 if $AW$ was not pushed on the stack; otherwise, the size of the BWT interval
 * of $AW$.
 */
static inline uint64_t pushA(const RightMaximalString_t *rightMaximalString, const BwtIndex_t *bwt, StackFrame_t **stack, uint64_t *stackSize, uint64_t *stackPointer, const uint64_t length, const uint64_t *rankPoints, const uint64_t *rankValues, const uint8_t *nRightExtensionsOfLeft, const uint64_t *intervalSizeOfLeft, uint8_t traversalMaximality) {
	uint8_t i, containsSharp;
	
	if (!isLeftExtensionRightMaximal(1,rightMaximalString,nRightExtensionsOfLeft,traversalMaximality)) return 0;
	if (*stackPointer>=*stackSize) {
		*stackSize=(*stackSize)<<1;
		*stack=(StackFrame_t *)realloc(*stack,sizeof(StackFrame_t)*(*stackSize));
	}
	(*stack)[*stackPointer].firstCharacter=1;
	(*stack)[*stackPointer].length=length;
	containsSharp=bwt->sharpPosition<(rankPoints[0]+1);
	(*stack)[*stackPointer].bwtStart=bwt->cArray[0]+rankValues[0]+1-containsSharp;
	(*stack)[*stackPointer].frequency=intervalSizeOfLeft[1];
	for (i=0; i<=5; i++) (*stack)[*stackPointer].frequency_right[i]=rightMaximalString->frequency_leftRight[1][i];
	*stackPointer=*stackPointer+1;
	return intervalSizeOfLeft[1];
}


/**
 * Tries to push $bW$ onto $stack$, where $b \in {C,G,T,N}$.
 *
 * @param b the character ID >=2 of the character to push;
 * @param stackPointer pointer to the first free frame in the stack; the procedure 
 * increments $stackPointer$ at the end;
 * @return 0 if $bW$ was not pushed on the stack; otherwise, the size of the BWT interval
 * of $bW$.
 */
static inline uint64_t pushNonA(uint8_t b, const RightMaximalString_t *rightMaximalString, const BwtIndex_t *bwt, StackFrame_t **stack, uint64_t *stackSize, uint64_t *stackPointer, const uint64_t length, const uint64_t *rankPoints, const uint64_t *rankValues, const uint8_t *nRightExtensionsOfLeft, const uint64_t *intervalSizeOfLeft, uint8_t traversalMaximality) {
	uint8_t i;

	if (!isLeftExtensionRightMaximal(b,rightMaximalString,nRightExtensionsOfLeft,traversalMaximality)) return 0;
	if (*stackPointer>=*stackSize) {
		*stackSize=(*stackSize)<<1;
		*stack=(StackFrame_t *)realloc(*stack,sizeof(StackFrame_t)*(*stackSize));
	}
	(*stack)[*stackPointer].firstCharacter=b;
	(*stack)[*stackPointer].length=length;
	(*stack)[*stackPointer].bwtStart=bwt->cArray[b-1]+rankValues[b-1]+1;
	(*stack)[*stackPointer].frequency=intervalSizeOfLeft[b];
	for (i=0; i<=5; i++) (*stack)[*stackPointer].frequency_right[i]=rightMaximalString->frequency_leftRight[b][i];
	*stackPointer=*stackPointer+1;
	return intervalSizeOfLeft[b];
}


/**
 * Variables for workpackage construction
 */
static UnaryIterator_t *workpackages;
static uint8_t workpackageCapacity, nWorkpackages;
static uint8_t workpackageLength;  // String length of a workpackage


/** 
 * Remark: the procedure assumes $iterator->stackPointer$ to be greater than zero.
 */
static void iterate(UnaryIterator_t *iterator) {
	const BwtIndex_t *BWT = iterator->BBWT;
	const uint64_t MAX_LENGTH = iterator->maxLength;
	uint8_t i;
	uint8_t maxIntervalID, nExplicitWL, containsSharp, rightExtensionBitmap, npref_query_points;
	uint64_t length, intervalSize, maxIntervalSize;
	RightMaximalString_t rightMaximalString = {0};
	uint64_t rankPoints[7];
	uint64_t rankValues[28];
	uint64_t rankValuesN[7];
	uint8_t nRightExtensionsOfLeft[6];
	uint64_t intervalSizeOfLeft[6];
	
	do {
		// Building workpackages, if needed.
		if (workpackageLength>0 && iterator->stack[iterator->stackPointer-1].length==workpackageLength) {
			if (nWorkpackages==workpackageCapacity) {
				workpackageCapacity+=MY_CEIL(workpackageCapacity*ALLOC_GROWTH_NUM,ALLOC_GROWTH_DENOM);
				workpackages=(UnaryIterator_t *)realloc(workpackages,workpackageCapacity*sizeof(UnaryIterator_t));
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
		getRanksOfRightExtensions(&iterator->stack[iterator->stackPointer],BWT,&rightExtensionBitmap,rankPoints,&npref_query_points,rankValues,rankValuesN,&containsSharp);

		// Issuing the callback function on the top of the stack
		memset(rightMaximalString.frequency_leftRight,0,sizeof(rightMaximalString.frequency_leftRight));
		memset(nRightExtensionsOfLeft,0,sizeof(nRightExtensionsOfLeft));
		memset(intervalSizeOfLeft,0,sizeof(intervalSizeOfLeft));
		buildCallbackState(&rightMaximalString,&iterator->stack[iterator->stackPointer],BWT,rightExtensionBitmap,rankPoints,npref_query_points,rankValues,rankValuesN,containsSharp,nRightExtensionsOfLeft,intervalSizeOfLeft);
		if (rightMaximalString.length>=iterator->minLength && rightMaximalString.frequency<=iterator->maxFrequency) iterator->SLT_callback(rightMaximalString,iterator->applicationData);
				
		// Pushing $aW$ for $a \in {A,C,G,T}$ only, if it exists and it is right-maximal.
		length=rightMaximalString.length+1;
		if (length>MAX_LENGTH) continue;
		if (intervalSizeOfLeft[1]>=iterator->minFrequency) {
			maxIntervalSize=pushA(&rightMaximalString,BWT,&iterator->stack,&iterator->stackSize,&iterator->stackPointer,length,rankPoints,rankValues,nRightExtensionsOfLeft,intervalSizeOfLeft,iterator->traversalMaximality);
			maxIntervalID=0;
			nExplicitWL=!!maxIntervalSize;
		}
		else {
			maxIntervalSize=0;
			maxIntervalID=0;
			nExplicitWL=0;
		}
		for (i=2; i<=4; i++) {
			if (intervalSizeOfLeft[i]<iterator->minFrequency) continue;
		    intervalSize=pushNonA(i,&rightMaximalString,BWT,&iterator->stack,&iterator->stackSize,&iterator->stackPointer,length,rankPoints,rankValues,nRightExtensionsOfLeft,intervalSizeOfLeft,iterator->traversalMaximality);
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


uint64_t iterate_sequential( BwtIndex_t *BWT, uint64_t minLength, uint64_t maxLength, uint64_t minFrequency, uint64_t maxFrequency, uint8_t traversalOrder, uint8_t traversalMaximality,
                             SLT_callback_t SLT_callback, CloneState_t cloneState, MergeState_t mergeState, FinalizeState_t finalizeState, void *applicationData, uint64_t applicationDataSize
				           ) {
   	uint8_t i;
   	UnaryIterator_t iterator;
	
	// Initializing the iterator			   
	iterator=newIterator( BWT,minLength,maxLength,minFrequency,maxFrequency,traversalOrder,traversalMaximality,  
	                      SLT_callback,cloneState,mergeState,finalizeState,applicationData,applicationDataSize
						);
	iterator.stack[0].firstCharacter=0;
	iterator.stack[0].length=0;
	iterator.stack[0].bwtStart=0;
	iterator.stack[0].frequency_right[0]=1;
	for (i=1; i<=4; i++) iterator.stack[0].frequency_right[i]=BWT->cArray[i]-BWT->cArray[i-1];
	iterator.stack[0].frequency_right[5]=BWT->textLength-BWT->cArray[4];
	iterator.stack[0].frequency=BWT->textLength+1;
	
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
uint64_t iterate_parallel( BwtIndex_t *BWT, uint64_t minLength, uint64_t maxLength, uint64_t minFrequency, uint64_t maxFrequency, uint8_t traversalOrder, uint8_t traversalMaximality, uint8_t nThreads,
                           SLT_callback_t SLT_callback, CloneState_t cloneState, MergeState_t mergeState, FinalizeState_t finalizeState, void *applicationData, uint64_t applicationDataSize
 				         ) {
	const uint8_t N_WORKPACKAGES = nThreads*N_WORKPACKAGES_RATE;
	uint8_t i;
	UnaryIterator_t iterator;
	
	workpackageCapacity=N_WORKPACKAGES;
	workpackages=(UnaryIterator_t *)malloc(workpackageCapacity*sizeof(UnaryIterator_t));
	workpackageLength=(uint8_t)ceil(log2(N_WORKPACKAGES)/log2(DNA5_alphabet_size));
	nWorkpackages=0;
	
	// First traversal (sequential): building workpackages.
	iterator=newIterator( BWT,minLength,maxLength,minFrequency,maxFrequency,traversalOrder,traversalMaximality,
	                      SLT_callback,cloneState,mergeState,finalizeState,applicationData,applicationDataSize
						);
	iterator.stack[0].firstCharacter=0;
	iterator.stack[0].length=0;
	iterator.stack[0].bwtStart=0;
	iterator.stack[0].frequency_right[0]=1;
	for (i=1; i<=4; i++) iterator.stack[0].frequency_right[i]=BWT->cArray[i]-BWT->cArray[i-1];
	iterator.stack[0].frequency_right[5]=BWT->textLength-BWT->cArray[4];
	iterator.stack[0].frequency=BWT->textLength+1;
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
