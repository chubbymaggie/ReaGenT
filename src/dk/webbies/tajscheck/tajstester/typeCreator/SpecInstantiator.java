package dk.webbies.tajscheck.tajstester.typeCreator;

import dk.au.cs.casa.typescript.SpecReader;
import dk.au.cs.casa.typescript.types.*;
import dk.brics.tajs.analysis.HostAPIs;
import dk.brics.tajs.analysis.Solver;
import dk.brics.tajs.lattice.ObjectLabel;
import dk.brics.tajs.lattice.Value;
import dk.brics.tajs.util.AnalysisException;
import dk.webbies.tajscheck.TypeWithContext;
import dk.webbies.tajscheck.benchmark.BenchmarkInfo;
import dk.webbies.tajscheck.typeutil.TypesUtil;
import dk.webbies.tajscheck.typeutil.typeContext.TypeContext;
import dk.webbies.tajscheck.util.Util;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static dk.brics.tajs.util.Collections.*;
import static java.util.Collections.singletonList;

public class SpecInstantiator {

    private static final Logger log = Logger.getLogger(SpecInstantiator.class);

    private static final String globalObjectPath = "<the global object>";

    private final Type global;

    private final InstantiatorVisitor visitor;

    private final Set<Type> processing;

    private final ObjectLabelMakerVisitor objectLabelMaker;

    private final ObjectLabelKindDecider objectLabelKindDecider;

    private final CanonicalHostObjectLabelPaths canonicalHostObjectLabelPaths;

    private final Effects effects;
    private final BenchmarkInfo info;

    // misc. paths that we choose to ignore
    private Map<Type, Value> valueCache;

    private Map<Type, ObjectLabel> labelCache;

    public SpecInstantiator(SpecReader reader, Solver.SolverInterface c, BenchmarkInfo info) {
        this.global = reader.getGlobal();
        this.visitor = new InstantiatorVisitor();
        this.objectLabelMaker = new ObjectLabelMakerVisitor();
        this.objectLabelKindDecider = new ObjectLabelKindDecider();
        this.canonicalHostObjectLabelPaths = new CanonicalHostObjectLabelPaths(c.getState().getStore().keySet()); // TODO: See what is inside this thing.
        this.labelCache = newMap();
        this.valueCache = newMap();
        this.processing = newSet();
        this.effects = new Effects(c);
        this.info = info;

        initializeLabelsCacheWithCanonicals();
    }

    /**
     * Ensures that whenever a type T with a canonical representation is encountered, the canonical version is used.
     */
    private void initializeLabelsCacheWithCanonicals() {
        canonicalHostObjectLabelPaths.getPaths().forEach(p -> {
                    if (singletonList("print").equals(p) || singletonList("alert").equals(p) || singletonList("unescape").equals(p) || singletonList("escape").equals(p) || p.get(p.size() - 1).equals("toString")) {
                        return;
                    }

                    if (!p.isEmpty() && p.iterator().next().equals("Symbol")) {
                        return;
                    }
                    if (p.get(p.size() - 1).endsWith("instances")) {
                        return;
                    }
                    if (Arrays.asList("Object", "module").equals(p)) {
                        return;
                    }

                    Type type = resolveType(p);
                    if (type == null) {
                        return;
                    }
                    Stack<String> stack = new Stack<>();
                    stack.addAll(p);
                    labelCache.put(type, canonicalHostObjectLabelPaths.get(stack));
                }
        );
    }

    private Type resolveType(List<String> path) {
        if (singletonList(globalObjectPath).equals(path)) {
            return global;
        }
        if(path.size() > 0 && path.get(0).equals("Window"))
            path = path.subList(1, path.size());
        return resolveType(global, path);
    }

    private Type resolveType(Type root, List<String> path) {
        if (path.isEmpty()) {
            return root;
        }
        String step = path.get(0);
        if ("prototype".equals(step)) {
            return null;
        }
        if (root == null) {
            return null;
        }
        Type newRoot = root.accept(new TypeVisitor<Type>() {
            @Override
            public Type visit(AnonymousType anonymousType) {
                throw new RuntimeException();
            }

            @Override
            public Type visit(ClassType classType) {
                return classType.getStaticProperties().get(step);
            }

            @Override
            public Type visit(GenericType genericType) {
                return genericType.getDeclaredProperties().get(step);
            }

            @Override
            public Type visit(InterfaceType interfaceType) {
                return interfaceType.getDeclaredProperties().get(step);
            }

            @Override
            public Type visit(ReferenceType referenceType) {
                return referenceType.getTarget().accept(this);
            }

            @Override
            public Type visit(SimpleType simpleType) {
                throw new RuntimeException();
            }

            @Override
            public Type visit(TupleType tupleType) {
                throw new RuntimeException();
            }

            @Override
            public Type visit(UnionType unionType) {
                throw new RuntimeException();
            }

            @Override
            public Type visit(TypeParameterType typeParameterType) {
                throw new RuntimeException();
            }

            @Override
            public Type visit(StringLiteral t) {
                throw new RuntimeException();
            }

            @Override
            public Type visit(BooleanLiteral t) {
                throw new RuntimeException();
            }

            @Override
            public Type visit(NumberLiteral t) {
                throw new RuntimeException();
            }

            @Override
            public Type visit(IntersectionType t) {
                throw new RuntimeException();
            }

            @Override
            public Type visit(ClassInstanceType t) {
                throw new RuntimeException();
            }

            @Override
            public Type visit(ThisType t) {
                throw new RuntimeException();
            }

            @Override
            public Type visit(IndexType t) {
                throw new RuntimeException();
            }

            @Override
            public Type visit(IndexedAccessType t) {
                throw new RuntimeException();
            }
        });
        if (newRoot == null) {
            throw new AnalysisException(String.format("Could not find type at step '%s' for root %s, with remaining path '%s'", step, root.toString(), path));
        }
        List<String> shorterPath = path.subList(1, path.size());
        return resolveType(newRoot, shorterPath);
    }

    public void showStats() {
        Effects.Stats stats = effects.getStats();

        StringBuilder sb = new StringBuilder();
        int allocationCount = stats.getNewObjects().size();
        long newFunctionCount = stats.getNewObjects().stream().filter(l -> l.getKind() == ObjectLabel.Kind.FUNCTION).count();
        long newObjectCount = stats.getNewObjects().stream().filter(l -> l.getKind() == ObjectLabel.Kind.OBJECT).count();

        sb.append(String.format(getClass().getSimpleName() + " statistics:%n"));
        sb.append(String.format(" Allocations: %d%n", allocationCount));
        sb.append(String.format("  Functions: %d%n", newFunctionCount));
        sb.append(String.format("  Objects: %d%n", newObjectCount));
        sb.append(String.format("  Other: %d%n", allocationCount - newFunctionCount - newObjectCount));

        System.out.println(sb.toString());
    }

    private ObjectLabel getObjectLabel(Type type, MiscInfo info) {
        if (!labelCache.containsKey(type)) {
            // (this call should not lead to recursion)
            final ObjectLabel label;
            if (canonicalHostObjectLabelPaths.has(info.path)) {
                label = canonicalHostObjectLabelPaths.get(info.path);
            } else {
                label = type.accept(objectLabelMaker, info);
            }
            labelCache.put(type, label);
        }
        return labelCache.get(type);
    }

    private ObjectLabel.Kind getObjectLabelKind(Type type) {
        return type.accept(objectLabelKindDecider);
    }

    private Value instantiate(Type type, MiscInfo info, String step) {
        if (step != null) {
            info.path.push(step);
        }
        if (!valueCache.containsKey(type)) {
            Value value;
            if (processing.contains(type)) {
                // trying to instantiate a (recursive) type that is already being instantiated
                value = Value.makeObject(getObjectLabel(type, info));
            } else {
                processing.add(type);
                log.debug("Visiting: " + info.path.toString());
                value = type.accept(visitor, info);
                processing.remove(type);
            }
            valueCache.put(type, value);
        }
        if (step != null) {
            info.path.pop();
        }
        return valueCache.get(type);
    }

    public Value createValue(TypeWithContext type, String path) {
        MiscInfo misc = new MiscInfo(Arrays.asList(path.split("\\.")), type.getTypeContext());
        return instantiate(type.getType(), misc, null); // TODO: TypeContext is currently ignored.
    }

    private class ObjectLabelMakerVisitor implements TypeVisitorWithArgument<ObjectLabel, MiscInfo> {

        @Override
        public ObjectLabel visit(AnonymousType t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel visit(ClassType t, MiscInfo miscInfo) {
            System.err.println("Inaccurate modelling of classType");
            return info.typesUtil.classToInterface(t).accept(this, miscInfo);
        }

        @Override
        public ObjectLabel visit(GenericType t, MiscInfo miscInfo) {
            return t.toInterface().accept(this, miscInfo);
        }

        @Override
        public ObjectLabel visit(InterfaceType t, MiscInfo miscInfo) {
            return makeObjectLabel(t, miscInfo);
        }

        private Map<TypeWithContext, ObjectLabel> labelCache = new HashMap<>();
        private ObjectLabel makeObjectLabel(Type t, MiscInfo miscInfo) {
            TypeWithContext key = new TypeWithContext(t, miscInfo.context);
            if (labelCache.containsKey(key)) {
                return labelCache.get(key);
            }
            if (labelCache.size() > 0) {
                labelCache.keySet().iterator().next().equals(key);
            }
            ObjectLabel label = ObjectLabel.mk(SpecObjects.getObjectAbstraction(miscInfo.path, key), getObjectLabelKind(t));
            labelCache.put(key, label);
            return label;
        }

        @Override
        public ObjectLabel visit(ReferenceType t, MiscInfo miscInfo) {
            return t.getTarget().accept(this, miscInfo);
        }

        @Override
        public ObjectLabel visit(SimpleType t, MiscInfo miscInfo) {
            switch (t.getKind()) {
                case Null:
                    return null;
                default:
                    throw new RuntimeException("Unknown case: " + t.getKind());
            }
        }

        @Override
        public ObjectLabel visit(TupleType t, MiscInfo miscInfo) {
            return makeObjectLabel(t, miscInfo);
        }

        @Override
        public ObjectLabel visit(UnionType t, MiscInfo miscInfo) {
            List<ObjectLabel> labels = t.getElements().stream().map(elem -> elem.accept(this, miscInfo.apendPath("<union-member>"))).filter(Objects::nonNull).collect(Collectors.toList());
            if (labels.size() == 1) {
                return labels.iterator().next();
            }
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel visit(TypeParameterType t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel visit(StringLiteral t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel visit(BooleanLiteral t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel visit(NumberLiteral t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel visit(IntersectionType t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel visit(ClassInstanceType t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel visit(ThisType t, MiscInfo miscInfo) {
            return miscInfo.context.getThisType().accept(this, miscInfo);
        }

        @Override
        public ObjectLabel visit(IndexType t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel visit(IndexedAccessType t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }
    }

    private class InstantiatorVisitor implements TypeVisitorWithArgument<Value, MiscInfo> {

        @Override
        public Value visit(AnonymousType t, MiscInfo info) {
            throw new RuntimeException();
        }

        @Override
        public Value visit(ClassType t, MiscInfo info) {
            System.err.println("Inaccurate modelling of classes");
            return SpecInstantiator.this.info.typesUtil.classToInterface(t).accept(this, info);
        }

        @Override
        public Value visit(GenericType t, MiscInfo info) {
            return t.toInterface().accept(this, info);
        }

        @Override
        public Value visit(InterfaceType t, MiscInfo info) {
            if (SpecInstantiator.this.info.freeGenericsFinder.hasThisTypes(t)) {
                info = info.withContext(info.context.withThisType(t));
            }
            MiscInfo finalInfo = info;
            return withNewObject(SpecInstantiator.this.getObjectLabel(t, info), label -> {
                Map<String, Type> declaredProperties = t.getDeclaredProperties();

                if (t.getDeclaredNumberIndexType() != null) {
                    effects.writeNumberIndexer(label, t.getDeclaredNumberIndexType().accept(this, finalInfo.apendPath("[numberIndexer]")));
                }
                writeProperties(label, declaredProperties, finalInfo);

                if (t.getDeclaredStringIndexType() != null) {
                    effects.writeStringIndexer(label, t.getDeclaredStringIndexType().accept(this, finalInfo.apendPath("[stringIndexer]")));
                }

                if (label.getKind() == ObjectLabel.Kind.FUNCTION) {
                    // TODO implement function stubs
                }
            });
        }

        private void writeProperties(ObjectLabel label, Map<String, Type> declaredProperties, MiscInfo info) {
            declaredProperties.forEach((propertyName, propertyType) -> {
                writeProperty(label, propertyName, propertyType, info);
            });

            // XXX circumventing the lack of knowledge of prototype chain of Functions (constructors)
            if (label.getKind() == ObjectLabel.Kind.FUNCTION) {
                effects.writeProperty(label, "prototype", Value.makeObject(label));
            }
        }

        private void writeProperty(ObjectLabel label, String propertyName, Type propertyType, MiscInfo info) {
            List<String> fullPath = newList(info.path);

            fullPath.add(propertyName);
            Value value = SpecInstantiator.this.instantiate(propertyType, info, propertyName);

            effects.writeProperty(label, propertyName, value);
        }

        @Override
        public Value visit(ReferenceType t, MiscInfo info) {
            info = info.withContext(SpecInstantiator.this.info.typesUtil.generateParameterMap(t, info.context));
            return SpecInstantiator.this.instantiate(t.getTarget(), info, null);
        }

        @Override
        public Value visit(SimpleType t, MiscInfo info) {
            switch (t.getKind()) {
                case Any:
                    // FIXME warn about underapproximated value
                    return Value.makeStr("THIS-SHOULD-BE-TOP-VALUE");
                case String:
                    return Value.makeAnyStr();
                case Enum:
                case Number:
                    return Value.makeAnyNum();
                case Boolean:
                    return Value.makeAnyBool();
                case Void:
                case Undefined:
                    return Value.makeUndef();
                case Null:
                    return Value.makeNull();
                case Never:
                    return Value.makeNone();
                default:
                    throw new RuntimeException("Unhandled TypeKind: " + t);
            }
        }

        private Value withNewObject(ObjectLabel label, Consumer<ObjectLabel> initializer) {
            if (label.getHostObject().getAPI() != HostAPIs.SPEC) {
                initializer.accept(label);
                return Value.makeObject(label);
            }
            // make the object a singleton to get the instantiation writes strongly
            ObjectLabel singletonLabel = label;
            effects.newObject(singletonLabel);
            effects.multiplyObject(singletonLabel);
            initializer.accept(singletonLabel); // TODO: This might be too strong, it should be summarized at some point.
            return Value.makeObject(singletonLabel);
        }

        @Override
        public Value visit(TupleType t, MiscInfo info) {
            return withNewObject(getObjectLabel(t, info), label -> {
                for (int i = 0; i < t.getElementTypes().size(); i++) {
                    Type componentType = t.getElementTypes().get(i);
                    String indexName = i + "";
                    writeProperty(label, indexName, componentType, info);
                }
                writeProperty(label, "length", new NumberLiteral(t.getElementTypes().size()), info);
            });
        }

        @Override
        public Value visit(UnionType t, MiscInfo info) {
            Value unionValue = Value.makeNone();
            List<Type> unionTypes = t.getElements();
            for (final Type componentType : unionTypes) {
                Value componentValue = SpecInstantiator.this.instantiate(componentType, info, "<union-member>");
                unionValue = unionValue.join(componentValue);
            }
            return unionValue;
        }

        @Override
        public Value visit(TypeParameterType t, MiscInfo info) {
            if (info.context.containsKey(t)) {
                TypeWithContext lookup = info.context.get(t);
                return lookup.getType().accept(this, info.withContext(lookup.getTypeContext()));
            } else {
                System.err.println("Just returning a dummy object for unbound type parameters.");
                return unboundTypeParameter.accept(this, info);
            }
        }

        private final InterfaceType unboundTypeParameter = SpecReader.makeEmptySyntheticInterfaceType();
        {
            unboundTypeParameter.getDeclaredProperties().put("_isUnboundGeneric", new BooleanLiteral(true));
        }

        @Override
        public Value visit(StringLiteral t, MiscInfo miscInfo) {
            return Value.makeSpecialStrings(Collections.singletonList(t.getText()));
        }

        @Override
        public Value visit(BooleanLiteral t, MiscInfo miscInfo) {
            return Value.makeBool(t.getValue());
        }

        @Override
        public Value visit(NumberLiteral t, MiscInfo miscInfo) {
            return Value.makeNum(t.getValue());
        }

        @Override
        public Value visit(IntersectionType t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public Value visit(ClassInstanceType t, MiscInfo miscInfo) {
            if (SpecInstantiator.this.info.freeGenericsFinder.hasThisTypes(t)) {
                miscInfo = miscInfo.withContext(miscInfo.context.withThisType(t));
            }
            System.err.println("Inaccurately modelling class instances");
            return info.typesUtil.createClassInstanceType(((ClassType) t.getClassType())).accept(this, miscInfo); // TODO:
        }

        @Override
        public Value visit(ThisType t, MiscInfo miscInfo) {
            return miscInfo.context.getThisType().accept(this, miscInfo);
        }

        @Override
        public Value visit(IndexType t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public Value visit(IndexedAccessType t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

    }

    private class MiscInfo {

        public final Stack<String> path;
        public final TypeContext context;

        MiscInfo(Collection<String> initialPath, TypeContext context) {
            this.context = context;
            path = new Stack<>();
            path.addAll(initialPath);
        }

        public MiscInfo withContext(TypeContext typeContext) {
            return new MiscInfo(path, typeContext);
        }

        public MiscInfo apendPath(String path) {
            return new MiscInfo(Util.concat(this.path, Collections.singletonList(path)), context);
        }
    }

    private class ObjectLabelKindDecider implements TypeVisitor<ObjectLabel.Kind> {

        @Override
        public ObjectLabel.Kind visit(AnonymousType t) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel.Kind visit(ClassType t) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel.Kind visit(GenericType t) {
            ObjectLabel.Kind kind;
            if (t.getDeclaredCallSignatures().isEmpty() && t.getDeclaredConstructSignatures().isEmpty()) {
                if (t.getDeclaredProperties().keySet().containsAll(Arrays.asList("slice", "pop", "push", "forEach", "filter", "concat"))) {
                    kind = ObjectLabel.Kind.ARRAY; // TODO make this less hacky
                } else {
                    kind = ObjectLabel.Kind.OBJECT;
                }
            } else {
                kind = ObjectLabel.Kind.FUNCTION;
            }
            if (kind == ObjectLabel.Kind.OBJECT) {
                for (ObjectLabel.Kind label : t.getBaseTypes().stream().map(subType -> subType.accept(this)).collect(Collectors.toList())) {
                    if (label != ObjectLabel.Kind.OBJECT) {
                        kind = label;
                    }
                }
            }
            return kind;
        }

        @Override
        public ObjectLabel.Kind visit(InterfaceType t) {
            ObjectLabel.Kind kind;
            if (t.getDeclaredCallSignatures().isEmpty() && t.getDeclaredConstructSignatures().isEmpty()) {
                kind = ObjectLabel.Kind.OBJECT;
            } else {
                kind = ObjectLabel.Kind.FUNCTION;
            }
            if (kind == ObjectLabel.Kind.OBJECT) {
                for (ObjectLabel.Kind label : t.getBaseTypes().stream().map(subType -> subType.accept(this)).collect(Collectors.toList())) {
                    if (label != ObjectLabel.Kind.OBJECT) {
                        kind = label;
                    }
                }
            }
            return kind;
        }

        @Override
        public ObjectLabel.Kind visit(ReferenceType t) {
            return t.getTarget().accept(this);
        }

        @Override
        public ObjectLabel.Kind visit(SimpleType t) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel.Kind visit(TupleType t) {
            return ObjectLabel.Kind.ARRAY;
        }

        @Override
        public ObjectLabel.Kind visit(UnionType t) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel.Kind visit(TypeParameterType t) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel.Kind visit(StringLiteral t) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel.Kind visit(BooleanLiteral t) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel.Kind visit(NumberLiteral t) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel.Kind visit(IntersectionType t) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel.Kind visit(ClassInstanceType t) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel.Kind visit(ThisType t) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel.Kind visit(IndexType t) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel.Kind visit(IndexedAccessType t) {
            throw new RuntimeException("Not implemented...");
        }


    }
}
