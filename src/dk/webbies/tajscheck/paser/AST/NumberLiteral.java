package dk.webbies.tajscheck.paser.AST;

import com.google.javascript.jscomp.parsing.parser.util.SourceRange;
import dk.webbies.tajscheck.paser.ExpressionVisitor;

/**
 * Created by erik1 on 01-09-2015.
 */
public class NumberLiteral extends Expression {
    private double number;

    public NumberLiteral(SourceRange location, double number) {
        super(location);
        this.number = number;
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public double getNumber() {
        return number;
    }
}
