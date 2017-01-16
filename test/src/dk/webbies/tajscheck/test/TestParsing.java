package dk.webbies.tajscheck.test;

import dk.webbies.tajscheck.parsespec.ParseDeclaration;
import dk.webbies.tajscheck.paser.AST.Statement;
import dk.webbies.tajscheck.paser.AstToStringVisitor;
import dk.webbies.tajscheck.paser.JavaScriptParser;
import dk.webbies.tajscheck.util.Util;
import org.hamcrest.core.Is;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * Created by erik1 on 03-01-2017.
 */
public class TestParsing {
    @Test
    public void testEscapedQuotes() throws Exception {
        testParse(
                "var test = \"\\\\[\" + whitespace + \"*(\" + identifier + \")(?:\" + whitespace +\n" +
                "                    // Operator (capture 2)\n" +
                "                    \"*([*^$|!~]?=)\" + whitespace +\n" +
                "                    // \"Attribute values must be CSS identifiers [capture 5] or strings [capture 3 or capture 4]\"\n" +
                "                    \"*(?:'((?:\\\\\\\\.|[^\\\\\\\\'])*)'|\\\"((?:\\\\\\\\.|[^\\\\\\\\\\\"])*)\\\"|(\" + identifier + \"))|)\" + whitespace +\n" +
                "                    \"*\\\\]\""
        );

    }

    @Test
    public void testNewOnExpression() throws Exception {
        testParse(
                "new ((function () {\n" +
                "\n" +
                "\n" +
                "})());"
        );
    }

    @Test
    public void labeledBreakWithNoWhile() throws Exception {
        testParse(
                "(function (global, factory) {}(this, (function (exports) { 'use strict';\n" +
                        "\tInterpolant.prototype = {\n" +
                        "\t\tevaluate: function( t ) {\n" +
                        "\t\t\tvalidate_interval: {\n" +
                        "\t\t\t\tseek: {\n" +
                        "\t\t\t\t\tlinear_scan: {\n" +
                        "\t\t\t\t\t\tforward_scan: if ( ! ( t < t1 ) ) {\n" +
                        "\t\t\t\t\t\t\tbreak linear_scan;\n" +
                        "\t\t\t\t\t\t}\n" +
                        "\t\t\t\t\t}\n" +
                        "\t\t\t\t} // seek\n" +
                        "\t\t\t} // validate_interval\n" +
                        "\t\t},\n" +
                        "\t}\n" +
                        "})));"
        );
    }

    @Test
    public void functionWithSemiColon() throws Exception {
        testParse(
                "var test = function () {\n" +
                "    ;\n" +
                "}"
        );

    }

    public static void testFile(String file) throws IOException {
        String script = Util.readFile(file);

        testParse(script);
    }

    private static void testParse(String content) {
        firstParsingComplete = false;
        JavaScriptParser parser = new JavaScriptParser(ParseDeclaration.Environment.ES5DOM);
        Statement iteration1Ast = parser.parse("name", content).toTSCreateAST().getBody();

        System.out.println("First parsing complete");
        firstParsingComplete = true;

        String iteration1String = AstToStringVisitor.toString(iteration1Ast);

        Statement iteration2Ast = parser.parse("name", iteration1String).toTSCreateAST().getBody();

        String iteration2String = AstToStringVisitor.toString(iteration2Ast);

        assertThat(iteration1String, Is.is(equalTo(iteration2String)));
    }

    public static boolean firstParsingComplete = false;
}
