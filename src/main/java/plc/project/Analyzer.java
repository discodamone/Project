package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Function function;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {

        for (Ast.Global g : ast.getGlobals())
        {
            visit(g);
        }
        boolean mainExists = false;
        boolean mainTypeInt = false;
        for (Ast.Function f : ast.getFunctions())
        {
            visit(f);
            if (f.getName().equals("main"))
            {
                if (f.getParameters().size() == 0)
                {
                    mainExists = true;
                    if (f.getReturnTypeName().isPresent() && f.getReturnTypeName().get().equals("Integer"))
                    {
                        mainTypeInt = true;
                    }
                }
            }
        }
        if (!mainExists || !mainTypeInt)
        {
            throw new RuntimeException("Missing main/0 function");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Global ast) {

        Environment.Variable ev = new Environment.Variable(ast.getName(), ast.getName(), checkType(ast.getTypeName()), ast.getMutable(), Environment.NIL);
        if (ast.getValue().isPresent())
        {
            visit(ast.getValue().get());
            requireAssignable(checkType(ast.getTypeName()), ast.getValue().get().getType());
        }

        ast.setVariable(ev);
        scope.defineVariable(ast.getName(), ast.getName(), checkType(ast.getTypeName()), ast.getMutable(), Environment.NIL);
        return null;
    }

    public static Environment.Type checkType(String s)
    {
        Environment.Type type = Environment.Type.NIL;
        switch (s)
        {
            case "Boolean":
                type = Environment.Type.BOOLEAN;
                break;
            case "Integer":
                type = Environment.Type.INTEGER;
                break;
            case "Nil":
                type = Environment.Type.NIL;
                break;
            case "Character":
                type = Environment.Type.CHARACTER;
                break;
            case "String":
                type = Environment.Type.STRING;
                break;
            case "Decimal":
                type = Environment.Type.DECIMAL;
                break;
            case "Any":
                type = Environment.Type.ANY;
                break;
            case "Comparable":
                type = Environment.Type.COMPARABLE;
                break;
            default:
                throw new RuntimeException("Bad type");
        }
        return type;
    }

    @Override
    public Void visit(Ast.Function ast) {
        java.util.ArrayList<Environment.Type> types = new java.util.ArrayList<>();
        for (String s : ast.getParameters())
        {
            types.add(scope.lookupVariable(s).getType());
        }
        Environment.Type returnType = Environment.Type.NIL;
        if (ast.getReturnTypeName().isPresent())
        {
            if (ast.getReturnTypeName().get().equals("Integer"))
            {
                returnType = Environment.Type.INTEGER;
            }
            else if (ast.getReturnTypeName().get().equals("Any"))
            {
                returnType = Environment.Type.ANY;
            }
            else if (ast.getReturnTypeName().get().equals("Boolean"))
            {
                returnType = Environment.Type.BOOLEAN;
            }
            else if (ast.getReturnTypeName().get().equals("String"))
            {
                returnType = Environment.Type.STRING;
            }
            else if (ast.getReturnTypeName().get().equals("Character"))
            {
                returnType = Environment.Type.CHARACTER;
            }
            else if (ast.getReturnTypeName().get().equals("Comparable"))
            {
                returnType = Environment.Type.COMPARABLE;
            }
            else if (ast.getReturnTypeName().get().equals("Decimal"))
            {
                returnType = Environment.Type.DECIMAL;
            }
        }
        scope.defineFunction(ast.getName(), ast.getName(), types, returnType, args -> Environment.NIL);
        ast.setFunction(new Environment.Function(ast.getName(), ast.getName(), types, returnType, args -> Environment.NIL));
        scope.defineVariable("$RETURNTYPE", "$RETURNTYPE", returnType, false, Environment.NIL);

        scope = new Scope(scope);
        try {
            for (Ast.Statement stmt : ast.getStatements()) {
                visit(stmt);
            }
        } finally
        {
            scope = scope.getParent();
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        Ast.Expression e = ast.getExpression();
        if (!(e instanceof Ast.Expression.Function))
        {
            throw new RuntimeException("Not a function");
        }
        visit(e);
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        //TODO: mutable check
        Environment.Type type = Environment.Type.NIL;
        if (ast.getValue().isPresent())
        {
            // use this value for type
            visit(ast.getValue().get());
            type = ast.getValue().get().getType();
        }
        else
        {
            // match name to type
            if (!ast.getTypeName().isPresent())
            {
                throw new RuntimeException("No type provided");
            }
            type = checkType(ast.getTypeName().get());

        }
        scope.defineVariable(ast.getName(), ast.getName(), type, true, Environment.NIL);
        Environment.Variable ev = new Environment.Variable(ast.getName(), ast.getName(), type, true, Environment.NIL);
        ast.setVariable(ev);
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        if (!(ast.getReceiver() instanceof Ast.Expression.Access))
        {
            throw new RuntimeException("Receiver is not an access expression.");
        }
        visit(ast.getValue());
        visit(ast.getReceiver());
        requireAssignable(ast.getReceiver().getType(), ast.getValue().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        Ast.Expression cond = ast.getCondition();
        visit(cond);
        if (cond.getType() != Environment.Type.BOOLEAN)
        {
            throw new RuntimeException("Condition must be a boolean");
        }
        if (ast.getThenStatements().isEmpty())
        {
            throw new RuntimeException("Empty statements list");
        }
        else
        {
            try
            {
                scope = new Scope(scope);
                for (Ast.Statement e : ast.getThenStatements())
                {
                    visit(e);
                }
            }
            finally
            {
                scope = scope.getParent();
            }
        }
        try
        {
            scope = new Scope(scope);
            for (Ast.Statement e : ast.getElseStatements())
            {
                visit(e);
            }
        }
        finally
        {
            scope = scope.getParent();
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        visit(ast.getCondition());
        for (int i = 0; i < ast.getCases().size(); i++)
        {
            try {
                scope = new Scope(scope);
                Ast.Statement.Case c = ast.getCases().get(i);
                visit(c);
                if (c.getValue().isPresent() && c.getValue().get().getType() != ast.getCondition().getType()) {
                    throw new RuntimeException("Case type does not match switch type");
                }
                if (i == ast.getCases().size() - 1) {
                    if (c.getValue().isPresent()) {
                        throw new RuntimeException("Default case cannot have a value");
                    }
                }
            }
            finally
            {
                scope = scope.getParent();
            }
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {

        if (ast.getValue().isPresent())
        {
            visit(ast.getValue().get());
        }
        for (int i = 0; i < ast.getStatements().size(); i++)
        {
            visit(ast.getStatements().get(i));
        }


        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        try
        {
            scope = new Scope(scope);
        }
        finally
        {
            scope = scope.getParent();
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {

        visit(ast.getValue());
        Environment.Variable ev = scope.lookupVariable("$RETURNTYPE");
        requireAssignable(ast.getValue().getType(), ev.getType());

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        Object o = ast.getLiteral();
        if (o instanceof BigInteger)
        {
            if (((java.math.BigInteger)o).compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0 || ((java.math.BigInteger)o).compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0)
            {
                throw new RuntimeException("Integer too big or too small");
            }
            ast.setType(Environment.Type.INTEGER);

        }
        else if (o instanceof BigDecimal)
        {
            if (((java.math.BigDecimal)o).doubleValue() == Double.POSITIVE_INFINITY || ((java.math.BigDecimal)o).doubleValue() == Double.NEGATIVE_INFINITY)
            {
                throw new RuntimeException("Decimal too big or too small");
            }
            ast.setType(Environment.Type.DECIMAL);
        }
        else if (o instanceof Boolean)
        {
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if (o instanceof Character)
        {
            ast.setType(Environment.Type.CHARACTER);
        }
        else if (o instanceof String)
        {
            ast.setType(Environment.Type.STRING);
        }
        else if (o == null)
        {
            ast.setType(Environment.Type.NIL);
        }
        else
        {
            throw new RuntimeException("Invalid literal type");
        }


        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {

        Ast.Expression e = ast.getExpression();
        visit(e);
        if (!(e instanceof Ast.Expression.Binary))
        {
            throw new RuntimeException("Not a binary expression in the group");
        }
        ast.setType(e.getType());

        return null;
    }

    private boolean isComparable(Environment.Type type){

        if (type.getName().equals("Comparable") || type.getName().equals("String") || type.getName().equals("Character") || type.getName().equals("Integer") || type.getName().equals("Decimal"))
        {
            return true;
        }
        return false;

    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        visit(ast.getRight());
        visit(ast.getLeft());
        ast.setType(Environment.Type.NIL);
        if (ast.getOperator().equals("&&") || ast.getOperator().equals("||"))
        {
            if (!(ast.getLeft().getType() == Environment.Type.BOOLEAN && ast.getRight().getType() == Environment.Type.BOOLEAN))
            {
                throw new RuntimeException("Both operands must be boolean");


            }
            else {
                ast.setType(Environment.Type.BOOLEAN);
                return null;
            }
        }
        else if (ast.getOperator().equals(">") || ast.getOperator().equals("<") || ast.getOperator().equals("==") || ast.getOperator().equals("!="))
        {
            if (!(isComparable(ast.getLeft().getType()) && isComparable(ast.getRight().getType())) || !(ast.getLeft().getType() != ast.getRight().getType()))
            {
                throw new RuntimeException("Both operands must be comparable and the same type");
            }
            else {
                ast.setType(Environment.Type.BOOLEAN);
                return null;
            }
        }
        else if (ast.getOperator().equals("+"))
        {
            // string concatenation
            if (ast.getLeft().getType().getName().equals("String") || ast.getRight().getType().getName().equals("String"))
            {
                ast.setType(Environment.Type.STRING);
                return null;
            }
            else
            {
                if ((ast.getLeft().getType().getName().equals("Integer") || ast.getLeft().getType().getName().equals("Decimal")) && ast.getRight().getType() == ast.getLeft().getType())
                {
                    ast.setType(ast.getLeft().getType());
                    return null;
                }else {
                    throw new RuntimeException("Invalid addition");
                }
            }

        }
        else if (ast.getOperator().equals("-") || ast.getOperator().equals("/") || ast.getOperator().equals("*"))
        {
            if ((ast.getLeft().getType().getName().equals("Integer") || ast.getLeft().getType().getName().equals("Decimal")) && ast.getRight().getType() == ast.getLeft().getType())
            {
                ast.setType(ast.getLeft().getType());
                return null;
            }else {
                throw new RuntimeException("Invalid arithmetic");
            }
        }
        else if (ast.getOperator().equals("^"))
        {
            if ((ast.getLeft().getType().getName().equals("Integer") || ast.getLeft().getType().getName().equals("Decimal")) && ast.getRight().getType().getName().equals("Integer"))
            {
                ast.setType(ast.getLeft().getType());
                return null;
            }else {
                throw new RuntimeException("Invalid exponent");
            }
        }else
        {
            throw new RuntimeException("Invalid operator" + ast.getOperator());
        }
    }


    @Override
    public Void visit(Ast.Expression.Access ast) {

        if (ast.getOffset().isPresent())
        {
            visit(ast.getOffset().get());
            if (ast.getOffset().get().getType() != Environment.Type.INTEGER)
            {
                throw new RuntimeException("Offset is not an integer");
            }
        }
        ast.setVariable(scope.lookupVariable(ast.getName()));
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        java.util.ArrayList<Environment.Type> types = new java.util.ArrayList<>();

        for (int i = 0; i < ast.getArguments().size(); i++)
        {
            Ast.Expression e = ast.getArguments().get(i);
            visit(e);
            types.add(e.getType());
        }
        Environment.Function lookedupfunc = scope.lookupFunction(ast.getName(), types.size());
        for (int i = 0; i < lookedupfunc.getParameterTypes().size(); i++)
        {
            Environment.Type t = lookedupfunc.getParameterTypes().get(i);
            requireAssignable(t, types.get(i));
        }
        ast.setFunction(new Environment.Function(lookedupfunc.getName(), lookedupfunc.getJvmName(), lookedupfunc.getParameterTypes(), lookedupfunc.getReturnType(), args -> Environment.NIL));
        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        boolean first = true;
        for (Ast.Expression e : ast.getValues())
        {
            visit(e);
            if (first)
            {
                ast.setType(checkType(e.getType().getName()));
                first = false;
            }
            requireAssignable(ast.getType(), e.getType());
        }


        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        if (target.getName() == type.getName())
        {
            return;
        }
        if (target.getName().equals("Any"))
        {
            return;
        }
        if (target.getName().equals("Comparable") && (type.getName().equals("Integer") || type.getName().equals("Decimal") || type.getName().equals("Character") || type.getName().equals("String" )))
        {
            return;
        }
        throw new RuntimeException("Cannot assign " + type + " to " + target);

    }

}
