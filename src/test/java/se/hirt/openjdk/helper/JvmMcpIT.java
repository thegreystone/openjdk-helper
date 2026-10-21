/*
 * Copyright (C) 2026 Marcus Hirt
 *
 * This software is free:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESSED OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package se.hirt.openjdk.helper;

import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIfSystemProperty(named = "native.image.path", matches = ".+")
class JvmMcpIT extends PackagedMcpTest {
	@Override
	List<String> command(List<String> properties) {
		String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
		var command = new ArrayList<String>();
		command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
		command.addAll(properties);
		command.add("-jar");
		Properties artifact = new Properties();
		try (var input = Files.newInputStream(Path.of("target", "quarkus-artifact.properties"))) {
			artifact.load(input);
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Cannot locate packaged application metadata", e);
		}
		String artifactPath = artifact.getProperty("path");
		assertTrue(artifactPath != null && artifactPath.endsWith(".jar"),
				"Expected packaged JVM artifact: " + artifact);
		Path jar = Path.of("target").resolve(artifactPath);
		assertTrue(Files.isRegularFile(jar), "Packaged jar is missing: " + jar);
		command.add(jar.toAbsolutePath().toString());
		return command;
	}
}
