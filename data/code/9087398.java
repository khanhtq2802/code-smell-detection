/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.runtime.conn;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.conn.ConnectionSupport.AbstractOpenMode;
import com.oracle.truffle.r.runtime.conn.ConnectionSupport.BaseRConnection;
import com.oracle.truffle.r.runtime.conn.ConnectionSupport.ConnectionClass;

public class SocketConnections {
    /**
     * Base class for socket connections.
     *
     * While binary operations, e.g. {@code writeBin} are only legal on binary connections, text
     * operations are legal on text and binary connections.
     */
    public static class RSocketConnection extends BaseRConnection {
        protected final boolean server;
        protected final String host;
        protected final int port;
        protected final int timeout;

        public RSocketConnection(String modeString, boolean server, String host, int port, boolean blocking, int timeout, String encoding) throws IOException {
            super(ConnectionClass.Socket, modeString, AbstractOpenMode.Read, blocking, encoding);
            this.server = server;
            this.host = host;
            this.port = port;
            this.timeout = timeout;
            openNonLazyConnection();
        }

        @Override
        public boolean canRead() {
            // socket connections can always be read
            return true;
        }

        @Override
        public boolean canWrite() {
            // socket connections can always be written
            return true;
        }

        @Override
        @TruffleBoundary
        protected void createDelegateConnection() throws IOException {
            DelegateRConnection delegate;
            if (server) {
                delegate = new RServerSocketConnection(this);
            } else {
                if (isBlocking()) {
                    delegate = new RClientSocketConnection(this);
                } else {
                    delegate = new RClientSocketNonBlockConnection(this);
                }
            }
            setDelegate(delegate);
        }

        @Override
        public String getSummaryDescription() {
            return (server ? "<-" : "->") + host + ":" + port;
        }

        @TruffleBoundary
        public static byte[] select(RSocketConnection[] socketConnections, boolean write, long timeout) throws IOException {
            int op = write ? SelectionKey.OP_WRITE : SelectionKey.OP_READ;

            HashMap<RSocketConnection, SelectionKey> table = new HashMap<>();
            Selector selector = Selector.open();
            for (RSocketConnection con : socketConnections) {
                con.checkOpen();

                SocketChannel sc = (SocketChannel) con.theConnection.getChannel();
                sc.configureBlocking(false);
                table.put(con, sc.register(selector, op));
            }
            int select;
            if (timeout >= 0) {
                select = selector.select(timeout);
            } else {
                select = selector.select();
            }

            byte[] result = new byte[socketConnections.length];
            if (select > 0) {
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                for (int i = 0; i < result.length; i++) {
                    result[i] = RRuntime.asLogical(selectedKeys.contains(table.get(socketConnections[i])));
                }
            }
            return result;
        }
    }

    private abstract static class RSocketReadWriteConnection extends DelegateReadWriteRConnection {
        private Socket socket;
        private SocketChannel channel;
        protected final RSocketConnection thisBase;

        protected RSocketReadWriteConnection(RSocketConnection base) {
            super(base, 0);
            this.thisBase = base;
        }

        protected void openStreams(SocketChannel socketArg) throws IOException {
            channel = socketArg;
            socket = socketArg.socket();
            if (thisBase.isBlocking()) {
                channel.configureBlocking(true);
                // Java (int) timeouts do not meet the POSIX standard of 31 days
                long millisTimeout = ((long) thisBase.timeout) * 1000;
                if (millisTimeout > Integer.MAX_VALUE) {
                    millisTimeout = Integer.MAX_VALUE;
                }
                socket.setSoTimeout((int) millisTimeout);
            } else {
                channel.configureBlocking(false);
            }
        }

        @Override
        public ByteChannel getChannel() {
            return channel;
        }

        @Override
        public boolean isSeekable() {
            return false;
        }
    }

    private abstract static class RSocketReadWriteNonBlockConnection extends DelegateReadWriteRConnection {
        private Socket socket;
        private SocketChannel socketChannel;

        protected RSocketReadWriteNonBlockConnection(RSocketConnection base) {
            super(base, 0);
        }

        protected void openStreams(Socket socketArg) throws IOException {
            this.socket = socketArg;
            this.socketChannel = socket.getChannel();
            socketChannel.configureBlocking(false);
        }

        @Override
        public void close() throws IOException {
            socketChannel.close();
            socket.close();
        }

        @Override
        public ByteChannel getChannel() {
            return socketChannel;
        }

        @Override
        public boolean isSeekable() {
            return false;
        }
    }

    private static class RServerSocketConnection extends RSocketReadWriteConnection {
        private final SocketChannel connectionSocket;

        RServerSocketConnection(RSocketConnection base) throws IOException {
            super(base);
            InetSocketAddress addr = new InetSocketAddress(base.port);
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();

            // we expect only one connection per-server socket; furthermore, we need to accommodate
            // for multiple connections being established locally on the same server port;
            // consequently, we close the server socket at the end of the constructor and allow
            // address reuse to be able to open the next connection after the current one closes
            serverSocketChannel.socket().setReuseAddress(true);
            serverSocketChannel.socket().bind(addr);
            connectionSocket = serverSocketChannel.accept();
            openStreams(connectionSocket);
            serverSocketChannel.close();
        }

        @Override
        public void close() throws IOException {
            super.close();
            connectionSocket.close();
        }

        @Override
        public ByteChannel getChannel() {
            return connectionSocket;
        }

    }

    private static class RClientSocketConnection extends RSocketReadWriteConnection {

        RClientSocketConnection(RSocketConnection base) throws IOException {
            super(base);
            SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(base.host, base.port));
            openStreams(socketChannel);
        }
    }

    private static class RClientSocketNonBlockConnection extends RSocketReadWriteNonBlockConnection {

        RClientSocketNonBlockConnection(RSocketConnection base) throws IOException {
            super(base);
            SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(base.host, base.port));
            openStreams(socketChannel.socket());
        }
    }
}
