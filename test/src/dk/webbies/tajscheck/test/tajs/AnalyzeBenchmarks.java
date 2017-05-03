package dk.webbies.tajscheck.test.tajs;

import dk.webbies.tajscheck.Main;
import dk.webbies.tajscheck.benchmark.Benchmark;
import dk.webbies.tajscheck.test.dynamic.RunBenchmarks;
import dk.webbies.tajscheck.util.Util;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by erik1 on 19-12-2016.
 */
@RunWith(Parameterized.class)
public class AnalyzeBenchmarks {

    @SuppressWarnings("WeakerAccess")
    @Parameterized.Parameter
    public Benchmark benchmark = null;

    // Benchmarks that seem analyzeable.
    private static final Set<String> whitelist = new HashSet<>(Arrays.asList(
            "D3.js",
            "q",
            "async",
            "Redux",
            "Redux",
            "Redux",
            "Leaflet",
            "Ace",
            "reveal.js",
            "intro.js",
            "PleaseJS",
            "highlight.js",
            "RxJS",
            "Zepto.js",
            "pathjs",
            "Moment.js",
            "CodeMirror",
            "PhotoSwipe",
            "Jasmine",
            "Swiper",
            "box2dweb",
            "Sortable",
            "accounting.js",
            "CreateJS",
            "lunr.js",
            "jQuery",
            "Knockout"
    ));

    // Benchmarks that does not invoke any DOM functions, and are on the whitelist // TODO: Fill.
    private static final Set<String> simpleBenchmarks = new HashSet<>(Arrays.asList(
            "q",
            "async"
    ));

    // Benchmarks that for various reasons are unanalyzeable.
    private static final Set<String> blacklist = new HashSet<>(Arrays.asList(
            // because it has getters/setters, which TAJS does not support
            "PDF.js",
            "Vue.js",
            "three.js",
            "Ember.js",
            "Polymer", // <- because webcomponents has getter.

            "RequireJS", // weird error, replicated in TestMicro
            "QUnit", // weird error with arrays.
            "React", // No transfer function for Object.freeze.
            "Modernizr", // Run a WebGL function that is unsupported. (and sometimes it timeouts)
            "Hammer.js", // Object.assign crashes TAJS
            "Medium Editor", // "crashes" TAJS when calling Node.contains().
            "MathJax" // "Unevalable eval: window"
            ));

    // Benchmarks where just the initialization reaches a timeout
    private static final Set<String> timeouts = new HashSet<>(Arrays.asList(
            "AngularJS",
            "Chart.js",
            "PeerJS",
            "PixiJS",
            "Foundation",
            "Materialize",
            "P2.js",
            "bluebird",
            "Fabric.js",
            "Ionic",
            "Handlebars",
            "Video.js",
            "Sugar",
            "Backbone.js",
            "axios",
            "Lodash",
            "Underscore.js"
    ));

    static {
        if (!Util.concat(whitelist, blacklist, timeouts, simpleBenchmarks).stream().allMatch(RunBenchmarks.benchmarks.keySet()::contains)) {
            System.err.println("AnalyzeBenchmarks: A benchmark was misspelled");
        }
        if (!simpleBenchmarks.stream().allMatch(whitelist::contains)) {
            System.err.println("AnalyzeBenchmarks: A benchmark in SimpleBenchmarks wasn't in the whiteList");
        }
    }

    // TODO: I need way better type-errors when running statically.

    @Parameterized.Parameters(name = "{0}")
    public static List<Benchmark> getBenchmarks() {
        return RunBenchmarks.getBenchmarks().stream()
                .filter(bench -> !blacklist.contains(bench.name))
                .filter(bench -> !timeouts.contains(bench.name))
//                .filter(bench -> !whitelist.contains(bench.name))
                .map(Benchmark::useTAJS)
                .collect(Collectors.toList());
    }

    // TODO: re-introduce the "small drivers", and analyze those. Use a parametarized test case.

    @Test
//    @Ignore // Mostly timeouts
    public void analyzeBenchmark() throws Exception {
        // Just testing that it CAN be analyzed.

        Main.writeFullDriver(benchmark);

        double size = Util.readFile(Main.getFolderPath(benchmark) + Main.TEST_FILE_NAME).length() / 1024.0;

        System.out.println(Util.toFixed(size, 2) + "kb driver");

        if (size > 2000) {
            System.out.println("Skipping because driver is BIG");
            return; // Currently ignoring when the driver is too big.
        }

        try {
            TAJSUtil.run(benchmark.useTAJS(), 10 * 60);
        } catch (TimeoutException e) {
            System.out.println("Timeout");
            System.out.println(e.toString());

        }
    }

    @Test
    public void initialize() throws Exception {
        try {
            TAJSUtil.run(benchmark.useTAJS().withOptions(options -> options.getBuilder().setOnlyInitialize(true).build()), 60);
        } catch (TimeoutException e) {
            System.out.println("Timeout");
            System.out.println(e.toString());
            throw e;
        }
    }
}
