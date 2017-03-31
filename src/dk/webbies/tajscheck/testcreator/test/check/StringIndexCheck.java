package dk.webbies.tajscheck.testcreator.test.check;

/**
 * Created by erik1 on 06-12-2016.
 */
public class StringIndexCheck implements Check {
    private Check subCheck;

    public StringIndexCheck(Check subCheck) {
        this.subCheck = subCheck;
    }

    public Check getSubCheck() {
        return subCheck;
    }

    @Override
    public <T, A> T accept(CheckVisitorWithArgument<T, A> visitor, A a) {
        return visitor.visit(this, a);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StringIndexCheck that = (StringIndexCheck) o;

        return subCheck != null ? subCheck.equals(that.subCheck) : that.subCheck == null;
    }

    @Override
    public int hashCode() {
        return subCheck != null ? subCheck.hashCode() : 0;
    }
}
