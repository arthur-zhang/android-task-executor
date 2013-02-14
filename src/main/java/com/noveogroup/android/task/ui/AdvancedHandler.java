/*
 * Copyright (c) 2013 Noveo Group
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Except as contained in this notice, the name(s) of the above copyright holders
 * shall not be used in advertising or otherwise to promote the sale, use or
 * other dealings in this Software without prior written authorization.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.noveogroup.android.task.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.Set;

/**
 * An AdvancedHandler allows you to process Runnable objects using usual Handler.
 * The main features of AdvancedHandler are: joins and sub-handlers.
 * <p/>
 * Scheduling callbacks is accomplished with the {@link #post(Runnable)}, {@link #postDelayed(Runnable, long)},
 * {@link #postSingleton(Runnable)}, {@link #postDelayedSingleton(Runnable, long)} and
 * {@link #postSyncSingleton(Runnable)} methods.
 * <p/>
 * Joining callbacks can cause a blocking. To ensure all threads will be resumed call {@link #removeCallbacks()}
 * when handler is no longer needed.
 */
public class AdvancedHandler {

    private static class WaitCallback implements Runnable {

        private final Object lock = new Object();
        private boolean finished = false;

        protected void runCallback() {
        }

        @Override
        public final void run() {
            try {
                runCallback();
            } finally {
                release();
            }
        }

        public final void join() throws InterruptedException {
            synchronized (lock) {
                while (!finished) {
                    lock.wait();
                }
            }
        }

        public final void release() {
            synchronized (lock) {
                finished = true;
                lock.notifyAll();
            }
        }

    }

    private final Object handlerLock;
    private final Handler handler;
    private final AssociationSet<WaitCallback> associationSet;
    private final AdvancedHandler parent;
    private final Object token;

    /**
     * Default constructor associates this handler with the queue for the current thread.
     * If there isn't one, this handler won't be able to receive messages.
     */
    public AdvancedHandler() {
        this(new Handler());
    }

    /**
     * Uses main looper of the context to initialize the handler.
     */
    public AdvancedHandler(Context context) {
        this(context.getMainLooper());
    }

    /**
     * Use the provided queue instead of the default one.
     *
     * @param looper the custom queue.
     */
    public AdvancedHandler(Looper looper) {
        this(new Handler(looper));
    }

    /**
     * Use the specified handler to delegate callbacks to.
     *
     * @param handler the delegate.
     */
    public AdvancedHandler(Handler handler) {
        this(new Object(), handler, new AssociationSet<WaitCallback>(), null, null);
    }

    private AdvancedHandler(Object handlerLock, Handler handler, AssociationSet<WaitCallback> associationSet, AdvancedHandler parent, Object token) {
        this.handlerLock = handlerLock;
        this.handler = handler;
        this.associationSet = associationSet;
        this.parent = parent;
        this.token = token;
    }

    /**
     * Returns sub-handler associated with the specified token.
     * The callbacks of the sub-handler will be owned by parent handler too.
     *
     * @param token the token object.
     * @return the sub-handler.
     */
    public AdvancedHandler sub(Object token) {
        return new AdvancedHandler(handlerLock, handler, associationSet, this, new Pair<AdvancedHandler, Object>(this, token));
    }

    private WaitCallback createWaitCallback(final Runnable callback) {
        return new WaitCallback() {
            @Override
            protected void runCallback() {
                try {
                    callback.run();
                } finally {
                    synchronized (handlerLock) {
                        associationSet.remove(this);
                    }
                }
            }
        };
    }

    private void checkJoinAbility() {
        if (Thread.currentThread() == handler.getLooper().getThread()) {
            throw new RuntimeException("current thread blocks the callback");
        }
    }

    private <K> void joinAssociated(K key) throws InterruptedException {
        checkJoinAbility();
        Set<WaitCallback> waitCallbacks;
        synchronized (handlerLock) {
            waitCallbacks = associationSet.getAssociated(key);
        }
        for (WaitCallback waitCallback : waitCallbacks) {
            waitCallback.join();
        }
    }

    private <K> void removeAssociated(K key) {
        Set<WaitCallback> waitCallbacks;
        synchronized (handlerLock) {
            waitCallbacks = associationSet.getAssociated(key);
        }
        for (WaitCallback waitCallback : waitCallbacks) {
            waitCallback.release();
            synchronized (handlerLock) {
                associationSet.remove(waitCallback);
            }
        }
    }

    private void associateCallback(Runnable callback, WaitCallback waitCallback) {
        synchronized (handlerLock) {
            associationSet.add(waitCallback);
            for (AdvancedHandler ah = this; ah != null; ah = ah.parent) {
                associationSet.associate(waitCallback, ah.token);
                associationSet.associate(waitCallback, new Pair<Object, Runnable>(ah.token, callback));
            }
        }
    }

    /**
     * Causes the callback to be added to the queue.
     *
     * @param callback the callback that will be executed.
     */
    public void post(Runnable callback) {
        synchronized (handlerLock) {
            WaitCallback waitCallback = createWaitCallback(callback);
            if (handler.post(waitCallback)) {
                associateCallback(callback, waitCallback);
            }
        }
    }

    /**
     * Causes the callback to be added to the queue.
     *
     * @param callback the callback that will be executed.
     * @param delay    the delay (in milliseconds) until the callback will be executed.
     */
    public void postDelayed(Runnable callback, long delay) {
        synchronized (handlerLock) {
            WaitCallback waitCallback = createWaitCallback(callback);
            if (handler.postDelayed(waitCallback, delay)) {
                associateCallback(callback, waitCallback);
            }
        }
    }

    /**
     * Similar to {@link #post(Runnable)} but removes callbacks first before adding to queue.
     *
     * @param callback the callback that will be executed.
     */
    public void postSingleton(Runnable callback) {
        removeCallbacks(callback);
        post(callback);
    }

    /**
     * Similar to {@link #postDelayed(Runnable, long)} but removes callbacks first before adding to queue.
     *
     * @param callback the callback that will be executed.
     * @param delay    the delay (in milliseconds) until the callback will be executed.
     */
    public void postDelayedSingleton(Runnable callback, long delay) {
        removeCallbacks(callback);
        postDelayed(callback, delay);
    }

    /**
     * Similar to {@link #postSingleton(Runnable)} but joins the callbacks after adding to queue.
     *
     * @param callback the callback that will be executed.
     */
    public void postSyncSingleton(Runnable callback) throws InterruptedException {
        checkJoinAbility();

        removeCallbacks(callback);
        post(callback);
        joinCallbacks(callback);
    }

    /**
     * Joins the specified callback. If the callback will be removed before finish
     * this method successfully ends.
     *
     * @param callback the callback to join to.
     * @throws InterruptedException if any thread interrupted the current thread.
     */
    public void joinCallbacks(Runnable callback) throws InterruptedException {
        joinAssociated(new Pair<Object, Runnable>(token, callback));
    }

    /**
     * Joins all callbacks of the handler. If the callbacks will be removed before finish
     * this method successfully ends.
     *
     * @throws InterruptedException if any thread interrupted the current thread.
     */
    public void joinCallbacks() throws InterruptedException {
        joinAssociated(token);
    }

    /**
     * Removes any pending posts of the specified callback that are in the message queue.
     * All waiting threads joining to this callback will be notified and resumed.
     *
     * @param callback the callback to remove.
     */
    public void removeCallbacks(Runnable callback) {
        removeAssociated(new Pair<Object, Runnable>(token, callback));
    }

    /**
     * Removes any callbacks from the queue.
     * All waiting threads joining to callbacks of this handler will be notified and resumed.
     */
    public void removeCallbacks() {
        removeAssociated(token);
    }

}