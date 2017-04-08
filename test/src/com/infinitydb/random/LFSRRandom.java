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

package com.infinitydb.random;

import java.util.Random;

/**
 * This pseudo random number generator or PRNG has a period of about 2^64, which
 * you can't get by combining a 2^32 PRNG with itself or
 * com.infinitydb.FastRandom, which is a dual 16-bit one. The java.util.Random
 * has an internal state of 42 bits.
 * 
 * @author Roger
 */
public class LFSRRandom extends Random {
    long v;

    public LFSRRandom(long seed) {
        if (seed == 0)
            seed = 1;
        v = seed;
    }

    public long nextLong() {
        long taps = ((v >> 62) ^ (v >> 59) ^ (v >> 57) ^ (v >> 56)) & 1;
        v <<= 1;
        v |= taps;
        return v;
    }

    public int nextInt() {
        return (int)nextLong();
    }

    public static void main(String[] args) {
        p("LFSRRandom.");
        LFSRRandom fr = new LFSRRandom(0);
        for (int i = 0; i < 1000; i++) {
            p("nextLong()=" + fr.nextLong());
        }
        p("LFSRRandom done.");
    }

    private static void p(Object o) {
        System.out.println(o);
    }
}
