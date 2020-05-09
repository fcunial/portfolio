Coding portfolio
=========

**Warning: this repository includes some large (15MB) data files, to make testing straightforward.**

Example of Java code
------------

Directory `surprisingStrings` contains a prototype implementation (unpublished) of the following paper:

* D. Belazzougui, F. Cunial (2015). [Space-efficient detection of unusual words](https://link.springer.com/chapter/10.1007/978-3-319-23826-5_22). SPIRE 2015, LNCS, volume 9309, pp 222-233.

The program detects all substrings, of any length, that occur in a text more frequently or less frequently than expected, according to an IID or a Markov model. The program can use multiple threads, and it is space-efficient since it is based on the Burrows-Wheeler transform.

To read representative examples of my coding style, I suggest looking at the following files:
* [SubstringIterator.java](https://github.com/fcunial/portfolio/blob/master/surprisingStrings/SubstringIterator.java)
* [Suffixes.java](https://github.com/fcunial/portfolio/blob/master/surprisingStrings/Suffixes.java)
* [BorderSubstring.java](https://github.com/fcunial/portfolio/blob/master/surprisingStrings/BorderSubstring.java)


**Requirements**

* A 64-bit Java compiler. The code was tested on Oracle's `javac 9.0.1`.
* A 64-bit operating system. The code was tested on macOS 10.13.

**Compiling**

Assuming the current directory is `surprisingStrings`:

```
javac -classpath .:./commons-math3-3.5.jar *.java
```

**Running**

Since this code is still a prototype, I created a test program with hardwired input arguments, to make running it easier. The program uses file `NC_021658.fna` included in the repository. 

Example run using `nThreads` parallel threads (assuming the current directory is `surprisingStrings`):
```
java -classpath .:./commons-math3-3.5.jar TestDrive nThreads
```

This simple program builds the BWT of `NC_021658.fna` and prints to STDOUT all substrings whose exact frequency is at least 100 times greater than expected according to an IID model (the model can easily be made non-uniform, e.g. by making character probabilities match their frequencies in the input). Please interrupt the program with CTRL+C, because the list of surprising strings is very long.

**Example output**

In the output, each string is followed by its exact frequency (denoted by `f=`) and by the following, space-separated scores:
* frequency-expectation
* frequency/expectation
* (frequency-expectation)/expectation
* (frequency-expectation)/sqrt(expectation)
* abs(frequency-expectation)/sqrt(expectation)
* (frequency-expectation)*(frequency-expectation)/expectation;
* (frequency-expectation)/sqrt(expectation*(1-probability))
* expectation/sqrt(variance)
* (frequency-expectation)/sqrt(variance)
* abs((frequency-expectation)/sqrt(variance))

```
construction time: 11.897s, length=14782125
String length: 14782125 nThreads: 4
ttgcgcctttgcgtttttt f=5 4,999946E0 92,97657E3 92,97557E3 681,8158E0 681,8158E0 464,8728E3 681,8158E0 7,33328E-3 681,8158E0 681,8158E0 
ttgcgcctttgcgttttttctt f=3 2,999999E0 3,570301E6 3,5703E6 3,272751E3 3,272751E3 10,7109E6 3,272751E3 916,6599E-6 3,272751E3 3,272751E3 
ctttgcgcctttgcgtttttt f=4 3,999997E0 1,1901E6 1,190099E6 2,181832E3 2,181832E3 4,760393E6 2,181832E3 1,83332E-3 2,181832E3 2,181832E3 
ctttgcgcctttgcgcctttgcgtttttt f=3 3E0 58,49584E9 58,49584E9 418,9123E3 418,9123E3 175,4875E9 418,9123E3 7,161404E-6 418,9123E3 418,9123E3 
ttgcgcctttgcgttttttct f=4 3,999997E0 1,1901E6 1,190099E6 2,181832E3 2,181832E3 4,760393E6 2,181832E3 1,83332E-3 2,181832E3 2,181832E3 
ctttgcgcctttgcgttttttct f=3 3E0 14,2812E6 14,2812E6 6,545503E3 6,545503E3 42,84361E6 6,545503E3 458,3299E-6 6,545503E3 6,545503E3 
gagctccgggttctt f=3 2,986233E0 217,9138E0 216,9138E0 25,45103E0 25,45103E0 647,7551E0 25,45103E0 117,3325E-3 25,45103E0 25,45103E0 
cgccgccgggttctt f=3 2,986233E0 217,9138E0 216,9138E0 25,45103E0 25,45103E0 647,7551E0 25,45103E0 117,3325E-3 25,45103E0 25,45103E0 
cctggcgggatttct f=3 2,986233E0 217,9138E0 216,9138E0 25,45103E0 25,45103E0 647,7551E0 25,45103E0 117,3325E-3 25,45103E0 25,45103E0 
gccgatcgggttctt f=3 2,986233E0 217,9138E0 216,9138E0 25,45103E0 25,45103E0 647,7551E0 25,45103E0 117,3325E-3 25,45103E0 25,45103E0 
cgtcttcgggttctt f=3 2,986233E0 217,9138E0 216,9138E0 25,45103E0 25,45103E0 647,7551E0 25,45103E0 117,3325E-3 25,45103E0 25,45103E0 
gccgagcaggttctt f=3 2,986233E0 217,9138E0 216,9138E0 25,45103E0 25,45103E0 647,7551E0 25,45103E0 117,3325E-3 25,45103E0 25,45103E0 
cgagcaggttgttgt f=5 4,986233E0 363,1896E0 362,1896E0 42,49661E0 42,49661E0 1,805962E3 42,49661E0 117,3325E-3 42,49661E0 42,49661E0 
tgagcagcttgttgt f=3 2,986233E0 217,9138E0 216,9138E0 25,45103E0 25,45103E0 647,7551E0 25,45103E0 117,3325E-3 25,45103E0 25,45103E0 
cgccgccggtgttgt f=3 2,986233E0 217,9138E0 216,9138E0 25,45103E0 25,45103E0 647,7551E0 25,45103E0 117,3325E-3 25,45103E0 25,45103E0 
gatcgcgcggttctt f=3 2,986233E0 217,9138E0 216,9138E0 25,45103E0 25,45103E0 647,7551E0 25,45103E0 117,3325E-3 25,45103E0 25,45103E0 
ttgcgcctttgcgtttt f=6 5,99914E0 6,973241E3 6,972241E3 204,5176E0 204,5176E0 41,82745E3 204,5176E0 29,33312E-3 204,5176E0 204,5176E0 
ctttgcgcctttgcgtttt f=5 4,999946E0 92,97657E3 92,97557E3 681,8158E0 681,8158E0 464,8728E3 681,8158E0 7,33328E-3 681,8158E0 681,8158E0 
ccttcatcgtgttgt f=3 2,986233E0 217,9138E0 216,9138E0 25,45103E0 25,45103E0 647,7551E0 25,45103E0 117,3325E-3 25,45103E0 25,45103E0 
tttgcgcctttgcgcctttgcgtttt f=4 4E0 1,218663E9 1,218663E9 69,81871E3 69,81871E3 4,874652E9 69,81871E3 57,29123E-6 69,81871E3 69,81871E3 
ccagctcggcgtttt f=7 6,986233E0 508,4655E0 507,4655E0 59,54219E0 59,54219E0 3,545272E3 59,54219E0 117,3325E-3 59,54219E0 59,54219E0 
tcacccgcacaggagacgggtccagctcggcgtttt f=5 5E0 1,597327E15 1,597327E15 89,36798E6 89,36798E6 7,986635E15 89,36798E6 55,94845E-9 89,36798E6 89,36798E6 
```



Example of C code
------------

Directory `bwtman` contains a prototype implementation (unpublished) of the following paper:

* D. Belazzougui, F. Cunial (2017). [A framework for space-efficient string kernels](https://link.springer.com/article/10.1007/s00453-017-0286-4). Algorithmica, volume 79, pages 857–883.

For now the program detects just all *minimal absent words* (MAWs) of a text, of any length, and scores them based on an IID or Markov model of the text. A minimal absent word is a string that never appears in the input, but such that every one of its substrings appears (exactly) in the input. The program is space-efficient, since it is based on the Burrows-Wheeler transform. We are currently working on making it use multiple threads, and on supporting more string analysis algorithms with the same algorithmic framework, but this is still work in progress.

To read representative examples of my coding style, I suggest looking at the following files:
* [indexed_DNA5_seq.c](https://github.com/fcunial/portfolio/blob/master/bwtman/iterator/indexed_DNA5_seq.c)
* [indexed_DNA5_seq.h](https://github.com/fcunial/portfolio/blob/master/bwtman/iterator/indexed_DNA5_seq.h)
* [SLT_single_string.c](https://github.com/fcunial/portfolio/blob/master/bwtman/iterator/SLT_single_string.c)
* [SLT_single_string.h](https://github.com/fcunial/portfolio/blob/master/bwtman/iterator/SLT_single_string.h)


**Requirements**

* A modern C compiler with support for OpenMP. The code was tested on GCC 9.2.0.
* A 64-bit operating system. The code was tested on macOS 10.13.

**Compiling**

First, let's compile `libdivsufsort`, an [external library](https://github.com/y-256/libdivsufsort) that we use for constructing the Burrows-Wheeler Transform of the input text (it is already included in this repository). Assuming the current directory is `bwtman`, let's do:

```
cd libdivsufsort
mkdir build
cd build
cmake -DCMAKE_BUILD_TYPE="Release" -DCMAKE_INSTALL_PREFIX="." ..
make
```

Then, let's edit the first line of file `Makefile` in directory `bwtman` (the main directory of the project): on the first line, let's specify in variable `CC` the path of a C compiler that supports OpenMP (in some systems, e.g. macOS, this is not the default C compiler: gcc would do). Then, let's just type `make` from directory `bwtman`.


**Running**

Since this code is still a prototype, I created a shell script with hardwired input arguments, to make running it easier. The program uses file `HS22.fasta.indexed` included in the repository, which is a Burrows-Wheeler index that I pre-built on a human chromosome 22 sequence. Assuming the current directory is `bwtman`, it suffices to type:

```
./example-maws-filterByScore.sh
```

This single-threaded program traverses the BWT index, and prints to the output file `HS22.fasta.maws.filter` all minimal absent words (MAWs) that are expected to occur at least twice if the sequence were generated by a Markov model of large order (trained on the sequence itself).


**Example output**

The program prints to STDOUT a line with global statistics, followed by a histogram of lengths of all selected minimal absent words. The statistics are:
* length of the input sequence
* minimum length of a MAW (specified by the user)
* maximum length of a MAW (specified by the user)
* time to load the index (seconds)
* time to traverse the index and generate the output (seconds)
* peak memory usage (bytes)
* number of MAWs selected			
* minimum length of a selected MAW
* maximum length of an observed MAW
* fraction of maximal repeats that are the infix of a MAW

In the output file `HS22.fasta.maws.filter`, MAWs are represented in the following compact form: we print the maximal repeat *W* that is the infix of a MAW (or possibly of many MAWs) just once; then we print, on each line, the characters *a* and *b* such that *aWb* is a MAW, followed by several statistical scores on *aWb* (the fourth of which is the one we filtered by in the input). Example output:

```
cattaattat
c,t,3.65274,9.32778e-08,-1.91121,3,7.66091e-08,-1.73205,0.00694444,1e-12,
ccattaattat
g,a,0.863419,2.20486e-08,-0.929204,2,5.10728e-08,-1.41421,0.00591716,1e-13,
tattaattat
g,c,3.68685,9.41488e-08,-1.92012,2.34483,5.98784e-08,-1.53128,0.00694444,1e-12,
agttaattat
t,c,3.68685,9.41488e-08,-1.92012,2.48889,6.35572e-08,-1.57762,0.00694444,1e-12,
acttaattat
c,g,3.25666,8.31633e-08,-1.80462,2.625,6.7033e-08,-1.62019,0.00694444,1e-12,
catttaattat
a,c,0.96843,2.47302e-08,-0.984088,2,5.10728e-08,-1.41421,0.00591716,1e-13,
ttatttaattat
a,t,0.328702,8.39385e-09,-0.573325,2.26087,5.77344e-08,-1.50362,0.00510204,1e-14,
tgtttaattat
t,t,1.10414,2.81958e-08,-1.05078,2,5.10728e-08,-1.41421,0.00591716,1e-13,
tattttaattat
t,g,0.292735,7.47539e-09,-0.54105,2.12903,5.43678e-08,-1.45912,0.00510204,1e-14,
aggtaattat
t,t,3.72127,9.50278e-08,-1.92906,2.22222,5.67475e-08,-1.49071,0.00694444,1e-12,
ggtgtaattat
t,g,0.782503,1.99823e-08,-0.884592,2,5.10728e-08,-1.41421,0.00591716,1e-13,
cactaattat
a,c,3.23011,8.24853e-08,-1.79725,2.27586,5.81173e-08,-1.5086,0.00694444,1e-12,
tgctaattat
a,g,3.28707,8.39398e-08,-1.81303,2.05882,5.25749e-08,-1.43486,0.00694444,1e-12,
agctaattat
c,g,2.90353,7.41456e-08,-1.70397,2.88136,7.35794e-08,-1.69746,0.00694444,1e-12,
cagctaattat
g,t,0.768943,1.9636e-08,-0.876894,2.47059,6.30899e-08,-1.57181,0.00591716,1e-13,
gcctaattat
t,a,3.25666,8.31633e-08,-1.80462,2,5.10728e-08,-1.41421,0.00694444,1e-12,
ttctaattat
g,g,3.28343,8.38469e-08,-1.81202,2.27273,5.80372e-08,-1.50756,0.00694444,1e-12,
t,c,3.6487,9.31746e-08,-1.91016,3,7.66091e-08,-1.73205,0.00694444,1e-12,
```
