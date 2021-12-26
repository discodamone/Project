package plc.project;

import java.io.PrintWriter;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {

        // 1. class header
        print("public class Main {");
        newline(indent);

        // 2. if there are globals...
        if (ast.getGlobals().size() > 0)
        {
            indent+=1;
            newline(indent);
            for (int i = 0; i < ast.getGlobals().size(); i++)
            {
                visit(ast.getGlobals().get(i));
                if (i != ast.getGlobals().size()-1)
                {
                    newline(indent);
                }
            }
            indent-=1;
            newline(indent);
        }

        // 3. main method
        indent+=1;
        newline(indent);
        print("public static void main(String[] args) {");
        indent+=1;
        newline(indent);
        print("System.exit(new Main().main());");
        indent-=1;
        newline(indent);
        print("}");
        indent-=1;
        newline(indent);


        // 4. the source's functions

        for (Ast.Function f : ast.getFunctions())
        {
            indent+=1;
            newline(indent);
            visit(f);
            indent-=1;
            newline(indent);
        }

        // 5. closing brace
        newline(indent);
        print("}");


        return null;
    }

    @Override
    public Void visit(Ast.Global ast) {

        if (!ast.getMutable())
        {
            writer.write("final ");
        }
        //TODO: need to change this for cases when value is not present
        if (ast.getValue().isPresent() && ast.getValue().get() instanceof Ast.Expression.PlcList)
        {
            print(ast.getVariable().getType().getJvmName(), "[] ");
        }
        else
        {
            writer.write(toJvmName(ast.getTypeName()) + " ");
        }
        print(ast.getName());
        if (ast.getValue().isPresent())
        {
            print(" = ");
            if (ast.getValue().get() instanceof Ast.Expression.PlcList)
            {

                print("{");
                boolean first = true;
                for (Ast.Expression e : ((Ast.Expression.PlcList)ast.getValue().get()).getValues())
                {
                    if (!first)
                    {
                        print(", ");
                    }
                    print(e);
                    first = false;
                }
                print("}");
            }
            else
            {
                print(ast.getValue().get());
            }
        }
        print(";");



        return null;
    }

    @Override
    public Void visit(Ast.Function ast) {

        print(ast.getFunction().getReturnType().getJvmName(), " ", ast.getFunction().getJvmName(), "(");
        boolean first = true;
        for (int i = 0; i < ast.getFunction().getParameterTypes().size(); i++)
        {
            if (!first)
            {
                print(", ");
            }
            print(ast.getFunction().getParameterTypes().get(i).getJvmName(), " ", ast.getParameters().get(i));
            first = false;
        }
        print(") {");
        if (ast.getStatements().size() == 0)
        {
            print("}");
            return null;
        }
        indent+=1;
        for (Ast.Statement s : ast.getStatements())
        {
            newline(indent);
            visit(s);
        }
        indent-=1;
        newline(indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        print(ast.getExpression(),";");
        return null;
    }

    public String toJvmName(String s)
    {
        //TODO: check for other types
        if (s.equals("Decimal"))
        {
            return "double";
        }
        else if (s.equals("Integer"))
        {
            return "int";
        }
        else
        {
            return s;
        }
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {

        if (ast.getValue().isPresent())
        {
            writer.write(ast.getValue().get().getType().getJvmName() + " ");
        }
        else
        {
            writer.write(toJvmName(ast.getTypeName().get()) + " ");
        }
        writer.write(ast.getName());
        if (ast.getValue().isPresent())
        {
            writer.write(" = ");
            print(ast.getValue().get());
        }
        writer.write(";");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {

        print(ast.getReceiver(), " = ", ast.getValue(), ";");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {

        writer.write("if (");
        visit(ast.getCondition());
        writer.write(") {");
        indent+=1;
        for (Ast.Statement e : ast.getThenStatements())
        {
            newline(indent);
            visit(e);
        }
        indent-=1;
        newline(indent);
        writer.write("}");
        if (!ast.getElseStatements().isEmpty())
        {
            writer.write(" else {");
            indent+=1;
            for (Ast.Statement e : ast.getElseStatements())
            {
                newline(indent);
                visit(e);
            }
            indent-=1;
            newline(indent);
            writer.write("}");
        }


        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {

        print("switch (", ast.getCondition(), ") {");
        indent+=1;
        for (int i = 0; i < ast.getCases().size(); i++)
        {
            newline(indent);
            visit(ast.getCases().get(i));
        }
        indent-=1;
        newline(indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {

        if (ast.getValue().isPresent())
        {
            // case
            print("case ", ast.getValue().get(), ":");
            indent+=1;
            for (Ast.Statement s : ast.getStatements())
            {
                newline(indent);
                visit(s);
            }
            indent-=1;


        }
        else
        {
            // default
            print("default:");
            indent+=1;
            for (Ast.Statement s : ast.getStatements())
            {
                newline(indent);
                visit(s);
            }
            indent-=1;

        }


        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {

        print("while (", ast.getCondition(), ") {");
        // handle empty statements case
        if (ast.getStatements().size() == 0)
        {
            print("}");
            return null;
        }
        // list statements if not empty
        indent+=1;
        for (Ast.Statement e : ast.getStatements())
        {
            newline(indent);
            visit(e);
        }
        indent-=1;
        newline(indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        print("return ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        // assignment says we do not need to convert back to escape sequences
        if (ast.getLiteral() instanceof String)
        {
            writer.print("\"" + String.valueOf(ast.getLiteral()) + "\"");
        }
        else if (ast.getLiteral() instanceof Character)
        {
            writer.print("'" + String.valueOf(ast.getLiteral()) + "'");
        }
        else
        {
            writer.print(String.valueOf(ast.getLiteral()));
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        print("(", ast.getExpression(), ")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        visit(ast.getLeft());
        writer.write(" " + ast.getOperator() + " ");
        visit(ast.getRight());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {

        if (ast.getOffset().isPresent())
        {
            print(ast.getVariable().getJvmName());
            print("[", ast.getOffset().get(), "]");
        }
        else
        {
            print(ast.getVariable().getJvmName());
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        String params = "";
        boolean first = true;
        writer.write(ast.getFunction().getJvmName()+ "(");
        for (Ast.Expression s : ast.getArguments())
        {
            if (!first)
            {
                writer.write(",");
            }
            print(s);
            first = false;
        }
        writer.write(")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {

        writer.write("{");
        for (int i = 0; i < ast.getValues().size(); i++)
        {
            if (i != 0 && i < ast.getValues().size()-1)
            {
                writer.write(", ");
            }
            print(ast.getValues().get(i));
        }
        writer.write("}");

        return null;
    }

}
