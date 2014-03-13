/*
 * The MIT License (MIT)
 * 
 * Copyright (c) 2014 Jeff Nelson, Cinchapi Software Collective
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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.cinchapi.concourse;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.concurrent.ThreadSafe;

import org.cinchapi.concourse.config.ConcourseClientPreferences;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

/**
 * <p>
 * A {@link ConnectionPool} manages multiple concurrent connections to Concourse
 * for a single user. Generally speaking, a ConnectionPool is handy in long
 * running server API endpoints that want to reduce the overhead of creating a
 * new client connection for every asynchronous request. Using a ConnectionPool,
 * those applications can maintain a finite number of connections while ensuring
 * the resources are disconnected gracefully when necessary.
 * </p>
 * <h2>Usage</h2>
 * 
 * <pre>
 * Concourse concourse = pool.request();
 * try {
 *  ...
 * }
 * finally {
 *  pool.release();
 * }
 * ...
 * // All the threads with connections are done
 * pool.shutdown()
 * </pre>
 * 
 * @author jnelson
 */
@ThreadSafe
public abstract class ConnectionPool implements AutoCloseable {

    // NOTE: This class does not define #hashCode or #equals because the
    // defaults are the desired behaviour

    /**
     * Return a new {@link ConnectionPool} that provides connections to the
     * Concourse instance defined in the client {@code prefs} on behalf of the
     * user defined in the client {@code prefs}.
     * 
     * @param prefs
     * @return the ConnectionPool
     * @deprecated Use {@link #newFixedConnectionPool(String, int)} instead.
     */
    @Deprecated
    public static ConnectionPool newConnectionPool(String prefs) {
        return newFixedConnectionPool(prefs, DEFAULT_POOL_SIZE);
    }

    /**
     * Return a new {@link ConnectionPool} that provides {@code poolSize}
     * connections to the Concourse instance defined in the client {@code prefs}
     * on behalf of the user defined in the client {@code prefs}.
     * 
     * @param prefs
     * @param poolSize
     * @return the ConnectionPool
     * @deprecated Use {@link #newFixedConnectionPool(String, int)} instead.
     */
    @Deprecated
    public static ConnectionPool newConnectionPool(String prefs, int poolSize) {
        return newFixedConnectionPool(prefs, poolSize);
    }

    /**
     * Return a new {@link ConnectionPool} that provides connections to the
     * Concourse instance at {@code host}:{@code port} on behalf of the user
     * identified by {@code username} and {@code password}.
     * 
     * @param host
     * @param port
     * @param username
     * @param password
     * @return the ConnectionPool
     * @deprecated Use
     *             {@link #newFixedConnectionPool(String, int, String, String, int)}
     *             instead.
     */
    @Deprecated
    public static ConnectionPool newConnectionPool(String host, int port,
            String username, String password) {
        return newFixedConnectionPool(host, port, username, password,
                DEFAULT_POOL_SIZE);
    }

    /**
     * Return a new {@link ConnectionPool} that provides {@code poolSize}
     * connections to the Concourse instance at {@code host}:{@code port} on
     * behalf of the user identified by {@code username} and {@code password}.
     * 
     * @param host
     * @param port
     * @param username
     * @param password
     * @param poolSize
     * @return the ConnectionPool
     * @deprecated Use
     *             {@link #newFixedConnectionPool(String, int, String, String, int)}
     *             instead.
     */
    @Deprecated
    public static ConnectionPool newConnectionPool(String host, int port,
            String username, String password, int poolSize) {
        return newFixedConnectionPool(host, port, username, password, poolSize);
    }

    /**
     * Return a new {@link ConnectionPool} with a fixed number of
     * connections to the Concourse instance defined in the client {@code prefs}
     * on behalf of the user defined in the client {@code prefs}.
     * <p>
     * If all the connections from the pool are active, subsequent request
     * attempts will block until a connection is returned.
     * </p>
     * 
     * @param prefs
     * @param poolSize
     * @return the ConnectionPool
     */
    public static ConnectionPool newFixedConnectionPool(String prefs,
            int poolSize) {
        ConcourseClientPreferences cp = ConcourseClientPreferences.load(prefs);
        return new FixedConnectionPool(cp.getHost(), cp.getPort(),
                cp.getUsername(), new String(cp.getPassword()), poolSize);
    }

    /**
     * Return a new {@link ConnectionPool} with a fixed number of connections to
     * the Concourse instance at {@code host}:{@code port} on behalf of the user
     * identified by {@code username} and {@code password}.
     * 
     * <p>
     * If all the connections from the pool are active, subsequent request
     * attempts will block until a connection is returned.
     * </p>
     * 
     * @param host
     * @param port
     * @param username
     * @param password
     * @return the ConnectionPool
     */
    public static ConnectionPool newFixedConnectionPool(String host, int port,
            String username, String password, int poolSize) {
        return new FixedConnectionPool(host, port, username, password, poolSize);
    }

    /**
     * The default connection pool size.
     */
    private static final int DEFAULT_POOL_SIZE = 10;

    // Each connection will, if not active, will automatically expire. These
    // units are chosen to correspond to the AccessToken TTL that is defined in
    // {@link AccessManager}.
    private static final int CONNECTION_TTL = 24;
    private static final TimeUnit CONNECTION_TTL_UNIT = TimeUnit.HOURS;

    /**
     * A mapping from connection to a flag indicating if the connection is
     * active (e.g. taken).
     */
    private final Cache<Concourse, AtomicBoolean> connections;

    /**
     * The number of connections that are currently available;
     */
    private int numAvailableConnections;

    /**
     * A flag to indicate if the pool is currently open and operational.
     */
    private boolean open = true;

    /**
     * The lock that is used for concurrency control.
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    /**
     * Construct a new instance.
     * 
     * @param host
     * @param port
     * @param username
     * @param password
     * @param poolSize
     */
    protected ConnectionPool(String host, int port, String username,
            String password, int poolSize) {
        this.connections = CacheBuilder
                .newBuilder()
                .initialCapacity(poolSize)
                .expireAfterWrite(CONNECTION_TTL, CONNECTION_TTL_UNIT)
                .removalListener(
                        new RemovalListener<Concourse, AtomicBoolean>() {

                            @Override
                            public void onRemoval(
                                    RemovalNotification<Concourse, AtomicBoolean> notification) {
                                lock.writeLock().lock();
                                try {
                                    if(notification.getValue().get()) { // ensure
                                                                        // that
                                                                        // active
                                                                        // connections
                                                                        // don't
                                                                        // get
                                                                        // dropped
                                        connections.put(notification.getKey(),
                                                notification.getValue());

                                    }
                                    else {
                                        // Take care of the case where a
                                        // non-active connection is evicted. For
                                        // example, a FixedConnectionPool will
                                        // probably want to add back a new
                                        // connection to ensure that the
                                        // poolSize remains the same
                                        handleEvictedNonActiveConnection(notification
                                                .getKey());
                                    }
                                }
                                finally {
                                    lock.writeLock().unlock();
                                }

                            }

                        }).build();
        this.numAvailableConnections = poolSize;
        for (int i = 0; i < poolSize; i++) {
            connections.put(Concourse.connect(host, port, username, password),
                    new AtomicBoolean(false));
        }
        // Ensure that the client connections are forced closed when the JVM is
        // shutdown in case the user does not properly shutdown the pool
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

            @Override
            public void run() {
                if(open) {
                    exitAllConnections();
                }
            }

        }));
    }

    @Override
    public void close() throws Exception {
        lock.writeLock().lock();
        try {
            Preconditions.checkState(open, "Connection pool is closed");
            Preconditions.checkState(isCloseable(),
                    "Cannot shutdown the connection pool "
                            + "until all the connections have been returned");
            open = false;
            exitAllConnections();
        }
        finally {
            lock.writeLock().unlock();
        }

    }

    /**
     * Return {@code true} if the pool has any available connections.
     * 
     * @return {@code true} if there are one or more available connections
     */
    public boolean hasAvailableConnection() {
        Preconditions.checkState(open, "Connection pool is closed");
        lock.readLock().lock();
        try {
            return numAvailableConnections > 0;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Return a previously requested connection back to the pool.
     * 
     * @param connection
     */
    public void release(Concourse connection) {
        Preconditions.checkState(open, "Connection pool is closed");
        Preconditions.checkArgument(
                connections.getIfPresent(connection) != null,
                "Cannot release the connection because it "
                        + "was not previously requested from this pool");
        lock.writeLock().lock();
        try {
            if(connections.getIfPresent(connection).compareAndSet(true, false)) {
                numAvailableConnections++;
            }
            else {
                throw new IllegalStateException(
                        "This is an unreachable state that is obviously "
                                + "reachable, indicating a bug in the code");
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Request a connection from the pool and block until one is available and
     * returned.
     * 
     * @return a connection
     */
    public Concourse request() {
        Preconditions.checkState(open, "Connection pool is closed");
        while (!hasAvailableConnection()) {
            continue;
        }
        lock.writeLock().lock();
        try {
            for (Concourse connection : connections.asMap().keySet()) {
                if(connections.getIfPresent(connection).compareAndSet(false,
                        true)) {
                    numAvailableConnections--;
                    return connection;
                }
            }
            throw new IllegalStateException(
                    "This is an unreachable state that is obviously "
                            + "reachable, indicating a bug in the code");
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Handle the case where a non-active connection is evicted from the
     * {@link #connections} cache.
     * 
     * @param connection
     */
    protected void handleEvictedNonActiveConnection(Concourse connection) {
        connections.put(connection, new AtomicBoolean(false));
    }

    /**
     * Exit all the connections managed by the pool.
     */
    private void exitAllConnections() {
        // No need to do local locking since this is a private method called
        // internally in an already locked context
        for (Concourse concourse : connections.asMap().keySet()) {
            concourse.exit();
        }
    }

    /**
     * Return {@code true} if none of the connections are currently active.
     * 
     * @return {@code true} if the pool can be closed
     */
    private boolean isCloseable() {
        // No need to do local locking since this is a private method called
        // internally in an already locked context
        for (AtomicBoolean bool : connections.asMap().values()) {
            if(bool.get()) {
                return false;
            }
        }
        return true;
    }

}