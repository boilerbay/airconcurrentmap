// Copyright (C) 1997-2015 Roger L. Deran. All Rights Reserved.
//
// THIS SOFTWARE CONTAINS CONFIDENTIAL INFORMATION AND TRADE SECRETS
// OF Roger L Deran.  USE, DISCLOSURE, OR REPRODUCTION IS PROHIBITED
// WITHOUT PRIOR EXPRESS WRITTEN PERMISSION.
//
// Patent Pending.
//
// Roger L Deran. MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT
// THE SUITABILITY OF THE SOFTWARE, EITHER EXPRESS OR IMPLIED,
// INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, OR
// NON-INFRINGEMENT. Roger L Deran. SHALL NOT BE LIABLE FOR
// ANY DAMAGES SUFFERED BY USER AS A RESULT OF USING,
// MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.

package com.infinitydb.map.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.infinitydb.map.air.AirConcurrentMap;
import com.infinitydb.random.FastRandom;
import com.infinitydb.runthreaded.RunThreaded;
import com.infinitydb.runthreaded.RunThreadedGroup;

/**
 * We test speed and space for put(), get(), ceiling(), floor(), and remove() in
 * {@link com.infinitydb.map.air.AirConcurrentMap} and
 * {@link java.util.concurrent.ConcurrentSkipListMap}.
 * 
 * Change the MAP_TYPE to choose which map to test.
 * 
 * @author Roger Deran
 */
public class AirConcurrentMapPerformanceTestOfficial {

    // Set this for the test run by default (Not from a subclass).
    static final MapType MAP_TYPE = MapType.ConcurrentSkipListMap;

    // Set this for the test run. The types of keys used.
    static final KeyType KEY_TYPE = KeyType.LONG;

    // Set this for the test run. Applies to the testGet...() tests
    static final GetType GET_TYPE = GetType.GET;

    // Set this for the test run. Applies to testMixture...()
    static final RemoveType REMOVE_TYPE = RemoveType.RANDOM;

    // The types of maps testable.
    static enum MapType {
        AirConcurrentMap, ConcurrentSkipListMap
    }

    // The types of the keys to be tested.
    static enum KeyType {
        LONG, STRING
    }

    // Which of the several kinds of retrievals to use in testMixture
    static enum GetType {
        GET, LOWER, HIGHER, LOWER_AT_END, HIGHER_AT_START
    }

    // in testMixture, how to do the removes
    static enum RemoveType {
        RANDOM, START, END
    }

    static final int N_PRELOAD_THREADS = 8;
    /*
     * Less than 10 seconds or so causes GC events to become very significant.
     * For example, sometimes the JVM will increase in size 350MB or so at a
     * time, with significant periods of very low activity.
     */
    static final long SAMPLE_TIME = 100;

    /*
     * These control the termination of the tests to try to avoid actual OOME,
     * which will disturb the tests when run in sequence rather than
     * one-at-a-time. Normally these are very large.
     */
    static final double MAX_FRACTION_OF_MEMORY_TO_USE = 0.6;
    static final long MAX_OPERATIONS = 1000_000_000;
    static final long TIMEOUT = 1000_000;
    /*
     * This is a good way to prevent OutOfMemoryError, which is bad because it
     * can freeze or invalidate an entire test run.
     */
    static final long MIN_OPERATIONS_PER_SECOND = 10_000;

    static final long GET_UNIT_INCREMENT = 1000_000;
    /*
     * Keep this less than core count to prevent 'saturation' and loss of
     * responsiveness of the monitoring thread during the put steps in
     * testGet(). Otherwise, the OOME will not be prevented because we will not
     * be able to catch the eventual too-slow puts.
     */
    static final int GET_PUT_THREAD_COUNT = 1;
    // No less than 1 second, preferably 5 or more
    static final long GET_SAMPLE_TIME = 5000;
    static final long GET_ENTRIES_MAXIMUM = Long.MAX_VALUE;

    // TODO this test uses a single value - we could test with multiple.
    static final Long VALUE = new Long(42);

    /*
     * Warming can pre-allocate a set of arrays to force the JVM to get big
     * initially rather than grow as entries are put into the Map. The JVM is
     * almost immediately shrunk back down after the pre-allocation by a GC, but
     * afterwards, it grows to OOME faster, and we have a state more like a
     * long-running system. The pe-allocation must not reach OOME of course, so
     * it needs some headroom.
     * 
     * An alternative technique is to set the -Xms command-line JVM parameter
     * equal to the -Xmx parameter or the default JVM size so that the JVM
     * process' memory immediately grows to the maximum. This situation would be
     * rare in practice, since it immediately uses the worst-case amount of
     * memory, but it does serve to remove GC delays from the measurements.
     * Also, the -Xincgc incremental GC will grow the JVM almost immediately to
     * the maximum. A large fraction of the time to grow the JVM is taken up in
     * GC. The AirTree is apparently particularly easy for the standard GC
     * mechanisms to work with, because the performance is still very good. Note
     * however, that eliminating GC altogether will not produce results that are
     * representative of a normal running system, which will almost always have
     * significant GC intervals, and that the Objects put() in these tests will
     * simply move a pointer forwards in order to get new memory.
     */
    static final boolean USE_WARMING = true;
    static final int WARM_CHUNK = 1000_000;
    // Experimental minimum value to avoid OOME
    static final long WARM_HEAD_ROOM = WARM_CHUNK * 400L;

    /*
     * We freeze the test threads for a while between samples and do a gc. Using
     * this will make the total puts/total time too low, so it should be used
     * only for measuring instantaneous rates, i.e. puts/s etc. but it makes the
     * puts/s much more repeatable.
     */
    static final boolean USE_GC_WAITS = true;
    static final long GC_WAIT_TIME = 2000;
    static final long GC_LIMIT_TIME = 10_000;
    /*
     * Every COUNTER_INCREMENT loops, we increment the counters by this amount.
     * By keeping this much higher than one, we amortize the counting to reduce
     * its affect, since AtomicLong.incrementAndGet() is slow.
     */
    static final long COUNTER_INCREMENT = 100;

    /*
     * Can be overridden. Default to MAP_TYPE so we can run this class directly.
     */
    public Map<Object, Object> getMap() {
        System.out.println("map type=" + MAP_TYPE.name());
        return MAP_TYPE == MapType.AirConcurrentMap ?
                new AirConcurrentMap<Object, Object>() :
                new ConcurrentSkipListMap<Object, Object>();
    }

    // The Map under test. It can be Navigable or Concurrent or both 
    // and the appropriate tests will be run.
    public Map<Object, Object> map = getMap();
    Set<Object> keySet = map.keySet();

    // These are not a concurrency bottleneck because they increase
    // only every COUNTER_INCREMENT number of loop iterations.
    final AtomicLong entryCounter = new AtomicLong();
    final AtomicLong getCounter = new AtomicLong();
    final AtomicLong putCounter = new AtomicLong();
    final AtomicLong removeCounter = new AtomicLong();

    boolean isCheckingCorrectness;
    boolean isRunUntilOOM;

    public AirConcurrentMapPerformanceTestOfficial() {
        System.out.println("java.version=" + System.getProperty("java.version"));
        System.out.println("java.vm.version=" + System.getProperty("java.vm.version"));
        long maxMem = Runtime.getRuntime().maxMemory() / 1000 / 1000;
        System.out.println("maxMemory=" + maxMem);
        System.out.println("KeyType=" + KEY_TYPE.name());
        // System.out.println("MapType=" + MAP_TYPE.name());
        Runtime.getRuntime().gc();
    }

    void setCheckingCorrectness(boolean isCheckingCorrectness) {
        if (isCheckingCorrectness) {
            if (REMOVE_TYPE == RemoveType.END || REMOVE_TYPE == RemoveType.START)
                throw new RuntimeException("Cannot check correctness with " +
                        "removing at start or end");
        }

        this.isCheckingCorrectness = isCheckingCorrectness;
    }

    void setRunUntilOOM(boolean isRunUntilOOM) {
        this.isRunUntilOOM = isRunUntilOOM;
    }

    @Ignore
    @Test
    public void testGet1() {
        testGet(1);
    }

    // This is the physical core count on Intel i7 quad core.
    @Ignore
    @Test
    public void testGet4() {
        testGet(4);
    }

    // This is the hypertheaded core count on Intel i7 quad-core

    @Ignore
    @Test
    public void testGet8() {
        testGet(8);
    }

    @Ignore
    @Test
    public void testGet100() {
        testGet(100);
    }

    /*
     * Test get() by itself, i.e. with no other concurrent operations. This is
     * somewhat different than testMixture() because we do the puts in steps,
     * separated from the gets.
     */
    public void testGet(int threadCount) {
        try {
            System.out.println("Test=get()");
            System.out.println("ThreadCount=" + threadCount);
            System.out.printf("%9s %9s %9s %9s %9s %9s\n", "kItems", "kItems/s", "totalMem", "freeMem", "usedMem", "B/Entry");
            FastRandom random = new FastRandom(1, 20);
            for (long loops = 0; loops * GET_UNIT_INCREMENT < GET_ENTRIES_MAXIMUM; loops++) {
                RunThreaded getThreads = new RunThreaded(threadCount, "getThreads") {
                    @Override
                    public void workerRun(final RunThreaded.Worker worker) {
                        for (long i = 0;; i++) {
                            if (i != 0 && i % COUNTER_INCREMENT == 0) {
                                if (worker.isQuitting())
                                    return;
                                getCounter.addAndGet(COUNTER_INCREMENT);
                            }
                            Object key = nextRandomKey(
                                    worker.getFastRandom(), worker.getThreadNumber());
                            // TODO this never actually finds anything. Should
                            // use higher() and lower() etc.
                            map.get(key);
                        }
                    }
                };
                getThreads.startSuspended();
                // Start them all at once
                getThreads.resume();
                // let the get threads run for a while without putting in more
                // entries.
                long startEntries = getCounter.get();
                long startTime = System.currentTimeMillis();
                Thread.sleep(GET_SAMPLE_TIME);
                long endEntries = getCounter.get();
                long endTime = System.currentTimeMillis();
                getThreads.stop();
                double deltaSeconds = (endTime - startTime) / 1000.0;
                double entriesPerSecond = (endEntries - startEntries) / deltaSeconds;
                if (reachedMemoryLimit()) {
                    System.out.println("done: reached memory limit.");
                    return;
                }
                long entries = loops * GET_UNIT_INCREMENT / 1000;
                long totalMemory = Runtime.getRuntime().totalMemory() / 1000 / 1000;
                long freeMemory = Runtime.getRuntime().freeMemory() / 1000 / 1000;
                long usedMemory = totalMemory - freeMemory;
                System.out.printf("%9d %9.3f %9d %9d %9d %9.3f\n",
                        entries,
                        entriesPerSecond / 1000,
                        totalMemory,
                        freeMemory,
                        usedMemory,
                        (double)usedMemory * 1000 / entries);
                // Add a batch of entries of size GET_UNIT_INCREMENT.
                // Doing the puts multi-threaded means we don't get an exact
                // entryCount
                RunThreaded putThreads = new RunThreaded(
                        map instanceof ConcurrentMap ? GET_PUT_THREAD_COUNT : 1, "putThreads") {
                    @Override
                    public void workerRun(final RunThreaded.Worker worker) {
                        for (long i = 0; !worker.isQuitting() &&
                                i < GET_UNIT_INCREMENT / GET_PUT_THREAD_COUNT; i++) {
                            putCounter.incrementAndGet();
                            Object key = nextRandomKey(worker.getFastRandom(),
                                    worker.getThreadNumber());
                            map.put(key, VALUE);
                        }
                    }
                };
                long startPutTime = System.currentTimeMillis();
                putThreads.start();
                putThreads.join();

                Throwable throwable = putThreads.getThrowable();
                if (throwable != null) {
                    p("done: exception = " + throwable.getMessage());
                    if (!(throwable instanceof OutOfMemoryError)) {
                        throw new RuntimeException(throwable);
                    }
                    return;
                }
                long endPutTime = System.currentTimeMillis();
                long startGCTime = System.currentTimeMillis();
                Runtime.getRuntime().gc();
                long endGCTime = System.currentTimeMillis();

                long gcTime = endGCTime - startGCTime;
                if (gcTime > GC_LIMIT_TIME) {
                    // stop if puts are too slow, so OOM is likely soon
                    p("done: reached max GC time: : " + gcTime);
                    return;
                }
                Thread.sleep(1000);
                // System.out.println("startPUt=" + startPutTime + " endPUt=" +
                // endPutTime);
                deltaSeconds = (endPutTime - startPutTime) / 1000.0;
                double putsPerSecond = GET_UNIT_INCREMENT / deltaSeconds;
                if (putsPerSecond < MIN_OPERATIONS_PER_SECOND) {
                    // stop if puts are too slow, so OOM is likely soon
                    p("done: reached minimum puts speed: " + putsPerSecond);
                    return;
                }
            }
            System.out.println("done");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testMixture1put() {
        testMixture(1, 0, 0, MAX_OPERATIONS, 0, TIMEOUT);
    }

    @Test
    public void testMixture4put() {
        Assert.assertTrue("Skipped test: not a ConcurrentMap", map instanceof ConcurrentMap);
        testMixture(4, 0, 0, MAX_OPERATIONS, 0, TIMEOUT);
    }

    @Test
    public void testMixture8put() {
        Assert.assertTrue("Skipped test: not a ConcurrentMap", map instanceof ConcurrentMap);
        testMixture(8, 0, 0, MAX_OPERATIONS, 0, TIMEOUT);
    }

    @Test
    public void testMixture24put() {
        Assert.assertTrue("Skipped test: not a ConcurrentMap", map instanceof ConcurrentMap);
        testMixture(24, 0, 0, MAX_OPERATIONS, 0, TIMEOUT);
    }

    @Test
    public void testMixture100put() {
        Assert.assertTrue("Skipped test: not a ConcurrentMap", map instanceof ConcurrentMap);
        testMixture(100, 0, 0, MAX_OPERATIONS, 0, TIMEOUT);
    }

    @Test
    public void testMixture4put4get() {
        Assert.assertTrue("Skipped test: not a ConcurrentMap", map instanceof ConcurrentMap);
        testMixture(4, 4, 0, MAX_OPERATIONS, 0, TIMEOUT);
    }

    // By default we ignore the remove tests because the
    // used memory in the JVM does not reach MAX_FRACTION_OF_MEMORY_TO_USE.

    @Test
    public void testMixture1put1get1remove() {
        Assert.assertTrue("Skipped test: not a ConcurrentMap", map instanceof ConcurrentMap);
        testMixture(1, 1, 1, MAX_OPERATIONS, 0, TIMEOUT);
    }

    @Test
    public void testMixture100puts100gets() {
        Assert.assertTrue("Skipped test: not a ConcurrentMap", map instanceof ConcurrentMap);
        testMixture(100, 100, 0, MAX_OPERATIONS, 0, TIMEOUT);
    }

    // By default we ignore the remove tests because the
    // used memory in the JVM does not reach MAX_FRACTION_OF_MEMORY_TO_USE.

    @Test
    public void testMixture100puts100gets100removes() {
        Assert.assertTrue("Skipped test: not a ConcurrentMap", map instanceof ConcurrentMap);
        testMixture(100, 100, 100, MAX_OPERATIONS, 0, TIMEOUT);
    }

    // By default we ignore the remove tests because the
    // used memory in the JVM does not reach MAX_FRACTION_OF_MEMORY_TO_USE.

    @Test
    public void testMixture2puts2gets2removes() {
        Assert.assertTrue("Skipped test: not a ConcurrentMap", map instanceof ConcurrentMap);
        testMixture(2, 2, 2, MAX_OPERATIONS, 0, TIMEOUT);
    }

    /*
     * This increases the JVM size to prevent frequent GC. It seems to work, but
     * the memory is reclaimed so quickly that the JVM immediately shrinks down
     * again, but the performance does increase dramatically.
     */
    void warmJVM() {
        ArrayList<Object> list = new ArrayList<Object>();
        long maxMem = Runtime.getRuntime().maxMemory();
        long limit = maxMem - WARM_HEAD_ROOM;
        for (long i = 0; i < limit; i += WARM_CHUNK) {
            list.add(new byte[WARM_CHUNK]);
        }
        // This is supposed to prevent the JIT from factoring out this code.
        long totalSize = 0;
        for (int i = 0; i < list.size(); i++) {
            totalSize += ((byte[])list.get(i)).length;
        }
        // We have to print something derived from the array
        // so this code has a tangible result and can't be factored
        // out.
        System.out.println("JVM_is_warm:_preallocation=" + totalSize);
    }

    @Test
    public void testMemoryEfficiencyInMixture() {
        setRunUntilOOM(true);
        testMixture(8, 0, 0, Integer.MAX_VALUE, 0, Long.MAX_VALUE);
    }

    // The shift and add keeps the keys for different threads separate
    final Object nextRandomKey(FastRandom random, int threadNumber) {
        Object key = KEY_TYPE == KeyType.LONG ?
                new Long((random.nextLong() << 10) + threadNumber) :
                random.nextString();
        return key;
    }

    /*
     * Test a mixture of put, get, and, remove with any number of threads for
     * each. A controllable pre-load can create a set of initial entries before
     * the test starts. Either Longs or Strings can be generated randomly, using
     * a special, fast randomizer called FastRandom.
     */
    void testMixture(int putThreadCount, int getThreadCount, int removeThreadCount,
            long maxOperations, final long preloadEntries, long timeOut) {
        try {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            System.out.println("Test=mixture()");
            System.out.println("preloadEntries=" + preloadEntries);
            System.out.println("put()_ThreadCount=" + putThreadCount);
            System.out.println("get()_ThreadCount=" + getThreadCount);
            System.out.println("remove()_ThreadCount=" + removeThreadCount);
            System.out.println("maximum_memory=" + Runtime.getRuntime().maxMemory() / 1000 / 1000);
            System.out.println("warm=" + USE_WARMING);
            System.out.printf("%9s %9s %9s %9s %9s %9s %9s %9s %9s %9s %9s %9s\n", "t", "entries", "puts", "gets", "removes", "put/s", "get/s", "removes/s", "JVM_size", "freeMem", "usedMem", "B/Entry");

            /*
             * There is actually a significant problem in comparing adjacent
             * tests that do not exit the JVM. We can try to gc in order to
             * minimize the effect, but it is still significant. So, these tests
             * should be run in isolation for ideal performance comparison.
             */
            // Runtime.getRuntime().gc();

            if (preloadEntries > 0) {
                System.out.println("preloading_entries");
                RunThreaded preloadThreads = new RunThreaded(
                        map instanceof ConcurrentMap ? N_PRELOAD_THREADS : 1, "preloadThreads") {
                    @Override
                    public void workerRun(final RunThreaded.Worker worker) {
                        for (long i = 0;; i++) {
                            if (i != 0 && i % COUNTER_INCREMENT == 0) {
                                if (worker.isQuitting())
                                    return;
                                entryCounter.addAndGet(COUNTER_INCREMENT);
                                if (entryCounter.get() > preloadEntries)
                                    return;
                                if (worker.checkForSuspend())
                                    worker.waitForRunnable();
                            }
                            // if (i != 0 && i % (100 * 1000) == 0) {/
                            // System.out.println("preloaded entries=" +
                            // entryCounter.get() / 1000 + "K");
                            // }
                            Object key = nextRandomKey(worker.getFastRandom(),
                                    worker.getThreadNumber());
                            map.put(key, VALUE);
                        }
                    }
                };
                preloadThreads.start();
                preloadThreads.join();
            }

            // Using a group allows all RunThreads to look like one.
            RunThreadedGroup runThreadedGroup = new RunThreadedGroup();
            RunThreaded putThreads =
                    new RunThreaded(putThreadCount, "putThreads", runThreadedGroup) {
                        @Override
                        public void workerRun(final RunThreaded.Worker worker) {
                            for (long i = 0;; i++) {
                                if (i != 0 && i % COUNTER_INCREMENT == 0) {
                                    if (worker.isQuitting())
                                        return;
                                    putCounter.addAndGet(COUNTER_INCREMENT);
                                    entryCounter.addAndGet(COUNTER_INCREMENT);
                                    if (worker.checkForSuspend())
                                        worker.waitForRunnable();
                                }
                                Object key = nextRandomKey(worker.getFastRandom(),
                                        worker.getThreadNumber());
                                map.put(key, VALUE);
                                if (isCheckingCorrectness) {
                                    if (map.get(key) == null)
                                        throw new RuntimeException("put entry does not exist");
                                }
                            }
                        }
                    };
            RunThreaded getThreads =
                    new RunThreaded(getThreadCount, "getThreads", runThreadedGroup) {
                        @Override
                        public void workerRun(final RunThreaded.Worker worker) {
                            for (long i = 0;; i++) {
                                if (i != 0 && i % COUNTER_INCREMENT == 0) {
                                    if (worker.isQuitting())
                                        return;
                                    getCounter.addAndGet(COUNTER_INCREMENT);
                                    if (worker.checkForSuspend())
                                        worker.waitForRunnable();
                                }
                                Object key = nextRandomKey(worker.getFastRandom(),
                                        worker.getThreadNumber());
                                // These conditional branches are optimized away
                                if (GET_TYPE == GetType.GET) {
                                    map.get(key);
                                } else if (!(keySet instanceof NavigableSet)) {
                                    map.get(key);
                                } else {
                                    NavigableSet navigableKeySet = (NavigableSet)keySet;
                                    if (GET_TYPE == GetType.HIGHER) {
                                        navigableKeySet.higher(key);
                                    } else if (GET_TYPE == GetType.LOWER) {
                                        navigableKeySet.lower(key);
                                    } else if (GET_TYPE == GetType.LOWER_AT_END) {
                                        navigableKeySet.lower(Long.MAX_VALUE);
                                    } else if (GET_TYPE == GetType.HIGHER_AT_START) {
                                        navigableKeySet.higher(Long.MIN_VALUE);
                                    }
                                }
                            }
                        }
                    };
            // Only for NavigableMaps
            RunThreaded removeThreads =
                    new RunThreaded(removeThreadCount, "removeThreads", runThreadedGroup) {
                        @Override
                        public void workerRun(final RunThreaded.Worker worker) {
                            for (long i = 0;;) {
                                if (i != 0 && i % COUNTER_INCREMENT == 0) {
                                    if (worker.isQuitting())
                                        return;
                                    removeCounter.addAndGet(COUNTER_INCREMENT);
                                    entryCounter.addAndGet(-COUNTER_INCREMENT);
                                    if (worker.checkForSuspend())
                                        worker.waitForRunnable();
                                }
                                Object key = nextRandomKey(worker.getFastRandom(),
                                        worker.getThreadNumber());
                                if (REMOVE_TYPE == RemoveType.RANDOM) {
                                    key = ((NavigableMap)map).ceilingKey(key);
                                } else if (REMOVE_TYPE == RemoveType.START) {
                                    key = ((NavigableMap)map).higherKey(Long.MIN_VALUE);
                                } else if (REMOVE_TYPE == RemoveType.END) {
                                    key = ((NavigableMap)map).lowerKey(Long.MAX_VALUE);
                                }
                                if (key != null) {
                                    if (isCheckingCorrectness) {
                                        if (map.get(key) == null)
                                            throw new RuntimeException("floor did not return a key");
                                    }
                                    map.remove(key);
                                    if (isCheckingCorrectness) {
                                        if (map.get(key) != null)
                                            throw new RuntimeException("removed entry still exists");
                                    }
                                    i++;
                                }
                            }
                        }
                    };

            // Allocate some arrays to force the JVM to expand.
            // It contracts right away again, but the performance is more
            // meaningful, and the concurrency shows up. Instead, however,
            // -Xms is more extreme - it's 'hot'.
            // warmJVM();

            // Runtime.getRuntime().gc();
            // Thread.sleep(GC_WAIT_TIME);

            // start all at once.
            runThreadedGroup.startSuspended();
            runThreadedGroup.setRunnable(true);
            try {
                double tStart = System.currentTimeMillis() / 1000.0;
                while (true) {
                    double tLast = System.currentTimeMillis() / 1000.0;
                    long putsLast = putCounter.get();
                    long getsLast = getCounter.get();
                    long removesLast = removeCounter.get();

                    Thread.sleep(SAMPLE_TIME);

                    double tCurrent = System.currentTimeMillis() / 1000.0;
                    long entries = entryCounter.get();
                    long puts = putCounter.get();
                    long gets = getCounter.get();
                    long removes = removeCounter.get();

                    double deltaT = tCurrent - tLast;
                    double t = tCurrent - tStart;

                    long deltaPuts = puts - putsLast;
                    double putsPerSecond = deltaPuts / deltaT;

                    long deltaGets = gets - getsLast;
                    double getsPerSecond = deltaGets / deltaT;

                    long deltaRemoves = removes - removesLast;
                    double removesPerSecond = deltaRemoves / deltaT;

                    /*
                     * This would have to stop the updates during the count to
                     * guarantee to avoid a NoSuchElementException, which in a
                     * concurrent environment can happen because of a deletion
                     * between the hasNext() and the next().
                     */
                    // long iteratedCount = 0;
                    // for (Object o : map.keySet()) {
                    // iteratedCount++;
                    // }

                    long totalMemory = Runtime.getRuntime().totalMemory() / 1000 / 1000;
                    long freeMemory = Runtime.getRuntime().freeMemory() / 1000 / 1000;
                    long maxMemory = Runtime.getRuntime().maxMemory() / 1000 / 1000;
                    long usedMemory = totalMemory - freeMemory;
                    double bytesPerEntry = (double)usedMemory * 1e6 / (double)entries;
                    System.out.printf("%9.3f %9.3f %9.3f %9.3f %9.3f %9.3f %9.3f %9.3f %9d %9d %9d %9.3f\n",
                            t,
                            entries / 1e3,
                            puts / 1e3,
                            gets / 1e3,
                            removes / 1e3,
                            putsPerSecond / 1e3,
                            getsPerSecond / 1e3,
                            removesPerSecond / 1e3,
                            totalMemory,
                            freeMemory,
                            usedMemory,
                            bytesPerEntry
                            );
                    if (reachedMemoryLimit()) {
                        System.out.println("done: reached memory limit.");
                        return;
                    } else if (putCounter.get() + getCounter.get() +
                            removeCounter.get() > maxOperations) {
                        System.out.println("done");
                        return;
                    } else if (t > timeOut / 1000.0) {
                        System.out.println("done timeout");
                        return;
                    } else if (runThreadedGroup.getThrowable() != null) {
                        Throwable throwable = runThreadedGroup.getThrowable();
                        if (isRunUntilOOM && throwable instanceof OutOfMemoryError) {
                            p("OutOfMemory as expected");
                            // At this point, the JVM is actually unstable, but
                            // we assume that the GC of the Maps will allow
                            // continuation.
                            return;
                        }
                        throwable.printStackTrace();
                        throw new RuntimeException(throwable);
                        // } else if (putsPerSecond + getsPerSecond +
                        // removesPerSecond
                        // < MIN_OPERATIONS_PER_SECOND) {
                        // p("done - too slow");
                        // return;
                    }
                    /*
                     * Note: In USE_GC_WAITS mode, you should manually watch the
                     * system CPU activity monitor to make sure there are gaps
                     * between the samples. At the end of the run, however,
                     * there will be continuous 100% usage because the GC will
                     * use all of the cycles, and the JVM will be stalled at
                     * that point, and OOME will happen after a while.
                     * 
                     * There is no official programmatic way to detect this 100%
                     * CPU GC condition, and in fact there is no way (without
                     * JNI) to watch the CPU activity at all, but you can
                     * predict problems when the JVM total Memory reaches the
                     * maximum. At that point, there is still the possibility of
                     * constructing more Objects and using more space for a
                     * while, but as that goes on, the likelihood of the stall
                     * increases and OOME can happen at any time - actually,
                     * OOME can happen well before the limit is reached! So a
                     * fair policy is at least to react immediately when the JVM
                     * reaches the size limit or some smaller guess.
                     * 
                     * The current JVM size in virtual memory is
                     * Runtime.getRuntime().totalMemory() and the limit is
                     * Runtime.getRuntime().maxMemory(), which is constant once
                     * the JVM process launches. The total memory usually
                     * increases, but may decrease at times.
                     */
                    if (USE_GC_WAITS) {
                        runThreadedGroup.suspend();
                        runThreadedGroup.waitForNoWorkersRunning();

                        long tGCStart = System.currentTimeMillis();
                        Runtime.getRuntime().gc();
                        // Thread.sleep(GC_WAIT_TIME);
                        long tGCEnd = System.currentTimeMillis();

                        runThreadedGroup.resume();
                        runThreadedGroup.waitForAllWorkersRunning();

                        long tGC = tGCEnd - tGCStart;
                        // System.out.println("GC time: " + tGC);
                        // This doesn't work because sometimes GC stays at 1.5
                        // sec and
                        // then we get OOME anyway
                        if (tGC > GC_LIMIT_TIME) {
                            System.out.println("GC too slow: " + tGC + " done");
                            return;
                        }
                    }

                }
            } finally {
                runThreadedGroup.stop();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Do not substantially change this, because its output is official.
     */
    static final Class[] MAP_EFFICIENCY_TEST_CLASSES_ONE_THREAD = {
            // run the first one twice to initialize memory
            HashMap.class,
            ConcurrentHashMap.class,
            TreeMap.class,
            ConcurrentSkipListMap.class,
            AirConcurrentMap.class
    };
    static final Class[] MAP_EFFICIENCY_TEST_CLASSES_MULTI_THREAD = {
            // run the first one twice to initialize memory
            ConcurrentHashMap.class,
            ConcurrentSkipListMap.class,
            AirConcurrentMap.class
    };
    static final int EFFICIENCY_TEST_MULTI_THREAD_COUNT = 8;

    @Test
    public void testMemoryEfficiencyOfMap() throws InstantiationException, IllegalAccessException {
        System.out.println("Map memory efficiency test" +
                " maxMemory=" + Runtime.getRuntime().maxMemory() / 1e6);
        System.out.println("Multi-Threads Tests: threadCount=" + EFFICIENCY_TEST_MULTI_THREAD_COUNT);
        for (Class classToTest : MAP_EFFICIENCY_TEST_CLASSES_MULTI_THREAD) {
            testOneMemoryEfficiency(classToTest, EFFICIENCY_TEST_MULTI_THREAD_COUNT);
            testOneMemoryEfficiency(classToTest, EFFICIENCY_TEST_MULTI_THREAD_COUNT);
        }
        System.out.println("Single-Thread Tests");
        for (Class classToTest : MAP_EFFICIENCY_TEST_CLASSES_ONE_THREAD) {
            testOneMemoryEfficiency(classToTest, 1);
            testOneMemoryEfficiency(classToTest, 1);
        }
    }

    void testOneMemoryEfficiency(Class classToTest, int threadCount)
            throws InstantiationException, IllegalAccessException {
        final Map<Object, Object> map =
                ((Map<Object, Object>)classToTest.newInstance());
        final long maxMemory = Runtime.getRuntime().maxMemory();
        System.out.println("TestClass=" + classToTest.getName());
        doGC();
        final AtomicLong entryCount = new AtomicLong();
        RunThreaded runThreaded = new RunThreaded(threadCount, "memory efficiency") {
            public void workerRun(RunThreaded.Worker worker) {
                for (long i = 0; !worker.isQuitting(); i++) {
                    // if (i % (1024 * 1024) == 0)
                    // System.out.print(".");
                    if (reachedMemoryLimit()) {
                        return;
                    }
                    Long n = new Long(worker.getFastRandom().nextLong());
                    map.put(n, VALUE);
                    entryCount.incrementAndGet();
                }
            }

            @Override
            public void onThrowable(Throwable e) {
                e.printStackTrace();
                quit();
            }
        };
        final long totalMemory0 = Runtime.getRuntime().totalMemory();
        final long freeMemory0 = Runtime.getRuntime().freeMemory();
        final long usedMemory0 = totalMemory0 - freeMemory0;
        printMemoryUsage("\tstart run:");
        long t0 = System.currentTimeMillis();
        runThreaded.start();
        runThreaded.join();
        long t1 = System.currentTimeMillis();
        double duration = (t1 - t0) / 1000.0;
        final long totalMemory1 = Runtime.getRuntime().totalMemory();
        final long freeMemory1 = Runtime.getRuntime().freeMemory();
        final long usedMemory1 = totalMemory1 - freeMemory1;
        final long deltaUsedMemory = usedMemory1 - usedMemory0;
        final double bytesPerEntry = (double)deltaUsedMemory / entryCount.get();
        printMemoryUsage("\tend run:  ");
        System.out.printf("\tentries=%5.2fM t=%5.2f entries/s=%6.0f bytes/Entry=%6.3f\n",
                entryCount.get() / 1e6,
                duration,
                entryCount.get() / duration,
                bytesPerEntry
                );
    }

    void printMemoryUsage(String msg) {
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;
        System.out.printf("%15s memory total=%9.3f free=%9.3f used=%9.3f\n",
                msg,
                totalMemory / 1e6,
                freeMemory / 1e6,
                usedMemory / 1e6);
    }

    /**
     * Test the Iterators of the standard Maps vs AirConcurrentMap. Note the
     * testing is best done in an IDE as described in the class doc.
     */
    static final long MAX_SIZE_SMALL = 100_000;
    static final long INCREMENT_SMALL = 1000;
    static final int REPEAT_COUNT_SMALL = 25;

    static final long MAX_SIZE_LARGE = 4_000_000;
    static final long INCREMENT_LARGE = 100_000;
    static final int REPEAT_COUNT_LARGE = 10;

    static final FastRandom fastRandom = new FastRandom(1, 1000);
    static final Random random = new Random(1);

    enum CollectionType {
        KEYS, ENTRIES, VALUES
    }

    @Test
    public void testSetIterator() {
        for (int i = 0; i < 2; i++) {
            System.out.println("KEYS");
            System.out.println(" count(K) AirConcurrentMap ConcurrentSkipListMap CHashMap HashMap TreeMap");
            AirConcurrentMap<Object, Object> airMap = new AirConcurrentMap<Object, Object>();
            ConcurrentSkipListMap<Object, Object> cslm = new ConcurrentSkipListMap<Object, Object>();
            ConcurrentHashMap<Object, Object> concurrentHashMap = new ConcurrentHashMap<Object, Object>();
            HashMap<Object, Object> hashMap = new HashMap<Object, Object>();
            TreeMap<Object, Object> treeMap = new TreeMap<Object, Object>();
            timeMaps(CollectionType.KEYS, airMap, cslm, concurrentHashMap, hashMap, treeMap);
        }
        for (int i = 0; i < 2; i++) {
            System.out.println("ENTRIES");
            System.out.println(" count(K) AirConcurrentMap ConcurrentSkipListMap CHashMap HashMap TreeMap");
            AirConcurrentMap<Object, Object> airMap = new AirConcurrentMap<Object, Object>();
            ConcurrentSkipListMap<Object, Object> cslm = new ConcurrentSkipListMap<Object, Object>();
            ConcurrentHashMap<Object, Object> concurrentHashMap = new ConcurrentHashMap<Object, Object>();
            HashMap<Object, Object> hashMap = new HashMap<Object, Object>();
            TreeMap<Object, Object> treeMap = new TreeMap<Object, Object>();
            timeMaps(CollectionType.ENTRIES, airMap, cslm, concurrentHashMap, hashMap, treeMap);
        }
        for (int i = 0; i < 2; i++) {
            System.out.println("VALUES");
            System.out.println(" count(K) AirConcurrentMap ConcurrentSkipListMap CHashMap HashMap TreeMap");
            AirConcurrentMap<Object, Object> airMap = new AirConcurrentMap<Object, Object>();
            ConcurrentSkipListMap<Object, Object> cslm = new ConcurrentSkipListMap<Object, Object>();
            ConcurrentHashMap<Object, Object> concurrentHashMap = new ConcurrentHashMap<Object, Object>();
            HashMap<Object, Object> hashMap = new HashMap<Object, Object>();
            TreeMap<Object, Object> treeMap = new TreeMap<Object, Object>();
            timeMaps(CollectionType.VALUES, airMap, cslm, concurrentHashMap, hashMap, treeMap);
        }
        // AirConcurrentSet is not released as of 1.0.
        // for (int i = 0; i < 2; i++) {
        // System.out.println("SETS");
        // System.out.println(" count(K) AirConcurrentSet ConcurrentSkipListSet HashSet TreeSet");
        // AirConcurrentSet<Object> airSet = new AirConcurrentSet<Object>();
        // ConcurrentSkipListSet<Object> csls = new
        // ConcurrentSkipListSet<Object>();
        // HashSet<Object> hashSet = new HashSet<Object>();
        // TreeSet<Object> treeSet = new TreeSet<Object>();
        // timeSets(airSet, csls, hashSet, treeSet);
        // }
    }

    void timeSets(Set<Object>... sets) {
        printTimeSets(INCREMENT_SMALL, MAX_SIZE_SMALL, REPEAT_COUNT_LARGE, sets);
        printTimeSets(INCREMENT_LARGE, MAX_SIZE_LARGE, REPEAT_COUNT_SMALL, sets);

    }

    void timeMaps(CollectionType type, Map<Object, Object>... maps) {
        printTimeMaps(type, INCREMENT_SMALL, MAX_SIZE_SMALL, REPEAT_COUNT_LARGE, maps);
        printTimeMaps(type, INCREMENT_LARGE, MAX_SIZE_LARGE, REPEAT_COUNT_SMALL, maps);

    }

    void printTimeSets(long increment, long max, int repeatCount, Set<Object>... sets) {
        System.out.println();
        for (long size = increment; size <= max; size += increment) {
            System.out.printf("%9.0f ", (double)size / 1e3);
            for (Set<Object> set : sets) {
                long currentSetSize = set.size();
                // System.out.println("size=" + currentSetSize);
                while (currentSetSize < size) {
                    long k = random.nextLong();
                    Long key = new Long(k);
                    set.add(key);
                    currentSetSize++;
                }
                double speed = timeCollection(set, size, repeatCount);
                System.out.printf("%7.3f ", speed);
            }
            System.out.println();
        }
        System.out.println();
    }

    void printTimeMaps(CollectionType type, long increment, long max, int repeatCount, Map<Object, Object>... maps) {
        System.out.println();
        for (long size = increment; size <= max; size += increment) {
            System.out.printf("%9.0f ", (double)size / 1e3);
            for (Map<Object, Object> map : maps) {
                long currentSize = map.size();
                // System.out.println("size=" + currentSetSize);
                while (currentSize < size) {
                    long k = random.nextLong();
                    Long key = new Long(k);
                    map.put(key, key);
                    currentSize++;
                }
                // @formatter:off
				double speed =
					type == CollectionType.KEYS ?
							timeCollection(map.keySet(), size, repeatCount) :
					type == CollectionType.ENTRIES ?
							timeCollection(map.entrySet(), size, repeatCount) :
					type == CollectionType.VALUES ?
							timeCollection(map.values(), size, repeatCount) : 0;
				// @formatter:on
                System.out.printf("%7.3f ", speed);
            }
            System.out.println();
        }
        System.out.println();
    }

    double timeCollection(Collection< ? > c, long newSize, int repeatCount) {
        long count = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < repeatCount; i++) {
            count = 0;
            for (Object key : c) {
                count++;
            }
        }
        // System.out.println("COUNT=" + count + " size=" + currentSetSize +
        // " newSize=" + newSize + " size()=" + set.size());
        long t1 = System.nanoTime();
        if (newSize != count)
            throw new RuntimeException("count=" + count + " newSize=" + newSize);
        double duration = (t1 - t0) / 1e9 / repeatCount;
        double iterationsPerUSec = newSize / duration / 1e6;
        return iterationsPerUSec;
    }

    // Do and wait for GC, hoping totalMemory will go back to -Xms - it doesn't.
    // It isn't actually a big problem, because we do the accounting
    // including the free memory.
    void doGC() {
        System.gc();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
    }

    boolean reachedMemoryLimit() {
        long totalMemory = Runtime.getRuntime().totalMemory() / 1000 / 1000;
        long freeMemory = Runtime.getRuntime().freeMemory() / 1000 / 1000;
        long maxMemory = Runtime.getRuntime().maxMemory() / 1000 / 1000;
        long usedMemory = totalMemory - freeMemory;
        return usedMemory > maxMemory * MAX_FRACTION_OF_MEMORY_TO_USE;
    }

    static synchronized void p(Object o) {
        System.out.println(Thread.currentThread() + " " + o);
    }

    public static void main(String... args) {
        try {
            //new AirConcurrentMapPerformanceTestOfficial().testGet1();
            // new AirConcurrentMapPerformanceTestOfficial().testGet4();
            // new AirConcurrentMapPerformanceTestOfficial().testGet8();
            // new AirConcurrentMapPerformanceTestOfficial().testGet100();
//            new AirConcurrentMapPerformanceTestOfficial().testMixture1put();
  //          new AirConcurrentMapPerformanceTestOfficial().testMixture4put();
//            new AirConcurrentMapPerformanceTestOfficial().testMixture8put();
//            new AirConcurrentMapPerformanceTestOfficial().testMixture24put();
//            new AirConcurrentMapPerformanceTestOfficial().testMixture100put();
//            new AirConcurrentMapPerformanceTestOfficial().testMixture4put4get();
//            new AirConcurrentMapPerformanceTestOfficial().testMixture100puts100gets();
            // The remove tests do not terminate on memory limit reached
            // new
            // AirConcurrentMapPerformanceTestOfficial().testMixture1put1get1remove();
            // new
            // AirConcurrentMapPerformanceTestOfficial().testMixture2puts2gets2removes();
            new AirConcurrentMapPerformanceTestOfficial().testMemoryEfficiencyInMixture();
            new AirConcurrentMapPerformanceTestOfficial().testMemoryEfficiencyOfMap();
            new AirConcurrentMapPerformanceTestOfficial().testSetIterator();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
