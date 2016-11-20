package dk.webbies.tajscheck.testcreator.test;

import dk.au.cs.casa.typescript.types.Type;
import dk.webbies.tajscheck.ParameterMap;

import java.util.Collections;
import java.util.HashMap;

/**
 * Created by erik1 on 02-11-2016.
 */
public class LoadModuleTest extends Test {
    private final String module;

    public LoadModuleTest(String module, Type typeToTest) {
        super(Collections.EMPTY_LIST, Collections.EMPTY_LIST, typeToTest, "require(\"" + module + "\")", new ParameterMap());
        this.module = module;
    }

    public String getModule() {
        return module;
    }

    @Override
    public boolean equalsNoPath(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LoadModuleTest test = (LoadModuleTest) o;
        if (!test.module.equals(this.module)) return false;
        return super.equalsNoPathBase(test);
    }

    @Override
    public int hashCodeNoPath() {
        return super.hashCodeNoPathBase() * this.module.hashCode();
    }

    @Override
    public <T> T accept(TestVisitor<T> visitor) {
        return visitor.visit(this);
    }
}