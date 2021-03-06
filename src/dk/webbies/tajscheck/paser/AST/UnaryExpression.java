package dk.webbies.tajscheck.paser.AST;

import com.google.javascript.jscomp.parsing.parser.util.SourceRange;
import dk.webbies.tajscheck.paser.ExpressionVisitor;

/**
 * Created by Erik Krogh Kristensen on 06-09-2015.
 */
public class UnaryExpression extends Expression {
    private final Operator operator;
    private final Expression expression;
    private boolean postfix;

    public UnaryExpression(SourceRange loc, Operator operator, Expression expression, boolean postfix) {
        super(loc);
        this.operator = operator;
        this.expression = expression;
        this.postfix = postfix;
    }

    public Operator getOperator() {
        return operator;
    }

    public Expression getExpression() {
        return expression;
    }

    public boolean isPostfix() {
        return postfix;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
