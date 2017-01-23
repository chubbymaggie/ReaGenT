package dk.webbies.tajscheck.typeutil.typeContext;

import dk.au.cs.casa.typescript.SpecReader;
import dk.au.cs.casa.typescript.types.Type;
import dk.au.cs.casa.typescript.types.TypeParameterType;
import dk.webbies.tajscheck.TypeWithContext;
import dk.webbies.tajscheck.benchmarks.Benchmark;
import dk.webbies.tajscheck.typeutil.FreeGenericsFinder;
import dk.webbies.tajscheck.typeutil.TypesUtil;
import dk.webbies.tajscheck.util.Util;

import java.util.*;

/**
 * Created by erik1 on 12-01-2017.
 */
public class OptimizingTypeContext implements TypeContext {
    private final Map<TypeParameterType, Type> map;
    private final Type thisType;
    public final Benchmark bench;

    public OptimizingTypeContext(Benchmark bench) {
        this.bench = bench;
        this.map = Collections.emptyMap();
        this.thisType = null;
    }

    private OptimizingTypeContext(Map<TypeParameterType, Type> map, Type classType, Benchmark bench) {
        this.map = map;
        this.thisType = classType;
        this.bench = bench;
    }

    @Override
    public OptimizingTypeContext append(Map<TypeParameterType, Type> newParameters) {
        Map<TypeParameterType, Type> newMap = new HashMap<>(this.map);
        newMap.putAll(newParameters);
        return new OptimizingTypeContext(newMap, this.thisType, bench);
    }

    @Override
    public OptimizingTypeContext withThisType(Type thisType) {
        if (thisType == null || this.thisType == null) {
            return new OptimizingTypeContext(this.map, thisType, bench);
        }
        Set<Type> baseTypes = TypesUtil.getAllBaseTypes(this.thisType, new HashSet<>());

        if (baseTypes.contains(thisType)) {
            return this;
        } else {
            return new OptimizingTypeContext(this.map, thisType, bench);
        }
    }

    @Override
    public boolean containsKey(TypeParameterType parameter) {
        return map.containsKey(parameter);
    }

    @Override
    public TypeWithContext get(TypeParameterType parameter) {
        Type type = map.get(parameter);
        if (type == null) {
            return null;
        }
        return new TypeWithContext(type, this);
    }


    @Override
    public Map<TypeParameterType, Type> getMap() {
        return map;
    }

    @Override
    public Type getThisType() {
        return thisType;
    }

    @Override
    public TypeContext append(TypeContext other) {
        OptimizingTypeContext result = append(other.getMap());
        if (other instanceof OptimizingTypeContext) {
            return result;
        } else {
            throw new RuntimeException();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OptimizingTypeContext that = (OptimizingTypeContext) o;

        if (map != null ? !map.equals(that.map) : that.map != null) return false;
        if (thisType != null ? !thisType.equals(that.thisType) : that.thisType != null) return false;
        return bench != null ? bench.equals(that.bench) : that.bench == null;
    }

    @Override
    public int hashCode() {
        int result = map != null ? map.hashCode() : 0;
        result = 31 * result + (thisType != null ? thisType.hashCode() : 0);
        result = 31 * result + (bench != null ? bench.hashCode() : 0);
        return result;
    }

    @Override
    public OptimizingTypeContext optimizeTypeParameters(Type baseType, FreeGenericsFinder freeGenericsFinder) {
        if (bench.options.disableSizeOptimization) {
            return this;
        }
        OptimizingTypeContext clone = this.append(Collections.emptyMap());

        Set<TypeParameterType> reachable = new HashSet<>();

        ArrayList<Type> typesToTest = new ArrayList<>(Collections.singletonList(baseType));

        for (Type type : typesToTest) {
            reachable.addAll(freeGenericsFinder.findFreeGenerics(type));
        }

        clone.map.keySet().retainAll(reachable);

        boolean progress = true;
        while (progress) {
            progress = false;
            Set<TypeParameterType> extraReachable = new HashSet<>();
            for (Type type : clone.map.values()) {
                extraReachable.addAll(freeGenericsFinder.findFreeGenerics(type));
            }
            for (TypeParameterType parameterType : extraReachable) {
                if (!reachable.contains(parameterType) && this.map.containsKey(parameterType)) {
                    reachable.add(parameterType);
                    clone.map.put(parameterType, this.map.get(parameterType));
                    progress = true;
                }
            }
        }

        if (bench.options.combineAllUnconstrainedGenerics) {
            boolean foundShortcut = false;
            for (Map.Entry<TypeParameterType, Type> entry : new HashMap<>(clone.map).entrySet()) {
                if (entry.getValue() instanceof TypeParameterType) {
                    if (!reachable.contains(entry.getValue())) {
                        foundShortcut = true;
                        clone.map.remove(entry.getKey());
                    }
                }
            }
            if (foundShortcut) {
                return clone.optimizeTypeParameters(baseType, freeGenericsFinder);
            }
        }

        if (clone.thisType != null) {
            if (!freeGenericsFinder.isThisTypeVisible(baseType) && clone.map.values().stream().noneMatch(freeGenericsFinder::isThisTypeVisible)) {
                clone = clone.withThisType(null);
            }
        }
        return clone;
    }

    private static final TypeParameterType freeParameterType = new TypeParameterType();
    static {
        freeParameterType.setConstraint(SpecReader.makeEmptySyntheticInterfaceType());
    }

    @Override
    public Benchmark getBenchmark() {
        return bench;
    }
}
