// Copyright (C) 2014-2017 Roger L. Deran. All Rights Reserved.
//
//  May 5, 2017        Roger L. Deran
//
// THIS SOFTWARE CONTAINS CONFIDENTIAL INFORMATION AND TRADE SECRETS
// OF Roger L Deran.  USE, DISCLOSURE, OR REPRODUCTION IS PROHIBITED
// WITHOUT THE PRIOR EXPRESS WRITTEN PERMISSION OF Roger L Deran.
//
// Roger L Deran. MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT
// THE SUITABILITY OF THE SOFTWARE, EITHER EXPRESS OR IMPLIED,
// INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, OR
// NON-INFRINGEMENT. Roger L Deran. SHALL NOT BE LIABLE FOR
// ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING,
// MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.

package com.infinitydb;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Test the load on a multi-threaded system like a server presented by various
 * operations on various Maps using the Java Microbenchmarking Harness at
 * {@link http://openjdk.java.net/projects/code-tools/jmh/}.
 *  Change the @Threads(n) or use -t n in the java command. Try the
 *  number of CPU's or a large number, simulating a Web server.
 * 
 * HashMap and TreeMap use Collections.synchronizedMap(map). They apparently
 * deadlock with large Maps and 100 threads. These Maps must still use synchronized
 * (map) {...} in order to use iteration, forEach, or streams. Thus large Maps
 * block for a long time, unlike ConcurrentMaps.
 * 
 * @author Roger Deran
 *
 */
@Fork(1)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
// @OutputTimeUnit(TimeUnit.SECONDS)
@Threads(8)
@State(Scope.Benchmark)
public class ServerLoadJMHMapTest {

    @Param({ "0", "1", "10", "100", "1000", "10000", "100000", "1000000", "10000000" })
    static long mapSize;
    
    @Param({
            "com.infinitydb.map.air.AirConcurrentMap",
            // "java.util.HashMap",
            // "java.util.TreeMap",
            "java.util.concurrent.ConcurrentHashMap",
            "java.util.concurrent.ConcurrentSkipListMap"
    })
    static String mapClassName;
    static Map<Long, Long> map;
    
    // This will be optimized out by the JIT
    static boolean isCollectionsSynchronizedMap;
    // For testing the results of the scans
    static long maxForReference = 0;

    @Setup(Level.Trial)
    public static void setup() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Class<Map<Long, Long>> mapClass =
                (Class<Map<Long, Long>>)Class.forName(mapClassName);
        map = mapClass.newInstance();
        if (!(map instanceof ConcurrentMap)) {
            isCollectionsSynchronizedMap = true;
            if (!(map instanceof NavigableMap))
                map = Collections.synchronizedMap(map);
            else
                map = Collections.synchronizedNavigableMap((NavigableMap)map);
        }
        // Random random = new Random(System.nanoTime());
        Random random = new Random(1);
        // Load up the Map
        maxForReference = Long.MIN_VALUE;
        for (long i = 0; i < mapSize; i++) {
            long o = random.nextLong();
            map.put(o, o);
            maxForReference = maxForReference > o ? maxForReference : o; 
        }
    }

    @Benchmark
    public static long testServerLoadGet() {
        long k = ThreadLocalRandom.current().nextLong();
        Long v = map.get(k);
        return v == null ? Integer.MIN_VALUE : v;
    }

    /*
     * We have to do both put() and remove() so the map doesn't change size and
     * so the remove() actually has an effect because the removed key actually
     * exists.
     */
    @Benchmark
    public static long testServerLoadPutRemove() {
        long k = ThreadLocalRandom.current().nextLong();;
        map.put(k, k);
        long kReturned = map.remove(k);
        // this is probably one instruction. 
        // It takes about 10% from CHM for < 1K Entries.
        if (kReturned != k)
            throw new RuntimeException("remove(k) != put(k) k=" + k 
                    + " kReturned=" + kReturned);
        return kReturned;
    }

    @Benchmark
    public static long testServerLoadIterator() {
        long max = Long.MIN_VALUE;
        // This test is actually very fast and the JIT will remove it.
        if (isCollectionsSynchronizedMap) {
            // Must synch on the map, not the iterator or values!
            // This prevents concurrent modification
            synchronized (map) {
                for (Long v : map.values()) {
                    max = v > max ? v : max;
                }
            }
        } else {
            for (Long v : map.values()) {
                max = v > max ? v : max;
            }
        }
        if (max != maxForReference)
            throw new RuntimeException("max != maxForReference: max=" + max 
                    + " maxForReference=" + maxForReference);
        return max;
    }

    @Benchmark
    public static long testServerLoadForEach() {
        class SummingBiConsumer implements BiConsumer<Object, Long> {
            long max = Long.MIN_VALUE;

            public void accept(Object k, Long v) {
                max = v > max ? v : max;
            }
        }
        SummingBiConsumer summingBiConsumer = new SummingBiConsumer();
        if (isCollectionsSynchronizedMap) {
            // Must synch on the map, not the iterator or values!
            // This prevents concurrent modification
            synchronized (map) {
                map.forEach(summingBiConsumer);
            }
        } else {
            map.forEach(summingBiConsumer);
        }
        if (summingBiConsumer.max != maxForReference)
            throw new RuntimeException("max != maxForReference: max=" + summingBiConsumer.max 
                    + " maxForReference=" + maxForReference);
        return summingBiConsumer.max;
    }

    @Benchmark
    public static long testServerLoadSerialStream() {
        // This test is actually very fast and the JIT will remove it.
        long max = 0;
        if (isCollectionsSynchronizedMap) {
            // Must synch on the map, not the iterator or values!
            // This prevents concurrent modification
            synchronized (map) {
                max = map.values().stream()
                        .mapToLong(o -> ((Long)o).longValue())
                        .reduce(Long.MIN_VALUE, (x, y) -> x > y ? x : y);
            }
        } else {
            max = map.values().stream()
                    .mapToLong(o -> ((Long)o).longValue())
                    .reduce(Long.MIN_VALUE, (x, y) -> x > y ? x : y);
        }
        if (max != maxForReference)
            throw new RuntimeException("max != maxForReference: max=" + max 
                    + " maxForReference=" + maxForReference);
        return max;
    }

    @Benchmark
    public static long testServerLoadParallelStream() {
        // This test is actually very fast and the JIT will remove it.
        long max = 0;
        if (isCollectionsSynchronizedMap) {
            // Must synch on the map, not the iterator or set!
            // This prevents concurrent modification
            synchronized (map) {
                max = map.values().stream().parallel()
                        .mapToLong(o -> ((Long)o).longValue())
                        .reduce(Long.MIN_VALUE, (x, y) -> x > y ? x : y);
            }
        } else {
            max = map.values().stream().parallel()
                    .mapToLong(o -> ((Long)o).longValue())
                    .reduce(Long.MIN_VALUE, (x, y) -> x > y ? x : y);
        }
        if (max != maxForReference)
            throw new RuntimeException("max != maxForReference: max=" + max 
                    + " maxForReference=" + maxForReference);
        return max;
    }

}
