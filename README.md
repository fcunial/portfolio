Coding portfolio
=========

**Warning: this repository includes some large (15MB) data files, to make testing straightforward.**

Example of Java code
------------

Directory `surprisingStrings` contains a prototype implementation of the following paper:

* D. Belazzougui, F. Cunial (2015). [Space-efficient detection of unusual words](https://link.springer.com/chapter/10.1007/978-3-319-23826-5_22). SPIRE 2015, LNCS, volume 9309, pp 222-233.

The program detects all substrings, of any length, that occur in a text more frequently or less frequently than expected, according to an IID or a Markov model. The program can use multiple threads, and it is space-efficient since it is based on the Burrows-Wheeler transform.

**Requirements**

* A 64-bit Java compiler. The code was tested on javac 9.0.1.
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

This simple program builds the BWT of `NC_021658.fna` and prints to STDOUT all substrings whose exact frequency is at least 100 times bigger than expected according to an IID model (the model can easily be made non-uniform, e.g. by making character probabilities match their frequencies in the input). Please interrupt the program with CTRL+C, because the list of surprising strings is very long.

In the output, each string is followed by its frequency and by the following scores:
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

Example output:
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
