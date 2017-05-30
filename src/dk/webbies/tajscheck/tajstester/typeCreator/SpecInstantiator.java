package dk.webbies.tajscheck.tajstester.typeCreator;

import dk.au.cs.casa.typescript.SpecReader;
import dk.au.cs.casa.typescript.types.*;
import dk.brics.tajs.analysis.HostAPIs;
import dk.brics.tajs.analysis.Solver;
import dk.brics.tajs.lattice.ObjectLabel;
import dk.brics.tajs.lattice.Value;
import dk.brics.tajs.util.AnalysisException;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.function.Function;

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

    // misc. paths that we choose to ignore
    private final Set<List<String>> ignoredPaths;

    private Map<Type, Value> valueCache;

    private Map<Type, ObjectLabel> labelCache;

    public SpecInstantiator(SpecReader reader, Solver.SolverInterface c) {
        this.global = reader.getGlobal();
        this.visitor = new InstantiatorVisitor();
        this.objectLabelMaker = new ObjectLabelMakerVisitor();
        this.objectLabelKindDecider = new ObjectLabelKindDecider();
        this.canonicalHostObjectLabelPaths = new CanonicalHostObjectLabelPaths(c.getState().getStore().keySet());
        this.labelCache = newMap();
        this.valueCache = newMap();
        this.processing = newSet();
        this.effects = new Effects(c);
        this.ignoredPaths = newSet();

        // TODO @esbena: consider these cases
        ignoredPaths.add(Arrays.asList("<the global object>", "Reflect"));

        // Added by host function sources
        ignoredPaths.add(Arrays.asList("<the global object>", "Map"));
        ignoredPaths.add(Arrays.asList("<the global object>", "Set"));
        ignoredPaths.add(Arrays.asList("<the global object>", "WeakMap"));
        ignoredPaths.add(Arrays.asList("<the global object>", "WeakSet"));
        ignoredPaths.add(Arrays.asList("<the global object>", "Object", "assign"));

        initializeLabelsCacheWithCanonicals();
    }

    /**
     * Ensures that whenever a type T with a canonical representation is encountered, the canonical version is used.
     */
    private void initializeLabelsCacheWithCanonicals() {
        canonicalHostObjectLabelPaths.getPaths().forEach(p -> {
                    if(singletonList("print").equals(p) || singletonList("alert").equals(p) || singletonList("unescape").equals(p) || singletonList("escape").equals(p)|| p.get(p.size() - 1).equals("toString")){
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
        if(singletonList(globalObjectPath).equals(path)){
            return global;
        };
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
                return null;
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
                return null;
            }

            @Override
            public Type visit(TupleType tupleType) {
                return null;
            }

            @Override
            public Type visit(UnionType unionType) {
                return null;
            }

            @Override
            public Type visit(TypeParameterType typeParameterType) {
                return null;
            }

            @Override
            public Type visit(StringLiteral t) {
                return null;
            }

            @Override
            public Type visit(BooleanLiteral t) {
                return null;
            }

            @Override
            public Type visit(NumberLiteral t) {
                return null;
            }

            @Override
            public Type visit(IntersectionType t) {
                return null;
            }

            @Override
            public Type visit(ClassInstanceType t) {
                return null;
            }

            @Override
            public Type visit(ThisType t) {
                return null;
            }

            @Override
            public Type visit(IndexType t) {
                return null;
            }

            @Override
            public Type visit(IndexedAccessType t) {
                return null;
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

    public void instantiateGlobal() {
        global.accept(visitor, new MiscInfo(globalObjectPath));
    }

    private ObjectLabel getObjectLabel(Type type, MiscInfo info) {
        if (!labelCache.containsKey(type)) {
            // (this call should not lead to recursion)
            final ObjectLabel label;
            if (canonicalHostObjectLabelPaths.has(info.path)) {
                label = canonicalHostObjectLabelPaths.get(info.path);
            } else {
                label = type.accept(objectLabelMaker, info).makeSummary();
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

    private class ObjectLabelMakerVisitor implements TypeVisitorWithArgument<ObjectLabel, MiscInfo> {

        @Override
        public ObjectLabel visit(AnonymousType t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel visit(ClassType t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel visit(GenericType t, MiscInfo miscInfo) {
            // TODO should this really be the exact same implementation as for the InterfaceType? (we only lose the type parameters?)
            return ObjectLabel.mk(SpecObjects.getObjectAbstraction(miscInfo.path), getObjectLabelKind(t));
        }

        @Override
        public ObjectLabel visit(InterfaceType t, MiscInfo miscInfo) {
            return ObjectLabel.mk(SpecObjects.getObjectAbstraction(miscInfo.path), getObjectLabelKind(t));
        }

        @Override
        public ObjectLabel visit(ReferenceType t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel visit(SimpleType t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public ObjectLabel visit(TupleType t, MiscInfo miscInfo) {
            return ObjectLabel.mk(SpecObjects.getTupleAbstraction(miscInfo.path), getObjectLabelKind(t));
        }

        @Override
        public ObjectLabel visit(UnionType t, MiscInfo miscInfo) {
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
            throw new RuntimeException("Not implemented...");
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
            String message = "Not implemented... at " + info.path;
            if (true) {
                throw new RuntimeException(message);
            }
            log.warn(message);
            return Value.makeNull();
        }

        @Override
        public Value visit(ClassType t, MiscInfo info) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public Value visit(GenericType t, MiscInfo info) {
            // TODO should this really be the exact same implementation as for the InterfaceType? (we only lose the type parameters?)
            ObjectLabel objectLabel = SpecInstantiator.this.getObjectLabel(t.getTarget() /* use unparametized type */, info);
            return withNewObject(objectLabel, label -> {
                Map<String, Type> declaredProperties = t.getDeclaredProperties();
                writeProperties(label, declaredProperties, info);
                if (label.getKind() == ObjectLabel.Kind.FUNCTION) {
                    // TODO implement function stubs
                }
                return null;
            });
        }

        @Override
        public Value visit(InterfaceType t, MiscInfo info) {
            return withNewObject(SpecInstantiator.this.getObjectLabel(t, info), label -> {
                Map<String, Type> declaredProperties = t.getDeclaredProperties();
                writeProperties(label, declaredProperties, info);
                if (label.getKind() == ObjectLabel.Kind.FUNCTION) {
                    // TODO implement function stubs
                }
                return null;
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
            if (ignoredPaths.contains(fullPath)) {
                return;
            }
            Value value = SpecInstantiator.this.instantiate(propertyType, info, propertyName);

            effects.writeProperty(label, propertyName, value);
        }

        @Override
        public Value visit(ReferenceType t, MiscInfo info) {
            // NB: we are ignoring t.getTypeArguments()
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
                default:
                    throw new RuntimeException("Unhandled TypeKind: " + t);
            }
        }

        private Value withNewObject(ObjectLabel label, Function<ObjectLabel, Void> initializer) {
            if (label.getHostObject().getAPI() != HostAPIs.SPEC) {
                initializer.apply(label);
                return Value.makeObject(label);
            }
            // make the object a singleton to get the instantiation writes strongly
            ObjectLabel singletonLabel = label.makeSingleton();
            effects.newObject(singletonLabel);
            effects.multiplyObject(singletonLabel);
            ObjectLabel summaryLabel = singletonLabel.makeSummary();
            initializer.apply(summaryLabel);
            return Value.makeObject(summaryLabel);
        }

        @Override
        public Value visit(TupleType t, MiscInfo info) {
            return withNewObject(getObjectLabel(t, info), label -> {
                for (int i = 0; i < t.getElementTypes().size(); i++) {
                    Type componentType = t.getElementTypes().get(i);
                    String indexName = i + "";
                    writeProperty(label, indexName, componentType, info);
                }
                return null;
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
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public Value visit(StringLiteral t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public Value visit(BooleanLiteral t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public Value visit(NumberLiteral t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public Value visit(IntersectionType t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public Value visit(ClassInstanceType t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
        }

        @Override
        public Value visit(ThisType t, MiscInfo miscInfo) {
            throw new RuntimeException("Not implemented...");
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

        public MiscInfo(String initialPath) {
            path = new Stack<>();
            path.push(initialPath);
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
            final ObjectLabel.Kind kind;
            if (t.getDeclaredCallSignatures().isEmpty() && t.getDeclaredConstructSignatures().isEmpty()) {
                if(t.getDeclaredProperties().keySet().containsAll(Arrays.asList("slice", "pop", "push", "forEach", "filter", "concat"))){
                    kind = ObjectLabel.Kind.ARRAY; // TODO make this less hacky
                }else {
                    kind = ObjectLabel.Kind.OBJECT;
                }
            } else {
                kind = ObjectLabel.Kind.FUNCTION;
            }
            return kind;
        }

        @Override
        public ObjectLabel.Kind visit(InterfaceType t) {
            final ObjectLabel.Kind kind;
            if (t.getDeclaredCallSignatures().isEmpty() && t.getDeclaredConstructSignatures().isEmpty()) {
                kind = ObjectLabel.Kind.OBJECT;
            } else {
                kind = ObjectLabel.Kind.FUNCTION;
            }
            return kind;
        }

        @Override
        public ObjectLabel.Kind visit(ReferenceType t) {
            throw new RuntimeException("Not implemented...");
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