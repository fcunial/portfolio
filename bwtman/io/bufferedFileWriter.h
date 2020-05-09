/**
 * Basic buffer of characters.
 *
 * @author Fabio Cunial
 */
#ifndef buffered_file_writer_h
#define buffered_file_writer_h


#include <stdint.h>
#include <stdio.h>
#include "bits.h"


typedef struct {
	char *buffer;
	uint64_t capacity;  // Maximum number of characters in the buffer
	uint64_t size;  // Number of characters currently in the buffer
	FILE *file;
} BufferedFileWriter_t;


/** 
 * Remark: $file->file$ is opened in append mode, so its content is preserved.
 */
void initializeBufferedFileWriter(BufferedFileWriter_t *file, char *path);


void finalizeBufferedFileWriter(BufferedFileWriter_t *file);


/**
 * Writes character $c$ to $to->file$.
 */
void writeChar(char c, BufferedFileWriter_t *to);


/**
 * Writes to $to->file$ all characters in $from[0..last]$.
 */
void writeChars(char *from, uint64_t last, BufferedFileWriter_t *to);


/**
 * Let $from$ be an array of bits. The procedure appends to $to$ all bits in 
 * $from[0..lastBit]$ (coordinates refer to bits), as characters.
 *
 * Remark: bits inside each long of $from$ are assumed to be stored from LSB to MSB.
 */
void writeBits(uint64_t *from, uint64_t lastBit, BufferedFileWriter_t *to);


/**
 * Let $from$ be an array of 2-bit numbers. The procedure appends to $to$ all numbers in 
 * $from[0..last]$ (coordinates refer to numbers), in reverse order, interpreting each 
 * number as a position in $alphabet$.
 *
 * Remark: bits inside each long of $from$ are assumed to be stored from LSB to MSB.
 */
void writeTwoBitsReversed(uint64_t *from, uint64_t last, BufferedFileWriter_t *to, char *alphabet);


#endif