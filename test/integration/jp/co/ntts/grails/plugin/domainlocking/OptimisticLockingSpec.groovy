package jp.co.ntts.grails.plugin.domainlocking

import grails.plugin.spock.IntegrationSpec
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import spock.lang.Unroll
import test.TestDomain

class OptimisticLockingSpec extends IntegrationSpec {

    def testDomain

    def setup() {
        assert TestDomain.list() == []

        // saving TestDomain
        testDomain = new TestDomain(value: "OptimisticLockingSpec's INIT_VALUE").save(failOnError: true, flush: true)
        assert TestDomain.count() == 1

        // updating version
        testDomain.value = "OptimisticLockingSpec's TEST_VALUE"
        testDomain.save(failOnError: true, flush: true)
        assert testDomain.version == 1
    }

    @Unroll
    def "withOptimisticLock: calls or not onFailure handler when persistentVersion( #persistentVersion ) #operator modificationBaseVersion( #modificationBaseVersion )"() {
        given:
        testDomain.version = persistentVersion

        when:
        def result = OptimisticLocking.withOptimisticLock(testDomain, modificationBaseVersion) { domain ->
            assert executedMain
            assert domain.id == testDomain.id
            return "OK"
        }.onFailure { domain ->
            assert domain.id == testDomain.id
            return "NG"
        }

        then:
        result.returnValue == expectedResult

        and:
        if (expectedResult == "NG") {
            assertVersionConflict(testDomain)
        }

        where:
        persistentVersion | modificationBaseVersion | operator | expectedResult | executedMain
        0                 | 0                       | "="      | "OK"           | true
        1                 | 1                       | "="      | "OK"           | true
        10                | 10                      | "="      | "OK"           | true
        100               | 100                     | "="      | "OK"           | true
        0                 | 1                       | "<"      | "OK"           | true
        0                 | 10                      | "<"      | "OK"           | true
        1                 | 2                       | "<"      | "OK"           | true
        10                | 11                      | "<"      | "OK"           | true
        100               | 101                     | "<"      | "OK"           | true
        1                 | 0                       | ">"      | "NG"           | false
        2                 | 0                       | ">"      | "NG"           | false
        2                 | 1                       | ">"      | "NG"           | false
        11                | 10                      | ">"      | "NG"           | false
        101               | 100                     | ">"      | "NG"           | false
    }

    def "withOptimisticLock: executes main clause and returns its return value when versionCompare isn't specified"() {
        when:
        def result = OptimisticLocking.withOptimisticLock(testDomain) { domain ->
            return "OK"
        }.onFailure { domain ->
            assert false
        }

        then:
        result.returnValue == "OK"
    }

    def "withOptimisticLock: executes main clause and returns its return value when onFailure isn't specified and the locking is succeed"() {
        when:
        def result = OptimisticLocking.withOptimisticLock(testDomain, 1) { domain ->
            return "OK"
        }

        then:
        result.returnValue == "OK"
    }

    def "withOptimisticLock: returns null when onFailure isn't specified and the locking is failed"() {
        when:
        def result = OptimisticLocking.withOptimisticLock(testDomain, 0) { domain ->
            assert false
        }

        then:
        result.returnValue == null

        and:
        assertVersionConflict(testDomain)
    }

    @Unroll
    def "withOptimisticLock: calls onFailure handler when #exception.class.name was thrown"() {
        when:
        def result = OptimisticLocking.withOptimisticLock(testDomain) { domain ->
            throw exception
        }.onFailure { domain, caused ->
            assert domain.id == testDomain.id
            assert domain.version == testDomain.version
            assert caused.is(exception)
            return "NG"
        }

        then:
        result.returnValue == "NG"

        and:
        assertVersionConflict(testDomain)

        where:
        exception << [
            new OptimisticLockingFailureException("EXCEPTION_FOR_TEST"),
            new DataIntegrityViolationException("EXCEPTION_FOR_TEST"),
        ]
    }

    def "withOptimisticLock: returns null when called without onFailure handler"() {
        when:
        def result = OptimisticLocking.withOptimisticLock(testDomain) { domain ->
            throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")
        }

        then:
        result.returnValue == null

        and:
        assertVersionConflict(testDomain)
    }

    def "withOptimisticLock: flushes hibernate session implicitly in order to publish SQL and made exception occur just in time if necessary"() {
        given:
        testDomain.save(failOnError: true, flush: true)

        when:
        def result = OptimisticLocking.withOptimisticLock(testDomain) { domain ->
            // to make DataIntegrityViolationException occur by flushing
            testDomain.value = null
            testDomain.save(validate: false)
        }.onFailure { domain, caused ->
            assert caused instanceof DataIntegrityViolationException
            return "NG"
        }

        then:
        result.returnValue == "NG"
    }

    def "withOptimisticLock: throws the original exception when an exception excepting OptimisticLockingFailureException and DataIntegrityViolationException occurs in main closure"() {
        when:
        OptimisticLocking.withOptimisticLock(testDomain) { domain ->
            throw new IOException("EXCEPTION_FOR_TEST")
        }.onFailure { domain ->
            assert false
        }

        then:
        def e = thrown(IOException)
        e.message == "EXCEPTION_FOR_TEST"
    }

    def "withOptimisticLock: throws the original exception when an exception occurs in onFailure closure"() {
        when:
        OptimisticLocking.withOptimisticLock(testDomain, 0) { domain ->
            assert false
        }.onFailure { domain ->
            throw new IOException("EXCEPTION_FOR_TEST")
        }

        then:
        def e = thrown(IOException)
        e.message == "EXCEPTION_FOR_TEST"
    }

    def "withOptimisticLock: throws IllegalArgumentException when called with no domain argument"() {
        when:
        OptimisticLocking.withOptimisticLock(null, { /.../ })

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "domain should not be null."
    }

    def "withOptimisticLock: throws IllegalArgumentException when called with no mainClosure argument"() {
        when:
        OptimisticLocking.withOptimisticLock(testDomain, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "mainClosure should not be null."
    }

    def "withOptimisticLock: can have some following failureHandler Closure which receives 0-3 argument(s)"() {
        given:
        def exception = new DataIntegrityViolationException("EXCEPTION_FOR_TEST")
        def history = []

        when:
        OptimisticLocking.withOptimisticLock(testDomain) { domain ->
            history << "mainClosure"
            throw exception
        }.onFailure {->
            history << "failureHandler of no args"
        }.onFailure {
            history << "failureHandler of 1 arg as implicit 'it'"
            assert it.is(testDomain)
        }.onFailure { domain ->
            history << "failureHandler of 1 arg as explicit 'domain'"
            assert domain.is(testDomain)
        }.onFailure { domain, caused ->
            history << "failureHandler of 2 args"
            assert caused.is(exception)
        }

        then: "DataIntegrityViolationException isn't thrown to caller"
        noExceptionThrown()

        and: "all failureHandlers are executed"
        history == [
            "mainClosure",
            "failureHandler of no args",
            "failureHandler of 1 arg as implicit 'it'",
            "failureHandler of 1 arg as explicit 'domain'",
            "failureHandler of 2 args",
        ]
    }

    private static void assertVersionConflict(domain) {
        assert domain.hasErrors()
        def errors = domain.errors.getFieldErrors("version")
        assert errors.size() == 1
        assert errors[0].codes.toList().contains("default.optimistic.locking.failure")
    }
}
