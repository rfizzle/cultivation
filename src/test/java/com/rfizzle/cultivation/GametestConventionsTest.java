package com.rfizzle.cultivation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 guard for the two {@code @GameTest} attribute conventions in
 * {@code mc-mod-testing}'s naming section: a non-default {@code timeoutTicks} is a
 * named constant, and a {@code batch} is a mod-prefixed camelCase named constant.
 *
 * <p>Both were surveyed rather than enforced, and both drifted — eleven bare
 * timeout literals against zero constants, and one batch written inline in two
 * places. A literal repeated across a suite is N edits when the timing changes and
 * it never says <em>why</em> this suite needs longer than the default; an inline
 * batch string is a typo away from silently splitting one batch into two, which
 * shows up as a flake rather than as an error.
 *
 * <p>The mod prefix is the half worth asserting on the batch value. Batch names
 * share one namespace across every mod in a gametest run, so an unprefixed
 * {@code weather} would merge with a sibling member's identically-named batch the
 * first time two members are tested together — and merged batches run
 * concurrently, which is exactly the property a batch is declared to avoid.
 *
 * <p>Like the other gametest guards here, this reads the source tree as text: the
 * gametest source set is not on the test classpath, so the annotations cannot be
 * enumerated reflectively.
 */
class GametestConventionsTest {
    private static final Path GAMETEST_SOURCES = Path.of("src/gametest/java");

    /** A {@code timeoutTicks} assigned a bare number rather than a named constant. */
    private static final Pattern BARE_TIMEOUT = Pattern.compile("timeoutTicks\\s*=\\s*\\d");

    /** A {@code batch} assigned a string literal rather than a named constant. */
    private static final Pattern BARE_BATCH = Pattern.compile("batch\\s*=\\s*\"");

    /** The declaration of a batch constant, capturing its value. */
    private static final Pattern BATCH_CONSTANT =
            Pattern.compile("static\\s+final\\s+String\\s+\\w*BATCH\\w*\\s*=\\s*\"([^\"]*)\"");

    /** Mod-prefixed camelCase: {@code cultivation} then at least one capitalised word. */
    private static final Pattern PREFIXED_CAMEL =
            Pattern.compile(Cultivation.MOD_ID + "(?:[A-Z][A-Za-z0-9]*)+");

    private static TreeMap<String, String> gametestSources() {
        TreeMap<String, String> found = new TreeMap<>();
        try (Stream<Path> tree = Files.walk(GAMETEST_SOURCES)) {
            tree.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    found.put(GAMETEST_SOURCES.relativize(p).toString(),
                            Files.readString(p, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new AssertionError("could not walk " + GAMETEST_SOURCES, e);
        } catch (UncheckedIOException e) {
            throw new AssertionError("could not read a source file under " + GAMETEST_SOURCES, e.getCause());
        }
        return found;
    }

    @Test
    void everyNonDefaultTimeoutIsANamedConstant() {
        TreeSet<String> offenders = new TreeSet<>();
        gametestSources().forEach((file, source) -> {
            if (BARE_TIMEOUT.matcher(source).find()) {
                offenders.add(file);
            }
        });
        assertTrue(offenders.isEmpty(),
                "timeoutTicks must be a named constant, never a bare number at the annotation —"
                        + " a literal repeated across a suite is one edit per method when the timing"
                        + " changes, and it never records why this suite needs longer than the"
                        + " default: " + offenders);
    }

    @Test
    void everyBatchIsANamedConstant() {
        TreeSet<String> offenders = new TreeSet<>();
        gametestSources().forEach((file, source) -> {
            if (BARE_BATCH.matcher(source).find()) {
                offenders.add(file);
            }
        });
        assertTrue(offenders.isEmpty(),
                "batch must be a named constant, never an inline string — two spellings of one"
                        + " intended batch split it in half, and concurrent execution is what a batch"
                        + " is declared to prevent: " + offenders);
    }

    @Test
    void everyBatchNameIsModPrefixedCamelCase() {
        TreeSet<String> offenders = new TreeSet<>();
        gametestSources().forEach((file, source) -> {
            Matcher matcher = BATCH_CONSTANT.matcher(source);
            while (matcher.find()) {
                String value = matcher.group(1);
                if (!PREFIXED_CAMEL.matcher(value).matches()) {
                    offenders.add(file + " declares batch \"" + value + "\"");
                }
            }
        });
        assertTrue(offenders.isEmpty(),
                "batch names must be mod-prefixed camelCase (e.g. cultivationWeather) — batch names"
                        + " share one namespace across every mod in a run, so an unprefixed name"
                        + " merges with a sibling member's: " + offenders);
    }

    /**
     * Without this, a rename of either attribute would make the checks above vacuous:
     * zero matches found everywhere, green forever, guarding nothing.
     */
    @Test
    void theGuardActuallySeesTheAnnotationAttributes() {
        int timeouts = 0;
        int batches = 0;
        for (String source : gametestSources().values()) {
            timeouts += count(source, "timeoutTicks");
            batches += count(source, "batch = ");
        }
        assertTrue(timeouts >= 8,
                "found only " + timeouts + " timeoutTicks attributes across " + GAMETEST_SOURCES
                        + " — the scan is stale, so the constant check is vacuous");
        assertTrue(batches >= 8,
                "found only " + batches + " batch attributes across " + GAMETEST_SOURCES
                        + " — the scan is stale, so the batch checks are vacuous");
    }

    private static int count(String haystack, String needle) {
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}
