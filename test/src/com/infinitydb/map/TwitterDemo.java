// Copyright (C) 1997-2017 Roger L. Deran.
//
//    This file is part of AirConcurrentMap. AirConcurrentMap 
//    is proprietary software.
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
//    See <http://www.gnu.org/licenses/>.
//
//    For dual licensing, see boilerbay.com.
//    The author email is rlderan2 there.

package com.infinitydb.map;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import com.infinitydb.map.air.AirConcurrentMap;
import com.infinitydb.map.lambda.CollectingThreadedMapVisitor;
import com.infinitydb.map.lambda.MapVisitorBiConsumerWrapper;
import com.infinitydb.map.lambda.ValueCollectingThreadedMapVisitor;
import com.infinitydb.map.visitor.MapVisitor;
import com.infinitydb.map.visitor.ThreadedMapVisitor;
import com.infinitydb.map.visitor.VisitableMap;

/**
 * @author Roger
 *
 */

public class TwitterDemo {
    public static void main(String... args) {
        try {
            // args = new String[] { "100", "100" };
            TwitterDemoForMapPut.main(args);
            TwitterDemoForMapMemorySize.main(args);
            TwitterDemoForMapIterator.main(args);
            TwitterDemoForNavigableMapGet.main(args);
            TwitterDemoForThreadedGet.main(args);
            TwitterDemoForThreadedLambda.main(args);
            TwitterDemoForBiConsumerWithLambdaInteger.main(args);
            TwitterDemoForThreadedSummerPerformance.main(args);
            TwitterDemoForStreamsCollectorsVsAirConcurrentMap.main(args);
            TwitterDemoForStreamsCollectorsVsAirConcurrentMapArrayList.main(args);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}

/**
 * How to determine a Map's put speed simply. Both are ConcurrentNavigableMaps,
 * which are ordered, for fairness. ACM reaches 600K/s and CSLM reaches 351K/s
 */
class TwitterDemoForMapPut {
    public static void main(String... args) {
        System.out.println("TwitterDemoForMapPut");
        final int MAX_SIZE = args.length >= 1 ? Integer.valueOf(args[0]) : 20_000_000;
        final int STEP_SIZE = args.length >= 2 ? Integer.valueOf(args[1]) : 1000_000;
        Random random = new Random();
        Map<Long, Long> map = new AirConcurrentMap<Long, Long>();
        // Map<Long, Long> map = new ConcurrentSkipListMap<Long, Long>();
        // Map<Long, Long> map = new ConcurrentHashMap<Long, Long>();
        // Map<Long, Long> map = new HashMap<Long, Long>();
        // Map<Long, Long> map = new TreeMap<Long, Long>();
        long t0 = System.nanoTime();
        for (int i = 0; i < MAX_SIZE;) {
            for (int j = 0; j < STEP_SIZE; j++) {
                Long n = random.nextLong();
                map.put(n, n);
                i++;
            }
            long t1 = System.nanoTime();
            double entriesPerSecond = i * 1e9 / (t1 - t0);
            System.out.println("Entries=" + i + " Entries/s=" +
                    entriesPerSecond / 1e6 + "M");
        }
    }
}

/**
 * Simple code to show the performance of get() and higher() of
 * ConcurrentNavigableMaps over various sizes.
 * 
 * <pre>
 * AirConcurrentMap:      Entries=10000000 gets/s=817.472K
 * ConcurrentSkipListMap: Entries=10000000 gets/s=390.339K
 * TreeMap:               Entries=10000000 gets/s=919.522K
 * </pre>
 */
class TwitterDemoForNavigableMapGet {
    static final int ITERATIONS = 100_000;

    public static void main(String... args) {
        System.out.println("TwitterDemoForNavigableMapGet");
        final int MAX_SIZE = args.length >= 1 ? Integer.valueOf(args[0]) : 10_000_000;
        final int STEP_SIZE = args.length >= 2 ? Integer.valueOf(args[1]) : 1000_000;
        Random random = new Random();
        ConcurrentNavigableMap<Integer, Integer> map = new
                AirConcurrentMap<Integer, Integer>();
        // ConcrentNavigableMap<Integer, Integer> map = new
        // ConcurrentSkipListMap<Integer, Integer>();
        // NavigableMap<Integer, Integer> map = new
        // TreeMap<Integer, Integer>();
        // Without a tangible result, the loop is optimized out!
        long total = 0;
        for (int i = 0; i < MAX_SIZE;) {
            for (int j = 0; j < STEP_SIZE; j++) {
                Integer n = new Integer(random.nextInt());
                map.put(n, n);
                i++;
            }
            long t0 = System.nanoTime();
            for (int k = 0; k < ITERATIONS; k++) {
                Integer n = new Integer(random.nextInt());
                // map.get(n);
                // higherKey et al are only in NavigableMaps, which are ordered
                map.higherKey(n);
            }
            long t1 = System.nanoTime();
            double seconds = (t1 - t0) / 1e9;
            System.out.printf("Entries=%d gets/s=%6.3fK\n", i,
                    ITERATIONS / seconds / 1e3);
        }
        System.out.println("total=" + total);
    }
}

/**
 * A simple threading test for get() for ConcurrentNavigableMaps.
 * AirConcurrentMap wins.
 * 
 * <pre>
 * AirConcurrentMap:      Entries=10000000 gets/s=549.281K. 
 * ConcurrentSkipListMap: Entries=10000000 gets/s=274.786K.
 * </pre>
 */
class TwitterDemoForThreadedGet {
    static final int THREADS = 8;
    static final int ITERATIONS = 3_000_000;

    static final NavigableMap<Integer, Integer> map = new
            AirConcurrentMap<Integer, Integer>();
    // static final NavigableMap<Integer, Integer> map =
    // new ConcurrentSkipListMap<Integer, Integer>();

    static int entryCount = 0;
    // increment this if the get() is commented out to avoid the loop being
    // optimized away.
    static volatile int doNothing;

    public static void main(String... args) {
        System.out.println("TwitterDemoForThreadedGet");
        final int MAX_SIZE = args.length >= 1 ? Integer.valueOf(args[0]) : 10_000_000;
        final int STEP_SIZE = args.length >= 2 ? Integer.valueOf(args[1]) : 1000_000;
        Random random = new Random(new SecureRandom().nextInt());
        while (entryCount < MAX_SIZE) {
            for (int i = 0; i < STEP_SIZE; i++) {
                Integer n = new Integer(random.nextInt());
                map.put(n, n);
                entryCount++;
            }
            Thread[] threads = new Thread[THREADS];
            for (int j = 0; j < THREADS; j++)
                threads[j] = new Thread() {
                    public void run() {
                        Random random = new Random(new SecureRandom().nextInt());
                        long t0 = System.nanoTime();
                        for (int k = 0; k < ITERATIONS; k++) {
                            Integer n = new Integer(random.nextInt());
                            // map.get(n);
                            map.higherKey(n);
                            // doNothing++; Watch for being optimized out
                            // though!
                        }
                        long t1 = System.nanoTime();
                        double seconds = (t1 - t0) / 1e9;
                        System.out.printf("Entries=%d gets/s=%6.3fK\n",
                                entryCount, ITERATIONS / seconds / 1e3);
                    }
                };
            for (Thread thread : threads)
                thread.start();
            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                }
            }
        }
    }

}

/**
 * How to determine a Map's memory efficiency. AirConcurrentMap: reaches 39
 * bytes/Entry above about 1K Entries, CSLM 59 bytes/entry, CHashMap 65
 * bytes/Entry.
 */
class TwitterDemoForMapMemorySize {
    public static void main(String... args) {
        System.out.println("TwitterDemoForMapMemorySize");
        final int MAX_SIZE = 32768;
        Random random = new Random(1);
        // We must hold on to all the maps or they will get optimized away.
        ArrayList<Map<Long, Long>> maps = new ArrayList<Map<Long, Long>>(MAX_SIZE);
        for (long mapSize = 1, nMaps = MAX_SIZE; mapSize <= MAX_SIZE; mapSize <<= 1, nMaps >>= 1) {
            // clear sets all elements null but does not shorten internal array.
            maps.clear();
            long size0 = memorySize();
            for (int k = 0; k < nMaps; k++) {
                Map<Long, Long> map = new AirConcurrentMap<>();
                // Map<Long, Long> map = new ConcurrentHashMap<Long, Long>();
                // Map<Long, Long> map = new ConcurrentSkipListMap<Long,
                // Long>();
                maps.add(map);
                for (int m = 0; m < mapSize; m++) {
                    Long n = random.nextLong();
                    map.put(n, n);
                }
            }
            long size1 = memorySize();
            // outputting something dependent on maps.size() prevents optimizing
            // out and gc.
            long bytesPerMap = (size1 - size0) / maps.size();
            System.out.println("Entries=" + mapSize +
                    " bytes/Entry=" + bytesPerMap / mapSize + " memory=" + (size1 - size0));
        }
    }

    static long memorySize() {
        Runtime.getRuntime().gc();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        return totalMemory - freeMemory;
    }
}

/**
 * Test Iterator performance with various Maps. AirConcurrentMap wins by a
 * factor of 2 to 3.
 * 
 * <pre>
 * AirConcurrentMap:    Entries=11000000 gets/s=52172.725K
 * HashMap:             Entries=11000000 gets/s=26719.118K
 * CSLM:                Entries=11000000 gets/s=24056.478K
 * CHM:                 Entries=11000000 gets/s=22709.103K
 * TreeMap:             Entries=11000000 gets/s=16525.371K
 * </pre>
 */
class TwitterDemoForMapIterator {
    public static void main(String... args) {
        System.out.println("TwitterDemoForMapIterator");
        final int MAX_SIZE = args.length >= 1 ? Integer.valueOf(args[0]) : 10_000_000;
        final int STEP_SIZE = args.length >= 2 ? Integer.valueOf(args[1]) : 1000_000;
        Random random = new Random();
        Map<Integer, Integer> map = new AirConcurrentMap<Integer, Integer>();
        // Map<Integer, Integer> map = new HashMap<Integer, Integer>();
        // Map<Integer, Integer> map = new TreeMap<Integer, Integer>();
        // Map<Integer, Integer> map = new ConcurrentSkipListMap<Integer,
        // Integer>();
        // Map<Integer, Integer> map = new ConcurrentHashMap<Integer,
        // Integer>();
        // Without a tangible result, the loop can be optimized out!
        for (int i = 0; i < MAX_SIZE;) {
            for (int j = 0; j < STEP_SIZE; j++) {
                Integer n = new Integer(random.nextInt());
                map.put(n, n);
                i++;
            }
            long t0 = System.nanoTime();
            long total = 0;
            for (Integer k : map.keySet()) {
                total += k.intValue();
            }
            long t1 = System.nanoTime();
            double seconds = (t1 - t0) / 1e9;
            System.out.printf("Entries=%d gets/s=%6.3fK total=%d\n",
                    i, MAX_SIZE / seconds / 1e3, total);
        }
    }
}

/**
 * Test forEach() with lambdas with various Maps. AirConcurrenMap wins by a
 * multiple of 3 to 6 times.
 * 
 * The AirConcurrentMap Threaded visiting technique using a ThreadedMapVisitor
 * is not used here, but is shown in other tests to be 10 to 20 times faster.
 * 
 * <pre>
 * AirConcurrentMap w/BiConsumer Entries=10000000 ops/s=115833.202K map.size()=9988406
 * 
 * AirConcurrentMap:    Entries=10000000 ops/s=40651.439K map.size()=9988406
 * HashMap:             Entries=10000000 ops/s=37160.047K map.size()=9988406
 * CSLM:                Entries=10000000 ops/s=28687.409K map.size()=9988406
 * CHM:                 Entries=10000000 ops/s=25548.065K map.size()=9988406
 * TreeMap:             Entries=10000000 ops/s=20235.311K map.size()=9988406
 * </pre>
 */
class TwitterDemoForBiConsumerWithLambdaInteger {
    static final int STEP_SIZE = 1000_000;
    // Without a tangible result, the loop can be optimized out!
    static int sum = 0;

    public static void main(String... args) {
        System.out.println("TwitterDemoForBiConsumerWithLambdaInteger");
        final int MAX_SIZE = args.length >= 1 ? Integer.valueOf(args[0]) : 10_000_000;
        Random random = new Random(315664);
        AirConcurrentMap<Integer, Integer> map = new AirConcurrentMap<Integer, Integer>();
        // Map<Integer, Integer> map = new HashMap<Integer, Integer>();
        // Map<Integer, Integer> map = new TreeMap<Integer, Integer>();
        // Map<Integer, Integer> map = new ConcurrentSkipListMap<Integer,
        // Integer>();
        // Map<Integer, Integer> map = new ConcurrentHashMap<Integer,
        // Integer>();
        for (int i = 0; i < MAX_SIZE;) {
            for (int j = 0; j < STEP_SIZE; j++) {
                Integer n = new Integer(random.nextInt());
                map.put(n, n);
                i++;
            }
            sum = 0;
            long t0 = System.nanoTime();
            if (false)
                map.forEach((k, v) -> sum += v.intValue());
            else if (false) {
                // Fast, for AirConcurrentMap.
                // It becomes Java-8 dependent by using a BiConsumer and lambda
                map.visit(new MapVisitorBiConsumerWrapper<Integer, Integer>(
                        (k, v) -> sum += v.intValue()));
            } else if (true) {
                sum += new SummerMapVisitor<Integer>().getSum(map);
            }
            long t1 = System.nanoTime();
            double seconds = (t1 - t0) / 1e9;
            System.out.printf("Entries=%d ops/s=%6.3fK map.size()=%d\n", i, i / seconds / 1e3, map.size());
        }
        System.out.println("sum=" + sum);
    }

    // This shows how to avoid BiConsumer and lambdas. Re-usable.
    static class SummerMapVisitor<K> extends MapVisitor<K, Integer> {
        int sum;

        int getSum(VisitableMap<K, Integer> map) {
            sum = 0;
            map.visit(this);
            return sum;
        }

        @Override
        public void visit(K k, Integer v) {
            sum += v.intValue();
        }
    }
}

/**
 * Test forEach() with lambdas with various Maps. AirConcurrenMap wins by a
 * multiple of 4 to 7 times.
 * 
 * The separate scans are all done in parallel to get all cores running,
 * simulating heavy overall load. The AirConcurrentMap Threaded visiting
 * technique using a ThreadedVisitor BiConsumer is not used here, but is shown
 * in other tests to be 10 to 20 times faster.
 * 
 * <pre>
 * AirConcurrentMap:    Entries=10000000 ops/s=682427.850K sum=530270295304560
 * CSLM:                Entries=10000000 ops/s=176247.562K sum=530270295304560
 * HashMap:             Entries=10000000 ops/s=115310.715K sum=530270295304560
 * TreeMap:             Entries=10000000 ops/s=117812.730K sum=530270295304560
 * CHM:                 Entries=10000000 ops/s=102514.533K sum=530270295304560
 * </pre>
 */
class TwitterDemoForThreadedLambda {
    static final int THREADS = 8;
    static final int REPEATS = 30;

    static final AirConcurrentMap<Integer, Integer> map = new AirConcurrentMap<Integer,
            Integer>();
    // static final Map<Integer, Integer> map = new
    // ConcurrentSkipListMap<Integer, Integer>();
    // static final Map<Integer, Integer> map = new HashMap<Integer, Integer>();
    // static final Map<Integer, Integer> map = new ConcurrentHashMap<Integer,
    // Integer>();
    // static final Map<Integer, Integer> map = new TreeMap<Integer, Integer>();

    static int entryCount = 0;

    public static void main(String... args) {
        System.out.println("TwitterDemoForThreadedLambda");
        final int MAX_SIZE = args.length >= 1 ? Integer.valueOf(args[0]) : 10_000_000;
        final int STEP_SIZE = args.length >= 2 ? Integer.valueOf(args[1]) : 1000_000;
        final Random random = new Random(1);
        final AtomicLong sum = new AtomicLong();
        while (entryCount <= MAX_SIZE) {
            for (int i = 0; i < STEP_SIZE; i++) {
                Integer n = new Integer(random.nextInt());
                map.put(n, n);
                entryCount++;
            }
            Thread[] threads = new Thread[THREADS];
            for (int j = 0; j < THREADS; j++)
                threads[j] = new Thread() {
                    long localSum = 0;

                    public void run() {
                        for (int r = 0; r < REPEATS; r++) {
                            if (false) {
                                map.forEach((k, v) -> localSum += v.longValue());
                            } else if (false) {
                                // Not faster for AirConcurrentmap
                                map.forEach(new BiConsumer<Integer, Integer>() {
                                    public final void accept(Integer k, Integer v) {
                                        localSum += v.intValue();
                                    }
                                });
                            } else if (true) {
                                map.visit(
                                        new MapVisitorBiConsumerWrapper<Integer, Integer>(
                                                (k, v) -> localSum += v.longValue()));
                            }
                        }
                        sum.addAndGet(localSum);
                    }
                };
            sum.set(0);
            for (Thread thread : threads)
                thread.start();
            long t0 = System.nanoTime();
            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                }
            }
            long t1 = System.nanoTime();
            double seconds = (t1 - t0) / 1e9;
            System.out.printf("Entries=%d ops/s=%6.3fK sum=%d\n",
                    entryCount,
                    entryCount / seconds / 1e3 * THREADS * REPEATS,
                    sum.get());
        }
    }
}

/**
 * Test forEach() with lambdas with various Maps. AirConcurrenMap uses a
 * threaded technique and it wins by a multiple of 10 to 20 times.
 * 
 * <pre>
 * AirConcurrentMap:    Entries=10000000 ops/s=358614.367K
 * HashMap:             Entries=10000000 ops/s=37201.442K
 * CSLM:                Entries=10000000 ops/s=25353.822K
 * CHM:                 Entries=10000000 ops/s=22421.307K
 * TreeMap:             Entries=10000000 ops/s=17833.000K
 * </pre>
 */
class TwitterDemoForThreadedSummerPerformance {
    static long sum = 0;

    public static void main(String... args) {
        System.out.println("TwitterDemoForThreadedSummerPerformance");
        final int MAX_SIZE = args.length >= 1 ? Integer.valueOf(args[0]) : 10_000_000;
        final int STEP_SIZE = args.length >= 2 ? Integer.valueOf(args[1]) : 1000_000;
        Random random = new Random(315664);
        Map<Long, Long> map = new AirConcurrentMap<Long,
                Long>();
        // Map<Long, Long> map = new HashMap<Long, Long>();
        // Map<Long, Long> map = new TreeMap<Long, Long>();
        // Map<Long, Long> map = new ConcurrentSkipListMap<Long, Long>();
        // Map<Long, Long> map = new ConcurrentHashMap<Long, Long>();
        for (long i = 0; i < MAX_SIZE;) {
            for (int j = 0; j < STEP_SIZE; j++) {
                Long n = new Long(random.nextLong());
                map.put(n, n);
                i++;
            }
            long t0 = System.nanoTime();
            // A special Way to parallelize AirConcurrentMap compatible with all
            // Java versions
            sum += new ThreadedSummer<Long>().getSum(map);
            // map.forEach((k, v) -> sum += v.longValue());
            long t1 = System.nanoTime();
            double seconds = (t1 - t0) / 1e9;
            System.out.printf("Entries=%d ops/s=%6.3fK\n", i, i / seconds / 1e3);
        }
        System.out.println("sum=" + sum);
    }

    /**
     * This is a re-usable general-purpose very fast map summer for
     * AirConcurrentMap
     * 
     * @param <K>
     */
    static class ThreadedSummer<K> extends ThreadedMapVisitor<K, Long> {
        long sum;

        long getSum(Map<K, Long> map) {
            sum = 0;
            if (map instanceof VisitableMap) {
                ((VisitableMap)map).visit(this);
            } else {
                // Make this handle non-Visitable Maps as well for convenience.
                // You can use lambdas here
                for (Long v : map.values()) {
                    sum += v.longValue();
                }
                // This can be faster than iterator for some standard Maps, but
                // not CSLM
                // map.forEach((k, v) -> sum += v.longValue());
            }
            return sum;
        }

        @Override
        public void visit(K key, Long value) {
            sum += value.longValue();
        }

        @Override
        public ThreadedSummer<K> split() {
            return new ThreadedSummer<K>();
        }

        @Override
        public void merge(ThreadedMapVisitor<K, Long> otherSummer) {
            sum += ((ThreadedSummer<K>)otherSummer).sum;
        }
    }
}

/**
 * This simple code performance tests Java 8 Stream reducers, collectors and
 * forEach(). AirConcurrentMap wins by 4.2 to 7.4 times.
 * 
 * We compare with AirConcurrentMap visit(), which uses internal threading.
 * (Intel i7 3GHz quad-core hyperthreaded)
 * 
 * <pre>
 * AirConcurrentMap forEach:    Entries=10000000 ops/s=341933.286K
 * 
 * Using stream reducers:
 * CSLM:                Entries=10000000 ops/s=81102.013K
 * AirConcurrentMap:    Entries=10000000 ops/s=77899.483K
 * TreeMap:             Entries=10000000 ops/s=58306.705K
 * HashMap:             Entries=10000000 ops/s=53881.339K
 * CHM:                 Entries=10000000 ops/s=52893.072K
 * 
 * Using the parallel stream forEach():
 * HashMap:             Entries=10000000 ops/s=50186.038K
 * CSLM:                Entries=10000000 ops/s=49186.583K
 * TreeMap:             Entries=10000000 ops/s=47517.139K
 * CHM:                 Entries=10000000 ops/s=47213.580K
 * AirConcurrentMap:    Entries=10000000 ops/s=46708.239K
 * </pre>
 * 
 * For logarithmic HashMap
 * 
 * <pre>
 * TwitterDemoStreamsVsAirConcurrentMapVisit
 * Entries=1 ops/s= 1.950K
 * Entries=2 ops/s=199.687K
 * Entries=4 ops/s=126.825K
 * Entries=8 ops/s=230.151K
 * Entries=16 ops/s=491.699K
 * Entries=32 ops/s=788.148K
 * Entries=64 ops/s=1693.489K
 * Entries=128 ops/s=5044.784K
 * Entries=256 ops/s=9942.107K
 * Entries=512 ops/s=17459.946K
 * Entries=1024 ops/s=31468.749K
 * Entries=2048 ops/s=55472.191K
 * Entries=4096 ops/s=84008.657K
 * Entries=8192 ops/s=127508.852K
 * Entries=16384 ops/s=139030.754K
 * Entries=32768 ops/s=129456.354K
 * Entries=65536 ops/s=137753.496K
 * Entries=131072 ops/s=73409.318K
 * Entries=262144 ops/s=86452.857K
 * Entries=524288 ops/s=56080.844K
 * Entries=1048576 ops/s=62596.781K
 * Entries=2097152 ops/s=61629.909K
 * Entries=4194304 ops/s=59846.422K
 * Entries=8388608 ops/s=63410.829K
 * sum=4532819305485309948
 * </pre>
 * 
 * For logarithmic ACM
 * 
 * <pre>
 * Entries=1 ops/s=34.828K
 * Entries=2 ops/s=1313.672K
 * Entries=4 ops/s=2941.306K
 * Entries=8 ops/s=4735.886K
 * Entries=16 ops/s=8133.346K
 * Entries=32 ops/s=12307.030K
 * Entries=64 ops/s=33858.314K
 * Entries=128 ops/s=50051.420K
 * Entries=256 ops/s=58515.623K
 * Entries=512 ops/s=45888.416K
 * Entries=1024 ops/s=56713.801K
 * Entries=2048 ops/s=106562.380K
 * Entries=4096 ops/s=114966.112K
 * Entries=8192 ops/s=380374.496K
 * Entries=16384 ops/s=466983.385K
 * Entries=32768 ops/s=438670.767K
 * Entries=65536 ops/s=220747.697K
 * Entries=131072 ops/s=500883.325K
 * Entries=262144 ops/s=222404.001K
 * Entries=524288 ops/s=189674.639K
 * Entries=1048576 ops/s=286982.513K
 * Entries=2097152 ops/s=319906.799K
 * Entries=4194304 ops/s=331126.640K
 * Entries=8388608 ops/s=325753.752K
 * sum=4532819305485309948
 * </pre>
 *
 * For Logarithmic CSLM
 * 
 * <pre>
 * Entries=1 ops/s= 1.914K
 * Entries=2 ops/s=238.606K
 * Entries=4 ops/s=483.627K
 * Entries=8 ops/s=155.863K
 * Entries=16 ops/s=385.944K
 * Entries=32 ops/s=640.092K
 * Entries=64 ops/s=1790.115K
 * Entries=128 ops/s=3213.174K
 * Entries=256 ops/s=5229.899K
 * Entries=512 ops/s=971.942K
 * Entries=1024 ops/s=10125.410K
 * Entries=2048 ops/s=6878.351K
 * Entries=4096 ops/s=78532.531K
 * Entries=8192 ops/s=95041.705K
 * Entries=16384 ops/s=154182.690K
 * Entries=32768 ops/s=213606.043K
 * Entries=65536 ops/s=230937.116K
 * Entries=131072 ops/s=183588.811K
 * Entries=262144 ops/s=256798.679K
 * Entries=524288 ops/s=180037.276K
 * Entries=1048576 ops/s=90792.162K
 * Entries=2097152 ops/s=47158.045K
 * Entries=4194304 ops/s=70862.696K
 * Entries=8388608 ops/s=163192.459K
 * sum=4532819305485309948
 * </pre>
 */
class TwitterDemoForStreamsVsAirConcurrentMapVisit {
    static final int REPEATS = 100;

    static long sum = 0;
    static AtomicLong atomicSum = new AtomicLong();

    public static void main(String... args) {
        System.out.println("TwitterDemoStreamsVsAirConcurrentMapVisit");
        final int MAX_SIZE = args.length >= 1 ? Integer.valueOf(args[0]) : 10_000_000;
        final int STEP_SIZE = args.length >= 2 ? Integer.valueOf(args[1]) : 1000_000;
        Random random = new Random(315664);
        // Map<Long, Long> map = new AirConcurrentMap<Long, Long>();
        // Map<Long, Long> map = new HashMap<Long, Long>();
        // Map<Long, Long> map = new TreeMap<Long, Long>();
        Map<Long, Long> map = new ConcurrentSkipListMap<Long, Long>();
        // Map<Long, Long> map = new ConcurrentHashMap<Long, Long>();
        // UNCOMMENT FOR FIXED STEPS
        for (long i = 0; i < MAX_SIZE;) {
            for (int j = 0; j < STEP_SIZE; j++, i++) {
                Long n = new Long(random.nextLong());
                map.put(n, n);
            }
            // UNCOMMENT for logarithmic steps
            // long currentSize = 1;
            // for (long i = 1; i < MAX_SIZE; i <<= 1) {
            // for (; currentSize < i; currentSize++) {
            // Long n = new Long(random.nextLong());
            // map.put(n, n);
            // }
            long t0 = System.nanoTime();
            for (int repeats = 0; repeats < REPEATS; repeats++) {
                // A special reusable means to parallelize AirConcurrentMap.
                if (true)
                    sum += new ThreadedSummer<Long>().getSum(map);
                else if (false)
                    map.values().stream().parallel().forEach(x -> {
                        atomicSum.set(atomicSum.get() + x.longValue());
                    });
                else if (false)
                    map.values().stream().mapToLong(x -> x.longValue()).parallel().forEach(x -> {
                        atomicSum.set(atomicSum.get() + x);
                    });
                else if (false)
                    sum += map.values().stream().parallel().mapToLong(x -> x.longValue())
                            .reduce(0L, (x, y) -> x + y);
                else if (false)
                    sum += map.values().stream().parallel()
                            .reduce(0L, (x, y) -> x + y);
                else if (false)
                    sum += map.values().stream().parallel()
                            .collect(Collectors.summingLong(
                                    x -> ((Long)x).longValue()));
                else if (false)
                    // Slowest
                    map.forEach((k, v) -> sum += v.longValue());
            }
            long t1 = System.nanoTime();
            double seconds = (t1 - t0) / 1e9;
            System.out.printf("Entries=%d ops/s=%6.3fK\n",
                    i, i * REPEATS / seconds / 1e3);
        }
        System.out.println("sum=" + sum);
    }

    // This implementation allows AirConcurrentMap to parallelize
    static final class ThreadedSummer<K> extends ThreadedMapVisitor<K, Long> {
        // Does not need to be atomic - not shared.
        long sum;

        final long getSum(Map<K, Long> map) {
            // initalize here so a single ThreadedSummer instance can be reused.
            sum = 0;
            if (map instanceof VisitableMap) {
                ((VisitableMap)map).visit(this);
            } else {
                // Optionally make this run on non-Visitable Maps
                sum = map.values().stream().parallel().mapToLong(x -> x.longValue())
                        .reduce(0L, (x, y) -> x + y);
            }
            return sum;
        }

        @Override
        public final void visit(K key, Long value) {
            sum += value.longValue();
        }

        // For parallelism
        @Override
        public final ThreadedSummer<K> split() {
            return new ThreadedSummer<K>();
        }

        // For parallelism
        @Override
        public final void merge(ThreadedMapVisitor<K, Long> otherSummer) {
            sum += ((ThreadedSummer<K>)otherSummer).sum;
        }
    }
}

/**
 * This simple code shows how to use and performance test Java 8 Map Stream
 * Collectors to do summing. AirConcurrentMap wins at 3x to 4x.
 * 
 * We also test a wrapper class ValueCollectingThreadedMapVisitor for
 * AirConcurrentMap that uses the internal thread pool which is about 3 times to
 * 4 times faster. We use a 'collection' that is just a 'LongHolder'. (Intel i7
 * 3GHz quad-core hyperthreaded)
 * 
 * <pre>
 * AirConcurrentMap collect:    Entries=10000000 ops/s=316164.749K
 * 
 * Using regular parallel collectors:
 * 
 * CSLM:                Entries=9000000 ops/s=100714.430K
 * TreeMap:             Entries=10000000 ops/s=88388.320K
 * AirConcurrentMap:    Entries=10000000 ops/s=83344.249K
 * HashMap:             Entries=10000000 ops/s=80473.108K
 * CHM:                 Entries=10000000 ops/s=74286.026K
 * </pre>
 * 
 * sum=-8605951044782024408
 */
class TwitterDemoForStreamsCollectorsVsAirConcurrentMap {
    static final int REPEATS = 100;

    static long sum = 0;

    public static void main(String... args) {
        System.out.println("TwitterDemoForStreamsCollectorsVsAirConcurrentMap");
        final int MAX_SIZE = args.length >= 1 ? Integer.valueOf(args[0]) : 10_000_000;
        final int STEP_SIZE = args.length >= 2 ? Integer.valueOf(args[1]) : 1000_000;
        Random random = new Random(315664);
        Map<Long, Long> map = new AirConcurrentMap<Long, Long>();
        // Map<Long, Long> map = new HashMap<Long, Long>();
        // Map<Long, Long> map = new TreeMap<Long, Long>();
        // Map<Long, Long> map = new ConcurrentSkipListMap<Long, Long>();
        // Map<Long, Long> map = new ConcurrentHashMap<Long, Long>();
        for (long i = 0; i < MAX_SIZE;) {
            for (int j = 0; j < STEP_SIZE; j++) {
                Long n = new Long(random.nextLong());
                map.put(n, n);
                i++;
            }
            long t0 = System.nanoTime();
            for (int repeats = 0; repeats < REPEATS; repeats++) {
                // @formatter:off
                if (true) {
                    // Very fast internally threaded adapter.
                    // AirConcurrentMap: 3x is typical.
                    sum += new ValueCollectingThreadedMapVisitor<Long, Long, LongHolder>( 
                        LongHolder::new, (r, v) -> { r.x += v; },
                        (r1, r2) -> { r1.x += r2.x; }).getResult(map).x;
                }
                if (false) {
                    // This way adds a 'mapper' lambda as the first parameter.
                    sum += new CollectingThreadedMapVisitor<Long, Long, LongHolder, Long>(
                        (k, v) -> v,
                        LongHolder::new,
                        (r, v) -> { r.x += v; }, 
                        (r1, r2) -> { r1.x += r2.x; }).getResult(map).x;
                }
                if (false) {
                    // standard technique
                    sum += map.values().stream().parallel().collect(
                        LongHolder::new, (r, v) -> { r.x += v; }, 
                        (r1, r2) -> { r1.x += r2.x; }).x;
                }
                // @formatter:on
                // The standard library has a specialized summer:
                if (false)
                    sum += map.values().stream().parallel().collect(
                            Collectors.summingLong(x -> ((Long)x).longValue()));
            }
            long t1 = System.nanoTime();
            double seconds = (t1 - t0) / 1e9;
            System.out.printf("Entries=%d ops/s=%6.3fK\n",
                    i, i * REPEATS / seconds / 1e3);
        }
        System.out.println("sum=" + sum);
    }

    // The 'container' we work with for dealing with longs, such as for summing.
    // This is similar to AtomicInteger, which could be used instead (maybe
    // slower).
    static class LongHolder {
        public long x;
    }
}

/**
 * This simple code shows how to use and test Java 8 Map Stream Collectors with
 * ArrayLists. AirConcurrenMap wins with its internal threads.
 * 
 * There is a collector class that uses AirConcurrentMap's internal thread pool
 * for extra speed. (Intel i7 3GHz quad-core hyperthreaded)
 * 
 * <pre>
 * AirConcurrentMap collectParallel(): Entries=10000000 ops/s=68928.258K size=10000000   
 * 
 * Using regular non-native parallel collectors (ACM in serial mode)
 * 
 * TreeMap:             Entries=10000000 ops/s=43153.720K size=10000000
 * HashMap:             Entries=10000000 ops/s=42291.941K size=10000000
 * CHM:                 Entries=10000000 ops/s=41806.186K size=10000000
 * AirConcurrentMap:    Entries=10000000 ops/s=39429.624K size=10000000
 * CSLM:                Entries=10000000 ops/s=31816.849K size=10000000
 * </pre>
 * 
 * sum=-5416378075002339540 (Intel i7 3GHz quad-core hyperthreaded)
 */
class TwitterDemoForStreamsCollectorsVsAirConcurrentMapArrayList {
    // static final int REPEATS = 300;
    static final int REPEATS = 30;

    public static void main(String... args) {
        System.out.println("TwitterDemoForStreamsCollectorsVsAirConcurrentMapArrayList");
        final int MAX_SIZE = args.length >= 1 ? Integer.valueOf(args[0]) : 10_000_000;
        final int STEP_SIZE = args.length >= 2 ? Integer.valueOf(args[1]) : 1000_000;
        Random random = new Random(315664);
        // Uncomment a Map to test.
        Map<Long, Long> map = new AirConcurrentMap<Long, Long>();
        // Map<Long, Long> map = new HashMap<Long, Long>();
        // Map<Long, Long> map = new TreeMap<Long, Long>();
        // Map<Long, Long> map = new ConcurrentSkipListMap<Long, Long>();
        // Map<Long, Long> map = new ConcurrentHashMap<Long, Long>();
        for (long i = 0; i < MAX_SIZE;) {
            for (int j = 0; j < STEP_SIZE; j++) {
                Long n = new Long(random.nextLong());
                map.put(n, n);
                i++;
            }
            long t0 = System.nanoTime();
            ArrayList<Long> result = null;
            for (int k = 0; k < REPEATS; k++) {
                if (false) {
                    // Very fast internally threaded adapter.
                    // AirConcurrentMap: 3x is typical.
                    result = new ValueCollectingThreadedMapVisitor<Long, Long, ArrayList>(
                            ArrayList::new, ArrayList::add, ArrayList::addAll).getResult(map);
                } else if (true) {
                    result = map.values().stream().collect(
                            ArrayList::new, ArrayList::add, ArrayList::addAll);
                } else if (false) {
                    // AirConcurrentMap has a bug here - it is slow and runs
                    // out of memory in parallel mode, but is still fastest in
                    // serial mode, even compared with parallel for the others.
                    result = map.values().stream().parallel().collect(
                            ArrayList::new, ArrayList::add, ArrayList::addAll);
                }
            }
            long t1 = System.nanoTime();
            double seconds = (t1 - t0) / 1e9;
            System.out.printf("Entries=%d ops/s=%6.3fK size=%d\n",
                    i, i * REPEATS / seconds / 1e3, result.size());
        }
    }

}
