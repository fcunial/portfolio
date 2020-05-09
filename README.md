Coding portfolio
=========

Example of Java code
------------

Directory surprisingStrings contains a prototype implementation of the following paper:

* D. Belazzougui, F. Cunial (2015). [Space-efficient detection of unusual words](https://link.springer.com/chapter/10.1007/978-3-319-23826-5_22). SPIRE 2015, LNCS, volume 9309, pp 222-233.

The program detects all substrings, of any length, that occur in a text more frequently or less frequently than expected according to an IID or a Markov model. The program can use multiple threads and is space-efficient since it is based on the Burrows-Wheeler transform.

Requirements
------------

* A modern, 64-bit Java compiler.
* A 64-bit operating system. The code was tested on macOS 10.13.

Compiling:
```
cd surprisingStrings
javac -classpath .:./commons-math3-3.5.jar *.java
```

Example run with T threads:
```
java -classpath .:./commons-math3-3.5.jar TestDrive T
```

