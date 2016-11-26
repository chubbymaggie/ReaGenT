package dk.webbies.tajscheck.buildprogram;

import dk.au.cs.casa.typescript.types.*;
import dk.au.cs.casa.typescript.types.BooleanLiteral;
import dk.au.cs.casa.typescript.types.NumberLiteral;
import dk.au.cs.casa.typescript.types.StringLiteral;
import dk.webbies.tajscheck.ParameterMap;
import dk.webbies.tajscheck.TypesUtil;
import dk.webbies.tajscheck.buildprogram.typechecks.FieldTypeCheck;
import dk.webbies.tajscheck.buildprogram.typechecks.SimpleTypeCheck;
import dk.webbies.tajscheck.buildprogram.typechecks.TypeCheck;
import dk.webbies.tajscheck.paser.AST.*;
import dk.webbies.tajscheck.testcreator.test.check.Check;
import dk.webbies.tajscheck.testcreator.test.check.CheckToExpression;
import dk.webbies.tajscheck.util.Util;

import java.util.*;
import java.util.stream.Collectors;

import static dk.webbies.tajscheck.paser.AstBuilder.*;

/**
 * Created by erik1 on 03-11-2016.
 */
public class CheckType {
    private final Set<Type> nativeTypes;
    private Map<Type, String> typeNames;
    private TestProgramBuilder.TypeParameterIndexer typeParameterIndexer;
    private ParameterMap parameterMap;

    public CheckType(Set<Type> nativeTypes, Map<Type, String> typeNames, TestProgramBuilder.TypeParameterIndexer typeParameterIndexer, ParameterMap parameterMap) {
        this.nativeTypes = nativeTypes;
        this.typeNames = typeNames;
        this.typeParameterIndexer = typeParameterIndexer;
        this.parameterMap = parameterMap;
    }

    public Statement assertResultingType(Type type, Expression exp, String path, int depth) {
        List<TypeCheck> typeChecks = type.accept(new CreateTypeCheckVisitor(nativeTypes, typeParameterIndexer, typeNames), new Arg(parameterMap, depth));

        return block(
                variable("typeChecked", bool(true)),
                block(
                        typeChecks.stream().map(check -> checkToAssertions(check, exp, path)).collect(Collectors.toList())
                ),
                ifThen(
                        unary(Operator.NOT, identifier("typeChecked")),
                        Return(bool(false))
                )
        );
    }

    public Expression checkResultingType(Type type, Expression exp, String path, int depth) {
        return call(function(
                block(
                        statement(function("assert", block(
                                comment("Overriding assert, i'm only interested in IF the type check passes. "),
                                Return(identifier("cond"))
                        ), "cond")),

                        assertResultingType(type, exp, path, depth), // This returns false, if the type check fails
                        Return(bool(true)) // If it didn't return false, then the type check passed!
                )
        ));
    }

    public String getTypeDescription(Type type, int depth) {
        List<TypeCheck> result = type.accept(new CreateTypeCheckVisitor(nativeTypes, typeParameterIndexer, typeNames), new Arg(parameterMap, depth));
        return createIntersection(result).getExpected();
    }

    private Statement checkToAssertions(TypeCheck typeCheck, Expression exp, String path) {
        if (typeCheck instanceof FieldTypeCheck) {
            FieldTypeCheck fieldTypeCheck = (FieldTypeCheck) typeCheck;
            String field = fieldTypeCheck.getField();
            return statement(call(function(block(
                    fieldTypeCheck.getFieldChecks().stream().map(subCheck -> checkToAssertions(subCheck, member(exp, field), path + "." + field)).collect(Collectors.toList())
            ))));
        }

        assert typeCheck instanceof SimpleTypeCheck;
        // assert(cond, path, expected, actual)
        Expression checkExpression = CheckToExpression.generate(typeCheck.getCheck(), exp);
        CallExpression assertCall = call(identifier("assert"), checkExpression, string(path), string(typeCheck.getExpected()), exp);
        return ifThen(
                unary(Operator.NOT, assertCall),
                block(
                        statement(binary(identifier("typeChecked"),Operator.EQUAL, bool(false))),
                        Return(bool(false))
                )
        );
    }

    private static final class Arg {
        final ParameterMap parameterMap;
        final int depthRemaining;

        private Arg(ParameterMap parameterMap, int depthRemaining) {
            this.parameterMap = parameterMap;
            this.depthRemaining = depthRemaining;
        }

        public Arg withParameters(ParameterMap map) {
            return new Arg(map, this.depthRemaining);
        }

        public Arg decreaseDepth() {
            return new Arg(this.parameterMap, this.depthRemaining - 1);
        }
    }

    private static final class CreateTypeCheckVisitor implements TypeVisitorWithArgument<List<TypeCheck>, Arg> {
        private final Set<Type> nativeTypes;
        private final TestProgramBuilder.TypeParameterIndexer typeParameterIndexer;
        private final Map<Type, String> typeNames;

        private CreateTypeCheckVisitor(Set<Type> nativeTypes, TestProgramBuilder.TypeParameterIndexer typeParameterIndexer, Map<Type, String> typeNames) {
            this.nativeTypes = nativeTypes;
            this.typeParameterIndexer = typeParameterIndexer;
            this.typeNames = typeNames;
        }

        @Override
        public List<TypeCheck> visit(AnonymousType t, Arg arg) {
            throw new RuntimeException();
        }

        @Override
        public List<TypeCheck> visit(ClassType t, Arg arg) {
            throw new RuntimeException();
        }

        @Override
        public List<TypeCheck> visit(GenericType t, Arg arg) {
            if (nativeTypes.contains(t)) {
                if ("RegExp".equals(typeNames.get(t))) {
                    return Arrays.asList(
                            expectNotNull(),
                            new SimpleTypeCheck(
                                    Check.instanceOf(identifier("RegExp")),
                                    "RegExp"
                            )
                    );
                }
                throw new RuntimeException();
            }
            assert t.getTypeParameters().equals(t.getTypeArguments());
            return t.toInterface().accept(this, arg);
        }

        @Override
        public List<TypeCheck> visit(InterfaceType t, Arg arg) {
            if (TypesUtil.isEmptyInterface(t)) {
                return Collections.singletonList(new SimpleTypeCheck(Check.alwaysTrue(), "[any]"));
            }
            if ("Date".equals(typeNames.get(t))) {
                return Arrays.asList(expectNotNull(), new SimpleTypeCheck(Check.instanceOf(identifier("Date")), "Date"));
            }
            if ("Function".equals(typeNames.get(t))) {
                return Collections.singletonList(new SimpleTypeCheck(Check.typeOf("function"), "function"));
            }
            if ("String".equals(typeNames.get(t))) {
                return new SimpleType(SimpleTypeKind.String).accept(this, arg);
            }
            if ("Number".equals(typeNames.get(t))) {
                return new SimpleType(SimpleTypeKind.Number).accept(this, arg);
            }
            if ("Object".equals(typeNames.get(t))) {
                return Arrays.asList(expectNotNull(), new SimpleTypeCheck(Check.typeOf("object"), "Object"));
            }
            if ("Error".equals(typeNames.get(t))) {
                return Arrays.asList(expectNotNull(), new SimpleTypeCheck(Check.instanceOf(identifier("Error")), "Error"));
            }

            if (nativeTypes.contains(t)) {
                throw new RuntimeException();
            }

            List<TypeCheck> result = new ArrayList<>();

            if (!t.getDeclaredCallSignatures().isEmpty() || !t.getDeclaredConstructSignatures().isEmpty()) {
                result.add(
                        new SimpleTypeCheck(
                                Check.typeOf("function"),
                                "function"
                        )
                );
            } else {
                result.add(
                        new SimpleTypeCheck(
                                Check.or(
                                        Check.typeOf("function"),
                                        Check.typeOf("object")
                                ),
                                "(function or object)"
                        )
                );
            }

            // Adding all baseTypes
            t.getBaseTypes().forEach(base -> result.addAll(base.accept(this, arg)));

            if (arg.depthRemaining > 0) {
                Arg subArg = arg.decreaseDepth();
                for (Map.Entry<String, Type> entry : t.getDeclaredProperties().entrySet()) {
                    List<TypeCheck> fieldChecks = entry.getValue().accept(this, subArg);
                    result.add(new FieldTypeCheck(entry.getKey(), fieldChecks));
                }

            }

            return result;
        }

        @Override
        public List<TypeCheck> visit(ReferenceType t, Arg arg) {
            if ("Array".equals(typeNames.get(t.getTarget()))) {
                // TODO: Check the index type:
                return Arrays.asList(expectNotNull(), new SimpleTypeCheck(Check.instanceOf(identifier("Array")), "Array"));
            }

            if (nativeTypes.contains(t)) {
                throw new RuntimeException();
            }
            if (nativeTypes.contains(t.getTarget())) {
                throw new RuntimeException();
            }
            return t.getTarget().accept(this, arg.withParameters(arg.parameterMap.append(TypesUtil.generateParameterMap(t))));
        }

        @Override
        public List<TypeCheck> visit(SimpleType t, Arg arg) {
            if (t.getKind() == SimpleTypeKind.Any) {
                return Collections.singletonList(
                        new SimpleTypeCheck(Check.alwaysTrue(), "[any]")
                );
            }
            if (t.getKind() == SimpleTypeKind.Null) {
                return Collections.singletonList(
                        new SimpleTypeCheck(Check.equalTo(nullLiteral()), "null")
                );
            }
            String typeOf = getTypeOf(t);
            return Collections.singletonList(new SimpleTypeCheck(Check.typeOf(typeOf), typeOf));
        }

        @Override
        public List<TypeCheck> visit(TupleType t, Arg arg) {
            throw new RuntimeException();
        }

        @Override
        public List<TypeCheck> visit(UnionType t, Arg arg) {
            return Collections.singletonList(
                    createUnionCheck(t.getElements().stream().map(subType -> subType.accept(this, arg)).collect(Collectors.toList()))
            );
        }

        @Override
        public List<TypeCheck> visit(UnresolvedType t, Arg arg) {
            throw new RuntimeException();
        }

        @Override
        public List<TypeCheck> visit(TypeParameterType parameter, Arg arg) {
            assert parameter.getTarget() == null;

            ParameterMap parameterMap = arg.parameterMap;

            if (parameterMap.containsKey(parameter)) {
                if (!TypesUtil.findRecursiveDefinition(parameter, parameterMap, typeParameterIndexer).isEmpty()) {
                    List<Type> constraints = TypesUtil.findRecursiveDefinition(parameter, parameterMap, typeParameterIndexer);
                    IntersectionType constraintsIntersection = new IntersectionType();
                    constraintsIntersection.setElements(constraints);
                    return constraintsIntersection.accept(this, arg);
                }
                return parameterMap.get(parameter).accept(this, arg);
            }

            List<TypeCheck> checks = new ArrayList<>(parameter.getConstraint().accept(this, arg));

            String markerField = typeParameterIndexer.getMarkerField(parameter);

            checks.add(expectNotNull());
            checks.add(new SimpleTypeCheck(
                    Check.field(markerField, Check.equalTo(bool(true))),
                    "a generic type marker (." + markerField + ")"
            ));

            return checks;
        }

        @Override
        public List<TypeCheck> visit(SymbolType t, Arg arg) {
            throw new RuntimeException();
        }

        @Override
        public List<TypeCheck> visit(StringLiteral t, Arg arg) {
            return Collections.singletonList(
                    new SimpleTypeCheck(Check.equalTo(string(t.getText())), "\"" + t.getText() + "\"")
            );
        }

        @Override
        public List<TypeCheck> visit(BooleanLiteral t, Arg arg) {
            return Collections.singletonList(
                    new SimpleTypeCheck(Check.equalTo(bool(t.getValue())), Boolean.toString(t.getValue()))
            );
        }

        @Override
        public List<TypeCheck> visit(NumberLiteral t, Arg arg) {
            return Collections.singletonList(
                    new SimpleTypeCheck(Check.equalTo(number(t.getValue())), Double.toString(t.getValue()))
            );
        }

        @Override
        public List<TypeCheck> visit(IntersectionType t, Arg arg) {
            return t.getElements()
                    .stream()
                    .map(subType -> subType.accept(this, arg))
                    .reduce(new ArrayList<>(), Util::reduceList);
        }
    }

    private static TypeCheck createUnionCheck(List<List<TypeCheck>> checksLists) {
        assert !checksLists.isEmpty();
        if (checksLists.size() == 1) {
            return createIntersection(checksLists.iterator().next());
        }

        List<TypeCheck> checks = checksLists.stream().map(CheckType::createIntersection).collect(Collectors.toList());

        StringBuilder expected = new StringBuilder("(");
        for (int i = 0; i < checks.size(); i++) {
            expected.append(checks.get(i).getExpected());
            if (i != checks.size() - 1) {
                expected.append(" or ");
            }
        }
        expected.append(")");

        Check check = Check.or(checks.stream().map(TypeCheck::getCheck).collect(Collectors.toList()));

        return new SimpleTypeCheck(check, expected.toString());
    }

    private static TypeCheck createIntersection(List<TypeCheck> checks) {
        assert !checks.isEmpty();
        if (checks.size() == 1) {
            return checks.iterator().next();
        }
        String expected = createIntersectionDescription(checks);

        Check check = Check.and(checks.stream().map(TypeCheck::getCheck).collect(Collectors.toList()));

        return new SimpleTypeCheck(check, expected);
    }

    public static String createIntersectionDescription(List<TypeCheck> checks) {
        if (checks.size() == 1) {
            return checks.iterator().next().getExpected();
        }
        StringBuilder expected = new StringBuilder("(");
        for (int i = 0; i < checks.size(); i++) {
            expected.append(checks.get(i).getExpected());
            if (i != checks.size() - 1) {
                expected.append(" and ");
            }
        }
        expected.append(")");
        return expected.toString();
    }

    private static String getTypeOf(SimpleType type) {
        switch (type.getKind()) {
            case String:
                return "string";
            case Number:
                return "number";
            case Boolean:
                return "boolean";
            case Void:
            case Undefined:
                return "undefined";
            default:
                throw new RuntimeException(type.getKind().toString());
        }
    }

    private static TypeCheck expectNotNull() {
        return new SimpleTypeCheck(
                Check.and(
                    Check.not(Check.typeOf("undefined")),
                    Check.not(Check.equalTo(nullLiteral()))
                ),
                "a non null value"
        );
    }
}
