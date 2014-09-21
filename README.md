AMC (Chen and Liu, KDD 2014)
===

AMC is an open-source Java package implementing the algorithm proposed in the paper (Chen and Liu, KDD 2014), created by [Zhiyuan (Brett) Chen](http://www.cs.uic.edu/~zchen/). For more details, please refer to [this paper](http://www.cs.uic.edu/~zchen/papers/KDD2014-Zhiyuan(Brett)Chen.pdf).

If you use this package, please cite the paper: __Zhiyuan Chen and Bing Liu. Mining Topics in Documents: Standing on the Shoulders of Big Data. In Proceedings of _KDD 2014_, pages 1116-1125__.

If you have any question or bug report, please send it to Zhiyuan (Brett) Chen (czyuanacm@gmail.com).

## Table of Contents
- [Quick Start](#quickstart)
- [Commandline Arguments](#commandlinearguments)
- [Input and Output](#inputandoutput)
- [Efficiency](#efficiency)
- [Contact Information](#contactinformation)

<a name="quickstart"/>
## Quick Start

First, Clone the repo: `git clone https://github.com/czyuan/AMC.git`.

Then, 2 quick start options are available:

1. Import the directory into Eclipse (__recommended__).

  _If you get the exception Java.Lang.OutOfMemoryError, please increase the Java heap memory for Eclipse: http://www.mkyong.com/eclipse/eclipse-java-lang-outofmemoryerror-java-heap-space/._
  
2. Use [Maven](http://maven.apache.org/guides/getting-started/maven-in-five-minutes.html)

  a. Then, change the current working directory to Src.
  ```
  cd AMC/Src
  ```
  b. Build the package.
  ```
  mvn clean package
  ```
  c. Increase the Java heap memory for Maven.
  ```
  export MAVEN_OPTS=-Xmx1024m
  ```
  d. Run the program.
  ```
  mvn exec:java -Dexec.mainClass="launch.MainEntry"
  ```

<a name="commandlinearguments"/>
## Commandline Arguments
The commandline arguments are stored in the file "global/CmdOption.java". If no argument is provided, the program uses the default arguments. There are several arguments that are subject to change:

1. -ismall: the path of input domains directory containing small dataset (e.g., 100 reviews each domain).
2. -ibig: the path of input domains directory containing big dataset (e.g., 1000 reviews each domain).
3. -o: the path of output model directory.
4. -nthreads: the number of threads used in the program. The program runs in parallel supporting multithreading.
5. -nTopics: the number of topics used in Topic Model for each domain.

<a name="inputandoutput"/>
## Input and Output
### Input
The input directory should have two datasets, one is small and the other is big. Each dataset contains domain files. For each domain, there should be 2 files (can be opened by text editors):

1. domain.docs: each line (representing a document) contains a list of word ids.
2. domain.vocab: mapping from word id (starting from 0) to word, separated by ":".

### Output
The output directory contains topic model results for LDA and AMC.

Under each model (LDA or AMC) directory, we have results for different datasets (small or big). Under the sub-folder "DomainModels", there are a list of domain folders where each domain folder contains topic model results for each domain. Under each domain folder, there are 9 files (can be opened by text editors):

1. domain.docs: each line (representing a document) contains a list of word ids.
2. domain.dtopicdist: document-topic distribution.
3. domain.knowl_mustlinks: record the must-links used in the model (for AMC only).
4. domain.knowl_cannotlinks: record the cannnot-links used in the model (for AMC only).
5. domain.param: parameter settings.
6. domain.tassign: topic assignment for each word in each document.
7. domain.twdist: topic-word distribution
8. domain.twords: top words under each topic. The columns are separated by '\t' where each column corresponds to each topic.
9. domain.vocab: mapping from word id (starting from 0) to word.

<a name="efficiency"/>
## Efficiency
The program and parameters are set to achieve the best performance in terms of topic coherence quality, instead of efficiency. There are several ways to improve efficiency (from the simplest to the hardest).

1. Increase the number of threads in the program (specified by -nthreads in file "global/CmdOption.java"). The topic models are execuated in parallel in each domain using multithreading.
2. Use a better implementation for Apriori algorithm or use faster frequent itemset algorithm such as FP-growth.

<a name="contactinformation"/>
## Contact Information
* Author: Zhiyuan (Brett) Chen
* Affiliation: University of Illinois at Chicago
* Research Area: Text Mining, Machine Learning, Statistical Natural Language Processing, and Data Mining
* Email: czyuanacm@gmail.com
* Homepage: http://www.cs.uic.edu/~zchen/
