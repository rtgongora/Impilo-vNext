package zw.gov.mohcc.impilo.tshepo.authz.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.ActorType;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.PurposeOfUse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Guard: every {@code policy_rule} seed value must be inside the vocabulary the runtime can
 * actually produce and match.
 *
 * <p><b>Why this test exists.</b> {@code policy_rule.purpose}, {@code actor_type} and
 * {@code effect} are plain {@code VARCHAR} columns — Postgres accepts any string. But the
 * runtime only ever compares them against a closed vocabulary:
 *
 * <ul>
 *   <li>{@code purpose} — {@code PolicyEngine.parsePurpose} rejects anything outside
 *       {@link PurposeOfUse} with {@code INVALID_PURPOSE} at Step 2, long before rule matching.
 *       A rule seeded with a purpose outside the enum can therefore never match a live request:
 *       either no caller can legally send that purpose (so the rule is unreachable and the
 *       access it was written to grant silently does not exist), or a caller does send it and
 *       is denied outright.</li>
 *   <li>{@code actor_type} — matched against the request's {@code X-Actor-Type} by raw string
 *       equality. Nothing parses it, so nothing rejects an unknown value; the rule simply never
 *       matches. That makes enum membership the <em>wrong</em> test here — see below.</li>
 *   <li>{@code effect} — {@code PolicyEngine.evaluatePolicies} tests only
 *       {@code "DENY".equalsIgnoreCase(...)} and {@code "ALLOW".equalsIgnoreCase(...)}. Any
 *       other value makes the rule contribute neither an allow nor a deny: inert.</li>
 * </ul>
 *
 * <p>None of these fail loudly. They produce a rule that exists in the table, reads correctly
 * to a human, and never fires.
 *
 * <p><b>Enum membership is not the same question as reachability.</b> An earlier version of this
 * guard checked {@code actor_type} against {@link ActorType} and would have passed a seed reading
 * {@code actor_type = 'ADMIN'} — a value sitting in the enum that no client in the estate has ever
 * emitted, so the rule would be exactly as dead as the ones this guard was written to catch, one
 * level up. For {@link PurposeOfUse} the enum genuinely is the runtime vocabulary, because
 * {@code parsePurpose} enforces it. For {@link ActorType} the enum is only a declaration, so the
 * real test is whether anyone can send the value:
 *
 * <ul>
 *   <li>a value some client union declares (see {@link ContractMirrors}), or</li>
 *   <li>a service-side value that only Java callers emit ({@code SYSTEM}, {@code SERVICE}).</li>
 * </ul>
 *
 * <p>{@code ContractMirrorsTest} holds the other half of that contract — it keeps the mirrors and
 * the enum in agreement, so this reachability set cannot quietly drift out from under the seeds.
 *
 * <p><b>Effective state, not file text.</b> An applied migration can never be edited (Flyway
 * validates its checksum), so a bad seed is corrected by a later migration issuing an
 * {@code UPDATE}. This test therefore reads the migrations in order and checks the value each
 * rule <em>ends up with</em> — a violation in an early script is cleared by a later script that
 * sets a valid value for that rule by name. That keeps the guard honest without ever asking
 * anyone to rewrite history, and without an allowlist to rot.
 *
 * <p><b>Deliberately not guarded:</b> {@code role} and {@code resource_type} are open
 * vocabularies (free-form role codes and service-defined resource names) with no enum to
 * check against — a guard there would be guesswork. Note this means the guard cannot see a
 * rule made unreachable by an unmatchable {@code resource_type}; see V049's scope note.
 */
@DisplayName("policy_rule seeds stay inside the runtime's closed vocabularies")
class PolicyRuleSeedVocabularyTest {

    /**
     * The only two effects the policy engine acts on. Source of truth is
     * {@code PolicyEngine.evaluatePolicies} — there is no Effect enum to read.
     */
    private static final Set<String> EFFECTS = Set.of("ALLOW", "DENY");

    private static final Set<String> PURPOSES = Arrays.stream(PurposeOfUse.values())
            .map(Enum::name).collect(Collectors.toUnmodifiableSet());

    /** The columns this guard checks. Order is irrelevant; membership is what matters. */
    private static final List<String> GUARDED_COLUMNS = List.of("purpose", "actor_type", "effect");

    /**
     * Actor types someone can actually put on the wire: whatever a client union declares, plus the
     * service-side values only Java callers emit. Deliberately NOT {@code ActorType.values()} —
     * see the class javadoc.
     */
    private static Set<String> emittableActorTypes() {
        Set<String> reachable = new LinkedHashSet<>(ContractMirrors.declaredByAnyClient("ActorType"));
        reachable.addAll(ContractMirrors.SERVICE_SIDE_ACTOR_TYPES);
        return reachable;
    }

    private static Map<String, Set<String>> closedVocabularies() {
        return Map.of(
                "purpose", PURPOSES,
                "actor_type", emittableActorTypes(),
                "effect", EFFECTS);
    }

    private static final Pattern INSERT_HEAD = Pattern.compile(
            "INSERT\\s+INTO\\s+(?:tshepo_authz\\.)?policy_rule\\s*\\(([^)]*)\\)\\s*VALUES",
            Pattern.CASE_INSENSITIVE);

    /** Matches a single-quoted SQL literal at the start of a value, honouring '' escapes. */
    private static final Pattern LEADING_LITERAL = Pattern.compile("^'((?:[^']|'')*)'");

    private static final Pattern UPDATE_STATEMENT = Pattern.compile(
            "UPDATE\\s+(?:tshepo_authz\\.)?policy_rule\\s+SET\\s+(.*?)\\s+WHERE\\s+(.*?);",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** {@code name = 'x'} or {@code name IN ('x', 'y')} in an UPDATE's WHERE clause. */
    private static final Pattern NAME_PREDICATE = Pattern.compile(
            "\\bname\\s*(?:=\\s*'((?:[^']|'')*)'|IN\\s*\\(([^)]*)\\))",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern QUOTED = Pattern.compile("'((?:[^']|'')*)'");

    @Test
    void everySeededPurposeActorTypeAndEffectIsInVocabulary() {
        List<Path> migrations = locateMigrations();

        // A guard that silently stops finding anything is worse than no guard: pin the floor.
        assertThat(migrations)
                .as("migration scripts discovered under db/migration")
                .isNotEmpty();

        Map<String, Set<String>> vocabularies = closedVocabularies();
        assertThat(vocabularies.get("actor_type"))
                .as("actor types anyone can actually emit — if this collapses to the service-side"
                        + " values alone, the client mirrors stopped being readable and the"
                        + " actor_type check would pass everything")
                .hasSizeGreaterThan(ContractMirrors.SERVICE_SIDE_ACTOR_TYPES.size());

        // Pass 1: read every script once, in Flyway order.
        List<String> scripts = migrations.stream().map(m -> stripComments(read(m))).toList();

        // Pass 2: collect corrections — later UPDATEs that re-set a guarded column by rule name.
        Map<String, Correction> corrections = new LinkedHashMap<>();
        for (int i = 0; i < scripts.size(); i++) {
            for (Correction c : parseCorrections(scripts.get(i), i)) {
                // Later migrations win; equal indexes cannot occur (one script per index).
                corrections.merge(c.key(), c, (a, b) -> a.scriptIndex() >= b.scriptIndex() ? a : b);
            }
        }

        List<String> violations = new ArrayList<>();
        int rowsChecked = 0;

        for (int fileIndex = 0; fileIndex < migrations.size(); fileIndex++) {
            Path migration = migrations.get(fileIndex);
            String sql = scripts.get(fileIndex);
            int rowsInFile = 0;

            for (Statement statement : parseInserts(sql)) {
                for (Row row : statement.rows()) {
                    rowsInFile++;

                    if (row.values().size() != statement.columns().size()) {
                        violations.add(location(migration, sql, row.offset())
                                + " — seed row has " + row.values().size() + " values for "
                                + statement.columns().size() + " columns; the parser cannot tell "
                                + "which value belongs to which column, and neither can a reviewer");
                        continue;
                    }

                    Map<String, Value> byColumn = new LinkedHashMap<>();
                    for (int i = 0; i < statement.columns().size(); i++) {
                        byColumn.put(statement.columns().get(i), row.values().get(i));
                    }
                    String ruleName = literalOf(byColumn.get("name"));

                    int seededIn = fileIndex;
                    vocabularies.forEach((column, allowed) -> {
                        Value value = byColumn.get(column);
                        if (value == null || isSqlNull(value)) {
                            return; // column absent from this INSERT, or an intentional wildcard
                        }
                        String literal = literalOf(value);
                        if (literal == null) {
                            violations.add(location(migration, sql, value.offset())
                                    + " — " + column + " is not a plain string literal ("
                                    + value.text() + "); rule '" + ruleName + "'");
                            return;
                        }
                        if (allowed.contains(literal)) {
                            return;
                        }
                        // A later migration may already have corrected this row in place.
                        Correction fix = corrections.get(Correction.key(column, ruleName));
                        if (fix != null && fix.scriptIndex() > seededIn && allowed.contains(fix.value())) {
                            return;
                        }
                        violations.add(location(migration, sql, value.offset())
                                + " — " + column + "='" + literal + "' is outside the "
                                + "runtime vocabulary " + new LinkedHashSet<>(allowed)
                                + "; rule '" + ruleName + "' can never match a live request"
                                + (fix == null ? "" : " (later UPDATE sets '" + fix.value()
                                        + "', which is also outside the vocabulary)"));
                    });
                }
            }

            // Self-calibrating coverage check: if a file seeds policy_rule, we must have read rows
            // out of it. This fails if the SQL shape drifts away from what the parser understands,
            // rather than letting the guard quietly degrade to checking nothing.
            if (sql.toUpperCase().contains("INSERT INTO TSHEPO_AUTHZ.POLICY_RULE") && rowsInFile == 0) {
                violations.add(migration.getFileName()
                        + " — seeds policy_rule but no rows could be parsed; the guard is blind to "
                        + "this file. Update the parser in " + getClass().getSimpleName() + ".");
            }
            rowsChecked += rowsInFile;
        }

        assertThat(rowsChecked)
                .as("policy_rule seed rows parsed across %d migration scripts", migrations.size())
                .isPositive();

        if (!violations.isEmpty()) {
            fail("%d policy_rule seed value(s) fall outside the runtime's closed vocabularies. "
                    + "These rules are dead — they are in the table but can never fire:%n%n%s%n%n"
                    + "Fix by either correcting the seed to an existing code, or — if the concept "
                    + "is genuinely missing — adding it to the enum in libs/tshepo-contracts and "
                    + "defining its visibility envelope in VisibilityObligationComposer.purposeDefaults.",
                    violations.size(), String.join("\n", violations));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Corrections — later UPDATEs that re-set a guarded column by rule name
    // ════════════════════════════════════════════════════════════════════

    /** A later migration setting {@code column} to {@code value} for the rule named {@code rule}. */
    private record Correction(String column, String rule, String value, int scriptIndex) {
        static String key(String column, String rule) {
            return column + " " + rule;
        }

        String key() {
            return key(column, rule);
        }
    }

    /**
     * Extracts {@code UPDATE policy_rule SET <guarded column> = '<value>' WHERE ... name ...}.
     * An UPDATE whose WHERE clause does not identify rules by name cannot be attributed, so it is
     * ignored rather than assumed to fix anything — the conservative direction for a guard.
     */
    private static List<Correction> parseCorrections(String sql, int scriptIndex) {
        List<Correction> found = new ArrayList<>();
        Matcher stmt = UPDATE_STATEMENT.matcher(sql);
        while (stmt.find()) {
            String setClause = stmt.group(1);
            String whereClause = stmt.group(2);

            List<String> names = new ArrayList<>();
            Matcher predicate = NAME_PREDICATE.matcher(whereClause);
            while (predicate.find()) {
                if (predicate.group(1) != null) {
                    names.add(predicate.group(1).replace("''", "'"));
                } else {
                    Matcher each = QUOTED.matcher(predicate.group(2));
                    while (each.find()) {
                        names.add(each.group(1).replace("''", "'"));
                    }
                }
            }
            if (names.isEmpty()) {
                continue;
            }

            for (String column : GUARDED_COLUMNS) {
                Matcher assignment = Pattern
                        .compile("\\b" + column + "\\s*=\\s*'((?:[^']|'')*)'", Pattern.CASE_INSENSITIVE)
                        .matcher(setClause);
                if (assignment.find()) {
                    String value = assignment.group(1).replace("''", "'");
                    for (String name : names) {
                        found.add(new Correction(column, name, value, scriptIndex));
                    }
                }
            }
        }
        return found;
    }

    // ════════════════════════════════════════════════════════════════════
    // Migration discovery
    // ════════════════════════════════════════════════════════════════════

    /**
     * Resolves the migration directory off the test classpath (main resources are on it), so the
     * test does not depend on the working directory. Falls back to the module-relative source path
     * for IDE runs that do not copy resources.
     */
    private List<Path> locateMigrations() {
        Path dir = null;
        URL onClasspath = getClass().getClassLoader().getResource("db/migration");
        if (onClasspath != null && "file".equals(onClasspath.getProtocol())) {
            try {
                dir = Paths.get(onClasspath.toURI());
            } catch (Exception ignored) {
                // fall through to the source path
            }
        }
        if (dir == null || !Files.isDirectory(dir)) {
            dir = Paths.get("src/main/resources/db/migration");
        }
        if (!Files.isDirectory(dir)) {
            return fail("Cannot locate db/migration — the seed vocabulary guard would silently "
                    + "pass without checking anything. Looked on the classpath and at "
                    + Paths.get("src/main/resources/db/migration").toAbsolutePath());
        }
        try (var files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String location(Path migration, String sql, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < sql.length(); i++) {
            if (sql.charAt(i) == '\n') line++;
        }
        return migration.getFileName() + ":" + line;
    }

    // ════════════════════════════════════════════════════════════════════
    // Minimal SQL parsing — enough for the seed shape, quote-aware throughout
    // ════════════════════════════════════════════════════════════════════

    /** A value literal together with its absolute offset in the (comment-stripped) script. */
    private record Value(String text, int offset) {}

    private record Row(List<Value> values, int offset) {}

    private record Statement(List<String> columns, List<Row> rows) {}

    private static boolean isSqlNull(Value v) {
        return "NULL".equalsIgnoreCase(v.text().trim());
    }

    private static String literalOf(Value v) {
        if (v == null) return null;
        Matcher m = LEADING_LITERAL.matcher(v.text().trim());
        return m.find() ? m.group(1).replace("''", "'") : null;
    }

    /** Replaces {@code --} comments with nothing, preserving newlines so line numbers stay true. */
    private static String stripComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        boolean inString = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\'') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                        out.append('\'');
                        i++;
                    } else {
                        inString = false;
                    }
                }
                continue;
            }
            if (c == '\'') {
                inString = true;
                out.append(c);
                continue;
            }
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                if (i < sql.length()) out.append('\n');
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static List<Statement> parseInserts(String sql) {
        List<Statement> statements = new ArrayList<>();
        Matcher head = INSERT_HEAD.matcher(sql);
        while (head.find()) {
            List<String> columns = Arrays.stream(head.group(1).split(","))
                    .map(s -> s.trim().toLowerCase())
                    .filter(s -> !s.isEmpty())
                    .toList();
            int bodyStart = head.end();
            int bodyEnd = statementEnd(sql, bodyStart);
            statements.add(new Statement(columns, parseRows(sql, bodyStart, bodyEnd)));
        }
        return statements;
    }

    /** Offset of the {@code ;} terminating the statement that begins at {@code from}. */
    private static int statementEnd(String sql, int from) {
        int depth = 0;
        boolean inString = false;
        for (int i = from; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (inString) {
                if (c == '\'') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') i++;
                    else inString = false;
                }
                continue;
            }
            switch (c) {
                case '\'' -> inString = true;
                case '(' -> depth++;
                case ')' -> depth--;
                case ';' -> {
                    if (depth == 0) return i;
                }
                default -> { }
            }
        }
        return sql.length();
    }

    /** Each top-level {@code (...)} group between {@code from} and {@code to} is one seeded row. */
    private static List<Row> parseRows(String sql, int from, int to) {
        List<Row> rows = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        for (int i = from; i < to; i++) {
            char c = sql.charAt(i);
            if (inString) {
                if (c == '\'') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') i++;
                    else inString = false;
                }
                continue;
            }
            if (c == '\'') {
                inString = true;
            } else if (c == '(') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0 && start >= 0) {
                    rows.add(new Row(splitValues(sql, start + 1, i), start));
                    start = -1;
                }
            }
        }
        return rows;
    }

    /** Splits a row body on commas at paren-depth 0, keeping each value's absolute offset. */
    private static List<Value> splitValues(String sql, int from, int to) {
        List<Value> values = new ArrayList<>();
        int depth = 0;
        int start = from;
        boolean inString = false;
        for (int i = from; i < to; i++) {
            char c = sql.charAt(i);
            if (inString) {
                if (c == '\'') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') i++;
                    else inString = false;
                }
                continue;
            }
            if (c == '\'') {
                inString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                values.add(trimmedValue(sql, start, i));
                start = i + 1;
            }
        }
        values.add(trimmedValue(sql, start, to));
        return values;
    }

    /** Captures the value text with leading whitespace skipped, so the offset points at the token. */
    private static Value trimmedValue(String sql, int from, int to) {
        int s = from;
        while (s < to && Character.isWhitespace(sql.charAt(s))) s++;
        return new Value(sql.substring(s, to).trim(), s);
    }
}
