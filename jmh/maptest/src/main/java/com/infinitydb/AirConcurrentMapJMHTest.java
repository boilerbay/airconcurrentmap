// Copyright (C) 1997-2017 Roger L. Deran.
//
//    This file is part of AirConcurrentMap. AirConcurrentMap
//    itself is proprietary.
//
//    This file is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 2 of the License, or
//    (at your option) any later version.
//
//    This file is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    To get a copy of the GNU General Public License,
//    see <http://www.gnu.org/licenses/>.
//
//    For dual licensing of this file, see boilerbay.com. 
//    For commercial licensing of AirConcurrentMap email 
//    support@boilerbay.com. The author email is rlderan2 at boilerbay.com.

package com.infinitydb;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.infinitydb.map.air.AirConcurrentMap;
import com.infinitydb.map.visitor.MapVisitor;
import com.infinitydb.map.visitor.ThreadedMapVisitor;
import com.infinitydb.map.visitor.VisitableMap;

/**
 * The AirConcurrentMap performance tests using the defacto standard Java
 * Microbenchmarking Harness.
 * 
 * Before running these tests, install maven from https://maven.apache.org,
 * then, do this once in the root directory of the archive:
 * 
 * <pre>
 * cd jmh/maptest
 * mvn install:install-file -DgroupId=com.infinitydb \
 *  -DartifactId=airconcurrentmap -Dversion=3.1.0 -Dpackaging=jar -Dfile=../../airconcurrentmap.jar
 * </pre>
 * 
 * The above takes airconcurrentmap.java and puts it into the 'local maven
 * repository' which is actually just a cache in ~/.m2/repository where it stays
 * for all further tests (unless a newer version of airconcurrentmap.jar is
 * released, in which case you change the version number there and in pom.xml).
 *
 * Then, the following builds the test into target/benchmark.jar, so do it once
 * initially, and then again after any change to this test code:
 * 
 * <pre>
 * mvn clean install
 * </pre>
 * 
 * Then, to run a test, do for example (or just use defaults):
 * 
 * <pre>
 * java  -Xmx4g -jar target/benchmarks.jar -f 1 -i 2 -wi 5 <testName>
 * </pre>
 * 
 * The available test parameters can be shown with:
 * 
 * <pre>
 * java -jar target/benchmark.jar -h
 * </pre>
 * 
 * The bytes per entry are printed to sysout on each trial after the first
 * warmup iteration. The bytes per entry are appended to 'bytesPerEntry.txt'.
 * 
 * As new versions of airconcurrentmap come out, the version number can be
 * increased and the above done again. The pom.xml needs to have its dependency
 * changed to match in that case.
 */

@Fork(1)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
//@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class AirConcurrentMapJMHTest {

    @Param({
            "com.infinitydb.map.air.AirConcurrentMap",
            "java.util.concurrent.ConcurrentSkipListMap",
            "java.util.concurrent.ConcurrentHashMap"
    })
    static String mapClassName;
    @Param({ "0", "1", "10", "100", "1000", "10000", "100000", "1000000", "10000000" })
    static long mapSize;
    static ConcurrentMap<Long, Long> map;
    static boolean isParallelStreams = System.getProperty("parallel") != null;
    static boolean isNoVisitors = System.getProperty("no.visitors") != null;

    @Setup(Level.Trial)
    static public void setup() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Class<ConcurrentMap<Long, Long>> mapClass =
                (Class<ConcurrentMap<Long, Long>>)Class.forName(mapClassName);
        map = mapClass.newInstance();
        Random random = new Random(System.nanoTime());
        System.gc();
        BytesPerEntryReport bytesPerEntryReport = new BytesPerEntryReport();
        // Load up the Map
        for (long i = 0; i < mapSize; i++) {
            long v = random.nextLong();
            map.put(v, v);
        }
        System.gc();
        bytesPerEntryReport.print();
        // Show whether we had -Dparallel=true
        if (isParallelStreams)
            System.out.println("using parallel streams");
        // Show whether we had -Dno.visitors=true
        if (isNoVisitors)
            System.out.println("avoiding AirConcurrentMap visitors");
    }

    // Randomize between invocations
    @State(Scope.Thread)
    public static class LocalRandom {
        Random random = new Random(System.nanoTime());
    }

    // We have to do the removes too, to keep the Map the same size.
    @Benchmark
    public static long testPutAndRemove(LocalRandom localRandom) {
        long k = localRandom.random.nextLong();
        map.put(k, k);
        map.remove(k);
        return k;
    }

    @Benchmark
    public static long testGet(LocalRandom localRandom) {
        long k = localRandom.random.nextLong();
        // v is almost always null. This biases hashmaps measurements
        Long v = map.get(k);
        if (v != null)
            System.out.println("v != null");
        return v == null ? 0 : v.longValue();
    }

    @Benchmark
    public static long testIterateKeySet() {
        long sum = 0;
        for (Long k : map.keySet()) {
            sum += k.longValue();
        }
        return sum;
    }

    @Benchmark
    public static long testIterateEntrySet() {
        long sum = 0;
        for (Entry<Long, Long> e : map.entrySet()) {
            sum += e.getKey().longValue();
        }
        return sum;
    }

    @Benchmark
    public static long testIterateValues() {
        long sum = 0;
        for (Long v : map.values()) {
            sum += v.longValue();
        }
        return sum;
    }

    @Benchmark
    public static long testMapVisitor() {
        if (map instanceof VisitableMap && !isNoVisitors) {
            VisitableMap visitableMap = (VisitableMap)map;
            /**
             * AirConcurrentMap can also do forEach() but this shows the extra
             * speed of its MapVisitor technique. This can be easily extended
             * into a ThreadedMapVisitor and can implement BiConsumer too.
             */
            class V extends MapVisitor<Long, Long> {
                public long sum = 0;

                public void visit(Long k, Long v) {
                    sum += v.longValue();
                }
            }
            V visitor = new V();
            visitableMap.visit(visitor);
            return visitor.sum;
        } else {
            class Consumer implements BiConsumer<Long, Long> {
                long sum = 0;

                public void accept(Long k, Long v) {
                    sum += v.longValue();
                }
            }
            Consumer consumer = new Consumer();
            map.forEach(consumer);
            return consumer.sum;
        }
    }

    @Benchmark
    public static long testForEach() {
        if (map instanceof AirConcurrentMap && !isNoVisitors) {
            // If the BiConsumer is an extension of MapVisitor, it will be
            // faster.
            class SummingBiConsumer extends MapVisitor<Long, Long> implements BiConsumer<Long, Long> {
                long sum = 0;

                /*
                 * Implement BiConsumer, so this class is useful everywhere. If
                 * the map is a VisitableMap, it will actually use visit(Long,
                 * Long) instead for speed. In this test, the fact that we are a
                 * BiConsumer is not used except in the invocation of forEach().
                 */
                @Override
                public void accept(Long k, Long v) {
                    sum += v.longValue();
                }

                // implements MapVisitor for very high speed.
                @Override
                public void visit(Long k, Long v) {
                    sum += v.longValue();
                }
            }
            ;
            SummingBiConsumer summingBiConsumer = new SummingBiConsumer();
            map.forEach(summingBiConsumer);
            return summingBiConsumer.sum;
        } else {
            // the normal way
            class SummingBiConsumer implements BiConsumer<Long, Long> {
                long sum = 0;

                public void accept(Long k, Long v) {
                    sum += v.longValue();
                }
            }
            ;
            SummingBiConsumer summingBiConsumer = new SummingBiConsumer();
            map.forEach(summingBiConsumer);
            return summingBiConsumer.sum;
        }
    }

    @Benchmark
    public static long testStreams() {
        class SummingVisitor extends ThreadedMapVisitor<Long, Long>
                implements BiConsumer<Long, Long> {
            long sum = 0;

            long getSum(Map<Long, Long> map) {
                sum = 0;
                if (map instanceof VisitableMap && !isNoVisitors) {
                    /*
                     * The VisitableMap detects that the visitor is actually a
                     * ThreadedMapVisitor and runs parallel
                     */
                    ((VisitableMap)map).visit(this);
                } else {
                    /*
                     * Drop back to slower streams.
                     */
                    if (false) {
                        Stream<Long> stream = map.values().stream();
                        if (isParallelStreams)
                            stream = stream.parallel();
                        sum = stream.reduce(0L, (x, y) -> x + y).longValue();
                    } else {
                        // Use a LongStream for the reduce.
                        // This is apparently the best case for long streams.
                        LongStream stream = map.values().stream().mapToLong(
                                v -> ((Long)v).longValue());
                        if (isParallelStreams)
                            stream = stream.parallel();
                        // The code for sum() is just a reduce, giving the same
                        // performance.
                        sum = stream.sum();
                        // sum = stream.reduce(0L, (x, y) -> x + y);
                    }
                }

                return sum;
            }

            /*
             * Implement BiConsumer. This is optional, so SummingVisitor can be
             * used serially as a BiConsumer on any regular non-VisitableMap.
             * Actually, since SummingVisitor is a MapVisitor, and the map in
             * the test is a VisitableMap, visit() will be invoked for speed,
             * not accept().
             */
            @Override
            public void accept(Long k, Long v) {
                sum += v.longValue();
            }

            /*
             * implement MapVisitor for speed. Invoked when used with a
             * VisitableMap, possibly with multiple instances of this.
             */
            @Override
            public void visit(Long k, Long v) {
                sum += v.longValue();
            }

            // Implement ThreadedMapVisitor For parallelism
            @Override
            public SummingVisitor split() {
                return new SummingVisitor();
            }

            // Implement ThreadedMapVisitor For parallelism
            @Override
            public void merge(ThreadedMapVisitor tmv) {
                sum += ((SummingVisitor)tmv).sum;
            }
        }
        return new SummingVisitor().getSum(map);
    }

    static class BytesPerEntryReport {
        PrintStream printStream;
        long freeMemory = Runtime.getRuntime().freeMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long startMemory = totalMemory - freeMemory;

        BytesPerEntryReport() {
            try {
                // true means append mode
                printStream = new PrintStream(new FileOutputStream("bytesPerEntry.txt", true));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        void print() {
            long freeMemory = Runtime.getRuntime().freeMemory();
            long totalMemory = Runtime.getRuntime().totalMemory();
            long endMemory = totalMemory - freeMemory;
            float bytesPerEntry = (float)(endMemory - startMemory) / mapSize;
            System.out.println("bytesPerEntry=" + bytesPerEntry);
            printStream.println("\"" + new Date() + "\",\"" + mapClassName + "\"," + mapSize + "," + bytesPerEntry);
            printStream.flush();
        }
    }
}

/**
 * A minimal parallel summer example. This works only with VisitableMaps such as
 * AirConcurrentMap, and does not fall back to regular streams if necessary in
 * getSum() as shown elsewhere.
 * 
 * Use: long result = new MinimalThreadedSummingVisitor().getSum(map);
 */
class ParrallelSummer extends ThreadedMapVisitor<Object, Long> {
    long sum = 0;

    long getSum(VisitableMap<Object, Long> map) {
        map.visit(this);
        return sum;
    }

    // Implement MapVisitor
    @Override
    public void visit(Object k, Long v) {
        sum += v.longValue();
    }

    // Implement ThreadedMapVisitor For parallelism
    @Override
    public ParrallelSummer split() {
        return new ParrallelSummer();
    }

    // Implement ThreadedMapVisitor For parallelism
    @Override
    public void merge(ThreadedMapVisitor tmv) {
        sum += ((ParrallelSummer)tmv).sum;
    }
}
