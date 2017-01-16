package dk.webbies.tajscheck.paser;

import dk.brics.tajs.util.Collections;
import dk.webbies.tajscheck.paser.AST.*;
import dk.webbies.tajscheck.util.Pair;
import dk.webbies.tajscheck.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static dk.webbies.tajscheck.paser.AstBuilder.*;

/**
 * Created by erik1 on 01-11-2016.
 */
public class AstToStringVisitor implements ExpressionVisitor<Void>, StatementVisitor<Void> {
    private int ident = 0;
    private StringBuilder builder = new StringBuilder();

    @Override
    public Void visit(BinaryExpression binOp) {
        writeParenthesizedExpression(binOp.getLhs());
        write(" ");
        switch (binOp.getOperator()) {
            case LESS_THAN:
                write("<");
                break;
            case EQUAL:
                write("=");
                break;
            case PLUS:
                write("+");
                break;
            case MULT:
                write("*");
                break;
            case BITWISE_OR:
                write("|");
                break;
            case EQUAL_EQUAL_EQUAL:
                write("===");
                break;
            case OR:
                write("||");
                break;
            case AND:
                write("&&");
                break;
            case NOT_EQUAL_EQUAL:
                write("!==");
                break;
            case PLUS_EQUAL:
                write("+=");
                break;
            case INSTANCEOF:
                write("instanceof");
                break;
            case GREATER_THAN:
                write(">");
                break;
            case GREATER_THAN_EQUAL:
                write(">=");
                break;
            case LESS_THAN_EQUAL:
                write("<=");
                break;
            case BITWISE_AND:
                write("&");
                break;
            case MOD:
                write("%");
                break;
            case EQUAL_EQUAL:
                write("==");
                break;
            case MINUS:
                write("-");
                break;
            case BITWISE_XOR_EQUAL:
                write("^=");
                break;
            case MULT_EQUAL:
                write("*=");
                break;
            case DIV_EQUAL:
                write("/=");
                break;
            case UNSIGNED_RIGHT_SHIFT_EQUAL:
                write(">>>=");
                break;
            case RIGHT_SHIFT_EQUAL:
                write(">>=");
                break;
            case RIGHT_SHIFT:
                write(">>");
                break;
            case UNSIGNED_RIGHT_SHIFT:
                write(">>>");
                break;
            case LEFT_SHIFT:
                write("<<");
                break;
            case LEFT_SHIFT_EQUAL:
                write("<<=");
                break;
            case DIV:
                write("/");
                break;
            case IN:
                write("in");
                break;
            case NOT_EQUAL:
                write("!=");
                break;
            case BITWISE_OR_EQUAL:
                write("|=");
                break;
            case BITWISE_AND_EQUAL:
                write("&=");
                break;
            case MINUS_EQUAL:
                write("-=");
                break;
            case MOD_EQUAL:
                write("%=");
                break;
            case BITWISE_XOR:
                write('^');
                break;
            default:
                throw new RuntimeException("Yet unhandled operator: " + binOp.getOperator());
        }

        write(" ");

        writeParenthesizedExpression(binOp.getRhs());

        return null;
    }

    @Override
    public Void visit(BooleanLiteral bool) {
        write(Boolean.toString(bool.getBooleanValue()));
        return null;
    }

    @Override
    public Void visit(CallExpression call) {
        writeParenthesizedExpression(call.getFunction());
        writeArgs(call.getArgs());

        return null;
    }

    private void writeArgs(List<? extends Expression> args) {
        write("(");
        for (int i = 0; i < args.size(); i++) {
            Expression arg = args.get(i);
            if (i != 0) {
                write(", ");
            }
            arg.accept(this);
        }
        write(")");
    }

    @Override
    public Void visit(CommaExpression commaExpression) {
        write("(");
        List<Expression> expressions = commaExpression.getExpressions();
        for (int i = 0; i < expressions.size(); i++) {
            expressions.get(i).accept(this);
            if (i != expressions.size() - 1) {
                write(", ");
            }
        }
        write(")");
        return null;
    }

    @Override
    public Void visit(ConditionalExpression conditionalExpression) {
        writeParenthesizedExpression(conditionalExpression.getCondition());
        write(" ? ");
        writeParenthesizedExpression(conditionalExpression.getLeft());
        write(" : ");
        writeParenthesizedExpression(conditionalExpression.getRight());
        return null;
    }

    @Override
    public Void visit(FunctionExpression func) {
        write("function ");
        if (func.getName() != null) {
            write(func.getName().getName());
            write(" ");
        }
        writeArgs(func.getArguments());
        if (!explode(func.getBody()).isEmpty()) {
            write(" {\n");
            ident++;
            writeAsBlock(func.getBody());
            ident--;
            ident();
            write("}");
        } else {
            write(" {}");
        }
        return null;
    }

    private List<Statement> explode(Statement statement) {
        if (statement instanceof BlockStatement) {
            return ((BlockStatement) statement).getStatements().stream().map(this::explode).reduce(new ArrayList<>(), Util::reduceList);
        } else {
            return Collections.singletonList(statement);
        }
    }

    @Override
    public Void visit(Identifier identifier) {
        write(identifier.getName());
        return null;
    }

    @Override
    public Void visit(MemberExpression memberExpression) {
        writeParenthesizedExpression(memberExpression.getExpression());
        write(".");
        write(memberExpression.getProperty());
        return null;
    }

    @Override
    public Void visit(DynamicAccessExpression exp) {
        writeParenthesizedExpression(exp.getOperand());
        write("[");
        exp.getLookupKey().accept(this);
        write("]");
        return null;
    }

    @Override
    public Void visit(MethodCallExpression methodCallExpression) {
        new CallExpression(null, methodCallExpression.getMemberExpression(), methodCallExpression.getArgs()).accept(this);
        return null;
    }

    @Override
    public Void visit(NewExpression call) {
        write("new ");
        writeParenthesizedExpression(call.getOperand());
        writeArgs(call.getArgs());

        return null;
    }

    @Override
    public Void visit(NullLiteral nullLiteral) {
        write("null");
        return null;
    }

    @Override
    public Void visit(NumberLiteral numberLiteral) {
        double d = numberLiteral.getNumber();
        write(Util.toPrettyNumber(d));
        return null;
    }

    @Override
    public Void visit(ObjectLiteral object) {
        if (object.getProperties().isEmpty()) {
            write("{}");
        } else {
            write("{\n");
            ident++;
            for (int i = 0; i < object.getProperties().size(); i++) {
                ObjectLiteral.Property property = object.getProperties().get(i);
                ident();
                Expression expression = property.expression;
                if (expression instanceof SetterExpression) {
                    SetterExpression setter = (SetterExpression) expression;
                    write("set ");
                    string(property.name).accept(this);
                    write(" (");
                    write(setter.getParameter().getName());
                    write(")");
                    write(" {\n");
                    ident++;
                    writeAsBlock(setter.getBody());
                    ident--;
                    ident();
                    write("}");
                } else if (expression instanceof GetterExpression) {
                    GetterExpression getter = (GetterExpression) expression;
                    write("get ");
                    string(property.name).accept(this);
                    write(" ()");
                    write(" {\n");
                    ident++;
                    writeAsBlock(getter.getBody());
                    ident--;
                    ident();
                    write("}");
                } else {
                    string(property.name).accept(this);
                    write(": ");
                    expression.accept(this);
                }
                if (i != object.getProperties().size() - 1) {
                    write(", ");
                }
                write("\n");
            }

            ident--;
            ident();
            write("}");
        }
        return null;
    }

    @Override
    public Void visit(StringLiteral stringLiteral) {
        if (stringLiteral.location != null) {
            write(stringLiteral.toString());
            return null;
        }
        write("\"");
        List<String> collect = Arrays.stream(stringLiteral.getString().split(Pattern.quote("\\\""))).map(str -> str.replace("\"", "\\\"")).collect(Collectors.toList());

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < collect.size(); i++) {
            builder.append(collect.get(i));
            if (i != collect.size() - 1) {
                builder.append("\\\"");
            }
        }
        if (collect.size() == 0) {
            builder.append("\\\"");
        }

        write(builder.toString());
        write("\"");
        return null;
    }

    @Override
    public Void visit(ThisExpression thisExpression) {
        write("this");
        return null;
    }

    @Override
    public Void visit(UnaryExpression unary) {
        switch (unary.getOperator()) {
            case POST_PLUS_PLUS:
                writeParenthesizedExpression(unary.getExpression());
                write("++");
                return null;
            case POST_MINUS_MINUS:
                writeParenthesizedExpression(unary.getExpression());
                write("--");
                return null;

        }


        switch (unary.getOperator()) {
            case NOT:
                write("!");
                break;
            case TYPEOF:
                write("typeof ");
                break;
            case VOID:
                write("void ");
                break;
            case PLUS:
                write("+");
                break;
            case MINUS:
                write("-");
                break;
            case DELETE:
                write("delete ");
                break;
            case PRE_MINUS_MINUS:
                write("--");
                break;
            case PRE_PLUS_PLUS:
                write("++");
                break;
            case BITWISE_NOT:
                write("~");
                break;
            default:
                throw new RuntimeException("Yet unknown operator: " + unary.getOperator());
        }
        writeParenthesizedExpression(unary.getExpression());
        return null;
    }

    private void writeParenthesizedExpression(Expression exp) {
        if (exp instanceof BinaryExpression || exp instanceof UnaryExpression || exp instanceof ConditionalExpression || exp instanceof FunctionExpression || exp instanceof NumberLiteral) {
            write("(");
            exp.accept(this);
            write(")");
        } else {
            exp.accept(this);
        }
    }

    @Override
    public Void visit(UndefinedLiteral undefinedLiteral) {
        throw new RuntimeException();
    }

    @Override
    public Void visit(GetterExpression getter) {
        throw new RuntimeException();
    }

    @Override
    public Void visit(SetterExpression setter) {
        throw new RuntimeException();
    }

    @Override
    public Void visit(ArrayLiteral arrayLiteral) {
        write("[");
        List<? extends Expression> elements = arrayLiteral.getExpressions();
        for (int i = 0; i < elements.size(); i++) {
            elements.get(i).accept(this);
            if (i != elements.size() - 1) {
                write(", ");
            }
        }
        write("]");
        return null;
    }

    @Override
    public Void visit(RegExpExpression regExp) {
        write(regExp.getValue());
        return null;
    }

    @Override
    public Void visit(BlockStatement block) {
        throw new RuntimeException();
    }

    @Override
    public Void visit(BreakStatement breakStatement) {
        ident();
        if (breakStatement.getLabel() == null) {
            write("break;\n");
        } else {
            write("break ");
            write(breakStatement.getLabel());
            write(";\n");
        }
        return null;
    }

    @Override
    public Void visit(ContinueStatement continueStatement) {
        ident();
        write("continue;\n");
        return null;
    }

    @Override
    public Void visit(ExpressionStatement expressionStatement) {
        ident();
        expressionStatement.getExpression().accept(this);
        if (expressionStatement.getExpression() instanceof FunctionExpression) {
            write("\n");
        } else {
            write(";\n");
        }
        return null;
    }

    @Override
    public Void visit(ForStatement forStatement) {
        ident();

        write("for (");

        if (forStatement.getInitialize() instanceof BlockStatement && ((BlockStatement) forStatement.getInitialize()).getStatements().size() == 1) {
            forStatement = new ForStatement(
                    forStatement.location,
                    ((BlockStatement) forStatement.getInitialize()).getStatements().iterator().next(),
                    forStatement.getCondition(),
                    forStatement.getIncrement(),
                    forStatement.getBody()
            );
        }

        if (forStatement.getInitialize() instanceof VariableNode) {
            write("var ");
            ((VariableNode) forStatement.getInitialize()).getlValue().accept(this);
            write(" = ");
            ((VariableNode) forStatement.getInitialize()).getInit().accept(this);

            write("; ");
        } else if (forStatement.getInitialize() instanceof BlockStatement) {
            BlockStatement block = (BlockStatement) forStatement.getInitialize();
            List<Statement> statements = new ArrayList<>(block.getStatements());
            assert statements.stream().allMatch(VariableNode.class::isInstance);

            write("var ");

            for (int i = 0; i < statements.size(); i++) {
                VariableNode variable = (VariableNode) statements.get(i);

                variable.getlValue().accept(this);

                if (variable.getInit() != null && !(variable.getInit() instanceof UnaryExpression && ((UnaryExpression) variable.getInit()).getOperator() == Operator.VOID)) {
                    write(" = ");
                    variable.getInit().accept(this);
                }

                if (i != statements.size() - 1) {
                    write(", ");
                } else {
                    write(";");
                }
            }
        } else if (forStatement.getInitialize() instanceof ExpressionStatement) {
            ((ExpressionStatement) forStatement.getInitialize()).getExpression().accept(this);
            write(";");
        } else {
            throw new RuntimeException();
        }

        forStatement.getCondition().accept(this);

        write("; ");

        forStatement.getIncrement().accept(this);

        write(") {\n");
        ident++;

        writeAsBlock(forStatement.getBody());

        ident--;
        ident();
        write("} \n");

        return null;
    }

    @Override
    public Void visit(IfStatement ifStatement) {
        ident();
        write("if (");
        ifStatement.getCondition().accept(this);
        write(") {\n");
        ident++;
        Statement ifBranch = ifStatement.getIfBranch();
        writeAsBlock(ifBranch);
        ident--;
        ident();
        write("}");
        Statement elseBranch = ifStatement.getElseBranch();
        if (elseBranch != null) {
            write(" else { \n");
            ident++;
            writeAsBlock(elseBranch);
            ident--;
            ident();
            write("}");
        }
        write("\n");
        return null;
    }

    private void writeAsBlock(Statement statement) {
        explode(statement).forEach(this::accept);
    }

    @Override
    public Void visit(Return aReturn) {
        ident();
        if (aReturn.getExpression() != null && !(aReturn.getExpression() instanceof UnaryExpression && ((UnaryExpression) aReturn.getExpression()).getOperator() == Operator.VOID && ((UnaryExpression) aReturn.getExpression()).getExpression() instanceof NumberLiteral)) {
            write("return ");
            aReturn.getExpression().accept(this);
            write(";\n");
        } else {
            write("return;\n");
        }
        return null;
    }

    @Override
    public Void visit(SwitchStatement switchStatement) {
        ident();
        write("switch (");
        switchStatement.getExpression().accept(this);
        write(") {\n");
        ident++;
        for (Pair<Expression, Statement> pair : switchStatement.getCases()) {
            ident();
            write("case ");
            pair.getLeft().accept(this);
            write(":\n");
            ident++;
            writeAsBlock(pair.getRight());
            ident--;
        }

        if (switchStatement.getDefaultCase() != null) {
            ident();
            write("default: \n");
            ident++;
            writeAsBlock(switchStatement.getDefaultCase());
            ident--;
        }

        ident--;
        ident();
        write("}\n");
        return null;
    }

    @Override
    public Void visit(ThrowStatement throwStatement) {
        ident();

        write("throw ");
        throwStatement.getExpression().accept(this);
        write(";\n");

        return null;
    }

    @Override
    public Void visit(VariableNode variableNode) {
        ident();
        if (variableNode.getInit() != null && !(variableNode.getInit() instanceof UnaryExpression && ((UnaryExpression) variableNode.getInit()).getOperator() == Operator.VOID)) {
            write("var ");
            variableNode.getlValue().accept(this);
            write(" = ");
            variableNode.getInit().accept(this);
            write(";\n");
        } else {
            write("var ");
            variableNode.getlValue().accept(this);
            write(";\n");
        }
        return null;
    }


    @Override
    public Void visit(WhileStatement whileStatement) {
        ident();
        write("while (");
        whileStatement.getCondition().accept(this);
        write(") {\n");
        ident++;
        writeAsBlock(whileStatement.getBody());
        ident--;
        ident();
        write("} \n");
        return null;
    }

    @Override
    public Void visit(LabeledStatement labeledStatement) {
        write(labeledStatement.getName() + ":");
        if (labeledStatement.getStatement() instanceof BlockStatement) {
            write("{\n");
            ident++;
            writeAsBlock(labeledStatement.getStatement());
            ident--;
            ident();
            write("}\n");
        } else {
            writeAsBlock(labeledStatement.getStatement());
        }
        return null;
    }
    @Override
    public Void visit(ForInStatement forinStatement) {
        if (forinStatement.getInitializer() instanceof BlockStatement && ((BlockStatement) forinStatement.getInitializer()).getStatements().size() == 1) {
            forinStatement = new ForInStatement(
                    forinStatement.location,
                    ((BlockStatement) forinStatement.getInitializer()).getStatements().iterator().next(),
                    forinStatement.getCollection(),
                    forinStatement.getBody()
            );
        }

        ident();

        write("for (");

        if (forinStatement.getInitializer() instanceof VariableNode) {
            write("var ");
            ((VariableNode) forinStatement.getInitializer()).getlValue().accept(this);
        } else if (forinStatement.getInitializer() instanceof ExpressionStatement) {
            ((ExpressionStatement) forinStatement.getInitializer()).getExpression().accept(this);
        } else {
            throw new RuntimeException(forinStatement.getInitializer().getClass().toString());
        }

        write(" in ");

        forinStatement.getCollection().accept(this);

        write(") {\n");
        ident++;

        writeAsBlock(forinStatement.getBody());

        ident--;
        ident();
        write("} \n");

        return null;
    }

    @Override
    public Void visit(TryStatement tryStatement) {
        assert tryStatement.getCatchBlock() != null || tryStatement.getFinallyBlock() != null;
        ident();

        write("try {\n");

        ident++;
        writeAsBlock(tryStatement.getTryBlock());
        ident--;

        if (tryStatement.getCatchBlock() != null) {
            ident();
            write("} catch(" + tryStatement.getCatchBlock().getException().getName() + ") { \n");

            ident++;
            writeAsBlock(tryStatement.getCatchBlock().getBody());
            ident--;
        }
        ident();
        write("} ");
        if (tryStatement.getFinallyBlock() != null) {
            write("finally {");
            ident++;
            writeAsBlock(tryStatement.getFinallyBlock());
            ident--;
            ident();
            write("}" );
        }
        write("\n");

        return null;
    }

    @Override
    public Void visit(CatchStatement catchStatement) {
        throw new RuntimeException();
    }

    @Override
    public Void visit(CommentStatement commentStatement) {
        for (String comment : commentStatement.getComment().split("\n")) {
            writeLn("// " + comment);
        }

        return null;
    }

    public static String toString(Expression exp) {
        AstToStringVisitor visitor = new AstToStringVisitor();
        exp.accept(visitor);
        return visitor.builder.toString();
    }

    public static String toString(Statement stmt) {
        AstToStringVisitor visitor = new AstToStringVisitor();
        if (stmt instanceof BlockStatement) {
            visitor.writeAsBlock(stmt);
        } else {
            stmt.accept(visitor);
        }
        return visitor.builder.toString();
    }

    private void write(String s) {
        this.builder.append(s);
    }

    private void write(char c) {
        this.builder.append(c);
    }

    private void writeLn(String s) {
        ident();
        write(s);
        write("\n");
    }

    private void ident() {
        for (int i = 0; i < this.ident; i++) {
            builder.append("    ");
        }
    }

    public static String toString(List<Statement> program) {
        AstToStringVisitor visitor = new AstToStringVisitor();
        program.forEach(visitor::accept);
        return visitor.builder.toString();
    }

    private void accept(Statement statement) {
        statement.accept(this);
    }
}
