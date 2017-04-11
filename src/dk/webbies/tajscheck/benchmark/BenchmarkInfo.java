package dk.webbies.tajscheck.benchmark;

import dk.au.cs.casa.typescript.SpecReader;
import dk.au.cs.casa.typescript.types.*;
import dk.webbies.tajscheck.parsespec.ParseDeclaration;
import dk.webbies.tajscheck.typeutil.TypesUtil;
import dk.webbies.tajscheck.util.Util;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by erik1 on 29-01-2017.
 */
public class BenchmarkInfo {
    public final Benchmark bench;
    public final Type typeToTest;
    public final Set<Type> nativeTypes;
    public final FreeGenericsFinder freeGenericsFinder;
    public final Map<Type, String> typeNames;
    public final TypeParameterIndexer typeParameterIndexer;
    public final CheckOptions options;
    private SpecReader spec;
    private final Set<Type> globalProperties;

    private final Map<Class<?>, Map<String, Object>> attributes = new HashMap<>();

    private BenchmarkInfo(Benchmark bench, Type typeToTest, Set<Type> nativeTypes, FreeGenericsFinder freeGenericsFinder, Map<Type, String> typeNames, TypeParameterIndexer typeParameterIndexer, Set<Type> globalProperties, SpecReader spec) {
        this.bench = bench;
        this.typeToTest = typeToTest;
        this.nativeTypes = nativeTypes;
        this.freeGenericsFinder = freeGenericsFinder;
        this.typeNames = typeNames;
        this.typeParameterIndexer = typeParameterIndexer;
        this.globalProperties = globalProperties;
        this.options = bench.options;
        this.spec = spec;
    }

    public SpecReader getSpec() {
        return spec;
    }

    public static BenchmarkInfo create(Benchmark bench) {
        SpecReader spec = ParseDeclaration.getTypeSpecification(bench.environment, Collections.singletonList(bench.dTSFile));

        SpecReader emptySpec = ParseDeclaration.getTypeSpecification(bench.environment, new ArrayList<>());

        Set<Type> nativeTypes = TypesUtil.collectNativeTypes(spec, emptySpec);

        Map<Type, String> typeNames = ParseDeclaration.getTypeNamesMap(spec);

        Type typeToTest = getTypeToTest(bench, spec, typeNames);

        FreeGenericsFinder freeGenericsFinder = new FreeGenericsFinder(typeToTest);

        TypeParameterIndexer typeParameterIndexer = new TypeParameterIndexer(bench.options);

        Set<Type> globalProperties = ((InterfaceType) spec.getGlobal()).getDeclaredProperties().values().stream().map(prop -> {
            if (prop instanceof ReferenceType) {
                return ((ReferenceType) prop).getTarget();
            } else {
                return prop;
            }
        }).collect(Collectors.toSet());

        return new BenchmarkInfo(bench, typeToTest, nativeTypes, freeGenericsFinder, typeNames, typeParameterIndexer, globalProperties, spec);
    }

    private static Type getTypeToTest(Benchmark bench, SpecReader spec, Map<Type, String> typeNames) {
        Type result = ((InterfaceType) spec.getGlobal()).getDeclaredProperties().get(bench.module);

        if (result == null) {
            throw new RuntimeException("Module: " + bench.module + " not found in benchmark");
        }

        // Various fixes, to transform the types into something more consistent (+ workarounds).
        applyTypeFixes(bench, typeNames, result);


        // Fixing if the top-level export is a class, sometimes we can an interface with a prototype property instead of the actual class.
        if (result instanceof InterfaceType) {
            InterfaceType inter = (InterfaceType) result;
            if (inter.getDeclaredCallSignatures().size() + inter.getDeclaredConstructSignatures().size() > 0) {
                if (inter.getDeclaredProperties().keySet().contains("prototype") && inter.getDeclaredProperties().get("prototype") instanceof ClassType) {
                    return inter.getDeclaredProperties().get("prototype");
                }
            }
        }

        return result;
    }

    private static void applyTypeFixes(Benchmark bench, Map<Type, String> typeNames, Type result) {
        List<Type> allTypes = new ArrayList<>(TypesUtil.collectAllTypes(result));
        for (Type type : allTypes) {
            // splitting unions
            if (bench.options.splitUnions) {
                if (type instanceof InterfaceType) {
                    InterfaceType inter = (InterfaceType) type;
                    inter.setDeclaredCallSignatures(TypesUtil.splitSignatures(inter.getDeclaredCallSignatures()));
                    inter.setDeclaredConstructSignatures(TypesUtil.splitSignatures(inter.getDeclaredConstructSignatures()));
                } else if (type instanceof GenericType) {
                    GenericType inter = (GenericType) type;
                    inter.setDeclaredCallSignatures(TypesUtil.splitSignatures(inter.getDeclaredCallSignatures()));
                    inter.setDeclaredConstructSignatures(TypesUtil.splitSignatures(inter.getDeclaredConstructSignatures()));
                }
            }

            // names starting with underscore has a bug; there are too many underscores.
            if (type instanceof InterfaceType) {
                ((InterfaceType) type).setDeclaredProperties(fixUnderscoreNames(((InterfaceType) type).getDeclaredProperties()));
            } else if (type instanceof GenericType) {
                ((GenericType) type).setDeclaredProperties(fixUnderscoreNames(((GenericType) type).getDeclaredProperties()));
            } else if (type instanceof ClassType) {
                ((ClassType) type).setStaticProperties(fixUnderscoreNames(((ClassType) type).getStaticProperties()));
                ((ClassType) type).setInstanceProperties(fixUnderscoreNames(((ClassType) type).getInstanceProperties()));
            }


            // Setting the instance of a class to an existing instance instead of creating a new.
            if (type instanceof ClassInstanceType) {
                ((ClassType) ((ClassInstanceType) type).getClassType()).instance = (ClassInstanceType) type;
            }

            // Collapsing nested unions
            if (type instanceof UnionType) {
                UnionType union = (UnionType) type;
                HashSet<UnionType> es = new HashSet<>(Collections.singletonList(union));
                union.setElements(collectAllUnionElements(union.getElements(), es));
            }

            // An intersection of functions can be represented in a better way (to allow detecting overloads).
            if (type instanceof IntersectionType) {
                IntersectionType intersection = (IntersectionType) type;
                assert !intersection.getElements().isEmpty();
                if (intersection.getElements().stream().allMatch(t -> {
                    if (!(t instanceof InterfaceType)) {
                        return false;
                    }
                    InterfaceType inter = (InterfaceType) t;
                    if (!inter.getDeclaredProperties().isEmpty()) {
                        return false;
                    }
                    if (inter.getDeclaredNumberIndexType() != null) {
                        return false;
                    }
                    if (inter.getDeclaredStringIndexType() != null) {
                        return false;
                    }
                    if (!inter.getDeclaredConstructSignatures().isEmpty()) {
                        return false;
                    }
                    if (inter.getDeclaredCallSignatures().isEmpty()) {
                        return false;
                    }
                    return true;
                })) {
                    InterfaceType combinedInter = SpecReader.makeEmptySyntheticInterfaceType();

                    String name = typeNames.get(type);
                    typeNames.put(combinedInter, name +".[mergedIntersection]");

                    for (InterfaceType inter : Util.cast(InterfaceType.class, intersection.getElements())) {
                        combinedInter.getDeclaredCallSignatures().addAll(inter.getDeclaredCallSignatures());
                    }
                    intersection.setElements(Collections.singletonList(combinedInter));
                }
            }
        }

        // It is only Void, if it is a function-return.
        for (Type type : allTypes) {
            if (type instanceof SimpleType && ((SimpleType) type).getKind() == SimpleTypeKind.Void) {
                ((SimpleType) type).setKind(SimpleTypeKind.Undefined);
            }
        }
        for (Type type : allTypes) {
            if (type instanceof InterfaceType) {
                InterfaceType inter = (InterfaceType) type;
                setUndefinedReturnToVoid(inter.getDeclaredCallSignatures());
                setUndefinedReturnToVoid(inter.getDeclaredConstructSignatures());
            } else if (type instanceof GenericType) {
                GenericType inter = (GenericType) type;
                setUndefinedReturnToVoid(inter.getDeclaredCallSignatures());
                setUndefinedReturnToVoid(inter.getDeclaredConstructSignatures());
            }
        }


        // Combining type-arguments, that are identical (have the same constraint). We can however only do that if it is not referenced anywhere.
        if (bench.options.combineAllUnboundGenerics) {
            Set<TypeParameterType> parameters = new HashSet<>();
            Set<TypeParameterType> arguments = new HashSet<>();
            for (Type type : allTypes) {
                if (type instanceof InterfaceType) {
                    parameters.addAll(Util.cast(TypeParameterType.class, ((InterfaceType) type).getTypeParameters()));
                } else if (type instanceof ClassInstanceType) {
                    parameters.addAll(Util.cast(TypeParameterType.class, ((ClassType) ((ClassInstanceType) type).getClassType()).getInstanceType().getTypeParameters()));
                } else if (type instanceof ClassType) {
                    parameters.addAll(Util.cast(TypeParameterType.class, ((ClassType) type).getInstanceType().getTypeParameters()));
                } else if (type instanceof GenericType) {
                    parameters.addAll(Util.cast(TypeParameterType.class, ((GenericType) type).getTypeParameters()));
                } else if (type instanceof ReferenceType) {
                    List<TypeParameterType> typeArguments = ((ReferenceType) type).getTypeArguments().stream().filter(TypeParameterType.class::isInstance).map(TypeParameterType.class::cast).collect(Collectors.toList());
                    arguments.addAll(typeArguments);
                }
            }
            arguments.removeAll(parameters); // Now i only have the ones that are only arguments.

            Map<Type, TypeParameterType> map = new HashMap<>();
            for (TypeParameterType parameterType : arguments) {
                if (!map.containsKey(parameterType.getConstraint())) {
                    map.put(parameterType.getConstraint(), parameterType);
                }
            }

            for (Type type : allTypes) {
                if (type instanceof ReferenceType) {
                    ReferenceType ref = (ReferenceType) type;
                    ref.setTypeArguments(ref.getTypeArguments().stream().map(typeArgument -> {
                        //noinspection SuspiciousMethodCalls
                        if (typeArgument instanceof TypeParameterType && arguments.contains(typeArgument)) {
                            TypeParameterType parameter = (TypeParameterType) typeArgument;
                            if (parameter.getConstraint() != null && map.containsKey(parameter.getConstraint())) {
                                return map.get(parameter.getConstraint());
                            } else {
                                throw new RuntimeException();
                            }
                        } else {
                            return typeArgument;
                        }
                    }).collect(Collectors.toList()));
                }
            }
        }
    }

    private static List<Type> collectAllUnionElements(List<Type> elements, Set<UnionType> seenUnions) {
        ArrayList<Type> result = new ArrayList<>();
        for (Type type : elements) {
            if (type instanceof UnionType) {
                UnionType union = (UnionType) type;
                if (!seenUnions.contains(union)) {
                    seenUnions.add(union);
                    result.addAll(collectAllUnionElements(union.getElements(), seenUnions));
                }
            } else {
                result.add(type);
            }
        }

        return result.stream().distinct().collect(Collectors.toList());
    }

    private static void setUndefinedReturnToVoid(List<Signature> signatures) {
        // A function that returns a value is assignable to a function type that returns void
        for (Signature signature : signatures) {
            if (signature.getResolvedReturnType() instanceof SimpleType && ((SimpleType) signature.getResolvedReturnType()).getKind() == SimpleTypeKind.Undefined) {
                signature.setResolvedReturnType(new SimpleType(SimpleTypeKind.Void));
            }
        }
    }

    private static Map<String, Type> fixUnderscoreNames(Map<String, Type> declaredProperties) {
        return declaredProperties.entrySet().stream().collect(Collectors.toMap(
                entry -> fixUnderscoreName(entry.getKey()),
                Map.Entry::getValue
        ));
    }

    private static String fixUnderscoreName(String key) {
        // For some reason, everything with two or more underscore in the beginning, gets an extra underscore. I have a test that fails if this behaviour changes.
        if (key.startsWith("___")) {
            return key.substring(1, key.length());
        }
        return key;
    }

    public BenchmarkInfo withBench(Benchmark bench) {
        return new BenchmarkInfo(
                bench,
                this.typeToTest,
                this.nativeTypes,
                this.freeGenericsFinder,
                this.typeNames,
                this.typeParameterIndexer,
                globalProperties, spec);
    }

    public boolean shouldConstructType(Type type) {
        if (bench.options.constructOnlyPrimitives) {
            if (type instanceof SimpleType || type instanceof BooleanLiteral || type instanceof StringLiteral || type instanceof NumberLiteral) {
                return true;
            } else {
                return false;
            }
        }
        if (bench.options.constructAllTypes) {
            return true;
        }
        if (!bench.options.constructClassInstances && type instanceof ClassInstanceType) {
            return false;
        }
        if (!bench.options.constructClassTypes && (type instanceof ClassType)) {
            return false;
        }

        while (type instanceof ReferenceType) {
            type = ((ReferenceType) type).getTarget();
        }

        if (type instanceof SimpleType || type instanceof BooleanLiteral || type instanceof StringLiteral || type instanceof NumberLiteral || type instanceof UnionType || type instanceof IntersectionType || type instanceof TypeParameterType || type instanceof TupleType) {
            return true;
        }

        if (globalProperties.contains(type)) {
            return false;
        }

        if (type instanceof GenericType || type instanceof InterfaceType) {
            return true;
        }

        if (type instanceof ClassInstanceType || type instanceof ClassType || type instanceof ThisType) {
            return true;
        }

        throw new RuntimeException(type.getClass().getSimpleName());
    }

    public <T> T getAttribute(Class clazz, String key, T defaultValue) {
        if (!attributes.containsKey(clazz)) {
            attributes.put(clazz, new HashMap<>());
        }
        if (attributes.get(clazz).containsKey(key)) {
            //noinspection unchecked
            return (T) attributes.get(clazz).get(key);
        } else {
            attributes.get(clazz).put(key, defaultValue);
            return defaultValue;
        }
    }
}
