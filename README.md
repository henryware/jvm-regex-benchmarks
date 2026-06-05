All plots are published at https://henryware.github.io/jvm-regex-benchmarks

# Overview

Speed test of various JVM regex libraries and C libraries callable
from the JVM.  Following in the tradition of
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

Some of the engines have a scan to identify interesting/uninteresting
(sub)strings.

# The Contenders

Some are Jars, some are libraries.  Except for KMY and JITrex, all are
maintained.  Most are in active developement.

All the jars are comparable in size at a modest 100 to 200 kB. 

All do Unicode.  All passed some sanity checks and properties tests of
basic functionality.  Most have extra features, but these vary quite a
bit and are generally not tested here.

Versions and latest release dates are in the [project
file](project.scala)

The libraries are widely used C/C++ libraries called via the JVM's FFI.

### Current Contenders

- **JavaUtil** version included in Java.  Backtracking NFA.  The
  standard.  Superpower: it's the standard.

- [**Brics Automaton**](https://www.brics.dk/automaton/) BSD license.
  DFA.  Called *DkBrics* in the code and graphs.  Superpower:
  regular-language algebra — intersection, union, complement,
  difference, reversal, finite-string enumeration.  Also
  programatically exposes the automation, which, while not a
  full Superpower is pretty cool.

- [**MonqJFA**](https://codeberg.org/harald/monqjfa) GPL2
  license. DFA.  I couldn't find this from Ivy/Maven but a jar is
  available.  MonqJFA has output-action machinery adjacent to
  capture, but it isn't exposed as standard `(group)` capture and the
  adapter here doesn't wire any of it up.  Superpower: tooling inside
  DFA matches

- [**RE2/J**](https://github.com/google/re2j) Go license.
  Non-backtracking NFA.  Related to the engine in Go and the Re2 C++
  libraries.  Superpower: bulletproof due to its solidly linear
  implementation. ReDOS immune.

- [**floriangerl**](https://github.com/florianingerl/com.florianingerl.util.regex)
  MIT license.  Backtracking NFA.  Superpower: recursive regular
  expressions

- [**Joni**](https://github.com/jruby/joni) MIT license.  Backtracking
  NFA.  Superpowers: capture history as well as configurable input
  encoding and regex syntax dialect.  The main driver feeds it UTF-8.
  We also have the **JoniUTF16** driver which is faster on CJK.

- [**kmy.regex.util**](https://jint.sourceforge.net/javadoc/kmy/regex/util/Regex.html)
  Artistic license.  Very abandoned, but was the most performant
  backtracking implementation in 2015 at
  [regex-libraries-benchmark](https://github.com/gpanther/regex-libraries-benchmarks)
  Compiles the regex to bytecode.  *Not recommended*: code is old and
  creaky and maybe was never exactly finished.   JITrex has the same
  approach and doesn't have these problems.

- [**Humio JITrex**](https://github.com/humio/jitrex) Apache license.
  Backtracking NFA.  Update of the KMY code — the working version of
  that experiment.  Superpower: compiles the regex to JVM bytecode at
  pattern-compile time, so matching is direct bytecode execution
  rather than NFA-state interpretation.  Likely unmaintained — last
  known activity around the Humio→CrowdStrike acquisition (early
  2021); we run bundled jar 0.1.17.
  
- [**Amygdalum**](https://patternsearchalgorithms.amygdalum.net/)
  LGPL.  DFA.  Superpower is streaming input.

- [**Needle**](https://github.com/hyperpape/needle) MIT.  DFA with
  Perl style ambiguity resolution.  Still under construction, but
  basic functionality implemented and working.  Superpower: compiles
  the regex to JVM bytecode at either run time or build time.

- [**Pcre2FFI**](https://github.com/PCRE2Project/pcre2) BSD-ish
  license.  Backtracking NFA.  Requires library.  Perl Compatable RE
  library.  Compiles regex to its private bytecode.
  
- [**Re2FFI**](https://github.com/google/re2) BSD license.
  Backtracking NFA with DFA cache.  Requires library.
  Nonbacktracking.  Natively C++, we ship a C shim so FFI can call it.

- [**HyperscanFFI**](https://www.hyperscan.io) BSD license.  Requires
  library.  Focus on runtime vs compile time and on SIMD
  optimizations.  Seems especially focused on the common regex use
  case of scanning log streams.  Doesn't exactly pass all the tests---
  returns more potential matches and doesn't handle zero length
  matches at all.  Hyperscan's `locate*` methods exist and are
  benchmarked but maybe shouldn't be, as they are not compatable.

### Also Benchmarked

- **BricsScreen** an alternative driver I wrote for Brics, to avoid
  the `O(N²)` worst case of the stock driver on "almost matches"
  (e.g. `a(.*X)?` against text with no X).  Builds a reverse automaton
  from `(re ~ .*).reverse`, screens end-points in `O(N)`, then runs
  the forward automaton only from those points — turning the bad cases
  from `O(N²)` to `O(N)`.  Costs vs stock Brics: extra compile time
  and memory (two automata) and `O(N)` runtime space.  This trick is
  only possible because Brics exposes its regular-language algebra.
  
### Not (yet) contenders

- **Lucerne** seems to use Brics with less tooling, so I didn't test it

- [**DFA-Regex**](https://github.com/zhztheplayer/DFA-Regex) Odd match semantics, ASCII only, abandoned since 2016.

- harpocrates? 


# Benchmarks

After the hierarchy in https://swtch.com/~rsc/regexp/regexp3.html

### Whole Match

Does the regular expression match the whole input?

- [**WholeMatch DotStar Ascii**](https://henryware.github.io/jvm-regex-benchmarks/WholeMatch_DotStar_Ascii.html) `/.*/` vs `<random string of ascii printables>`.

### Partial Match

Does the regular expression match a substring of the input?

- [**Match Phone Ascii Hit**](https://henryware.github.io/jvm-regex-benchmarks/Match_Phone_Ascii_Hit.html)  `/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/` vs  `⟨random ascii printable string⟩⟨us style phoneNumber⟩`

- [**Match Phone Ascii Miss**](https://henryware.github.io/jvm-regex-benchmarks/Match_Phone_Ascii_Miss.html)  `/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/` vs  `⟨random ascii printable string⟩`

- [**Match Abc Ascii Hit**](https://henryware.github.io/jvm-regex-benchmarks/Match_Abc_Ascii_Hit.html) `/[ -~]*ABCDEFGHIJKLMNZ/` vs `<ascii printable>ABCDEFGHIJKLMNZ`.

- [**Match Phone Cjk Hit**](https://henryware.github.io/jvm-regex-benchmarks/Match_Phone_Cjk_Hit.html)  `/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/` vs `⟨random CJK Unified Ideographs⟩⟨us style phoneNumber⟩`

- [**Match Phone Cjk Miss**](https://henryware.github.io/jvm-regex-benchmarks/Match_Phone_Cjk_Miss.html)  `/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/` vs `⟨random CJK Unified Ideographs⟩`

### Locate First

Does the regular expression match a subset of the string?  What is the location of the first such match? 

- [**Locate Phone Ascii Hit**](https://henryware.github.io/jvm-regex-benchmarks/Locate_Phone_Ascii_Hit.html)
  `/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/` vs `⟨random
  ascii printable string⟩⟨phoneNumber⟩`

- [**Locate Phone Ascii Miss**](https://henryware.github.io/jvm-regex-benchmarks/Locate_Phone_Ascii_Miss.html)  `/(?:\d{3}\s?-\s?|\(?:\d{3}\)\s{0,2})(?:\d{3}-\d{4})/` vs  `⟨random ascii printable string⟩`

- [**Locate Company Cjk Hit**](https://henryware.github.io/jvm-regex-benchmarks/Locate_Company_Cjk_Hit.html)  `/[一-鿿]{2,8}(?:株式会社|有限公司|股份有限公司)(?:第[0-9一-鿿]{1,3}号)?/` vs `⟨random CJK Unified Ideographs⟩⟨company name⟩`

- [**Locate Company Cjk Miss**](https://henryware.github.io/jvm-regex-benchmarks/Locate_Company_Cjk_Miss.html)  `/[一-鿿]{2,8}(?:株式会社|有限公司|股份有限公司)(?:第[0-9一-鿿]{1,3}号)?/` vs `⟨random CJK Unified Ideographs⟩`

### Locate All

Does the regular expression match a subset of the string?  What are the locations of the such matches? 

- [**LocateAll Torture Test**](https://henryware.github.io/jvm-regex-benchmarks/LocateAll_Torture_Test.html)  `/a(.*X)?/g` vs `a+`.  All tested implementations struggled with this usage pattern (ie `O(N²)`).   Proper parsers are not obsolete.

### Backtracking Torture Test

- [**Backtrack Torture Test**](https://henryware.github.io/jvm-regex-benchmarks/Backtrack_Torture_Test.html)
  `/(a?){N}a{N}/` vs `a{N}` This is exponential for backtracking
  implementations and trival for non-backtracking implementations

### Compilation Times

- [**Compile Jumbo**](https://henryware.github.io/jvm-regex-benchmarks/Compile_Jumbo.html)  Compile a pattern of the form 'word|locution|morpheme|..." with (mostly) 9 character long English words.

### Lacuna

#### Line test

A common regex uses case is work on lines of text of, say, 120 characters.   None of these benchmarks are exactly like that.  We do, however, test against single lines of 64 and 128 chars.

#### DFA Torture test
For DFAs, compiling `/a(a|b){N}x/` is exponential in space.  Not currently tested or benchmarked.

#### Locate All with Submatches

Does the regular expression match a subset of the string?  What are the locations and submatches of the such matches?

This is a common poor-man's-parser use case.  Not currently benchmarked.

# Instructions

The only prerequisit for the JVM engines is the Scala cli.

#### Library Install (Optional)
To run the FFI engines, you need the libraries.  I installed them with:

```
apt install libpcre2-dev  libhyperscan-dev libre2-dev
```

The Re2 engine also needs a shim.  If you have g++:  ```make re2```

If any or all of the libraries are not installed, the other engines should still work.

#### Running

If you want to make a repl:

```
scala repl .
```

If you want to run the sanity tests:

```
scala test .
``` 

To run the JMH benchmarks and generate the CSV (warning: takes about a day):

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

