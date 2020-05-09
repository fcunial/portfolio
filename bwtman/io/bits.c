/**
 * @author Fabio Cunial
 */
#include <stdio.h>
#include "bits.h"


void printLong(uint64_t number) {
	uint8_t i;
	uint64_t mask = 1L<<(BITS_PER_LONG-1);
	
	for (i=0; i<BITS_PER_LONG; i++) {
		printf("%c",(number&mask)==0?'0':'1');
		mask>>=1;
	}
	printf("\n");
}


uint64_t writeLong(uint64_t x, uint64_t *buffer, uint64_t fromByte) {
	const uint8_t FLAG = 0x80;
	const uint8_t MASK = 0x7F;
	uint8_t i;
	uint64_t xPrime, future;
	
	i=fromByte; xPrime=x;
	do {
		future=xPrime>>7;
		writeByte(buffer,i++,(xPrime&MASK)|(future==0?FLAG:0));
		xPrime=future;
	} while (xPrime!=0);
	return i-1;
}


uint64_t readLong(uint64_t *x, uint64_t *buffer, uint64_t fromByte) {
	const uint8_t FLAG = 0x80;
	const uint8_t MASK = 0x7F;
	uint8_t i, j;
	uint8_t value;
	uint64_t xPrime;
	
	i=fromByte; j=0; xPrime=0L;
	do {
		value=readByte(buffer,i++);
		xPrime|=((uint64_t)(value&MASK))<<j;
		j+=7;
	} while ((value&FLAG)==0);
	*x=xPrime;
	return i-1;
}


void writeByte(uint64_t *buffer, uint64_t i, uint8_t value) {
	uint64_t cell, rem;
	
	cell=i/BYTES_PER_LONG; rem=i%BYTES_PER_LONG;
	buffer[cell]&=~(ALL_ONES_8<<(rem*BITS_PER_BYTE));
	buffer[cell]|=((uint64_t)value)<<(rem*BITS_PER_BYTE);
}


uint8_t readByte(uint64_t *buffer, uint64_t i) {
	uint64_t cell, rem;
	
	cell=i/BITS_PER_LONG; rem=i%BITS_PER_LONG;
	return (uint8_t)((buffer[cell]&(ALL_ONES_8<<(rem*BITS_PER_BYTE)))>>(rem*BITS_PER_BYTE));
}


uint8_t readTwoBits(uint64_t *buffer, uint64_t i) {
	uint64_t bit, cell, rem;
	
	bit=i<<1; cell=bit/BITS_PER_LONG; rem=bit%BITS_PER_LONG;
	return (uint8_t)((buffer[cell]&(TWO_BIT_MASK<<rem))>>rem);
}


void writeTwoBits(uint64_t *buffer, uint64_t i, uint8_t value) {
	uint64_t bit, cell, rem;
	
	bit=i<<1; cell=bit/BITS_PER_LONG; rem=bit%BITS_PER_LONG;
	buffer[cell]&=~(TWO_BIT_MASK<<rem);
	buffer[cell]|=((uint64_t)value)<<rem;
}


uint8_t readBit(uint64_t *buffer, uint64_t i) {
	uint64_t bit, cell, rem;
	
	bit=i; cell=bit/BITS_PER_LONG; rem=bit%BITS_PER_LONG;			
	return (buffer[cell]&(BIT_MASK<<rem))==0?0:1;
}


void writeBit(uint64_t *buffer, uint64_t i, uint8_t value) {
	uint64_t bit, cell, rem;
	
	bit=i; cell=bit/BITS_PER_LONG; rem=bit%BITS_PER_LONG;
	buffer[cell]&=~(BIT_MASK<<rem);
	if (value==1) buffer[cell]|=BIT_MASK<<rem;
}


uint8_t hasOneBit(uint64_t *bitvector, uint64_t lastBit) {
	uint64_t i;
	uint64_t lastCell, rem, mask;

	lastCell=lastBit/BITS_PER_LONG;
	for (i=0; i<lastCell; i++) {
		if (bitvector[i]!=0) return 1;
	}
	rem=lastBit%BITS_PER_LONG;
	mask=ALL_ONES_64>>(BITS_PER_LONG-rem-1);
	return (bitvector[lastCell]&mask)!=0?1:0;
}




