package dev.isaacudy.udytils.mutation.driver;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Runs JUnit Platform tests in one JVM for the mutation runner, switching the active mutant
 * between requests. Commands arrive on stdin, one per line, tab-separated. Replies go to the
 * original stdout, each line prefixed with {@link #PREFIX}; test output goes to the log file
 * named by the first argument.
 *
 * <ul>
 *   <li>{@code discover <class roots>}: one {@code test <uid> <class> <tags>} line per test.</li>
 *   <li>{@code baseline <tags>}: runs the discovered tests with no mutant active, one request per
 *   top-level class; one {@code baseline <uid> <passed|failed> <ms> <hit ids>} line per test.</li>
 *   <li>{@code run <mutant> <n>}, then n lines {@code <timeout ms> <uid>}: runs the tests in order
 *   and stops at the first failure; one {@code result <killed|timed-out|survived> <uid> <ms>} line.</li>
 *   <li>{@code exit}.</li>
 * </ul>
 * Every reply ends with an {@code end} line.
 */
public final class JUnitDriver {
    static final String PREFIX = "@@mutation ";
    private static final String TIMEOUT_PARAMETER = "junit.jupiter.execution.timeout.default";

    private final PrintStream protocol;
    private final Launcher launcher = LauncherFactory.create();
    private final Switch mutants = new Switch();
    private final Map<String, String> classByTest = new LinkedHashMap<>();
    private final Map<String, Set<String>> tagsByTest = new HashMap<>();
    // A test annotated with @Timeout ignores the Jupiter default timeout, so the driver interrupts the test thread itself.
    private final java.util.concurrent.ScheduledExecutorService timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "mutation-test-timeout");
        thread.setDaemon(true);
        return thread;
    });

    private JUnitDriver(PrintStream protocol) {
        this.protocol = protocol;
    }

    public static void main(String[] args) throws Exception {
        PrintStream protocol = new PrintStream(new FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream log = new PrintStream(new FileOutputStream(args[0], true), true, StandardCharsets.UTF_8);
        System.setOut(log);
        System.setErr(log);
        new JUnitDriver(protocol).serve(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)));
        System.exit(0);
    }

    private void serve(BufferedReader input) throws Exception {
        String line;
        while ((line = input.readLine()) != null) {
            String[] parts = line.split("\t", -1);
            switch (parts[0]) {
                case "discover": discover(parts[1]); break;
                case "baseline": baseline(parts.length > 1 ? parts[1] : ""); break;
                case "run": run(Long.parseLong(parts[1]), readTests(input, Integer.parseInt(parts[2]))); break;
                case "exit": return;
                default: throw new IllegalArgumentException("Unknown driver command: " + parts[0]);
            }
            reply("end");
        }
    }

    private void discover(String roots) {
        Set<Path> paths = new HashSet<>();
        for (String root : roots.split(java.io.File.pathSeparator)) {
            Path path = Paths.get(root);
            if (path.toFile().exists()) paths.add(path);
        }
        TestPlan plan = launcher.discover(LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClasspathRoots(paths)).build());
        for (TestIdentifier root : plan.getRoots()) {
            for (TestIdentifier test : plan.getDescendants(root)) {
                if (!test.isTest()) continue;
                String className = className(plan, test);
                if (className == null) continue;
                Set<String> tags = test.getTags().stream().map(t -> t.getName()).collect(Collectors.toSet());
                classByTest.put(test.getUniqueId(), className);
                tagsByTest.put(test.getUniqueId(), tags);
                reply("test\t" + test.getUniqueId() + "\t" + className + "\t" + String.join(",", tags));
            }
        }
    }

    private void baseline(String tagList) {
        Set<String> tags = tagList.isEmpty() ? Set.of() : Set.of(tagList.split(","));
        Map<String, List<String>> testsByClass = new LinkedHashMap<>();
        classByTest.forEach((test, className) -> {
            if (tags.isEmpty() || tagsByTest.get(test).stream().anyMatch(tags::contains)) {
                testsByClass.computeIfAbsent(topLevel(className), k -> new ArrayList<>()).add(test);
            }
        });
        mutants.setActive(0);
        mutants.setRecordHits(true);
        try {
            for (Map.Entry<String, List<String>> entry : testsByClass.entrySet()) {
                LauncherDiscoveryRequestBuilder request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(DiscoverySelectors.selectClass(entry.getKey()));
                if (!tags.isEmpty()) request.filters(TagFilter.includeTags(new ArrayList<>(tags)));
                mutants.clearHits();
                Recorder recorder = new Recorder(mutants);
                launcher.execute(request.build(), recorder);
                Thread.interrupted();
                for (String test : entry.getValue()) {
                    Recorder.Outcome outcome = recorder.outcomes.get(test);
                    if (outcome == null) continue;
                    Set<Long> hits = new HashSet<>(outcome.hits);
                    hits.addAll(recorder.containerHits);
                    String ids = hits.stream().map(String::valueOf).collect(Collectors.joining(","));
                    reply("baseline\t" + test + "\t" + (outcome.failure == null ? "passed" : "failed") + "\t" + outcome.millis + "\t" + ids);
                }
            }
        } finally {
            mutants.setRecordHits(false);
            mutants.clearHits();
        }
    }

    private void run(long mutant, List<String[]> tests) {
        mutants.setActive(mutant);
        try {
            long started = System.nanoTime();
            for (String[] test : tests) {
                LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(DiscoverySelectors.selectUniqueId(test[1]))
                    .configurationParameter(TIMEOUT_PARAMETER, test[0] + " ms")
                    .build();
                Recorder recorder = new Recorder(null);
                Thread runner = Thread.currentThread();
                java.util.concurrent.atomic.AtomicBoolean fired = new java.util.concurrent.atomic.AtomicBoolean();
                java.util.concurrent.ScheduledFuture<?> interrupt = timer.schedule(() -> {
                    fired.set(true);
                    runner.interrupt();
                }, Long.parseLong(test[0]), java.util.concurrent.TimeUnit.MILLISECONDS);
                launcher.execute(request, recorder);
                interrupt.cancel(false);
                Thread.interrupted();
                Throwable failure = recorder.firstFailure();
                if (fired.get()) {
                    reply("result\ttimed-out\t" + test[1] + "\t" + elapsed(started) + "\t" +
                        (failure == null ? "passed after the timeout" : describe(failure)));
                    return;
                }
                if (failure != null) {
                    String verdict = isTimeout(failure) ? "timed-out" : "killed";
                    reply("result\t" + verdict + "\t" + test[1] + "\t" + elapsed(started) + "\t" + describe(failure));
                    return;
                }
            }
            reply("result\tsurvived\t-\t" + elapsed(started) + "\t-");
        } finally {
            mutants.setActive(0);
        }
    }

    private static String describe(Throwable failure) {
        String message = String.valueOf(failure.getMessage()).split("\n", 2)[0];
        if (message.length() > 200) message = message.substring(0, 200);
        return (failure.getClass().getName() + ": " + message).replace('\t', ' ');
    }

    private static boolean isTimeout(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException) return true;
        }
        return false;
    }

    private static List<String[]> readTests(BufferedReader input, int count) throws Exception {
        List<String[]> tests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) tests.add(input.readLine().split("\t", 2));
        return tests;
    }

    private static String className(TestPlan plan, TestIdentifier test) {
        for (TestIdentifier node = test; node != null; node = plan.getParent(node).orElse(null)) {
            Object source = node.getSource().orElse(null);
            if (source instanceof MethodSource) return ((MethodSource) source).getClassName();
            if (source instanceof ClassSource) return ((ClassSource) source).getClassName();
        }
        return null;
    }

    private static String topLevel(String className) {
        int nested = className.indexOf('$');
        return nested < 0 ? className : className.substring(0, nested);
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private void reply(String line) {
        protocol.println(PREFIX + line);
    }

    /** Records each test's outcome, and with a {@link Switch}, the mutant hits of each test. */
    private static final class Recorder implements TestExecutionListener {
        static final class Outcome {
            Throwable failure;
            long millis;
            Set<Long> hits = Set.of();
        }

        private final Switch hits;
        final Map<String, Outcome> outcomes = new HashMap<>();
        final Set<Long> containerHits = new HashSet<>();
        private final Map<String, Long> startTimes = new HashMap<>();
        private Throwable containerFailure;

        Recorder(Switch hits) {
            this.hits = hits;
        }

        @Override
        public void executionStarted(TestIdentifier id) {
            if (hits != null && id.isTest()) containerHits.addAll(hits.takeHits());
            startTimes.put(id.getUniqueId(), System.nanoTime());
        }

        @Override
        public void executionFinished(TestIdentifier id, TestExecutionResult result) {
            Throwable failure = result.getStatus() == TestExecutionResult.Status.FAILED
                ? result.getThrowable().orElse(new AssertionError("failed")) : null;
            if (id.isTest()) {
                Outcome outcome = new Outcome();
                outcome.failure = failure;
                outcome.millis = (System.nanoTime() - startTimes.getOrDefault(id.getUniqueId(), System.nanoTime())) / 1_000_000;
                if (hits != null) outcome.hits = hits.takeHits();
                outcomes.put(id.getUniqueId(), outcome);
            } else {
                if (hits != null) containerHits.addAll(hits.takeHits());
                if (failure != null && containerFailure == null) containerFailure = failure;
            }
        }

        Throwable firstFailure() {
            for (Outcome outcome : outcomes.values()) {
                if (outcome.failure != null) return outcome.failure;
            }
            return containerFailure;
        }
    }

    /** Reflective access to {@code dev.isaacudy.udytils.mutation.Mutants} on the test classpath. */
    private static final class Switch {
        private final Object instance;
        private final Method setActive;
        private final Method setRecordHits;
        private final Method hits;
        private final Method clearHits;

        Switch() {
            Object found = null;
            Method active = null, record = null, read = null, clear = null;
            try {
                Class<?> type = Class.forName("dev.isaacudy.udytils.mutation.Mutants");
                found = type.getField("INSTANCE").get(null);
                active = type.getMethod("setActive", long.class);
                record = type.getMethod("setRecordHits", boolean.class);
                read = type.getMethod("hits");
                clear = type.getMethod("clearHits");
            } catch (ClassNotFoundException e) {
                // A test classpath without mutation-runtime contains no mutated code: every test records no hits.
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("dev.isaacudy.udytils.mutation.Mutants on the test classpath does not match the driver.", e);
            }
            instance = found;
            setActive = active;
            setRecordHits = record;
            hits = read;
            clearHits = clear;
        }

        void setActive(long id) { invoke(setActive, id); }

        void setRecordHits(boolean record) { invoke(setRecordHits, record); }

        void clearHits() { invoke(clearHits); }

        @SuppressWarnings("unchecked")
        Set<Long> takeHits() {
            if (instance == null) return Set.of();
            Set<Long> taken = new HashSet<>((Collection<Long>) invoke(hits));
            clearHits();
            return taken;
        }

        private Object invoke(Method method, Object... args) {
            if (instance == null) return null;
            try {
                return method.invoke(instance, args);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
