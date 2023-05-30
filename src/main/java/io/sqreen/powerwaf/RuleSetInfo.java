/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2022 Datadog, Inc.
 */

package io.sqreen.powerwaf;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RuleSetInfo {
    public static class SectionInfo {
        private final String error;
        private final List<String> loaded;
        private final List<String> failed;
        // map error string -> array of rule ids
        private final Map<String, List<String>> errors;

        public SectionInfo(String error) {
            this.error = error;
            this.loaded = null;
            this.failed = null;
            this.errors = null;
        }

        public SectionInfo(List<String> loaded, List<String> failed, Map<String, List<String>> errors) {
            this.error = null;
            this.loaded = loaded;
            this.failed = failed;
            this.errors = errors;
        }

        public String getError() {
            return error;
        }

        public List<String> getLoaded() {
            if (loaded == null) {
                return Collections.emptyList();
            }
            return loaded;
        }

        public List<String> getFailed() {
            if (failed == null) {
                return Collections.emptyList();
            }
            return failed;
        }

        public Map<String, List<String>> getErrors() {
            if (errors == null) {
                return Collections.emptyMap();
            }
            return errors;
        }

        @Override
        public String toString() {
            if (error != null) {
                return new StringJoiner(", ", SectionInfo.class.getSimpleName() + "[", "]")
                        .add("error=" + error)
                        .toString();
            }
            return new StringJoiner(", ", SectionInfo.class.getSimpleName() + "[", "]")
                    .add("loaded=" + loaded)
                    .add("failed=" + failed)
                    .add("errors=" + errors)
                    .toString();
        }
    }


    public final String rulesetVersion;
    public final SectionInfo rules;
    public final SectionInfo customRules;
    public final SectionInfo rulesData;
    public final SectionInfo rulesOverride;
    public final SectionInfo exclusions;

    public RuleSetInfo(String rulesetVersion, SectionInfo rules, SectionInfo customRules, SectionInfo rulesData, SectionInfo rulesOverride, SectionInfo exclusions) {
        this.rulesetVersion = rulesetVersion;
        this.rules = rules;
        this.customRules = customRules;
        this.rulesData = rulesData;
        this.rulesOverride = rulesOverride;
        this.exclusions = exclusions;
    }

    public int getNumRulesOK() {
        int count = 0;
        if (this.rules != null) {
            count += this.rules.getLoaded().size();
        }
        if (this.customRules != null) {
            count += this.customRules.getLoaded().size();
        }

        return count;
    }

    public int getNumRulesError() {
        int count = 0;
        if (this.rules != null) {
            count += this.rules.getFailed().size();
        }
        if (this.customRules != null) {
            count += this.customRules.getFailed().size();
        }

        return count;
    }

    public Map<String, List<String>> getErrors() {
        return Stream.of(this.rules, this.customRules)
                .filter(Objects::nonNull)
                .map(r -> r.getErrors())
                .flatMap(e -> e.entrySet().stream())
                .collect(Collectors.toMap(
                        e -> e.getKey(),
                        e -> e.getValue(),
                        (l1, l2) -> Stream.concat(l1.stream(), l2.stream()).collect(Collectors.toList())));
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RuleSetInfo.class.getSimpleName() + "[", "]")
                .add("rulesetVersion='" + rulesetVersion + "'")
                .add("rules=" + rules)
                .add("customRules=" + customRules)
                .add("rulesData=" + rulesData)
                .add("rulesOverride=" + rulesOverride)
                .add("exclusions=" + exclusions)
                .toString();
    }
}
