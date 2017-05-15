// Copyright (C) 2014-2017 Roger L. Deran. All Rights Reserved.
//
//  Apr 23, 2017        Roger L. Deran
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

import java.util.Map;
import java.util.Random;
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
import org.openjdk.jmh.annotations.Warmup;

/**
 * Test Map Iterator and forEach() speeds using the Java Microbenchmarking
 * Harness . There are more tests like this at
 * https://github.com/boilerbay/airconcurrentmap.
 * 
 * @author Roger
 */

@Fork(1)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
// @OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class SitePointJMHIteratorTest {

    @Param({
            "com.infinitydb.map.air.AirConcurrentMap",
            "java.util.HashMap",
            "java.util.TreeMap",
            "java.util.concurrent.ConcurrentSkipListMap",
            "java.util.concurrent.ConcurrentHashMap"
    })
    static String mapClassName;
    @Param({ "0", "1", "10", "100", "1000", "10000", "100000", "1000000", "10000000" })
    static long mapSize;
    static Map<Object, Long> map;

    @Setup(Level.Trial)
    static public void setup() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Class<Map<Object, Long>> mapClass =
                (Class<Map<Object, Long>>)Class.forName(mapClassName);
        map = mapClass.newInstance();
        Random random = new Random(System.nanoTime());
        System.gc();
        // Load up the Map
        for (long i = 0; i < mapSize; i++) {
            long v = random.nextLong();
            map.put(v, v);
        }
        System.gc();
    }

    @Benchmark
    public long testIteratorSitePoint() {
        long total = 0;
        for (Long i : map.values()) {
            total += i;
        }
        // return a tangible result to prevent optimizing-out
        return total;
    }

    // Much faster
    @Benchmark
    public long testForEachSitePoint() {
        Summer summer = new Summer();
        map.forEach(summer);
        return summer.sum;
    }

    static class Summer implements BiConsumer<Object, Long> {
        long sum = 0;

        @Override
        public void accept(Object k, Long v) {
            sum += v;
        }
    }

    // public static void main(String... args) throws RunnerException {
    // Options opt = new OptionsBuilder()
    // .include(SitePointJMHIteratorTest.class.getSimpleName())
    // .addProfiler("gc")
    // .addProfiler("comp")
    // .build();
    // new Runner(opt).run();
    // }

}
