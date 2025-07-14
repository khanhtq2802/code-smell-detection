/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.ffi.impl.nfi;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.r.ffi.impl.common.LibPaths;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.ffi.DLL;
import com.oracle.truffle.r.runtime.ffi.DLL.SymbolHandle;
import com.oracle.truffle.r.runtime.ffi.DLLRFFI;
import com.oracle.truffle.r.runtime.ffi.RFFIFactory;
import com.oracle.truffle.r.runtime.ffi.RFFIFactory.Type;

public class TruffleNFI_DLL implements DLLRFFI {

    public static final class NFIHandle implements LibHandle {
        @SuppressWarnings("unused") private final String libName;
        final TruffleObject libHandle;

        NFIHandle(String libName, TruffleObject libHandle) {
            this.libName = libName;
            this.libHandle = libHandle;
        }

        @Override
        public Type getRFFIType() {
            return RFFIFactory.Type.NFI;
        }
    }

    private static final class TruffleNFI_DLOpenNode extends Node implements DLLRFFI.DLOpenNode {

        @Override
        @TruffleBoundary
        public LibHandle execute(String path, boolean local, boolean now) {
            String librffiPath = LibPaths.getBuiltinLibPath("R");
            // Do not call before/afterDowncall when loading libR to prevent the pushing/popping of
            // the callback array, which requires that the libR have already been loaded
            boolean notifyStateRFFI = !librffiPath.equals(path);
            long before = notifyStateRFFI ? RContext.getInstance().getStateRFFI().beforeDowncall(RFFIFactory.Type.NFI) : 0;
            try {
                String libName = DLL.libName(path);
                Env env = RContext.getInstance().getEnv();
                TruffleObject libHandle = (TruffleObject) env.parse(Source.newBuilder("nfi", prepareLibraryOpen(path, local, now), path).build()).call();
                return new NFIHandle(libName, libHandle);
            } finally {
                if (notifyStateRFFI) {
                    RContext.getInstance().getStateRFFI().afterDowncall(before, RFFIFactory.Type.NFI);
                }
            }
        }
    }

    @TruffleBoundary
    private static String prepareLibraryOpen(String path, boolean local, boolean now) {
        StringBuilder sb = new StringBuilder("load");
        sb.append("(");
        sb.append(local ? "RTLD_LOCAL" : "RTLD_GLOBAL");
        sb.append('|');
        sb.append(now ? "RTLD_NOW" : "RTLD_LAZY");
        sb.append(") \"");
        sb.append(path);
        sb.append('"');
        return sb.toString();
    }

    private static class TruffleNFI_DLSymNode extends Node implements DLLRFFI.DLSymNode {

        @Child private Node lookupSymbol;

        @Override
        @TruffleBoundary
        public SymbolHandle execute(Object handle, String symbol) {
            assert handle instanceof NFIHandle;
            NFIHandle nfiHandle = (NFIHandle) handle;
            if (lookupSymbol == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupSymbol = insert(Message.READ.createNode());
            }
            try {
                TruffleObject result = (TruffleObject) ForeignAccess.sendRead(lookupSymbol, nfiHandle.libHandle, symbol);
                return new SymbolHandle(result);
            } catch (UnknownIdentifierException e) {
                throw new UnsatisfiedLinkError();
            } catch (InteropException e) {
                throw RInternalError.shouldNotReachHere();
            }
        }
    }

    private static class TruffleNFI_DLCloseNode extends Node implements DLLRFFI.DLCloseNode {

        @Override
        public int execute(Object handle) {
            assert handle instanceof NFIHandle;
            // TODO
            return 0;
        }
    }

    @Override
    public DLOpenNode createDLOpenNode() {
        return new TruffleNFI_DLOpenNode();
    }

    @Override
    public DLSymNode createDLSymNode() {
        return new TruffleNFI_DLSymNode();
    }

    @Override
    public DLCloseNode createDLCloseNode() {
        return new TruffleNFI_DLCloseNode();
    }
}
