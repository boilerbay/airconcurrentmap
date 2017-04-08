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
 * NOTE: This may not have close to 2^64 states. The product of the CONSTANT0
 * and CONSTANT1 is less than 2^32. That is very common in PRNGs, however. If
 * you use it to generate over 2^32 different keys to insert into an AirConcurrentMap
 * or InfinityDB or even a HashTable, you will see duplicates
 * and it can be very confusing. Use the LFSRRandom instead, which has 2^64
 * bits. It can be even worse than 2^32 depending on the seed.
 * 
 * About 167M/s, while java.util.Random goes about 61M/s
 * 
 * Algorithm source unclear.
 * 
 * @author Roger Deran
 */

public class FastRandom extends Random {

	// The constants
	private static final int CONSTANT0 = 36969;
	private static final int CONSTANT1 = 18000;
	// for mixing seed provided by client
	private static final int MASK0 = 0x3bdff65c; // from /dev/random
	private static final int MASK1 = 0x1deef660; // from /dev/random

	/**
	 * These are not good for 64 bits of randomness! See LFSRRandom
	 * for 64 bits. seed0 must not be zero or 0x464fffff after XOR with MASK0
	 * seed1 must not be zero or 0x9068ffff after XOR with MASK1.
	 */
	private int seed0;
	private int seed1;
	
	/*
	 * If initialized using FastRandom(seed,length) then this buffer
	 * is initialized and we can use nextString().
	 */
	char[] buf;

	public FastRandom(long seed) {
		setSeed(seed);
	}

	public FastRandom(long seed, int bufLength) {
		setSeed(seed, bufLength);
	}

	public void setSeed(long seed) {
		this.seed0 = (int)((seed & 0xffff) ^ MASK0);
		this.seed1 = (int)((seed >>> 32) ^ MASK1);
	}
	
	public void setSeed(long seed, int bufLength) {
		setSeed(seed);
		buf = new char[bufLength]; 
	}

	public short nextShort() {
		seed0 = (CONSTANT0 * (seed0 & 0xffff)) + (seed0 >> 16);
		return (short)seed0;
	}

	public int nextInt() {
		seed0 = (CONSTANT0 * (seed0 & 0xffff)) + (seed0 >> 16);
		seed1 = (CONSTANT1 * (seed1 & 0xffff)) + (seed1 >> 16);
		return (seed1 << 16) ^ seed0;
	}

	public long nextLong() {
		long x = 0;
		seed0 = (CONSTANT0 * (seed0 & 0xffff)) + (seed0 >> 16);
		x ^= seed0 << 48;
		seed1 = (CONSTANT1 * (seed1 & 0xffff)) + (seed1 >> 16);
		x ^= seed1 << 32;
		seed0 = (CONSTANT0 * (seed0 & 0xffff)) + (seed0 >> 16);
		x ^= seed0 << 16;
		seed1 = (CONSTANT1 * (seed1 & 0xffff)) + (seed1 >> 16);
		x ^= seed1;
		return x;
	}

	public static void main(String[] args) {
		p("FastRandom.");
		FastRandom fr = new FastRandom(0);
		for (int i = 0; i < 100; i++) {
			p("nextInt()=" + fr.nextInt());
		}
		p("FastRandom done.");
	}

	public String nextString() {
		return nextString(buf.length);
	}
	
	// BUG: length must be divisible by 4
	public String nextString(int desiredStringLength) {
		if (desiredStringLength > buf.length)
			throw new RuntimeException("desiredStringLength > bufLength");
		int i = 0;
		while (i < desiredStringLength) {
			int x = nextInt();
//			p(x);
			buf[i++] = (char)((x & 0x1f) + 'a');
			buf[i++] = (char)(((x >> 8)  & 0x1f) + 'a');
			buf[i++] = (char)(((x >> 16) & 0x1f) + 'a');
			buf[i++] = (char)(((x >> 24) & 0x1f) + 'a');
		}
		if (i < desiredStringLength) {
			int x = nextInt();
			buf[i++] = (char)(((x) & 0x1f) + 'a');
			if (i < desiredStringLength) { 
				buf[i++] = (char)(((x >> 8) & 0x1f) + 'a');
				if (i < desiredStringLength) {
					buf[i++] = (char)(((x >> 16) & 0x1f) + 'a');
				}
			}
		}
		return new String(buf, 0, desiredStringLength);
	}

	private static void p(Object o) {
		System.out.println(o);
	}
}
