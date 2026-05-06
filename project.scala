//>using jvm 25
//>using scala 3.8.1


// turn off scary warnings reguarding lazy vals
//>using javaOpt --sun-misc-unsafe-memory-access=allow

// Panama FFI for native regex engines (PCRE2, Hyperscan, RE2)
//>using javaOpt --enable-native-access=ALL-UNNAMED

// this is literally a dictionary.
//>using jar lib/dictionary.jar

// ====
//  Brics Automaton Jan 2022
//  https://www.brics.dk/automaton/
//  BSD

//> using dep dk.brics:automaton:1.12-4

// ====
// MonqJFA from Aug 2025
// https://codeberg.org/harald/monqjfa
// curl -OJ https://codeberg.org/api/packages/harald/generic/monqjfa/4.3.0/monq-4.3.0.jar
// GPL2

//> using jar lib/monq-4.3.0.jar

// ====
// Jint KMY from 2002(!)
// Version 0.1.2
//

//> using jar lib/jint.jar

// ====
// RE2/J 1.8, released on March 2024
//  Go license

//> using dep com.google.re2j:re2j:1.8

// ====
// florianingerl 1.1.11 released June 2024
// MIT

//> using dep com.github.florianingerl.util:regex:1.1.11

// ====
// Joni 2.2.5 release March 2025
// MIT

//> using dep org.jruby.joni:joni:2.2.7

// ====
// JiTrex 0.1.17 release ?
// Apache

//> using jar lib/jitrex-0.1.17.jar

// ====
// Pattern Search Algorithms (amygdalum) 0.1.3 released 2024
// https://patternsearchalgorithms.amygdalum.net/
// https://github.com/almondtools/patternsearchalgorithms
// LGPL

//> using dep net.amygdalum:patternsearchalgorithms:0.1.3


// ====
// Needle 0.0.1 release May 2024
// https://github.com/hyperpape/needle
// MIT
// Parent POM `com.justinblank:needle:0.0.1` was never published to Maven
// Central, so `//> using dep com.justinblank:needle-compiler:0.0.1` fails to
// resolve.  Workaround: take the jars directly and pull the transitives by
// explicit dep.

//> using jar lib/needle-compiler-0.0.1.jar
//> using jar lib/needle-types-0.0.1.jar
//> using dep com.justinblank:mako:0.0.4
//> using dep org.apache.commons:commons-lang3:3.8
//> using dep net.bytebuddy:byte-buddy:1.10.20

// ====
// Lucerne 10.1.0 release Dec 2024
// Apache
// Seems to based on Brics with less support tooling

// //> using dep org.apache.lucene:lucene-core:10.1.0


// ====
// harpocrates regex-automata from July 2022
// https://github.com/harpocrates/regex-automata
// had to pull, build and package
//
// taking it out, isn't finished or active
// //> using dep org.ow2.asm:asm:9.8
// //> using jar lib/regex-automata-0.1.0-SNAPSHOT.jar


// benchmarking code is not officially a test 
//> using dep org.openjdk.jmh:jmh-core:1.37

// this generates scalacheck examples from a regex pattern 
//> using dep io.github.wolfendale::scalacheck-gen-regexp:1.1.0

// for reading the benchmarks from csv and plotting them 
//> using dep com.github.tototoshi::scala-csv:2.0.0
//> using dep org.plotly-scala:plotly-render_2.13:0.8.5

// here down are test dependencies

// //> use testframework ScalaTest
// org.scalatest.tools.Runner
// org.scalatest.run(new MyTest)

//> using dep org.scalacheck::scalacheck:1.19.0
//> using dep org.scalatest::scalatest:3.2.20
//> using dep org.scalatestplus::scalacheck-1-18:3.2.19.0

//> using test.dep org.scalameta::munit::1.3.0
//> using test.dep org.scalameta::munit-scalacheck::1.3.0

//> using dep io.github.martinhh::scalacheck-derived:0.10.0
