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

import java.util.ArrayList;

/**
 * } This acts exactly like a RunThreaded, but it delegates to a set of
 * RunThreadeds, allowing them to be controlled as a group. You createa one
 * RunThreaded at a time, and add it to one of these groups. The individual
 * RunThreadeds override abstract {@link RunThreaded#workerRun} and
 * {@link RunThreaded#onThrowable}.
 * 
 * @see RunThreaded
 * 
 * @author Roger
 */
public class RunThreadedGroup {
	ArrayList<RunThreaded> runThreadeds = new ArrayList<RunThreaded>();
	// Set by RunThreaded.Worker.onThrowable
	Throwable throwable;

	public RunThreadedGroup(RunThreaded... runThreadeds) {
		for (RunThreaded runThreaded : runThreadeds)
			add(runThreaded);
	}
	
	// Invoked by RunThreaded constructor if
	// RunThreadedGroup parameter not null
	public void add(RunThreaded runThreaded) {
		runThreadeds.add(runThreaded);
	}

	// invoked by RunThreaded if a RunThreadedGroup was given
	// in the constructor of RunThreaded
	public void onThrowable(Throwable throwable) {
		throwable.printStackTrace();
		this.throwable = throwable;
		// don't stop() or it will deadlock
		quit();
	}

	public Throwable getThrowable() {
		return throwable;
	}

	public synchronized void start() {
		// Start them all together to avoid the different delays of Thread
		// creation.
		startSuspended();
		setRunnable(true);
	}

	public void setDaemon(boolean isDaemon) {
		for (RunThreaded runThreaded : runThreadeds) {
			runThreaded.setDaemon(isDaemon);
		}
	}
	
	// Start them all together to avoid the different delays of Thread
	// creation.
	public synchronized void startSuspended() {
		for (RunThreaded runThreaded : runThreadeds) {
			runThreaded.startSuspended();
		}
	}

	public void setRunnable(boolean isRunnable) {
		for (RunThreaded runThreaded : runThreadeds) {
			runThreaded.setRunnable(isRunnable);
		}
	}

	public void stop() {
		for (RunThreaded runThreaded : runThreadeds) {
			runThreaded.stop();
		}
	}

	public void quit() {
		for (RunThreaded runThreaded : runThreadeds) {
			runThreaded.quit();
		}
	}

	public void waitForAllWorkersRunning() {
		for (RunThreaded runThreaded : runThreadeds) {
			runThreaded.waitForAllWorkersRunning();
		}
	}

	public void waitForNoWorkersRunning() {
		for (RunThreaded runThreaded : runThreadeds) {
			runThreaded.waitForNoWorkersRunning();
		}
	}

	public void suspend() {
		for (RunThreaded runThreaded : runThreadeds) {
			runThreaded.suspend();
		}
	}

	public void resume() {
		for (RunThreaded runThreaded : runThreadeds) {
			runThreaded.resume();
		}
	}

	public void join() {
		for (RunThreaded runThreaded : runThreadeds) {
			runThreaded.join();
		}
	}
}
