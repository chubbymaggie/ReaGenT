package dk.webbies.tajscheck.test.tajs.analyze;

import dk.webbies.tajscheck.benchmark.Benchmark;
import dk.webbies.tajscheck.benchmark.options.CheckOptions;
import dk.webbies.tajscheck.benchmark.options.staticOptions.LimitTransfersRetractionPolicy;
import dk.webbies.tajscheck.benchmark.options.staticOptions.StaticOptions;
import dk.webbies.tajscheck.benchmark.options.staticOptions.expansionPolicy.LateExpansionToFunctionsWithConstructedArguments;
import dk.webbies.tajscheck.test.dynamic.RunBenchmarks;
import dk.webbies.tajscheck.tajstester.TAJSUtil;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by erik1 on 19-12-2016.
 */
@RunWith(Parameterized.class)
public class AnalyzeBenchmarks extends TestCase {
    private final static int BENCHMARK_TIMEOUT = 60 * 60;
    private final static int INIT_TIMEOUT = 10 * 60;

    @SuppressWarnings("WeakerAccess")
    @Parameterized.Parameter
    public Benchmark benchmark = null;

    // Benchmarks that seem analyzeable.
    // TODO: Most of these remarks are before the rebase to extended.
    static final Set<String> whitelist = new HashSet<>(Arrays.asList(
            "Sortable", // can analyze
            "async", // currently loops/takes to long.
            "PleaseJS", // can analyze
            "PhotoSwipe", // encounters (cannot construct intersectionType) at top-level constructor.
            "Knockout", // 48 violations in the top-level object. So no methods are called.
            "Swiper", // Top level constructor gets retracted (takes way to long).
            "pathjs", // can analyze.
            "reveal.js", // can analyze.
            "accounting.js", // ~4 minutes on my desktop.
            "PDF.js", // can analyze. (but lots of timeouts).
            "Hammer.js", // TODO: Seemingly have some false positives (like Hammer.TouchAction.preventDefaults).
            "intro.js", // TODO: Why does MethodCallTest(introJs().setOptions(obj)) end up not being called.

            "Moment.js", // can analyze (requires lots of memory)
            "Zepto.js", // can analyze. (TODO: Try to run with a lot of mem, it after rebase it seems different) (Before: Gets a useless spurious result after few minutes, because: We analyze the global object, is fine, we analyze some methods get some state, doing this a spurious write is performed on the global object, this causes everything except global object to be removed from type-to-test, and the single spurious error is reported.)
            "CodeMirror", // TODO: Crashes (after 6 minutes on my desktop) with "Reading undefined register v10).

            "Jasmine", // has a lot of globals that it cannot find (because they aren't registered).
            "Medium Editor", // TODO: Top level object not found.
            "Handlebars", // TODO: Error in top-level object.
            "Redux", // TODO: Top level object not found (try to not have an exports object)
            "axios", // TODO: Module not found (node?)
            "PeerJS", // TODO: Top level constructor always returns exceptionally.
            "QUnit", // TODO: Takes a long time
            "highlight.js", // TODO: Takes a long time
            "Leaflet" // initialization crashes on line 2302, because TAJS thinks it is reading an undefined property.
    ));

    static final Set<String> blackList = new HashSet<>(Arrays.asList(
            "AngularJS",
            "MathJax",
            "Chart.js",
            "PixiJS",
            "P2.js",
            "bluebird",
            "Foundation",
            "Materialize",
            "Backbone.js",
            "Vue.js",
            "D3.js",
            "Modernizr",
            "Fabric.js",
            "Video.js",
            "q",
            "RequireJS",
            "CreateJS",
            "Lodash",
            "Sugar",
            "Ace",
            "Ember",
            "QUnit",
            "Polymer",
            "Backbone.js",
            "React",
            "Knockout",
            "q",
            "jQuery",
            "Underscore.js",
            "RxJS",
            "three.js",
            "Ember.js",
            "Ionic"
    ));

    @Parameterized.Parameters(name = "{0}")
    public static List<Benchmark> getBenchmarks() {
        return RunBenchmarks.getBenchmarks().stream()
                .filter(bench -> whitelist.contains(bench.name))
                .collect(Collectors.toList());
    }

    public static Function<CheckOptions.Builder, StaticOptions.Builder> options() {
        return options -> options
                .setCombineNullAndUndefined(true) // because no-one cares.

                // same as default, but just to be explicit about it.
                .setConstructClassInstances(false)
                .setConstructClassTypes(false)
                .setConstructAllTypes(false)

                .staticOptions
                    .setKillGetters(true) // because getters currently causes the analysis to loop. // TODO: Still?

                    .setRetractionPolicy(new LimitTransfersRetractionPolicy(10000, 0))

                    .setCheckAllPropertiesAreFunctionCall(true)
                    .setPropagateStateFromFailingTest(false)

                    .setArgumentValuesStrategy(StaticOptions.ArgumentValuesStrategy.FEEDBACK_IF_POSSIBLE)
                    .setExpansionPolicy(new LateExpansionToFunctionsWithConstructedArguments())
                ;
    }

    @Test(timeout = (int)(BENCHMARK_TIMEOUT * 1000 * 1.3))
    public void analyzeBenchmark() throws Exception {
        Benchmark benchmark = this.benchmark.withOptions(options());
        TAJSUtil.TajsAnalysisResults result = TAJSUtil.runNoDriver(benchmark, BENCHMARK_TIMEOUT);
        System.out.println(result);
    }

    @Test(timeout = (int)(BENCHMARK_TIMEOUT * 1000 * 1.3))
    public void analyzeBenchmarkPatched() throws Exception {
        Benchmark benchmark = getPatchedBenchmark(this.benchmark);
        if (benchmark == null) {
            return;
        }
        benchmark = benchmark.withOptions(options());
        TAJSUtil.TajsAnalysisResults result = TAJSUtil.runNoDriver(benchmark, BENCHMARK_TIMEOUT);
        System.out.println(result);
        assert(!result.timedout);
    }

    public static Benchmark getPatchedBenchmark(Benchmark benchmark) {
        Path dtspath = Paths.get(benchmark.dTSFile);
        Path entryPath = Paths.get(benchmark.jsFile);
        String patched = dtspath.getParent().resolve("patched." + dtspath.getFileName()).toString();
        if (!new File(patched).exists()) {
            return null;
        } else {
            String patchedEntry = entryPath.getParent().resolve("patched." + entryPath.getFileName()).toString();
            return benchmark
                    .withDecl(patched)
                    .withJsFile(patchedEntry);
        }
    }

    @Test(timeout = (int)(INIT_TIMEOUT * 1000 * 1.3))
    public void initialize() throws Exception {
        Benchmark benchmark = this.benchmark.withOptions(options -> options().apply(options).getOuterBuilder().setOnlyInitialize(true));
        TAJSUtil.TajsAnalysisResults result = TAJSUtil.runNoDriver(benchmark, INIT_TIMEOUT);
        System.out.println(result);
        assert(!result.timedout);
    }
}
