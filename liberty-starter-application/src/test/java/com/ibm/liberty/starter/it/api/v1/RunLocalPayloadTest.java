package com.ibm.liberty.starter.it.api.v1;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class RunLocalPayloadTest {

    private final static String tempDir = System.getProperty("liberty.temp.dir");
    private static String osName;
    private final String extractedZip = "/extractedZip";
    private final static String installLog = tempDir + "/mvnLog/log.txt";
    private final static String cleanLog = tempDir + "/mvnLog/cleanLog.txt";

    @BeforeClass
    // Check that maven is on the classpath
    public static void assertMavenPathSet() throws IOException {
        System.out.println("RunLocalPayloadTest.assertMavenPathSet @BeforeClass entered");
        osName = System.getProperty("os.name");
        assertTrue(osName != null);
        File file = new File(System.getProperty("liberty.temp.dir"));
        file.mkdir();
        System.out.println("RunLocalPayloadTest.assertMavenPathSet System path is:" + System.getenv("PATH"));
        ProcessBuilder pb = null;
        if (osName.startsWith("Windows")) {
            pb = new ProcessBuilder("cmd", "/c", "mvn", "--version");
        } else {
            System.out.println("RunLocalPayloadTest.assertMavenPathSet os is not windows.");
            pb = new ProcessBuilder("mvn", "--version");
        }
        pb.directory(file);
        Process process = pb.start();
        InputStream is = process.getInputStream();
        String output = inputStreamToString(is);
        is.close();
        System.out.println("Output from env is:" + output);
        assertTrue(output, output.contains("Apache Maven"));
    }

    @Before
    public void downloadZip() throws IOException {
        Client client = ClientBuilder.newClient();
        String port = System.getProperty("liberty.test.port");
        String url = "http://localhost:" + port + "/start/api/v1/data?tech=test&name=TestApp&deploy=local";
        System.out.println("Testing " + url);
        Response response = client.target(url).request("application/zip").get();
        InputStream entityInputStream = response.readEntity(InputStream.class);
        extractZip(entityInputStream);
    }

    @Test
    public void testLocalMvnInstallRuns() throws Exception {
        testMvnInstall();
        testEndpoint();
        testMvnClean();
    }

    @After
    public void shutdownServer() throws IOException, InterruptedException {
        try {
            runMvnClean();
        } catch (AssertionError e) {
            System.out.println("Caught exception during server shutdown, ignoring because server should be shut down by test " + e.getMessage());
        }
    }

    public void testMvnInstall() throws IOException, InterruptedException {
        File logFile = runMvnInstall();
        FileInputStream fis = new FileInputStream(logFile);
        String logs = inputStreamToString(fis);
        assertTrue("testMvnInstall() expected message BUILD SUCCESS in logs located in " + logFile.getAbsolutePath(), logs.contains("BUILD SUCCESS"));
    }

    public void testEndpoint() {
        Client client = ClientBuilder.newClient();
        String url = "http://localhost:9080/myLibertyApp/";
        System.out.println("Testing " + url);
        Response response = client.target(url).request().get();
        int status = response.getStatus();
        assertTrue("Endpoint response status was not 200, found:" + status, status == 200);
        String responseString = response.readEntity(String.class);
        String[] expectedStrings = { "Welcome to your Liberty Application", "Test" };
        assertTrue("Endpoint response incorrect, expected:" + expectedStrings[0] + ", found:" + responseString, responseString.contains(expectedStrings[0]));
        assertTrue("Endpoint response incorrect, expected:" + expectedStrings[1] + ", found:" + responseString, responseString.contains(expectedStrings[1]));
    }

    public void testMvnClean() throws IOException, InterruptedException {
        File logFile = runMvnClean();
        FileInputStream fis = new FileInputStream(logFile);
        String logs = inputStreamToString(fis);
        assertTrue("testMvnClean() expected message BUILD SUCCESS in logs located in " + logFile.getAbsolutePath(), logs.contains("BUILD SUCCESS"));
    }

    private static void extractZip(InputStream entityInputStream) throws IOException {
        // Create a new ZipInputStream from the response InputStream
        ZipInputStream zipIn = new ZipInputStream(entityInputStream);
        String tempDir = System.getProperty("liberty.temp.dir");
        File file = new File(tempDir + "/TestApp.zip");
        System.out.println("Creating zip file: " + file.toString());
        File extractedZip = new File(tempDir + "/extractedZip");
        ZipEntry inputEntry = null;
        while ((inputEntry = zipIn.getNextEntry()) != null) {
            if (inputEntry.isDirectory()) {
                continue;
            }
            String entryName = inputEntry.getName();
            System.out.println("Creating " + entryName);
            File zipFile = new File(extractedZip, entryName);
            zipFile.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(zipFile);
            byte[] bytes = new byte[4096];
            int bytesRead = 0;
            while ((bytesRead = zipIn.read(bytes)) >= 0) {
                fos.write(bytes, 0, bytesRead);
            };
            fos.close();
        }
    }

    private File runMvnInstall() throws IOException, InterruptedException {
        String filePath = tempDir + extractedZip;
        File logFile = new File(installLog);
        logFile.getParentFile().mkdirs();
        logFile.createNewFile();
        System.out.println("mvn output will go to " + installLog);
        File file = new File(filePath);
        ProcessBuilder pb = null;
        if (osName.startsWith("Windows")) {
            pb = new ProcessBuilder("cmd", "/c", "mvn", "install", "--log-file", installLog);
        } else {
            pb = new ProcessBuilder("mvn", "install", "--log-file", installLog);
        }
        pb.directory(file);
        Process process = pb.start();
        process.waitFor();
        int exitValue = process.exitValue();
        System.out.println("Exit value is " + exitValue);
        assertTrue("runMvnInstall() expected return value of 0, instead found:" + exitValue, exitValue == 0);
        return logFile;
    }

    private File runMvnClean() throws IOException, InterruptedException {
        String filePath = tempDir + extractedZip;
        File logFile = new File(cleanLog);
        logFile.getParentFile().mkdirs();
        logFile.createNewFile();
        System.out.println("mvn output will go to " + cleanLog);
        File file = new File(filePath);
        ProcessBuilder pb = null;
        if (osName.startsWith("Windows")) {
            pb = new ProcessBuilder("cmd", "/c", "mvn", "clean", "-P", "stopServer", "--log-file", cleanLog);
        } else {
            pb = new ProcessBuilder("mvn", "clean", "-P", "stopServer", "--log-file", cleanLog);
        }
        pb.directory(file);
        Process process = pb.start();
        process.waitFor();
        int exitValue = process.exitValue();
        System.out.println("Exit value is " + exitValue);
        assertTrue("runMvnClean() expected return value of 0, instead found:" + exitValue, exitValue == 0);
        return logFile;
    }

    private static String inputStreamToString(InputStream inputStream) throws IOException {
        InputStreamReader isr = new InputStreamReader(inputStream);
        char[] chars = new char[1024];
        StringBuilder responseBuilder = new StringBuilder();

        int read;
        while ((read = isr.read(chars)) != -1) {
            responseBuilder.append(chars, 0, read);
        }
        return responseBuilder.toString();
    }

}
