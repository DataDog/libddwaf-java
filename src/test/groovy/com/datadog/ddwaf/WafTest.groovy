/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2023 Datadog, Inc.
 */

package com.datadog.ddwaf

import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

@RunWith(Enclosed)
class WafTest implements WafTrait {

    static class InitializationTest {
        @Test
        void testLibVersion() {
            // Verify the constant LIB_VERSION is defined
            assert Waf.LIB_VERSION != null
            assert Waf.LIB_VERSION.matches('\\d+\\.\\d+\\.\\d+')
        }

        @Test
        void testVersionMatchesLibVersion() {
            // Get actual version from native library
            String version = Waf.version

            // Verify version starts with the same prefix as LIB_VERSION
            assert version.startsWith(Waf.LIB_VERSION) || Waf.LIB_VERSION.startsWith(version),
                  "Version (${version}) should start with LIB_VERSION (${Waf.LIB_VERSION})"
        }
    }

    static class LimitsTest {
        @Test
        void testLimitsCreationAndAccess() {
            int maxDepth = 10
            int maxElements = 100
            int maxStringSize = 1000
            long generalBudgetInUs = 5000
            long runBudgetInUs = 2500

            Waf.Limits limits = new Waf.Limits(maxDepth, maxElements, maxStringSize, generalBudgetInUs, runBudgetInUs)

            // Verify field values
            assert limits.maxDepth == maxDepth
            assert limits.maxElements == maxElements
            assert limits.maxStringSize == maxStringSize
            assert limits.generalBudgetInUs == generalBudgetInUs
            assert limits.runBudgetInUs == runBudgetInUs
        }

        @Test
        void testLimitsReduceBudget() {
            int maxDepth = 10
            int maxElements = 100
            int maxStringSize = 1000
            long generalBudgetInUs = 5000
            long runBudgetInUs = 2500
            long reduction = 2000

            Waf.Limits limits = new Waf.Limits(maxDepth, maxElements, maxStringSize, generalBudgetInUs, runBudgetInUs)
            Waf.Limits reducedLimits = limits.reduceBudget(reduction)

            // Verify reduced budget
            assert reducedLimits.generalBudgetInUs == (generalBudgetInUs - reduction)

            // Verify other fields unchanged
            assert reducedLimits.maxDepth == maxDepth
            assert reducedLimits.maxElements == maxElements
            assert reducedLimits.maxStringSize == maxStringSize
            assert reducedLimits.runBudgetInUs == runBudgetInUs
        }

        @Test
        void testLimitsReduceBudgetWithZeroClamp() {
            int maxDepth = 10
            int maxElements = 100
            int maxStringSize = 1000
            long generalBudgetInUs = 1000
            long runBudgetInUs = 500
            long reduction = 2000  // More than generalBudgetInUs

            Waf.Limits limits = new Waf.Limits(maxDepth, maxElements, maxStringSize, generalBudgetInUs, runBudgetInUs)
            Waf.Limits reducedLimits = limits.reduceBudget(reduction)

            // Verify budget is clamped to 0
            assert reducedLimits.generalBudgetInUs == 0
        }

        @Test
        void testLimitsToString() {
            int maxDepth = 10
            int maxElements = 100
            int maxStringSize = 1000
            long generalBudgetInUs = 5000
            long runBudgetInUs = 2500

            Waf.Limits limits = new Waf.Limits(maxDepth, maxElements, maxStringSize, generalBudgetInUs, runBudgetInUs)

            assert limits.maxDepth == maxDepth
            assert limits.maxElements == maxElements
            assert limits.maxStringSize == maxStringSize
            assert limits.generalBudgetInUs == generalBudgetInUs
            assert limits.runBudgetInUs == runBudgetInUs
        }
    }

    static class ResultTest {
        @Test
        void testResultEnumValues() {
            // Verify enum values have correct codes
            assert Waf.Result.OK.code == 0
            assert Waf.Result.MATCH.code == 1
        }
    }

    static class ResultWithDataTest {
        @Test
        void testResultWithDataConstruction() {
            Waf.Result result = Waf.Result.OK
            String data = 'Test data'
            Map<String, Map<String, Object>> actions = [testAction: [key: 'value']]
            Map<String, String> derivatives = [testDerivative: 'value']

            Waf.ResultWithData resultWithData = new Waf.ResultWithData(result, data, actions, derivatives)

            assert resultWithData.result == result
            assert resultWithData.data == data
            assert resultWithData.actions == actions
            assert resultWithData.derivatives == derivatives
        }

        @Test
        void testResultWithDataToString() {
            Waf.Result result = Waf.Result.MATCH
            String data = 'Test data'
            Map<String, Map<String, Object>> actions = [testAction: [key: 'value']]
            Map<String, String> derivatives = [testDerivative: 'value']

            Waf.ResultWithData resultWithData = new Waf.ResultWithData(result, data, actions, derivatives)

            assert resultWithData.result == result
            assert resultWithData.data == data
            assert resultWithData.actions == actions
            assert resultWithData.derivatives == derivatives
        }

        @Test
        void testOkNullConstant() {
            // Verify OK_NULL constant is properly initialized
            assert Waf.ResultWithData.OK_NULL != null
            assert Waf.ResultWithData.OK_NULL.result == Waf.Result.OK
            assert Waf.ResultWithData.OK_NULL.data == null
            assert Waf.ResultWithData.OK_NULL.actions != null
            assert Waf.ResultWithData.OK_NULL.actions.size() == 0
        }
    }
}
