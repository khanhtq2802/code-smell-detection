/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.function;

import static com.oracle.truffle.r.runtime.context.FastROptions.RestrictForceSplitting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.nodes.RASTUtils;
import com.oracle.truffle.r.nodes.RRootNode;
import com.oracle.truffle.r.nodes.access.ConstantNode;
import com.oracle.truffle.r.nodes.access.variables.LocalReadVariableNode;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinRootNode;
import com.oracle.truffle.r.nodes.function.PromiseHelperNode.PromiseCheckHelperNode;
import com.oracle.truffle.r.nodes.function.RCallNodeGen.FunctionDispatchNodeGen;
import com.oracle.truffle.r.nodes.function.RCallNodeGen.SendForeignExecuteMessageNodeGen;
import com.oracle.truffle.r.nodes.function.RCallNodeGen.SendForeignInvokeMessageNodeGen;
import com.oracle.truffle.r.nodes.function.S3FunctionLookupNode.Result;
import com.oracle.truffle.r.nodes.function.call.CallRFunctionNode;
import com.oracle.truffle.r.nodes.function.call.PrepareArguments;
import com.oracle.truffle.r.nodes.function.visibility.SetVisibilityNode;
import com.oracle.truffle.r.nodes.profile.TruffleBoundaryNode;
import com.oracle.truffle.r.nodes.profile.VectorLengthProfile;
import com.oracle.truffle.r.runtime.Arguments;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RArguments.S3Args;
import com.oracle.truffle.r.runtime.RArguments.S3DefaultArguments;
import com.oracle.truffle.r.runtime.RCaller;
import com.oracle.truffle.r.runtime.RDeparse;
import com.oracle.truffle.r.runtime.RDispatch;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RErrorHandling;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.RVisibility;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.builtins.FastPathFactory;
import com.oracle.truffle.r.runtime.builtins.RBuiltinDescriptor;
import com.oracle.truffle.r.runtime.conn.RConnection;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.Closure;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RAttributeStorage;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.REmpty;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.env.frame.FrameSlotChangeMonitor;
import com.oracle.truffle.r.runtime.interop.Foreign2R;
import com.oracle.truffle.r.runtime.interop.R2Foreign;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;
import com.oracle.truffle.r.runtime.nodes.RFastPathNode;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxCall;
import com.oracle.truffle.r.runtime.nodes.RSyntaxElement;
import com.oracle.truffle.r.runtime.nodes.RSyntaxLookup;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

/**
 * Represents and executes the syntax node for a function call.
 *
 * See {@code documentation/dev/arcane.md} for the documentation of the {@link RCallNode} AST.
 */
@NodeInfo(cost = NodeCost.NONE)
@NodeChild(value = "function", type = RNode.class)
@ReportPolymorphism
public abstract class RCallNode extends RCallBaseNode implements RSyntaxNode, RSyntaxCall {

    // currently cannot be RSourceSectionNode because of TruffleDSL restrictions

    @CompilationFinal private SourceSection sourceSection;

    @Override
    public final void setSourceSection(SourceSection sourceSection) {
        assert sourceSection != null;
        this.sourceSection = sourceSection;
    }

    @Override
    public final SourceSection getLazySourceSection() {
        return sourceSection;
    }

    @Override
    public final SourceSection getSourceSection() {
        RDeparse.ensureSourceSection(this);
        return sourceSection;
    }

    @Override
    public RBaseNode getErrorContext() {
        return this;
    }

    private final RSyntaxNode[] arguments;
    private final int[] varArgIndexes;
    private final ArgumentsSignature signature;
    @Child private ReadVariableNode lookupVarArgs;
    @Child public LocalReadVariableNode explicitArgs;

    private final ConditionProfile nullBuiltinProfile = ConditionProfile.createBinaryProfile();

    // needed for INTERNAL_GENERIC calls:
    @Child private FunctionDispatch internalDispatchCall;
    @Child private GetBasicFunction getBasicFunction;

    private ExplicitArgs readExplicitArgs(VirtualFrame frame) {
        Object result = explicitArgs.execute(frame);
        if (result instanceof ExplicitArgs) {
            return (ExplicitArgs) result;
        } else {
            throw RInternalError.shouldNotReachHere("explicit args should always be of type ExplicitArgs");
        }
    }

    protected RCaller createCaller(VirtualFrame frame, RFunction function) {
        if (explicitArgs == null) {
            return RCaller.create(frame, this);
        } else {
            RCaller explicitCallerValue = readExplicitArgs(frame).caller;
            if (explicitCallerValue != null) {
                return explicitCallerValue;
            }
            return RCaller.create(frame, RCallerHelper.createFromArguments(function, readExplicitArgs(frame).args));
        }
    }

    protected Object getCallerFrame(VirtualFrame frame) {
        return explicitArgs == null ? null : readExplicitArgs(frame).callerFrame;
    }

    protected RCallNode(SourceSection sourceSection, RSyntaxNode[] arguments, ArgumentsSignature signature) {
        assert sourceSection != null;
        this.sourceSection = sourceSection;
        this.arguments = arguments;
        this.explicitArgs = null;
        this.varArgIndexes = getVarArgIndexes(arguments);
        this.lookupVarArgs = varArgIndexes.length == 0 ? null : ReadVariableNode.createSilent(ArgumentsSignature.VARARG_NAME, RType.Any);
        this.signature = signature;
    }

    protected RCallNode(SourceSection sourceSection, Object explicitArgsIdentifier) {
        assert sourceSection != null;
        this.sourceSection = sourceSection;
        this.arguments = null;
        this.explicitArgs = LocalReadVariableNode.create(explicitArgsIdentifier, false);
        this.varArgIndexes = null;
        this.lookupVarArgs = null;
        this.signature = null;
    }

    public static int[] getVarArgIndexes(RSyntaxNode[] arguments) {
        List<Integer> varArgsSymbolIndices = new ArrayList<>();
        for (int i = 0; i < arguments.length; i++) {
            RSyntaxNode arg = arguments[i];
            if (arg instanceof RSyntaxLookup) {
                RSyntaxLookup lookup = (RSyntaxLookup) arg;
                // Check for presence of "..." in the arguments
                if (ArgumentsSignature.VARARG_NAME.equals(lookup.getIdentifier())) {
                    varArgsSymbolIndices.add(i);
                }
            }
        }
        // Setup and return
        int[] varArgsSymbolIndicesArr = new int[varArgsSymbolIndices.size()];
        for (int i = 0; i < varArgsSymbolIndicesArr.length; i++) {
            varArgsSymbolIndicesArr[i] = varArgsSymbolIndices.get(i);
        }
        return varArgsSymbolIndicesArr;
    }

    @Override
    public Arguments<RSyntaxNode> getArguments() {
        return Arguments.create(arguments, signature);
    }

    private RArgsValuesAndNames lookupVarArgs(VirtualFrame frame, boolean ignore, RBuiltinDescriptor builtin) {
        return ignore ? null : lookupVarArgs(frame, builtin);
    }

    private RArgsValuesAndNames lookupVarArgs(VirtualFrame frame, RBuiltinDescriptor builtin) {
        if (explicitArgs != null) {
            return readExplicitArgs(frame).args;
        }
        if (lookupVarArgs == null) {
            return null;
        }
        if (builtin != null && !builtin.lookupVarArgs()) {
            return null;
        }
        Object varArgs = lookupVarArgs.execute(frame);
        if (!(varArgs instanceof RArgsValuesAndNames)) {
            CompilerDirectives.transferToInterpreter();
            throw RError.error(RError.SHOW_CALLER, RError.Message.NO_DOT_DOT_DOT);
        }
        return (RArgsValuesAndNames) varArgs;
    }

    protected FunctionDispatch createUninitializedCall() {
        return FunctionDispatchNodeGen.create(this, explicitArgs != null, null);
    }

    protected FunctionDispatch createUninitializedExplicitCall() {
        return FunctionDispatchNodeGen.create(this, true, null);
    }

    /**
     * If there are no parameters, or the target function does not refer to a builtin, or the
     * builtin has no special dispatching, then we know that we will just call the function with no
     * special dispatch logic.
     */
    protected boolean isDefaultDispatch(RFunction function) {
        return (signature != null && signature.isEmpty()) || nullBuiltinProfile.profile(function.getRBuiltin() == null) || function.getRBuiltin().getDispatch() == RDispatch.DEFAULT;
    }

    @Specialization
    public Object callForeign(VirtualFrame frame, DeferredFunctionValue function,
                    @Cached("createForeignInvoke()") ForeignInvoke call) {
        return call.execute(frame, function);
    }

    @Specialization(guards = "isDefaultDispatch(function)")
    public Object call(VirtualFrame frame, RFunction function,
                    @Cached("createUninitializedCall()") FunctionDispatch call,
                    @Cached("createIdentityProfile()") ValueProfile builtinValueProfile) {
        RBuiltinDescriptor builtin = builtinValueProfile.profile(function.getRBuiltin());
        return call.execute(frame, function, lookupVarArgs(frame, builtin), null, null);
    }

    protected RNode createDispatchArgument(int index) {
        return RContext.getASTBuilder().process(arguments[index]).asRNode();
    }

    /**
     * If the target function refers to a builtin that requires internal generic dispatch and there
     * are actual parameters to dispatch on, then we will do an internal generic dispatch on the
     * first parameter.
     */
    protected boolean isInternalGenericDispatch(RFunction function) {
        if (signature != null && signature.isEmpty()) {
            return false;
        }
        RBuiltinDescriptor builtin = function.getRBuiltin();
        return builtin != null && builtin.getDispatch() == RDispatch.INTERNAL_GENERIC;
    }

    @Specialization(guards = {"explicitArgs == null", "isInternalGenericDispatch(function)"})
    public Object callInternalGeneric(VirtualFrame frame, RFunction function,
                    @Cached("createDispatchArgument(0)") RNode dispatchArgument,
                    @Cached("new()") TemporarySlotNode dispatchTempSlot,
                    @Cached("create()") ClassHierarchyNode classHierarchyNode,
                    @Cached("createWithError()") S3FunctionLookupNode dispatchLookup,
                    @Cached("createIdentityProfile()") ValueProfile builtinProfile,
                    @Cached("createBinaryProfile()") ConditionProfile implicitTypeProfile,
                    @Cached("createBinaryProfile()") ConditionProfile isAttributableProfile,
                    @Cached("createBinaryProfile()") ConditionProfile resultIsBuiltinProfile,
                    @Cached("create()") GetBaseEnvFrameNode getBaseEnvFrameNode,
                    @Cached("createBinaryProfile()") ConditionProfile isS4Profile) {

        RBuiltinDescriptor builtin = builtinProfile.profile(function.getRBuiltin());
        Object dispatchObject = dispatchArgument.execute(frame);
        // Cannot dispatch on REmpty
        if (dispatchObject == REmpty.instance) {
            throw error(RError.Message.ARGUMENT_EMPTY, 1);
        }

        FrameSlot slot = dispatchTempSlot.initialize(frame, dispatchObject);
        try {
            boolean isFieldAccess = builtin.isFieldAccess();
            if (internalDispatchCall == null) {
                createInternDispatchCall(isFieldAccess, slot);
            }

            if (isAttributableProfile.profile(dispatchObject instanceof RAttributeStorage) && isS4Profile.profile(((RAttributeStorage) dispatchObject).isS4())) {
                if (getBasicFunction == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    getBasicFunction = insert(new GetBasicFunction());
                }
                Object basicFun = getBasicFunction.execute(frame, builtin.getName());
                if (basicFun != null) {
                    Object result = internalDispatchCall.execute(frame, (RFunction) basicFun, lookupVarArgs(frame, isFieldAccess, builtin), null, null);
                    if (result != RRuntime.DEFERRED_DEFAULT_MARKER) {
                        return result;
                    }
                }
            }
            RStringVector type = classHierarchyNode.execute(dispatchObject);
            S3Args s3Args;
            RFunction resultFunction;
            if (implicitTypeProfile.profile(type != null)) {
                Result result = dispatchLookup.execute(frame, builtin.getGenericName(), type, null, frame.materialize(), getBaseEnvFrameNode.execute());
                if (resultIsBuiltinProfile.profile(result.function.isBuiltin())) {
                    s3Args = null;
                } else {
                    s3Args = result.createS3Args(frame);
                }
                resultFunction = result.function;
            } else {
                // We always call the builtin even if there is "xyz.default" function.
                // This means that the builtin can implement a fast-path of the "default" function
                // for class-less dispatch argument values
                // Note: e.g., "range" relies on this
                s3Args = null;
                resultFunction = function;
            }
            if (internalDispatchCall == null || internalDispatchCall.tempFrameSlot != slot) {
                createInternDispatchCall(isFieldAccess, slot);
            }
            return internalDispatchCall.execute(frame, resultFunction, lookupVarArgs(frame, isFieldAccess, builtin), s3Args, null);
        } finally {
            TemporarySlotNode.cleanup(frame, dispatchObject, slot);
        }
    }

    private void createInternDispatchCall(boolean isFieldAccess, FrameSlot slot) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        AlteredArguments alteredArguments = null;
        if (isFieldAccess) {
            RSyntaxNode[] newArgs = Arrays.copyOf(arguments, arguments.length);
            newArgs[1] = RContext.getASTBuilder().constant(newArgs[1].getSourceSection(), CallUtils.unevaluatedArgAsFieldName(this, newArgs[1]));
            // we know that there are no varargs in the signature, but this RCallNode
            // instance could have been confused by lookup of "..." as the field, in which
            // case it would think it should lookup varargs.
            alteredArguments = new AlteredArguments(newArgs, new int[0]);
        }
        internalDispatchCall = insert(FunctionDispatchNodeGen.create(this, alteredArguments, false, slot));
    }

    @Specialization(guards = {"explicitArgs != null", "isInternalGenericDispatch(function)"})
    public Object callInternalGenericExplicit(VirtualFrame frame, RFunction function,
                    @Cached("create()") ClassHierarchyNode classHierarchyNode,
                    @Cached("createWithError()") S3FunctionLookupNode dispatchLookup,
                    @Cached("createIdentityProfile()") ValueProfile builtinProfile,
                    @Cached("createBinaryProfile()") ConditionProfile implicitTypeProfile,
                    @Cached("createBinaryProfile()") ConditionProfile isAttributableProfile,
                    @Cached("createBinaryProfile()") ConditionProfile resultIsBuiltinProfile,
                    @Cached("createPromiseHelper()") PromiseCheckHelperNode promiseHelperNode,
                    @Cached("createUninitializedExplicitCall()") FunctionDispatch call,
                    @Cached("createBinaryProfile()") ConditionProfile isS4Profile,
                    @Cached("create()") GetBaseEnvFrameNode getBaseEnvFrameNode) {
        RBuiltinDescriptor builtin = builtinProfile.profile(function.getRBuiltin());
        RArgsValuesAndNames argAndNames = readExplicitArgs(frame).args;

        RStringVector type = null;
        if (!argAndNames.isEmpty()) {
            Object dispatchObject = argAndNames.getArgument(0);
            if (isAttributableProfile.profile(dispatchObject instanceof RAttributeStorage) && isS4Profile.profile(((RAttributeStorage) dispatchObject).isS4())) {
                if (getBasicFunction == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    getBasicFunction = insert(new GetBasicFunction());
                }
                Object basicFun = getBasicFunction.execute(frame, builtin.getName());
                if (basicFun != null) {
                    Object result = call.execute(frame, (RFunction) basicFun, argAndNames, null, null);
                    if (result != RRuntime.DEFERRED_DEFAULT_MARKER) {
                        return result;
                    }
                }
            }
            type = classHierarchyNode.execute(promiseHelperNode.checkVisibleEvaluate(frame, dispatchObject));
        }

        S3Args s3Args;
        RFunction resultFunction;
        if (implicitTypeProfile.profile(type != null)) {
            Result result = dispatchLookup.execute(frame, builtin.getName(), type, null, frame.materialize(), getBaseEnvFrameNode.execute());
            if (resultIsBuiltinProfile.profile(result.function.isBuiltin())) {
                s3Args = null;
            } else {
                s3Args = result.createS3Args(frame);
            }
            resultFunction = result.function;
        } else {
            s3Args = null;
            resultFunction = function;
        }
        return call.execute(frame, resultFunction, argAndNames, s3Args, null);
    }

    protected CallArgumentsNode createGenericDispatchArguments() {
        return signature == null ? null : createArguments(null, false, true);
    }

    protected boolean isGroupGenericDispatch(RFunction function) {
        if (signature != null && signature.isEmpty()) {
            return false;
        }
        RBuiltinDescriptor builtin = function.getRBuiltin();
        return builtin != null && builtin.getDispatch().isGroupGeneric();
    }

    protected PromiseCheckHelperNode createPromiseHelper() {
        return new PromiseCheckHelperNode();
    }

    @CompilationFinal private ArgumentsSignature summaryGroupSignatureCached = null;
    @CompilationFinal private boolean summaryGroupHasNaRmCached;

    @Specialization(guards = "isGroupGenericDispatch(function)")
    public Object callGroupGeneric(VirtualFrame frame, RFunction function,
                    @Cached("createGenericDispatchArguments()") CallArgumentsNode callArguments,
                    @Cached("create()") ClassHierarchyNode classHierarchyNodeX,
                    @Cached("createWithException()") S3FunctionLookupNode dispatchLookupX,
                    @Cached("create()") ClassHierarchyNode classHierarchyNodeY,
                    @Cached("createWithException()") S3FunctionLookupNode dispatchLookupY,
                    @Cached("createIdentityProfile()") ValueProfile builtinProfile,
                    @Cached("createBinaryProfile()") ConditionProfile emptyArgumentsProfile,
                    @Cached("createBinaryProfile()") ConditionProfile implicitTypeProfileX,
                    @Cached("createBinaryProfile()") ConditionProfile implicitTypeProfileY,
                    @Cached("createBinaryProfile()") ConditionProfile mismatchProfile,
                    @Cached("createBinaryProfile()") ConditionProfile resultIsBuiltinProfile,
                    @Cached("createBinaryProfile()") ConditionProfile summaryGroupNaRmProfile,
                    @Cached("createBinaryProfile()") ConditionProfile summaryGroupProfile,
                    @Cached("createBinaryProfile()") ConditionProfile isAttributableProfile,
                    @Cached("createBinaryProfile()") ConditionProfile isS4Profile,
                    @Cached("createCountingProfile()") LoopConditionProfile s4ArgsProfile,
                    @Cached("createPromiseHelper()") PromiseCheckHelperNode promiseHelperNode,
                    @Cached("createUninitializedExplicitCall()") FunctionDispatch call,
                    @Cached("create()") GetBaseEnvFrameNode getBaseEnvFrameNode) {

        RBuiltinDescriptor builtin = builtinProfile.profile(function.getRBuiltin());
        Object[] args;
        ArgumentsSignature argsSignature;
        if (explicitArgs != null) {
            ExplicitArgs explicitArgsVal = readExplicitArgs(frame);
            args = explicitArgsVal.args.getArguments();
            argsSignature = explicitArgsVal.args.getSignature();
        } else {
            RArgsValuesAndNames varArgsVal = lookupVarArgs(frame, builtin);
            args = callArguments.evaluateFlattenObjects(frame, varArgsVal);
            argsSignature = callArguments.flattenNames(varArgsVal);
        }

        if (emptyArgumentsProfile.profile(args.length == 0)) {
            // nothing to dispatch on, this is a valid situation, e.g. prod() == 1
            return call.execute(frame, function, new RArgsValuesAndNames(args, argsSignature), null, null);
        }

        RDispatch dispatch = builtin.getDispatch();
        // max(na.rm=TRUE,arg1) dispatches to whatever is class of arg1 not taking the
        // named argument 'na.rm' into account. Note: signatures should be interned, identity
        // comparison is enough. Signature length > 0, because we dispatched on at least one arg
        int typeXIdx = 0;
        if (summaryGroupNaRmProfile.profile(dispatch == RDispatch.SUMMARY_GROUP_GENERIC &&
                        Utils.identityEquals(argsSignature.getName(typeXIdx), RArguments.SUMMARY_GROUP_NA_RM_ARG_NAME))) {
            typeXIdx = 1;
        }

        Object dispatchObject = promiseHelperNode.checkVisibleEvaluate(frame, args[typeXIdx]);

        boolean isS4Dispatch = false;

        // CHECK FOR S4 DISPATCH
        // First, check S4 dispatch for 'dispatchObject' (= first suitable argument)
        if (isAttributableProfile.profile(dispatchObject instanceof RAttributeStorage) && isS4Profile.profile(((RAttributeStorage) dispatchObject).isS4())) {
            isS4Dispatch = true;
        } else if (args.length > typeXIdx + 1 && dispatch == RDispatch.OPS_GROUP_GENERIC) {
            s4ArgsProfile.profileCounted(args.length - typeXIdx);
            for (int i = typeXIdx + 1; s4ArgsProfile.inject(i < args.length); i++) {
                Object argi = promiseHelperNode.checkEvaluate(frame, args[i]);
                if (isAttributableProfile.profile(argi instanceof RAttributeStorage) && isS4Profile.profile(((RAttributeStorage) argi).isS4())) {
                    isS4Dispatch = true;
                    break;
                }
            }
        }

        if (isS4Dispatch) {
            RList list = (RList) promiseHelperNode.checkVisibleEvaluate(frame, REnvironment.getRegisteredNamespace("methods").get(".BasicFunsList"));
            int index = list.getElementIndexByName(builtin.getName());
            if (index != -1) {
                RFunction basicFun = (RFunction) list.getDataAt(index);
                Object res = call.execute(frame, basicFun, new RArgsValuesAndNames(args, argsSignature), null, null);
                if (res != RRuntime.DEFERRED_DEFAULT_MARKER) {
                    return res;
                }
            }
        }

        RStringVector typeX = classHierarchyNodeX.execute(dispatchObject);
        Result resultX = null;
        if (implicitTypeProfileX.profile(typeX != null)) {
            resultX = dispatchLookupX.execute(frame, builtin.getName(), typeX, dispatch.getGroupGenericName(), frame.materialize(), getBaseEnvFrameNode.execute());
        }
        Result resultY = null;
        if (args.length > 1 && dispatch == RDispatch.OPS_GROUP_GENERIC) {
            RStringVector typeY = classHierarchyNodeY.execute(promiseHelperNode.checkEvaluate(frame, args[1]));
            if (implicitTypeProfileY.profile(typeY != null)) {
                resultY = dispatchLookupY.execute(frame, builtin.getName(), typeY, dispatch.getGroupGenericName(), frame.materialize(), getBaseEnvFrameNode.execute());
            }
        }

        Result result;
        RStringVector dotMethod;
        if (resultX == null) {
            if (resultY == null) {
                result = null;
                dotMethod = null;
            } else {
                result = resultY;
                dotMethod = RDataFactory.createStringVector(new String[]{"", result.targetFunctionName}, true);
            }
        } else {
            if (resultY == null) {
                result = resultX;
                dotMethod = RDataFactory.createStringVector(new String[]{result.targetFunctionName, ""}, true);
            } else {
                if (mismatchProfile.profile(resultX.function != resultY.function)) {
                    RError.warning(this, RError.Message.INCOMPATIBLE_METHODS, resultX.targetFunctionName, resultY.targetFunctionName, dispatch.getGroupGenericName());
                    result = null;
                    dotMethod = null;
                } else {
                    result = resultX;
                    dotMethod = RDataFactory.createStringVector(new String[]{result.targetFunctionName, result.targetFunctionName}, true);
                }
            }
        }
        final S3Args s3Args;
        final RFunction resultFunction;
        if (result == null) {
            s3Args = null;
            resultFunction = function;
        } else {
            if (resultIsBuiltinProfile.profile(result.function.isBuiltin())) {
                s3Args = null;
            } else {
                s3Args = new S3Args(builtin.getName(), result.clazz, dotMethod, frame.materialize(), null, result.groupMatch ? dispatch.getGroupGenericName() : null);
            }
            resultFunction = result.function;
        }

        // Note: since we are actually not executing the function prologue which would write default
        // values to local variables representing arguments with default values, we have to check
        // whether we need to provide default values ourselves. The next call invoked by the
        // call.execute statement thinks that the formal signature is formal signature of the
        // concrete method, e.g. max.data.frame, which may not have default value for the same
        // arguments as the entry method, e.g. max(...,na.rm=TRUE). Unfortunately, in GnuR this is
        // inconsistent and only applies for 'Summary' group and not, for example, for Math group
        // with its default value for 'digits'
        S3DefaultArguments s3DefaulArguments = null;
        if (summaryGroupProfile.profile(dispatch == RDispatch.SUMMARY_GROUP_GENERIC)) {
            if (argsSignature != summaryGroupSignatureCached) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                summaryGroupSignatureCached = argsSignature;
                summaryGroupHasNaRmCached = false;
                for (int i = 0; i < argsSignature.getLength(); i++) {
                    if (Utils.identityEquals(argsSignature.getName(i), RArguments.SUMMARY_GROUP_NA_RM_ARG_NAME)) {
                        summaryGroupHasNaRmCached = true;
                        break;
                    }
                }
            }
            if (!summaryGroupHasNaRmCached) {
                s3DefaulArguments = RArguments.SUMMARY_GROUP_DEFAULT_VALUE_NA_RM;
            }
        }

        return call.execute(frame, resultFunction, new RArgsValuesAndNames(args, argsSignature), s3Args, s3DefaulArguments);
    }

    protected abstract static class ForeignCall extends LeafCallNode {
        @Child private CallArgumentsNode arguments;
        @Child private Foreign2R foreign2RNode;

        protected ForeignCall(RCallNode originalCall, CallArgumentsNode arguments) {
            super(originalCall);
            this.arguments = arguments;
        }

        protected Object[] evaluateArgs(VirtualFrame frame) {
            return originalCall.explicitArgs != null ? originalCall.readExplicitArgs(frame).args.getArguments()
                            : arguments.evaluateFlattenObjects(frame, originalCall.lookupVarArgs(frame, null));
        }

        protected Foreign2R getForeign2RNode() {
            if (foreign2RNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreign2RNode = insert(Foreign2R.create());
            }
            return foreign2RNode;
        }
    }

    protected static final class ForeignExecute extends ForeignCall {
        @Child private SendForeignExecuteMessage sendExecuteMessage = SendForeignExecuteMessageNodeGen.create();

        protected ForeignExecute(RCallNode originalCall, CallArgumentsNode arguments) {
            super(originalCall, arguments);
        }

        public Object execute(VirtualFrame frame, TruffleObject function) {
            return getForeign2RNode().execute(sendExecuteMessage.execute(function, evaluateArgs(frame)));
        }
    }

    protected static final class ForeignInvoke extends ForeignCall {
        @Child private SendForeignInvokeMessage sendInvokeMessage = SendForeignInvokeMessageNodeGen.create();

        protected ForeignInvoke(RCallNode originalCall, CallArgumentsNode arguments) {
            super(originalCall, arguments);
        }

        public Object execute(VirtualFrame frame, DeferredFunctionValue function) {
            return getForeign2RNode().execute(sendInvokeMessage.execute(function, evaluateArgs(frame)));
        }
    }

    @ImportStatic(DSLConfig.class)
    protected abstract static class SendForeignMessageBase extends Node {
        @Child private R2Foreign r2ForeignNode;

        protected Object[] args2Foreign(Object[] args) {
            if (r2ForeignNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                r2ForeignNode = insert(R2Foreign.create());
            }
            for (int i = 0; i < args.length; i++) {
                args[i] = r2ForeignNode.execute(args[i]);
            }
            return args;
        }
    }

    /**
     * Calls a foreign function using message EXECUTE.
     */
    protected abstract static class SendForeignExecuteMessage extends SendForeignMessageBase {

        public abstract Object execute(TruffleObject function, Object[] args);

        protected static Node createMessageNode() {
            return Message.EXECUTE.createNode();
        }

        @Specialization(guards = "argumentsArray.length == foreignCallArgCount", limit = "getCacheSize(8)")
        protected Object doCached(TruffleObject function, Object[] argumentsArray,
                        @Cached("createMessageNode()") Node messageNode,
                        @Cached("argumentsArray.length") @SuppressWarnings("unused") int foreignCallArgCount) {
            try {
                return ForeignAccess.sendExecute(messageNode, function, args2Foreign(argumentsArray));
            } catch (ArityException | UnsupportedMessageException | UnsupportedTypeException e) {
                CompilerDirectives.transferToInterpreter();
                RInternalError.reportError(e);
                throw RError.interopError(RError.findParentRBase(this), e, function);
            } catch (RuntimeException e) {
                throw RErrorHandling.handleInteropException(this, e);
            }
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        protected Object doGeneric(TruffleObject function, Object[] argumentsArray) {
            return doCached(function, argumentsArray, createMessageNode(), argumentsArray.length);
        }
    }

    /**
     * Calls a foreign function using message INVOKE.
     */
    protected abstract static class SendForeignInvokeMessage extends SendForeignMessageBase {

        public abstract Object execute(DeferredFunctionValue function, Object[] args);

        protected static Node createMessageNode() {
            return Message.INVOKE.createNode();
        }

        @Specialization(guards = "argumentsArray.length == foreignCallArgCount", limit = "getCacheSize(8)")
        protected Object doCached(DeferredFunctionValue lhs, Object[] argumentsArray,
                        @Cached("createMessageNode()") Node messageNode,
                        @Cached("argumentsArray.length") @SuppressWarnings("unused") int foreignCallArgCount) {
            TruffleObject receiver = lhs.getLHSReceiver();
            String member = lhs.getLHSMember();
            try {
                return ForeignAccess.sendInvoke(messageNode, receiver, member, args2Foreign(argumentsArray));
            } catch (ArityException | UnsupportedMessageException | UnsupportedTypeException | UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreter();
                RInternalError.reportError(e);
                throw RError.interopError(RError.findParentRBase(this), e, receiver);
            }
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        protected Object doGeneric(DeferredFunctionValue lhs, Object[] argumentsArray) {
            return doCached(lhs, argumentsArray, createMessageNode(), argumentsArray.length);
        }
    }

    protected ForeignExecute createForeignCall() {
        return new ForeignExecute(this, createArguments(null, true, true));
    }

    /**
     * Creates a foreign invoke node for a call of structure {@code lhsReceiver$lhsMember(args)}.
     */
    protected ForeignInvoke createForeignInvoke() {
        return new ForeignInvoke(this, createArguments(null, true, true));
    }

    protected static boolean isForeignObject(Object value) {
        return RRuntime.isForeignObject(value);
    }

    @Specialization(guards = "isForeignObject(function)")
    public Object call(VirtualFrame frame, TruffleObject function,
                    @Cached("createForeignCall()") ForeignExecute foreignCall) {
        return foreignCall.execute(frame, function);
    }

    @TruffleBoundary
    @Fallback
    public Object call(@SuppressWarnings("unused") Object function) {
        throw RError.error(RError.SHOW_CALLER, RError.Message.APPLY_NON_FUNCTION);
    }

    public CallArgumentsNode createArguments(FrameSlot tempFrameSlot, boolean modeChange, boolean modeChangeAppliesToAll) {
        return createArguments(tempFrameSlot, modeChange, modeChangeAppliesToAll, arguments, varArgIndexes, signature);
    }

    public CallArgumentsNode createArguments(FrameSlot tempFrameSlot, boolean modeChange, boolean modeChangeAppliesToAll, AlteredArguments alteredArguments) {
        RSyntaxNode[] args = alteredArguments == null ? arguments : alteredArguments.arguments;
        int[] varArgIdx = alteredArguments == null ? varArgIndexes : alteredArguments.varArgIndexes;
        return createArguments(tempFrameSlot, modeChange, modeChangeAppliesToAll, args, varArgIdx, signature);
    }

    private static CallArgumentsNode createArguments(FrameSlot tempFrameSlot, boolean modeChange, boolean modeChangeAppliesToAll, RSyntaxNode[] arguments, int[] varArgIndexes,
                    ArgumentsSignature signature) {
        RNode[] args = new RNode[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            if (tempFrameSlot != null && i == 0) {
                args[i] = new GetTempNode(tempFrameSlot, arguments[i]);
            } else {
                args[i] = arguments[i] == null ? null : RASTUtils.cloneNode(arguments[i].asRNode());
            }
        }
        return CallArgumentsNode.create(modeChange, modeChangeAppliesToAll, args, signature, varArgIndexes);
    }

    /**
     * The standard way to create a call to {@code function} with given arguments. If
     * {@code src == RSyntaxNode.EAGER_DEPARSE} we force a deparse.
     */
    public static RCallNode createCall(SourceSection src, RNode function, ArgumentsSignature signature, RSyntaxNode... arguments) {
        return RCallNodeGen.create(src, arguments, signature, function);
    }

    /**
     * Creates a call that reads its explicit arguments from the frame under given identifier. This
     * allows to invoke a function with argument(s) supplied by hand. Consider using
     * {@link com.oracle.truffle.r.nodes.function.call.RExplicitCallNode} instead.
     */
    public static RCallNode createExplicitCall(Object explicitArgsIdentifier) {
        return RCallNodeGen.create(RSyntaxNode.INTERNAL, explicitArgsIdentifier, null);
    }

    static boolean needsSplitting(RootCallTarget target) {
        RRootNode root = (RRootNode) target.getRootNode();
        return root.containsDispatch() || root.needsSplitting();
    }

    public static final class GetTempNode extends RNode {

        private final FrameSlot slot;
        private final RSyntaxNode arg;

        GetTempNode(FrameSlot slot, RSyntaxNode arg) {
            this.slot = slot;
            this.arg = arg;
        }

        @Override
        protected RSyntaxNode getRSyntaxNode() {
            return arg;
        }

        @Override
        public SourceSection getSourceSection() {
            return arg.getSourceSection();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return FrameSlotChangeMonitor.getObject(slot, frame);
            } catch (FrameSlotTypeException e) {
                throw RInternalError.shouldNotReachHere();
            }
        }
    }

    /**
     * Dispatches a call to a function for given arguments.
     */
    @ImportStatic(DSLConfig.class)
    public abstract static class FunctionDispatch extends Node {

        /**
         * Note: s3DefaultArguments is intended to carry default arguments from
         * {@link RCallNode#callGroupGeneric} if the R dispatch method has some. Currently this is
         * only the case for 'summary' group so this argument is either null or set to
         * {@link RArguments#SUMMARY_GROUP_DEFAULT_VALUE_NA_RM}
         */
        public abstract Object execute(VirtualFrame frame, RFunction function, Object varArgs, Object s3Args, Object s3DefaultArguments);

        protected static final int CACHE_SIZE = 4;

        private final RCallNode originalCall;
        private final AlteredArguments alteredArguments;
        private final boolean explicitArgs;

        private final FrameSlot tempFrameSlot;

        public FunctionDispatch(RCallNode originalCall, AlteredArguments alteredArguments, boolean explicitArgs, FrameSlot tempFrameSlot) {
            this.originalCall = originalCall;
            this.explicitArgs = explicitArgs;
            this.tempFrameSlot = tempFrameSlot;
            this.alteredArguments = alteredArguments;
        }

        public FunctionDispatch(RCallNode originalCall, boolean explicitArgs, FrameSlot tempFrameSlot) {
            this(originalCall, null, explicitArgs, tempFrameSlot);
        }

        protected LeafCallFunctionNode createCacheNode(RootCallTarget cachedTarget) {
            CompilerAsserts.neverPartOfCompilation();
            RRootNode root = (RRootNode) cachedTarget.getRootNode();
            FormalArguments formals = root.getFormalArguments();
            if (root instanceof RBuiltinRootNode) {
                RBuiltinRootNode builtinRoot = (RBuiltinRootNode) root;
                return new BuiltinCallNode(RBuiltinNode.inline(builtinRoot.getBuiltin()), builtinRoot.getBuiltin(), formals, originalCall, explicitArgs);
            } else {
                return new DispatchedCallNode(cachedTarget, originalCall);
            }
        }

        protected PrepareArguments createArguments(RootCallTarget cachedTarget, boolean noOpt) {
            RRootNode root = (RRootNode) cachedTarget.getRootNode();
            if (explicitArgs) {
                return PrepareArguments.createExplicit(root);
            } else {
                CallArgumentsNode args = originalCall.createArguments(tempFrameSlot, root.getBuiltin() == null, true, alteredArguments);
                return PrepareArguments.create(root, args, noOpt);
            }
        }

        protected PrepareArguments createArguments(RootCallTarget cachedTarget) {
            return createArguments(cachedTarget, false);
        }

        @Specialization(limit = "getCacheSize(CACHE_SIZE)", guards = "function.getTarget() == cachedTarget")
        protected Object dispatch(VirtualFrame frame, RFunction function, Object varArgs, Object s3Args, Object s3DefaultArguments,
                        @Cached("function.getTarget()") @SuppressWarnings("unused") RootCallTarget cachedTarget,
                        @Cached("createCacheNode(cachedTarget)") LeafCallFunctionNode leafCall,
                        @Cached("createArguments(cachedTarget)") PrepareArguments prepareArguments) {
            RArgsValuesAndNames orderedArguments = prepareArguments.execute(frame, (RArgsValuesAndNames) varArgs, (S3DefaultArguments) s3DefaultArguments, originalCall);
            return leafCall.execute(frame, function, orderedArguments, (S3Args) s3Args);
        }

        private static final class GenericCallEntry extends Node {
            private final RootCallTarget cachedTarget;
            @Child private LeafCallFunctionNode leafCall;
            @Child private PrepareArguments prepareArguments;

            GenericCallEntry(RootCallTarget cachedTarget, LeafCallFunctionNode leafCall, PrepareArguments prepareArguments) {
                this.cachedTarget = cachedTarget;
                this.leafCall = leafCall;
                this.prepareArguments = prepareArguments;
            }
        }

        /*
         * Use a TruffleBoundaryNode to be able to switch child nodes without invalidating the whole
         * method.
         */
        protected final class GenericCall extends TruffleBoundaryNode {

            @Child private GenericCallEntry entry;

            @TruffleBoundary
            public Object execute(MaterializedFrame materializedFrame, RFunction function, Object varArgs, Object s3Args, Object s3DefaultArguments) {
                GenericCallEntry e = entry;
                RootCallTarget cachedTarget = function.getTarget();
                if (e == null || e.cachedTarget != cachedTarget) {
                    entry = e = insert(new GenericCallEntry(cachedTarget, createCacheNode(cachedTarget), createArguments(cachedTarget)));
                }
                RArgsValuesAndNames orderedArguments = e.prepareArguments.execute(materializedFrame, (RArgsValuesAndNames) varArgs, (S3DefaultArguments) s3DefaultArguments, originalCall);
                return e.leafCall.execute(materializedFrame, function, orderedArguments, (S3Args) s3Args);
            }
        }

        protected GenericCall createGenericCall() {
            return new GenericCall();
        }

        @Specialization
        protected Object dispatchFallback(VirtualFrame frame, RFunction function, Object varArgs, Object s3Args, Object s3DefaultArguments,
                        @Cached("createGenericCall()") GenericCall generic) {
            return generic.execute(frame.materialize(), function, varArgs, s3Args, s3DefaultArguments);
        }
    }

    public abstract static class LeafCallNode extends RBaseNode {
        /**
         * The original {@link RSyntaxNode} this derives from.
         */
        protected final RCallNode originalCall;

        private LeafCallNode(RCallNode originalCall) {
            this.originalCall = originalCall;
        }

        @Override
        public RSyntaxNode getRSyntaxNode() {
            return originalCall;
        }

    }

    public abstract static class LeafCallFunctionNode extends LeafCallNode {

        protected LeafCallFunctionNode(RCallNode originalCall) {
            super(originalCall);
        }

        public abstract Object execute(VirtualFrame frame, RFunction currentFunction, RArgsValuesAndNames orderedArguments, S3Args s3Args);
    }

    /**
     * It executes a builtin node using its call method (not the execute), which casts the
     * arguments. Potential argument promises are evaluated before the execution.
     *
     * NB: The arguments are not cast here, but in the builtin.
     */
    @NodeInfo(cost = NodeCost.NONE)
    public static final class BuiltinCallNode extends LeafCallFunctionNode {

        @Child private RBuiltinNode builtin;
        /**
         * Evaluates potential promises in varArgs.
         */
        @Child private PromiseCheckHelperNode varArgsPromiseHelper;
        /**
         * Evaluates arg promises.
         */
        @Children private final PromiseHelperNode[] promiseHelpers;
        @Child private SetVisibilityNode visibility = SetVisibilityNode.create();

        // not using profiles to save overhead
        @CompilationFinal(dimensions = 1) private final boolean[] argEmptySeen;
        @CompilationFinal(dimensions = 1) private final boolean[] varArgSeen;
        @CompilationFinal(dimensions = 1) private final boolean[] nonWrapSeen;
        @CompilationFinal(dimensions = 1) private final boolean[] wrapSeen;

        private final FormalArguments formals;
        private final RBuiltinDescriptor builtinDescriptor;
        private final boolean explicitArgs;

        public BuiltinCallNode(RBuiltinNode builtin, RBuiltinDescriptor builtinDescriptor, FormalArguments formalArguments, RCallNode originalCall, boolean explicitArgs) {
            super(originalCall);
            this.builtin = builtin;
            this.builtinDescriptor = builtinDescriptor;
            this.explicitArgs = explicitArgs;
            this.formals = formalArguments;
            promiseHelpers = new PromiseHelperNode[formals.getLength()];
            argEmptySeen = new boolean[formals.getLength()];
            varArgSeen = new boolean[formals.getLength()];
            nonWrapSeen = new boolean[formals.getLength()];
            wrapSeen = new boolean[formals.getLength()];
        }

        @Override
        public RBaseNode getErrorContext() {
            return builtin.getErrorContext();
        }

        public RBuiltinNode getBuiltin() {
            return builtin;
        }

        public FormalArguments getFormals() {
            return formals;
        }

        @ExplodeLoop
        public Object[] forceArgPromises(VirtualFrame frame, Object[] args) {
            int argCount = formals.getLength();
            int varArgIndex = formals.getSignature().getVarArgIndex();
            Object[] result = new Object[argCount];
            for (int i = 0; i < argCount; i++) {
                Object arg = args[i];
                if (explicitArgs && arg == REmpty.instance) {
                    if (!argEmptySeen[i]) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        argEmptySeen[i] = true;
                    }
                    arg = formals.getInternalDefaultArgumentAt(i);
                }
                if (varArgIndex == i && arg instanceof RArgsValuesAndNames) {
                    if (!varArgSeen[i]) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        varArgSeen[i] = true;
                    }
                    RArgsValuesAndNames varArgs = (RArgsValuesAndNames) arg;
                    if (builtinDescriptor.evaluatesArg(i)) {
                        forcePromises(frame, varArgs);
                    } else {
                        wrapPromises(varArgs);
                    }
                } else {
                    if (builtinDescriptor.evaluatesArg(i)) {
                        if (arg instanceof RPromise) {
                            if (promiseHelpers[i] == null) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                promiseHelpers[i] = insert(new PromiseHelperNode());
                            }
                            arg = promiseHelpers[i].evaluate(frame, (RPromise) arg);
                        }
                    } else {
                        if (arg instanceof RPromise || arg instanceof RMissing) {
                            if (!nonWrapSeen[i]) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                nonWrapSeen[i] = true;
                            }
                        } else {
                            if (!wrapSeen[i]) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                wrapSeen[i] = true;
                            }
                            arg = createPromise(arg);
                        }
                    }
                }
                result[i] = arg;
            }
            return result;
        }

        private final VectorLengthProfile varArgProfile = VectorLengthProfile.create();

        private void forcePromises(VirtualFrame frame, RArgsValuesAndNames varArgs) {
            if (varArgsPromiseHelper == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                varArgsPromiseHelper = insert(new PromiseCheckHelperNode());
            }
            varArgProfile.profile(varArgs.getLength());
            int cachedLength = varArgProfile.getCachedLength();
            if (cachedLength >= 0) {
                forcePromisesUnrolled(frame, varArgs, cachedLength);
            } else {
                forcePromisesDynamic(frame, varArgs);
            }
        }

        @ExplodeLoop
        private void forcePromisesUnrolled(VirtualFrame frame, RArgsValuesAndNames varArgs, int length) {
            Object[] array = varArgs.getArguments();
            for (int i = 0; i < length; i++) {
                array[i] = varArgsPromiseHelper.checkEvaluate(frame, array[i]);
            }
        }

        private void forcePromisesDynamic(VirtualFrame frame, RArgsValuesAndNames varArgs) {
            Object[] array = varArgs.getArguments();
            for (int i = 0; i < array.length; i++) {
                array[i] = varArgsPromiseHelper.checkEvaluate(frame, array[i]);
            }
        }

        private static void wrapPromises(RArgsValuesAndNames varArgs) {
            Object[] array = varArgs.getArguments();
            for (int i = 0; i < array.length; i++) {
                Object arg = array[i];
                if (!(arg instanceof RPromise || arg instanceof RMissing)) {
                    array[i] = createPromise(arg);
                }
            }
        }

        @TruffleBoundary
        private static Object createPromise(Object arg) {
            return RDataFactory.createEvaluatedPromise(Closure.createPromiseClosure(ConstantNode.create(arg)), arg);
        }

        @Override
        public Object execute(VirtualFrame frame, RFunction currentFunction, RArgsValuesAndNames orderedArguments, S3Args s3Args) {
            Object result = builtin.call(frame, forceArgPromises(frame, orderedArguments.getArguments()));
            assert result != null : "builtins cannot return 'null': " + builtinDescriptor.getName();
            assert !(result instanceof RConnection) : "builtins cannot return connection': " + builtinDescriptor.getName();
            visibility.execute(frame, builtinDescriptor.getVisibility());
            return result;
        }
    }

    /**
     * It executes {@link RFastPathNode} first, if available. It also splits the function's call
     * target, if needed. Then it executes CallRFunctionNode if either RFastPathNode is not
     * available or it returns null
     */
    private static final class DispatchedCallNode extends LeafCallFunctionNode {

        @Child private CallRFunctionNode call;
        @Child private RFastPathNode fastPath;
        @Child private SetVisibilityNode visibility;

        private final RootCallTarget cachedTarget;
        private final FastPathFactory fastPathFactory;
        private final RVisibility fastPathVisibility;
        private final boolean containsDispatch;

        DispatchedCallNode(RootCallTarget cachedTarget, RCallNode originalCall) {
            super(originalCall);
            RRootNode root = (RRootNode) cachedTarget.getRootNode();
            this.cachedTarget = cachedTarget;
            this.fastPathFactory = root.getFastPath();
            this.fastPath = fastPathFactory == null ? null : fastPathFactory.create();
            this.fastPathVisibility = fastPathFactory == null ? null : fastPathFactory.getVisibility();
            this.visibility = fastPathFactory == null ? null : SetVisibilityNode.create();
            this.containsDispatch = root.containsDispatch();
        }

        @Override
        public Object execute(VirtualFrame frame, RFunction function, RArgsValuesAndNames orderedArguments, S3Args s3Args) {
            if (fastPath != null) {
                Object result = fastPath.execute(frame, orderedArguments.getArguments());
                if (result != null) {
                    assert fastPathVisibility != null;
                    visibility.execute(frame, fastPathVisibility);
                    return result;
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fastPath = null;
                visibility = null;
            }

            if (call == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                call = insert(CallRFunctionNode.create(cachedTarget));
                if (needsSplitting(cachedTarget)) {
                    if (!RContext.getInstance().getOption(RestrictForceSplitting)) {
                        call.getCallNode().cloneCallTarget();
                    }
                }
                if (containsDispatch) {
                    call.setNeedsCallerFrame();
                }
            }
            Object callerFrame = s3Args != null ? s3Args.callEnv : originalCall.getCallerFrame(frame);
            RCaller caller = originalCall.createCaller(frame, function);

            return call.execute(frame, function, caller, callerFrame, orderedArguments.getArguments(), orderedArguments.getSignature(), function.getEnclosingFrame(), s3Args);
        }
    }

    @Override
    public RSyntaxElement getSyntaxLHS() {
        return getFunction() == null ? RSyntaxLookup.createDummyLookup(RSyntaxNode.LAZY_DEPARSE, "FUN", true) : getFunction().asRSyntaxNode();
    }

    @Override
    public ArgumentsSignature getSyntaxSignature() {
        return signature == null ? ArgumentsSignature.empty(1) : signature;
    }

    @Override
    public RSyntaxElement[] getSyntaxArguments() {
        return arguments == null ? new RSyntaxElement[]{RSyntaxLookup.createDummyLookup(RSyntaxNode.LAZY_DEPARSE, "...", false)} : arguments;
    }

    public static final class ExplicitArgs {
        public final RArgsValuesAndNames args;
        public final RCaller caller;
        public final Object callerFrame;

        public ExplicitArgs(RArgsValuesAndNames args, RCaller caller, Object callerFrame) {
            this.args = args;
            this.caller = caller;
            this.callerFrame = callerFrame;
        }
    }

    /**
     * Represents the LHS of a possible foreign member call.
     */
    protected static final class DeferredFunctionValue {
        private final TruffleObject lhsReceiver;
        private final String lhsMember;

        protected DeferredFunctionValue(TruffleObject lhsReceiver, String lhsMember) {
            this.lhsReceiver = lhsReceiver;
            this.lhsMember = lhsMember;
        }

        public String getLHSMember() {
            return lhsMember;
        }

        public TruffleObject getLHSReceiver() {
            return lhsReceiver;
        }

    }

    /**
     * Encapsulates alternation to {@link RCallNode} original arguments so that we do not need to
     * create new {@link RCallNode}.
     */
    protected static final class AlteredArguments {
        public final RSyntaxNode[] arguments;
        public final int[] varArgIndexes;

        public AlteredArguments(RSyntaxNode[] arguments, int[] varArgIndexes) {
            this.arguments = arguments;
            this.varArgIndexes = varArgIndexes;
        }
    }

    public static Object createDeferredMemberAccess(TruffleObject object, String name) {
        return new DeferredFunctionValue(object, name);
    }

    @Override
    public String toString() {
        return "call: " + RDeparse.deparseSyntaxElement(this);
    }
}
