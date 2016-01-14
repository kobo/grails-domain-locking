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

import grails.test.mixin.integration.Integration
import grails.util.Holders
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification
import spock.lang.Unroll
import test.TestDomain

@Integration
@Transactional
class OptimisticLockingDynamicMethodSpec extends Specification {

    private static final String DEFAULT_FIELD_ERROR_MESSAGE_CODE = "default.optimistic.locking.failure"

    TestDomain testDomain

    def setup() {
        TestDomain.withNewSession {
            assert TestDomain.list() == []

            TestDomain.withNewTransaction {
                // saving TestDomain
                testDomain = new TestDomain(value: "OptimisticLockingSpec's INIT_VALUE").save(failOnError: true, flush: true)
                assert TestDomain.count() == 1

                // updating version
                testDomain.value = "OptimisticLockingSpec's TEST_VALUE"
                testDomain.save(failOnError: true, flush: true)
                assert testDomain.version == 1L
            }
        }

        Holders.config.grails.plugins.domainlocking.defaultErrorBinding = true
    }

    def cleanup() {
        TestDomain.withNewSession {
            TestDomain.withNewTransaction {
                TestDomain.list()*.delete(flush: true)
            }
        }
    }

    def "withOptimisticLock: executes main clause and returns its return value when versionCompare isn't specified"() {
        when:
        def result = testDomain.withOptimisticLock { domain ->
            return "OK"
        }.onConflict { domain ->
            assert false
        }

        then:
        result.returnValue == "OK"
    }

    def "withOptimisticLock: executes main clause and returns its return value when onConflict isn't specified and the locking is succeed"() {
        when:
        def result = testDomain.withOptimisticLock(1) { domain ->
            return "OK"
        }

        then:
        result.returnValue == "OK"
    }

    def "withOptimisticLock: returns null when onConflict isn't specified and the locking is failed"() {
        when:
        def result = testDomain.withOptimisticLock(0) { domain ->
            assert false
        }

        then:
        result.returnValue == null

        and:
        assertFieldError(testDomain)
    }

    @Unroll
    def "withOptimisticLock: calls onConflict handler when #exception.class.name was thrown"() {
        given:
        def exception = new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")

        when:
        def result = testDomain.withOptimisticLock { domain ->
            throw exception
        }.onConflict { domain, caused ->
            assert domain.is(testDomain)
            assert caused.is(exception)
            return "NG"
        }

        then:
        result.returnValue == "NG"

        and:
        assertFieldError(testDomain)
    }

    def "withOptimisticLock: returns null when called without onConflict handler"() {
        when:
        def result = testDomain.withOptimisticLock { domain ->
            throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")
        }

        then:
        result.returnValue == null

        and:
        assertFieldError(testDomain)
    }

    def "withOptimisticLock: throws the original exception when an exception excepting OptimisticLockingFailureException and DataIntegrityViolationException occurs in main closure"() {
        when:
        testDomain.withOptimisticLock { domain ->
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
        testDomain.withOptimisticLock(0) { domain ->
            assert false
        }.onConflict { domain ->
            throw new IOException("EXCEPTION_FOR_TEST")
        }

        then:
        def e = thrown(IOException)
        e.message == "EXCEPTION_FOR_TEST"
    }

    def "withOptimisticLock: doesn't bind default field error when errorBinding: false"() {
        when:
        testDomain.withOptimisticLock(errorBinding: false) { domain ->
            throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")
        }

        then:
        !testDomain.hasErrors()
    }

    def "withOptimisticLock: binds default field error when errorBinding: true explicitly"() {
        when:
        testDomain.withOptimisticLock(errorBinding: true) { domain ->
            throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")
        }

        then:
        assertFieldError(testDomain)
    }

    def "withOptimisticLock: binds default field error when there is no errorBinding params and defaultErrorBinding: true in Config.groovy"() {
        given:
        Holders.config.grails.plugins.domainlocking.defaultErrorBinding = true

        when:
        testDomain.withOptimisticLock { domain ->
            throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")
        }

        then:
        assertFieldError(testDomain)
    }

    def "withOptimisticLock: doesn't bind default field error when there is no errorBinding params and defaultErrorBinding: false in Config.groovy"() {
        given:
        Holders.config.grails.plugins.domainlocking.defaultErrorBinding = false

        when:
        testDomain.withOptimisticLock { domain ->
            throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")
        }

        then:
        !testDomain.hasErrors()
    }

    def "withOptimisticLock: doesn't bind default field error when there is no errorBinding params and defaultErrorBinding is null in Config.groovy"() {
        given:
        Holders.config.grails.plugins.domainlocking.defaultErrorBinding = null

        when:
        testDomain.withOptimisticLock { domain ->
            throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")
        }

        then:
        assertFieldError(testDomain)
    }

    private static void assertFieldError(domain, code = DEFAULT_FIELD_ERROR_MESSAGE_CODE) {
        assert domain.hasErrors()
        def errors = domain.errors.getFieldErrors("version")
        assert errors.size() == 1
        assert errors[0].codes.toList().contains(code)
    }
}
