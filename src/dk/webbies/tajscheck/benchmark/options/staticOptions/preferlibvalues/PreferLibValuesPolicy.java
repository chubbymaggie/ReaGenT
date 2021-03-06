package dk.webbies.tajscheck.benchmark.options.staticOptions.preferlibvalues;

import dk.au.cs.casa.typescript.types.Type;
import dk.webbies.tajscheck.TypeWithContext;
import dk.webbies.tajscheck.benchmark.options.staticOptions.StaticOptions;
import dk.webbies.tajscheck.benchmark.options.staticOptions.expansionPolicy.LateExpansionToFunctionsWithConstructedArguments;
import dk.webbies.tajscheck.tajstester.TajsTypeTester;
import dk.webbies.tajscheck.tajstester.typeCreator.SpecInstantiator;
import dk.webbies.tajscheck.testcreator.test.Test;
import dk.webbies.tajscheck.typeutil.typeContext.TypeContext;
import dk.webbies.tajscheck.util.Util;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PreferLibValuesPolicy {
    private boolean initialized = false;
    private TajsTypeTester typeTester;

    private final Set<TypeWithContext> libraryConstructed = new HashSet<>();
    private final Set<TypeWithContext> clientConstructed = new HashSet<>();

    private boolean initializeable(TypeWithContext type) {
        if (libraryConstructed.contains(type)) {
            return true;
        }
        if (clientConstructed.contains(type)) {
            return true;
        }
        return isEasilyConstructType(type.getType(), type.getTypeContext(), libraryConstructed);
    }

    private boolean isEasilyConstructType(Type type, TypeContext typeContext, Set<TypeWithContext> isConstructable) {
        if (!typeTester.getBenchmarkInfo().shouldConstructType(type)) {
            return false; // if we cannot construct it, it is not a constructed type.
        }
        SpecInstantiator instantiator = typeTester.getValueHandler().getInstantiator();
        Predicate<TypeWithContext> whiteList = subType ->
                instantiator.getNativesInstantiator().shouldConstructAsNative(subType.getType()) ||
                isConstructable.contains(subType);

        return type.accept(new LateExpansionToFunctionsWithConstructedArguments.CanEasilyConstructVisitor(typeContext, typeTester.getBenchmarkInfo(), whiteList));
    }

    private void initialize() {
        Set<Test> tests = new HashSet<>(typeTester.getAllTests());
        boolean outerProgress = true;

        while (outerProgress) {
            outerProgress = false;

            boolean innerProgress = true;
            while (innerProgress) {
                innerProgress = false;
                for (Test test : new ArrayList<>(tests)) {
                    if (Util.concat(test.getTypeToTest(), test.getDependsOn()).stream().map(type -> new TypeWithContext(type, test.getTypeContext())).allMatch(this::initializeable)) {
                        innerProgress = true;
                        tests.remove(test);
                        for (Type produces : test.getProduces()) {
                            typeTester.getBenchmarkInfo().typesUtil.forAllSuperTypes(produces, test.getTypeContext(), libraryConstructed::add);
                        }
                    }
                }
            }

            Set<TypeWithContext> lackingDependencies = tests.stream()
                    .map(test ->
                            Util.concat(test.getTypeToTest(), test.getDependsOn()).stream()
                                    .map(type -> new TypeWithContext(type, test.getTypeContext()))
                    )
                    .flatMap(Function.identity())
                    .collect(Collectors.toSet());

            for (Test test : tests) {
                if (!test.getTypeToTest().stream().allMatch(typeToTest -> initializeable(new TypeWithContext(typeToTest, test.getTypeContext())))) {
                    continue; // If the "base" type of the test is unavailable, it is not interesting.
                }
                // if it produces a value I need, all arguments are hereby client-constructed.
                if (test.getProduces().stream().map(t -> new TypeWithContext(t, test.getTypeContext())).anyMatch(lackingDependencies::contains)) {
                    outerProgress = true;
                    for (Type dependsOn : test.getDependsOn()) {
                        typeTester.getBenchmarkInfo().typesUtil.forAllSuperTypes(dependsOn, test.getTypeContext(), subType -> {
                            if (!initializeable(subType)) {
                                clientConstructed.add(subType);
                            }
                        });
                    }
                }
            }

            if (!outerProgress && !tests.isEmpty()) {
                outerProgress = true;
                // all arguments in all remaining tests are client-constructed!
                for (Test test : tests) {
                    if (!test.getTypeToTest().stream().allMatch(typeToTest -> initializeable(new TypeWithContext(typeToTest, test.getTypeContext())))) {
                        continue; // If the "base" type of the test is unavailable, it is not interesting.
                    }
                    for (Type dependsOn : test.getDependsOn()) {
                        typeTester.getBenchmarkInfo().typesUtil.forAllSuperTypes(dependsOn, test.getTypeContext(), subType -> {
                            if (!initializeable(subType)) {
                                clientConstructed.add(subType);
                            }
                        });
                    }
                }
            }
        }
        assert tests.isEmpty();
    }

    public StaticOptions.ArgumentValuesStrategy getArgumentStrategy(TypeWithContext type, TajsTypeTester typeTester) {
        if (!initialized) {
            this.typeTester = typeTester;
            initialize();
            initialized = true;
        }
        if (isEasilyConstructType(type.getType(), type.getTypeContext(), Collections.emptySet())  || clientConstructed.contains(type) || !libraryConstructed.contains(type)) {
            return StaticOptions.ArgumentValuesStrategy.MIX_FEEDBACK_AND_CONSTRUCTED;
        } else {
            return StaticOptions.ArgumentValuesStrategy.FORCE_FEEDBACK;
        }

    }
}
