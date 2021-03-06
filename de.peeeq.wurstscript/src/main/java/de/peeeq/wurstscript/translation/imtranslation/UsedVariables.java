package de.peeeq.wurstscript.translation.imtranslation;

import com.google.common.collect.Sets;
import de.peeeq.wurstscript.jassIm.*;

import java.util.Set;

public class UsedVariables {

    public static Set<ImVar> calculate(ImFunction f) {
        final Set<ImVar> result = Sets.newLinkedHashSet();
        f.accept(new ImFunction.DefaultVisitor() {
            @Override
            public void visit(ImSet e) {
                super.visit(e);
                result.add(e.getLeft());
            }

            @Override
            public void visit(ImSetArrayTuple e) {
                super.visit(e);
                result.add(e.getLeft());
            }

            @Override
            public void visit(ImSetArray e) {
                super.visit(e);
                result.add(e.getLeft());
            }

            @Override
            public void visit(ImSetTuple e) {
                super.visit(e);
                result.add(e.getLeft());
            }

            @Override
            public void visit(ImVarAccess e) {
                super.visit(e);
                result.add(e.getVar());
            }

            @Override
            public void visit(ImVarArrayAccess e) {
                super.visit(e);
                result.add(e.getVar());
            }
        });
        return result;
    }

    public static Set<ImVar> calculateReadVars(ImFunction f) {
        final Set<ImVar> result = Sets.newLinkedHashSet();
        f.accept(new ImFunction.DefaultVisitor() {
            @Override
            public void visit(ImVarAccess e) {
                super.visit(e);
                result.add(e.getVar());
            }

            @Override
            public void visit(ImVarArrayAccess e) {
                super.visit(e);
                result.add(e.getVar());
            }
        });
        return result;
    }
}
