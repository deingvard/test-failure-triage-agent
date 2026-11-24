package io.github.deingvard.triage.io;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.deingvard.triage.model.FailureCase;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Extracts failed and errored test cases from JUnit / Maven Surefire XML reports.
 * <p>
 * The XML parser is hardened against XXE: DOCTYPE declarations and external entities are rejected,
 * because CI reports are untrusted input.
 */
public final class JUnitXmlReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JUnitXmlReader() {
    }

    /**
     * Reads every {@code TEST-*.xml} in a directory (or a single file) and returns the failures.
     *
     * @param path        report directory or file
     * @param historyFile optional JSON map {@code "Class#method" -> "PPFP..."}; may be null
     */
    public static List<FailureCase> read(Path path, Path historyFile) {
        Map<String, String> history = historyFile == null ? Map.of() : readHistory(historyFile);
        List<FailureCase> failures = new ArrayList<>();
        for (Path file : reportFiles(path)) {
            failures.addAll(readFile(file, history));
        }
        return failures;
    }

    static List<FailureCase> readFile(Path file, Map<String, String> history) {
        Document doc = parse(file);
        NodeList testcases = doc.getElementsByTagName("testcase");
        List<FailureCase> failures = new ArrayList<>();
        for (int i = 0; i < testcases.getLength(); i++) {
            Element testcase = (Element) testcases.item(i);
            Element problem = firstChild(testcase, "failure");
            if (problem == null) {
                problem = firstChild(testcase, "error");
            }
            if (problem == null) {
                continue; // passed or skipped
            }
            String testName = simpleName(testcase.getAttribute("classname")) + "#" + testcase.getAttribute("name");
            String body = problem.getTextContent().strip();
            failures.add(new FailureCase(
                    testName,
                    testName,
                    guessSuite(testcase.getAttribute("classname")),
                    problem.getAttribute("type"),
                    problem.getAttribute("message"),
                    body,
                    textOf(firstChild(testcase, "system-out")) + "\n" + textOf(firstChild(testcase, "system-err")),
                    history.getOrDefault(testName, ""),
                    null));
        }
        return failures;
    }

    private static List<Path> reportFiles(Path path) {
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }
        try (Stream<Path> files = Files.list(path)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".xml")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list reports in " + path, e);
        }
    }

    private static Document parse(Path file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void fatalError(SAXParseException e) throws SAXParseException {
                    throw e; // fail loudly via the exception, not by printing to stderr
                }
            });
            return builder.parse(file.toFile());
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot parse JUnit report " + file + ": " + e.getMessage(), e);
        }
    }

    private static Map<String, String> readHistory(Path file) {
        try {
            return MAPPER.readValue(file.toFile(), new TypeReference<Map<String, String>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read history file " + file, e);
        }
    }

    private static Element firstChild(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(tag)) {
                return e;
            }
        }
        return null;
    }

    private static String textOf(Element e) {
        return e == null ? "" : e.getTextContent().strip();
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    private static String guessSuite(String className) {
        String c = className.toLowerCase();
        if (c.contains(".web.") || c.endsWith("uitest")) {
            return "ui";
        }
        if (c.endsWith("it")) {
            return "integration";
        }
        return "api";
    }
}
