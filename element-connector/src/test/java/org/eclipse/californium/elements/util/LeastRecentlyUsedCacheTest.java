/*******************************************************************************
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Kai Hudalla (Bosch Software Innovations GmbH) - initial creation
 *    Achim Kraus (Bosch Software Innovations GmbH) - add test for find and
 *                                                    evict on access
 *    Achim Kraus (Bosch Software Innovations GmbH) - add test for iterator
 *                                                    and update last-access time
 *    Achim Kraus (Bosch Software Innovations GmbH) - use TimeAssume to relax failures
 *                                                    caused by delayed execution
 ******************************************************************************/
package org.eclipse.californium.elements.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.californium.elements.assume.TimeAssume;
import org.eclipse.californium.elements.rule.TestTimeRule;
import org.eclipse.californium.elements.util.LeastRecentlyUsedCache.EvictionListener;
import org.eclipse.californium.elements.util.LeastRecentlyUsedCache.Predicate;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies behavior of {@code LeastRecentlyUsedCache}.
 *
 */
public class LeastRecentlyUsedCacheTest {

	private static final long THRESHOLD_MILLIS = 300;

	@Rule
	public TestTimeRule time = new TestTimeRule();

	LeastRecentlyUsedCache<Integer, String> cache;

	@Test
	public void testGetFailsWhenExpired() throws InterruptedException {
		int capacity = 5;
		int numberOfSessions = 1;

		givenACacheWithEntries(capacity, THRESHOLD_MILLIS, numberOfSessions);
		cache.setEvictingOnReadAccess(true);
		String eldest = cache.getEldest();
		Integer key = Integer.valueOf(eldest);
		assertThat(cache.get(key), is(notNullValue()));
		time.setTestTimeShift(THRESHOLD_MILLIS + 100, TimeUnit.MILLISECONDS);
		assertThat(cache.get(key), is(nullValue()));
	}

	@Test
	public void testGetSucceedsEvenExpired() throws InterruptedException {
		int capacity = 5;
		int numberOfSessions = 1;

		givenACacheWithEntries(capacity, THRESHOLD_MILLIS, numberOfSessions);
		cache.setEvictingOnReadAccess(false);
		String eldest = cache.getEldest();
		Integer key = Integer.valueOf(eldest);
		assertThat(cache.get(key), is(notNullValue()));
		assertThat(cache.get(key), is(notNullValue()));
		time.setTestTimeShift(THRESHOLD_MILLIS + 100, TimeUnit.MILLISECONDS);
		assertThat(cache.get(key), is(notNullValue()));
	}

	@Test
	public void testUpdate() throws InterruptedException {
		int capacity = 5;
		int numberOfSessions = 1;
		TimeAssume assume = new TimeAssume(time);

		givenACacheWithEntries(capacity, THRESHOLD_MILLIS, numberOfSessions);
		cache.setEvictingOnReadAccess(true);
		cache.setUpdatingOnReadAccess(false);
		String eldest = cache.getEldest();
		Integer key = Integer.valueOf(eldest);
		time.setFixedTestTime(true);
		assume.sleep(THRESHOLD_MILLIS / 2);
		assertThat(cache.get(key), assume.inTime(is(notNullValue())));
		// update last-access time
		assertThat(cache.update(key), assume.inTime(is(true)));
		assume.sleep((THRESHOLD_MILLIS / 2) + 50);
		// not expired
		assertThat(cache.get(key), assume.inTime(is(notNullValue())));
		assertThat(cache.update(key), assume.inTime(is(true)));
		assume.sleep(THRESHOLD_MILLIS / 2);
		// no update last-access time
		assertThat(cache.get(key), assume.inTime(is(notNullValue())));
		assume.sleep((THRESHOLD_MILLIS / 2) + 50);
		// expired!
		assertThat(cache.get(key), assume.inTime(is(nullValue())));
	}

	@Test
	public void testRemoveExpiredEntriesWithLimit() throws InterruptedException {
		int capacity = 10;
		int numberOfSessions = 10;
		TimeAssume assume = new TimeAssume(time);

		givenACacheWithEntries(capacity, THRESHOLD_MILLIS, numberOfSessions);
		cache.setEvictingOnReadAccess(false);
		cache.setUpdatingOnReadAccess(true);
		time.setFixedTestTime(true);
		assume.sleep(THRESHOLD_MILLIS / 2);
		// update some entries
		assertThat(cache.get(2), assume.inTime(is(notNullValue())));
		assertThat(cache.get(8), assume.inTime(is(notNullValue())));
		assertThat(cache.get(5), assume.inTime(is(notNullValue())));

		// expire not updated entries
		assume.sleep((THRESHOLD_MILLIS / 2) + 50);
		// remove with limit
		assertThat(cache.removeExpiredEntries(3), is(3));
		// remove with exceeded limit
		assertThat(cache.removeExpiredEntries(10), is(4));
		// remove without expired entries
		assertThat(cache.removeExpiredEntries(1), is(0));
		// expires all
		assume.sleep((THRESHOLD_MILLIS / 2) + 50);
		// remove with exceeded limit
		assertThat(cache.removeExpiredEntries(10), is(3));
		// remove without expired entries
		assertThat(cache.removeExpiredEntries(1), is(0));
	}

	@Test
	public void testRemoveExpiredEntriesWithoutLimit() throws InterruptedException {
		int capacity = 10;
		int numberOfSessions = 10;
		TimeAssume assume = new TimeAssume(time);

		givenACacheWithEntries(capacity, THRESHOLD_MILLIS, numberOfSessions);
		cache.setEvictingOnReadAccess(false);
		cache.setUpdatingOnReadAccess(true);
		time.setFixedTestTime(true);
		assume.sleep(THRESHOLD_MILLIS / 2);
		// update some entries
		assertThat(cache.get(2), assume.inTime(is(notNullValue())));
		assertThat(cache.get(8), assume.inTime(is(notNullValue())));
		assertThat(cache.get(5), assume.inTime(is(notNullValue())));

		// expire not updated entries
		assume.sleep((THRESHOLD_MILLIS / 2) + 50);
		// remove all
		assertThat(cache.removeExpiredEntries(0), is(7));
		// remove without expired entries
		assertThat(cache.removeExpiredEntries(0), is(0));
		// expires all
		assume.sleep((THRESHOLD_MILLIS / 2) + 50);
		// remove all
		assertThat(cache.removeExpiredEntries(0), is(3));
		// remove without expired entries
		assertThat(cache.removeExpiredEntries(0), is(0));
	}

	@Test
	public void testIteratorWhenExpired() throws InterruptedException {
		int capacity = 5;
		int numberOfSessions = 5;
		TimeAssume assume = new TimeAssume(time);

		givenACacheWithEntries(capacity, THRESHOLD_MILLIS, numberOfSessions);
		cache.setEvictingOnReadAccess(true);
		time.setFixedTestTime(true);
		assume.sleep(THRESHOLD_MILLIS / 2);
		Iterator<String> valuesIterator = cache.valuesIterator();
		cache.setUpdatingOnReadAccess(true);
		assertNext(valuesIterator, assume);
		cache.setUpdatingOnReadAccess(false);
		assertNext(valuesIterator, assume);
		cache.setUpdatingOnReadAccess(true);
		assertNext(valuesIterator, assume);
		cache.setUpdatingOnReadAccess(false);
		assertNext(valuesIterator, assume);
		cache.setUpdatingOnReadAccess(true);
		assertNext(valuesIterator, assume);
		assume.sleep((THRESHOLD_MILLIS / 2) + 20, 100);

		valuesIterator = cache.valuesIterator();
		assertNext(valuesIterator, assume);
		assertNext(valuesIterator, assume);
		assertNext(valuesIterator, assume);
		assertFalse(valuesIterator.hasNext());
	}

	@Test
	public void testIteratorOnRemove() throws InterruptedException {
		int capacity = 5;
		int numberOfSessions = 5;

		givenACacheWithEntries(capacity, THRESHOLD_MILLIS, numberOfSessions);
		cache.setEvictingOnReadAccess(false);
		Iterator<String> valuesIterator = cache.valuesIterator();
		cache.remove(2);
		cache.remove(4);
		assertThat(valuesIterator.next(), is(notNullValue()));
		assertThat(valuesIterator.next(), is(notNullValue()));
		assertThat(valuesIterator.next(), is(notNullValue()));
		assertThat(valuesIterator.hasNext(), is(false));
	}

	private void assertNext(Iterator<String> iterator, TimeAssume assume) {
		String value = iterator.hasNext() ? iterator.next() : null;
		assertThat(value, assume.inTime(is(notNullValue())));
	}

	@Test
	public void testIteratorTimestamped() throws InterruptedException {
		int capacity = 5;
		int numberOfSessions = 5;

		LeastRecentlyUsedCache<Integer, String> clone = new LeastRecentlyUsedCache<>(3, 0);

		givenACacheWithEntries(capacity, THRESHOLD_MILLIS, numberOfSessions);
		cache.setEvictingOnReadAccess(false);
		cache.remove(2);
		Iterator<LeastRecentlyUsedCache.Timestamped<String>> valuesIterator = cache.timestampedIterator();
		LeastRecentlyUsedCache.Timestamped<String> timestamped = valuesIterator.next();
		LeastRecentlyUsedCache.Timestamped<String> first = timestamped;
		int id = capacity + 1;
		assertThat(timestamped, is(notNullValue()));
		long time1 = timestamped.getLastUpdate();
		assertTrue(clone.put(id++, timestamped));
		timestamped = valuesIterator.next();
		assertThat(timestamped, is(notNullValue()));
		long time2 = timestamped.getLastUpdate();
		assertThat(time1, is(lessThan(time2)));
		time1 = time2;
		assertTrue(clone.put(id++, timestamped));
		timestamped = valuesIterator.next();
		assertThat(timestamped, is(notNullValue()));
		time2 = timestamped.getLastUpdate();
		assertThat(time1, is(lessThan(time2)));
		assertTrue(clone.put(id++, timestamped));
		timestamped = valuesIterator.next();
		assertThat(timestamped, is(notNullValue()));
		time2 = timestamped.getLastUpdate();
		assertThat(time1, is(lessThanOrEqualTo(time2)));

		assertThat(clone.size(), is(3));

		// fail for time
		assertFalse(clone.put(id, first));
		// succeed with time
		assertTrue(clone.put(id, timestamped));

		assertThat(clone.size(), is(3));

		assertThat(valuesIterator.hasNext(), is(false));
	}

	@Test
	public void testFindUniqueFailsWhenExpired() throws InterruptedException {
		int capacity = 5;
		int numberOfSessions = 3;

		givenACacheWithEntries(capacity, THRESHOLD_MILLIS, numberOfSessions);
		cache.setEvictingOnReadAccess(true);
		final String eldest = cache.getEldest();
		Predicate<String> predicate = new Predicate<String>() {

			@Override
			public boolean accept(String value) {
				return eldest.equals(value);
			}

		};
		assertThat(cache.find(predicate), is(notNullValue()));
		time.setTestTimeShift(THRESHOLD_MILLIS + 100, TimeUnit.MILLISECONDS);
		assertThat(cache.find(predicate), is(nullValue()));
	}

	@Test
	public void testFindUniqueSucceedsEvenExpired() throws InterruptedException {
		int capacity = 5;
		int numberOfSessions = 3;

		givenACacheWithEntries(capacity, THRESHOLD_MILLIS, numberOfSessions);
		cache.setEvictingOnReadAccess(false);
		final String eldest = cache.getEldest();
		Predicate<String> predicate = new Predicate<String>() {

			@Override
			public boolean accept(String value) {
				return eldest.equals(value);
			}

		};
		assertThat(cache.find(predicate), is(notNullValue()));
		time.setTestTimeShift(THRESHOLD_MILLIS + 100, TimeUnit.MILLISECONDS);
		assertThat(cache.find(predicate), is(notNullValue()));
	}

	@Test
	public void testFindNoneUniqueSucceedsEvenFirstEvicted() throws InterruptedException {
		int capacity = 5;
		int numberOfSessions = 3;
		TimeAssume assume = new TimeAssume(time);

		givenACacheWithEntries(capacity, THRESHOLD_MILLIS, numberOfSessions);
		cache.setEvictingOnReadAccess(true);
		time.setFixedTestTime(true);

		assume.sleep(THRESHOLD_MILLIS / 2);

		// skip 1., update 2. by read
		SkipFirsts predicate = new SkipFirsts(1);
		String value;
		assertThat((value = cache.find(predicate, false)), assume.inTime(is(notNullValue())));

		// expires 1.
		assume.sleep((THRESHOLD_MILLIS / 2) + 25);

		EvictionCounter counter = new EvictionCounter();
		cache.addEvictionListener(counter);
		// evict 1., select the 2.
		predicate = new SkipFirsts(0);
		assertThat(cache.find(predicate, false), assume.inTime(is(value)));
		assertThat(counter.count, is(1));
	}

	@Test
	public void testStoreAddsNewValueIfCapacityNotReached() {
		int capacity = 10;

		givenACacheWithEntries(capacity, 0L, capacity - 1);
		assertThat(cache.remainingCapacity(), is(1));
		String eldest = cache.getEldest();

		String newValue = "50";
		assertTrue(cache.put(50, newValue));
		assertThat(cache.get(Integer.valueOf(eldest)), is(notNullValue()));
		assertThat(cache.remainingCapacity(), is(0));
	}

	@Test
	public void testStoreEvictsEldestStaleEntry() {
		int capacity = 10;

		givenACacheWithEntries(capacity, 0L, capacity);
		assertThat(cache.remainingCapacity(), is(0));
		String eldest = cache.getEldest();

		String newValue = "50";
		assertTrue(cache.put(Integer.valueOf(newValue), newValue));
		assertThat(cache.get(Integer.valueOf(eldest)), is(nullValue()));
	}

	@Test
	public void testStoreFailsIfCapacityReached() {
		int capacity = 10;
		int numberOfSessions = 10;

		givenACacheWithEntries(capacity, THRESHOLD_MILLIS * 100, numberOfSessions);
		assertThat(cache.remainingCapacity(), is(0));
		String eldest = cache.getEldest();

		String newValue = "50";
		Integer key = Integer.valueOf(newValue);
		assertFalse(cache.put(key, newValue));
		assertThat(cache.get(key), is(nullValue()));
		assertThat(cache.get(Integer.valueOf(eldest)), is(notNullValue()));
	}

	@Test
	public void testContinuousEviction() {
		int capacity = 10;

		givenACacheWithEntries(capacity, 0L, 0);
		assertThat(cache.remainingCapacity(), is(capacity));
		final AtomicInteger evicted = new AtomicInteger(0);

		cache.addEvictionListener(new EvictionListener<String>() {

			@Override
			public void onEviction(String evictedSession) {
				evicted.incrementAndGet();
			}
		});

		int noOfSessions = 1000;
		for (int i = 0; i < noOfSessions; i++) {
			Integer key = i + 1000;
			String value = String.valueOf(key);
			assertTrue(cache.put(key, value));
		}
		assertThat(evicted.get(), is(noOfSessions - capacity));
		assertThat(cache.remainingCapacity(), is(0));
	}

	/**
	 * 
	 * @param capacity
	 * @param expirationThresholdMillis
	 * @param noOfEntries
	 */
	private void givenACacheWithEntries(int capacity, long expirationThresholdMillis, int noOfEntries) {
		cache = new LeastRecentlyUsedCache<>(capacity, capacity, expirationThresholdMillis, TimeUnit.MILLISECONDS);
		for (int i = 0; i < noOfEntries; i++) {
			cache.put(i, Integer.toString(i));
		}
	}

	private static class SkipFirsts implements Predicate<String> {

		private int skipCount;

		private SkipFirsts(int skipCount) {
			this.skipCount = skipCount;
		}

		@Override
		public boolean accept(String value) {
			return skipCount-- <= 0;
		}
	};

	private static class EvictionCounter implements EvictionListener<String> {

		private int count;

		@Override
		public void onEviction(String value) {
			++count;
		}
	};

}
