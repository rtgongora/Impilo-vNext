package zw.gov.mohcc.impilo.rules.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleRuleEvaluatorTest {

    private SimpleRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SimpleRuleEvaluator();
    }

    @Test
    @DisplayName("String equality comparison works")
    void simpleEquality() {
        Map<String, Object> facts = Map.of("country", "ZW");
        assertThat(evaluator.evaluate("facts.country == 'ZW'", facts)).isTrue();
        assertThat(evaluator.evaluate("facts.country == 'US'", facts)).isFalse();
    }

    @Test
    @DisplayName("Numeric comparisons work (>=, <, ==)")
    void numericComparison() {
        Map<String, Object> facts = Map.of("age", 25);
        assertThat(evaluator.evaluate("facts.age >= 18", facts)).isTrue();
        assertThat(evaluator.evaluate("facts.age < 18", facts)).isFalse();
        assertThat(evaluator.evaluate("facts.age == 25", facts)).isTrue();
    }

    @Test
    @DisplayName("AND operator evaluates correctly")
    void andOperator() {
        Map<String, Object> facts = Map.of("age", 25, "country", "ZW");
        assertThat(evaluator.evaluate("facts.age >= 18 AND facts.country == 'ZW'", facts)).isTrue();
        assertThat(evaluator.evaluate("facts.age >= 30 AND facts.country == 'ZW'", facts)).isFalse();
    }

    @Test
    @DisplayName("OR operator evaluates correctly")
    void orOperator() {
        Map<String, Object> facts = Map.of("age", 15, "country", "ZW");
        assertThat(evaluator.evaluate("facts.age >= 18 OR facts.country == 'ZW'", facts)).isTrue();
        assertThat(evaluator.evaluate("facts.age >= 18 OR facts.country == 'US'", facts)).isFalse();
    }

    @Test
    @DisplayName("Parentheses group expressions correctly")
    void parentheses() {
        Map<String, Object> facts = Map.of("age", 15, "country", "ZW", "status", "ACTIVE");
        assertThat(evaluator.evaluate(
                "(facts.age >= 18 OR facts.country == 'ZW') AND facts.status == 'ACTIVE'", facts)).isTrue();
        assertThat(evaluator.evaluate(
                "facts.age >= 18 OR (facts.country == 'ZW' AND facts.status == 'ACTIVE')", facts)).isTrue();
    }

    @Test
    @DisplayName("Null fact values return no match")
    void nullFactReturnsNoMatch() {
        Map<String, Object> facts = Map.of("age", 25);
        assertThat(evaluator.evaluate("facts.missing == 'value'", facts)).isFalse();
    }

    @Test
    @DisplayName("Boolean literal comparison works")
    void booleanLiteral() {
        Map<String, Object> facts = Map.of("active", true);
        assertThat(evaluator.evaluate("facts.active == true", facts)).isTrue();
        assertThat(evaluator.evaluate("facts.active == false", facts)).isFalse();
    }

    @Test
    @DisplayName("Invalid expression throws RuleExpressionInvalidException")
    void invalidExpressionThrows() {
        Map<String, Object> facts = Map.of("age", 25);
        assertThatThrownBy(() -> evaluator.evaluate("", facts))
                .isInstanceOf(RuleExpressionInvalidException.class);
        assertThatThrownBy(() -> evaluator.evaluate("facts.age >=", facts))
                .isInstanceOf(RuleExpressionInvalidException.class);
    }

    @Test
    @DisplayName("Not-equal operator works")
    void notEqualOperator() {
        Map<String, Object> facts = Map.of("country", "ZW");
        assertThat(evaluator.evaluate("facts.country != 'US'", facts)).isTrue();
        assertThat(evaluator.evaluate("facts.country != 'ZW'", facts)).isFalse();
    }

    @Test
    @DisplayName("Decimal number comparison works")
    void decimalComparison() {
        Map<String, Object> facts = Map.of("score", 85.5);
        assertThat(evaluator.evaluate("facts.score > 80.0", facts)).isTrue();
        assertThat(evaluator.evaluate("facts.score <= 85.5", facts)).isTrue();
    }
}
