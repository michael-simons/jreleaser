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

import org.jreleaser.bundle.RB;
import org.jreleaser.model.Archive;
import org.jreleaser.model.Artifact;
import org.jreleaser.model.JReleaserContext;
import org.jreleaser.model.Jlink;
import org.jreleaser.util.Errors;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.jreleaser.util.StringUtils.isNotBlank;

/**
 * @author Andres Almiray
 * @since 0.2.0
 */
public abstract class JlinkResolver extends Validator {
    public static void resolveJlinkOutputs(JReleaserContext context, Errors errors) {
        context.getLogger().debug("jlink");

        for (Jlink jlink : context.getModel().getAssemble().getActiveJlinks()) {
            if (jlink.isExported()) resolveJlinkOutputs(context, jlink, errors);
        }
    }

    private static void resolveJlinkOutputs(JReleaserContext context, Jlink jlink, Errors errors) {
        Path baseOutputDirectory = context.getAssembleDirectory()
            .resolve(jlink.getName())
            .resolve(jlink.getType());

        String imageName = jlink.getResolvedImageName(context);

        for (Artifact targetJdk : jlink.getTargetJdks()) {
            if (!context.isPlatformSelected(targetJdk)) continue;

            String platform = targetJdk.getPlatform();
            String platformReplaced = jlink.getPlatform().applyReplacements(platform);
            String str = targetJdk.getExtraProperties()
                .getOrDefault("archiveFormat", "ZIP")
                .toString();
            Archive.Format archiveFormat = Archive.Format.of(str);

            Path image = baseOutputDirectory
                .resolve(imageName + "-" + platformReplaced + "." + archiveFormat.extension())
                .toAbsolutePath();

            if (!Files.exists(image)) {
                errors.assembly(RB.$("validation_missing_assembly",
                    jlink.getType(), jlink.getName(), jlink.getName()));
            } else {
                Artifact artifact = Artifact.of(image, platform);
                artifact.setExtraProperties(jlink.getExtraProperties());
                artifact.activate();
                if (isNotBlank(jlink.getImageNameTransform())) {
                    artifact.setTransform(jlink.getResolvedImageNameTransform(context) + "-" +
                        platformReplaced + "." +
                        archiveFormat.extension());
                    artifact.getEffectivePath(context);
                }
                jlink.addOutput(artifact);
            }
        }
    }
}
