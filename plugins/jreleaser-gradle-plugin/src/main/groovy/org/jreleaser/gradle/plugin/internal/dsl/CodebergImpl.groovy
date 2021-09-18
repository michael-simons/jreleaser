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
package org.jreleaser.gradle.plugin.internal.dsl

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.internal.provider.Providers
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.jreleaser.gradle.plugin.dsl.Codeberg
import org.kordamp.gradle.util.ConfigureUtil

import javax.inject.Inject

/**
 *
 * @author Andres Almiray
 * @since 0.4.0
 */
@CompileStatic
class CodebergImpl extends AbstractGitService implements Codeberg {
    final Property<Boolean> draft
    final ChangelogImpl changelog
    final MilestoneImpl milestone
    final CommitAuthorImpl commitAuthor
    final PrereleaseImpl prerelease

    @Inject
    CodebergImpl(ObjectFactory objects) {
        super(objects)
        draft = objects.property(Boolean).convention(Providers.notDefined())

        changelog = objects.newInstance(ChangelogImpl, objects)
        milestone = objects.newInstance(MilestoneImpl, objects)
        commitAuthor = objects.newInstance(CommitAuthorImpl, objects)
        prerelease = objects.newInstance(PrereleaseImpl, objects)
    }

    @Override
    @Internal
    boolean isSet() {
        super.isSet() ||
            draft.present ||
            prerelease.isSet() ||
            changelog.isSet() ||
            milestone.isSet() ||
            commitAuthor.isSet()
    }

    @Override
    void prerelease(Action<? super Prerelease> action) {
        action.execute(prerelease)
    }

    @Override
    void prerelease(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = Prerelease) Closure<Void> action) {
        ConfigureUtil.configure(action, prerelease)
    }

    org.jreleaser.model.Codeberg toModel() {
        org.jreleaser.model.Codeberg service = new org.jreleaser.model.Codeberg()
        toModel(service)
        service.draft = draft.getOrElse(false)
        service.prerelease = prerelease.toModel()
        if (changelog.isSet()) service.changelog = changelog.toModel()
        if (milestone.isSet()) service.milestone = milestone.toModel()
        if (commitAuthor.isSet()) service.commitAuthor = commitAuthor.toModel()
        service
    }
}
