package dk.webbies.tajscheck;

import dk.au.cs.casa.typescript.types.Type;

/**
 * Created by erik1 on 11-11-2016.
 */
public class TypeWithParameters {
    private final Type type;
    private final ParameterMap parameterMap;

    public TypeWithParameters(Type type, ParameterMap parameterMap) {
        this.type = type;
        this.parameterMap = parameterMap;
    }

    public Type getType() {
        return type;
    }

    public ParameterMap getParameterMap() {
        return parameterMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeWithParameters that = (TypeWithParameters) o;

        if (!type.equals(that.type)) return false;
        return parameterMap.equals(that.parameterMap);

    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + parameterMap.hashCode();
        return result;
    }
}
