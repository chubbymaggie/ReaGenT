package dk.webbies.tajscheck.parsespec.flow;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dk.au.cs.casa.typescript.SpecReader;
import dk.au.cs.casa.typescript.types.DelayedType;
import dk.au.cs.casa.typescript.types.InterfaceType;
import dk.au.cs.casa.typescript.types.Type;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class TypeNameCreator {
    private final BiFunction<JsonObject, String, DelayedType> parseType;

    static Type lookUp(Map<String, Type> namedTypes, String nameContext, String name) {
        if (namedTypes.containsKey(name)) {
            return namedTypes.get(name);
        }
        if (nameContext.equals("")) {
            return namedTypes.get(name);
        }
        while (true) {
            String key = nameContext + "." + name;
            if (namedTypes.containsKey(key)) {
                return namedTypes.get(key);
            }
            if (nameContext.contains(".")) {
                nameContext = nameContext.substring(0, nameContext.lastIndexOf("."));
            } else {
                break;
            }
        }
        return null;
    }

    private DelayedType parseType(JsonObject obj, String typeContext) {
        return parseType.apply(obj, typeContext);
    }

    TypeNameCreator(BiFunction<JsonObject, String, DelayedType> parseType) {
        this.parseType = parseType;
    }

    Map<String, DelayedType> createTypeNames(JsonArray body) {
        Map<String, DelayedType> result = new HashMap<>();
        for (JsonElement rawStatement : body) {
            parseModuleStatement("", result, rawStatement.getAsJsonObject());
        }


        return result;
    }

    private Map<String, DelayedType> parseModule(JsonObject statement, String nameContext) {
        JsonObject id = statement.get("id").getAsJsonObject();
        assert id.get("type").getAsString().equals("Literal");

        nameContext = newNameContext(nameContext, statement.get("id").getAsJsonObject().get("raw").getAsString());

        assert statement.get("body").getAsJsonObject().get("type").getAsString().equals("BlockStatement");

        Map<String, DelayedType> result = new HashMap<>();

        List<JsonObject> moduleStatements = Lists.newArrayList(statement.get("body").getAsJsonObject().get("body").getAsJsonArray()).stream().map(JsonObject.class::cast).collect(Collectors.toList());

        for (JsonObject moduleStatementRaw : moduleStatements) {
            JsonObject moduleStatement = moduleStatementRaw.getAsJsonObject();
            parseModuleStatement(nameContext, result, moduleStatement);
        }

        if (moduleStatements.stream().anyMatch(stmt -> stmt.get("type").getAsString().equals("DeclareModuleExports"))) {
            //noinspection ConstantConditions
            JsonObject exports = moduleStatements.stream().filter(stmt -> stmt.get("type").getAsString().equals("DeclareModuleExports")).findFirst().get();
            DelayedType type = this.parseType(exports.get("typeAnnotation").getAsJsonObject(), nameContext);
            result.put(nameContext, type);
        }

        return result;
    }

    private String newNameContext(String previous, String name) {
        if (previous.isEmpty()) {
            return name;
        }
        return previous + "." + name;
    }

    private void parseModuleStatement(String nameContext, Map<String, DelayedType> result, JsonObject moduleStatement) {
        switch (moduleStatement.get("type").getAsString()) {
            case "DeclareModuleExports":
                // Does not produce a named type, and it has already been registered as the type for the module.
                break;
            case "DeclareExportDeclaration":{ // For now, everthing is assumed to be exported. Which is not an issue for my purpose.
                JsonObject declaration = moduleStatement.get("declaration").getAsJsonObject();
                parseModuleStatement(nameContext, result, declaration);
                break;
            }
            case "DeclareVariable": {
                JsonObject id = moduleStatement.get("id").getAsJsonObject();
                String name = id.get("name").getAsString();
                DelayedType type = parseType(id.get("typeAnnotation").getAsJsonObject(), nameContext);
                result.put(newNameContext(nameContext, name), type);
                break;
            }
            case "InterfaceDeclaration": {
                assert moduleStatement.get("extends").getAsJsonArray().size() == 0;
                assert moduleStatement.get("typeParameters").isJsonNull();
                JsonObject id = moduleStatement.get("id").getAsJsonObject();
                String name = id.get("name").getAsString();
                DelayedType type = parseType(moduleStatement.get("body").getAsJsonObject(), nameContext);
                result.put(newNameContext(nameContext, name), type);
                break;
            }
            case "DeclareTypeAlias":
            case "TypeAlias":
                String name = moduleStatement.get("id").getAsJsonObject().get("name").getAsString();
                assert moduleStatement.get("typeParameters").isJsonNull();
                result.put(newNameContext(nameContext, name), parseType(moduleStatement.get("right").getAsJsonObject(), nameContext));
                break;
            case "DeclareClass":
                result.put(
                        newNameContext(nameContext, moduleStatement.get("id").getAsJsonObject().get("name").getAsString()),
                        parseType(moduleStatement, nameContext)
                );
                break;
            case "DeclareModule":
                result.putAll(parseModule(moduleStatement, nameContext));
                break;
            default:
                throw new RuntimeException(moduleStatement.get("type").getAsString());
        }
    }

}
