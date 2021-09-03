package io.sqreen.powerwaf

import org.junit.Test

import static io.sqreen.powerwaf.Powerwaf.ActionWithData
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

class BasicTests implements PowerwafTrait {

    @Test
    void 'the version is correct'() {
        assert Powerwaf.version =~ Powerwaf.LIB_VERSION
    }

    @Test
    void 'test running basic rule'() {
        def ruleSet = ARACHNI_ATOM

        ctx = Powerwaf.createContext('test', ruleSet)

        ActionWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni']], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)

        def json = slurper.parseText(awd.data)
        assert json.ret_code == [1]
        assert json.flow == ['arachni_detection']
        assert json.rule == ['arachni_rule']
    }

    @Test
    void 'test with array of string lists'() {
        def ruleSet = ARACHNI_ATOM

        ctx = Powerwaf.createContext('test', ruleSet)

        def data = [
            attack: ['o:1:"ee":1:{}'],
            PassWord: ['Arachni'],
        ]
        ActionWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
    }

    @Test
    void 'test with array'() {
        def ruleSet = ARACHNI_ATOM

        ctx = Powerwaf.createContext('test', ruleSet)

        def data = ['foo', 'Arachni'] as String[]
        ActionWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
    }

    @Test
    void 'test null argument'() {
        def ruleSet = ARACHNI_ATOM

        ctx = Powerwaf.createContext('test', ruleSet)

        def data = [null, 'Arachni']
        ActionWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
    }

    @Test
    void 'test boolean arguments'() {
        def ruleSet = ARACHNI_ATOM

        ctx = Powerwaf.createContext('test', ruleSet)

        def data = [true, false, 'Arachni']
        ActionWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
    }

    @SuppressWarnings('EmptyClass')
    static class MyClass { }

    @Test
    void 'test unencodable arguments'() {
        def ruleSet = ARACHNI_ATOM

        ctx = Powerwaf.createContext('test', ruleSet)

        def data = [new MyClass(), 'Arachni']
        ActionWithData awd = ctx.runRules(
                ['server.request.headers.no_cookies': ['user-agent': data]], limits)
        assertThat awd.action, is(Powerwaf.Action.MONITOR)
    }
}
