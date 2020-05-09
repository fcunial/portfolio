/**
 * A simple BWT index that supports just rank and access.
 *
 * @author Djamal Belazzougui, Fabio Cunial
 */
#ifndef DNA5_Basic_BWT_h
#define DNA5_Basic_BWT_h


#define Basic_bwt_no_free_text 0
#define Basic_bwt_free_text 0


#include <stdint.h>
#include <stdlib.h>
#include "indexed_DNA5_seq.h"
#include "../io/io.h"


typedef struct {
	uint64_t size;  // Size of $indexedBWT$, in bytes.
	uint64_t sharpPosition;  // Position of the sharp in the BWT.
	uint64_t textLength;  // Length of the text, excluding the sharp.
	uint64_t cArray[5];  // C array. 0=A, 1=C, 2=G, 3=T/U, 4=N.
	
	uint32_t *indexedBWT;
	
	uint64_t textLengthDNA;  // Number of DNA characters
	double dnaProbabilities[4];  // Empirical probability of each DNA character
	double logDnaProbabilities[4];  // \log_e of the above
} BwtIndex_t;


/**
 * Allocates the memory for the index, without creating it.
 */
BwtIndex_t *newBwtIndex();


void freeBwtIndex(BwtIndex_t *bwtIndex);


/**
 * Creates the index.
 * 
 * @param options for dbwt.
 */
BwtIndex_t *buildBwtIndex(char *text, uint64_t textLength, uint32_t options);


/**
 * Stores the index to the file with address $path$.
 * 
 * @param index assumed to be of a nonempty string;
 * @return the number of bytes written, or zero if an error occurred.
 */
uint64_t serializeBwtIndex(BwtIndex_t *index, char *path);


/**
 * Loads the index from the file with address $path$.
 * 
 * @param index assumed to have been already allocated;
 * @param path assumed to contain the index of a nonempty string;
 * @return the number of bytes read, or zero if an error occurred.
 */
uint64_t deserializeBwtIndex(BwtIndex_t *index, char *path);


#endif