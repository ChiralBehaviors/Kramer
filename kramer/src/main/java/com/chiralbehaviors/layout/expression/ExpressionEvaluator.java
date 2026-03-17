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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.chiralbehaviors.layout.expression.Expr.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Tree-walking evaluator over the sealed {@link Expr} hierarchy. Uses Java 25
 * pattern matching switch for dispatch.
 * <p>
 * Per-row evaluation for filter, formula, and sort expressions. Aggregate
 * evaluation for cross-row reduction. Includes AST caching and cycle detection
 * for formula dependency graphs.
 *
 * @author hhildebrand
 */
public final class ExpressionEvaluator {

    private static final int MAX_CACHE_SIZE = 256;

    @SuppressWarnings("serial")
    private final Map<String, Expr> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Expr> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };
    private long lastVersion = -1;

    /**
     * Parse and cache an expression string. Returns cached AST on subsequent
     * calls with the same string.
     */
    public Expr compile(String expression) throws ParseException {
        var cached = cache.get(expression);
        if (cached != null) {
            return cached;
        }
        var ast = Parser.parse(expression);
        cache.put(expression, ast);
        return ast;
    }

    /** Clear all cached ASTs. Call when stylesheet version changes. */
    public void invalidateCache() {
        cache.clear();
    }

    /**
     * Sync with stylesheet version. Clears cache if version has changed.
     */
    public void syncVersion(long version) {
        if (version != lastVersion) {
            cache.clear();
            lastVersion = version;
        }
    }

    /**
     * Evaluate an expression against a single row.
     *
     * @return Double, String, Boolean, or null
     * @throws IllegalStateException if an AggregateCall is encountered in row
     *                               context
     */
    public Object evaluate(Expr expr, JsonNode row) {
        return switch (expr) {
            case Literal(var v) -> v;
            case FieldRef(var path) -> resolveField(row, path);
            case BinaryOp(var op, var l, var r) -> evalBinary(op, l, r, row);
            case UnaryOp(var op, var operand) -> evalUnary(op, operand, row);
            case ScalarCall(var name, var args) -> evalScalar(name, args, row);
            case AggregateCall(_, _) -> throw new IllegalStateException(
                "Aggregate call in per-row evaluation context");
        };
    }

    /**
     * Evaluate an aggregate expression over a set of rows.
     *
     * @return the aggregated value (Double or null)
     */
    public Object evaluateAggregate(AggregateCall call, List<JsonNode> rows) {
        if ("count".equals(call.fn())) {
            return (double) rows.size();
        }

        // Map the argument expression over all rows, collect non-null numeric values
        var values = new ArrayList<Double>();
        for (var row : rows) {
            var val = call.arg() != null ? evaluate(call.arg(), row) : null;
            if (val != null) {
                var num = toNumber(val);
                if (num != null) {
                    values.add(num);
                }
            }
        }

        if (values.isEmpty()) {
            return null;
        }

        return switch (call.fn()) {
            case "sum" -> values.stream().mapToDouble(Double::doubleValue).sum();
            case "avg" -> values.stream().mapToDouble(Double::doubleValue).average().getAsDouble();
            case "min" -> values.stream().mapToDouble(Double::doubleValue).min().getAsDouble();
            case "max" -> values.stream().mapToDouble(Double::doubleValue).max().getAsDouble();
            default -> null;
        };
    }

    // --- Binary operations ---

    private Object evalBinary(BinaryOp.Op op, Expr left, Expr right, JsonNode row) {
        // Short-circuit for && and ||
        if (op == BinaryOp.Op.AND) {
            var lv = evaluate(left, row);
            if (!toBool(lv)) return false;
            return toBool(evaluate(right, row));
        }
        if (op == BinaryOp.Op.OR) {
            var lv = evaluate(left, row);
            if (toBool(lv)) return true;
            return toBool(evaluate(right, row));
        }

        var lv = evaluate(left, row);
        var rv = evaluate(right, row);

        return switch (op) {
            case ADD -> evalAdd(lv, rv);
            case SUB -> evalArith(lv, rv, (a, b) -> a - b);
            case MUL -> evalArith(lv, rv, (a, b) -> a * b);
            case DIV -> evalDiv(lv, rv);
            case EQ -> evalEq(lv, rv);
            case NEQ -> !Boolean.TRUE.equals(evalEq(lv, rv));
            case LT -> evalOrdering(lv, rv, c -> c < 0);
            case GT -> evalOrdering(lv, rv, c -> c > 0);
            case LTE -> evalOrdering(lv, rv, c -> c <= 0);
            case GTE -> evalOrdering(lv, rv, c -> c >= 0);
            case AND, OR -> throw new AssertionError("unreachable");
        };
    }

    private Object evalAdd(Object lv, Object rv) {
        if (lv == null || rv == null) return null;
        // String + String → concat
        if (lv instanceof String ls && rv instanceof String rs) {
            return ls + rs;
        }
        // Try numeric
        var ln = toNumber(lv);
        var rn = toNumber(rv);
        if (ln != null && rn != null) {
            return ln + rn;
        }
        // String + non-string → string concat if one side is string
        if (lv instanceof String || rv instanceof String) {
            return String.valueOf(lv) + String.valueOf(rv);
        }
        return null;
    }

    private Object evalDiv(Object lv, Object rv) {
        if (lv == null || rv == null) return null;
        var ln = toNumber(lv);
        var rn = toNumber(rv);
        if (ln == null || rn == null) return null;
        if (rn == 0.0) return null; // division by zero → null
        return ln / rn;
    }

    private Object evalArith(Object lv, Object rv, ArithOp arith) {
        if (lv == null || rv == null) return null;
        var ln = toNumber(lv);
        var rn = toNumber(rv);
        if (ln == null || rn == null) return null;
        return arith.apply(ln, rn);
    }

    @FunctionalInterface
    private interface ArithOp {
        double apply(double a, double b);
    }

    private Object evalEq(Object lv, Object rv) {
        if (lv == null && rv == null) return true;
        if (lv == null || rv == null) return false;
        if (lv instanceof Double ld && rv instanceof Double rd) {
            return ld.equals(rd);
        }
        if (lv instanceof String && rv instanceof String) {
            return lv.equals(rv);
        }
        if (lv instanceof Boolean && rv instanceof Boolean) {
            return lv.equals(rv);
        }
        // Mixed: try numeric coercion
        var ln = toNumber(lv);
        var rn = toNumber(rv);
        if (ln != null && rn != null) {
            return ln.equals(rn);
        }
        return lv.toString().equals(rv.toString());
    }

    private Object evalOrdering(Object lv, Object rv, java.util.function.IntPredicate test) {
        if (lv == null || rv == null) return false;
        if (lv instanceof Double ld && rv instanceof Double rd) {
            return test.test(Double.compare(ld, rd));
        }
        if (lv instanceof String ls && rv instanceof String rs) {
            return test.test(ls.compareTo(rs));
        }
        // Mixed: try numeric
        var ln = toNumber(lv);
        var rn = toNumber(rv);
        if (ln != null && rn != null) {
            return test.test(Double.compare(ln, rn));
        }
        // Fall back to string comparison
        return test.test(lv.toString().compareTo(rv.toString()));
    }

    // --- Unary operations ---

    private Object evalUnary(UnaryOp.Op op, Expr operand, JsonNode row) {
        var val = evaluate(operand, row);
        return switch (op) {
            case NEG -> {
                if (val == null) yield null;
                var n = toNumber(val);
                yield (n != null) ? -n : null;
            }
            case NOT -> !toBool(val);
        };
    }

    // --- Scalar functions ---

    private Object evalScalar(String name, List<Expr> args, JsonNode row) {
        return switch (name) {
            case "if" -> {
                var cond = evaluate(args.get(0), row);
                yield toBool(cond)
                    ? evaluate(args.get(1), row)
                    : evaluate(args.get(2), row);
            }
            case "len" -> {
                var val = evaluate(args.getFirst(), row);
                if (val == null) yield null;
                yield (double) val.toString().length();
            }
            case "upper" -> {
                var val = evaluate(args.getFirst(), row);
                if (val == null) yield null;
                yield val.toString().toUpperCase();
            }
            case "lower" -> {
                var val = evaluate(args.getFirst(), row);
                if (val == null) yield null;
                yield val.toString().toLowerCase();
            }
            case "abs" -> {
                var val = evaluate(args.getFirst(), row);
                if (val == null) yield null;
                var n = toNumber(val);
                yield (n != null) ? Math.abs(n) : null;
            }
            case "round" -> {
                var val = evaluate(args.getFirst(), row);
                if (val == null) yield null;
                var n = toNumber(val);
                yield (n != null) ? (double) Math.round(n) : null;
            }
            default -> null;
        };
    }

    // --- Field resolution ---

    private Object resolveField(JsonNode row, List<String> path) {
        JsonNode current = row;
        for (var segment : path) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(segment);
        }
        return materialize(current);
    }

    /** Materialize a JsonNode to an expression value per the spec type table. */
    private Object materialize(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        // ArrayNode, ObjectNode → null
        return null;
    }

    // --- Type coercion helpers ---

    /** Coerce to boolean per spec: null→false, 0→false, ""→false. */
    private boolean toBool(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        if (val instanceof Double d) return d != 0.0;
        if (val instanceof String s) return !s.isEmpty();
        return false;
    }

    /** Try to coerce to Double. Returns null if not possible. */
    private Double toNumber(Object val) {
        if (val instanceof Double d) return d;
        if (val instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // --- Cycle detection (Kahn's algorithm) ---

    /** Internal graph representation for Kahn's algorithm. */
    private record DepGraph(
        LinkedHashMap<String, Integer> inDegree,
        LinkedHashMap<String, Set<String>> dependents
    ) {}

    private static DepGraph buildDepGraph(Map<String, Set<String>> deps) {
        var inDegree = new LinkedHashMap<String, Integer>();
        var dependents = new LinkedHashMap<String, Set<String>>();
        for (var entry : deps.entrySet()) {
            inDegree.putIfAbsent(entry.getKey(), 0);
            dependents.putIfAbsent(entry.getKey(), new HashSet<>());
            for (var dep : entry.getValue()) {
                inDegree.putIfAbsent(dep, 0);
                dependents.putIfAbsent(dep, new HashSet<>());
                dependents.get(dep).add(entry.getKey());
                inDegree.merge(entry.getKey(), 1, Integer::sum);
            }
        }
        return new DepGraph(inDegree, dependents);
    }

    private static List<String> kahnTraverse(DepGraph g) {
        var queue = new ArrayList<String>();
        for (var entry : g.inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }
        int idx = 0;
        while (idx < queue.size()) {
            var node = queue.get(idx++);
            for (var dependent : g.dependents.getOrDefault(node, Set.of())) {
                if (g.inDegree.merge(dependent, -1, Integer::sum) == 0) {
                    queue.add(dependent);
                }
            }
        }
        return queue;
    }

    /**
     * Detect fields participating in dependency cycles.
     *
     * @param deps map of field → set of fields it depends on
     * @return set of fields in cycles (empty if no cycles)
     */
    public static Set<String> detectCycles(Map<String, Set<String>> deps) {
        var g = buildDepGraph(deps);
        var sorted = new HashSet<>(kahnTraverse(g));
        var allNodes = new HashSet<>(g.inDegree.keySet());
        allNodes.removeAll(sorted);
        return allNodes;
    }

    /**
     * Topological sort of dependency graph. Fields in cycles are excluded.
     *
     * @param deps map of field → set of fields it depends on
     * @return ordered list of fields (dependencies first)
     */
    public static List<String> topologicalSort(Map<String, Set<String>> deps) {
        var traversal = kahnTraverse(buildDepGraph(deps));
        return traversal.stream()
            .filter(deps::containsKey)
            .toList();
    }

    /**
     * Check whether an expression AST contains any AggregateCall node.
     */
    public static boolean containsAggregate(Expr expr) {
        return switch (expr) {
            case AggregateCall(_, _) -> true;
            case BinaryOp(_, var l, var r) -> containsAggregate(l) || containsAggregate(r);
            case UnaryOp(_, var operand) -> containsAggregate(operand);
            case ScalarCall(_, var args) -> args.stream().anyMatch(ExpressionEvaluator::containsAggregate);
            case Literal(_), FieldRef(_) -> false;
        };
    }

    /**
     * Extract the set of top-level field names referenced in an expression.
     * Only the first segment of each FieldRef path is collected (sibling refs).
     */
    public static Set<String> extractFieldRefs(Expr expr) {
        var refs = new HashSet<String>();
        collectFieldRefs(expr, refs);
        return refs;
    }

    private static void collectFieldRefs(Expr expr, Set<String> refs) {
        switch (expr) {
            case FieldRef(var path) -> refs.add(path.getFirst());
            case BinaryOp(_, var l, var r) -> { collectFieldRefs(l, refs); collectFieldRefs(r, refs); }
            case UnaryOp(_, var operand) -> collectFieldRefs(operand, refs);
            case ScalarCall(_, var args) -> args.forEach(a -> collectFieldRefs(a, refs));
            case AggregateCall(_, var arg) -> { if (arg != null) collectFieldRefs(arg, refs); }
            case Literal(_) -> {}
        }
    }

    /**
     * Convert an evaluation result (Double, String, Boolean, null) to a JsonNode.
     */
    public static JsonNode toJsonNode(Object value) {
        if (value == null) return JsonNodeFactory.instance.nullNode();
        if (value instanceof Double d) return JsonNodeFactory.instance.numberNode(d);
        if (value instanceof String s) return JsonNodeFactory.instance.textNode(s);
        if (value instanceof Boolean b) return JsonNodeFactory.instance.booleanNode(b);
        return JsonNodeFactory.instance.nullNode();
    }

    /**
     * Coerce a value to boolean (public access for pipeline wiring).
     * null→false, 0→false, ""→false.
     */
    public boolean toBoolean(Object val) {
        return toBool(val);
    }
}
