package de.peeeq.wurstscript.attributes.prettyPrint;

import de.peeeq.wurstscript.gui.WurstGui;
import de.peeeq.wurstscript.gui.WurstGuiCliImpl;
import de.peeeq.wurstio.WurstCompilerJassImpl;
import de.peeeq.wurstscript.RunArgs;
import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.ast.CompilationUnit;

import org.eclipse.jdt.annotation.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.lang.StringUtils;

import static de.peeeq.wurstscript.utils.Utils.printElement;


public class PrettyUtils {

    /**
     * @param args
     */
    public static void pretty(String[] args) throws IOException {
        if (args.length == 0) {
            return;
        }
        String arg = args[0];
        if (args.equals("...")) {
            prettyAll(".");
        }
        if (arg.equals("tree")) {
            debug(args[1]);
            return;
        }

        prettify(arg);
    }

    private static void debug(String filename) {
        String contents = readFile(filename);
        CompilationUnit cu = parse(contents);

        walkTree(cu, 0);
    }

    private static void check(Element e, int indent) {
        System.out.println(StringUtils.repeat("\t", indent) + printElement(e));
    }

    private static @Nullable Element lastElement = null;
    private static void walkTree(Element e, int indent) {
        lastElement = e;
        check(e, indent);
        lastElement = null;
        for (int i = 0; i < e.size(); i++) {
            walkTree(e.get(i), indent+1);
        }
    }

    private static void prettyAll(String root) throws IOException {
        Files.walk(Paths.get(root))
                .filter(p -> p.toString().endsWith(".wurst"))
                .forEach(p -> prettify(p.toString()));
    }

    private static void prettify(String filename) {
        String contents = readFile(filename);
        CompilationUnit cu = parse(contents);

        Spacer spacer = new MaxOneSpacer();
        StringBuilder sb = new StringBuilder();
        cu.prettyPrint(spacer, sb, 0);

        System.out.println(sb.toString());
    }

    private static String readFile(String filename) {
        String everything = "";
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            everything = sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return everything;
    }

    private static CompilationUnit parse(String input) {
        WurstGui gui = new WurstGuiCliImpl();
        WurstCompilerJassImpl compiler = new WurstCompilerJassImpl(gui, null, new RunArgs("-prettyPrint"));
        return compiler.parse("test", new StringReader(input));
    }
}
