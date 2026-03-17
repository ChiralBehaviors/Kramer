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

import java.util.List;
import java.util.Optional;

/**
 * Sealed AST hierarchy for the expression language (RDR-021).
 *
 * @author hhildebrand
 */
public sealed interface Expr
        permits Expr.Literal, Expr.FieldRef, Expr.BinaryOp, Expr.UnaryOp,
                Expr.ScalarCall, Expr.AggregateCall {

    /** A constant value: Double, String, Boolean, or null. */
    record Literal(Object value) implements Expr {}

    /** A field reference: $name or ${a.b.c}. Path segments for nested access. */
    record FieldRef(List<String> path) implements Expr {}

    /** A binary operation. */
    record BinaryOp(Op op, Expr left, Expr right) implements Expr {
        public enum Op {
            ADD, SUB, MUL, DIV,
            EQ, NEQ, LT, GT, LTE, GTE,
            AND, OR
        }
    }

    /** A unary operation. */
    record UnaryOp(Op op, Expr operand) implements Expr {
        public enum Op { NEG, NOT }
    }

    /** A scalar function call: len, upper, lower, abs, round, if. */
    record ScalarCall(String name, List<Expr> args) implements Expr {}

    /** An aggregate function call: sum, count, avg, min, max. count() has empty arg. */
    record AggregateCall(String fn, Optional<Expr> arg) implements Expr {}
}
