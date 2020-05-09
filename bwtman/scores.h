/**
 * Functions for handling substring scores in the iterator.
 *
 * @author Fabio Cunial
 */
#ifndef scores_h
#define scores_h

#include "./iterator/SLT_single_string.h"
#include "./io/bufferedFileWriter.h"


typedef struct {
	double *scores;  // List of scores
	double *scoreStack;
	unsigned int scoreStackCapacity;  // In elements
	char *scoreBuffer;  // String representation of a score
	double *dnaProbabilities;  // Empirical probability of each DNA character
	double *logDnaProbabilities;  // \log_e of the above
} ScoreState_t;


void scoreInitialize(ScoreState_t *scoreState, double *dnaProbabilities, double *logDnaProbabilities);


void scoreFinalize(ScoreState_t *scoreState);


/**
 * Called for each MAW $W=aVb$ where $V$ is described by $RightMaximalString$.
 *
 * @param leftCharID,rightCharID (in [0..3]) position of characters $a$ and $b$ in the 
 * alphabet;
 * @param leftFreq,rightFreq frequency of $aV$ and $Vb$ in the text.
 */
void scoreCallback(uint8_t leftCharID, uint8_t rightCharID, uint64_t leftFreq, uint64_t rightFreq, uint64_t textLength, RightMaximalString_t *RightMaximalString, ScoreState_t *scoreState);


/**
 * Called whenever a character is pushed on the character stack.
 *
 * @param charID position of the character in the alphabet;
 * @param stringDepth depth of the stack after the character has been pushed.
 */
void scorePush(uint8_t charID, uint64_t stringDepth, ScoreState_t *scoreState);


/**
 * Prints all scores to $file$.
 */
void scorePrint(ScoreState_t *scoreState, BufferedFileWriter_t *file);


/**
 * Returns a number different from 0 iff the scores in $scoreState$ have an
 * implementation-defined property.
 */
uint8_t scoreSelect(ScoreState_t *scoreState);


void scoreClone(ScoreState_t *from, ScoreState_t *to);


#endif