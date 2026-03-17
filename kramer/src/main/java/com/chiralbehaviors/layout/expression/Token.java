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

/**
 * A token produced by the expression lexer.
 *
 * @author hhildebrand
 */
record Token(Type type, String text, int offset) {

    enum Type {
        NUMBER, STRING, IDENT,
        FIELD_REF,       // $name
        FIELD_PATH_REF,  // ${a.b.c}
        PLUS, MINUS, STAR, SLASH,
        BANG, AMP_AMP, PIPE_PIPE,
        EQ, NEQ, LT, GT, LTE, GTE,
        LPAREN, RPAREN, COMMA,
        EOF
    }
}
