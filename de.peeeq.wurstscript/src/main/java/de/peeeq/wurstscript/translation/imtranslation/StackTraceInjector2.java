package de.peeeq.wurstscript.translation.imtranslation;

import com.google.common.base.Preconditions;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import de.peeeq.wurstscript.WurstOperator;
import de.peeeq.wurstscript.jassIm.*;
import de.peeeq.wurstscript.parser.WPos;
import de.peeeq.wurstscript.utils.Utils;

import java.util.*;
import java.util.Map.Entry;

/**
 * Takes a program and inserts stack traces at error messages
 */
public class StackTraceInjector2 {

    private static final int MAX_STACKTRACE_SIZE = 20;
    private static final String WURST_STACK_TRACE = "wurstStackTrace";
    private ImProg prog;
    private ImVar stackSize;
    private ImVar stack;
    private Map<Element, Element> replacements = new LinkedHashMap<>();

    public StackTraceInjector2(ImProg prog) {
        this.prog = prog;
    }

    public void transform() {
        // @Deprecated final Multimap<ImFunction, ImError> errorPrints =
        // LinkedListMultimap.create();
        final Multimap<ImFunction, ImGetStackTrace> stackTraceGets = LinkedListMultimap.create();
        final Multimap<ImFunction, ImFunctionCall> calls = LinkedListMultimap.create();
        final Multimap<ImFunction, ImFunction> callRelation = LinkedListMultimap.create();
        final List<ImFuncRef> funcRefs = Lists.newArrayList();
        prog.accept(new ImProg.DefaultVisitor() {

            @Override
            public void visit(ImGetStackTrace e) {
                super.visit(e);
                stackTraceGets.put(e.getNearestFunc(), e);
            }

            @Override
            public void visit(ImFunctionCall c) {
                super.visit(c);
                calls.put(c.getFunc(), c);
                ImFunction caller = c.getNearestFunc();
                callRelation.put(caller, c.getFunc());
            }

            @Override
            public void visit(ImFuncRef imFuncRef) {
                super.visit(imFuncRef);
                funcRefs.add(imFuncRef);
            }
        });

        de.peeeq.wurstscript.ast.Element trace = prog.attrTrace();
        stackSize = JassIm.ImVar(trace, JassIm.ImSimpleType("integer"), "wurst_stack_depth", false);
        prog.getGlobals().add(stackSize);
        stack = JassIm.ImVar(trace, JassIm.ImArrayType("string"), "wurst_stack", false);
        prog.getGlobals().add(stack);
        prog.getGlobalInits().put(stackSize, JassIm.ImIntVal(0));

        Multimap<ImFunction, ImFunction> callRelationTr = Utils.transientClosure(callRelation);

        // find affected functions
        Set<ImFunction> affectedFuncs = Sets.newHashSet(stackTraceGets.keySet());
        for (Entry<ImFunction, ImFunction> e : callRelationTr.entries()) {
            if (stackTraceGets.containsKey(e.getValue())) {
                affectedFuncs.add(e.getKey());
            }
        }

        addStackTracePop(affectedFuncs);
        passStacktraceParams(calls, affectedFuncs);
        rewriteFuncRefs(funcRefs, affectedFuncs);
        rewriteErrorStatements(stackTraceGets);

        for (Entry<Element, Element> e : replacements.entrySet()) {
            e.getKey().replaceBy(e.getValue());
        }
    }

    /**
     * pops a stackframe when returning from an affected function
     */
    private void addStackTracePop(Set<ImFunction> affectedFuncs) {
        // add parameter to affected functions
        for (ImFunction f : affectedFuncs) {
            if (isMainOrConfig(f)) {
                continue;
            }
            Set<ImReturn> returns = new HashSet<>();
            f.getBody().accept(new ImStmts.DefaultVisitor() {
                @Override
                public void visit(ImReturn imReturn) {
                    super.visit(imReturn);
                    returns.add(imReturn);
                }
            });

            for (ImReturn ret : returns) {
                ImStmts stmts = JassIm.ImStmts();
                ImReturn newReturn;
                if (!containsAffectedFunctioncall(ret.getReturnValue())) {
                    newReturn = ret.copy();
                } else {
                    // temp = result
                    ImVar temp = JassIm.ImVar(ret.getTrace(), f.getReturnType(), "stackTrace_tempReturn", false);
                    f.getLocals().add(temp);
                    stmts.add(JassIm.ImSet(ret.getTrace(), temp, (ImExpr) ret.getReturnValue().copy()));
                    newReturn = JassIm.ImReturn(ret.getTrace(), JassIm.ImVarAccess(temp));
                }
                // stackSize = stackSize - 1
                stmts.add(decrement(ret.getTrace(), stackSize));
                stmts.add(newReturn);
                replacements.put(ret, JassIm.ImStatementExpr(stmts, JassIm.ImNull()));

            }

        }
    }

    private boolean containsAffectedFunctioncall(ImExprOpt ret) {
        boolean[] res = {false};
        ret.accept(new ImExprOpt.DefaultVisitor() {
            @Override
            public void visit(ImFunctionCall imFunctionCall) {
                super.visit(imFunctionCall);
                res[0] = true;
            }

            @Override
            public void visit(ImGetStackTrace imGetStackTrace) {
                super.visit(imGetStackTrace);
                res[0] = true;
            }

        });
        return res[0];
    }

    private boolean isMainOrConfig(ImFunction f) {
        Preconditions.checkNotNull(f);
        return f.getName().equals("main") || f.getName().equals("config");
    }

    private void passStacktraceParams(final Multimap<ImFunction, ImFunctionCall> calls, Set<ImFunction> affectedFuncs) {

        // pass the stacktrace parameter at all cals
        for (ImFunction f : affectedFuncs) {
            for (ImFunctionCall call : calls.get(f)) {
                ImFunction caller = call.getNearestFunc();
                de.peeeq.wurstscript.ast.Element trace = call.getTrace();
                ImStmts stmts = JassIm.ImStmts();
                if (isMainOrConfig(caller)) {
                    // reset stack and add name of main/config:
                    stmts.add(JassIm.ImSet(trace, stackSize, JassIm.ImIntVal(1)));
                    stmts.add(JassIm.ImSetArray(trace, stack, JassIm.ImIntVal(0), str(f.getName())));
                } else {
                    WPos source = call.attrTrace().attrErrorPos();
                    String callPos;
                    if (source.getFile().startsWith("<")) {
                        callPos = "";
                    } else {
                        callPos = source.printShort();
                    }
                    // stack[stackSize] = ...
                    stmts.add(JassIm.ImSetArray(trace, stack, JassIm.ImVarAccess(stackSize), str(callPos)));
                    // stackSize = stackSize + 1
                    stmts.add(increment(trace, stackSize));
                }


                replacements.put(call, JassIm.ImStatementExpr(stmts, call.copy()));
            }
        }

    }

    private ImStmt increment(de.peeeq.wurstscript.ast.Element trace, ImVar v) {
        return JassIm.ImSet(trace, v,
                JassIm.ImOperatorCall(WurstOperator.PLUS, JassIm.ImExprs(JassIm.ImVarAccess(v), JassIm.ImIntVal(1))));
    }

    private ImStmt decrement(de.peeeq.wurstscript.ast.Element trace, ImVar v) {
        return JassIm.ImSet(trace, v,
                JassIm.ImOperatorCall(WurstOperator.MINUS, JassIm.ImExprs(JassIm.ImVarAccess(v), JassIm.ImIntVal(1))));
    }

    private void rewriteFuncRefs(final List<ImFuncRef> funcRefs, Set<ImFunction> affectedFuncs) {
        // rewrite funcrefs
        for (ImFuncRef fr : funcRefs) {
            ImFunction f = fr.getFunc();
            if (!affectedFuncs.contains(f)) {
                continue;
            }

            ImFunction bridgeFunc = JassIm.ImFunction(f.getTrace(), "bridge_" + f.getName(), f.getParameters().copy(),
                    (ImType) f.getReturnType().copy(), JassIm.ImVars(), JassIm.ImStmts(), f.getFlags());
            prog.getFunctions().add(bridgeFunc);

            ImStmt stmt;
            ImExpr str = str(fr.attrTrace().attrSource().printShort());
            ImExprs args = JassIm.ImExprs();
            ImStmts body = bridgeFunc.getBody();
            de.peeeq.wurstscript.ast.Element trace = fr.attrTrace();
            if (trace.getParent() == null) {
                throw new RuntimeException("no trace");
            }
            // reset stack and add information for callback:
            body.add(JassIm.ImSet(trace, stackSize, JassIm.ImIntVal(1)));
            body.add(JassIm.ImSetArray(trace, stack, JassIm.ImIntVal(0), str));

            ImFunctionCall call = JassIm.ImFunctionCall(fr.attrTrace(), f, args, true, CallType.NORMAL);
            if (bridgeFunc.getReturnType() instanceof ImVoid) {
                stmt = call;
            } else {
                stmt = JassIm.ImReturn(fr.attrTrace(), call);
            }
            body.add(stmt);

            fr.setFunc(bridgeFunc);
        }
    }

    private void rewriteErrorStatements(final Multimap<ImFunction, ImGetStackTrace> stackTraceGets) {
        // rewrite error statements
        for (Entry<ImFunction, ImGetStackTrace> e : stackTraceGets.entries()) {
            ImFunction f = e.getKey();
            ImGetStackTrace s = e.getValue();

            de.peeeq.wurstscript.ast.Element trace = s.attrTrace();
            ImVar traceStr = JassIm.ImVar(trace, JassIm.ImSimpleType("string"), "stacktraceStr", false);
            f.getLocals().add(traceStr);
            ImVar traceI = JassIm.ImVar(trace, JassIm.ImSimpleType("integer"), "stacktraceIndex", false);
            f.getLocals().add(traceI);
            ImVar traceLimit = JassIm.ImVar(trace, JassIm.ImSimpleType("integer"), "stacktraceLimit", false);
            f.getLocals().add(traceLimit);
            ImStmts stmts = JassIm.ImStmts();
            stmts.add(JassIm.ImSet(trace, traceStr, JassIm.ImStringVal("")));
            stmts.add(JassIm.ImSet(trace, traceI, JassIm.ImVarAccess(stackSize)));
            stmts.add(JassIm.ImSet(trace, traceLimit, JassIm.ImIntVal(0)));
            ImStmts loopBody = JassIm.ImStmts();
            stmts.add(JassIm.ImLoop(trace, loopBody));
            // i = i - 1
            loopBody.add(decrement(trace, traceI));
            // limit = limit + 1
            loopBody.add(increment(trace, traceLimit));
            // exitwhen limit > 20
            loopBody.add(JassIm.ImExitwhen(trace, JassIm.ImOperatorCall(WurstOperator.GREATER,
                    JassIm.ImExprs(JassIm.ImVarAccess(traceLimit), JassIm.ImIntVal(MAX_STACKTRACE_SIZE)))));
            // exitwhen i < 0
            loopBody.add(JassIm.ImExitwhen(trace, JassIm.ImOperatorCall(WurstOperator.LESS,
                    JassIm.ImExprs(JassIm.ImVarAccess(traceI), JassIm.ImIntVal(0)))));
            // s = s + "\n " + stack[i]
            loopBody.add(JassIm.ImSet(trace, traceStr, JassIm.ImOperatorCall(WurstOperator.PLUS,
                    JassIm.ImExprs(JassIm.ImVarAccess(traceStr),
                            JassIm.ImOperatorCall(WurstOperator.PLUS, JassIm.ImExprs(JassIm.ImStringVal("\n   "),
                                    JassIm.ImVarArrayAccess(stack, JassIm.ImVarAccess(traceI))))))));

            replacements.put(s, JassIm.ImStatementExpr(stmts, JassIm.ImVarAccess(traceStr)));
        }
    }

    private ImExpr str(String s) {
        return JassIm.ImStringVal(s);
    }

}
