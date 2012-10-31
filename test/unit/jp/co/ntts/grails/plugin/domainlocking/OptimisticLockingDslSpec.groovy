package jp.co.ntts.grails.plugin.domainlocking

import grails.plugin.spock.UnitSpec

class OptimisticLockingDslSpec extends UnitSpec {

    def mainClosure
    def onFailureClosure

    def setup() {
        mainClosure = { domain ->
            return "OK"
        }
        onFailureClosure = { domain ->
            return "NG"
        }
    }

    def "parse: with all configurations is of course valid"() {
        when:
        def dsl = OptimisticLockingDsl.parse {
            baseVersion 123
            main mainClosure
            onFailure onFailureClosure
        }

        then:
        dsl.baseVersion == 123
        dsl.mainClosure == mainClosure
        dsl.onFailureHandler == onFailureClosure
    }

    def "parse: without baseVersion which is option is valid"() {
        when:
        def dsl = OptimisticLockingDsl.parse {
            main mainClosure
            onFailure onFailureClosure
        }

        then:
        dsl.baseVersion == null
        dsl.mainClosure == mainClosure
        dsl.onFailureHandler == onFailureClosure
    }

    def "parse: without onFailure which is option is valid"() {
        when:
        def dsl = OptimisticLockingDsl.parse {
            baseVersion 123
            main mainClosure
        }

        then:
        dsl.baseVersion == 123
        dsl.mainClosure == mainClosure
        dsl.onFailureHandler == null
    }

    def "parse: without main which is mandatory causes exception"() {
        when:
        OptimisticLockingDsl.parse {
            baseVersion 123
            onFailure onFailureClosure
        }

        then:
        def e = thrown(DslParseException)
        e.message == "'main' is missing"
    }

    def "parse: with invalid DSL element causes exception"() {
        when:
        OptimisticLockingDsl.parse {
            baseVersion 123
            main mainClosure
            onFailure onFailureClosure

            invalidDslElement "INVALID"
        }

        then:
        def e = thrown(DslParseException)
        e.message == "Unexpected DSL element is found: invalidDslElement([INVALID])"
    }
}
