package plc.project;

import java.util.List;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException
    {
        List<Ast.Global> globals = new java.util.ArrayList<Ast.Global>();
        List<Ast.Function> functions = new java.util.ArrayList<Ast.Function>();
        if (!tokens.has(0))
        {
            return new Ast.Source(globals, functions);
        }
        while (peek("LIST") || peek("VAR") || peek("VAL"))
        {
            globals.add(parseGlobal());
        }
        while (peek("FUN"))
        {
            functions.add(parseFunction());
        }
        if (tokens.has(0))
        {
            throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
        return new Ast.Source(globals, functions);
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Global parseGlobal() throws ParseException
    {
        if (match("LIST"))
        {
            return parseList();
        }
        else if (match("VAR"))
        {
            return parseMutable();
        }
        else if (match("VAL"))
        {
            return parseImmutable();
        }
        else
        {
            throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
    }

    /**
     * Parses the {@code list} rule. This method should only be called if the
     * next token declares a list, aka {@code LIST}.
     */
    public Ast.Global parseList() throws ParseException {
        if (match(Token.Type.IDENTIFIER) && (peek(Token.Type.OPERATOR) && match(":")) && match(Token.Type.IDENTIFIER))
        {
            //TODO: adjust for 2 more matches, -1 -> -3
            String name = tokens.get(-3).getLiteral();
            String typename = tokens.get(-1).getLiteral();
            if (match("=") && match("["))
            {
                List<Ast.Expression> list = new java.util.ArrayList<Ast.Expression>();
                while (!peek("]") && tokens.has(0))
                {
                    Ast.Expression expr = parseExpression();
                    list.add(expr);
                    if (match(",") && peek("]"))
                    {
                        throw new ParseException("Hanging comma", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                    }

                }
                if (!match("]"))
                {
                    throw new ParseException("Missing closing bracket", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                }
                if (!match(";"))
                {
                    throw new ParseException("Missing semicolon", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                }
                Ast.Expression.PlcList plclist = new Ast.Expression.PlcList(list);
                return new Ast.Global(name, typename, true, java.util.Optional.of(plclist));
            }
            else
            {
                throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
        }
        else
        {
            throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
    }

    /**
     * Parses the {@code mutable} rule. This method should only be called if the
     * next token declares a mutable global variable, aka {@code VAR}.
     */
    public Ast.Global parseMutable() throws ParseException
    {
        if (match(Token.Type.IDENTIFIER) && (peek(Token.Type.OPERATOR) && match(":")) && match(Token.Type.IDENTIFIER))
        {
            //TODO: adjust index to account for 2 more matches, -1 -> -3
            String name = tokens.get(-3).getLiteral();
            String typename = tokens.get(-1).getLiteral();
            if (match("="))
            {
                java.util.Optional<Ast.Expression> expr = java.util.Optional.of(parseExpression());
                if (!match(";"))
                {
                    throw new ParseException("Missing semicolon", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                }
                return new Ast.Global(name, typename, true, expr);
            }
            else
            {
                return new Ast.Global(name, typename, true, java.util.Optional.empty());
            }
        }
        else
        {
            throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
    }

    /**
     * Parses the {@code immutable} rule. This method should only be called if the
     * next token declares an immutable global variable, aka {@code VAL}.
     */
    public Ast.Global parseImmutable() throws ParseException
    {
        if (match(Token.Type.IDENTIFIER) && (peek(Token.Type.OPERATOR) && match(":")) && match(Token.Type.IDENTIFIER))
        {
            //TODO: adjust for 2 more matches, -1 -> -3
            String name = tokens.get(-3).getLiteral();
            String typename = tokens.get(-1).getLiteral();
            if (match("="))
            {
                java.util.Optional<Ast.Expression> expr = java.util.Optional.of(parseExpression());
                if (!match(";"))
                {
                    throw new ParseException("Missing semicolon", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                }
                return new Ast.Global(name, typename, false, expr);
            }
            else
            {
                throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
        }
        else
        {
            throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Function parseFunction() throws ParseException
    {
        if (match("FUN") && match(Token.Type.IDENTIFIER))
        {
            String name = tokens.get(-1).getLiteral();
            if (match("("))
            {
                List<List<String>> parameters = new java.util.ArrayList<List<String>>();
                if (match(Token.Type.IDENTIFIER))
                {
                    if (match(":") && match(Token.Type.IDENTIFIER))
                    {
                        List<String> parameter = new java.util.ArrayList<String>();
                        parameter.add(tokens.get(-3).getLiteral());
                        parameter.add(tokens.get(-1).getLiteral());
                        parameters.add(parameter);
                        while (match(","))
                        {
                            if (match(Token.Type.IDENTIFIER) && match(":") && match(Token.Type.IDENTIFIER))
                            {
                                List<String> parameter2 = new java.util.ArrayList<String>();
                                parameter2.add(tokens.get(-3).getLiteral());
                                parameter2.add(tokens.get(-1).getLiteral());
                                parameters.add(parameter2);
                            }
                            else
                            {
                                throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                            }
                        }
                    }
                    else
                    {
                        throw new ParseException("Missing parameter type", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    }
                }

                if (match(")"))
                {
                    boolean hasType = false;
                    String typename = "";
                    if (match(":"))
                    {
                        if (match(Token.Type.IDENTIFIER))
                        {
                            hasType = true;
                            typename = tokens.get(-1).getLiteral();
                        }
                        else
                        {
                            throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                        }
                    }
                    if (match("DO"))
                    {
                        List<Ast.Statement> statements = parseBlock();
                        if (match("END"))
                        {

                            java.util.ArrayList<String> parameterNames = new java.util.ArrayList<String>();
                            java.util.ArrayList<String> parameterTypeNames = new java.util.ArrayList<String>();
                            for (java.util.List<String> pair : parameters)
                            {
                                parameterNames.add(pair.get(0));
                                parameterTypeNames.add(pair.get(1));
                            }

                            if (hasType)
                            {
                                return new Ast.Function(name, parameterNames, parameterTypeNames, java.util.Optional.of(typename), statements);
                            }
                            else
                            {
                                return new Ast.Function(name, parameterNames, parameterTypeNames, java.util.Optional.empty(), statements);
                            }
                        }
                        else
                        {
                            throw new ParseException("Unexpected end of block", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                        }
                    }
                    else
                    {
                        throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                    }

                }
                else
                {
                    throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                }
            }
            else
            {
                throw new ParseException("Missing opening parenthesis", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
        }
        else
        {
            throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
    }

    /**
     * Parses the {@code block} rule. This method should only be called if the
     * preceding token indicates the opening a block.
     */
    public List<Ast.Statement> parseBlock() throws ParseException {
        List<Ast.Statement> l = new java.util.ArrayList<Ast.Statement>();
        while (!peek("ELSE") && !peek("END") && !peek("DEFAULT") && !peek("CASE"))
        {
            if (!tokens.has(0))
            {
                throw new ParseException("Unterminated block", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            l.add(parseStatement());
            match(";");
        }
        return l;
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Statement parseStatement() throws ParseException
    {
        // TODO: Part b
        //return new Ast.Statement.Expression(parseExpression());
        if (match("LET"))
        {
            return parseDeclarationStatement();
        }
        else if (match("IF"))
        {
            return parseIfStatement();
        }
        else if (match("WHILE"))
        {
            return parseWhileStatement();
        }
        else if (match("SWITCH"))
        {
            return parseSwitchStatement();
        }
        else if (match("RETURN"))
        {
            return parseReturnStatement();
        }
        else
        {
            // either assignment or expression
            Ast.Expression rec = parseExpression();
            if (peek("=") && match(Token.Type.OPERATOR))
            {
                Ast.Expression val = parseExpression();
                if (match(";"))
                {
                    return new Ast.Statement.Assignment(rec, val);
                }
                else
                {
                    throw new ParseException("Missing semicolon", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                }
            }
            else
            {
                if (match(";"))
                {
                    return new Ast.Statement.Expression(rec);
                }
                else
                {
                    throw new ParseException("Missing semicolon", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                }

            }

        }

    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
        if (match(Token.Type.IDENTIFIER))
        {
            String typename = "";
            boolean hasType = false;
            String name = tokens.get(-1).getLiteral();
            if (match(":") && match(Token.Type.IDENTIFIER))
            {
                hasType = true;
                typename = tokens.get(-1).getLiteral();
            }
            if (match("="))
            {
                //String name = tokens.get(-2).getLiteral();
                java.util.Optional<Ast.Expression> expr = java.util.Optional.of(parseExpression());
                if (match(";"))
                {
                    if (hasType)
                    {
                        return new Ast.Statement.Declaration(name, java.util.Optional.of(typename), expr);
                    }
                    return new Ast.Statement.Declaration(name, java.util.Optional.empty(), expr);
                }
                else
                {
                    throw new ParseException("Missing Semicolon", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                }

            }
            if (match(";"))
            {
                if (hasType)
                {
                    return new Ast.Statement.Declaration(name, java.util.Optional.of(typename), java.util.Optional.empty());
                }
                return new Ast.Statement.Declaration(name, java.util.Optional.empty(), java.util.Optional.empty());
            }
            else
            {
                throw new ParseException("Missing Semicolon", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
        }
        else
        {
            throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
        Ast.Expression expr = parseExpression();
        if (match("DO"))
        {
            List<Ast.Statement> l1 = parseBlock();
            List<Ast.Statement> l2 = new java.util.ArrayList<Ast.Statement>();
            if (match("ELSE"))
            {
                l2 = parseBlock();
            }
            if (match("END"))
            {
                return new Ast.Statement.If(expr, l1, l2);
            }
        }
        throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
    }

    /**
     * Parses a switch statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a switch statement, aka
     * {@code SWITCH}.
     */
    public Ast.Statement.Switch parseSwitchStatement() throws ParseException {
        Ast.Expression expr = parseExpression();
        List<Ast.Statement.Case> cases = new java.util.ArrayList<Ast.Statement.Case>();
        while (match("CASE")) // potentially infinite case statements
        {
            Ast.Statement.Case a = parseCaseStatement();
            cases.add(a);
        }
        if (match("DEFAULT"))
        {
            List<Ast.Statement> defaultBlock = parseBlock();
            Ast.Statement.Case defaultCase = new Ast.Statement.Case(java.util.Optional.empty(), defaultBlock);
            cases.add(defaultCase);
            if (match("END"))
            {
                return new Ast.Statement.Switch(expr, cases);
            }
            else
            {
                throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
        }
        else
        {
            throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
    }

    /**
     * Parses a case or default statement block from the {@code switch} rule. 
     * This method should only be called if the next tokens start the case or 
     * default block of a switch statement, aka {@code CASE} or {@code DEFAULT}.
     */
    public Ast.Statement.Case parseCaseStatement() throws ParseException
    {
        java.util.Optional<Ast.Expression> expr = java.util.Optional.of(parseExpression());
        if (match(":"))
        {
            List<Ast.Statement> list = parseBlock();
            return new Ast.Statement.Case(expr, list);
        }
        else
        {
            throw new ParseException("Missing colon", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException
    {
        Ast.Expression expr = parseExpression();
        if (match("DO"))
        {
            List<Ast.Statement> l = parseBlock();
            if (match("END"))
            {
                return new Ast.Statement.While(expr, l);
            }else
            {
                throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
        }
        else
        {
            throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Statement.Return parseReturnStatement() throws ParseException {
        Ast.Expression expr = parseExpression();
        if (match(";"))
        {
            return new Ast.Statement.Return(expr);
        }
        else
        {
            throw new ParseException("Missing semicolon", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expression parseExpression() throws ParseException {
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expression parseLogicalExpression() throws ParseException {
        plc.project.Ast.Expression e = parseComparisonExpression();
        while ((peek("&&") || peek("||")) && match(Token.Type.OPERATOR)) {
            Token operator = tokens.get(-1);
            plc.project.Ast.Expression right = parseComparisonExpression();
            e = new plc.project.Ast.Expression.Binary(operator.getLiteral(), e, right);
        }

        return e;

    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expression parseComparisonExpression() throws ParseException {
        plc.project.Ast.Expression e = parseAdditiveExpression();

        while ((peek("<=") || peek(">=") || peek("==") || peek("!=")) && match(Token.Type.OPERATOR) ) {
            Token operator = tokens.get(-1);
            plc.project.Ast.Expression right = parseAdditiveExpression();
            e = new plc.project.Ast.Expression.Binary(operator.getLiteral(), e, right);
        }

        return e;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expression parseAdditiveExpression() throws ParseException {
        plc.project.Ast.Expression e = parseMultiplicativeExpression();

        while ((peek("-") || peek("+")) && match(Token.Type.OPERATOR)) {
            Token operator = tokens.get(-1);
            plc.project.Ast.Expression right = parseMultiplicativeExpression();
            e = new plc.project.Ast.Expression.Binary(operator.getLiteral(), e, right);
        }

        return e;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
        plc.project.Ast.Expression e = parsePrimaryExpression();

        while ((peek("*") || peek("/")) && match(Token.Type.OPERATOR)) {
            Token operator = tokens.get(-1);
            plc.project.Ast.Expression right = parsePrimaryExpression();
            e = new plc.project.Ast.Expression.Binary(operator.getLiteral(), e, right);
        }

        return e;
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expression parsePrimaryExpression() throws ParseException {

        if (peek("FALSE") && match(Token.Type.IDENTIFIER)) return new plc.project.Ast.Expression.Literal(false);
        if (peek("TRUE") && match(Token.Type.IDENTIFIER)) return new plc.project.Ast.Expression.Literal(true);
        if (peek("NIL") && match(Token.Type.IDENTIFIER)) return new plc.project.Ast.Expression.Literal(null);

        if (match(Token.Type.INTEGER)) {
            return new plc.project.Ast.Expression.Literal(new java.math.BigInteger(tokens.get(-1).getLiteral()));
        }
        if (match(Token.Type.DECIMAL)) {
            return new plc.project.Ast.Expression.Literal(new java.math.BigDecimal(tokens.get(-1).getLiteral()));
        }
        if (match(Token.Type.STRING))
        {
            String currentToken = tokens.get(-1).getLiteral();
            StringBuilder s = new StringBuilder();
            for (int i = 1; i < currentToken.length()-1; i++)
            {
                if (currentToken.charAt(i) == '\\')
                {
                    if (currentToken.charAt(i+1) == 'b')
                    {
                        s.append('\b');
                        i++;
                    }
                    else if (currentToken.charAt(i+1) == 'n')
                    {
                        s.append('\n');
                        i++;
                    }
                    else if (currentToken.charAt(i+1) == 't')
                    {
                        s.append('\t');
                        i++;
                    }
                    else if (currentToken.charAt(i+1) == 'r')
                    {
                        s.append('\r');
                        i++;
                    }
                    else if (currentToken.charAt(i+1) == '\\')
                    {
                        s.append('\\');
                        i++;
                    }
                    else if (currentToken.charAt(i+1) == '\"')
                    {
                        s.append('\"');
                        i++;
                    }
                }
                else
                {
                    s.append(currentToken.charAt(i));
                }
            }
            return new plc.project.Ast.Expression.Literal(s.toString());
        }
        if (match(Token.Type.CHARACTER))
        {
            String currentToken = tokens.get(-1).getLiteral();
            if (currentToken.length() != 3) // handle escapes
            {
                if (currentToken.charAt(2) == 'b')
                {
                    return new plc.project.Ast.Expression.Literal('\b');
                }
                if (currentToken.charAt(2) == 'n')
                {
                    return new plc.project.Ast.Expression.Literal('\n');
                }
                if (currentToken.charAt(2) == 'r')
                {
                    return new plc.project.Ast.Expression.Literal('\r');
                }
                if (currentToken.charAt(2) == 't')
                {
                    return new plc.project.Ast.Expression.Literal('\t');
                }
                if (currentToken.charAt(2) == '\\')
                {
                    return new plc.project.Ast.Expression.Literal('\\');
                }
                if (currentToken.charAt(2) == '\"')
                {
                    return new plc.project.Ast.Expression.Literal('\"');
                }
            }
            return new plc.project.Ast.Expression.Literal(currentToken.charAt(1));
        }

        if (peek("(") && match(Token.Type.OPERATOR)) {
            plc.project.Ast.Expression e = parseExpression();
            if (match(")"))
            {
                return new plc.project.Ast.Expression.Group(e);
            }
            else
            {
                throw new ParseException("Missing closing parenthesis", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
        }
        if (peek(Token.Type.OPERATOR))
        {
            throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }

        if (match(Token.Type.IDENTIFIER))
        {
            if (peek(Token.Type.OPERATOR))
            {
                if (match("(")) // TODO: Function calls
                {
                    String name = tokens.get(-2).getLiteral();
                    List<Ast.Expression> l = new java.util.ArrayList<Ast.Expression>();
                    while (match(")") == false)
                    {
                        l.add(parseExpression());
                        if (match(",") && peek(")"))
                        {
                            throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                        }
                    }
                    return new Ast.Expression.Function(name, l);
                }
                else if (match("[")) // TODO: List access
                {
                    java.util.Optional<Ast.Expression> e = java.util.Optional.of(parseExpression());
                    if (match("]"))
                    {
                        return new Ast.Expression.Access(e, tokens.get(-4).getLiteral());
                    }
                    else
                    {
                        throw new ParseException("Missing closing square bracket", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                    }
                }
                else
                {
                    return new Ast.Expression.Access(java.util.Optional.empty(), tokens.get(-1).getLiteral());
                }
            }
            else
            {
                return new Ast.Expression.Access(java.util.Optional.empty(), tokens.get(-1).getLiteral());
            }
        }
        throw new ParseException("Unexpected token", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length()); //TODO
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) { // TODO: Lecture
         for (int i = 0; i < patterns.length; i++)
         {
             if (!tokens.has(i))
             {
                 return false;
             }
             else if (patterns[i] instanceof Token.Type)
             {
                 if (patterns[i] != tokens.get(i).getType())
                 {
                     return false;
                 }
             }
             else if (patterns[i] instanceof String)
             {
                 if (!patterns[i].equals(tokens.get(i).getLiteral()))
                 {
                     return false;
                 }
             }
             else
             {
                 throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());
             }
         }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) { //TODO (in lecture)
        boolean peek = peek(patterns);
        if (peek)
        {
            for (int i = 0; i < patterns.length; i++)
            {
                tokens.advance();
            }
        }
        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
