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
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification
import test.TestDomain

@Integration
@Transactional
class PessimisticLockingDynamicMethodSpec extends Specification {

    private static final long NOT_FOUND_ID = 9999

    TestDomain testDomain

    def setup() {
        TestDomain.withNewSession {
            TestDomain.withNewTransaction {
                TestDomain.list()*.delete(flush: true)
                testDomain = new TestDomain(value: "PessimisticLockingSpec's TEST_VALUE").save(flush: true, failOnError: true)
            }
        }
        assert TestDomain.count() == 1
    }

    def cleanupSpec() {
        TestDomain.withNewSession {
            TestDomain.withNewTransaction {
                TestDomain.list()*.delete(flush: true)
            }
        }
    }

    def "withPessimisticLock: calls main closure when acquires a lock"() {
        when:
        def result = TestDomain.withPessimisticLock(testDomain.id) { lockedDomain ->
            assert lockedDomain.id == testDomain.id
            return "OK"
        }.onNotFound { id ->
            assert false
        }

        then:
        result.returnValue == "OK"
    }

    def "withPessimisticLock: calls onNotFound closure when target is not found"() {
        when:
        def result = TestDomain.withPessimisticLock(NOT_FOUND_ID) { lockedDomain ->
            assert false
        }.onNotFound { id ->
            return "NOT_FOUND: $id"
        }

        then:
        result.returnValue == "NOT_FOUND: ${NOT_FOUND_ID}"
    }

    def "withPessimisticLock: calls default onNotFound closure when calling without onNotFound closure"() {
        when:
        def result = TestDomain.withPessimisticLock(NOT_FOUND_ID) { lockedDomain ->
            assert false
        }

        then:
        result.returnValue == null
    }

    def "withPessimisticLock: throws the original exception when an exception occurs in main closure"() {
        when:
        TestDomain.withPessimisticLock(testDomain.id) { lockedDomain ->
            throw new IOException("EXCEPTION_FOR_TEST")
        }.onNotFound { id ->
            assert false
        }

        then:
        def e = thrown(IOException)
        e.message == "EXCEPTION_FOR_TEST"
    }

    def "withPessimisticLock: throws the original exception when an exception occurs in onNotFound closure"() {
        when:
        TestDomain.withPessimisticLock(NOT_FOUND_ID) { lockedDomain ->
            assert false
        }.onNotFound { id ->
            throw new IOException("EXCEPTION_FOR_TEST")
        }

        then:
        def e = thrown(IOException)
        e.message == "EXCEPTION_FOR_TEST"
    }
}
