package zw.gov.mohcc.impilo.rules.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A safe, sandboxed expression evaluator using recursive descent parsing.
 *
 * <p>Supports expressions like:
 * <pre>
 *   facts.age >= 18 AND facts.country == 'ZW'
 *   facts.status == 'ACTIVE' OR (facts.role == 'ADMIN' AND facts.level > 3)
 * </pre>
 *
 * <h3>Grammar:</h3>
 * <pre>
 * expression  -> orExpr
 * orExpr      -> andExpr ('OR' andExpr)*
 * andExpr     -> comparison ('AND' comparison)*
 * comparison  -> atom (compOp atom)?
 * compOp      -> '==' | '!=' | '>=' | '>' | '<=' | '<'
 * atom        -> '(' expression ')' | factRef | literal
 * factRef     -> 'facts.' IDENTIFIER
 * literal     -> NUMBER | STRING | BOOLEAN
 * </pre>
 */
@Component
public class SimpleRuleEvaluator {

    /**
     * Evaluate a rule expression against the given facts.
     *
     * @param expression the rule expression to evaluate
     * @param facts      the fact map (keys accessed via facts.&lt;key&gt;)
     * @return true if the expression evaluates to true, false otherwise
     * @throws RuleExpressionInvalidException if the expression is invalid
     */
    public boolean evaluate(String expression, Map<String, Object> facts) {
        if (expression == null || expression.isBlank()) {
            throw new RuleExpressionInvalidException("Expression must not be null or blank");
        }
        List<Token> tokens = tokenize(expression);
        Parser parser = new Parser(tokens, facts);
        boolean result = parser.parseExpression();
        if (!parser.isAtEnd()) {
            throw new RuleExpressionInvalidException(
                    "Unexpected token after end of expression: " + parser.current());
        }
        return result;
    }

    // =========================================================================
    // Tokenizer
    // =========================================================================

    enum TokenType {
        IDENTIFIER,       // facts.xxx
        STRING_LITERAL,   // 'some string'
        NUMBER_LITERAL,   // 123, 45.67
        BOOLEAN_LITERAL,  // true, false
        OPERATOR,         // ==, !=, >=, >, <=, <
        LPAREN,           // (
        RPAREN,           // )
        AND,              // AND
        OR                // OR
    }

    record Token(TokenType type, String value) {
        @Override
        public String toString() {
            return type + "(" + value + ")";
        }
    }

    List<Token> tokenize(String expression) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int len = expression.length();

        while (i < len) {
            char c = expression.charAt(i);

            // Skip whitespace
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // Parentheses
            if (c == '(') {
                tokens.add(new Token(TokenType.LPAREN, "("));
                i++;
                continue;
            }
            if (c == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")"));
                i++;
                continue;
            }

            // Two-character operators: ==, !=, >=, <=
            if (i + 1 < len) {
                String twoChar = expression.substring(i, i + 2);
                if (twoChar.equals("==") || twoChar.equals("!=") ||
                        twoChar.equals(">=") || twoChar.equals("<=")) {
                    tokens.add(new Token(TokenType.OPERATOR, twoChar));
                    i += 2;
                    continue;
                }
            }

            // Single-character operators: >, <
            if (c == '>' || c == '<') {
                tokens.add(new Token(TokenType.OPERATOR, String.valueOf(c)));
                i++;
                continue;
            }

            // String literal (single-quoted)
            if (c == '\'') {
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < len && expression.charAt(i) != '\'') {
                    sb.append(expression.charAt(i));
                    i++;
                }
                if (i >= len) {
                    throw new RuleExpressionInvalidException("Unterminated string literal");
                }
                i++; // skip closing quote
                tokens.add(new Token(TokenType.STRING_LITERAL, sb.toString()));
                continue;
            }

            // Number literal
            if (Character.isDigit(c) || (c == '-' && i + 1 < len && Character.isDigit(expression.charAt(i + 1)))) {
                StringBuilder sb = new StringBuilder();
                if (c == '-') {
                    sb.append('-');
                    i++;
                }
                boolean hasDot = false;
                while (i < len && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
                    if (expression.charAt(i) == '.') {
                        if (hasDot) break;
                        hasDot = true;
                    }
                    sb.append(expression.charAt(i));
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER_LITERAL, sb.toString()));
                continue;
            }

            // Identifiers, keywords (AND, OR, true, false, facts.xxx)
            if (Character.isLetter(c) || c == '_') {
                StringBuilder sb = new StringBuilder();
                while (i < len && (Character.isLetterOrDigit(expression.charAt(i)) ||
                        expression.charAt(i) == '_' || expression.charAt(i) == '.')) {
                    sb.append(expression.charAt(i));
                    i++;
                }
                String word = sb.toString();
                switch (word) {
                    case "AND" -> tokens.add(new Token(TokenType.AND, "AND"));
                    case "OR" -> tokens.add(new Token(TokenType.OR, "OR"));
                    case "true", "false" -> tokens.add(new Token(TokenType.BOOLEAN_LITERAL, word));
                    default -> tokens.add(new Token(TokenType.IDENTIFIER, word));
                }
                continue;
            }

            throw new RuleExpressionInvalidException("Unexpected character: '" + c + "' at position " + i);
        }

        return tokens;
    }

    // =========================================================================
    // Parser + Evaluator (recursive descent)
    // =========================================================================

    static class Parser {
        private final List<Token> tokens;
        private final Map<String, Object> facts;
        private int pos = 0;

        Parser(List<Token> tokens, Map<String, Object> facts) {
            this.tokens = tokens;
            this.facts = facts;
        }

        boolean isAtEnd() {
            return pos >= tokens.size();
        }

        Token current() {
            if (isAtEnd()) {
                throw new RuleExpressionInvalidException("Unexpected end of expression");
            }
            return tokens.get(pos);
        }

        Token advance() {
            Token t = current();
            pos++;
            return t;
        }

        boolean check(TokenType type) {
            return !isAtEnd() && tokens.get(pos).type() == type;
        }

        boolean match(TokenType type) {
            if (check(type)) {
                pos++;
                return true;
            }
            return false;
        }

        // expression -> orExpr
        boolean parseExpression() {
            return parseOr();
        }

        // orExpr -> andExpr ('OR' andExpr)*
        boolean parseOr() {
            boolean result = parseAnd();
            while (check(TokenType.OR)) {
                advance();
                boolean right = parseAnd();
                result = result || right;
            }
            return result;
        }

        // andExpr -> comparison ('AND' comparison)*
        boolean parseAnd() {
            boolean result = parseComparison();
            while (check(TokenType.AND)) {
                advance();
                boolean right = parseComparison();
                result = result && right;
            }
            return result;
        }

        // comparison -> atom (compOp atom)?
        boolean parseComparison() {
            Object left = parseAtom();

            if (check(TokenType.OPERATOR)) {
                String op = advance().value();
                Object right = parseAtom();
                return compare(left, op, right);
            }

            // Standalone atom: must be a boolean
            if (left instanceof Boolean b) {
                return b;
            }
            throw new RuleExpressionInvalidException(
                    "Expected a comparison operator or boolean value, got: " + left);
        }

        // atom -> '(' expression ')' | factRef | literal
        Object parseAtom() {
            if (match(TokenType.LPAREN)) {
                boolean result = parseExpression();
                if (!match(TokenType.RPAREN)) {
                    throw new RuleExpressionInvalidException("Expected closing parenthesis ')'");
                }
                return result;
            }

            if (check(TokenType.IDENTIFIER)) {
                Token t = advance();
                String name = t.value();
                if (name.startsWith("facts.")) {
                    String key = name.substring("facts.".length());
                    return facts.get(key); // may be null
                }
                throw new RuleExpressionInvalidException(
                        "Unknown identifier: '" + name + "'. Fact references must start with 'facts.'");
            }

            if (check(TokenType.STRING_LITERAL)) {
                return advance().value();
            }

            if (check(TokenType.NUMBER_LITERAL)) {
                String numStr = advance().value();
                if (numStr.contains(".")) {
                    return Double.parseDouble(numStr);
                }
                // Try long for large integers, fall back to integer
                try {
                    long val = Long.parseLong(numStr);
                    if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                        return (int) val;
                    }
                    return val;
                } catch (NumberFormatException e) {
                    return Double.parseDouble(numStr);
                }
            }

            if (check(TokenType.BOOLEAN_LITERAL)) {
                return Boolean.parseBoolean(advance().value());
            }

            throw new RuleExpressionInvalidException(
                    "Unexpected token: " + (isAtEnd() ? "end of expression" : current()));
        }

        // =====================================================================
        // Comparison logic
        // =====================================================================

        private boolean compare(Object left, String op, Object right) {
            // Null fact values -> no match for all comparisons
            if (left == null || right == null) {
                return false;
            }

            return switch (op) {
                case "==" -> equalityCompare(left, right);
                case "!=" -> !equalityCompare(left, right);
                case ">", ">=", "<", "<=" -> numericCompare(left, op, right);
                default -> throw new RuleExpressionInvalidException("Unknown operator: " + op);
            };
        }

        private boolean equalityCompare(Object left, Object right) {
            // String == String
            if (left instanceof String l && right instanceof String r) {
                return l.equals(r);
            }
            // Boolean == Boolean
            if (left instanceof Boolean l && right instanceof Boolean r) {
                return l.equals(r);
            }
            // Number == Number
            if (isNumber(left) && isNumber(right)) {
                return toDouble(left) == toDouble(right);
            }
            // Type mismatch -> no match
            return false;
        }

        private boolean numericCompare(Object left, String op, Object right) {
            // Numeric comparisons only for numbers
            if (!isNumber(left) || !isNumber(right)) {
                return false;
            }
            double l = toDouble(left);
            double r = toDouble(right);
            return switch (op) {
                case ">" -> l > r;
                case ">=" -> l >= r;
                case "<" -> l < r;
                case "<=" -> l <= r;
                default -> false;
            };
        }

        private boolean isNumber(Object obj) {
            return obj instanceof Number;
        }

        private double toDouble(Object obj) {
            return ((Number) obj).doubleValue();
        }
    }
}
