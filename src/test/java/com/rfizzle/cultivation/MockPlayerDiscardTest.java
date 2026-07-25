package com.rfizzle.cultivation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 guard that every connected mock player a gametest builds is discarded
 * before the test ends (mc-testing-mock, "Cleanup: always discard").
 *
 * <p>{@code MockPlayers.serverPlayerInLevel} finishes with
 * {@code PlayerList#placeNewPlayer}, which registers the player both in the
 * {@code ServerLevel} and in the player list. A test that returns without
 * calling {@code discard()} leaves it in {@code entityTickList} — ticked every
 * tick for the rest of the run — and holding a {@code DistanceManager} chunk
 * ticket. {@code discard()} releases exactly those two: it clears
 * {@code ServerLevel.players} and the tracker and ticket state through
 * {@code EntityCallbacks#onTrackingEnd}.
 *
 * <p>The player-list entry is deliberately left in place. {@code PlayerList#remove}
 * is the only path that clears it, so the retained player and its connection are
 * not reclaimed until shutdown — that boundary is accepted here rather than
 * papered over, because the per-tick and per-ticket cost is what actually grows
 * with the size of the suite.
 *
 * <p>The omission is invisible to every other signal: the player still behaves
 * correctly for the test that built it, so assertions pass, CI stays green, and
 * the cost accumulates silently across the suite. That is why it is checked here
 * rather than left to review — it went unnoticed long enough to need fixing
 * twice.
 *
 * <p>The gametest source set is not on the test classpath, so its classes cannot
 * be enumerated reflectively — the guard reads the source tree as text, the same
 * approach and for the same reason as {@link GametestRegistrationTest}.
 *
 * <p>Ownership is decided by return type. A method returning {@code ServerPlayer}
 * is a factory: it hands the player to its caller, so the caller owes the
 * discard and the factory itself is exempt. Every other method must discard each
 * player it binds, counting both direct {@code serverPlayerInLevel} calls and
 * calls to a factory declared in the same file. Discards are matched against the
 * variable each player was bound to rather than tallied loosely, so an unrelated
 * {@code entity.discard()} in the same method cannot stand in for a leaked
 * player. Checking per method rather than per file is what catches the case that
 * actually slipped through: three tests that each build two players against a
 * single {@code helper.succeed()}, where a file-level tally looks balanced while
 * half the players leak.
 *
 * <p>Scope is the connected replica only. The lightweight
 * {@code GameTestHelper#makeMockPlayer} stub is never added to the level or the
 * player list, so it cannot accrue this cost and is not required to discard.
 *
 * <p>Because a regex cannot see everything Java can express — constructors carry
 * no return type, and a declaration the pattern misses would drop a whole method
 * from the scan — {@link #theScanSeesEveryAcquisitionAndDiscard()} asserts that
 * every acquisition and discard token in each file lands inside some parsed
 * method body. A parser blind spot then fails loudly instead of quietly
 * exempting the code it cannot read.
 */
class MockPlayerDiscardTest {
    private static final Path GAMETEST_SOURCES = Path.of("src/gametest/java");

    /** The connected-replica factory whose product must be discarded. */
    private static final String ACQUIRE = "MockPlayers.serverPlayerInLevel";

    private static final String DISCARD = ".discard()";

    /** Matches a method declaration through its opening brace, capturing return type and name. */
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "(?:public|private|protected)\\s+(?:static\\s+)?(?:final\\s+)?"
                    + "([\\w.<>\\[\\], ?]+?)\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w.,\\s]+?)?\\{");

    /** Matches a local binding, capturing the variable name and its initializer. */
    private static final Pattern LOCAL_BINDING = Pattern.compile("(?:ServerPlayer|var)\\s+(\\w+)\\s*=\\s*([^;]*);");

    /** A method's declared return type, name, and body text with comments and literals stripped. */
    private record Method(String returnType, String name, String body) {
        /** A method handing a player to its caller: the caller owes the discard, not this method. */
        boolean isPlayerFactory() {
            return returnType.endsWith("ServerPlayer");
        }

        /** A factory that actually builds a player, so a call to it transfers ownership. */
        boolean isAcquiringFactory() {
            return isPlayerFactory() && body.contains(ACQUIRE);
        }
    }

    /** Every gametest source file, stripped of comments and literals, mapped to its parsed methods. */
    private static TreeMap<String, String> sources;
    private static Map<String, List<Method>> methods;

    @BeforeAll
    static void parseGametestTree() {
        sources = gametestSources();
        methods = new TreeMap<>();
        sources.forEach((file, source) -> methods.put(file, methodsOf(source, file)));
    }

    /**
     * Blanks comment and string/char literal contents so brace matching cannot be
     * thrown off by a brace inside an assertion message, and so the word
     * {@code discard} in prose is never counted as a call.
     */
    private static String stripCommentsAndLiterals(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            boolean hasNext = i + 1 < source.length();
            if (c == '/' && hasNext && source.charAt(i + 1) == '/') {
                while (i < source.length() && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && hasNext && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < source.length() && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(i + 2, source.length());
            } else if (source.startsWith("\"\"\"", i)) {
                // Must precede the single-quote branch: a text block's embedded quotes would
                // otherwise desync the pairing and silently swallow real code after it.
                out.append("\"\"");
                int close = source.indexOf("\"\"\"", i + 3);
                i = close < 0 ? source.length() : close + 3;
            } else if (c == '"' || c == '\'') {
                out.append(c).append(c);
                i++;
                while (i < source.length() && source.charAt(i) != c) {
                    i += source.charAt(i) == '\\' ? 2 : 1;
                }
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Every {@code .java} file under the gametest tree, mapped to its stripped source text. */
    private static TreeMap<String, String> gametestSources() {
        TreeMap<String, String> found = new TreeMap<>();
        try (Stream<Path> tree = Files.walk(GAMETEST_SOURCES)) {
            tree.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    found.put(GAMETEST_SOURCES.relativize(p).toString(),
                            stripCommentsAndLiterals(Files.readString(p, StandardCharsets.UTF_8)));
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

    /** Splits a stripped source file into its methods by brace matching from each declaration. */
    private static List<Method> methodsOf(String source, String file) {
        List<Method> found = new ArrayList<>();
        Matcher matcher = METHOD_DECLARATION.matcher(source);
        while (matcher.find()) {
            int open = matcher.end() - 1;
            int depth = 0;
            int i = open;
            for (; i < source.length(); i++) {
                char c = source.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}' && --depth == 0) {
                    break;
                }
            }
            if (depth != 0) {
                throw new AssertionError("unbalanced braces after " + file + "#" + matcher.group(2)
                        + " — the source scan cannot be trusted until the parser handles this file");
            }
            found.add(new Method(matcher.group(1).trim(), matcher.group(2), source.substring(open + 1, i)));
            // Resume past this body so nested declarations are not matched twice.
            matcher.region(i, source.length());
        }
        return found;
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        for (int from = haystack.indexOf(needle); from >= 0; from = haystack.indexOf(needle, from + needle.length())) {
            count++;
        }
        return count;
    }

    /** Players a method takes ownership of, split into those bound to a local and those not. */
    private record Acquired(Set<String> boundNames, int unbound) {}

    private static Acquired acquisitionsIn(Method method, List<Method> siblings) {
        List<Method> factories = siblings.stream().filter(s -> s.isAcquiringFactory() && s != method).toList();

        int direct = countOf(method.body(), ACQUIRE);
        int viaFactory = factories.stream().mapToInt(f -> countOf(method.body(), f.name() + "(")).sum();

        Set<String> bound = new LinkedHashSet<>();
        int boundCount = 0;
        Matcher binding = LOCAL_BINDING.matcher(method.body());
        while (binding.find()) {
            String initializer = binding.group(2);
            boolean acquires = initializer.contains(ACQUIRE)
                    || factories.stream().anyMatch(f -> initializer.contains(f.name() + "("));
            if (acquires) {
                bound.add(binding.group(1));
                boundCount++;
            }
        }
        return new Acquired(bound, Math.max(0, direct + viaFactory - boundCount));
    }

    @Test
    void everyAcquiredMockPlayerIsDiscarded() {
        TreeSet<String> offenders = new TreeSet<>();
        methods.forEach((file, fileMethods) -> {
            for (Method method : fileMethods) {
                if (method.isPlayerFactory()) {
                    continue; // Hands the player to its caller, which owes the discard.
                }
                Acquired acquired = acquisitionsIn(method, fileMethods);
                for (String name : acquired.boundNames()) {
                    if (!method.body().contains(name + DISCARD)) {
                        offenders.add(file + "#" + method.name() + " never calls " + name + DISCARD);
                    }
                }
                if (acquired.unbound() > 0) {
                    offenders.add(file + "#" + method.name() + " builds " + acquired.unbound()
                            + " mock player(s) without binding them to a local, so the discard cannot be checked");
                }
            }
        });
        assertTrue(offenders.isEmpty(),
                "connected mock players must be discarded before the test ends, or they keep being ticked and"
                        + " holding chunk tickets for the rest of the gametest run: " + offenders);
    }

    @Test
    void theScanSeesEveryAcquisitionAndDiscard() {
        // A declaration the regex cannot match would drop its whole method from the scan and exempt it
        // silently — the one failure mode a guard like this must not have. Every acquisition and discard
        // token in a file has to land inside some parsed body, or the parser is behind the source.
        TreeSet<String> unseen = new TreeSet<>();
        sources.forEach((file, source) -> {
            for (String token : List.of(ACQUIRE, DISCARD)) {
                int inFile = countOf(source, token);
                int inMethods = methods.get(file).stream().mapToInt(m -> countOf(m.body(), token)).sum();
                if (inFile != inMethods) {
                    unseen.add(file + " has " + inFile + " '" + token + "' token(s) but the parser found "
                            + inMethods + " inside method bodies");
                }
            }
        });
        assertTrue(unseen.isEmpty(),
                "the method parser cannot see part of the gametest source, so the discard check silently"
                        + " skips it: " + unseen);
    }

    @Test
    void theGuardActuallySeesMockPlayerAcquisitions() {
        // Without this, a rename of MockPlayers.serverPlayerInLevel would make the checks above vacuous —
        // zero acquisitions found everywhere, green forever, guarding nothing.
        int total = 0;
        TreeSet<String> factories = new TreeSet<>();
        for (var entry : methods.entrySet()) {
            for (Method method : entry.getValue()) {
                if (method.isAcquiringFactory()) {
                    factories.add(entry.getKey() + "#" + method.name());
                } else if (!method.isPlayerFactory()) {
                    Acquired acquired = acquisitionsIn(method, entry.getValue());
                    total += acquired.boundNames().size() + acquired.unbound();
                }
            }
        }
        assertTrue(total >= 20,
                "the scan found only " + total + " mock-player acquisitions across " + GAMETEST_SOURCES
                        + " — the parser or the " + ACQUIRE + " token is stale, so the discard check is vacuous");
        assertTrue(factories.size() >= 2,
                "expected the ServerPlayer-returning factories to be recognized as ownership handoffs,"
                        + " found: " + factories);
    }
}
