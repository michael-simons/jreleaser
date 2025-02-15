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

import org.jreleaser.model.Distribution;
import org.jreleaser.model.GitService;
import org.jreleaser.model.JReleaserContext;
import org.jreleaser.model.Macports;
import org.jreleaser.model.Project;
import org.jreleaser.model.tool.spi.ToolProcessingException;
import org.jreleaser.util.Constants;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.jreleaser.templates.TemplateUtils.trimTplExtension;
import static org.jreleaser.util.Constants.KEY_MACPORTS_PACKAGE_NAME;
import static org.jreleaser.util.MustacheUtils.passThrough;

/**
 * @author Andres Almiray
 * @since 0.9.0
 */
public class MacportsToolProcessor extends AbstractRepositoryToolProcessor<Macports> {
    private static final String LINE_SEPARATOR = " \\\n                 ";

    public MacportsToolProcessor(JReleaserContext context) {
        super(context);
    }

    @Override
    protected void doPackageDistribution(Distribution distribution, Map<String, Object> props, Path packageDirectory) throws ToolProcessingException {
        super.doPackageDistribution(distribution, props, packageDirectory);
        copyPreparedFiles(distribution, props);
    }

    @Override
    protected void fillToolProperties(Map<String, Object> props, Distribution distribution) throws ToolProcessingException {
        GitService gitService = context.getModel().getRelease().getGitService();

        props.put(Constants.KEY_MACPORTS_REPOSITORY_REPO_URL,
            gitService.getResolvedRepoUrl(context.getModel(), tool.getRepository().getOwner(), tool.getRepository().getResolvedName()));
        props.put(Constants.KEY_MACPORTS_REPOSITORY_REPO_CLONE_URL,
            gitService.getResolvedRepoCloneUrl(context.getModel(), tool.getRepository().getOwner(), tool.getRepository().getResolvedName()));

        List<String> longDescription = Arrays.asList(context.getModel().getProject().getLongDescription().split("\\n"));

        props.put(KEY_MACPORTS_PACKAGE_NAME, tool.getPackageName());
        props.put(Constants.KEY_MACPORTS_REVISION, tool.getRevision());
        props.put(Constants.KEY_MACPORTS_CATEGORIES, String.join(" ", tool.getCategories()));
        props.put(Constants.KEY_MACPORTS_MAINTAINERS, passThrough(String.join(LINE_SEPARATOR, tool.getResolvedMaintainers(context))));
        props.put(Constants.KEY_PROJECT_LONG_DESCRIPTION, passThrough(String.join(LINE_SEPARATOR, longDescription)));
        if (distribution.getType() == Distribution.DistributionType.JAVA_BINARY) {
            props.put(Constants.KEY_MACPORTS_JAVA_VERSION, resolveJavaVersion(distribution));
        }

        String distributionUrl = (String) props.get(Constants.KEY_DISTRIBUTION_URL);
        String artifactFilename = (String) props.get(Constants.KEY_DISTRIBUTION_ARTIFACT_FILE_NAME);
        if (distributionUrl.endsWith(artifactFilename)) {
            distributionUrl = distributionUrl.substring(0, distributionUrl.length() - artifactFilename.length());
        }
        distributionUrl = distributionUrl.replace(context.getModel().getProject().getResolvedVersion(), "${version}");
        props.put(Constants.KEY_MACPORTS_DISTRIBUTION_URL, distributionUrl);
    }

    private String resolveJavaVersion(Distribution distribution) {
        String version = distribution.getJava().getVersion();
        if ("8".equals(version)) return "1.8+";
        try {
            Integer.parseInt(version);
            return version + "+";
        } catch (NumberFormatException ignored) {
            // noop
        }

        return version;
    }

    @Override
    protected void writeFile(Project project,
                             Distribution distribution,
                             String content,
                             Map<String, Object> props,
                             Path outputDirectory,
                             String fileName)
        throws ToolProcessingException {
        fileName = trimTplExtension(fileName);

        Path outputFile = "Portfile".equals(fileName) ?
            outputDirectory.resolve("ports")
                .resolve(tool.getCategories().get(0))
                .resolve(tool.getPackageName())
                .resolve(fileName) :
            outputDirectory.resolve(fileName);

        writeFile(content, outputFile);
    }
}
