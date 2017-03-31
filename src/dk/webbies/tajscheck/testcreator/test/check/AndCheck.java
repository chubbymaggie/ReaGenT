package dk.webbies.tajscheck.testcreator.test.check;

import java.util.List;

/**
 * Created by erik1 on 14-11-2016.
 */
public class AndCheck implements Check {
    private final List<Check> checks;

    AndCheck(List<Check> checks) {
        this.checks = checks;
    }

    public List<Check> getChecks() {
        return checks;
    }

    @Override
    public <T, A> T accept(CheckVisitorWithArgument<T, A> visitor, A a) {
        return visitor.visit(this, a);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AndCheck andCheck = (AndCheck) o;

        return checks != null ? checks.equals(andCheck.checks) : andCheck.checks == null;
    }

    @Override
    public int hashCode() {
        return checks != null ? checks.hashCode() : 0;
    }
}
