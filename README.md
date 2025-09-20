# Overview

Speed test of various JVM regex libraries.  Following in the tradition
of
[tusker.org](https://web.archive.org/web/20221205160707/https://tusker.org/regex/regex_benchmark.html)
and the
[regex-libraries-benchmark](https://github.com/gpanther/regex-libraries-benchmarks),
this is a effort to benchmark as many non-orphaned libraries as I
could find.

# Implementation types
There are 3 main implementation approaches: backtracking NFA,
non-backtracking NFA and DFA.  They have very different performance
characteristics in terms of the length of the pattern (`M`) and the
length of the text (`N`).

Backtracking NFAs, aka Perl-style, have all the familiar features and
compilation is very quick.  They can support a rich set of boundary
conditions as well as back-references and submatches.  Execution,
while good in many common cases, is `O(2ᴺ)` in some not very remote
corner cases.
 
DFAs, aka grep-style, are the headline implementation in compiler classes.
FullMatch is always a fast `O(N)`, others functions worse case to a 
respectable `O(N²)`.  However, compilation can be `O(2ᴹ)` in space and 
time in corner cases.  These do not support submatches, boundary conditions 
or back-references.

Non-backtracking NFAs, aka Thompson's construction, combine good
`O(NM)` execution with good `O(M)` compilation at the expense of a
higher constant.  Support for submatches and simple boundary
conditions is included.

The DFAs use POSIX style ambiguity resolution: they return the
leftmost/longest match.  The NFAs are more complicated: `a|b|ab` is
not the same as `ab|a|b`.  When repeatedly matched against "ab" the
first returns "a","b" and the second "ab".

# The Contenders

Except for KMY:

- all of these are maintained— most are in active development.

- all jars are comparable in size at 100 to 200 kB. 

All do Unicode and passed some sanity checks and properties tests of
basic functionality.  Most have extra features, but these vary quite a
bit.

Versions and latest release date are in the [project
file](project.scala)

- **JavaUtil** version included in Java.  Backtracking NFA.  The
  standard.  Superpower: it's the standard.

- [**Brics Automaton**](https://www.brics.dk/automaton/) BSD license.
  DFA.  Called *DkBrics* in the code and graphs.  Superpower: support
  for intersection, inversion, reversal, enumeration, and other set
  operations

- [**MonqJFA**](https://codeberg.org/harald/monqjfa) GPL2
  license. DFA.  I couldn't find this from Ivy/Maven but a jar is
  available.  This has some support for capturing groups, but I have
  not yet written an adapter.  Superpower: tooling inside DFA matches

- [**RE2/J**](https://github.com/google/re2j) Go license.  Related to
  the engine in Go and the Re2 C++ libraries.  Non-backtracking NFA.
  Re2 includes a DFA, not sure if this does.  Superpower: bulletproof
  due to it's solidly polynomial implementation

- [**floriangerl**](https://github.com/florianingerl/com.florianingerl.util.regex)
  MIT license.  Backtracking NFA.  Superpower: recursive regular
  expressions

- [**Joni**](https://github.com/jruby/joni) MIT license.  Seems to be
  a backtracking NFA.  Natively uses UTF-8 bytes as input— tested via
  a java.lang.Character based API.  Superpower: Even faster with UTF-8
  input

- [**kmy.regex.util**](https://jint.sourceforge.net/javadoc/kmy/regex/util/Regex.html)
  Artistic license.  Very abandoned, but was the most performant
  backtracking implementation in 2015 at
  [regex-libraries-benchmark](https://github.com/gpanther/regex-libraries-benchmarks)
  Compiles the regex to bytecode.  Not recommended: code is old and
  creaky and maybe was never exactly finished.   JITrex has the same
  approach and doesn't have these problems.

- [**Humio JITrex**](https://github.com/humio/jitrex) Apache license.
  Update of the KMY code.  Backtracking NFA.  Superpower: compiles the
  regex to bytecode.  Recently updated but possibly now unmaintained;
  running version 0.1.17 from a jar.

Also benchmarked:

- **BricsScreen** a different driver I wrote for Brics to avoid
  `O(N²)` behavior by screening the possible start locations.  This
  costs `O(N)` space and time at runtime as well as time and memory at
  compile.  Also, it is not compatable with streams.

Not (yet) contenders:

- **Lucerne** seems to use Brics with less tooling, so I didn't test it

- hyperscan? harpocrates? [needle](https://github.com/hyperpape/needle)? 

# Benchmarks

After the hierarchy in https://swtch.com/~rsc/regexp/regexp3.html

### Whole Match

Does the regular expression match the whole input?

- [**DotStar vs Long Text**](https://henryware.github.io/jvm-regex-benchmarks/DotStar_vs_Long_Text.html) `/.*/` vs `<random string of ascii printables>`.

### Partial Match

Does the regular expression match a substring of the input?

- [**Match Phone Number in Long Text**](https://henryware.github.io/jvm-regex-benchmarks/Match_Phone_Number_in_Long_Text.html)  `/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/` vs  `⟨random ascii printable string⟩⟨us style phoneNumber⟩`

- [**Fail to Match Phone Number in Long Text**](https://henryware.github.io/jvm-regex-benchmarks/Fail_to_Match_Phone_Number_in_Long_Text.html)  `/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/` vs  `⟨random ascii printable string⟩`

- [**Match ABC in Long Text**](https://henryware.github.io/jvm-regex-benchmarks/Fail_to_Match_Phone_Number_in_Long_Text.html) `/[ -~]*ABCDEFGHIJKLMNZ/` vs `<ascii printable>ABCDEFGHIJKLMNZ`.

### Locate First

Does the regular expression match a subset of the string?  What is the location of the first such match? 

- [**Locate Phone Number in Long
  Text**](https://henryware.github.io/jvm-regex-benchmarks/Locate_Phone_Number_in_Long_Text.html)
  `/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/` vs `⟨random
  ascii printable string⟩⟨phoneNumber⟩`

- [**Fail to Locate Phone Number in Long Text**](https://henryware.github.io/jvm-regex-benchmarks/Fail_to_Locate_Phone_Number_in_Long_Text.html)  `/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/` vs  `⟨random ascii printable string⟩`

### Locate All

Does the regular expression match a subset of the string?  What are the locations of the such matches? 

- [**Locate All Torture Test**](https://henryware.github.io/jvm-regex-benchmarks/Locate_All_Torture_Test.html)  `/a(.*X)?/g` vs `a+`.  All tested implementations struggled with this pattern (ie `O(N²)`).

### Backtracking Torture Test

- [**Backtrack Torture
  Test**](https://henryware.github.io/jvm-regex-benchmarks/Backtrack_Torture_Test.html)
  `/(a?){N}a{N}/` vs `a{N}` This is exponential for backtracking
  implementations and trival for non-backtracking implementations

### Compilation Times

- [**Compile Long Pattern**](https://henryware.github.io/jvm-regex-benchmarks/Compile_Long_Pattern.html)  Compile a pattern of the form 'word|locution|morpheme|..." with (mostly) 9 character long English words.

### Lacuna

#### Line test

Alot of regexes work on lines of text of, say, 120 characters.   None of these benchmarks are like that.

#### DFA Torture test
For DFAs, compiling `/a(a|b){N}x/` is exponential in space.  Not currently tested.

#### Locate All with Submatches

Does the regular expression match a subset of the string?  What are the locations and submatches of the such matches?

This is a common poor-man's-parser use case.  Not currently tested

# Instructions

If you want to make a repl:

```
scala repl .
```

If you want to run the sanity tests:

```
scala test .
``` 

To run the JMH benchmarks and generate the CSV (warning: not fast):

```
scala run --power --jmh . -- -rf csv
```

To generate the plots, from the CSV:

```
echo "worldofregex.Graph.plot()" | scala repl .
```

Plots will be written to the ./plots directory and linked from ./plots/index.html

# Results

All plots are published at https://henryware.github.io/jvm-regex-benchmarks

