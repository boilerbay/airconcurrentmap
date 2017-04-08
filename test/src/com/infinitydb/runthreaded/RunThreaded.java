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

package com.infinitydb.runthreaded;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import com.infinitydb.random.FastRandom;

/**
 * Wrap most of the work of managing a thread pool. The subclass can override
 * workerRun() to do the actual job. This handles many subtle issues, like
 * getting a true random seed, having a different random generator for each
 * Worker thread so they do not need to be thread safe, making sure the random
 * generator is fast and can do both Longs and Strings, waiting for all of the
 * threads to be created before returning from start() etc.
 * 
 * @author Roger Deran
 */
public abstract class RunThreaded {
	final String name;
	final ArrayList<Worker> workers = new ArrayList<Worker>();
	// After a start(), we block until all the threads are running
	final AtomicInteger runningCount = new AtomicInteger();
	// It doesn't work to use the time as a seed!
	// Even nanoTime() is suspicious. This works for sure.
	final SecureRandom seedRandom = new SecureRandom();
	// Whether to ignore seedRandom and use only thread and a given seed
	final boolean isUsingSeedRandom;
	// A RunThreadedGroup acts like a single RunThreaded.
	final RunThreadedGroup runThreadedGroup;
	// Set by Worker when a Throwable is caught from workerRun().
	Throwable throwable;

	public RunThreaded(int nThreads, String name) {
		this(nThreads, name, null, true);
	}

	public RunThreaded(int nThreads, String name, boolean isUsingSeedRandom) {
		this(nThreads, name, null, isUsingSeedRandom);
	}

	public RunThreaded(int nThreads, String name, RunThreadedGroup runThreadedGroup) {
		this(nThreads, name, runThreadedGroup, true);
	}

	public RunThreaded(int nThreads, String name,
			RunThreadedGroup runThreadedGroup, boolean isUsingSeedRandom) {
		this.name = name;
		this.runThreadedGroup = runThreadedGroup;
		this.isUsingSeedRandom = isUsingSeedRandom;
		if (runThreadedGroup != null)
			runThreadedGroup.add(this);
		for (int i = 0; i < nThreads; i++) {
			Worker worker = new Worker(i);
			workers.add(worker);
			// Allow the main thread to execute reliably at a higher priority
			worker.setPriority(Thread.MIN_PRIORITY);
		}
	}

	public void setDaemon(boolean isDaemon) {
		for (Worker worker : workers)
			worker.setDaemon(isDaemon);
	}
	
	public Throwable getThrowable() {
		return throwable;
	}

	public void start() {
		for (Worker worker : workers) {
			worker.start();
			worker.setRunnable(true);
		}
	}

	// Start them all together to avoid the different delays of Thread
	// creation.
	public void startSuspended() {
		for (Worker worker : workers) {
			worker.startSuspended();
		}
	}

	// trigger them all only after startSuspended to keep them together
	public void setRunnable(boolean isRunnable) {
		for (Worker worker : workers) {
			worker.setRunnable(isRunnable);
		}
	}

	public void waitForRunnable() {
		for (Worker worker : workers) {
			worker.waitForRunnable();
		}
	}

	/*
	 * Implement this in a subclass and construct it and start() it, then sleep
	 * or do other work. If the workerRun is long-running, and it is desired to
	 * make it suspendable, then workerRun() should occasionally invoke the fast
	 * checkForSuspend() and if true, then invoke waitForRunnable() and then
	 * continue. (maybe that should actually loop instead of just testing once.)
	 */
	public abstract void workerRun(Worker worker);

	// Invoked by workers. May be overridden
	public void onThrowable(Throwable throwable) {
		if (runThreadedGroup != null) {
			runThreadedGroup.onThrowable(throwable);
		} else {
			throwable.printStackTrace();
			// Don't stop(), or it will deadlock
			quit();
		}
	}

	public void quit() {
		for (Worker worker : workers) {
			worker.isQuitting = true;
		}
 	}

	/*
	 * RunThreaded subclasses' workerRun(Worker worker) should not use this but
	 * instead the worker.isQuitting() on the worker that is passed into
	 * workerRun()
	 */
	// public boolean isQuitting() {
	// for (Worker worker : workers) {
	// if (worker.isQuitting)
	// return true;
	// }
	// return false;
	// }

	public synchronized void waitForAllWorkersRunning() {
		while (runningCount.get() < workers.size()) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
	}

	public synchronized void waitForNoWorkersRunning() {
		while (runningCount.get() > 0) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
	}

	public void suspend() {
		for (Worker worker : workers) {
			worker.setRunnable(false);
			runningCount.decrementAndGet();
		}
	}

	public void resume() {
		for (Worker worker : workers) {
			worker.setRunnable(true);
			runningCount.incrementAndGet();
		}
	}

	/*
	 * The workerRun()'s all observe isQuitting(), and return if true. The
	 * isQuitting flag is a visible volatile field of RunThreaded so that it can
	 * be tested directly, for speed. However, the fact that it is shared by all
	 * of the workers may make a ping-pong effect that will reduce core
	 * concurrency. So, the workerRun's will put the isQuitting() test inside
	 * the counter bump, which happens only about 1% of the time.
	 * 
	 * TODO check above.
	 */
	public void stop() {
		quit();
		join();
	}

	public void join() {
		for (Worker worker : workers) {
			try {
				worker.join();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public class Worker extends Thread {
		private final int threadNumber;
		private final FastRandom fastRandom;
		private final Random random;
		private volatile boolean isQuitting;
		private volatile boolean isRunnable;
		private volatile Throwable throwable;

		private Worker(int threadNumber) {
			this.threadNumber = threadNumber;
			// this is not reliable
			// long seed = System.nanoTime();
			// This always works for a random seed, even when
			// called rapidly in sequence.
			long seed = isUsingSeedRandom ? seedRandom.nextLong() : 0;
			fastRandom = new FastRandom(threadNumber + 1 + seed, 20);
			random = new Random(threadNumber + 1 + seed);
			// System.out.println("worker " + name + " " + threadNumber +
			// " created");
			setName(name + "-" + threadNumber);
		}

		public int getThreadNumber() {
			return threadNumber;
		}

		public void start() {
			super.start();
			setRunnable(true);
		}

		public void startSuspended() {
			super.start();
		}

		public void quit() {
			isQuitting = true;
		}

		public boolean isQuitting() {
			return isQuitting;
		}

		/*
		 * The subclass may invoke this fast method occasionally in its inner
		 * loop of workerRun(), and on a true result, the subclass invokes
		 * Context.waitForRunnable(). If the client invokes
		 * RunThreads.suspend(), all of the workers will suspend, then resume
		 * when RunThreads.resume() is invoked. This method should be easily
		 * inlined. There is a slight race.
		 */
		public boolean checkForSuspend() {
			return !isRunnable;
		}

		public FastRandom getFastRandom() {
			return fastRandom;
		}

		public Random getRandom() {
			return random;
		}
		
		public void run() {
			// Wait until all of the threads are created and ready
			// before actually beginning. This handles slow Thread creation.
			incrementRunningCount();
			waitForRunnable();
			try {

				// This is the core of it: the RunThreaded subclass implements
				// workerRun(), which is the abstract RunThreaded.workerRun().
				workerRun(this);

			} catch (Throwable throwable) {
				this.throwable = throwable;
				// This overwrites any previous throwable caught
				// by another thread, unfortunately.
				RunThreaded.this.throwable = throwable;
				// This is the RunThreaded method
				// which may be overridden
				onThrowable(throwable);
			}
		}

		private synchronized void setRunnable(boolean isRunnable) {
			// System.out.println("setRunnable: isRunnable=" + isRunnable +
			// " this.isRunnable=" + this.isRunnable +
			// " runnigCount=" + runningCount);
			// if (this.isRunnable && !isRunnable)
			// runningCount.decrementAndGet();
			// else if (!this.isRunnable && isRunnable)
			// runningCount.incrementAndGet();
			this.isRunnable = isRunnable;
			notify();
		}

		// Subclass invokes this if it notices that
		// isRunnable has become false.
		public synchronized void waitForRunnable() {
			while (!isRunnable) {
				try {
					wait();
				} catch (InterruptedException e) {
				}
			}
		}

		private synchronized void waitForNotRunnable() {
			while (isRunnable) {
				try {
					wait();
				} catch (InterruptedException e) {
				}
			}
		}

		private void incrementRunningCount() {
			runningCount.incrementAndGet();
			synchronized (RunThreaded.this) {
				RunThreaded.this.notify();
			}
		}

		private void decrementRunningCount() {
			runningCount.decrementAndGet();
			synchronized (RunThreaded.this) {
				RunThreaded.this.notify();
			}
		}
	}

	static synchronized void p(Object o) {
		System.out.println(Thread.currentThread() + " " + o);
	}
}
