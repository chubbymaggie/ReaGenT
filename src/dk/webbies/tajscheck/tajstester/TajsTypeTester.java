package dk.webbies.tajscheck.tajstester;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import dk.brics.tajs.analysis.FunctionCalls;
import dk.brics.tajs.analysis.PropVarOperations;
import dk.brics.tajs.analysis.Solver;
import dk.brics.tajs.analysis.js.UserFunctionCalls;
import dk.brics.tajs.analysis.nativeobjects.ECMAScriptObjects;
import dk.brics.tajs.flowgraph.AbstractNode;
import dk.brics.tajs.flowgraph.BasicBlock;
import dk.brics.tajs.lattice.*;
import dk.brics.tajs.type_testing.TypeTestRunner;
import dk.brics.tajs.util.AnalysisException;
import dk.brics.tajs.util.Pair;
import dk.webbies.tajscheck.TypeWithContext;
import dk.webbies.tajscheck.testcreator.test.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dk.brics.tajs.util.Collections.newList;

public class TajsTypeTester implements TypeTestRunner {
    private static final boolean DEBUG = true;

    private final List<Test> tests;
    private final BiMap<TypeWithContext, String> typeNames = HashBiMap.create();

    final private List<TypeChecker.TypeViolation> allViolations = newList();

    private final List<Test> performed = newList();

    public TajsTypeTester(List<Test> tests) {
        this.tests = tests;
    }

    public int getTotalTests() {return tests.size();}

    public List<Test> getAllTests() {return tests;}

    public List<Test> getPerformedTests() {return performed;}

    public List<TypeChecker.TypeViolation> getAllViolations() {return allViolations;}

    public void triggerTypeTests(Solver.SolverInterface c) {
        State callState = c.getState().clone();

        PropVarOperations pv = c.getAnalysis().getPropVarOperations();

        TajsTestVisitor visitor = new TajsTestVisitor(callState.getExtras(), c, callState);

        performed.clear();
        for (Test t : tests) {
            if(t.accept(visitor)){
                performed.add(t);
            }
        }
    }

    private static class Box<A> {
        A boxed;
        Box(A x){
            this.boxed = x;
        }
    }

    public class TajsTestVisitor implements TestVisitor<Boolean> {

        private final StateExtras se;
        private final Solver.SolverInterface c;
        private final PropVarOperations pv;
        private final State s;
        private final TypeValuesHandler typeValuesHandler;

        TajsTestVisitor(StateExtras se, Solver.SolverInterface c, State s) {
            this.se = se;
            this.s = s;
            this.pv = c.getAnalysis().getPropVarOperations();
            this.c = c;
            this.typeValuesHandler = new TypeValuesHandler(typeNames, se);
        }

        public boolean attemptAddValue(Value v, TypeWithContext t, Test test) {
            v = UnknownValueResolver.getRealValue(v, s);
            Pair<List<TypeChecker.TypeViolation>, Value> tcResult = TypeChecker.typeCheckAndFilter(t, v, s, c, test);
            List<TypeChecker.TypeViolation> violations = tcResult.getFirst();
            Value filteredValue = tcResult.getSecond();
            if(violations.isEmpty() && !filteredValue.isNone()) {
                typeValuesHandler.addValueForType(t, filteredValue);
                if(DEBUG) System.out.println("Value added for type:" + t + " in test " + test);
                return true;
            }
            else {
                if(c.isScanning()) {
                    allViolations.addAll(violations);
                }
                return false;
            }
        }

        public Value attemptGetValue(TypeWithContext t, Test test) {
            return typeValuesHandler.findValueForType(t);
        }


        @Override
        public Boolean visit(PropertyReadTest test) {
            boolean perfomed = false;
            Value baseValuesValue = attemptGetValue(new TypeWithContext(test.getBaseType(),test.getTypeContext()), test);
            Set<ObjectLabel> splittenObjectLabels = baseValuesValue.getObjectLabels();
            for (ObjectLabel l : splittenObjectLabels) {
                Value propertyValue = pv.readPropertyDirect(l, Value.makePKeyValue(PKey.mk(test.getProperty())));
                perfomed |= attemptAddValue(propertyValue, new TypeWithContext(test.getPropertyType(), test.getTypeContext()), test);
            }

            return perfomed;
        }

        @Override
        public Boolean visit(LoadModuleTest test) {
            Box<Boolean> performed = new Box<>(false);
            c.withState(c.getState(), () -> {
                ObjectLabel moduleObject = ObjectLabel.mk(ECMAScriptObjects.OBJECT_MODULE, ObjectLabel.Kind.OBJECT);
                Value v = pv.readPropertyDirect(moduleObject, Value.makeStr("exports"));
                performed.boxed |= attemptAddValue(v, new TypeWithContext(test.getModuleType(), test.getTypeContext()), test);
            });
            return performed.boxed;
        }

        @Override
        public Boolean visit(MethodCallTest test) {
            Value receiverValue = attemptGetValue(new TypeWithContext(test.getObject(), test.getTypeContext()), test);
            List<Value> argumentsValues = test.getParameters().stream().map(paramType -> attemptGetValue(new TypeWithContext(paramType, test.getTypeContext()), test)).collect(Collectors.toList());

            Value propertyValue = pv.readPropertyValue(receiverValue.getAllObjectLabels(), Value.makePKeyValue(PKey.mk(test.getPropertyName())));
            //TODO: Filter this value ! ::  propertyValue = new TypeValuesFilter(propertyValue, propertyType)

            List<Value> returnedValues = propertyValue.getAllObjectLabels().stream().map( l -> {

                BasicBlock implicitAfterCall = UserFunctionCalls.implicitUserFunctionCall(l, new FunctionCalls.CallInfo() {

                    @Override
                    public AbstractNode getSourceNode() {
                        return c.getNode();
                    }

                    @Override
                    public AbstractNode getJSSourceNode() {
                        return c.getNode();
                    }

                    @Override
                    public boolean isConstructorCall() {
                        return false;
                    }

                    @Override
                    public Value getFunctionValue() {
                        throw new AnalysisException();
                    }

                    @Override
                    public Value getThis(State caller_state, State callee_state) {
                        return receiverValue;
                    }

                    @Override
                    public Value getArg(int i) {
                        return Value.makeUndef();
                    }

                    @Override
                    public int getNumberOfArgs() {
                        return 0;
                    }

                    @Override
                    public Value getUnknownArg() {
                        return Value.makeUndef();
                    }

                    @Override
                    public boolean isUnknownNumberOfArgs() {
                        return false;
                    }

                    @Override
                    public int getResultRegister() {
                        return AbstractNode.NO_VALUE;
                    }

                    @Override
                    public ExecutionContext getExecutionContext() {
                        return c.getState().getExecutionContext();
                    }
                }, c);

                return UserFunctionCalls.implicitUserFunctionReturn(newList(), false, implicitAfterCall, c);
            }).collect(Collectors.toList());

            List<Boolean> added = returnedValues.stream().map(v -> attemptAddValue(v, new TypeWithContext(test.getReturnType(), test.getTypeContext()), test)).collect(Collectors.toList());
            return added.stream().anyMatch(x -> x.equals(true));
        }

        @Override
        public Boolean visit(ConstructorCallTest test) {
            return false;
        }

        @Override
        public Boolean visit(FunctionCallTest test) {
            return false;
        }

        @Override
        public Boolean visit(FilterTest test) {
            return false;
        }

        @Override
        public Boolean visit(UnionTypeTest test) {
            return false;
        }

        @Override
        public Boolean visit(NumberIndexTest test) {
            return false;
        }

        @Override
        public Boolean visit(StringIndexTest test) {
            return false;
        }

        @Override
        public Boolean visit(PropertyWriteTest test) {
            return false;
        }
    }
}
