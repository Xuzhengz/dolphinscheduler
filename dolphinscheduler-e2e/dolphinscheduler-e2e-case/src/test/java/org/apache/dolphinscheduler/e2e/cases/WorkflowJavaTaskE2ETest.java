/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.e2e.cases;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.dolphinscheduler.e2e.core.Constants;
import org.apache.dolphinscheduler.e2e.core.DolphinScheduler;
import org.apache.dolphinscheduler.e2e.core.WebDriverWaitFactory;
import org.apache.dolphinscheduler.e2e.pages.LoginPage;
import org.apache.dolphinscheduler.e2e.pages.common.NavBarPage;
import org.apache.dolphinscheduler.e2e.pages.project.ProjectDetailPage;
import org.apache.dolphinscheduler.e2e.pages.project.ProjectPage;
import org.apache.dolphinscheduler.e2e.pages.project.workflow.WorkflowDefinitionTab;
import org.apache.dolphinscheduler.e2e.pages.project.workflow.WorkflowForm;
import org.apache.dolphinscheduler.e2e.pages.project.workflow.WorkflowInstanceTab;
import org.apache.dolphinscheduler.e2e.pages.project.workflow.task.JavaTaskForm;
import org.apache.dolphinscheduler.e2e.pages.resource.FileManagePage;
import org.apache.dolphinscheduler.e2e.pages.resource.ResourcePage;
import org.apache.dolphinscheduler.e2e.pages.security.EnvironmentPage;
import org.apache.dolphinscheduler.e2e.pages.security.SecurityPage;
import org.apache.dolphinscheduler.e2e.pages.security.TenantPage;
import org.apache.dolphinscheduler.e2e.pages.security.UserPage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DisableIfTestFails;
import org.openqa.selenium.By;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@DolphinScheduler(composeFiles = "docker/basic/docker-compose.yaml")
@DisableIfTestFails
@Slf4j
public class WorkflowJavaTaskE2ETest {

    private static final String project = "test-workflow";

    private static final String workflow = "test-workflow-1";

    private static final String workflow2 = "test-workflow-2";

    private static final String user = "admin";

    private static final String password = "dolphinscheduler123";

    private static final String email = "admin@gmail.com";

    private static final String phone = "15800000000";

    private static final String tenant = System.getProperty("user.name");

    private static final String environmentName = "JAVA_HOME";

    private static final String environmentConfig = "export JAVA_HOME=${JAVA_HOME:-/opt/java/openjdk}";

    private static final String environmentDesc = "JAVA_HOME_DESC";

    private static final String environmentWorkerGroup = "default";

    private static final String filePath = Constants.HOST_TMP_PATH.toString();

    private static RemoteWebDriver browser;

    private static void createJar(String className, String classFilePath, String entryName, String mainPackage,
                                  String jarName) {

        String jarFilePath = Constants.HOST_TMP_PATH + "/" + jarName;

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainPackage);

        try (
                FileOutputStream fos = new FileOutputStream(jarFilePath);
                JarOutputStream jos = new JarOutputStream(fos, manifest)) {
            Path path = new File(classFilePath + className).toPath();
            JarEntry entry = new JarEntry(entryName);
            jos.putNextEntry(entry);
            byte[] bytes = Files.readAllBytes(path);
            jos.write(bytes, 0, bytes.length);
            jos.closeEntry();
        } catch (IOException e) {
            throw new RuntimeException("Create jar failed:", e);
        }

    }

    private static void createAndBuildJars() {
        String classPath = Constants.HOST_TMP_PATH + "/";
        compileJavaFile(Arrays.asList("docker/java-task/Fat.java"));
        compileJavaFile(Arrays.asList("docker/java-task/Normal1.java", "docker/java-task/Normal2.java"));
        compileJavaFile(Arrays.asList("docker/java-task/Normal2.java"));
        createJar("Fat.class", classPath,
                "Fat.class",
                "Fat",
                "fat.jar");
        createJar("Normal1.class", classPath,
                "Normal1.class",
                "Normal1",
                "normal1.jar");
        createJar("Normal2.class", classPath,
                "Normal2.class",
                "Normal2",
                "normal2.jar");

    }

    public static void compileJavaFile(List<String> sourceFilePaths) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            log.error("Cannot find the system Java compiler.", new IllegalStateException());
            return;
        }

        String outputDirPath = Constants.HOST_TMP_PATH.toString();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<File> sourceFiles = new ArrayList<>();
            for (String sourceFilePath : sourceFilePaths) {
                URL resourceUrl = Thread.currentThread().getContextClassLoader().getResource(sourceFilePath);
                if (resourceUrl == null) {
                    log.error("Java file not found: " + sourceFilePath, new IllegalArgumentException());
                    continue;
                }

                File resourceFile = new File(resourceUrl.toURI());
                if (!resourceFile.exists()) {
                    log.error("Java file does not exist: " + resourceFile.getAbsolutePath(),
                            new IllegalArgumentException());
                    continue;
                }
                sourceFiles.add(resourceFile);
            }

            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(sourceFiles);
            List<String> options = Arrays.asList("--release", "8", "-d", outputDirPath);

            JavaCompiler.CompilationTask task =
                    compiler.getTask(null, fileManager, null, options, null, compilationUnits);
            boolean success = task.call();

            if (!success) {
                throw new RuntimeException("Java compilation failed.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Compile java files failed:", e);
        }
    }

    @BeforeAll
    public static void setup() {
        createAndBuildJars();
        UserPage userPage = new LoginPage(browser)
                .login(user, password)
                .goToNav(SecurityPage.class)
                .goToTab(TenantPage.class)
                .create(tenant)
                .goToNav(SecurityPage.class)
                .goToTab(EnvironmentPage.class)
                .create(environmentName, environmentConfig, environmentDesc, environmentWorkerGroup)
                .goToNav(SecurityPage.class)
                .goToTab(UserPage.class);

        WebDriverWaitFactory.createWebDriverWait(userPage.driver())
                .until(ExpectedConditions.visibilityOfElementLocated(new By.ByClassName("name")));

        userPage.update(user, user, email, phone, tenant)
                .goToNav(ProjectPage.class)
                .create(project);

        ProjectPage projectPage = new ProjectPage(browser);
        Awaitility.await().untilAsserted(() -> assertThat(projectPage.projectList())
                .as("The project list should include newly created projects.")
                .anyMatch(it -> it.getText().contains(project)));
    }

    @AfterAll
    public static void cleanup() {
        new NavBarPage(browser)
                .goToNav(ProjectPage.class)
                .goTo(project)
                .goToTab(WorkflowDefinitionTab.class)
                .delete(workflow);

        new NavBarPage(browser)
                .goToNav(ProjectPage.class)
                .delete(project);

        browser.navigate().refresh();

        new NavBarPage(browser)
                .goToNav(SecurityPage.class)
                .goToTab(TenantPage.class)
                .delete(tenant);
    }

    @Test
    @Order(1)
    void testCreateFatJarWorkflow() {
        FileManagePage file = new NavBarPage(browser)
                .goToNav(ResourcePage.class)
                .goToTab(FileManagePage.class)
                .uploadFile(filePath + "/fat.jar");

        WebDriverWait wait = WebDriverWaitFactory.createWebDriverWait(browser);

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//span[text()='fat.jar']")));

        ProjectPage projectPage = new NavBarPage(browser)
                .goToNav(ProjectPage.class);

        wait.until(ExpectedConditions.visibilityOfAllElements(projectPage.projectList()));

        WorkflowDefinitionTab workflowDefinitionPage = projectPage
                .goTo(project)
                .goToTab(WorkflowDefinitionTab.class);

        WorkflowForm workflow1 = workflowDefinitionPage.createWorkflow();
        workflow1.<JavaTaskForm>addTask(WorkflowForm.TaskType.JAVA)
                .selectRunType("FAT_JAR")
                .selectMainPackage("fat.jar")
                .selectJavaResource("fat.jar")
                .name("test-1")
                .selectEnv(environmentName)
                .submit()
                .submit()
                .name(workflow)
                .submit();

        Awaitility.await().untilAsserted(() -> assertThat(workflowDefinitionPage.workflowList())
                .as("Workflow list should contain newly-created workflow")
                .anyMatch(it -> it.getText().contains(workflow)));

        workflowDefinitionPage.publish(workflow);
    }

    @Test
    @Order(30)
    void testRunFatJarWorkflow() {
        final ProjectDetailPage projectPage =
                new ProjectPage(browser)
                        .goToNav(ProjectPage.class)
                        .goTo(project);

        projectPage
                .goToTab(WorkflowInstanceTab.class)
                .deleteAll();
        projectPage
                .goToTab(WorkflowDefinitionTab.class)
                .run(workflow)
                .submit();

        Awaitility.await()
                .atMost(Duration.ofMinutes(2))
                .untilAsserted(() -> {
                    browser.navigate().refresh();

                    final WorkflowInstanceTab.Row row = projectPage
                            .goToTab(WorkflowInstanceTab.class)
                            .instances()
                            .iterator()
                            .next();

                    assertThat(row.isSuccess()).isTrue();
                    assertThat(row.executionTime()).isEqualTo(1);
                });

    }

    @Test
    @Order(60)
    void testCreateNormalJarWorkflow() {
        FileManagePage file = new NavBarPage(browser)
                .goToNav(ResourcePage.class)
                .goToTab(FileManagePage.class)
                .delete("fat.jar")
                .uploadFile(filePath + "/normal2.jar");

        WebDriverWait wait = WebDriverWaitFactory.createWebDriverWait(browser);

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//span[text()='normal2.jar']")));

        file.uploadFile(filePath + "/normal1.jar");

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//span[text()='normal1.jar']")));

        ProjectPage projectPage = new NavBarPage(browser)
                .goToNav(ProjectPage.class);

        wait.until(ExpectedConditions.visibilityOfAllElements(projectPage.projectList()));

        WorkflowDefinitionTab workflowDefinitionPage = projectPage
                .goTo(project)
                .goToTab(WorkflowDefinitionTab.class);

        workflowDefinitionPage.createWorkflow()
                .<JavaTaskForm>addTask(WorkflowForm.TaskType.JAVA)
                .selectRunType("NORMAL_JAR")
                .selectMainPackage("normal1.jar")
                .selectJavaResource("normal1.jar")
                .selectJavaResource("normal2.jar")
                .name("test-2")
                .selectEnv(environmentName)
                .submit()
                .submit()
                .name(workflow2)
                .submit();

        Awaitility.await().untilAsserted(() -> assertThat(workflowDefinitionPage.workflowList())
                .as("Workflow list should contain newly-created workflow")
                .anyMatch(it -> it.getText().contains(workflow2)));

        workflowDefinitionPage.publish(workflow2);
    }

    @Test
    @Order(90)
    void testRunNormalJarWorkflow() {
        final ProjectDetailPage projectPage =
                new ProjectPage(browser)
                        .goToNav(ProjectPage.class)
                        .goTo(project);

        projectPage
                .goToTab(WorkflowInstanceTab.class)
                .deleteAll();
        projectPage
                .goToTab(WorkflowDefinitionTab.class)
                .run(workflow2)
                .submit();

        Awaitility.await()
                .atMost(Duration.ofMinutes(2))
                .untilAsserted(() -> {
                    browser.navigate().refresh();

                    final WorkflowInstanceTab.Row row = projectPage
                            .goToTab(WorkflowInstanceTab.class)
                            .instances()
                            .iterator()
                            .next();

                    assertThat(row.isSuccess()).isTrue();
                    assertThat(row.executionTime()).isEqualTo(1);
                });

    }
}
