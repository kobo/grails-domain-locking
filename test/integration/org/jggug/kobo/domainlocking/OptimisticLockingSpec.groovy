/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jggug.kobo.domainlocking
import grails.plugin.spock.IntegrationSpec
import org.springframework.dao.OptimisticLockingFailureException
import spock.lang.Unroll
import test.TestDomain

class OptimisticLockingSpec extends IntegrationSpec {

    def transactional = false

    def testDomain

    def setup() {
        assert TestDomain.list() == []

        TestDomain.withNewSession {
            TestDomain.withNewTransaction {
                // saving TestDomain
                testDomain = new TestDomain(value: "OptimisticLockingSpec's INIT_VALUE").save(failOnError: true, flush: true)
                assert TestDomain.count() == 1

                // updating version
                testDomain.value = "OptimisticLockingSpec's TEST_VALUE"
                testDomain.save(failOnError: true, flush: true)
                assert testDomain.version == 1
            }
        }
    }

    def cleanup() {
        TestDomain.withNewSession {
            TestDomain.withNewTransaction {
                TestDomain.list()*.delete(flush: true)
            }
        }
    }

    @Unroll
    def "withOptimisticLock: calls or not onConflict handler when persistentVersion( #persistentVersion ) #operator modificationBaseVersion( #modificationBaseVersion )"() {
        given:
        testDomain.version = persistentVersion

        when:
        def result = OptimisticLocking.withOptimisticLock(testDomain, modificationBaseVersion) { domain ->
            assert executedMain
            assert domain.is(testDomain)
            return "OK"
        }.onConflict { domain ->
            assert domain.is(testDomain)
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
            assert domain.is(testDomain)
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
