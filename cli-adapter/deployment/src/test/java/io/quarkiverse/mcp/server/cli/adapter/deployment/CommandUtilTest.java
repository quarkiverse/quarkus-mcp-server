package io.quarkiverse.mcp.server.cli.adapter.deployment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;

import picocli.CommandLine.Option;

public class CommandUtilTest {

    public static class CommandWithOptions {
        @Option(names = "--json", description = "Output in JSON format.")
        boolean jsonOutput;

        @Option(names = { "-c", "--cluster" }, description = "The cluster name.")
        String clusterNameOrId;

        @Option(names = "-v", description = "Verbose output.")
        boolean verbose;
    }

    public static class Simple {
    }

    public static class SimpleBeanWithFieldInjection {
        @Inject
        Simple simple;
    }

    public static class SimpleBeanWithInheritedFieldInjection extends SimpleBeanWithFieldInjection {
    }

    public static class SimpleBeanWithConstructorInjection {
        private final Simple simple;

        public SimpleBeanWithConstructorInjection(Simple simple) {
            this.simple = simple;
        }
    }

    @Test
    public void testSimpleObject() throws Exception {
        Index index = Index.of(Simple.class);
        ClassInfo classInfo = index.getClassByName(DotName.createSimple(Simple.class.getName()));
        assertTrue(CommandUtil.canBeInstantiated(classInfo, index));
    }

    @Test
    public void testClassWithFieldInjection() throws Exception {
        Index index = Index.of(Simple.class, SimpleBeanWithFieldInjection.class);
        ClassInfo classInfo = index.getClassByName(DotName.createSimple(SimpleBeanWithFieldInjection.class.getName()));
        assertFalse(CommandUtil.canBeInstantiated(classInfo, index));
    }

    @Test
    public void testClassWithInheritedFieldInjection() throws Exception {
        Index index = Index.of(Simple.class, SimpleBeanWithFieldInjection.class, SimpleBeanWithInheritedFieldInjection.class);
        ClassInfo classInfo = index.getClassByName(DotName.createSimple(SimpleBeanWithInheritedFieldInjection.class.getName()));
        assertFalse(CommandUtil.canBeInstantiated(classInfo, index));
    }

    @Test
    public void testClassWithConstructorInjection() throws Exception {
        Index index = Index.of(Simple.class, SimpleBeanWithFieldInjection.class);
        ClassInfo classInfo = index.getClassByName(DotName.createSimple(SimpleBeanWithFieldInjection.class.getName()));
        assertFalse(CommandUtil.canBeInstantiated(classInfo, index));
    }

    @Test
    public void shouldFindMaxCommonPrefix() {
        assertEquals("io.quarkiverse.mcp.server.cli.adapter.deployment.",
                CommandUtil.findCommonPrefix(Set.of(
                        "io.quarkiverse.mcp.server.cli.adapter.deployment.CommandUtilTest",
                        "io.quarkiverse.mcp.server.cli.adapter.deployment.MyCommand")));

        assertEquals("", CommandUtil.findCommonPrefix(Set.of(
                "io.quarkiverse.mcp.server.cli.adapter.deployment.CommandUtilTest",
                "io.quarkiverse.mcp.server.cli.adapter.deployment.CommandUtil",
                "org.acme.MyCommand")));
    }

    @Test
    public void testListOptionsShouldUseLongestName() throws Exception {
        Index index = Index.of(CommandWithOptions.class);
        ClassInfo classInfo = index.getClassByName(DotName.createSimple(CommandWithOptions.class.getName()));
        Map<String, FieldInfo> options = CommandUtil.listOptions(classInfo, index);
        assertEquals(3, options.size());
        // Short + long: longest name should be the key
        assertTrue(options.containsKey("--cluster"));
        assertFalse(options.containsKey("-c"));
        assertEquals("clusterNameOrId", options.get("--cluster").name());
        // Single long name
        assertTrue(options.containsKey("--json"));
        assertEquals("jsonOutput", options.get("--json").name());
        // Single short name
        assertTrue(options.containsKey("-v"));
        assertEquals("verbose", options.get("-v").name());
    }

}
