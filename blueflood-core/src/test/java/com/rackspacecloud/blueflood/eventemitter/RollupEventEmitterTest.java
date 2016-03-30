/*
 * Copyright 2013 Rackspace
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.rackspacecloud.blueflood.eventemitter;

import com.rackspacecloud.blueflood.concurrent.ThreadPoolBuilder;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class RollupEventEmitterTest {

    final String testEventName = "test";
    final String testEventName2 = "test2";

    List<RollupEvent> store;
    Emitter<RollupEvent> emitter;

    @Before
    public void setUp() {
        store = Collections.synchronizedList(new ArrayList<RollupEvent>());
        emitter = new Emitter<RollupEvent>();
    }

    @Test
    public void testSubscription() {
        EventListener elistener = new EventListener();
        //Test subscription
        emitter.on(testEventName, elistener);
        Assert.assertTrue(emitter.listeners(testEventName).contains(elistener));
    }

    @Test
    public void testConcurrentEmission() throws InterruptedException, ExecutionException {
        EventListener elistener = new EventListener();
        //Test subscription
        emitter.on(testEventName, elistener);
        //Test concurrent emission
        ThreadPoolExecutor executors = new ThreadPoolBuilder()
                .withCorePoolSize(2)
                .withMaxPoolSize(3)
                .build();
        final RollupEvent obj1 = new RollupEvent(null, null, "payload1", "gran", 0);
        final RollupEvent obj2 = new RollupEvent(null, null, "payload2", "gran", 0);
        final CountDownLatch startLatch = new CountDownLatch(1);
        Future<Object> f1 = executors.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                startLatch.await();
                emitter.emit(testEventName, obj1);
                return null;
            }
        });
        Future<Object> f2 = executors.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                startLatch.await();
                emitter.emit(testEventName, obj2);
                return null;
            }
        });
        Thread.sleep(1000);
        //Assert that store is empty before testing emission
        Assert.assertTrue(store.isEmpty());
        startLatch.countDown();
        f1.get();
        f2.get();
        Assert.assertEquals(store.size(), 2);
        Assert.assertTrue(store.contains(obj1));
        Assert.assertTrue(store.contains(obj2));
    }

    @Test
    public void testUnsubscription() {
        EventListener elistener = new EventListener();
        emitter.on(testEventName, elistener);
        //Test unsubscription
        emitter.off(testEventName, elistener);
        Assert.assertFalse(emitter.listeners(testEventName).contains(elistener));
        //Clear the store and check if it is not getting filled again
        store.clear();
        emitter.emit(testEventName, new RollupEvent(null, null, "payload3", "gran", 0));
        Assert.assertTrue(store.isEmpty());
    }

    @Test
    public void testOnce() {
        EventListener eventListener = new EventListener();
        //Test once
        emitter.once(testEventName, eventListener);
        emitter.emit(testEventName, new RollupEvent(null, null, "payload1", "gran", 0));
        Assert.assertEquals(store.size(), 1);
        store.clear();
        emitter.emit(testEventName, new RollupEvent(null, null, "payload1", "gran", 0));
        Assert.assertEquals(store.size(), 0);
    }

    @Test
    public void testOn() {

        // given
        final List<RollupEvent> events = new ArrayList<RollupEvent>();
        Emitter.Listener<RollupEvent> listener = new Emitter.Listener<RollupEvent>() {
            @Override
            public void call(RollupEvent... args) {
                Collections.addAll(events, args);
            }
        };

        RollupEvent event1 = new RollupEvent(null, null, "event1", "gran", 0);
        RollupEvent event2 = new RollupEvent(null, null, "event2", "gran", 0);

        emitter.on(testEventName, listener);
        Assert.assertEquals(0, events.size());

        // when
        emitter.emit(testEventName, event1);

        // then
        Assert.assertEquals(1, events.size());
        Assert.assertSame(event1, events.get(0));

        // when
        emitter.emit(testEventName, event2);

        // then
        Assert.assertEquals(2, events.size());
        Assert.assertSame(event1, events.get(0));
        Assert.assertSame(event2, events.get(1));
    }

    @Test
    public void offClearsRegularCallbacks() {

        // given
        Emitter.Listener<RollupEvent> listener = new EventListener();
        RollupEvent event1 = new RollupEvent(null, null, "event1", "gran", 0);
        emitter.on(testEventName, listener);

        // precondition
        Assert.assertEquals(0, store.size());

        // when
        emitter.off();
        emitter.emit(testEventName, event1);

        // then
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void offClearsOnceCallbacks() {

        // given
        Emitter.Listener<RollupEvent> listener = new EventListener();
        RollupEvent event1 = new RollupEvent(null, null, "event1", "gran", 0);
        emitter.once(testEventName, listener);

        // precondition
        Assert.assertEquals(0, store.size());

        // when
        emitter.off();
        emitter.emit(testEventName, event1);

        // then
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void emitOnlyTriggersForGivenEvent1() {

        // given
        Emitter.Listener<RollupEvent> listener = new EventListener();
        RollupEvent event1 = new RollupEvent(null, null, "event1", "gran", 0);
        RollupEvent event2 = new RollupEvent(null, null, "event2", "gran", 0);
        emitter.on(testEventName, listener);
        emitter.on(testEventName2, listener);

        // precondition
        Assert.assertEquals(0, store.size());

        // when
        emitter.emit(testEventName, event1);

        // then
        Assert.assertEquals(1, store.size());
        Assert.assertSame(event1, store.get(0));
    }

    @Test
    public void emitOnlyTriggersForGivenEvent2() {

        // given
        Emitter.Listener<RollupEvent> listener = new EventListener();
        RollupEvent event1 = new RollupEvent(null, null, "event1", "gran", 0);
        RollupEvent event2 = new RollupEvent(null, null, "event2", "gran", 0);
        emitter.on(testEventName, listener);
        emitter.on(testEventName2, listener);

        // precondition
        Assert.assertEquals(0, store.size());

        // when
        emitter.emit(testEventName2, event2);

        // then
        Assert.assertEquals(1, store.size());
        Assert.assertSame(event2, store.get(0));
    }

    @Test
    public void emitTriggersListenerInSameOrder1() {

        // given
        Emitter.Listener<RollupEvent> listener = new EventListener();
        RollupEvent event1 = new RollupEvent(null, null, "event1", "gran", 0);
        RollupEvent event2 = new RollupEvent(null, null, "event2", "gran", 0);
        emitter.on(testEventName, listener);
        emitter.on(testEventName2, listener);

        // precondition
        Assert.assertEquals(0, store.size());

        // when
        emitter.emit(testEventName, event1);
        emitter.emit(testEventName2, event2);

        // then
        Assert.assertEquals(2, store.size());
        Assert.assertSame(event1, store.get(0));
        Assert.assertSame(event2, store.get(1));
    }

    @Test
    public void emitTriggersListenerInSameOrder2() {

        // given
        Emitter.Listener<RollupEvent> listener = new EventListener();
        RollupEvent event1 = new RollupEvent(null, null, "event1", "gran", 0);
        RollupEvent event2 = new RollupEvent(null, null, "event2", "gran", 0);
        emitter.on(testEventName, listener);
        emitter.on(testEventName2, listener);

        // precondition
        Assert.assertEquals(0, store.size());

        // when
        emitter.emit(testEventName2, event2);
        emitter.emit(testEventName, event1);

        // then
        Assert.assertEquals(2, store.size());
        Assert.assertSame(event2, store.get(0));
        Assert.assertSame(event1, store.get(1));
    }

    @Test
    public void offOnlyClearsForGivenEvent() {

        // given
        Emitter.Listener<RollupEvent> listener = new EventListener();
        RollupEvent event1 = new RollupEvent(null, null, "event1", "gran", 0);
        RollupEvent event2 = new RollupEvent(null, null, "event2", "gran", 0);
        emitter.on(testEventName, listener);
        emitter.on(testEventName2, listener);

        // precondition
        Assert.assertEquals(0, store.size());

        // when
        emitter.off(testEventName);
        emitter.emit(testEventName, event1);
        emitter.emit(testEventName2, event2);

        // then
        Assert.assertEquals(1, store.size());
        Assert.assertSame(event2, store.get(0));

    }

    private class EventListener implements Emitter.Listener<RollupEvent> {
        @Override
        public void call(RollupEvent... rollupEventObjects) {
            store.addAll(Arrays.asList(rollupEventObjects));
        }
    }
}
