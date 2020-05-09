/**
 * A simple string index that supports just rank and access.
 *
 * @author Djamal Belazzougui, Fabio Cunial
 */
#ifndef indexed_DNA5_seq_h
#define indexed_DNA5_seq_h

#include <stdint.h>
#include <stdio.h>
#include "../io/io.h"


/** 
 * Builds the index on string $text$ of length $textLength$. 
 *
 * @param characterCount output array: the procedure stores here the total number of 
 * occurrences of each character (0=A, 1=C, 2=G, 3=T);
 * @param outputSize output value: the procedure stores here the size of the data 
 * structure, in bytes;
 * @return NULL if construction failed.
 */
uint32_t *build_basic_DNA5_seq(uint8_t *text, uint64_t textLength, uint64_t *outputSize, uint64_t *characterCount);


void free_basic_DNA5_seq(uint32_t *index);


/**
 * Computes rank queries for $t>=1$ distinct positions.
 *
 * @param textPositions sorted in increasing order.
 */
void DNA5_multipe_char_pref_counts(uint32_t *index, uint64_t *textPositions, uint64_t nTextPositions, uint64_t *counts);


void DNA5_set_char(uint32_t *indexed_seq, uint64_t charpos, uint8_t char_val);


/**
 * @param textLength in characters;
 * @return the index size in bytes.
 */
uint64_t getIndexSize(const uint64_t textLength);


/**
 * @return $oldPointer$ moved forward by at least one page, and so that it coincides with 
 * the beginning of a page.
 */
uint32_t *alignIndex(uint32_t *oldPointer);


/**
 * Stores the index to $file$, which is assumed to be already open.
 * 
 * @param index assumed to be of a nonempty string;
 * @return the number of bytes written, or zero if an error occurred.
 */
uint64_t serialize(uint32_t *index, uint64_t textLength, FILE *file);


/**
 * Loads the index from $file$, which is assumed to be already open.
 *
 * @param file assumed to contain the index of a nonempty string;
 * @return the number of bytes read, or zero if an error occurred.
 */
uint64_t deserialize(uint32_t *index, uint64_t textLength, FILE *file);


#endif