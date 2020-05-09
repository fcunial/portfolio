Coding portfolio
=========

**Warning: this repository includes some large (15MB) data files, to make testing straightforward.**

Example of Java code
------------

Directory surprisingStrings contains a prototype implementation of the following paper:

* D. Belazzougui, F. Cunial (2015). [Space-efficient detection of unusual words](https://link.springer.com/chapter/10.1007/978-3-319-23826-5_22). SPIRE 2015, LNCS, volume 9309, pp 222-233.

The program detects all substrings, of any length, that occur in a text more frequently or less frequently than expected according to an IID or a Markov model. The program can use multiple threads and is space-efficient since it is based on the Burrows-Wheeler transform.

Requirements
------------

* A 64-bit Java compiler. The code was tested on javac 9.0.1.
* A 64-bit operating system. The code was tested on macOS 10.13.

Compiling:
```
cd surprisingStrings
javac -classpath .:./commons-math3-3.5.jar *.java
```

Since this code is still a prototype, I created a test program with hardwired input arguments, to make running it easier. The program uses file `NC_021658.fna` included in the repository.

Example run using T parallel threads:
```
java -classpath .:./commons-math3-3.5.jar TestDrive T
```

