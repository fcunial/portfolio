/**
 * @author Djamal Belazzougui, Fabio Cunial
 */
#include <stdlib.h>
#include <stdio.h>
#include "indexed_DNA5_seq.h"
#include "../io/bits.h"


/**
 * We assume that memory is allocated in chunks called \emph{pages}, and that a page 
 * is big enough to contain a pointer to memory.
 */
#ifndef BYTES_PER_PAGE
#define BYTES_PER_PAGE 8
#endif
#ifndef BITS_PER_PAGE
#define BITS_PER_PAGE (BYTES_PER_PAGE*8)
#endif

/**
 * Since the alphabet has size 5, rather than packing each character into a word using 3
 * bits, it is more space-efficient to encode a sequence of X consecutive characters as a 
 * chunk of Y bits, where $Y=ceil(log2(5^X))$, which represents the sequence as a number 
 * in base 5. We call \emph{miniblock} such a chunk of Y bits that encodes X characters. 
 * Function Y(X) is represented in <img src="miniblock-5.pdf">, and its first values are 
 * 3,5,7,10,12,14,17,19,21,24. We use X=3, Y=7 in the code, since this already achieves 
 * 2.3333 bits per character.
 *
 * Figure <img src="miniblock-3-14.pdf"> shows Y(X) for other alphabet sizes.
 */
#ifndef CHARS_PER_MINIBLOCK
#define CHARS_PER_MINIBLOCK 3
#endif
#ifndef BITS_PER_MINIBLOCK
#define BITS_PER_MINIBLOCK 7
#endif
#ifndef MINIBLOCK_MASK
#define MINIBLOCK_MASK 127  // The seven LSBs set to all ones
#endif
#ifndef MINIBLOCKS_PER_WORD
#define MINIBLOCKS_PER_WORD ((BITS_PER_WORD)/(BITS_PER_MINIBLOCK))
#endif
#ifndef BITS_IN_MINIBLOCKS_PER_WORD
#define BITS_IN_MINIBLOCKS_PER_WORD ((BITS_PER_MINIBLOCK)*(MINIBLOCKS_PER_WORD))
#endif

/**
 * A \emph{sub-block} is a group of 32 consecutive miniblocks, spanning seven 32-bit  
 * words, such that the 32-th miniblock ends at the end of the seventh word. 
 * Because of this periodicity, we use sub-blocks as units of computation.
 */
#ifndef MINIBLOCKS_PER_SUBBLOCK
#define MINIBLOCKS_PER_SUBBLOCK 32
#endif
#ifndef WORDS_PER_SUBBLOCK
#define WORDS_PER_SUBBLOCK 7
#endif
#ifndef CHARS_PER_SUBBLOCK
#define CHARS_PER_SUBBLOCK ((MINIBLOCKS_PER_SUBBLOCK)*(CHARS_PER_MINIBLOCK))
#endif

/**
 * A \emph{block} is a group of X sub-blocks (the payload), prefixed by a header that 
 * contains the counts of all characters in {A,C,G,T} before the block. X controls a 
 * space/time tradeoff for rank operations; we set it to an arbitrary value here.
 */
#ifndef BLOCK_HEADER_SIZE_IN_WORDS
#define BLOCK_HEADER_SIZE_IN_WORDS 8
#endif
#ifndef BLOCK_HEADER_SIZE_IN_BITS
#define BLOCK_HEADER_SIZE_IN_BITS (((BLOCK_HEADER_SIZE_IN_WORDS)*(BITS_PER_WORD)))
#endif
#ifndef WORDS_PER_BLOCK  // Including the header
#define WORDS_PER_BLOCK 36
#endif
#ifndef BYTES_PER_BLOCK  // Including the header
#define BYTES_PER_BLOCK (((WORDS_PER_BLOCK)*(BYTES_PER_WORD)))
#endif
#ifndef BITS_PER_BLOCK  // Including the header
#define BITS_PER_BLOCK (((WORDS_PER_BLOCK)*(BITS_PER_WORD)))
#endif
#ifndef PAYLOAD_WORDS_PER_BLOCK
#define PAYLOAD_WORDS_PER_BLOCK (((WORDS_PER_BLOCK)-(BLOCK_HEADER_SIZE_IN_WORDS)))
#endif
#ifndef PAYLOAD_BYTES_PER_BLOCK
#define PAYLOAD_BYTES_PER_BLOCK (PAYLOAD_WORDS_PER_BLOCK*BYTES_PER_WORD)
#endif
#ifndef PAYLOAD_BITS_PER_BLOCK
#define PAYLOAD_BITS_PER_BLOCK (PAYLOAD_WORDS_PER_BLOCK*BITS_PER_WORD)
#endif
#ifndef MINIBLOCKS_PER_BLOCK
#define MINIBLOCKS_PER_BLOCK (((PAYLOAD_BITS_PER_BLOCK)/BITS_PER_MINIBLOCK))
#endif
#ifndef CHARS_PER_BLOCK
#define CHARS_PER_BLOCK (((MINIBLOCKS_PER_BLOCK)*(CHARS_PER_MINIBLOCK)))
#endif


/**
 * Lookup tables
 */
extern uint8_t ascii2alphabet[256];
extern uint32_t DNA5_alpha_pows[3];
extern uint32_t miniblock2counts[128];
extern uint32_t miniblock2suffixCounts[128*4];
extern uint32_t miniblock2substringCounts[128*4];


/**
 * Writes the seven LSBs of $value$ into the $miniblockID$-th miniblock.
 */
static inline void DNA5_setMiniblock(uint32_t *restrict index, const uint64_t miniblockID, const uint32_t value) {
	const uint64_t BIT_ID = miniblockID*BITS_PER_MINIBLOCK;
	const uint64_t WORD_ID = BIT_ID/BITS_PER_WORD;
	const uint8_t OFFSET_IN_WORD = BIT_ID%BITS_PER_WORD;
	const uint64_t BLOCK_ID = WORD_ID/PAYLOAD_WORDS_PER_BLOCK;
	uint32_t tmpValue;
	uint64_t wordInIndex = BLOCK_ID*WORDS_PER_BLOCK+BLOCK_HEADER_SIZE_IN_WORDS+(WORD_ID%PAYLOAD_WORDS_PER_BLOCK);

	tmpValue=(value&MINIBLOCK_MASK)<<OFFSET_IN_WORD;
	index[wordInIndex]&=~(MINIBLOCK_MASK<<OFFSET_IN_WORD);
	index[wordInIndex]|=tmpValue;
	if (OFFSET_IN_WORD>BITS_PER_WORD-BITS_PER_MINIBLOCK) {
		wordInIndex++;
		tmpValue=value>>(BITS_PER_WORD-OFFSET_IN_WORD);
		index[wordInIndex]&=ALL_ONES_32<<(BITS_PER_MINIBLOCK-(BITS_PER_WORD-OFFSET_IN_WORD));
		index[wordInIndex]|=tmpValue;
	}
}


/**
 * Returns the value of the $miniblockID$-th miniblock in the seven LSBs of the result.
 */
static inline uint32_t DNA5_getMiniblock(uint32_t *restrict index, uint64_t miniblockID) {
	const uint64_t BIT_ID = miniblockID*BITS_PER_MINIBLOCK;
	const uint64_t WORD_ID = BIT_ID/BITS_PER_WORD;
	const uint8_t OFFSET_IN_WORD = BIT_ID%BITS_PER_WORD;
	const uint64_t BLOCK_ID = WORD_ID/PAYLOAD_WORDS_PER_BLOCK;
	const uint64_t wordInIndex = BLOCK_ID*WORDS_PER_BLOCK+BLOCK_HEADER_SIZE_IN_WORDS+(WORD_ID%PAYLOAD_WORDS_PER_BLOCK);
	uint32_t tmpValue;
	
	tmpValue=index[wordInIndex]>>OFFSET_IN_WORD;
	if (OFFSET_IN_WORD>BITS_PER_WORD-BITS_PER_MINIBLOCK) tmpValue|=index[wordInIndex+1]<<(BITS_PER_WORD-OFFSET_IN_WORD);
	return tmpValue&MINIBLOCK_MASK;
}


/**
 * Remark: the value of $oldPointer$ is stored immediately before the pointer returned in 
 * output.
 */
uint32_t *alignIndex(uint32_t *oldPointer) {
	uint32_t *newPointer = (uint32_t *)( (uint8_t *)oldPointer+(BYTES_PER_PAGE<<1)-((uintptr_t)oldPointer)%BYTES_PER_PAGE );
	*(((uint32_t **)newPointer)-1)=oldPointer;
	return newPointer;
}


/**
 * Uses the pointer written by $alignIndex()$.
 */
void free_basic_DNA5_seq(uint32_t *index) {
	free( *(((uint32_t **)index)-1) );
}


/**
 * The procedure takes into account the partially-used extra space at the beginning of the
 * index, needed to align it to pages (see $alignIndex()$).
 */
uint64_t getIndexSize(const uint64_t textLength) {
	const uint64_t N_BLOCKS = textLength/CHARS_PER_BLOCK;
	const uint64_t REMAINING_CHARS = textLength-N_BLOCKS*CHARS_PER_BLOCK;
	const uint64_t REMAINING_MINIBLOCKS = MY_CEIL(REMAINING_CHARS,CHARS_PER_MINIBLOCK);
	const uint64_t SIZE_IN_BITS = N_BLOCKS*BITS_PER_BLOCK+BLOCK_HEADER_SIZE_IN_BITS+REMAINING_MINIBLOCKS*BITS_PER_MINIBLOCK;
	const uint64_t SIZE_IN_PAGES = MY_CEIL(SIZE_IN_BITS,BITS_PER_PAGE);
	
	return (SIZE_IN_PAGES+2)*BYTES_PER_PAGE+BYTES_PER_BLOCK;
}


/**
 * Sets the $charID$-th character to the two LSBs in $value$.
 */
void DNA5_set_char(uint32_t *restrict index, uint64_t charID, uint8_t value) {
	uint64_t MINIBLOCK_ID = charID/CHARS_PER_MINIBLOCK;
	uint8_t OFFSET_IN_MINIBLOC = charID%CHARS_PER_MINIBLOCK;  // In chars
	uint32_t val = DNA5_getMiniblock(index,MINIBLOCK_ID);

	val+=DNA5_alpha_pows[OFFSET_IN_MINIBLOC]*value;
	DNA5_setMiniblock(index,MINIBLOCK_ID,val);
}


/**
 * Every substring $T[i..i+2]$ of length 3 is transformed into a number 
 * $25*T[i+2] + 5*T[i+1] + 1*T[i]$. At the boundary, $T$ is assumed to be concatenated to 
 * three zeros.
 */
uint32_t *build_basic_DNA5_seq(uint8_t *restrict text, uint64_t textLength, uint64_t *restrict outputSize, uint64_t *restrict characterCount) {
	uint32_t *pointer = NULL;
	uint64_t *pointer64 = NULL;
	uint32_t *index = NULL;
	uint8_t j, charID, miniblock;
	uint64_t i;
	uint64_t miniblockID, nAllocatedBytes;
	uint64_t cumulativeCounts[5];
	
	nAllocatedBytes=getIndexSize(textLength);
	pointer=(uint32_t *)calloc(1,nAllocatedBytes);
	if (pointer==NULL) {
		*outputSize=0;
		return NULL;
	}
	index=alignIndex(pointer);
	for (i=0; i<=4; i++) cumulativeCounts[i]=0;
	miniblockID=0; pointer=index;
	for (i=0; i<textLength; i+=CHARS_PER_MINIBLOCK) {
		// Block header
		if (miniblockID%MINIBLOCKS_PER_BLOCK==0) {
			pointer64=(uint64_t *)pointer;
			for (j=0; j<=3; j++) pointer64[j]=(uint64_t)cumulativeCounts[j];
			pointer+=WORDS_PER_BLOCK;
		}
		// Block payload
		miniblock=0;
		if (i+2<textLength) {
			charID=ascii2alphabet[(uint8_t)text[i+2]];		
			cumulativeCounts[charID]++;
			miniblock+=charID;
			miniblock*=DNA5_alphabet_size;
			charID=ascii2alphabet[(uint8_t)text[i+1]];
			cumulativeCounts[charID]++;
			miniblock+=charID;
			miniblock*=DNA5_alphabet_size;
		}
		else if (i+1<textLength) {
			charID=ascii2alphabet[(uint8_t)text[i+1]];		
			cumulativeCounts[charID]++;
			miniblock+=charID;
			miniblock*=DNA5_alphabet_size;
		}
		charID=ascii2alphabet[(uint8_t)text[i]];
		cumulativeCounts[charID]++;
		miniblock+=charID;
		DNA5_setMiniblock(index,miniblockID,miniblock);
		miniblockID++;
	}
	for (i=0; i<=3; i++) characterCount[i]=cumulativeCounts[i];
	*outputSize=nAllocatedBytes;	
	return index;
}


/**
 * Adds to $count$ the number of occurrences of all characters in A,C,G,T inside the 
 * interval that starts from the beginning of the $fromSubblock$-th sub-block of $block$, 
 * and that ends at the $charInToMiniblock$-th character of the $toMiniblock$-th miniblock
 * of $block$, included (such miniblock might belong to a different sub-block).
 *
 * Remark: the computation proceeds one sub-block at a time. The counts in a sub-block are
 * stored in a single word with binary representation $C_3 C_2 C_1 C_0$, where each $C_i$ 
 * takes 8 bits and is the number of times $i$ occurs inside a sub-block. Since a sub-
 * block contains 32 miniblocks, and each miniblock corresponds to 3 positions of the 
 * text, $C_i$ can be at most 96, so 7 bits suffice.
 *
 * Remark: transforming the loops in this procedure into a single loop with vector  
 * operations (e.g. $_mm256_and_si256$, $_mm256_i32gather_epi32$, $_mm256_srli_epi32$ and  
 * horizontal sum with AVX2) does not make the code faster in practice, and vectorization
 * does not seem to be applied by the compiler, either (from looking at the output of
 * $gcc -fopt-info-optimized-optall$): the only optimizations applied by the compiler seem 
 * to be inlining and loop unrolling.
 *
 * @param block pointer to the first sub-block in the block, i.e. excluding the header of 
 * the block.
 */
static inline void countInBlock(const uint32_t *restrict block, const uint64_t fromSubblock, uint64_t toMiniblock, uint64_t charInToMiniblock, uint64_t *restrict count) {
	const uint8_t IS_LAST_MINIBLOCK_IN_SUBBLOCK = (toMiniblock+1)%MINIBLOCKS_PER_SUBBLOCK==0;
	uint8_t i;
	register uint32_t tmpWord, tmpCounts, miniblockValue=0;
	uint64_t wordID, miniblock;
	register uint64_t count0, count1, count2, count3;
	
	// Occurrences in all sub-blocks before the target miniblock.
	//
	// Remark: if $toMiniblock$ is the last one in its sub-block, all the characters in
	// the miniblock are cumulated to $tmpCounts$, rather than just the characters up to
	// $charInToMiniblock$.
	count0=0; count1=0; count2=0; count3=0; tmpCounts=0;
	miniblock=fromSubblock*MINIBLOCKS_PER_SUBBLOCK;
	wordID=fromSubblock*WORDS_PER_SUBBLOCK;
	while (miniblock+MINIBLOCKS_PER_SUBBLOCK-1<=toMiniblock) {
		tmpWord=block[wordID];
		for (i=0; i<MINIBLOCKS_PER_WORD; i++) {
			miniblockValue=tmpWord&MINIBLOCK_MASK;
			tmpCounts+=miniblock2counts[miniblockValue];
			tmpWord>>=BITS_PER_MINIBLOCK;
		}
		tmpWord|=block[wordID+1]<<4;
		for (i=0; i<MINIBLOCKS_PER_WORD; i++) {
			miniblockValue=tmpWord&MINIBLOCK_MASK;
			tmpCounts+=miniblock2counts[miniblockValue];
			tmpWord>>=BITS_PER_MINIBLOCK;
		}
		tmpWord=(block[wordID+1]>>24)|(block[wordID+2]<<8);
		for (i=0; i<MINIBLOCKS_PER_WORD; i++) {
			miniblockValue=tmpWord&MINIBLOCK_MASK;
			tmpCounts+=miniblock2counts[miniblockValue];
			tmpWord>>=BITS_PER_MINIBLOCK;
		}
		tmpWord=(block[wordID+2]>>20)|(block[wordID+3]<<12);
		for (i=0; i<MINIBLOCKS_PER_WORD; i++) {
			miniblockValue=tmpWord&MINIBLOCK_MASK;
			tmpCounts+=miniblock2counts[miniblockValue];
			tmpWord>>=BITS_PER_MINIBLOCK;
		}
		tmpWord=(block[wordID+3]>>16)|(block[wordID+4]<<16);
		for (i=0; i<MINIBLOCKS_PER_WORD; i++) {
			miniblockValue=tmpWord&MINIBLOCK_MASK;
			tmpCounts+=miniblock2counts[miniblockValue];
			tmpWord>>=BITS_PER_MINIBLOCK;
		}
		tmpWord=(block[wordID+4]>>12)|(block[wordID+5]<<20);
		for (i=0; i<MINIBLOCKS_PER_WORD; i++) {
			miniblockValue=tmpWord&MINIBLOCK_MASK;
			tmpCounts+=miniblock2counts[miniblockValue];
			tmpWord>>=BITS_PER_MINIBLOCK;
		}
		tmpWord=(block[wordID+5]>>8)|(block[wordID+6]<<24);
		for (i=0; i<MINIBLOCKS_PER_WORD; i++) {
			miniblockValue=tmpWord&MINIBLOCK_MASK;
			tmpCounts+=miniblock2counts[miniblockValue];
			tmpWord>>=BITS_PER_MINIBLOCK;
		}
		tmpWord=block[wordID+6]>>4;
		for (i=0; i<MINIBLOCKS_PER_WORD; i++) {
			miniblockValue=tmpWord&MINIBLOCK_MASK;
			tmpCounts+=miniblock2counts[miniblockValue];
			tmpWord>>=BITS_PER_MINIBLOCK;
		}
		count0+=tmpCounts&ALL_ONES_8;
		tmpCounts>>=8;
		count1+=tmpCounts&ALL_ONES_8;
		tmpCounts>>=8;
		count2+=tmpCounts&ALL_ONES_8;
		tmpCounts>>=8;
		count3+=tmpCounts&ALL_ONES_8;
		tmpCounts>>=8;
		// Now $tmpCounts$ equals zero
		
		wordID+=WORDS_PER_SUBBLOCK;
		miniblock+=MINIBLOCKS_PER_SUBBLOCK;
	}
	if (IS_LAST_MINIBLOCK_IN_SUBBLOCK) {
		// Removing from $count$ the extra counts inside $toMiniblock$.
		tmpCounts=miniblock2suffixCounts[(miniblockValue<<2)+charInToMiniblock];
		count[0]+=count0-(tmpCounts&ALL_ONES_8);
		tmpCounts>>=8;
		count[1]+=count1-(tmpCounts&ALL_ONES_8);
		tmpCounts>>=8;
		count[2]+=count2-(tmpCounts&ALL_ONES_8);
		tmpCounts>>=8;
		count[3]+=count3-(tmpCounts&ALL_ONES_8);
		return;
	}
	
	// Occurrences inside the sub-block to which the target miniblock belongs.
	//
	// Remark: all characters in $toMiniblock$ are cumulated to $tmpCounts$, rather
	// than just the characters up to $charInToMiniblock$.
	tmpWord=block[wordID];
	for (i=0; i<MINIBLOCKS_PER_WORD; i++) {
		miniblockValue=tmpWord&MINIBLOCK_MASK;
		tmpCounts+=miniblock2counts[miniblockValue];
		if (miniblock==toMiniblock) goto countInBlock_end;
		tmpWord>>=BITS_PER_MINIBLOCK;
		miniblock++;
	}
	tmpWord|=block[wordID+1]<<4;
	for (i=0; i<MINIBLOCKS_PER_WORD; i++) {
		miniblockValue=tmpWord&MINIBLOCK_MASK;
		tmpCounts+=miniblock2counts[miniblockValue];
		if (miniblock==toMiniblock) goto countInBlock_end;
		tmpWord>>=BITS_PER_MINIBLOCK;
		miniblock++;
	}
	tmpWord=(block[wordID+1]>>24)|(block[wordID+2]<<8);
	for (i=0; i<MINIBLOCKS_PER_WORD; i++) {
		miniblockValue=tmpWord&MINIBLOCK_MASK;
		tmpCounts+=miniblock2counts[miniblockValue];
		if (miniblock==toMiniblock) goto countInBlock_end;
		tmpWord>>=BITS_PER_MINIBLOCK;
		miniblock++;
	}
	tmpWord=(block[wordID+2]>>20)|(block[wordID+3]<<12);
	for (i=0; i<MINIBLOCKS_PER_WORD; i++) {
		miniblockValue=tmpWord&MINIBLOCK_MASK;
		tmpCounts+=miniblock2counts[miniblockValue];
		if (miniblock==toMiniblock) goto countInBlock_end;
		tmpWord>>=BITS_PER_MINIBLOCK;
		miniblock++;
	}
	tmpWord=(block[wordID+3]>>16)|(block[wordID+4]<<16);
	for (i=0; i<MINIBLOCKS_PER_WORD; i++) {
		miniblockValue=tmpWord&MINIBLOCK_MASK;
		tmpCounts+=miniblock2counts[miniblockValue];
		if (miniblock==toMiniblock) goto countInBlock_end;
		tmpWord>>=BITS_PER_MINIBLOCK;
		miniblock++;
	}
	tmpWord=(block[wordID+4]>>12)|(block[wordID+5]<<20);
	for (i=0; i<MINIBLOCKS_PER_WORD; i++) {	
		miniblockValue=tmpWord&MINIBLOCK_MASK;
		tmpCounts+=miniblock2counts[miniblockValue];
		if (miniblock==toMiniblock) goto countInBlock_end;
		tmpWord>>=BITS_PER_MINIBLOCK;
		miniblock++;
	}
	tmpWord=(block[wordID+5]>>8)|(block[wordID+6]<<24);
	for (i=0; i<MINIBLOCKS_PER_WORD; i++) {
		miniblockValue=tmpWord&MINIBLOCK_MASK;
		tmpCounts+=miniblock2counts[miniblockValue];
		if (miniblock==toMiniblock) goto countInBlock_end;
		tmpWord>>=BITS_PER_MINIBLOCK;
		miniblock++;
	}	
	tmpWord=block[wordID+6]>>4;
	for (i=0; i<MINIBLOCKS_PER_WORD; i++) {
		miniblockValue=tmpWord&MINIBLOCK_MASK;
		tmpCounts+=miniblock2counts[miniblockValue];
		if (miniblock==toMiniblock) goto countInBlock_end;
		tmpWord>>=BITS_PER_MINIBLOCK;
		miniblock++;
	}
	
	// Removing from $tmpCounts$ the extra counts inside $toMiniblock$.
countInBlock_end:
	tmpCounts-=miniblock2suffixCounts[(miniblockValue<<2)+charInToMiniblock];
	count[0]+=count0+(tmpCounts&ALL_ONES_8);
	tmpCounts>>=8;
	count[1]+=count1+(tmpCounts&ALL_ONES_8);
	tmpCounts>>=8;
	count[2]+=count2+(tmpCounts&ALL_ONES_8);
	tmpCounts>>=8;
	count[3]+=count3+(tmpCounts&ALL_ONES_8);
}


/**
 * Returns the number of occurrences of all characters in A,C,G,T inside the 
 * interval that starts from the beginning of the $fromMiniblock$-th miniblock of $block$,
 * and that ends at the $charInToMiniblock$-th character of the $toMiniblock$-th miniblock
 * of $block$ (included), where $fromMiniblock$ and $toMiniblock$ are assumed to belong to
 * the same sub-block.
 *
 * @param block pointer to the first sub-block in the block, i.e. excluding the header of 
 * the block;
 * @param toMiniblock can be equal to $fromMiniblock$;
 * @param charInToMiniblock in {0,1,2};
 * @return the counts, packed in a single integer as described in $countInBlock()$.
 */
static inline uint32_t countInSubblock(const uint32_t *restrict block, uint64_t fromMiniblock, uint64_t toMiniblock, uint64_t charInToMiniblock) {
	const uint64_t LAST_BIT = (toMiniblock+1)*BITS_PER_MINIBLOCK-1;
	uint8_t i, bitsInWord;
	register uint32_t tmpWord, tmpCounts;
	uint32_t miniblockValue = 0;
	uint64_t bits, wordID;
	
	// Occurrences in the following miniblocks, considered in chunks of 
	// $MINIBLOCKS_PER_WORD$ miniblocks.
	tmpCounts=0; bits=fromMiniblock*BITS_PER_MINIBLOCK;
	while (bits+BITS_IN_MINIBLOCKS_PER_WORD-1<=LAST_BIT) {
		wordID=bits/BITS_PER_WORD;
		bitsInWord=bits%BITS_PER_WORD;
		tmpWord=block[wordID]>>bitsInWord;
		if (bitsInWord>BITS_PER_WORD-BITS_IN_MINIBLOCKS_PER_WORD) tmpWord|=block[wordID+1]<<(BITS_PER_WORD-bitsInWord);
		for (i=0; i<MINIBLOCKS_PER_WORD; i++) {
			miniblockValue=tmpWord&MINIBLOCK_MASK;
			tmpCounts+=miniblock2counts[miniblockValue];
			tmpWord>>=BITS_PER_MINIBLOCK;
		}
		bits+=BITS_IN_MINIBLOCKS_PER_WORD;		
	}
	if ((toMiniblock-fromMiniblock+1)%MINIBLOCKS_PER_WORD==0) {
		// Removing the extra counts inside $toMiniblock$.
		return tmpCounts-miniblock2suffixCounts[(miniblockValue<<2)+charInToMiniblock];
	}
	
	// Occurrences fewer than a word away from the beginning of the last miniblock
	while (bits<LAST_BIT) {
		wordID=bits/BITS_PER_WORD;
		bitsInWord=bits%BITS_PER_WORD;
		miniblockValue=block[wordID]>>bitsInWord;
		if (bitsInWord>BITS_PER_WORD-BITS_PER_MINIBLOCK) miniblockValue|=block[wordID+1]<<(BITS_PER_WORD-bitsInWord);
		miniblockValue&=MINIBLOCK_MASK;
		tmpCounts+=miniblock2counts[miniblockValue];
		bits+=BITS_PER_MINIBLOCK;
	}
	
	// Removing from $tmpCounts$ the extra counts inside $toMiniblock$.
	return tmpCounts-miniblock2suffixCounts[(miniblockValue<<2)+charInToMiniblock];
}


/**
 * Answers all positions that lie in the same block, using a single scan of the block.
 */
void DNA5_multipe_char_pref_counts(uint32_t *index, uint64_t *restrict textPositions, uint64_t nTextPositions, uint64_t *restrict counts) {
	uint8_t charInMiniblock, previousCharInMiniblock, bitsInWord;
	register uint32_t tmpCounts;
	uint32_t miniblockValue;
	register uint64_t count0, count1, count2, count3;
	uint64_t i;
	uint64_t blockID, previousBlockID, miniblockID, previousMiniblockID, charInBlock, previousCharInBlock;
	uint64_t wordID, row, bits, subBlockID, previousSubBlockID;
	uint32_t *block;
	uint64_t *block64;

	// First position
	previousBlockID=textPositions[0]/CHARS_PER_BLOCK;
	previousCharInBlock=textPositions[0]%CHARS_PER_BLOCK;
	previousMiniblockID=previousCharInBlock/CHARS_PER_MINIBLOCK;
	previousCharInMiniblock=textPositions[0]%CHARS_PER_MINIBLOCK;
	block=&index[previousBlockID*WORDS_PER_BLOCK];
	block64=(uint64_t *)block;
	counts[0]=(uint32_t)block64[0]; counts[1]=(uint32_t)block64[1]; 
	counts[2]=(uint32_t)block64[2]; counts[3]=(uint32_t)block64[3];
	countInBlock(&block[BLOCK_HEADER_SIZE_IN_WORDS],0,previousMiniblockID,previousCharInMiniblock,counts);
	if (nTextPositions==1) return;
	previousSubBlockID=previousCharInBlock/CHARS_PER_SUBBLOCK;
	
	// Other positions
	count0=counts[0]; count1=counts[1]; count2=counts[2]; count3=counts[3];
	for (i=1; i<nTextPositions; i++) {
		row=i<<2;
		blockID=textPositions[i]/CHARS_PER_BLOCK;
		charInBlock=textPositions[i]%CHARS_PER_BLOCK;
		subBlockID=charInBlock/CHARS_PER_SUBBLOCK;
		miniblockID=charInBlock/CHARS_PER_MINIBLOCK;
		charInMiniblock=textPositions[i]%CHARS_PER_MINIBLOCK;
		if (blockID!=previousBlockID) {
			// Counting just from the beginning of $blockID$.
			block=&index[blockID*WORDS_PER_BLOCK];
			block64=(uint64_t *)block;
			counts[row+0]=(uint32_t)block64[0]; counts[row+1]=(uint32_t)block64[1]; 
			counts[row+2]=(uint32_t)block64[2]; counts[row+3]=(uint32_t)block64[3];
			countInBlock(&block[BLOCK_HEADER_SIZE_IN_WORDS],0,miniblockID,charInMiniblock,&counts[row]);
			goto DNA5_multipe_char_pref_counts_nextPosition;
		}
		
		// Positions $i$ and $i-1$ lie in the same block
		block=&index[blockID*WORDS_PER_BLOCK+BLOCK_HEADER_SIZE_IN_WORDS];
		
		// Occurrences inside the previous miniblock
		if (previousCharInMiniblock!=2) {
			bits=previousMiniblockID*BITS_PER_MINIBLOCK;
			wordID=bits/BITS_PER_WORD;
			bitsInWord=bits%BITS_PER_WORD;
			miniblockValue=block[wordID]>>bitsInWord;
			if (bitsInWord>BITS_PER_WORD-BITS_PER_MINIBLOCK) miniblockValue|=block[wordID+1]<<(BITS_PER_WORD-bitsInWord);
			miniblockValue&=MINIBLOCK_MASK;
			if (previousMiniblockID==miniblockID) {
				tmpCounts=miniblock2substringCounts[(miniblockValue<<2)+(previousCharInMiniblock<<1)+charInMiniblock-1];
				counts[row+0]=count0+(tmpCounts&ALL_ONES_8);
				tmpCounts>>=8;
				counts[row+1]=count1+(tmpCounts&ALL_ONES_8);
				tmpCounts>>=8;
				counts[row+2]=count2+(tmpCounts&ALL_ONES_8);
				tmpCounts>>=8;
				counts[row+3]=count3+(tmpCounts&ALL_ONES_8);
				goto DNA5_multipe_char_pref_counts_nextPosition;
			}
			else tmpCounts=miniblock2suffixCounts[(miniblockValue<<2)+previousCharInMiniblock];
		}
		else tmpCounts=0;
		if (subBlockID==previousSubBlockID) {
			// Occurrences inside the common sub-block
			tmpCounts+=countInSubblock(block,previousMiniblockID+1,miniblockID,charInMiniblock);
			counts[row+0]=count0+(tmpCounts&ALL_ONES_8);
			tmpCounts>>=8;
			counts[row+1]=count1+(tmpCounts&ALL_ONES_8);
			tmpCounts>>=8;
			counts[row+2]=count2+(tmpCounts&ALL_ONES_8);
			tmpCounts>>=8;
			counts[row+3]=count3+(tmpCounts&ALL_ONES_8);
			goto DNA5_multipe_char_pref_counts_nextPosition;
		}
		if (((previousMiniblockID+1)%MINIBLOCKS_PER_SUBBLOCK)!=0) {
			// Occurrences inside the previous sub-block
			tmpCounts+=countInSubblock(block,previousMiniblockID+1,(previousSubBlockID+1)*MINIBLOCKS_PER_SUBBLOCK-1,2);
		}
		counts[row+0]=count0+(tmpCounts&ALL_ONES_8);
		tmpCounts>>=8;
		counts[row+1]=count1+(tmpCounts&ALL_ONES_8);
		tmpCounts>>=8;
		counts[row+2]=count2+(tmpCounts&ALL_ONES_8);
		tmpCounts>>=8;
		counts[row+3]=count3+(tmpCounts&ALL_ONES_8);
		// Occurrences inside the following sub-blocks
		countInBlock(block,previousSubBlockID+1,miniblockID,charInMiniblock,&counts[row]);
		
		// Next iteration
DNA5_multipe_char_pref_counts_nextPosition:
		previousBlockID=blockID;
		previousCharInBlock=charInBlock;
		previousSubBlockID=subBlockID;
		previousMiniblockID=miniblockID;
		previousCharInMiniblock=charInMiniblock;
		count0=counts[row+0]; count1=counts[row+1];
		count2=counts[row+2]; count3=counts[row+3];
	}
}




// ---------------------------------- SERIALIZATION --------------------------------------

/**
 * Remark: the procedure stores just the payload of each block.
 */
uint64_t serialize(uint32_t *index, uint64_t textLength, FILE *file) {
	uint64_t i;
	uint64_t tmp, nMiniblocks, nWords, out;
	uint32_t *block;
	
	block=index; out=0;
	for (i=0; i+CHARS_PER_BLOCK<=textLength; i+=CHARS_PER_BLOCK) {
		tmp=fwrite(block+BLOCK_HEADER_SIZE_IN_WORDS,BYTES_PER_WORD,PAYLOAD_WORDS_PER_BLOCK,file);
		if (tmp!=PAYLOAD_WORDS_PER_BLOCK) return 0;
		out+=PAYLOAD_BYTES_PER_BLOCK;
		block+=WORDS_PER_BLOCK;
	}
	if (i<textLength) {
		nMiniblocks=MY_CEIL(textLength-i,CHARS_PER_MINIBLOCK);
		nWords=MY_CEIL(nMiniblocks*BITS_PER_MINIBLOCK,BITS_PER_WORD);
		tmp=fwrite(block+BLOCK_HEADER_SIZE_IN_WORDS,BYTES_PER_WORD,nWords,file);
		if (tmp!=nWords) return 0;
		out+=nWords*BYTES_PER_WORD;
	}
	return out;
}


uint64_t deserialize(uint32_t *index, uint64_t textLength, FILE *file) {
	const uint64_t N_BLOCKS = MY_CEIL(textLength,CHARS_PER_BLOCK);
	uint8_t j;
	uint64_t i;
	uint64_t tmp, nMiniblocks, nWords, out;
	uint32_t *block;
	uint64_t *block64;
	uint64_t tmpCounts[4];
	
	// Loading block payloads
	block=index; out=0;
	for (i=0; i+CHARS_PER_BLOCK<=textLength; i+=CHARS_PER_BLOCK) {
		tmp=fread(block+BLOCK_HEADER_SIZE_IN_WORDS,BYTES_PER_WORD,PAYLOAD_WORDS_PER_BLOCK,file);
		if (tmp!=PAYLOAD_WORDS_PER_BLOCK) return 0;
		out+=PAYLOAD_BYTES_PER_BLOCK;
		block+=WORDS_PER_BLOCK;
	}
	if (i<textLength) {
		nMiniblocks=MY_CEIL(textLength-i,CHARS_PER_MINIBLOCK);
		nWords=MY_CEIL(nMiniblocks*BITS_PER_MINIBLOCK,BITS_PER_WORD);
		tmp=fread(block+BLOCK_HEADER_SIZE_IN_WORDS,BYTES_PER_WORD,nWords,file);
		if (tmp!=nWords) return 0;
		out+=nWords*BYTES_PER_WORD;
	}
	
	// Loading block headers from the payloads
	block=index;
	for (j=0; j<4; j++) tmpCounts[j]=0;
	for (i=0; i<N_BLOCKS-1; i++) {
		block64=(uint64_t *)block;
		for (j=0; j<4; j++) block64[j]=tmpCounts[j];
		countInBlock(block+BLOCK_HEADER_SIZE_IN_WORDS,0,MINIBLOCKS_PER_BLOCK-1,2,(uint64_t *)(&tmpCounts));
		block+=WORDS_PER_BLOCK;
	}
	block64=(uint64_t *)block;
	for (j=0; j<4; j++) block64[j]=tmpCounts[j];
	
	return out;
}
