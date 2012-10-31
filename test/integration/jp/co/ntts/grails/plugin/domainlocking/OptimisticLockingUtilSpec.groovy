package jp.co.ntts.grails.plugin.domainlocking

import grails.plugin.spock.IntegrationSpec
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import spock.lang.Unroll
import test.TestDomain

class OptimisticLockingUtilSpec extends IntegrationSpec {

    def testDomain

    def setup() {
        // saving TestDomain
        testDomain = new TestDomain(value: "INIT_VALUE").save(failOnError: true, flush: true)

        // updating version
        testDomain.value = "TEST_VALUE"
        testDomain.save(failOnError: true, flush: true)
        assert testDomain.version == 1
    }

    @Unroll
    def "withOptimisticLock: calls or not onFailure handler when persistentVersion( #persistentVersion ) #operator modificationBaseVersion( #modificationBaseVersion )"() {
        given:
        testDomain.version = persistentVersion

        when:
        def result = OptimisticLockingUtil.withOptimisticLock(testDomain) {
            baseVersion modificationBaseVersion
            main { domain ->
                assert executedMain
                assert domain.id == testDomain.id
                return "OK"
            }
            onFailure { domain ->
                assert domain.id == testDomain.id
                return "NG"
            }
        }

        then:
        result == expectedResult

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
        def result = OptimisticLockingUtil.withOptimisticLock(testDomain) {
            main { domain ->
                return "OK"
            }
            onFailure { domain ->
                assert false
            }
        }

        then:
        result == "OK"
    }

    def "withOptimisticLock: executes main clause and returns its return value when onFailure isn't specified and the locking is succeed"() {
        when:
        def result = OptimisticLockingUtil.withOptimisticLock(testDomain) {
            baseVersion 1 // OK
            main { domain ->
                return "OK"
            }
        }

        then:
        result == "OK"
    }

    def "withOptimisticLock: returns null when onFailure isn't specified and the locking is failed"() {
        when:
        def result = OptimisticLockingUtil.withOptimisticLock(testDomain) {
            baseVersion 0 // failure!
            main { domain ->
                assert false
            }
        }

        then:
        result == null

        and:
        assertVersionConflict(testDomain)
    }

    @Unroll
    def "withOptimisticLock: calls onFailure handler when #exception.class.name was thrown"() {
        when:
        def result = OptimisticLockingUtil.withOptimisticLock(testDomain) {
            main { domain ->
                throw exception
            }
            onFailure { domain ->
                assert domain.id == testDomain.id
                assert domain.version == testDomain.version
                return "NG"
            }
        }

        then:
        result == "NG"

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
        def result = OptimisticLockingUtil.withOptimisticLock(testDomain) {
            main { domain ->
                throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")
            }
        }

        then:
        result == null

        and:
        assertVersionConflict(testDomain)
    }

    def "withOptimisticLock: throws an exception when called without main handler"() {
        when:
        OptimisticLockingUtil.withOptimisticLock(testDomain) {
            baseVersion 0
            onFailure { domain ->
                assert false
            }
        }

        then:
        thrown DslParseException
    }

    def "withOptimisticLock: flushes hibernate session implicitly in order to publish SQL and made exception occur just in time if necessary"() {
        given:
        testDomain.save(failOnError: true, flush: true)

        when:
        def result = OptimisticLockingUtil.withOptimisticLock(testDomain) {
            main { domain ->
                // to make DataIntegrityViolationException occur
                testDomain.value = null
                testDomain.save(validate: false)
            }
            onFailure { domain ->
                return "NG"
            }
        }

        then:
        result == "NG"
    }

    def "withOptimisticLock: throws the original exception when an exception occurs in main closure"() {
        when:
        OptimisticLockingUtil.withOptimisticLock(testDomain) {
            main {
                throw new IOException("EXCEPTION_FOR_TEST")
            }
            onFailure { domain ->
                assert false
            }
        }

        then:
        def e = thrown(IOException)
        e.message == "EXCEPTION_FOR_TEST"
    }

    def "withOptimisticLock: throws the original exception when an exception occurs in onFailure closure"() {
        when:
        OptimisticLockingUtil.withOptimisticLock(testDomain) {
            baseVersion 0 // failure!
            main { domain ->
                assert false
            }
            onFailure { domain ->
                throw new IOException("EXCEPTION_FOR_TEST")
            }
        }

        then:
        def e = thrown(IOException)
        e.message == "EXCEPTION_FOR_TEST"
    }

    def "withOptimisticLock: throws IllegalArgumentException when called with no domain argument"() {
        when:
        OptimisticLockingUtil.withOptimisticLock(null, { main { /.../ } })

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "domain should not be null."
    }

    def "withOptimisticLock: throws IllegalArgumentException when called with no dslClosure argument"() {
        when:
        OptimisticLockingUtil.withOptimisticLock(testDomain, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "dslClosure should not be null."
    }

    def "convertToLong: converts to long or null from any types"() {
        when:
        def result = OptimisticLockingUtil.convertToLong(from)

        then:
        result == to

        and:
        if (result != null) {
            assert result instanceof Long
        }

        where:
        from         | to
        1            | 1L
        1F           | 1L
        1L           | 1L
        1G           | 1L
        "1"          | 1L
        "123"        | 123L
        "NOT NUMBER" | null
        ""           | null
        null         | null
        new Object() | null
    }

    private static void assertVersionConflict(domain) {
        assert domain.hasErrors()
        def errors = domain.errors.getFieldErrors("version")
        assert errors.size() == 1
        assert errors[0].codes.toList().contains("default.optimistic.locking.failure")
    }

}
