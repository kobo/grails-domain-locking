package jp.co.ntts.grails.plugin.domainlocking

import grails.plugin.spock.IntegrationSpec
import test.TestDomain

class DynamicMethodsSpec extends IntegrationSpec {

    def testDomain

    def setup() {
        assert TestDomain.list() == []

        // saving TestDomain
        testDomain = new TestDomain(value: "DynamicMethodsSpec's TEST_VALUE").save(failOnError: true, flush: true)
        assert TestDomain.count() == 1
    }

    def "withOptimisticLock: delegates to OptimisticLockingUtil.withOptimisticLock()"() {
        when:
        def result = testDomain.withOptimisticLock {
            baseVersion 0
            main {
                return "OK"
            }
            onFailure { domain ->
                assert false
            }
        }

        then:
        result == "OK"
    }

    def "withPessimisticLock: delegates to PessimisticLockingUtil.withPessimisticLock()"() {
        when:
        def result = TestDomain.withPessimisticLock(testDomain.id) {
            main { lockedDomain ->
                return "OK"
            }
            onNotFound { id ->
                assert false
            }
        }

        then:
        result == "OK"
    }

}
