//generated by parseq
package de.peeeq.wurstscript.jassAst;

class JassExprIntValImpl implements JassExprIntVal, JassAstElementIntern {
	JassExprIntValImpl(int val) {
		this.val = val;
	}

	private JassAstElement parent;
	public JassAstElement getParent() { return parent; }
	public void setParent(JassAstElement parent) {
		if (parent != null && this.parent != null) { 			throw new Error("Parent of " + this + " already set: " + this.parent + "\ntried to change to " + parent); 		}
		this.parent = parent;
	}

	private int val;
	public void setVal(int val) {
		this.val = val;
	} 
	public int getVal() { return val; }

	public JassAstElement get(int i) {
		switch (i) {
			default: throw new IllegalArgumentException("Index out of range: " + i);
		}
	}
	public int size() {
		return 0;
	}
	@Override public JassExprIntVal copy() {
		return new JassExprIntValImpl(val);
	}
	@Override public void accept(JassStmtLoop.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassStatements.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassExprAtomic.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassExpr.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassExprBinary.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassStmtSet.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassExprFunctionCall.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassProg.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassExprUnary.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassExprlist.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassFunction.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassStmtSetArray.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassExprVarArrayAccess.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassExprIntVal.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassStatement.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassFunctions.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassStmtReturn.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassStmtIf.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassStmtExitwhen.Visitor v) {
		v.visit(this);
	}
	@Override public void accept(JassStmtCall.Visitor v) {
		v.visit(this);
	}
	@Override public <T> T match(JassExprAtomic.Matcher<T> matcher) {
		return matcher.case_JassExprIntVal(this);
	}
	@Override public void match(JassExprAtomic.MatcherVoid matcher) {
		matcher.case_JassExprIntVal(this);
	}

	@Override public <T> T match(JassExpr.Matcher<T> matcher) {
		return matcher.case_JassExprIntVal(this);
	}
	@Override public void match(JassExpr.MatcherVoid matcher) {
		matcher.case_JassExprIntVal(this);
	}

	@Override public String toString() {
		return "JassExprIntVal(" + val+")";
	}
}
