// Copyright (C) 1997-2017 Roger L. Deran.
//
//    This file is part of AirConcurrentMap. AirConcurrentMap is
//    proprietary software.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.infinitydb.map.air.AirConcurrentMap;
import com.infinitydb.map.air.AirConcurrentSet;
import com.infinitydb.map.visitor.MapVisitor;
import com.infinitydb.map.visitor.ThreadedMapVisitor;
import com.infinitydb.map.visitor.Visitable;
import com.infinitydb.map.visitor.VisitableArrayWrapper;
import com.infinitydb.map.visitor.VisitableListWrapper;
import com.infinitydb.map.visitor.VisitableMap;
import com.infinitydb.map.visitor.VisitableMapWrapper;
import com.infinitydb.map.visitor.VisitableSetWrapper;

/**
 * Show how to make a MapVisitor and use it with an AirConcurrentMap or other
 * VisitableMap. ThreadedMapVisitor subclass is also shown with its additional
 * ability to split and merge visitors in response to the threading
 * implementation, such as AirConcurrentMap's internal thread pool.
 * <p>
 * It is also possible to wrap any non-visitable map so that it can be visited
 * as well, but only serially a present.
 * 
 * @author Roger
 */
public class VisitorExample {

    /**
     * A stateless visitor is very easy.
     *
     * @param <K>
     *            key to be printed
     * @param <V>
     *            value to be printed
     */
    static class PrintVisitor extends MapVisitor<Object, Object> {
        public void visit(Object key, Object value) {
            System.out.println("key=" + key + " value=" + value);
        }
    }

    /**
     * A stateful visitor that adds up the rounded values.
     * 
     * @param <K>
     *            The type of the keys of the map whose values are to be summed.
     *            Not used.
     */
    static class Summer extends MapVisitor<Object, Number> {
        // We could use double or float as well.
        long sum;

        public long getSum(VisitableMap< ? , Number> map) {
            sum = 0;
            map.visit(this);
            return sum;
        }

        // Optional: We can choose to provide other getSum() overloads for
        // convenience.
        public long getSum(List<Number> list) {
            return getSum(new VisitableListWrapper<Number>(list));
        }

        @Override
        public void visit(Object key, Number value) {
            sum += value.longValue();
        }
    }

    /**
     * Here we enhance the simple summing visitor with the necessary split/merge
     * for parallelism. The parallelism capability is provided by extending
     * ThreadedMapVisitor instead of MapVisitor.
     *
     * @param <K>
     *            The type of the keys of the map whose values are to be summed.
     *            Not used.
     */
    static class ThreadedSummer extends ThreadedMapVisitor<Object, Number> {
        // We could use double or float as well.
        long sum;

        public long getSum(VisitableMap< ? , Number> map) {
            sum = 0;
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

    /**
     * Demonstrate the simplicity of using these fast re-useable visitors. The
     * resulting code is more self-descriptive, taking only one expression.
     */
    public static void main(String... args) {
        AirConcurrentMap<Long, Number> map = new AirConcurrentMap<Long, Number>();
        map.put(1L, 1L);

        /**
         * Prints "key=key value=1"
         */
        map.visit(new PrintVisitor());

        /**
         * A single-line re-use of a handy visitor class. We could reuse an
         * instance of it for even tighter, faster, but still clear code. It
         * prints "sum=1"
         */
        System.out.println("sum=" + new Summer().getSum(map));

        /**
         * A parallel visitor. It works the same as the non-parallel one but
         * faster. It is not guaranteed the multiple threads will actually be
         * used, such as when the map is small.
         */
        System.out.println("threaded sum=" + new ThreadedSummer().getSum(map));

        /**
         * How to wrap a list so that its elements can be summed easily. The
         * VisitableListSWrapper does not currently support threading (v .3.0,
         * 6/2016), so the fact that ThreadedSummer is a subclass of
         * ThreadedMapVisitor instead of just MapVisitor is not important, and
         * it will be used in non-threaded mode. Alternatively, ThreadedSummer
         * could have provided a getSum(List) to avoid the wrapping here.
         */
        List<Number> list = new ArrayList<Number>();
        list.add(1);
        System.out.println("list sum=" + new ThreadedSummer().getSum(new VisitableListWrapper<Number>(list)));

        /**
         * Arrays can be wrapped just like lists.
         */
        Number[] numbers = new Number[] { 1 };
        System.out.println("array sum=" + new ThreadedSummer().getSum(new VisitableArrayWrapper(numbers)));

        /**
         * In the example of visiting a set, we use a virtual value of Long(1)
         * so the sum turns out to be the same as the count. While
         * AirConcurrentSet will eventually be able to use threads in this
         * situation, most likely VisitableSetWrapper will not be able to
         * parallelize sets in general.
         */
        Set<String> set = new AirConcurrentSet<String>();
        set.add("a single element");
        System.out.println("set sum=" + new ThreadedSummer().getSum(
                new VisitableSetWrapper<String, Number>(set, new Long(1))));

        /**
         * Suppose we want to visit a list, but for some reason we have only a
         * threaded visitor at hand, and we want to run it in sequential mode.
         * We can configure it this way. The configuration setters on Visitable
         * chain by returning this. We set reorderingAllowed(true) in case that
         * can be used to speed up some Maps but it isn't very important.
         */
        Visitable visitable = new VisitableListWrapper<Number>(list)
                .getVisitable().setThreadingAllowed(false).setReorderingAllowed(true);
        System.out.println("configured sum=" + new ThreadedSummer().getSum(visitable));

        /**
         * Make a regular Map visitable using a MapVisitor. Java 8 regular Maps
         * can also be scanned using streams, which is faster than this, but it
         * is slower than real threading as provided by AirConcurrentMap. If the
         * regularMap may happen to also implement VisitableMap at runtime, use
         * conditional client code like that below.
         * 
         * Of course using streams locks out Java 6 and 7 systems like old
         * phones and some IoT devices.
         */
        Map<String, Number> regularMap = new HashMap<String, Number>();
        regularMap.put("a single entry", 1L);
        System.out.println("regularMap sum=" + new ThreadedSummer().getSum(
                new VisitableMapWrapper<String, Number>(regularMap)));

        /**
         * Some typical client code in case map may not be a VisitableMap. The
         * code can be moved into ThreadedSummer.getSum(Map) to make
         * ThreadedSummer more general, such as with an overload of getSum().
         */
        long sum = map instanceof VisitableMap ?
                new ThreadedSummer().getSum(map) :
                map.values().stream().parallel()
                        .mapToLong(v -> ((Long)v).longValue())
                        .reduce(0L, (x, y) -> x + y);
        System.out.println("conditional mixture sum=" + sum);

    }
}
