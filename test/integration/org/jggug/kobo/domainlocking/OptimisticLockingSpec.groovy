package org.jggug.kobo.domainlocking

import grails.plugin.spock.IntegrationSpec
import org.jggug.kobo.domainlocking.OptimisticLocking
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
    def "withOptimisticLock: calls or not onConflict handler when persistentVersion( #persistentVersion ) #operator modificationBaseVersion( #modificationBaseVersion )"() {
        given:
        testDomain.version = persistentVersion

        when:
        def result = OptimisticLocking.withOptimisticLock(testDomain, modificationBaseVersion) { domain ->
            assert executedMain
            assert domain.id == testDomain.id
            return "OK"
        }.onConflict { domain ->
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
        }.onConflict { domain ->
            assert false
        }

        then:
        result.returnValue == "OK"
    }

    def "withOptimisticLock: executes main clause and returns its return value when onConflict isn't specified and the locking is succeed"() {
        when:
        def result = OptimisticLocking.withOptimisticLock(testDomain, 1) { domain ->
            return "OK"
        }

        then:
        result.returnValue == "OK"
    }

    def "withOptimisticLock: returns null when onConflict isn't specified and the locking is failed"() {
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
    def "withOptimisticLock: calls onConflict handler when #exception.class.name was thrown"() {
        given:
        def exception = new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")

        when:
        def result = OptimisticLocking.withOptimisticLock(testDomain) { domain ->
            throw exception
        }.onConflict { domain, caused ->
            assert domain.id == testDomain.id
            assert domain.version == testDomain.version
            assert caused.is(exception)
            return "NG"
        }

        then:
        result.returnValue == "NG"

        and:
        assertVersionConflict(testDomain)
    }

    def "withOptimisticLock: returns null when called without onConflict handler"() {
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
        OptimisticLocking.withOptimisticLock(testDomain) { domain ->
            // to make DataIntegrityViolationException occur by flushing
            testDomain.value = null
            testDomain.save(validate: false)
            return "OK"
        }.onConflict { domain ->
            return "NG"
        }

        then:
        thrown(DataIntegrityViolationException)
    }

    def "withOptimisticLock: throws the original exception when an exception excepting OptimisticLockingFailureException and DataIntegrityViolationException occurs in main closure"() {
        when:
        OptimisticLocking.withOptimisticLock(testDomain) { domain ->
            throw new IOException("EXCEPTION_FOR_TEST")
        }.onConflict { domain ->
            assert false
        }

        then:
        def e = thrown(IOException)
        e.message == "EXCEPTION_FOR_TEST"
    }

    def "withOptimisticLock: throws the original exception when an exception occurs in onConflict closure"() {
        when:
        OptimisticLocking.withOptimisticLock(testDomain, 0) { domain ->
            assert false
        }.onConflict { domain ->
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
        def exception = new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")
        def history = []

        when:
        OptimisticLocking.withOptimisticLock(testDomain) { domain ->
            history << "mainClosure"
            throw exception
        }.onConflict {->
            history << "failureHandler of no args"
        }.onConflict {
            history << "failureHandler of 1 arg as implicit 'it'"
            assert it.is(testDomain)
        }.onConflict { domain ->
            history << "failureHandler of 1 arg as explicit 'domain'"
            assert domain.is(testDomain)
        }.onConflict { domain, caused ->
            history << "failureHandler of 2 args"
            assert caused.is(exception)
        }

        then: "OptimisticLockingFailureException isn't thrown to caller"
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

    def "withOptimisticLock: can have a failureHandler Closure which receives null as 2nd argument when failed at version comparation"() {
        when:
        def result = OptimisticLocking.withOptimisticLock(testDomain, 0) { domain ->
            assert false
        }.onConflict { domain, caused ->
            assert domain.is(testDomain)
            assert caused == null
            return "NG"
        }

        then:
        result.returnValue == "NG"
    }

    def "withOptimisticLock: cannot have a failureHandler Closure which receives more than 3 arguments"() {
        when:
        OptimisticLocking.withOptimisticLock(testDomain, 0) { domain ->
            assert false
        }.onConflict { domain, caused, tooMany ->
            assert false
        }

        then:
        thrown(IllegalArgumentException)
    }

    private static void assertVersionConflict(domain) {
        assert domain.hasErrors()
        def errors = domain.errors.getFieldErrors("version")
        assert errors.size() == 1
        assert errors[0].codes.toList().contains("default.optimistic.locking.failure")
    }
}
