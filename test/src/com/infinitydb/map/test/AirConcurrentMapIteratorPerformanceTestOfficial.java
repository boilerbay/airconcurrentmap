// Copyright (C) 1997-2017 Roger L. Deran. All Rights Reserved.
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

import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import com.infinitydb.map.air.AirConcurrentMap;
import com.infinitydb.random.FastRandom;

/**
 * Test the speed of the Iterators of the standard Java library Maps and Sets
 * and compare with those of AirConcurrentMap and AirConcurrentSet.
 * 
 * com.infinitydb.air.AirConcurrentMap is a
 * java.util.concurrent.ConcurrentNavigableMap implementation that is very fast
 * over a wide range of Thread counts and Entry counts compared to all of the
 * standard Maps. It is much more memory efficient than any standard Map for
 * approximately one hundred Entries or more. Being a ConcurrentNavigableMap, it
 * is a drop-in replacement for the standard ConcurrentSkipListMap or any other
 * Map. AirConcurrentSet has similar characteristics and is a drop-in
 * replacement for any Set or SortedSet. AirConcurrentSet and AirConcurrentMap
 * require Java 1.6 or later, because that version introduced the
 * ConcurrentNavigableMap interface.
 * 
 * Each test runs twice, and the second run is more representative, because the
 * memory has been allocated for the JVM, and the JIT has optimized the relevant
 * code.
 * 
 * @author Roger Deran
 * 
 */
public class AirConcurrentMapIteratorPerformanceTestOfficial {
    static final long MAX_SIZE_SMALL = 100 * 1000L;
    static final long INCREMENT_SMALL = 1000;
    static final int REPEAT_COUNT_SMALL = 25;

    static final long MAX_SIZE_LARGE = 4 * 1000 * 1000L;
    static final long INCREMENT_LARGE = 100 * 1000;
    static final int REPEAT_COUNT_LARGE = 10;

    static final FastRandom fastRandom = new FastRandom(1, 1000);
    static final Random random = new Random(1);

    enum CollectionType {
        KEYS, ENTRIES, VALUES
    }

    public void testKeySetIterators() {
        for (int i = 0; i < 2; i++) {
            System.out.println("KEYS");
            ConcurrentSkipListMap<Object, Object> cslm = new ConcurrentSkipListMap<Object, Object>();
            AirConcurrentMap<Object, Object> airMap = new AirConcurrentMap<Object, Object>();
            ConcurrentHashMap<Object, Object> concurrentHashMap = new ConcurrentHashMap<Object, Object>();
            HashMap<Object, Object> hashMap = new HashMap<Object, Object>();
            TreeMap<Object, Object> treeMap = new TreeMap<Object, Object>();
            THashMap<Object, Object> tHashMap = new THashMap<Object, Object>();
            timeMaps(CollectionType.KEYS, airMap, cslm, concurrentHashMap, hashMap, treeMap, tHashMap);
            // timeMaps(CollectionType.KEYS, cslm, airMap);
        }
    }

    public void testEntrySetIterators() {
        for (int i = 0; i < 2; i++) {
            System.out.println("ENTRIES");
            AirConcurrentMap<Object, Object> airMap = new AirConcurrentMap<Object, Object>();
            ConcurrentSkipListMap<Object, Object> cslm = new ConcurrentSkipListMap<Object, Object>();
            ConcurrentHashMap<Object, Object> concurrentHashMap = new ConcurrentHashMap<Object, Object>();
            HashMap<Object, Object> hashMap = new HashMap<Object, Object>();
            TreeMap<Object, Object> treeMap = new TreeMap<Object, Object>();
            THashMap<Object, Object> tHashMap = new THashMap<Object, Object>();
            timeMaps(CollectionType.ENTRIES, airMap, cslm, concurrentHashMap, hashMap, treeMap, tHashMap);
            // timeMaps(CollectionType.ENTRIES, airMap, cslm);
        }
    }

    public void testValuesIterators() {
        for (int i = 0; i < 2; i++) {
            System.out.println("VALUES");
            AirConcurrentMap<Object, Object> airMap = new AirConcurrentMap<Object, Object>();
            ConcurrentSkipListMap<Object, Object> cslm = new ConcurrentSkipListMap<Object, Object>();
            ConcurrentHashMap<Object, Object> concurrentHashMap = new ConcurrentHashMap<Object, Object>();
            HashMap<Object, Object> hashMap = new HashMap<Object, Object>();
            TreeMap<Object, Object> treeMap = new TreeMap<Object, Object>();
            THashMap<Object, Object> tHashMap = new THashMap<Object, Object>();
            timeMaps(CollectionType.VALUES, airMap, cslm, concurrentHashMap, hashMap, treeMap, tHashMap);
            // timeMaps(CollectionType.VALUES, airMap, cslm);
        }
        // public void testSetIterators() {
        // // Current AirConcurrentSet is not released - when it is, uncomment
        // this.
        // for (int i = 0; i < 2; i++) {
        // System.out.println("SETS");
        // System.out.println(" count(K) AirConcurrentSet ConcurrentSkipListSet HashSet TreeSet");
        // AirConcurrentSet<Object> airSet = new AirConcurrentSet<Object>();
        // ConcurrentSkipListSet<Object> csls = new
        // ConcurrentSkipListSet<Object>();
        // HashSet<Object> hashSet = new HashSet<Object>();
        // TreeSet<Object> treeSet = new TreeSet<Object>();
        // THashSet<Object> tHashSet = new THashSet<Object>();
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
        System.out.print("count(K) ");
        for (Map map : maps) {
            String names[] = map.getClass().getName().split("[.]");
            String name = names[names.length - 1];
            System.out.print(name + " ");
        }
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
                count += ((Long)key).longValue();
            }
        }
        // System.out.println("COUNT=" + count + " size=" + currentSetSize +
        // " newSize=" + newSize + " size()=" + set.size());
        long t1 = System.nanoTime();
        double duration = (t1 - t0) / 1e9 / repeatCount;
        double iterationsPerUSec = newSize / duration / 1e6;
        return iterationsPerUSec;
    }

    public static void main(String... args) {
        try {
            new AirConcurrentMapIteratorPerformanceTestOfficial().testKeySetIterators();
            new AirConcurrentMapIteratorPerformanceTestOfficial().testEntrySetIterators();
            new AirConcurrentMapIteratorPerformanceTestOfficial().testValuesIterators();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
