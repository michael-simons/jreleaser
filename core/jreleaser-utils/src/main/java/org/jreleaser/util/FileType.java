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
package org.jreleaser.util;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.jreleaser.util.StringUtils.isBlank;

/**
 * @author Andres Almiray
 * @since 0.10.0
 */
public enum FileType {
    DEB("deb"),
    DMG("dmg"),
    EXE("exe"),
    JAR("jar"),
    MSI("msi"),
    NUGET("nuget"),
    PKG("pkg"),
    RPM("rpm"),
    TAR("tar"),
    TAR_BZ2("tar.bz2"),
    TAR_GZ("tar.gz"),
    TGZ("tgz"),
    ZIP("zip");

    private final String type;

    FileType(String type) {
        this.type = type;
    }

    public String type() {
        return this.type;
    }

    public String extension() {
        return "." + this.type;
    }

    @Override
    public String toString() {
        return type();
    }

    public static FileType of(String str) {
        if (isBlank(str)) return null;
        return FileType.valueOf(str.toUpperCase().trim()
            .replace(".", "_"));
    }

    public static Set<String> getSupportedTypes() {
        Set<String> set = new LinkedHashSet<>();
        for (FileType value : values()) {
            set.add(value.type());
        }
        return set;
    }

    public static Set<String> getSupportedExtensions() {
        Set<String> set = new LinkedHashSet<>();
        for (FileType value : values()) {
            set.add(value.extension());
        }
        return set;
    }
}