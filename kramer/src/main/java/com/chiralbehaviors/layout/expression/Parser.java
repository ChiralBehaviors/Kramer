/**
 * Copyright (c) 2016 Chiral Behaviors, LLC, all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chiralbehaviors.layout.expression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.chiralbehaviors.layout.expression.Expr.*;

/**
 * Recursive descent parser for the expression language (RDR-021).
 * <p>
 * Grammar precedence (highest to lowest): unary !/-, *, /, +/-, comparisons,
 * &amp;&amp;, ||.
 *
 * @author hhildebrand
 */
public final class Parser {

    private static final int MAX_LENGTH = 4096;
    private static final int MAX_DEPTH = 100;

    private static final Set<String> AGGREGATE_FUNCTIONS = Set.of(
        "sum", "count", "avg", "min", "max");

    private static final Set<String> SCALAR_FUNCTIONS = Set.of(
        "len", "upper", "lower", "abs", "round", "if");

    private final List<Token> tokens;
    private int pos;
    private int depth;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    /**
     * Parse an expression string into an AST.
     *
     * @param input the expression string
     * @return the parsed AST
     * @throws ParseException if the input is malformed
     */
    public static Expr parse(String input) throws ParseException {
        if (input == null || input.isBlank()) {
            throw new ParseException("Empty expression", 0);
        }
        if (input.length() > MAX_LENGTH) {
            throw new ParseException(
                "Expression too long (exceeds 4096 characters)", 0);
        }
        var lexer = new Lexer(input);
        var tokens = lexer.tokenize();
        var parser = new Parser(tokens);
        var expr = parser.parseOrExpr();
        parser.expect(Token.Type.EOF, "Unexpected token after expression");
        return expr;
    }

    // --- Precedence climbing ---

    private void checkDepth() throws ParseException {
        if (++depth > MAX_DEPTH) {
            throw new ParseException("Expression nesting too deep (exceeds " + MAX_DEPTH + ")", peek().offset());
        }
    }

    // or-expr ::= and-expr ( "||" and-expr )*
    private Expr parseOrExpr() throws ParseException {
        checkDepth();
        var left = parseAndExpr();
        while (check(Token.Type.PIPE_PIPE)) {
            advance();
            var right = parseAndExpr();
            left = new BinaryOp(BinaryOp.Op.OR, left, right);
        }
        return left;
    }

    // and-expr ::= not-expr ( "&&" not-expr )*
    private Expr parseAndExpr() throws ParseException {
        var left = parseNotExpr();
        while (check(Token.Type.AMP_AMP)) {
            advance();
            var right = parseNotExpr();
            left = new BinaryOp(BinaryOp.Op.AND, left, right);
        }
        return left;
    }

    // not-expr ::= "!" not-expr | compare-expr
    private Expr parseNotExpr() throws ParseException {
        checkDepth();
        if (check(Token.Type.BANG)) {
            advance();
            var operand = parseNotExpr();
            return new UnaryOp(UnaryOp.Op.NOT, operand);
        }
        return parseCompareExpr();
    }

    // compare-expr ::= add-expr ( compare-op add-expr )?
    private Expr parseCompareExpr() throws ParseException {
        var left = parseAddExpr();
        var op = matchCompareOp();
        if (op != null) {
            advance();
            var right = parseAddExpr();
            left = new BinaryOp(op, left, right);
        }
        return left;
    }

    private BinaryOp.Op matchCompareOp() {
        var type = peek().type();
        return switch (type) {
            case EQ  -> BinaryOp.Op.EQ;
            case NEQ -> BinaryOp.Op.NEQ;
            case LT  -> BinaryOp.Op.LT;
            case GT  -> BinaryOp.Op.GT;
            case LTE -> BinaryOp.Op.LTE;
            case GTE -> BinaryOp.Op.GTE;
            default  -> null;
        };
    }

    // add-expr ::= mul-expr ( ("+" | "-") mul-expr )*
    private Expr parseAddExpr() throws ParseException {
        var left = parseMulExpr();
        while (check(Token.Type.PLUS) || check(Token.Type.MINUS)) {
            var op = advance().type() == Token.Type.PLUS
                ? BinaryOp.Op.ADD : BinaryOp.Op.SUB;
            var right = parseMulExpr();
            left = new BinaryOp(op, left, right);
        }
        return left;
    }

    // mul-expr ::= unary-expr ( ("*" | "/") unary-expr )*
    private Expr parseMulExpr() throws ParseException {
        var left = parseUnaryExpr();
        while (check(Token.Type.STAR) || check(Token.Type.SLASH)) {
            var op = advance().type() == Token.Type.STAR
                ? BinaryOp.Op.MUL : BinaryOp.Op.DIV;
            var right = parseUnaryExpr();
            left = new BinaryOp(op, left, right);
        }
        return left;
    }

    // unary-expr ::= "-" unary-expr | primary
    private Expr parseUnaryExpr() throws ParseException {
        checkDepth();
        if (check(Token.Type.MINUS)) {
            advance();
            var operand = parseUnaryExpr();
            return new UnaryOp(UnaryOp.Op.NEG, operand);
        }
        return parsePrimary();
    }

    // primary ::= literal | field-ref | function-call | "(" expression ")"
    private Expr parsePrimary() throws ParseException {
        var token = peek();

        return switch (token.type()) {
            case NUMBER -> {
                advance();
                yield new Literal(Double.parseDouble(token.text()));
            }
            case STRING -> {
                advance();
                yield new Literal(token.text());
            }
            case IDENT -> parseIdentPrimary(token);
            case FIELD_REF -> {
                advance();
                yield new FieldRef(List.of(token.text()));
            }
            case FIELD_PATH_REF -> {
                advance();
                yield new FieldRef(Arrays.asList(token.text().split("\\.")));
            }
            case LPAREN -> {
                advance();
                var inner = parseOrExpr();
                expect(Token.Type.RPAREN, "Expected ')'");
                yield inner;
            }
            default -> throw new ParseException(
                "Unexpected token: " + token.text(), token.offset());
        };
    }

    private Expr parseIdentPrimary(Token token) throws ParseException {
        String name = token.text();

        // Keywords
        if ("true".equals(name)) { advance(); return new Literal(true); }
        if ("false".equals(name)) { advance(); return new Literal(false); }
        if ("null".equals(name)) { advance(); return new Literal(null); }

        // Must be a function call — check next token is '('
        if (pos + 1 >= tokens.size() || tokens.get(pos + 1).type() != Token.Type.LPAREN) {
            throw new ParseException(
                "Unknown identifier '" + name + "'; use $" + name + " for field references",
                token.offset());
        }

        if (AGGREGATE_FUNCTIONS.contains(name)) {
            return parseAggregateCall(token);
        } else if (SCALAR_FUNCTIONS.contains(name)) {
            return parseScalarCall(token);
        } else {
            throw new ParseException(
                "Unknown function '" + name + "'", token.offset());
        }
    }

    private Expr parseAggregateCall(Token nameToken) throws ParseException {
        String fn = nameToken.text();
        advance(); // skip name
        expect(Token.Type.LPAREN, "Expected '(' after function name");

        if ("count".equals(fn)) {
            expect(Token.Type.RPAREN, "count() takes no arguments");
            return new AggregateCall(fn, null);
        }

        // Other aggregates require exactly one argument
        if (check(Token.Type.RPAREN)) {
            throw new ParseException(
                fn + "() requires an argument", peek().offset());
        }
        var arg = parseOrExpr();
        expect(Token.Type.RPAREN, "Expected ')' after aggregate argument");
        return new AggregateCall(fn, arg);
    }

    private Expr parseScalarCall(Token nameToken) throws ParseException {
        String name = nameToken.text();
        advance(); // skip name
        expect(Token.Type.LPAREN, "Expected '(' after function name");

        var args = new ArrayList<Expr>();
        if (!check(Token.Type.RPAREN)) {
            args.add(parseOrExpr());
            while (check(Token.Type.COMMA)) {
                advance();
                args.add(parseOrExpr());
            }
        }
        expect(Token.Type.RPAREN, "Expected ')' after function arguments");
        // Arity validation
        int arity = args.size();
        switch (name) {
            case "if" -> {
                if (arity != 3) throw new ParseException(
                    "if() requires exactly 3 arguments, got " + arity, nameToken.offset());
            }
            case "len", "upper", "lower", "abs", "round" -> {
                if (arity != 1) throw new ParseException(
                    name + "() requires exactly 1 argument, got " + arity, nameToken.offset());
            }
            default -> {}
        }
        return new ScalarCall(name, List.copyOf(args));
    }

    // --- Token helpers ---

    private Token peek() {
        return tokens.get(pos);
    }

    private boolean check(Token.Type type) {
        return peek().type() == type;
    }

    private Token advance() {
        var token = tokens.get(pos);
        pos++;
        return token;
    }

    private void expect(Token.Type type, String message) throws ParseException {
        if (!check(type)) {
            var token = peek();
            throw new ParseException(message + " (found '" + token.text() + "')",
                token.offset());
        }
        advance();
    }
}
