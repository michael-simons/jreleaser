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
package org.jreleaser.model.validation;

import org.jreleaser.model.Distribution;
import org.jreleaser.model.GitService;
import org.jreleaser.model.JReleaserContext;
import org.jreleaser.model.JReleaserModel;
import org.jreleaser.model.Sdkman;
import org.jreleaser.util.Errors;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static org.jreleaser.model.Sdkman.SDKMAN_CONSUMER_KEY;
import static org.jreleaser.model.Sdkman.SDKMAN_CONSUMER_TOKEN;
import static org.jreleaser.model.validation.DistributionsValidator.validateArtifactPlatforms;
import static org.jreleaser.model.validation.ExtraPropertiesValidator.mergeExtraProperties;
import static org.jreleaser.util.StringUtils.isBlank;

/**
 * @author Andres Almiray
 * @since 0.6.0
 */
public abstract class SdkmanValidator extends Validator {
    public static void validateSdkman(JReleaserContext context, Distribution distribution, Sdkman tool, Errors errors) {
        JReleaserModel model = context.getModel();
        Sdkman parentTool = model.getPackagers().getSdkman();

        if (!tool.isActiveSet() && parentTool.isActiveSet()) {
            tool.setActive(parentTool.getActive());
        }
        if(model.getProject().isSnapshot()) {
            tool.disable();
        }
        if (!tool.resolveEnabled(context.getModel().getProject(), distribution)) return;
        GitService service = model.getRelease().getGitService();
        if (!service.isReleaseSupported()) {
            tool.disable();
            return;
        }

        context.getLogger().debug("distribution.{}.sdkman", distribution.getName());

        mergeExtraProperties(tool, parentTool);
        validateContinueOnError(tool, parentTool);

        if (null == tool.getCommand()) {
            tool.setCommand(parentTool.getCommand());
            if (null == tool.getCommand()) {
                tool.setCommand(Sdkman.Command.MAJOR);
            }
        }

        if (isBlank(tool.getCandidate())) {
            tool.setCandidate(parentTool.getCandidate());
            if (isBlank(tool.getCandidate())) {
                tool.setCandidate(distribution.getName());
            }
        }

        if (isBlank(tool.getReleaseNotesUrl())) {
            tool.setReleaseNotesUrl(parentTool.getReleaseNotesUrl());
            if (isBlank(tool.getReleaseNotesUrl())) {
                tool.setReleaseNotesUrl(service.getReleaseNotesUrl());
            }
        }

        tool.setConsumerKey(
            checkProperty(context.getModel().getEnvironment(),
                SDKMAN_CONSUMER_KEY,
                "sdkman.consumerKey",
                tool.getConsumerKey(),
                errors,
                context.isDryrun()));

        tool.setConsumerToken(
            checkProperty(context.getModel().getEnvironment(),
                SDKMAN_CONSUMER_TOKEN,
                "sdkman.consumerToken",
                tool.getConsumerToken(),
                errors,
                context.isDryrun()));

        if (tool.getConnectTimeout() <= 0 || tool.getConnectTimeout() > 300) {
            tool.setConnectTimeout(20);
        }
        if (tool.getReadTimeout() <= 0 || tool.getReadTimeout() > 300) {
            tool.setReadTimeout(60);
        }

        validateArtifactPlatforms(context, distribution, tool, errors);
    }

    public static void postValidateSdkman(JReleaserContext context, Errors errors) {
        Map<String, List<Distribution>> map = context.getModel().getDistributions().values().stream()
            .filter(d -> d.isEnabled() && d.getSdkman().isEnabled())
            .collect(groupingBy(d -> d.getSdkman().getCandidate()));

        map.forEach((candidate, distributions) -> {
            if (distributions.size() > 1) {
                errors.configuration("sdkman.candidate '" + candidate + "' is defined for more than one distribution: " +
                    distributions.stream().map(Distribution::getName).collect(Collectors.joining(", ")));
            }
        });
    }
}
