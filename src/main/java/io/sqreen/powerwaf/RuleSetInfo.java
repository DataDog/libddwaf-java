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
        private final List<String> skipped;
        // map error string -> array of rule ids
        private final Map<String, List<String>> errors;
        private final Map<String, List<String>> warnings;

        public SectionInfo(String error) {
            this.error = error;
            this.loaded = null;
            this.failed = null;
            this.errors = null;
            this.skipped = null;
            this.warnings = null;
        }

        public SectionInfo(List<String> loaded, List<String> failed, Map<String, List<String>> errors) {
            this.error = null;
            this.loaded = loaded;
            this.failed = failed;
            this.errors = errors;
            this.skipped = null;
            this.warnings = null;
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

    public final String error;
    public final String rulesetVersion;
    public final SectionInfo rules;
    public final SectionInfo customRules;
    public final SectionInfo rulesData;
    public final SectionInfo rulesOverride;
    public final SectionInfo exclusions;
    public final SectionInfo actions;
    public final SectionInfo processors;
    public final SectionInfo scanners;

    public RuleSetInfo(String error, String rulesetVersion, SectionInfo rules,
                       SectionInfo customRules, SectionInfo rulesData, SectionInfo rulesOverride,
                       SectionInfo exclusions, SectionInfo actions, SectionInfo processors, SectionInfo scanners) {
        this.error = error;
        this.rulesetVersion = rulesetVersion;
        this.rules = rules;
        this.customRules = customRules;
        this.rulesData = rulesData;
        this.rulesOverride = rulesOverride;
        this.exclusions = exclusions;
        this.actions = actions;
        this.processors = processors;
        this.scanners = scanners;
    }

    public int getNumRulesOK() {
        int count = countLoadedForSection(this.rules);
        count += countLoadedForSection(this.customRules);
        count += countLoadedForSection(this.rulesData);
        count += countLoadedForSection(this.rulesOverride);
        count += countLoadedForSection(this.exclusions);
        count += countLoadedForSection(this.actions);
        count += countLoadedForSection(this.processors);
        count += countLoadedForSection(this.scanners);
        return count;
    }

    public int getNumRulesError() {
        if (this.error != null && !this.error.isEmpty()) {
            return 1;
        }
        int count = countErrorsForSection(this.rules);
        count += countErrorsForSection(this.customRules);
        count += countErrorsForSection(this.rulesData);
        count += countErrorsForSection(this.rulesOverride);
        count += countErrorsForSection(this.exclusions);
        count += countErrorsForSection(this.actions);
        count += countErrorsForSection(this.processors);
        count += countErrorsForSection(this.scanners);

        return count;
    }

    public Map<String, List<String>> getErrors() {
        return Stream.of(this.rules, this.customRules, this.rulesData, this.rulesOverride, this.exclusions, this.actions, this.processors, this.scanners)
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
                .add("actions=" + actions)
                .add("processors=" + processors)
                .add("scanners=" + scanners)
                .toString();
    }

    private int countErrorsForSection(RuleSetInfo.SectionInfo section) {
        if (section != null && section.getError() != null && !section.getError().isEmpty()) {
            return 1;
        }
        if (section != null && section.getErrors() != null) {
            return section.getErrors().size();
        }
        return 0;
    }
    private int countLoadedForSection(RuleSetInfo.SectionInfo section) {
        if (section != null && section.getLoaded() != null) {
            return section.getLoaded().size();
        }
        return 0;
    }
}
