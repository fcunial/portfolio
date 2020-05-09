CC="/usr/local/opt/gcc/bin/gcc-9"
CFLAGS=-fopenmp -Wall -O3 
#-mavx2 #-fno-tree-vectorize #-fopt-info-optimized-optall 
LIBS=-ldl -lm
DIVSUFSORT_OBJS=./libdivsufsort/build/lib/libdivsufsort64.a
ROOT_DIR=$(CURDIR)
.PHONY: all clean   program-1   malloc-count random iterator io maws-single


all: program-1 program-2 program-3
	



# ---- MAIN PROGRAMS ----

PROGRAMS=$(PROGRAM_1) $(PROGRAM_2) $(PROGRAM_3)

PROGRAM_1=$(ROOT_DIR)/run_MAWs_single
program-1: $(ROOT_DIR)/scores.c $(PROGRAM_1).c io malloc-count iterator maws-single
		$(CC) $(CFLAGS) $(ROOT_DIR)/scores.c $(PROGRAM_1).c $(IO_OBJS) $(MALLOC_COUNT_OBJS) $(ITERATOR_OBJS) $(MAWS_SINGLE_OBJS) $(LIBS) -o $(PROGRAM_1)

PROGRAM_2=$(ROOT_DIR)/run_MRWs_single
program-2: $(ROOT_DIR)/scores.c $(PROGRAM_2).c io malloc-count iterator maws-single
	$(CC) $(CFLAGS) $(ROOT_DIR)/scores.c $(PROGRAM_2).c $(IO_OBJS) $(MALLOC_COUNT_OBJS) $(ITERATOR_OBJS) $(MAWS_SINGLE_OBJS) $(LIBS) -o $(PROGRAM_2)
	

PROGRAM_3=$(ROOT_DIR)/buildIndex
program-3: $(ROOT_DIR)/buildIndex.c $(PROGRAM_3).c io malloc-count iterator
		$(CC) $(CFLAGS) $(PROGRAM_3).c $(IO_OBJS) $(MALLOC_COUNT_OBJS) $(ITERATOR_OBJS) $(LIBS) -o $(PROGRAM_3)




# ---- COMPONENTS ----

MALLOC_COUNT_DIR=$(ROOT_DIR)/malloc_count
MALLOC_COUNT_SRC=$(MALLOC_COUNT_DIR)/malloc_count.c $(MALLOC_COUNT_DIR)/stack_count.c 
MALLOC_COUNT_HDRS=$(MALLOC_COUNT_DIR)/malloc_count.h $(MALLOC_COUNT_DIR)/stack_count.h
MALLOC_COUNT_OBJS=$(MALLOC_COUNT_DIR)/malloc_count.o $(MALLOC_COUNT_DIR)/stack_count.o 
malloc-count: $(MALLOC_COUNT_SRC) $(MALLOC_COUNT_HDRS)
	cd $(MALLOC_COUNT_DIR) && $(CC) $(CFLAGS) -c *.c


RANDOM_DIR=$(ROOT_DIR)/random
RANDOM_SRC=$(RANDOM_DIR)/mt19937ar.c
RANDOM_HDRS=$(RANDOM_DIR)/mt19937ar.h
RANDOM_OBJS=$(RANDOM_DIR)/mt19937ar.o
random: $(RANDOM_SRC) $(RANDOM_HDRS)
	cd $(RANDOM_DIR) && $(CC) $(CFLAGS) $(LIBS) -c $(RANDOM_SRC)


ITERATOR_DIR=$(ROOT_DIR)/iterator
ITERATOR_SRC=$(ITERATOR_DIR)/DNA5_tables.c $(ITERATOR_DIR)/indexed_DNA5_seq.c $(ITERATOR_DIR)/DNA5_Basic_BWT.c $(ITERATOR_DIR)/SLT_single_string.c $(ITERATOR_DIR)/SLT_many_strings.c
ITERATOR_HDRS=$(ITERATOR_DIR)/indexed_DNA5_seq.h $(ITERATOR_DIR)/DNA5_Basic_BWT.h $(ITERATOR_DIR)/SLT_single_string.h $(ITERATOR_DIR)/SLT_many_strings.h $(ITERATOR_DIR)/divsufsort64.h
ITERATOR_OBJS=$(ITERATOR_DIR)/DNA5_tables.o $(ITERATOR_DIR)/indexed_DNA5_seq.o $(ITERATOR_DIR)/DNA5_Basic_BWT.o $(ITERATOR_DIR)/SLT_single_string.o $(DIVSUFSORT_OBJS)
iterator: $(ITERATOR_SRC) $(ITERATOR_HDRS)
	cd $(ITERATOR_DIR) && $(CC) $(CFLAGS) -c $(ITERATOR_SRC)


IO_DIR=$(ROOT_DIR)/io
IO_SRC=$(IO_DIR)/io.c $(IO_DIR)/bufferedFileWriter.c $(IO_DIR)/bits.c
IO_HDRS=$(IO_DIR)/io.h $(IO_DIR)/bufferedFileWriter.h $(IO_DIR)/bits.h
IO_OBJS=$(IO_DIR)/io.o $(IO_DIR)/bufferedFileWriter.o $(IO_DIR)/bits.o
io: $(IO_SRC) $(IO_HDRS)
	cd $(IO_DIR) && $(CC) $(CFLAGS) -c $(IO_SRC)




# ---- CALLBACKS ----

CALLBACKS_DIR=$(ROOT_DIR)/callbacks

MAWS_SINGLE_SRC=$(CALLBACKS_DIR)/MAWs_single.c
MAWS_SINGLE_HDRS=$(CALLBACKS_DIR)/MAWs_single.h
MAWS_SINGLE_OBJS=$(CALLBACKS_DIR)/MAWs_single.o
maws-single: $(MAWS_SINGLE_SRC) $(MAWS_SINGLE_HDRS)
	cd $(CALLBACKS_DIR) && $(CC) $(CFLAGS) -c $(MAWS_SINGLE_SRC)




# ---- CLEANING ----

clean:
	rm $(CALLBACKS_DIR)/*.o $(IO_DIR)/*.o $(ITERATOR_DIR)/*.o $(RANDOM_DIR)/*.o $(MALLOC_COUNT_DIR)/*.o $(PROGRAMS)
 
