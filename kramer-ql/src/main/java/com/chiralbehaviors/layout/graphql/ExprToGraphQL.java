// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.graphql;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.chiralbehaviors.layout.expression.Expr;

import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;

/**
 * Translates simple Expr AST trees into GraphQL argument Values
 * for server-side filter push-down. Only simple comparisons are
 * translatable; complex expressions (ScalarCall, AggregateCall,
 * arithmetic) fall back to client-side evaluation.
 * <p>
 * The translation follows the Hasura/GraphQL convention:
 * <ul>
 *   <li>{@code $price > 100} → {@code {price: {_gt: 100}}}</li>
 *   <li>{@code $a > 1 && $b < 5} → {@code {a: {_gt: 1}, b: {_lt: 5}}}</li>
 *   <li>{@code $a > 1 || $b < 5} → {@code {_or: [{a: {_gt: 1}}, {b: {_lt: 5}}]}}</li>
 *   <li>{@code !($a > 1)} → {@code {_not: {a: {_gt: 1}}}}</li>
 * </ul>
 * <p>
 * See RDR-028 Phase 2 for design rationale.
 */
public final class ExprToGraphQL {

    private ExprToGraphQL() {}

    /**
     * Returns true if the expression can be fully translated to a
     * GraphQL filter argument. Rejects ScalarCall, AggregateCall,
     * and arithmetic BinaryOps (ADD/SUB/MUL/DIV).
     */
    public static boolean isTranslatable(Expr expr) {
        return switch (expr) {
            case Expr.Literal l -> true;
            case Expr.FieldRef f -> true;
            case Expr.BinaryOp(var op, var l, var r) -> switch (op) {
                case EQ, NEQ, LT, GT, LTE, GTE, AND, OR ->
                    isTranslatable(l) && isTranslatable(r);
                case ADD, SUB, MUL, DIV -> false;
            };
            case Expr.UnaryOp u when u.op() == Expr.UnaryOp.Op.NOT -> isTranslatable(u.operand());
            case Expr.UnaryOp u -> false;  // NEG is arithmetic — not translatable
            case Expr.ScalarCall s -> false;
            case Expr.AggregateCall a -> false;
        };
    }

    /**
     * Translate an Expr into a GraphQL ObjectValue for a "where" / "filter"
     * argument. Only call this after {@link #isTranslatable(Expr)} returns true.
     *
     * @param expr the expression to translate
     * @return an {@link ObjectValue} suitable as a GraphQL argument value
     * @throws IllegalArgumentException if the expression is not translatable
     */
    public static Value<?> translate(Expr expr) {
        return switch (expr) {
            case Expr.BinaryOp(var op, var l, var r) -> switch (op) {
                // Comparison: FieldRef op Literal (or either order)
                case EQ, NEQ, LT, GT, LTE, GTE -> translateComparison(op, l, r);
                // AND: merge object fields from both sides
                case AND -> mergeAnd(translate(l), translate(r));
                // OR: wrap in _or array
                case OR -> ObjectValue.newObjectValue()
                    .objectField(new ObjectField("_or",
                        ArrayValue.newArrayValue()
                            .value(translate(l))
                            .value(translate(r))
                            .build()))
                    .build();
                case ADD, SUB, MUL, DIV ->
                    throw new IllegalArgumentException("Arithmetic not translatable: " + op);
            };
            case Expr.UnaryOp u when u.op() == Expr.UnaryOp.Op.NOT ->
                ObjectValue.newObjectValue()
                    .objectField(new ObjectField("_not", translate(u.operand())))
                    .build();
            case Expr.UnaryOp u ->
                throw new IllegalArgumentException("NEG not translatable");
            case Expr.Literal l ->
                throw new IllegalArgumentException(
                    "Bare Literal cannot be used as a filter ObjectValue");
            case Expr.FieldRef f ->
                throw new IllegalArgumentException(
                    "Bare FieldRef cannot be used as a filter ObjectValue");
            case Expr.ScalarCall s ->
                throw new IllegalArgumentException("ScalarCall not translatable");
            case Expr.AggregateCall a ->
                throw new IllegalArgumentException("AggregateCall not translatable");
        };
    }

    // -----------------------------------------------------------------------

    /**
     * Translate a comparison BinaryOp into {@code {fieldName: {_op: value}}}.
     * Handles both {@code FieldRef op Literal} and {@code Literal op FieldRef}
     * (reversed comparisons flip the operator).
     */
    @SuppressWarnings("unchecked")
    private static ObjectValue translateComparison(
            Expr.BinaryOp.Op op, Expr left, Expr right) {

        String fieldName;
        Value<?> gqlValue;
        Expr.BinaryOp.Op effectiveOp = op;

        if (left instanceof Expr.FieldRef fl && right instanceof Expr.Literal rl) {
            fieldName = fieldName(fl);
            gqlValue  = literalValue(rl);
        } else if (left instanceof Expr.Literal ll && right instanceof Expr.FieldRef fr) {
            // Swap sides and flip operator direction
            fieldName    = fieldName(fr);
            gqlValue     = literalValue(ll);
            effectiveOp  = flip(op);
        } else if (left instanceof Expr.FieldRef fl && right instanceof Expr.FieldRef fr) {
            // Field-to-field comparison — use field name as string value placeholder
            fieldName = fieldName(fl);
            gqlValue  = StringValue.of("$" + String.join(".", fr.path()));
        } else {
            // Nested structure — translate each side independently and merge
            Value<?> lv = translate(left);
            Value<?> rv = translate(right);
            return mergeAnd(lv, rv);
        }

        String opKey = opKey(effectiveOp);
        return ObjectValue.newObjectValue()
            .objectField(new ObjectField(fieldName,
                ObjectValue.newObjectValue()
                    .objectField(new ObjectField(opKey, gqlValue))
                    .build()))
            .build();
    }

    /**
     * Merge two Values that are expected to be ObjectValues by combining
     * their fields. Falls back to a synthetic AND structure if either side
     * is not an ObjectValue.
     */
    private static ObjectValue mergeAnd(Value<?> left, Value<?> right) {
        if (left instanceof ObjectValue lv && right instanceof ObjectValue rv) {
            List<ObjectField> fields = new ArrayList<>(lv.getObjectFields());
            fields.addAll(rv.getObjectFields());
            return ObjectValue.newObjectValue().objectFields(fields).build();
        }
        // Fallback — wrap in _and (rare, for non-standard translations)
        return ObjectValue.newObjectValue()
            .objectField(new ObjectField("_and",
                ArrayValue.newArrayValue()
                    .value(left)
                    .value(right)
                    .build()))
            .build();
    }

    private static String fieldName(Expr.FieldRef ref) {
        // Use the leaf segment — last path segment for nested fields
        List<String> path = ref.path();
        return path.getLast();
    }

    @SuppressWarnings("unchecked")
    private static Value<?> literalValue(Expr.Literal literal) {
        Object v = literal.value();
        if (v == null) {
            return NullValue.of();
        }
        if (v instanceof Boolean b) {
            return BooleanValue.of(b);
        }
        if (v instanceof Double d) {
            // Represent whole numbers as IntValue for cleaner output
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < Long.MAX_VALUE) {
                return new IntValue(BigInteger.valueOf(d.longValue()));
            }
            return new FloatValue(BigDecimal.valueOf(d));
        }
        if (v instanceof String s) {
            return StringValue.of(s);
        }
        return StringValue.of(v.toString());
    }

    private static String opKey(Expr.BinaryOp.Op op) {
        return switch (op) {
            case EQ  -> "_eq";
            case NEQ -> "_neq";
            case LT  -> "_lt";
            case GT  -> "_gt";
            case LTE -> "_lte";
            case GTE -> "_gte";
            default  -> throw new IllegalArgumentException("Not a comparison op: " + op);
        };
    }

    /** Flip operator direction for reversed comparisons (Literal op FieldRef). */
    private static Expr.BinaryOp.Op flip(Expr.BinaryOp.Op op) {
        return switch (op) {
            case LT  -> Expr.BinaryOp.Op.GT;
            case GT  -> Expr.BinaryOp.Op.LT;
            case LTE -> Expr.BinaryOp.Op.GTE;
            case GTE -> Expr.BinaryOp.Op.LTE;
            default  -> op; // EQ, NEQ are symmetric
        };
    }
}
