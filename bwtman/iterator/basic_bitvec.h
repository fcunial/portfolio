#ifndef basic_bitvec_h
#define basic_bitvec_h
#include<stdlib.h>

#define bits_per_byte 8
#define bits_per_word ((sizeof(unsigned int)*bits_per_byte))


static inline unsigned int ismarkedbit(unsigned int bitpos,unsigned int * bitvec)
{
	return (bitvec[bitpos/bits_per_word]>>(bitpos%bits_per_word))&1;
};


static inline void mark_bit(unsigned int bitpos,unsigned int * bitvec)
{
	bitvec[bitpos/bits_per_word]|=(1<<(bitpos%bits_per_word));
};

static inline unsigned int test_and_mark_bit(unsigned int bitpos,unsigned int * bitvec)
{
	unsigned int bitpos_in_word=bitpos%bits_per_word;
	unsigned int word_pos=bitpos/bits_per_word;
	unsigned int oldbit=(bitvec[word_pos]>>bitpos_in_word)&1;
	bitvec[word_pos]|=1<<bitpos_in_word;
	return oldbit;
};

static inline unsigned int * new_bitvec(unsigned int size)
{
	return calloc((size+bits_per_word-1)/(bits_per_word),
			bits_per_word/bits_per_byte);

};


#endif