package io.sqreen.detailed_metrics

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.flipkart.zjsonpatch.JsonDiff
import com.github.tomakehurst.wiremock.common.Json
import com.github.tomakehurst.wiremock.matching.EqualToJsonPattern
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher

class JsonMatcher extends BaseMatcher<String> {

    private final JsonNode expected
    private final EqualToJsonPattern equalToJsonPattern

    private JsonMatcher(String expectedJson, boolean ignoreArrayOrder, boolean ignoreExtraElements) {
        this.expected = Json.read(expectedJson, JsonNode.class)
        this.equalToJsonPattern = new EqualToJsonPattern(expectedJson, ignoreArrayOrder, ignoreExtraElements)
    }

    static Matcher<String> matchesJson(String expectedJson) {
        return new JsonMatcher(expectedJson, false, false)
    }

    static Matcher<String> matchesJson(String expectedJson,
                                              boolean ignoreArrayOrder,
                                              boolean ignoreExtraElements) {
        return new JsonMatcher(expectedJson, ignoreArrayOrder, ignoreExtraElements)
    }

    @Override
    public boolean matches(Object item) {
        String s = item.toString()
        def match = this.equalToJsonPattern.match(s)
        match.isExactMatch()
    }

    @Override
    void describeTo(Description description) {
        description.appendText(this.equalToJsonPattern.getExpected())
        if (this.equalToJsonPattern.isIgnoreArrayOrder()) {
            description.appendText(" ignoring array order")
        }
        if (this.equalToJsonPattern.isIgnoreExtraElements()) {
            description.appendText(" ignoring extra elements")
        }
    }

    @Override
    void describeMismatch(Object item, Description description) {
        JsonNode actual = Json.read(item.toString(), JsonNode.class)
        String actualPrinted = Json.prettyPrint(actual.toString())
        description.appendText("was ").appendText(actualPrinted)
        description.appendText("\n\ndiff is ")
        ArrayNode diff = JsonDiff.asJson(this.expected, actual)
        description.appendText(Json.prettyPrint(diff.toString()))
    }
}
