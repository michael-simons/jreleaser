/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2020-2021 The JReleaser authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jreleaser.tools;

import org.jreleaser.bundle.RB;
import org.jreleaser.model.Artifact;
import org.jreleaser.model.Distribution;
import org.jreleaser.model.JReleaserContext;
import org.jreleaser.model.Tool;
import org.jreleaser.model.tool.spi.ToolProcessingException;
import org.jreleaser.model.tool.spi.ToolProcessor;
import org.jreleaser.util.Algorithm;
import org.jreleaser.util.FileUtils;
import org.jreleaser.util.StringUtils;
import org.jreleaser.util.command.Command;
import org.jreleaser.util.command.CommandException;
import org.jreleaser.util.command.CommandExecutor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.jreleaser.util.Constants.KEY_ARTIFACT_ARCHIVE_FORMAT;
import static org.jreleaser.util.Constants.KEY_ARTIFACT_FILE_NAME;
import static org.jreleaser.util.Constants.KEY_ARTIFACT_NAME;
import static org.jreleaser.util.Constants.KEY_ARTIFACT_PLATFORM;
import static org.jreleaser.util.Constants.KEY_ARTIFACT_PLATFORM_REPLACED;
import static org.jreleaser.util.Constants.KEY_ARTIFACT_SIZE;
import static org.jreleaser.util.Constants.KEY_DISTRIBUTION_ARTIFACT;
import static org.jreleaser.util.Constants.KEY_DISTRIBUTION_ARTIFACT_ARCHIVE_FORMAT;
import static org.jreleaser.util.Constants.KEY_DISTRIBUTION_ARTIFACT_FILE_NAME;
import static org.jreleaser.util.Constants.KEY_DISTRIBUTION_ARTIFACT_NAME;
import static org.jreleaser.util.Constants.KEY_DISTRIBUTION_ARTIFACT_PLATFORM;
import static org.jreleaser.util.Constants.KEY_DISTRIBUTION_ARTIFACT_PLATFORM_REPLACED;
import static org.jreleaser.util.Constants.KEY_DISTRIBUTION_ARTIFACT_SIZE;
import static org.jreleaser.util.Constants.KEY_DISTRIBUTION_PACKAGE_DIRECTORY;
import static org.jreleaser.util.Constants.KEY_DISTRIBUTION_PREPARE_DIRECTORY;
import static org.jreleaser.util.Constants.KEY_DISTRIBUTION_SHA_256;
import static org.jreleaser.util.Constants.KEY_DISTRIBUTION_SIZE;
import static org.jreleaser.util.Constants.KEY_DISTRIBUTION_URL;
import static org.jreleaser.util.Constants.KEY_REVERSE_REPO_HOST;
import static org.jreleaser.util.MustacheUtils.applyTemplate;
import static org.jreleaser.util.MustacheUtils.applyTemplates;
import static org.jreleaser.util.StringUtils.capitalize;
import static org.jreleaser.util.StringUtils.getFilename;
import static org.jreleaser.util.StringUtils.isBlank;
import static org.jreleaser.util.StringUtils.isNotBlank;

/**
 * @author Andres Almiray
 * @since 0.1.0
 */
abstract class AbstractToolProcessor<T extends Tool> implements ToolProcessor<T> {
    protected final JReleaserContext context;
    protected T tool;

    protected AbstractToolProcessor(JReleaserContext context) {
        this.context = context;
    }

    @Override
    public T getTool() {
        return tool;
    }

    @Override
    public void setTool(T tool) {
        this.tool = tool;
    }

    @Override
    public String getToolName() {
        return tool.getName();
    }

    @Override
    public boolean supportsDistribution(Distribution distribution) {
        return true;
    }

    @Override
    public void prepareDistribution(Distribution distribution, Map<String, Object> props) throws ToolProcessingException {
        try {
            String distributionName = distribution.getName();
            context.getLogger().debug(RB.$("tool.create.properties"), distributionName, getToolName());
            Map<String, Object> newProps = fillProps(distribution, props);
            if (newProps.isEmpty()) {
                context.getLogger().warn(RB.$("tool.skip.distribution"), distributionName);
                return;
            }

            doPrepareDistribution(distribution, newProps);
        } catch (RuntimeException e) {
            throw new ToolProcessingException(e);
        }
    }

    protected abstract void doPrepareDistribution(Distribution distribution, Map<String, Object> props) throws ToolProcessingException;

    @Override
    public void packageDistribution(Distribution distribution, Map<String, Object> props) throws ToolProcessingException {
        try {
            String distributionName = distribution.getName();
            context.getLogger().debug(RB.$("tool.create.properties"), distributionName, getToolName());
            Map<String, Object> newProps = fillProps(distribution, props);
            if (newProps.isEmpty()) {
                context.getLogger().warn(RB.$("tool.skip.distribution"), distributionName);
                return;
            }

            doPackageDistribution(distribution, newProps);
        } catch (IllegalArgumentException e) {
            throw new ToolProcessingException(e);
        }
    }

    @Override
    public void publishDistribution(Distribution distribution, Map<String, Object> props) throws ToolProcessingException {
        if (context.getModel().getProject().isSnapshot() && !tool.isSnapshotSupported()) {
            context.getLogger().info(RB.$("tool.publish.snapshot.not.supported"));
            return;
        }

        try {
            String distributionName = distribution.getName();
            context.getLogger().debug(RB.$("tool.create.properties"), distributionName, getToolName());
            Map<String, Object> newProps = fillProps(distribution, props);
            if (newProps.isEmpty()) {
                context.getLogger().warn(RB.$("tool.skip.distribution"), distributionName);
                return;
            }

            doPublishDistribution(distribution, newProps);
        } catch (IllegalArgumentException e) {
            throw new ToolProcessingException(e);
        }
    }

    protected abstract void doPackageDistribution(Distribution distribution, Map<String, Object> props) throws ToolProcessingException;

    protected abstract void doPublishDistribution(Distribution distribution, Map<String, Object> props) throws ToolProcessingException;

    protected Map<String, Object> fillProps(Distribution distribution, Map<String, Object> props) throws ToolProcessingException {
        Map<String, Object> newProps = new LinkedHashMap<>(props);
        context.getLogger().debug(RB.$("tool.fill.distribution.properties"));
        fillDistributionProperties(newProps, distribution);
        context.getLogger().debug(RB.$("tool.fill.git.properties"));
        context.getModel().getRelease().getGitService().fillProps(newProps, context.getModel());
        context.getLogger().debug(RB.$("tool.fill.artifact.properties"));
        if (!verifyAndAddArtifacts(newProps, distribution)) {
            // we can't continue with this tool
            return Collections.emptyMap();
        }
        context.getLogger().debug(RB.$("tool.fill.tool.properties"));
        fillToolProperties(newProps, distribution);
        applyTemplates(newProps, tool.getResolvedExtraProperties());
        if (isBlank(context.getModel().getRelease().getGitService().getReverseRepoHost())) {
            newProps.put(KEY_REVERSE_REPO_HOST,
                tool.getExtraProperties().get(KEY_REVERSE_REPO_HOST));
        }
        return newProps;
    }

    protected void fillDistributionProperties(Map<String, Object> props, Distribution distribution) {
        props.putAll(distribution.props());
    }

    protected abstract void fillToolProperties(Map<String, Object> props, Distribution distribution) throws ToolProcessingException;

    protected void executeCommand(Path directory, Command command) throws ToolProcessingException {
        try {
            int exitValue = new CommandExecutor(context.getLogger())
                .executeCommand(directory, command);
            if (exitValue != 0) {
                throw new CommandException(RB.$("ERROR_command_execution_exit_value", exitValue));
            }
        } catch (CommandException e) {
            throw new ToolProcessingException(RB.$("ERROR_unexpected_error"), e);
        }
    }

    protected void executeCommand(Command command) throws ToolProcessingException {
        try {
            int exitValue = new CommandExecutor(context.getLogger())
                .executeCommand(command);
            if (exitValue != 0) {
                throw new CommandException(RB.$("ERROR_command_execution_exit_value", exitValue));
            }
        } catch (CommandException e) {
            throw new ToolProcessingException(RB.$("ERROR_unexpected_error"), e);
        }
    }

    protected void executeCommandCapturing(Command command, OutputStream out) throws ToolProcessingException {
        try {
            int exitValue = new CommandExecutor(context.getLogger())
                .executeCommandCapturing(command, out);
            if (exitValue != 0) {
                context.getLogger().error(out.toString().trim());
                throw new CommandException(RB.$("ERROR_command_execution_exit_value", exitValue));
            }
        } catch (CommandException e) {
            throw new ToolProcessingException(RB.$("ERROR_unexpected_error"), e);
        }
    }

    protected void executeCommandWithInput(Command command, InputStream in) throws ToolProcessingException {
        try {
            int exitValue = new CommandExecutor(context.getLogger())
                .executeCommandWithInput(command, in);
            if (exitValue != 0) {
                throw new CommandException(RB.$("ERROR_command_execution_exit_value", exitValue));
            }
        } catch (CommandException e) {
            throw new ToolProcessingException(RB.$("ERROR_unexpected_error"), e);
        }
    }

    protected void copyPreparedFiles(Distribution distribution, Map<String, Object> props) throws ToolProcessingException {
        Path packageDirectory = getPackageDirectory(props);
        copyPreparedFiles(distribution, props, packageDirectory);
    }

    protected void copyPreparedFiles(Distribution distribution, Map<String, Object> props, Path outputDirectory) throws ToolProcessingException {
        Path prepareDirectory = getPrepareDirectory(props);
        try {
            if (!FileUtils.copyFilesRecursive(context.getLogger(), prepareDirectory, outputDirectory)) {
                throw new ToolProcessingException(RB.$("ERROR_copy_files_from_to",
                    context.relativizeToBasedir(prepareDirectory),
                    context.relativizeToBasedir(outputDirectory)));
            }
        } catch (IOException e) {
            throw new ToolProcessingException(RB.$("ERROR_unexpected_copy_files_from_to",
                context.relativizeToBasedir(prepareDirectory),
                context.relativizeToBasedir(outputDirectory)), e);
        }
    }

    protected boolean verifyAndAddArtifacts(Map<String, Object> props,
                                            Distribution distribution) throws ToolProcessingException {
        return verifyAndAddArtifacts(props, distribution, collectArtifacts(distribution));
    }

    protected boolean verifyAndAddArtifacts(Map<String, Object> props,
                                            Distribution distribution,
                                            List<Artifact> artifacts) throws ToolProcessingException {
        List<Artifact> activeArtifacts = artifacts.stream()
            .filter(Artifact::isActive)
            .collect(Collectors.toList());

        if (activeArtifacts.size() == 0) {
            // we can't proceed
            context.getLogger().warn(RB.$("tool.no.matching.artifacts"),
                distribution.getName(), capitalize(tool.getName()));
            return false;
        }

        for (int i = 0; i < activeArtifacts.size(); i++) {
            Artifact artifact = activeArtifacts.get(i);
            String platform = artifact.getPlatform();
            String artifactPlatform = isNotBlank(platform) ? capitalize(platform) : "";
            String platformReplaced = distribution.getPlatform().applyReplacements(platform);
            String artifactPlatformReplaced = isNotBlank(platformReplaced) ? capitalize(platformReplaced) : "";
            // add extra properties without clobbering existing keys
            Map<String, Object> artifactProps = artifact.getResolvedExtraProperties("artifact" + artifactPlatform);
            artifactProps.keySet().stream()
                .filter(k -> !props.containsKey(k))
                .forEach(k -> props.put(k, artifactProps.get(k)));

            Path artifactPath = artifact.getEffectivePath(context);

            long artifactSize = 0;
            try {
                artifactSize = Files.size(artifactPath);
            } catch (IOException ignored) {
                // this would be strange
                context.getLogger().trace(ignored);
            }

            String artifactFileName = artifactPath.getFileName().toString();
            String artifactName = getFilename(artifactFileName, tool.getSupportedExtensions());
            String archiveFormat = artifactFileName.substring(artifactName.length() + 1);
            props.put("artifact" + artifactPlatform + "Size", artifactSize);
            props.put("artifact" + artifactPlatform + "Name", artifactName);
            props.put("artifact" + artifactPlatform + "FileName", archiveFormat);
            props.put("artifact" + artifactPlatform + "ArchiveFormat", artifactFileName);
            props.put("artifact" + artifactPlatformReplaced + "Size", artifactSize);
            props.put("artifact" + artifactPlatformReplaced + "Name", artifactName);
            props.put("artifact" + artifactPlatformReplaced + "FileName", artifactFileName);
            props.put("artifact" + artifactPlatformReplaced + "ArchiveFormat", archiveFormat);
            for (Algorithm algorithm : context.getModel().getChecksum().getAlgorithms()) {
                props.put("artifact" + artifactPlatform + "Checksum" + capitalize(algorithm.formatted()), artifact.getHash(algorithm));
                props.put("artifact" + artifactPlatformReplaced + "Checksum" + capitalize(algorithm.formatted()), artifact.getHash(algorithm));
            }
            Map<String, Object> newProps = new LinkedHashMap<>(props);
            newProps.put(KEY_ARTIFACT_FILE_NAME, artifactFileName);
            newProps.put(KEY_ARTIFACT_NAME, artifactName);
            newProps.put(KEY_ARTIFACT_ARCHIVE_FORMAT, archiveFormat);
            String artifactUrl = applyTemplate(context.getModel().getRelease().getGitService().getDownloadUrl(), newProps);
            props.put("artifact" + artifactPlatform + "Url", artifactUrl);
            props.put("artifact" + artifactPlatformReplaced + "Url", artifactUrl);
            props.putAll(context.getModel().getUpload()
                .resolveDownloadUrls(context, distribution, artifact, "artifact" + artifactPlatform));
            props.putAll(context.getModel().getUpload()
                .resolveDownloadUrls(context, distribution, artifact, "artifact" + artifactPlatformReplaced));

            if (0 == i) {
                props.putAll(context.getModel().getUpload()
                    .resolveDownloadUrls(context, distribution, artifact, "distribution"));
                props.put(KEY_DISTRIBUTION_ARTIFACT, artifact);
                props.put(KEY_DISTRIBUTION_URL, artifactUrl);
                props.put(KEY_DISTRIBUTION_SIZE, artifactSize);
                props.put(KEY_DISTRIBUTION_SHA_256, artifact.getHash(Algorithm.SHA_256));
                for (Algorithm algorithm : context.getModel().getChecksum().getAlgorithms()) {
                    props.put("distributionChecksum" + capitalize(algorithm.formatted()), artifact.getHash(algorithm));
                }
                props.put(KEY_DISTRIBUTION_ARTIFACT_FILE_NAME, artifactFileName);
                props.put(KEY_DISTRIBUTION_ARTIFACT_NAME, artifactName);
                props.put(KEY_DISTRIBUTION_ARTIFACT_SIZE, artifactSize);
                props.put(KEY_DISTRIBUTION_ARTIFACT_ARCHIVE_FORMAT, archiveFormat);
                if (isNotBlank(platform)) props.put(KEY_DISTRIBUTION_ARTIFACT_PLATFORM, platform);
                if (isNotBlank(platform)) props.put(KEY_DISTRIBUTION_ARTIFACT_PLATFORM_REPLACED, platformReplaced);
                props.put(KEY_ARTIFACT_FILE_NAME, artifactFileName);
                props.put(KEY_ARTIFACT_NAME, artifactName);
                props.put(KEY_ARTIFACT_SIZE, artifactSize);
                props.put(KEY_ARTIFACT_ARCHIVE_FORMAT, archiveFormat);
                if (isNotBlank(platform)) props.put(KEY_ARTIFACT_PLATFORM, platform);
                if (isNotBlank(platform)) props.put(KEY_ARTIFACT_PLATFORM_REPLACED, platformReplaced);
                // add extra properties without clobbering existing keys
                Map<String, Object> aprops = artifact.getResolvedExtraProperties();
                applyTemplates(aprops, aprops);
                aprops.keySet().stream()
                    .filter(k -> !props.containsKey(k))
                    .forEach(k -> props.put(k, aprops.get(k)));
            }
        }

        return true;
    }

    protected List<Artifact> collectArtifacts(Distribution distribution) {
        List<String> fileExtensions = new ArrayList<>(tool.getSupportedExtensions());

        return distribution.getArtifacts().stream()
            .filter(Artifact::isActive)
            .filter(artifact -> fileExtensions.stream().anyMatch(ext -> artifact.getPath().endsWith(ext)))
            .filter(artifact -> tool.supportsPlatform(artifact.getPlatform()))
            .filter(artifact -> !isSkipped(artifact))
            // sort by platform, then by extension
            .sorted(Artifact.comparatorByPlatform().thenComparingInt(artifact -> {
                String ext = "." + StringUtils.getFilenameExtension(artifact.getPath());
                return fileExtensions.indexOf(ext);
            }))
            .collect(Collectors.toList());
    }

    protected boolean isSkipped(Artifact artifact) {
        return false;
    }

    protected void info(ByteArrayOutputStream out) {
        log(out, context.getLogger()::info);
    }

    protected void error(ByteArrayOutputStream err) {
        log(err, context.getLogger()::error);
    }

    private void log(ByteArrayOutputStream stream, Consumer<? super String> consumer) {
        String str = stream.toString();
        if (isBlank(str)) return;

        Arrays.stream(str.split(System.lineSeparator()))
            .forEach(consumer);
    }

    protected Path getPrepareDirectory(Map<String, Object> props) {
        return (Path) props.get(KEY_DISTRIBUTION_PREPARE_DIRECTORY);
    }

    protected Path getPackageDirectory(Map<String, Object> props) {
        return (Path) props.get(KEY_DISTRIBUTION_PACKAGE_DIRECTORY);
    }
}
