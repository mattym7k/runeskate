package com.gielinorskate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

public class TextTest
{
	/** A Text lookup with a literal key, or a literal key prefix followed by "+". */
	private static final Pattern LOOKUP = Pattern.compile(
		"Text\\.(?:get|lines|floats|ints)\\(\\s*\"([^\"]+)\"\\s*(\\+)?");

	private static String sources() throws IOException
	{
		Path root = Paths.get("src", "main", "java");
		try (Stream<Path> files = Files.walk(root))
		{
			List<Path> java = files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
			StringBuilder all = new StringBuilder();
			for (Path p : java)
			{
				all.append(new String(Files.readAllBytes(p), StandardCharsets.UTF_8)).append('\n');
			}
			return all.toString();
		}
	}

	private static Set<String> bundledKeys() throws IOException
	{
		Set<String> keys = new TreeSet<>();
		for (String name : Text.FILES)
		{
			Properties p = new Properties();
			try (Reader r = new InputStreamReader(Text.class.getResourceAsStream("text/" + name + ".properties"),
				StandardCharsets.UTF_8))
			{
				p.load(r);
			}
			for (String k : p.stringPropertyNames())
			{
				assertTrue("key " + k + " is in more than one file", keys.add(k));
			}
		}
		return keys;
	}

	@Test
	public void everyLiteralKeyTheCodeLooksUpIsBundled() throws IOException
	{
		Set<String> keys = bundledKeys();
		Matcher m = LOOKUP.matcher(sources());
		List<String> missing = new ArrayList<>();
		while (m.find())
		{
			String key = m.group(1);
			boolean found = m.group(2) == null ? keys.contains(key) : keys.stream().anyMatch(k -> k.startsWith(key));
			if (!found)
			{
				missing.add(key);
			}
		}
		assertEquals("keys looked up but not bundled", List.of(), missing);
	}

	@Test
	public void everyBundledKeyIsLookedUp() throws IOException
	{
		Set<String> literal = new TreeSet<>();
		List<String> prefixes = new ArrayList<>();
		Matcher m = LOOKUP.matcher(sources());
		while (m.find())
		{
			(m.group(2) == null ? literal : prefixes).add(m.group(1));
		}
		List<String> unused = bundledKeys().stream()
			.filter(k -> !literal.contains(k) && prefixes.stream().noneMatch(k::startsWith))
			.collect(Collectors.toList());
		assertEquals("bundled keys nothing looks up", List.of(), unused);
	}

	@Test
	public void everyBundledValueLoads() throws IOException
	{
		for (String k : bundledKeys())
		{
			assertTrue(k, Text.has(k));
			assertTrue(k, !Text.get(k).isEmpty());
		}
	}
}
