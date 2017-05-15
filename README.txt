Copyright (C) 2014-2017 Roger L Deran, all rights reserved. 

# AirConcurrentMap 

A fast, memory-efficient, multi-core implementation of the 
java.util.concurrent.ConcurrentNavigableMap interface which is 
compatible with Java 1.8 and later. This makes it a drop-in 
replacement for any other Map (with Comparable keys). The 
efficiency and performance measurements are documented 
graphically in [AirConcurrentMap Performance 
Testing.pdf](https://boilerbay.com/docs/AirConcurrentMap_Performance_Testing.pdf) 
There are performance tests in test/src/jmh which show similar 
results in a different way using the now-standard [Java 
Microbenchmark 
Harness](http://openjdk.java.net/projects/code-tools/jmh/). 
Also see 
[TwitterDemo.java](https://boilerbay.com/docs/TwitterDemo.java). 
It has high-performance extensions for scanning serially or in 
parallel. 

See <https://boilerbay.com/airmap>. 

--- 

# Advantages 

Here is a list of advantages: *Implementation Notes* at the 
bottom talks about limitations. 

### Memory efficient 

   It exceeds the efficiency any other Java Map in java.util or 
   java.util.concurrent for more than about 500 entries. About 
   70% to 50% of relative size is reached. There is a minimum 
   size of very roughly 5K, which is configurable. Freed space 
   after clear() or remove() is always returned to the JVM 
   except for the initial 5K chunk. (Note not all Collection 
   implementations return freed space to the JVM: for example 
   ArrayList never shrinks, and the table in a HashMap never 
   shrinks). 

### Faster key-Based operations than ConcurrentSkipListMap 

   It consistently exceeds by a large margin the performance of 
   its only ordered competitor, which is 
   java.util.concurrent.ConcurrentSkipListMap, for a wide 
   variety of Thread counts, operation mixtures, and memory 
   sizes. These two concurrent ordered Maps grow from equal 
   performance at 1000 entries to about 90% faster for 
   AirConcurrentMap for get, put, and remove plus the other 
   special NavigableMap operations lower, floor, higher, and 
   ceiling and others. 

### Multi-core 

   It allows Threads to spread their work out amongst the 
   available cores. It scales well as the core count increases, 
   and its performance rolls off slowly as Thread count reaches 
   hundreds of Threads. For example, web servers benefit from 
   reduced hit overhead for shared data. 

### Faster Iterators 

   Iteration is particularly fast, reaching two to four times 
   the speed of any other java.util or java.util.concurrent Map 
   as entry count increases, except that it is slightly slower 
   than ConcurrentSkipListMap for small Maps of less than about 
   1K Entries. 

### Faster forEach() 

   Faster than for any java.util or java.util.concurrent Map. 
   It is almost as fast as the MapVisitor extension. 

   The special MapVisitor feature allows very fast sequential 
   access. Usage is simple, especially compared to the Java 8 
   stream system, and clients usages only need a construction, 
   while minimal visitor classes need only a 'visit()' method. 
   Visitors are much faster than Iterators in Java version 1.8 
   and all Java library Map types. Often, visitor classes are 
   reused to provide clear functional, declarative code: see 
   com.infinitydb.map.visitor.VisitorExample.java. Also see 
   [TwitterDemo.java](https://boilerbay.com/docs/TwitterDemo.java). 
   Below is a trivial sequential visitor example. It looks very 
   much like a BiConsumer but is a class, not an interface. 

```java
        static class Summer extends MapVisitor<Object, Number> {

            long sum;

            // Optional for client convenience.    
            public long getSum(VisitableMap< ? , Number> map) {
                sum = 0;
                map.visit(this);
                return sum;
            }
            @Override
            public void visit(Object key, Number value) {
                sum += value.longValue();
            }

        }

``` 

Then, the client can just do: 

```java
        System.out.println("sum=" + new Summer().getSum(map));

``` 

### Fastest Threaded Scanning 

   There is also a ThreadedMapVisitor system that is a simple 
   extension to MapVisitor and which is much faster than the 
   Java 8 parallel streams and less peaky. Parallel streams 
   only work well for intermediate-size Maps - they are very 
   slow for small or large Maps. There are two minimal 
   additional methods split() and merge() to implement in a 
   MapVisitor to make it into a ThreadedMapVisitor. 
   AirConcurrentMap manages the Thread pool internally and 
   transparently for optimal performance. See the javadoc for 
   com.infinitydb.map.visitor.MapVisitor and its extension, 
   ThreadedMapVisitor. Scheduling is balanced and low-overhead, 
   with few stragglers to slow the entire scan. Speeds of 
   300M/s are possible in 2016 with 7 cores depending on the 
   operation, such as operations on Longs or Integers. Future 
   Maps compatible with such threading are in development. 

   Here is a ThreadedSummer subclass. It is used just like the 
   unthreaded MapVisitor. 

```java
    static class ThreadedSummer extends ThreadedMapVisitor<Object, Number> {
        // not shared between threads, so it is not an AtomicLong
        long sum;

        public long getSum(VisitableMap< ? , Number> map) {
            sum = 0;
            // here would could fall back to streams when non-visitable
            map.visit(this);
            return sum;
        }

        @Override
        public void visit(Object key, Number value) {
            sum += value.longValue();
        }

        // One of the two threading-specific methods
        @Override
        public ThreadedMapVisitor<Object, Number> split() {
            return new ThreadedSummer();
        }

        // The other threading-specific method
        @Override
        public void merge(ThreadedMapVisitor<Object, Number> visitor) {
            this.sum += ((ThreadedSummer)visitor).sum;
        }
    }

``` 

### Wrappers to make other collections visitable 

   There are wrappers for other collections to make them 
   visitable as well (which are sequential, but which will 
   become transparently threaded later on). See 
   com.infinitydb.map.wrapper.* in the javadoc. Simply 
   construct the appropriate wrapper, passing it a collection 
   such as a List, ArrayList, Set, or Map. (In the future a 
   polymorphic single wrapper class may come along.) 

### Fast Streams-like extensions 

   The classes in com.infinitydb.map.lambda provide 
   functionality similar to some serial or parallel streams 
   patterns but faster and not peaky over Map size. They use 
   ThreadedMapVisitors to get efficient threading 
   transparently. They are not as general as streams, but are 
   simple to use and clear, and many streams patterns 
   correspond directly to the lambda classes here. For example, 
   the streams-based collector might be: 

```java
    result = map.stream().parallel().map(mapper).collect(supplier, accumulator, combiner);

``` 

   while for AirConcurrentMap an additional alternative 
   corresponding but faster use might be: 

```java   
    result = new CollectingThreadedMapVisitor(
              mapper, supplier, accumulator, combiner).getResult(map);

``` 

### Ordered 

   The standard NavigableMap interface allows fuzzy prefix 
   matching for example via the key-access based 'nearness' 
   operations like higher(), lower(), ceiling() and floor(). 
   The ordering has a performance cost for key-based accesses 
   compared to hash maps as in all Maps. It Iterates and scans 
   via visitors in order, allowing a broader stronger set of 
   client algorithms, features, and client performance 
   optimizations. Functions like differencing of adjacent 
   entries, or moving averages or sequential graphing are 
   possible examples. 

### Does not stall 

   HashMaps stall while they are rehashing large tables for up 
   to 30% of the time while growing and at random other times 
   during put(), depending on size, making them useless in many 
   applications that need low latency. Also, the tables in hash 
   maps become large arrays that clog the GC system, slowing 
   the entire JVM at random times, especially as Map size 
   increases to about 1/2 memory size. AirConcurrentMap 
   minimizes the load on the garbage collector: for example, 
   its visitor scanning technique avoids constructing an Entry 
   such as the one returned normally by Iterator.next(). 

### Totally Compatible 

   It is a plug-in compatible replacement for any of the Maps 
   defined in the Java standard libraries, because 
   ConcurrentNavigableMap in turn implements Map, SortedMap, 
   NavigableMap, and ConcurrentMap, which cover all of the 
   ground. 

   AirConcurrentMap is dependent on no outside libraries or 
   classes and does not use JVM-implementation-specific classes 
   like the com.sun packages. It does not use the dangerous 
   JVM-specific access called 'unsafe' to break through the 
   Java sandbox into the underlying native level, as 
   ConcurrentSkipListMap and others do to get access to the 
   structures inside Objects. AirConcurrentMap uses no native 
   code via JNI. 

   Simply include airconcurrentmap.jar in your CLASSPATH and 
   change the constructors of selected Maps in your code to 
   com.infinitydb.map.air.AirConcurrentMap(). Try out the 
   extensions too. 

### Thread safe and overlapping execution 

   Using other non-thread-safe Maps in multi-threaded 
   environments is dangerous, because any accidental sharing of 
   Maps between Threads can cause not only garbage results, but 
   can corrupt any Map's internal data structures, resulting in 
   bugs that are rare, difficult to reproduce, and difficult to 
   understand. AirConcurrentMap does not have this problem. 
   Read/write sharing of any non-thread safe Map will produce 
   undefined results, and write/write sharing will produce 
   undefined modification of the Map. 

--- 

## Implementation Notes 

In this version, a small AirConcurrentMap is expensive compared 
to the standard Maps, taking about 200 initially reserved 
references, but the relative efficiency increases quickly with 
map size. 

This version of AirConcurrentMap is not optimized for submaps, 
and they slow down as O(log(n)) for iteration. However, small 
ranges will still take little time. 

AirConcurrentMap delays the dangerous OutOfMemoryError, due to 
its increased memory efficiency. OutOfMemoryError is a 
catastrophe, since it can corrupt any application, system, or 
library data structure and can cause subsequent instability and 
garbage results, even across security domains. 
AirConcurrentMap, like any other data structure, cannot be made 
immune to OutOfMemoryError itself. 

Testing compared the Java standard library Maps, a lock-free 
CTrie Map, and gnu trove THashMap. CTrie is apparently not 
licensed, and may be public domain, and is slower and less 
efficient. THashMap is not ordered, concurrent, internally 
threaded for scanning, or stall-free, does not return memory 
after remove(), and is less memory efficient. 
java.util.concurrent.ConcurrentHashMap can be very inefficient 
in some situations (see the code where it creates trees under 
each bucket) is not stall-free, and does not return all memory 
after remove, although like any hashmap, it is fast for get, 
put, and remove. 

--- # Java Microbenchmarking Harness Performance Results 

The jmh/maptest directory contains a JMH test for definitive 
performance comparisons. Here are the results: 

```
    Result "testSummingStream":
    6.195 ▒(99.9%) 0.034 ops/s [Average]
    (min, avg, max) = (5.462, 6.195, 6.510), stdev = 0.144
    CI (99.9%): [6.161, 6.229] (assumes normal distribution)

    
    # Run complete. Total time: 01:58:59

    Benchmark                                                                     (mapClassName)  (mapSize)   Mode  Cnt         Score        Error  Units
    StreamsJMHAirConcurrentMapTest.testSummingStream     com.infinitydb.map.air.AirConcurrentMap          0  thrpt  200  47669627.157 ▒ 408068.881  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream     com.infinitydb.map.air.AirConcurrentMap          1  thrpt  200  36128245.803 ▒ 219021.093  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream     com.infinitydb.map.air.AirConcurrentMap         10  thrpt  200  28819134.716 ▒ 215535.681  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream     com.infinitydb.map.air.AirConcurrentMap        100  thrpt  200   5983782.906 ▒  12171.457  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream     com.infinitydb.map.air.AirConcurrentMap       1000  thrpt  200    503450.631 ▒   2160.534  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream     com.infinitydb.map.air.AirConcurrentMap      10000  thrpt  200     51363.052 ▒    192.871  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream     com.infinitydb.map.air.AirConcurrentMap     100000  thrpt  200      8785.362 ▒    180.963  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream     com.infinitydb.map.air.AirConcurrentMap    1000000  thrpt  200       280.375 ▒      1.321  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream     com.infinitydb.map.air.AirConcurrentMap   10000000  thrpt  200        18.017 ▒      0.070  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream  java.util.concurrent.ConcurrentSkipListMap          0  thrpt  200  11715613.910 ▒  30641.713  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream  java.util.concurrent.ConcurrentSkipListMap          1  thrpt  200  10587246.514 ▒  25303.461  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream  java.util.concurrent.ConcurrentSkipListMap         10  thrpt  200    476882.573 ▒  69406.838  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream  java.util.concurrent.ConcurrentSkipListMap        100  thrpt  200     92033.529 ▒   4059.402  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream  java.util.concurrent.ConcurrentSkipListMap       1000  thrpt  200     49317.703 ▒    620.204  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream  java.util.concurrent.ConcurrentSkipListMap      10000  thrpt  200     17426.535 ▒    503.417  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream  java.util.concurrent.ConcurrentSkipListMap     100000  thrpt  200      2666.609 ▒     96.302  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream  java.util.concurrent.ConcurrentSkipListMap    1000000  thrpt  200       166.188 ▒      2.526  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream  java.util.concurrent.ConcurrentSkipListMap   10000000  thrpt  200         4.473 ▒      0.136  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream      java.util.concurrent.ConcurrentHashMap          0  thrpt  200  11748597.966 ▒  55376.295  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream      java.util.concurrent.ConcurrentHashMap          1  thrpt  200   7591252.605 ▒  58655.123  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream      java.util.concurrent.ConcurrentHashMap         10  thrpt  200    192421.408 ▒   1781.336  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream      java.util.concurrent.ConcurrentHashMap        100  thrpt  200     93215.872 ▒   1053.339  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream      java.util.concurrent.ConcurrentHashMap       1000  thrpt  200     65290.405 ▒    592.948  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream      java.util.concurrent.ConcurrentHashMap      10000  thrpt  200     15459.798 ▒     37.701  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream      java.util.concurrent.ConcurrentHashMap     100000  thrpt  200      1527.065 ▒     11.372  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream      java.util.concurrent.ConcurrentHashMap    1000000  thrpt  200        70.491 ▒      1.110  ops/s
    StreamsJMHAirConcurrentMapTest.testSummingStream      java.util.concurrent.ConcurrentHashMap   10000000  thrpt  200         6.195 ▒      0.034  ops/s

``` 

--- 

# InfinityDB 

[InfinityDB](https://boilerbay.com/infinitydb), a Java embedded 
database, is also offered at boilerbay.com. It is 
high-performance, transactional, compressing, dynamic-schema, 
and noSQL. It has a totally compatible 
java.util.concurrent.ConcurrentNavigableMap interface as well 
as a simple low-level API that allows partial forwards and full 
backwards database compatibility dynamically as the application 
grows or changes and the schema changes. The API has only a few 
operations, yet it allows a wide range of superimposed 
upper-level data models, such as relational, text indexes, 
sets, key/value, taxonomies, DAGs, and mixed, plus custom and 
more. It uses techniques similar to AirConcurrentMap. Its 
ConcurrentNavigableMap adapter interface can be mixed in with 
the regular lower-level access for speed as convenient. 

For licensing, email support at boilerbay.com. Roger Deran is 
at rlderan2 at boilerbay.com. 

Copyright (C) 2014-2017 Roger L Deran, all rights reserved. 

