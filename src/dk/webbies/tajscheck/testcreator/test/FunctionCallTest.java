package dk.webbies.tajscheck.testcreator.test;

import dk.au.cs.casa.typescript.types.Type;
import dk.au.cs.casa.typescript.types.TypeParameterType;
import dk.webbies.tajscheck.ParameterMap;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by erik1 on 03-11-2016.
 */
public class FunctionCallTest extends Test {
    private final Type function;
    private List<Type> parameters;

    public FunctionCallTest(Type function, List<Type> parameters, Type returnType, String path, ParameterMap parameterMap) {
        super(Collections.singletonList(function), parameters, returnType, path + "()", parameterMap);
        this.function = function;
        this.parameters = parameters;
    }


    public List<Type> getParameters() {
        return parameters;
    }

    public Type getFunction() {
        return function;
    }

    @Override
    public boolean equalsNoPath(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return super.equalsNoPathBase((Test) o);
    }

    @Override
    public int hashCodeNoPath() {
        return super.hashCodeNoPathBase();
    }

    @Override
    public <T> T accept(TestVisitor<T> visitor) {
        return visitor.visit(this);
    }
}