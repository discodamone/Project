package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });

        scope.defineFunction("logarithm", 1, args -> {
            if (!(args.get(0).getValue() instanceof BigDecimal))
            {
                throw new RuntimeException("Expected type BigDecimal, received " + args.get(0).getValue().getClass().getName());
            }

            BigDecimal bd1 = (BigDecimal) args.get(0).getValue();

            BigDecimal bd2 = requireType(BigDecimal.class, Environment.create(args.get(0).getValue()));

            BigDecimal result = BigDecimal.valueOf(Math.log(bd2.doubleValue()));
            return Environment.create(result);
        });

        scope.defineFunction("converter", 2, args ->{
            BigInteger decimal = requireType(BigInteger.class, Environment.create(args.get(0).getValue()));
            BigInteger base = requireType(BigInteger.class, Environment.create(args.get(1).getValue()));

            String number = new String();
            int i, n = 0;

            ArrayList<BigInteger> quotients = new ArrayList<>();
            ArrayList<BigInteger> remainders = new ArrayList<>();

            quotients.add(decimal);
            do
            {
                quotients.add(quotients.get(n).divide(base));
                remainders.add(quotients.get(n).subtract(quotients.get(n+1).multiply(base)));
                n++;
            }
            while (quotients.get(n).compareTo(BigInteger.ZERO) > 0);

            for (i = 0; i < remainders.size(); i++)
            {
                number = remainders.get(i).toString() + number;
            }

            return Environment.create(number);
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        Environment.PlcObject mainOutput = Environment.NIL;
        boolean hasMain = false;
        for (plc.project.Ast.Global g : ast.getGlobals())
        {
            visit(g);
        }
        for (plc.project.Ast.Function f : ast.getFunctions())
        {
            Environment.PlcObject current = visit(f);
        }
        mainOutput = scope.lookupFunction("main",0).invoke(new ArrayList<Environment.PlcObject>());
        return mainOutput;
    }

    @Override
    public Environment.PlcObject visit(Ast.Global ast) {
        // TODO: globals
        // set the variable
        if (ast.getValue().isPresent())
        {
            scope.defineVariable(ast.getName(), ast.getMutable(), visit(ast.getValue().get()));
        }
        else
        {
            scope.defineVariable(ast.getName(), ast.getMutable(), Environment.NIL);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Function ast) {
        scope.defineFunction(ast.getName(), ast.getParameters().size(), args ->{
            try
            {
                scope = new Scope(scope);
                for (int i = 0; i < args.size(); i++)
                {
                    scope.defineVariable(ast.getParameters().get(i), true, args.get(i));
                }
                for (Ast.Statement stmt : ast.getStatements())
                {
                    visit(stmt);
                }
            }
            catch(Return r)
            {
                return r.value;
            }
            finally
            {
                scope = scope.getParent();
            }
            return Environment.NIL;
        });
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast)
    {
        //throw new UnsupportedOperationException(); //TODO (in lecture)
        java.util.Optional optional = ast.getValue();
        Boolean present = optional.isPresent();
        String name = ast.getName();
        //throw new RuntimeException(name);
        if (present)
        {
            Ast.Expression expression = (Ast.Expression) optional.get();
            scope.defineVariable(name, true, visit(expression));
        }else
        {
            scope.defineVariable(name, true, Environment.NIL);
        }


        return Environment.NIL;

    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        if (ast.getReceiver() instanceof Ast.Expression.Access)
        {
            Ast.Expression.Access acc = (Ast.Expression.Access) ast.getReceiver();
            // check to see if already defined
            try {
                Environment.Variable ev = scope.lookupVariable(acc.getName());
                if (!ev.getMutable())
                {
                    throw new RuntimeException("Non mutable");
                }
                if (acc.getOffset().isPresent())
                {
                    List<Object> p = (List<Object>) scope.lookupVariable(acc.getName()).getValue().getValue();
                    BigInteger b = requireType(BigInteger.class, visit(acc.getOffset().get()));
                    p.set(b.intValue(), visit(ast.getValue()).getValue());
                    ev.setValue(Environment.create(p));
                }
                else
                {
                    ev.setValue(visit(ast.getValue()));
                }

            }
            catch(Exception e)
            {
                if (acc.getOffset().isPresent())
                {
                    List<Object> p = (List<Object>) scope.lookupVariable(acc.getName()).getValue().getValue();
                    BigInteger b = requireType(BigInteger.class, visit(acc.getOffset().get()));
                    p.set(b.intValue(), visit(ast.getValue()).getValue());
                    scope.defineVariable(acc.getName(), true, Environment.create(p));

                }
                else
                {
                    scope.defineVariable(acc.getName(), true, visit(ast.getValue()));
                }
            }


            return Environment.NIL;
        }
        throw new RuntimeException("Only Ast.Expression.Access is assignable");

    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {

        if (requireType(Boolean.class, visit(ast.getCondition())))
        {
            try
            {
                scope = new Scope(scope);
                for (Ast.Statement stmt : ast.getThenStatements())
                {
                    visit(stmt);
                }
            }
            finally
            {
                scope = scope.getParent();
            }
        }
        else
        {
            try
            {
                scope = new Scope(scope);
                for (Ast.Statement stmt : ast.getElseStatements())
                {
                    visit(stmt);
                }
            }
            finally
            {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Switch ast) {
        //TODO: switch
        {
            try
            {
                scope = new Scope(scope);
                Environment.PlcObject cond = visit(ast.getCondition());
                boolean matched = false;
                for (Ast.Statement.Case stmt : ast.getCases())
                {
                    if (visit(stmt).getValue().equals(cond.getValue()))
                    {
                        matched = true;
                        for (Ast.Statement inner : stmt.getStatements())
                        {
                            visit(inner);
                        }
                    }
                }
                if (!matched) // default case
                {
                    for (Ast.Statement.Case stmt : ast.getCases())
                    {
                        if (visit(stmt).equals(Environment.NIL))
                        {
                            for (Ast.Statement inner : stmt.getStatements())
                            {
                                visit(inner);
                            }
                        }
                    }
                }
            }
            finally
            {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Case ast) {
        if (ast.getValue().isPresent())
        {
            return visit(ast.getValue().get());
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {

        while (requireType(Boolean.class, visit(ast.getCondition())))
        {
            try
            {
                scope = new Scope(scope);
                for (Ast.Statement stmt : ast.getStatements())
                {
                    visit(stmt);
                }
            }
            finally
            {
                scope = scope.getParent();
            }

        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {
        throw new Return(visit(ast.getValue()));
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {
        if (ast.getLiteral() == null)
        {
            return Environment.NIL;
        }
        return Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {

        if (ast.getOperator().equals("&&"))
        {
            Environment.PlcObject leftObj = visit(ast.getLeft());
            boolean b1 = requireType(Boolean.class, leftObj);
            if (b1 == false)
            {
                return Environment.create(false);
            }
            Environment.PlcObject rightObj = visit(ast.getRight());
            boolean b2 = requireType(Boolean.class, rightObj);
            return Environment.create(b1 && b2);
        }
        else if (ast.getOperator().equals("||"))
        {
            Environment.PlcObject leftObj = visit(ast.getLeft());
            boolean b1 = requireType(Boolean.class, leftObj);
            if (b1 == true)
            {
                return Environment.create(true);
            }
            Environment.PlcObject rightObj = visit(ast.getRight());
            boolean b2 = requireType(Boolean.class, rightObj);
            return Environment.create(b1 || b2);
        }
        else if (ast.getOperator().equals("<"))
        {
            Environment.PlcObject leftObj = visit(ast.getLeft());
            Comparable b1 = requireType(Comparable.class, leftObj);
            Environment.PlcObject rightObj = visit(ast.getRight());
            Comparable b2 = requireType(Comparable.class, rightObj);
            return Environment.create(b1.compareTo(b2) < 0);
        }
        else if (ast.getOperator().equals(">"))
        {
            Environment.PlcObject leftObj = visit(ast.getLeft());
            Comparable b1 = requireType(Comparable.class, leftObj);
            Environment.PlcObject rightObj = visit(ast.getRight());
            Comparable b2 = requireType(Comparable.class, rightObj);
            return Environment.create(b1.compareTo(b2) > 0);
        }
        else if (ast.getOperator().equals("=="))
        {
            Environment.PlcObject leftObj = visit(ast.getLeft());
            Environment.PlcObject rightObj = visit(ast.getRight());
            return Environment.create(leftObj.equals(rightObj));
        }
        else if (ast.getOperator().equals("!="))
        {
            Environment.PlcObject leftObj = visit(ast.getLeft());
            Environment.PlcObject rightObj = visit(ast.getRight());
            return Environment.create(!leftObj.equals(rightObj));
        }
        else if (ast.getOperator().equals("+"))
        {
            Environment.PlcObject leftObj = visit(ast.getLeft());
            Environment.PlcObject rightObj = visit(ast.getRight());
            if (leftObj.getValue() instanceof String || rightObj.getValue() instanceof String)
            {
                // concatenation
                return Environment.create(leftObj.getValue().toString().concat(rightObj.getValue().toString()));
            }
            else if (leftObj.getValue() instanceof BigDecimal && rightObj.getValue() instanceof BigDecimal)
            {
                BigDecimal b1 = requireType(BigDecimal.class, leftObj);
                BigDecimal b2 = requireType(BigDecimal.class, rightObj);
                return Environment.create(b1.add(b2));
            }
            else if (leftObj.getValue() instanceof BigInteger && rightObj.getValue() instanceof BigInteger)
            {
                BigInteger b1 = requireType(BigInteger.class, leftObj);
                BigInteger b2 = requireType(BigInteger.class, rightObj);
                return Environment.create(b1.add(b2));
            }
            throw new RuntimeException("Invalid addition between " + leftObj.getClass().toString() + " and " + rightObj.getClass().toString());
        }
        else if (ast.getOperator().equals("-") || ast.getOperator().equals("*"))
        {
            Environment.PlcObject leftObj = visit(ast.getLeft());
            Environment.PlcObject rightObj = visit(ast.getRight());
            if (leftObj.getValue() instanceof BigDecimal && rightObj.getValue() instanceof BigDecimal)
            {
                BigDecimal b1 = requireType(BigDecimal.class, leftObj);
                BigDecimal b2 = requireType(BigDecimal.class, rightObj);
                if (ast.getOperator().equals("-"))
                {
                    return Environment.create(b1.add(b2));
                }
                else
                {
                    return Environment.create(b1.multiply(b2));
                }
            }
            else if (leftObj.getValue() instanceof BigInteger && rightObj.getValue() instanceof BigInteger)
            {
                BigInteger b1 = requireType(BigInteger.class, leftObj);
                BigInteger b2 = requireType(BigInteger.class, rightObj);
                if (ast.getOperator().equals("-"))
                {
                    return Environment.create(b1.add(b2));
                }
                else
                {
                    return Environment.create(b1.multiply(b2));
                }
            }
            throw new RuntimeException("Invalid arithmetic between " + leftObj.getClass().toString() + " and " + rightObj.getClass().toString());
        }
        else if (ast.getOperator().equals("/"))
        {
            Environment.PlcObject leftObj = visit(ast.getLeft());
            Environment.PlcObject rightObj = visit(ast.getRight());
            if (leftObj.getValue() instanceof BigDecimal && rightObj.getValue() instanceof BigDecimal)
            {
                BigDecimal b1 = requireType(BigDecimal.class, leftObj);
                BigDecimal b2 = requireType(BigDecimal.class, rightObj);
                return Environment.create(b1.divide(b2, java.math.RoundingMode.HALF_EVEN));
            }
            else if (leftObj.getValue() instanceof BigInteger && rightObj.getValue() instanceof BigInteger)
            {
                BigInteger b1 = requireType(BigInteger.class, leftObj);
                BigInteger b2 = requireType(BigInteger.class, rightObj);

                return Environment.create(b1.divide(b2));
            }
            throw new RuntimeException("Invalid division between " + leftObj.getClass().toString() + " and " + rightObj.getClass().toString());
        }
        else if (ast.getOperator().equals("^"))
        {
            Environment.PlcObject leftObj = visit(ast.getLeft());
            Environment.PlcObject rightObj = visit(ast.getRight());
            BigInteger exp = requireType(BigInteger.class, rightObj);
            if (leftObj.getValue() instanceof BigInteger)
            {
                BigInteger b1 = requireType(BigInteger.class, leftObj);
                BigInteger b2 = b1;
                while (exp.compareTo(BigInteger.ZERO) > 0)
                {
                    b2 = b2.multiply(b1);
                }
                if (exp.compareTo(BigInteger.ZERO) < 0)
                {
                    b2 = BigInteger.ONE;
                }
                while (exp.compareTo(BigInteger.ZERO) < 0)
                {

                    b2 = b2.divide(b1);
                }
                return Environment.create(b2);
            }
            else if (leftObj.getValue() instanceof BigDecimal)
            {
                BigDecimal b1 = requireType(BigDecimal.class, leftObj);
                BigDecimal b2 = b1;
                while (exp.compareTo(BigInteger.ZERO) > 0)
                {
                    b2 = b2.multiply(b1);
                }
                if (exp.compareTo(BigInteger.ZERO) < 0)
                {
                    b2 = BigDecimal.ZERO;
                }
                while (exp.compareTo(BigInteger.ZERO) < 0)
                {
                    b2 = b2.divide(b1);
                }
                return Environment.create(b2);
            }
            throw new RuntimeException("Invalid arithmetic between " + leftObj.getClass().toString() + " and " + rightObj.getClass().toString());
        }
        else
        {
            throw new RuntimeException("Invalid operator " + ast.getOperator());
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {
        //TODO: implement list access
        if (ast.getOffset().isPresent())
        {
            BigInteger i = requireType(BigInteger.class, visit(ast.getOffset().get()));
            List<Ast.Expression> p = (List<Ast.Expression>) scope.lookupVariable(ast.getName()).getValue().getValue();
            return Environment.create(p.get(i.intValue()));
        }
        return Environment.create(scope.lookupVariable(ast.getName()).getValue().getValue());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        ArrayList<Environment.PlcObject> list =  new ArrayList<>();
        for (Ast.Expression e : ast.getArguments())
        {
            list.add(visit(e));
        }
        return scope.lookupFunction(ast.getName(), ast.getArguments().size()).invoke(list);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.PlcList ast) {
        ArrayList<Object> al = new ArrayList<>();
        for (Ast.Expression e : ast.getValues())
        {
            al.add(visit(e).getValue());
        }
        return Environment.create(al);
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
