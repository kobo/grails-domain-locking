package jp.co.ntts.grails.plugin.domainlocking

import grails.plugin.spock.UnitSpec

class PessimisticLockingDslSpec extends UnitSpec {

    def mainClosure
    def onNotFoundClosure

    def setup() {
        mainClosure = { domain ->
            return "OK"
        }
        onNotFoundClosure = { domain ->
            return "NOT_FOUND"
        }
    }

    def "parse: with all configurations is of course valid"() {
        when:
        def dsl = PessimisticLockingDsl.parse {
            main mainClosure
            onNotFound onNotFoundClosure
        }

        then:
        dsl.mainClosure == mainClosure
        dsl.onNotFoundClosure == onNotFoundClosure
    }

    def "parse: without onNotFound closure which is option is valid"() {
        when:
        def dsl = PessimisticLockingDsl.parse {
            main mainClosure
        }

        then:
        dsl.mainClosure == mainClosure
        dsl.onNotFoundClosure == PessimisticLockingDsl.DEFAULT_ON_NOT_FOUND_CLOSURE
    }

    def "parse: without main which is mandatory causes exception"() {
        when:
        PessimisticLockingDsl.parse {
            onNotFound onNotFoundClosure
        }

        then:
        def e = thrown(DslParseException)
        e.message == "'main' is missing"
    }

    def "parse: with invalid DSL element causes exception"() {
        when:
        PessimisticLockingDsl.parse {
            main mainClosure
            onNotFound onNotFoundClosure

            invalidDslElement "INVALID"
        }

        then:
        def e = thrown(DslParseException)
        e.message == "Unexpected DSL element is found: invalidDslElement([INVALID])"
    }
}
