package de.peeeq.wurstscript.intermediatelang.interpreter;

import de.peeeq.wurstio.jassinterpreter.InterpreterException;
import de.peeeq.wurstscript.ast.Annotation;
import de.peeeq.wurstscript.ast.HasModifier;
import de.peeeq.wurstscript.ast.Modifier;
import de.peeeq.wurstscript.attributes.CompileError;
import de.peeeq.wurstscript.gui.WurstGui;
import de.peeeq.wurstscript.intermediatelang.ILconst;
import de.peeeq.wurstscript.intermediatelang.ILconstInt;
import de.peeeq.wurstscript.intermediatelang.ILconstReal;
import de.peeeq.wurstscript.jassIm.*;
import de.peeeq.wurstscript.jassinterpreter.ReturnException;
import de.peeeq.wurstscript.parser.WPos;
import de.peeeq.wurstscript.utils.LineOffsets;
import de.peeeq.wurstscript.utils.Utils;
import org.eclipse.jdt.annotation.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ILInterpreter {
    private ImProg prog;
    private final ProgramState globalState;

    public ILInterpreter(ImProg prog, WurstGui gui, @Nullable File mapFile, ProgramState globalState) {
        this.prog = prog;
        this.globalState = globalState;
        globalState.addNativeProvider(new BuiltinFuncs(globalState));
        globalState.addNativeProvider(new NativeFunctions());
    }

    public ILInterpreter(ImProg prog, WurstGui gui, @Nullable File mapFile, boolean isCompiletime) {
        this(prog, gui, mapFile, new ProgramState(gui, prog, isCompiletime));
    }

    public static LocalState runFunc(ProgramState globalState, ImFunction f, @Nullable Element caller,
                                     ILconst... args) {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterpreterException(globalState, "Execution interrupted");
        }
        try {
            if (f.getParameters().size() != args.length) {
                throw new Error("wrong number of parameters when calling func " + f.getName() + "(" +
                        Arrays.stream(args).map(Object::toString).collect(Collectors.joining(", ")) + ")");
            }

            for (int i = 0; i < f.getParameters().size(); i++) {
                // TODO could do typecheck here
                args[i] = adjustTypeOfConstant(args[i], f.getParameters().get(i).getType());
            }

            if (isCompiletimeNative(f)) {
                return runBuiltinFunction(globalState, f, args);
            }

            if (f.isNative()) {
                return runBuiltinFunction(globalState, f, args);
            }

            LocalState localState = new LocalState();
            int i = 0;
            for (ImVar p : f.getParameters()) {
                localState.setVal(p, args[i]);
                i++;
            }

            globalState.pushStackframe(f, args, (caller == null ? f : caller).attrTrace().attrErrorPos());

            try {
                f.getBody().runStatements(globalState, localState);
                globalState.popStackframe();
            } catch (ReturnException e) {
                globalState.popStackframe();
                ILconst retVal = e.getVal();
                retVal = adjustTypeOfConstant(retVal, f.getReturnType());
                return localState.setReturnVal(retVal);
            }
            if (f.getReturnType() instanceof ImVoid) {
                return localState;
            }
            throw new InterpreterException("function " + f.getName() + " did not return any value...");
        } catch (InterpreterException e) {
            String msg = buildStacktrace(globalState, e);
            throw e.withStacktrace(msg);
        } catch (Exception e) {
            String msg = buildStacktrace(globalState, e);
            throw new InterpreterException(globalState.getLastStatement().attrTrace(), "You encountered a bug in the interpreter: " + e, e).withStacktrace(msg);
        }
    }

    private static String buildStacktrace(ProgramState globalState, Exception e) {
        StringBuilder err = new StringBuilder();
        try {
            WPos src = globalState.getLastStatement().attrTrace().attrSource();
            err.append("at : ").append(new File(src.getFile()).getName()).append(", line ").append(src.getLine()).append("\n");
        } catch (Exception _e) {
            // ignore
        }
        globalState.getStackFrames().appendTo(err);
        return err.toString();
    }

    @SuppressWarnings("null")
    private static ILconst adjustTypeOfConstant(@Nullable ILconst retVal, ImType expectedType) {
        if (retVal instanceof ILconstInt && isTypeReal(expectedType)) {
            ILconstInt retValI = (ILconstInt) retVal;
            retVal = new ILconstReal(retValI.getVal());
        }
        return retVal;
    }

    private static boolean isTypeReal(ImType t) {
        if (t instanceof ImSimpleType) {
            ImSimpleType st = (ImSimpleType) t;
            return st.getTypename().equals("real");
        }
        return false;
    }

    private static LocalState runBuiltinFunction(ProgramState globalState, ImFunction f, ILconst... args) {
        StringBuilder errors = new StringBuilder();
        for (NativesProvider natives : globalState.getNativeProviders()) {
            try {
                return new LocalState(natives.invoke(f.getName(), args));
            } catch (NoSuchNativeException e) {
                errors.append("\n").append(e.getMessage());
                // ignore
            }
        }
        ImStmt lastStatement = globalState.getLastStatement();
        String errorMessage = "function " + f.getName() + " cannot be used from the Wurst interpreter.\n" + errors;
        if (lastStatement != null) {
            WPos source = lastStatement.attrTrace().attrSource();
            globalState.getGui().sendError(new CompileError(source, errorMessage));
        } else {
            globalState.getGui().sendError(new CompileError(new WPos("", new LineOffsets(), 0, 0), errorMessage));
        }
        for (ILStackFrame sf : Utils.iterateReverse(globalState.getStackFrames().getStackFrames())) {
            globalState.getGui().sendError(sf.makeCompileError());
        }
        return new LocalState();
    }

    private static boolean isCompiletimeNative(ImFunction f) {
        if (f.getTrace() instanceof HasModifier) {
            HasModifier f2 = (HasModifier) f.getTrace();
            for (Modifier m : f2.getModifiers()) {
                if (m instanceof Annotation) {
                    Annotation annotation = (Annotation) m;
                    if (annotation.getAnnotationType().equals("@compiletimenative")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public LocalState executeFunction(String funcName, @Nullable Element trace) {
        globalState.resetStackframes();
        for (ImFunction f : prog.getFunctions()) {
            if (f.getName().equals(funcName)) {
                return runFunc(globalState, f, trace);
            }
        }

        throw new Error("no function with name " + funcName + "was found.");
    }

    public void runVoidFunc(ImFunction f, @Nullable Element trace) {
        globalState.resetStackframes();
        runFunc(globalState, f, trace);
    }

    public @Nullable ImStmt getLastStatement() {
        return globalState.getLastStatement();
    }

    public void writebackGlobalState(boolean injectObjects) {
        globalState.writeBack(injectObjects);

    }

    public ProgramState getGlobalState() {
        return globalState;
    }

    public void addNativeProvider(NativesProvider np) {
        globalState.addNativeProvider(np);
    }

    public void setProgram(ImProg imProg) {
        this.prog = imProg;
        this.getGlobalState().setProg(imProg);
        globalState.resetStackframes();
    }

    public ProgramState.StackTrace getStackFrames() {
        return globalState.getStackFrames();

    }

}
