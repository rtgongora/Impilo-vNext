package zw.gov.mohcc.impilo.tshepo.authz.migration;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.fail;

/**
 * The {@code policy_rule} seeds as the database would end up holding them.
 *
 * <p>Every guard over the policy seeds needs the same two things and gets them wrong in the same
 * two ways if it improvises. It needs to read a row <em>by column</em> — a regex over a migration
 * file cannot tell {@code role} from the adjacent {@code actor_type}, which is how a previous
 * sweep reported "7 rows constrain actor_type='ADMIN'" about rows that said {@code role='ADMIN'}.
 * And it needs <em>effective state</em> rather than file text: an applied migration can never be
 * edited (Flyway validates its checksum), so a bad seed is corrected by a later migration issuing
 * an {@code UPDATE}, and a guard reading raw file text goes on reporting a defect that was fixed
 * three migrations ago — which means its baseline can never reach zero, and a baseline that can
 * never reach zero is one nobody believes.</p>
 *
 * <p>So this reads the scripts in Flyway order and answers what each rule <em>ends up</em> saying:
 * the columns from its {@code INSERT}, overlaid with any later {@code UPDATE ... SET col = '…'
 * WHERE name …}, and marked retired if a later script switched it off. Parsing is quote-aware
 * throughout ({@code ''} escapes inside descriptions are common) and deliberately narrow: it
 * understands the shape the seeds actually use and reports when it stops understanding one,
 * instead of degrading into a guard that reads nothing and passes.</p>
 */
final class SeededPolicyRules {

    private SeededPolicyRules() {}

    /**
     * One seeded rule in its effective state.
     *
     * @param name      {@code policy_rule.name} — the identity every later UPDATE keys on
     * @param columns   effective column values, SQL {@code NULL} represented as {@code null}
     * @param file      the migration that seeded it
     * @param line      1-based line of the row within that migration
     * @param seededIn  index of the seeding script in Flyway order
     * @param retiredIn index of the script that set {@code active = false}, or {@code -1}
     */
    record Rule(String name, Map<String, String> columns, String file, int line,
                int seededIn, int retiredIn) {

        String column(String name) {
            return columns.get(name);
        }

        String effect() {
            return columns.get("effect");
        }

        String conditions() {
            return columns.get("conditions");
        }

        /**
         * Whether a later migration switched this rule off. Note this is strictly "switched off
         * later": a row seeded {@code active=false} from birth is a SHADOW rule awaiting a flip and
         * is NOT retired — it stays under guard so it is not found to be broken on the day someone
         * turns it on, which is the whole point of guarding shadow rows.
         */
        boolean retired() {
            return retiredIn >= 0;
        }

        String location() {
            return file + ":" + line;
        }
    }

    private static final Pattern INSERT_HEAD = Pattern.compile(
            "INSERT\\s+INTO\\s+(?:tshepo_authz\\.)?policy_rule\\s*\\(([^)]*)\\)\\s*VALUES",
            Pattern.CASE_INSENSITIVE);

    /** Matches a single-quoted SQL literal at the start of a value, honouring {@code ''} escapes. */
    private static final Pattern LEADING_LITERAL = Pattern.compile("^'((?:[^']|'')*)'");

    private static final Pattern UPDATE_STATEMENT = Pattern.compile(
            "UPDATE\\s+(?:tshepo_authz\\.)?policy_rule\\s+SET\\s+(.*?)\\s+WHERE\\s+(.*?);",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** {@code name = 'x'} or {@code name IN ('x', 'y')} in an UPDATE's WHERE clause. */
    private static final Pattern NAME_PREDICATE = Pattern.compile(
            "\\bname\\s*(?:=\\s*'((?:[^']|'')*)'|IN\\s*\\(([^)]*)\\))",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern QUOTED = Pattern.compile("'((?:[^']|'')*)'");

    /** {@code active = false} in an UPDATE's SET clause — a rule being switched off. */
    private static final Pattern DEACTIVATION = Pattern.compile(
            "\\bactive\\s*=\\s*false\\b", Pattern.CASE_INSENSITIVE);

    /** A later {@code UPDATE} setting one column of one named rule. */
    record Correction(String column, String rule, String value, int scriptIndex) {
        static String key(String column, String rule) {
            // Escaped rather than a literal NUL: a literal one makes the source binary to git and
            // unreviewable in a diff. Same character, same key, readable source.
            return column + "\0" + rule;
        }

        String key() {
            return key(column, rule);
        }
    }

    /** A parse the guard could not make sense of — reported rather than silently skipped. */
    record ParseComplaint(String location, String detail) {
        @Override
        public String toString() {
            return location + " — " + detail;
        }
    }

    /** Everything a guard needs: the effective rules, plus whatever could not be parsed. */
    record Seeds(List<Rule> rules, List<ParseComplaint> complaints,
                 Map<String, Correction> corrections) {

        Optional<Rule> byName(String name) {
            return rules.stream().filter(r -> name.equals(r.name())).reduce((a, b) -> b);
        }

        /** Rules still claiming something: everything a later migration did not switch off. */
        List<Rule> live() {
            return rules.stream().filter(r -> !r.retired()).toList();
        }
    }

    private static Seeds cached;

    static synchronized Seeds read() {
        if (cached == null) {
            cached = parse(locateMigrations());
        }
        return cached;
    }

    // ════════════════════════════════════════════════════════════════════
    // Parsing
    // ════════════════════════════════════════════════════════════════════

    private static Seeds parse(List<Path> migrations) {
        List<String> scripts = migrations.stream().map(m -> stripComments(read(m))).toList();

        Map<String, Correction> corrections = new LinkedHashMap<>();
        for (int i = 0; i < scripts.size(); i++) {
            for (Correction c : parseCorrections(scripts.get(i), i)) {
                // Later migrations win; equal indexes cannot occur (one script per index).
                corrections.merge(c.key(), c, (a, b) -> a.scriptIndex() >= b.scriptIndex() ? a : b);
            }
        }

        Map<String, Integer> retiredAt = new LinkedHashMap<>();
        for (int i = 0; i < scripts.size(); i++) {
            for (String rule : parseRetirements(scripts.get(i))) {
                retiredAt.putIfAbsent(rule, i);
            }
        }

        List<Rule> rules = new ArrayList<>();
        List<ParseComplaint> complaints = new ArrayList<>();

        for (int fileIndex = 0; fileIndex < migrations.size(); fileIndex++) {
            String fileName = migrations.get(fileIndex).getFileName().toString();
            String sql = scripts.get(fileIndex);
            int rowsInFile = 0;

            for (Statement statement : parseInserts(sql)) {
                for (Row row : statement.rows()) {
                    rowsInFile++;
                    String where = fileName + ":" + lineOf(sql, row.offset());

                    if (row.values().size() != statement.columns().size()) {
                        complaints.add(new ParseComplaint(where, "seed row has "
                                + row.values().size() + " values for " + statement.columns().size()
                                + " columns; the parser cannot tell which value belongs to which "
                                + "column, and neither can a reviewer"));
                        continue;
                    }

                    Map<String, String> byColumn = new LinkedHashMap<>();
                    for (int i = 0; i < statement.columns().size(); i++) {
                        Value v = row.values().get(i);
                        byColumn.put(statement.columns().get(i), isSqlNull(v) ? null : literalOf(v));
                    }
                    String ruleName = byColumn.get("name");

                    // Overlay later corrections so callers see the effective value.
                    for (Map.Entry<String, String> column : byColumn.entrySet()) {
                        Correction fix = corrections.get(Correction.key(column.getKey(), ruleName));
                        if (fix != null && fix.scriptIndex() > fileIndex) {
                            column.setValue(fix.value());
                        }
                    }

                    Integer retired = retiredAt.get(ruleName);
                    rules.add(new Rule(ruleName, byColumn, fileName, lineOf(sql, row.offset()),
                            fileIndex, retired != null && retired > fileIndex ? retired : -1));
                }
            }

            // Self-calibrating coverage check: if a file seeds policy_rule, rows must have come out
            // of it. This complains when the SQL shape drifts away from what the parser understands,
            // rather than letting every guard downstream quietly degrade to checking nothing.
            if (sql.toUpperCase().contains("INSERT INTO TSHEPO_AUTHZ.POLICY_RULE") && rowsInFile == 0) {
                complaints.add(new ParseComplaint(fileName, "seeds policy_rule but no rows could be "
                        + "parsed; every seed guard is blind to this file. Update the parser in "
                        + SeededPolicyRules.class.getSimpleName() + "."));
            }
        }

        if (rules.isEmpty()) {
            fail("No policy_rule seed rows parsed from %d migration scripts — every seed guard "
                    + "would pass while checking nothing.", migrations.size());
        }
        return new Seeds(List.copyOf(rules), List.copyOf(complaints), Map.copyOf(corrections));
    }

    /**
     * Rule names identified by an UPDATE's WHERE clause, via {@code name = 'x'} or
     * {@code name IN ('x','y')}. An UPDATE that does not name rules cannot be attributed to any,
     * so it yields nothing rather than being assumed to affect something — the conservative
     * direction for a guard.
     */
    private static List<String> namesIn(String whereClause) {
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
        return names;
    }

    /** Rule names a script switches off via {@code UPDATE ... SET active = false WHERE name ...}. */
    private static List<String> parseRetirements(String sql) {
        List<String> retired = new ArrayList<>();
        Matcher stmt = UPDATE_STATEMENT.matcher(sql);
        while (stmt.find()) {
            if (!DEACTIVATION.matcher(stmt.group(1)).find()) {
                continue;
            }
            retired.addAll(namesIn(stmt.group(2)));
        }
        return retired;
    }

    /**
     * Extracts {@code UPDATE policy_rule SET <column> = '<value>' WHERE ... name ...} for every
     * single-quoted assignment in the SET clause. An UPDATE whose WHERE clause does not identify
     * rules by name cannot be attributed, so it is ignored rather than assumed to fix anything.
     */
    private static List<Correction> parseCorrections(String sql, int scriptIndex) {
        List<Correction> found = new ArrayList<>();
        Matcher stmt = UPDATE_STATEMENT.matcher(sql);
        while (stmt.find()) {
            String setClause = stmt.group(1);
            List<String> names = namesIn(stmt.group(2));
            if (names.isEmpty()) {
                continue;
            }
            Matcher assignment = Pattern
                    .compile("\\b([a-z_]+)\\s*=\\s*'((?:[^']|'')*)'", Pattern.CASE_INSENSITIVE)
                    .matcher(setClause);
            while (assignment.find()) {
                String column = assignment.group(1).toLowerCase();
                String value = assignment.group(2).replace("''", "'");
                for (String name : names) {
                    found.add(new Correction(column, name, value, scriptIndex));
                }
            }
        }
        return found;
    }

    // ════════════════════════════════════════════════════════════════════
    // Migration discovery
    // ════════════════════════════════════════════════════════════════════

    /**
     * Resolves the migration directory off the test classpath (main resources are on it), so this
     * does not depend on the working directory. Falls back to the module-relative source path for
     * IDE runs that do not copy resources.
     */
    static List<Path> locateMigrations() {
        Path dir = null;
        URL onClasspath = SeededPolicyRules.class.getClassLoader().getResource("db/migration");
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
            return fail("Cannot locate db/migration — every seed guard would silently pass without "
                    + "checking anything. Looked on the classpath and at "
                    + Paths.get("src/main/resources/db/migration").toAbsolutePath());
        }
        try (var files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".sql")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int lineOf(String sql, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < sql.length(); i++) {
            if (sql.charAt(i) == '\n') line++;
        }
        return line;
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
        if (!m.find()) {
            return v.text().trim();
        }
        String head = m.group(1).replace("''", "'");
        // Postgres implicit concatenation: adjacent literals across lines form one string, which
        // the descriptions use freely. Take them all, or a description reads as its first line.
        String rest = v.text().trim().substring(m.end());
        Matcher more = LEADING_LITERAL.matcher(rest.trim());
        while (more.find()) {
            head += more.group(1).replace("''", "'");
            rest = rest.trim().substring(more.end());
            more = LEADING_LITERAL.matcher(rest.trim());
        }
        return head;
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
