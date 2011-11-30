package de.peeeq.wurstscript.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import de.peeeq.wurstscript.utils.Utils;

public class ArrayTests extends PscriptTest {

	
	@Test
	public void testArray1() {
		assertOk("testArray1", true, 
				"int array blub",
				"init {",
				"	blub[5] = 3",
				"	if blub[5] == 3 {",
				"		testSuccess()",	
				"	}",
				"}");
	}
	
	@Test
	public void testArray_jass() {
		String[] lines = {
				"native testSuccess takes nothing returns nothing",
				"native testFail takes string s returns nothing",

				"globals",
				"	integer array blub",
				"endglobals",

				"function foo takes nothing returns nothing ",
				"	set blub[5] = 3",
				"	if blub[5] == 3 then",
				"		call testSuccess()",
				"	endif",
				"endfunction",
				
				"package test {",
				"	init {",
				"		foo()",
				"	} ",
				"	",
				"	",
				"}",
				""
		};
		testAssertOk("testArray_jass", true, Utils.join(lines, "\n"));
	}
	
	

	public void assertOk(String name, boolean executeProg, String ... input) {
		String prog = "package test {\n" +
				"native testFail(string msg)\n" +
				"native testSuccess()\n" +
				Utils.join(input, "\n") + "\n" +
				"}\n";
		System.out.println(prog);
		testAssertOk(name, executeProg, prog);
	}

}