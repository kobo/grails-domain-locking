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
import test.TestDomain

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

class PessimisticLockingSpec extends IntegrationSpec {

    private static final long NOT_FOUND_ID = 9999
    private static final String TEST_VALUE = "PessimisticLockingSpec's TEST_VALUE"

    def testDomain

    def setup() {
        TestDomain.withNewTransaction {
            TestDomain.list()*.delete(flush: true)
            testDomain = new TestDomain(value: "PessimisticLockingSpec's TEST_VALUE").save(flush: true, failOnError: true)
        }
        assert TestDomain.count() == 1
    }

    def cleanupSpec() {
        // workaround to delete the record on another independent transaction, to prevent affecting other tests.
        TestDomain.list()*.delete(flush: true)
    }

    def "withPessimisticLock: calls main closure when acquires a lock"() {
        when:
        def result = PessimisticLocking.withPessimisticLock(TestDomain, testDomain.id) { lockedDomain ->
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
        def result = PessimisticLocking.withPessimisticLock(TestDomain, NOT_FOUND_ID) { lockedDomain ->
            assert false
        }.onNotFound { id ->
            return "NOT_FOUND: $id"
        }

        then:
        result.returnValue == "NOT_FOUND: ${NOT_FOUND_ID}"
    }

    def "withPessimisticLock: calls default onNotFound closure when calling without onNotFound closure"() {
        when:
        def result = PessimisticLocking.withPessimisticLock(TestDomain, NOT_FOUND_ID) { lockedDomain ->
            assert false
        }

        then:
        result.returnValue == null
    }

    def "withPessimisticLock: throws the original exception when an exception occurs in main closure"() {
        when:
        PessimisticLocking.withPessimisticLock(TestDomain, testDomain.id) { lockedDomain ->
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
        PessimisticLocking.withPessimisticLock(TestDomain, NOT_FOUND_ID) { lockedDomain ->
            assert false
        }.onNotFound { id ->
            throw new IOException("EXCEPTION_FOR_TEST")
        }

        then:
        def e = thrown(IOException)
        e.message == "EXCEPTION_FOR_TEST"
    }

    def "withPessimisticLock: calls a main closure sequentially by using lock when target has been updated at another thread"() {
        given:
        def history = [] as CopyOnWriteArrayList // 複数スレッドから更新するため
        def thread = null // スレッドの待ち受け用
        def latch = new CountDownLatch(1)

        when:
        TestDomain.withNewTransaction {
            def testDomainInCurrentThread = TestDomain.lock(testDomain.id)
            assert testDomainInCurrentThread.value == TEST_VALUE
            history << "currentThread:locked"

            thread = Thread.start {->
                TestDomain.withNewSession { session ->
                    TestDomain.withNewTransaction {
                        history << "anotherThread:waiting"
                        latch.countDown()
                        PessimisticLocking.withPessimisticLock(TestDomain, testDomain.id) { testDomainInAnotherThread ->
                            history << "anotherThread:locked"
                            testDomainInAnotherThread.value = "UPDATED_VALUE_BY_ANOTHER_SESSION_OF_ANOTHER_THREAD"
                            testDomainInAnotherThread.save(failOnError: true, flush: true)
                            assert testDomainInAnotherThread.value == "UPDATED_VALUE_BY_ANOTHER_SESSION_OF_ANOTHER_THREAD"
                            history << "anotherThread:updated"
                        }
                        history << "anotherThread:released"
                    }
                }
            }

            // 別スレッドがロック取得処理まで確実に進んでからupdateする。
            latch.await()
            sleep 100
            testDomainInCurrentThread.value = "UPDATED_VALUE_BY_CURRENT_SESSION"
            testDomainInCurrentThread.save(failOnError: true, flush: true)
            history << "currentThread:updated"
        }
        thread?.join()
        TestDomain.withSession { it.clear() }

        then: "anotherThread:is waiting for releasing the testDomainInCurrentThread's lock"
        history.toList() == [
            "currentThread:locked",
            "anotherThread:waiting",
            "currentThread:updated",
            "anotherThread:locked",
            "anotherThread:updated",
            "anotherThread:released"
        ]

        and: "実際に後から悲観的ロックを取得して更新を実施した、別スレッド上の結果が残る(=後勝ち)"
        TestDomain.get(testDomain.id).value == "UPDATED_VALUE_BY_ANOTHER_SESSION_OF_ANOTHER_THREAD"
    }

    def "withPessimisticLock: calls a main closure successfully without cleaning session even if target has been updated at another session"() {
        given:
        TestDomain.withNewSession {
            def workTestDomain = TestDomain.get(testDomain.id)
            workTestDomain.value = "UPDATED_BY_ANOTHER_SESSION"
            workTestDomain.save(flush: true, failOnError: true)
            assert workTestDomain.version == 1
        }

        and: "still orignal value in current session"
        assert testDomain.version == 0
        assert testDomain.value == TEST_VALUE

        when:
        PessimisticLocking.withPessimisticLock(TestDomain, testDomain.id) { lockedDomain ->
            assert lockedDomain.version == 1
            assert lockedDomain.value == "UPDATED_BY_ANOTHER_SESSION"

            lockedDomain.value = "UPDATED_IN_MAIN_CLOSURE"
            lockedDomain.save(flush: true)
        }

        then:
        noExceptionThrown()

        and:
        assert testDomain.version == 0
        assert testDomain.value == TEST_VALUE

        when:
        TestDomain.withSession { it.clear() }

        then:
        def fetchedDomain = TestDomain.get(testDomain.id)
        assert fetchedDomain.version == 2
        assert fetchedDomain.value == "UPDATED_IN_MAIN_CLOSURE"
    }

    def "withPessimisticLock: calls a notFound closure successfully without cleaning session even if target has been deleted at another session"() {
        given:
        TestDomain.withNewSession {
            def workTestDomain = TestDomain.get(testDomain.id)
            workTestDomain.delete(flush: true)
        }

        and: "still there in current session"
        assert testDomain.version == 0
        assert testDomain.value == TEST_VALUE

        when:
        def result = PessimisticLocking.withPessimisticLock(TestDomain, testDomain.id) { lockedDomain ->
            assert false
        }.onNotFound { id ->
            assert id == testDomain.id
            return "NOT_FOUND"
        }

        then:
        noExceptionThrown()

        and:
        result.returnValue == "NOT_FOUND"
    }

    def "withPessimisticLock: throws IllegalArgumentException when called with no lockingDomainClass argument"() {
        when:
        PessimisticLocking.withPessimisticLock(null, 1, { /.../ })

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "lockingDomainClass should not be null."
    }

    def "withPessimisticLock: throws IllegalArgumentException when called with no lockingDomainId argument"() {
        when:
        PessimisticLocking.withPessimisticLock(TestDomain, null, { /.../ })

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "lockingDomainId should not be null."
    }

    def "withPessimisticLock: throws IllegalArgumentException when called with no dslClosure argument"() {
        when:
        PessimisticLocking.withPessimisticLock(TestDomain, 1, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "mainClosure should not be null."
    }
}
