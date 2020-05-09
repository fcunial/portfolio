/**
 * Basic input/output procedures.
 *
 * @author Fabio Cunial
 */
#ifndef io_h
#define io_h

#include <stdint.h>


// IO constants used throughout the code
#ifndef DNA5_alphabet_size
#define DNA5_alphabet_size 5  // Includes #
#endif
#ifndef CONCATENATION_SEPARATOR
#define CONCATENATION_SEPARATOR 'z'
#endif
#ifndef OUTPUT_SEPARATOR_1
#define OUTPUT_SEPARATOR_1 ','
#endif
#ifndef OUTPUT_SEPARATOR_2
#define OUTPUT_SEPARATOR_2 '\n'
#endif
#ifndef BUFFER_CHUNK
#define BUFFER_CHUNK 1024  // Size of a buffer chunk, in bytes.
#endif
#ifndef ALLOC_GROWTH_NUM
#define ALLOC_GROWTH_NUM 4  // Reallocation rate
#endif
#ifndef ALLOC_GROWTH_DENOM
#define ALLOC_GROWTH_DENOM 3  // Reallocation rate
#endif
extern char *DNA_ALPHABET;  // Characters of the alphabet
extern double DNA_ALPHABET_PROBABILITIES[4];  // Empirical probability of each character
extern double LOG_DNA_ALPHABET_PROBABILITIES[4];  // log_e of the above


/**
 * 
 * 
 */
typedef struct {
	char *buffer;
	uint64_t length;  // Number of characters in memory, including RC.
	uint64_t lengthDNA;  // Number of DNA characters in memory, including RC.
	uint64_t inputLength;  // Number of non-header characters in the input file
	uint8_t hasRC;  // Reverse-complement present (1/0).
} Concatenation;


/**
 * Loads a multi-FASTA file.
 * 
 * Remark: in the case of RNA, character U is kept in the output, i.e. it is not 
 * translated into T. Every maximal run of characters not in {A,C,G,T,U} is transformed 
 * into a single delimiter $CONCATENATION_SEPARATOR$.
 *
 * Remark: if the file contains more than one string, each string except the last one is 
 * terminated by $CONCATENATION_SEPARATOR$.
 *
 * @param appendRC TRUE: terminates with $CONCATENATION_SEPARATOR$ the string built as 
 * described above, and appends its reverse-complement.
 */
Concatenation loadFASTA(char *inputFilePath, uint8_t appendRC);


/**
 * @param inputFilePath assumed to be just a sequence of characters, not organized in
 * lines and with no headers. See $loadFASTA()$ for more details.
 */
Concatenation loadPlainText(char *inputFilePath, uint8_t appendRC);


/**
 * In microseconds.
 */
double getTime();


#endif