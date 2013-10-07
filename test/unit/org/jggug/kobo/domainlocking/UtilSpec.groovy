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

import spock.lang.Specification

class UtilSpec extends Specification {

    def "convertToLong: converts to long or null from any types"() {
        when:
        def result = Util.convertToLong(from)

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

    def "shouldNotNull: throws an exception when arguments have one or more null"() {
        when:
        Util.shouldNotNull(args)

        then:
        IllegalArgumentException e = thrown()
        assert e.message == message

        where:
        args                        | message
        [a: null]                   | "a should not be null."
        [a: 1, b: null, c: 3]       | "b should not be null."
        [a: null, b: null, c: null] | "a should not be null."

    }

    def "shouldNotNull: does nothing when arguments don't have any null"() {
        when:
        Util.shouldNotNull(args)

        then:
        noExceptionThrown()

        where:
        args << [
            [:],
            [a: 1],
            [a: 1, b: 2, c: 3],
        ]
    }

    def "notNullValue: returns first non-null value from arguments"() {
        expect:
        Util.notNullValue(args) == value

        where:
        args               | value
        [1, 2, 3]          | 1
        [null, 2, 3]       | 2
        [null, null, 3]    | 3
        [null, null, null] | null
        [null]             | null
        []                 | null
    }
}
