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
import java.util.List;

/**
 * Single-pass tokenizer for the expression language.
 *
 * @author hhildebrand
 */
final class Lexer {

    private final String input;
    private int pos;

    Lexer(String input) {
        this.input = input;
        this.pos = 0;
    }

    List<Token> tokenize() throws ParseException {
        var tokens = new ArrayList<Token>();
        while (pos < input.length()) {
            skipWhitespace();
            if (pos >= input.length()) break;

            int start = pos;
            char c = input.charAt(pos);

            switch (c) {
                case '+' -> { tokens.add(new Token(Token.Type.PLUS, "+", start)); pos++; }
                case '-' -> { tokens.add(new Token(Token.Type.MINUS, "-", start)); pos++; }
                case '*' -> { tokens.add(new Token(Token.Type.STAR, "*", start)); pos++; }
                case '/' -> { tokens.add(new Token(Token.Type.SLASH, "/", start)); pos++; }
                case '(' -> { tokens.add(new Token(Token.Type.LPAREN, "(", start)); pos++; }
                case ')' -> { tokens.add(new Token(Token.Type.RPAREN, ")", start)); pos++; }
                case ',' -> { tokens.add(new Token(Token.Type.COMMA, ",", start)); pos++; }
                case '!' -> {
                    pos++;
                    if (pos < input.length() && input.charAt(pos) == '=') {
                        tokens.add(new Token(Token.Type.NEQ, "!=", start));
                        pos++;
                    } else {
                        tokens.add(new Token(Token.Type.BANG, "!", start));
                    }
                }
                case '=' -> {
                    pos++;
                    if (pos < input.length() && input.charAt(pos) == '=') {
                        tokens.add(new Token(Token.Type.EQ, "==", start));
                        pos++;
                    } else {
                        throw new ParseException("Expected '==' but found '='", start);
                    }
                }
                case '&' -> {
                    pos++;
                    if (pos < input.length() && input.charAt(pos) == '&') {
                        tokens.add(new Token(Token.Type.AMP_AMP, "&&", start));
                        pos++;
                    } else {
                        throw new ParseException("Expected '&&' but found '&'", start);
                    }
                }
                case '|' -> {
                    pos++;
                    if (pos < input.length() && input.charAt(pos) == '|') {
                        tokens.add(new Token(Token.Type.PIPE_PIPE, "||", start));
                        pos++;
                    } else {
                        throw new ParseException("Expected '||' but found '|'", start);
                    }
                }
                case '<' -> {
                    pos++;
                    if (pos < input.length() && input.charAt(pos) == '=') {
                        tokens.add(new Token(Token.Type.LTE, "<=", start));
                        pos++;
                    } else {
                        tokens.add(new Token(Token.Type.LT, "<", start));
                    }
                }
                case '>' -> {
                    pos++;
                    if (pos < input.length() && input.charAt(pos) == '=') {
                        tokens.add(new Token(Token.Type.GTE, ">=", start));
                        pos++;
                    } else {
                        tokens.add(new Token(Token.Type.GT, ">", start));
                    }
                }
                case '"' -> tokens.add(readString());
                case '$' -> tokens.add(readFieldRef());
                default -> {
                    if (Character.isDigit(c)) {
                        tokens.add(readNumber());
                    } else if (isIdentStart(c)) {
                        tokens.add(readIdentifier());
                    } else {
                        throw new ParseException(
                            "Unexpected character: '" + c + "'", start);
                    }
                }
            }
        }
        tokens.add(new Token(Token.Type.EOF, "", pos));
        return tokens;
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private Token readString() throws ParseException {
        int start = pos;
        pos++; // skip opening "
        var sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '"') {
                pos++;
                return new Token(Token.Type.STRING, sb.toString(), start);
            }
            if (c == '\\') {
                pos++;
                if (pos >= input.length()) {
                    throw new ParseException("Unterminated escape sequence", pos);
                }
                char esc = input.charAt(pos);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    default -> throw new ParseException(
                        "Unknown escape sequence: \\" + esc, pos - 1);
                }
            } else {
                sb.append(c);
            }
            pos++;
        }
        throw new ParseException("Unterminated string literal", start);
    }

    private Token readFieldRef() throws ParseException {
        int start = pos;
        pos++; // skip $
        if (pos >= input.length()) {
            throw new ParseException("Expected field name after '$'", start);
        }
        char c = input.charAt(pos);
        if (c == '{') {
            // ${field.path}
            pos++; // skip {
            int pathStart = pos;
            while (pos < input.length() && input.charAt(pos) != '}') {
                pos++;
            }
            if (pos >= input.length()) {
                throw new ParseException("Unterminated field path reference", start);
            }
            String path = input.substring(pathStart, pos);
            pos++; // skip }
            if (path.isEmpty()) {
                throw new ParseException("Empty field path reference", start);
            }
            return new Token(Token.Type.FIELD_PATH_REF, path, start);
        } else if (isIdentStart(c)) {
            int nameStart = pos;
            while (pos < input.length() && isIdentPart(input.charAt(pos))) {
                pos++;
            }
            return new Token(Token.Type.FIELD_REF, input.substring(nameStart, pos), start);
        } else {
            throw new ParseException("Expected field name after '$'", start);
        }
    }

    private Token readNumber() {
        int start = pos;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        if (pos < input.length() && input.charAt(pos) == '.'
                && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1))) {
            pos++; // skip dot
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
        }
        return new Token(Token.Type.NUMBER, input.substring(start, pos), start);
    }

    private Token readIdentifier() {
        int start = pos;
        while (pos < input.length() && isIdentPart(input.charAt(pos))) {
            pos++;
        }
        return new Token(Token.Type.IDENT, input.substring(start, pos), start);
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || (c >= '0' && c <= '9');
    }
}
