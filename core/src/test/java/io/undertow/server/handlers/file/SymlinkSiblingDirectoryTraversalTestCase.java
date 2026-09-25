/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.server.handlers.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.undertow.server.handlers.CanonicalPathHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test case to demonstrate CVE: PathResourceManager safePaths check allows sibling-directory symlink traversal
 *
 * The vulnerability: isSymlinkSafe() uses canonicalPath.startsWith(safePath) without verifying path-segment boundaries.
 * This allows a symlink pointing to a sibling directory whose name begins with the same string as a safe path
 * to be incorrectly accepted.
 *
 * Example:
 * - Safe path: /app/data
 * - Attacker symlink points to: /app/data_private
 * - startsWith check incorrectly returns true, allowing unauthorized access
 *
 * @author JBEAP-34857
 */
@RunWith(DefaultServer.class)
public class SymlinkSiblingDirectoryTraversalTestCase {

    private static final Logger log = Logger.getLogger(SymlinkSiblingDirectoryTraversalTestCase.class);

    private Path testRoot;
    private Path safeDir;
    private Path privateDir;
    private Path maliciousSymlink;

    @Before
    public void createSiblingDirectoryScenario() throws IOException {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().contains("windows"));

        /**
         * Creating following structure to demonstrate the vulnerability in a temporary directory:
         *
         * $TEMP_ROOT/
         * ├── data/
         * │   └── safe.txt              (safe file - should be accessible)
         * ├── data_private/
         * │   └── secret.txt            (private file - should NOT be accessible)
         * └── malicious_link -> data_private  (symlink to sibling directory)
         *
         * Configure safePaths with: $TEMP_ROOT/data
         *
         * The bug: safePath.startsWith("/tmp/.../data") matches "/tmp/.../data_private"
         * Expected: symlink to data_private should be DENIED
         * Actual (vulnerable): symlink to data_private is ALLOWED
         */
        // Create a temporary directory for the entire test structure
        testRoot = Files.createTempDirectory("undertow-symlink-test-");

        // Create safe directory with a safe file
        safeDir = testRoot.resolve("data");
        Files.createDirectories(safeDir);
        Path safeFile = safeDir.resolve("safe.txt");
        Files.write(safeFile, "This is a safe file".getBytes());

        // Create private sibling directory with secret file
        privateDir = testRoot.resolve("data_private");
        Files.createDirectories(privateDir);
        Path secretFile = privateDir.resolve("secret.txt");
        Files.write(secretFile, "CONFIDENTIAL: This should not be accessible!".getBytes());

        // Create malicious symlink pointing to the private directory
        maliciousSymlink = testRoot.resolve("malicious_link");
        Files.createSymbolicLink(maliciousSymlink, privateDir);

        log.infof("=== Test Setup ===");
        log.infof("Test root: %s", testRoot.toAbsolutePath());
        log.infof("Safe dir: %s", safeDir.toAbsolutePath());
        log.infof("Private dir: %s", privateDir.toAbsolutePath());
        log.infof("Malicious symlink: %s -> %s", maliciousSymlink.toAbsolutePath(), privateDir.toAbsolutePath());
    }

    @After
    public void cleanupTestStructure() throws IOException {
        if (testRoot != null && Files.exists(testRoot)) {
            // Delete entire temporary directory tree
            deleteRecursively(testRoot);
        }
    }

    /**
     * Recursively delete a directory tree.
     */
    private void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        Files.walk(directory)
            .sorted((a, b) -> b.compareTo(a)) // Delete children before parents (reverse order)
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    log.errorf(e, "Failed to delete: %s", path);
                }
            });
    }

    @Test
    public void testSafeDirectoryAccessAllowed() throws IOException {
        TestHttpClient client = new TestHttpClient();

        try {
            // Configure PathResourceManager with safePaths pointing to the "data" directory
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/files", new ResourceHandler(
                                    new PathResourceManager(testRoot, 10485760, true, safeDir.toAbsolutePath().toString()))
                                    .setDirectoryListingEnabled(false))));

            /**
             * This request should return 200 - accessing safe.txt through the safe directory
             */
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/files/data/safe.txt");
            HttpResponse result = client.execute(get);
            Assert.assertEquals("Safe file should be accessible", StatusCodes.OK, result.getStatusLine().getStatusCode());

            String response = HttpClientUtils.readResponse(result);
            Assert.assertTrue("Should read safe file content", response.contains("This is a safe file"));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testDirectAccessToPrivateDirectoryDenied() throws IOException {
        TestHttpClient client = new TestHttpClient();

        try {
            // Configure PathResourceManager with base=safeDir (data directory only)
            // This means data_private is outside the base path and should be inaccessible
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/files", new ResourceHandler(
                                    new PathResourceManager(safeDir, 10485760))
                                    .setDirectoryListingEnabled(false))));

            /**
             * This request should return 404 - trying to access ../data_private from base=data
             * The path normalization check should prevent this directory traversal
             */
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/files/../data_private/secret.txt");
            HttpResponse result = client.execute(get);
            Assert.assertEquals("Direct access to private directory should be denied",
                    StatusCodes.NOT_FOUND, result.getStatusLine().getStatusCode());

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testSymlinkToSiblingDirectoryShouldBeDenied() throws IOException {
        TestHttpClient client = new TestHttpClient();

        try {
            // Configure PathResourceManager with safePaths pointing to the "data" directory
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/files", new ResourceHandler(
                                    new PathResourceManager(testRoot, 10485760, true, safeDir.toAbsolutePath().toString()))
                                    .setDirectoryListingEnabled(false))));

            /**
             * VULNERABILITY TEST:
             * This request should return 404 - the symlink points to data_private, which is NOT in safePaths.
             *
             * BUG: The isSymlinkSafe() method uses canonicalPath.startsWith(safePath)
             * - safePath = "/path/to/data"
             * - canonicalPath = "/path/to/data_private/secret.txt"
             * - startsWith returns TRUE (incorrect!)
             *
             * Expected behavior: Should return 404 (access denied)
             * Actual behavior (vulnerable): Returns 200 with secret file content
             */
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/files/malicious_link/secret.txt");
            HttpResponse result = client.execute(get);

            log.infof("=== Vulnerability Test Result ===");
            log.infof("HTTP Status: %d", result.getStatusLine().getStatusCode());
            log.infof("Expected: 404 (NOT_FOUND)");

            if (result.getStatusLine().getStatusCode() == StatusCodes.OK) {
                String response = HttpClientUtils.readResponse(result);
                log.errorf("VULNERABLE: Secret file was accessible!");
                log.errorf("Content: %s", response);

                // This assertion SHOULD pass when the vulnerability is fixed
                Assert.fail("SECURITY VULNERABILITY: Symlink to sibling directory '" + privateDir.getFileName() +
                        "' was incorrectly allowed when only '" + safeDir.getFileName() +
                        "' is in safePaths. Secret file content leaked: " + response);
            } else {
                log.infof("SECURE: Access correctly denied");
            }

            Assert.assertEquals("Symlink to sibling directory should be denied",
                    StatusCodes.NOT_FOUND, result.getStatusLine().getStatusCode());

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testSymlinkToExactSafePathAllowed() throws IOException {
        TestHttpClient client = new TestHttpClient();

        // Create a symlink that points to the exact safe directory (not a sibling)
        Path legitimateSymlink = testRoot.resolve("legitimate_link");
        Files.createSymbolicLink(legitimateSymlink, safeDir);

        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/files", new ResourceHandler(
                                    new PathResourceManager(testRoot, 10485760, true, safeDir.toAbsolutePath().toString()))
                                    .setDirectoryListingEnabled(false))));

            /**
             * This should return 200 - symlink points to the safe directory itself
             */
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/files/legitimate_link/safe.txt");
            HttpResponse result = client.execute(get);
            Assert.assertEquals("Symlink to safe directory should be allowed",
                    StatusCodes.OK, result.getStatusLine().getStatusCode());

            String response = HttpClientUtils.readResponse(result);
            Assert.assertTrue("Should read safe file content", response.contains("This is a safe file"));

        } finally {
            // No need to manually delete - cleanup will remove entire testRoot
            client.getConnectionManager().shutdown();
        }
    }
}
