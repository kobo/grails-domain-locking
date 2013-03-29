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

class DynamicMethodsSpec extends IntegrationSpec {

    def testDomain

    def setup() {
        assert TestDomain.list() == []

        // saving TestDomain
        testDomain = new TestDomain(value: "DynamicMethodsSpec's TEST_VALUE").save(failOnError: true, flush: true)
        assert TestDomain.count() == 1
    }

    def "withOptimisticLock: delegates to OptimisticLocking.withOptimisticLock()"() {
        when:
        def result = testDomain.withOptimisticLock(0) { domain ->
            return "OK"
        }.onConflict { domain ->
            assert false
        }

        then:
        result.returnValue == "OK"
    }

    def "withOptimisticLock: doesn't required modificationBaseVersion parameter"() {
        when:
        def result = testDomain.withOptimisticLock { domain ->
            return "OK"
        }.onConflict { domain ->
            assert false
        }

        then:
        result.returnValue == "OK"
    }

    def "withPessimisticLock: delegates to PessimisticLocking.withPessimisticLock()"() {
        when:
        def result = TestDomain.withPessimisticLock(testDomain.id) { lockedDomain ->
            return "OK"
        }.onNotFound { id ->
            assert false
        }

        then:
        result.returnValue == "OK"
    }

}
