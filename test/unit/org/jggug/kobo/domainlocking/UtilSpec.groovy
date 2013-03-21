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
import grails.plugin.spock.UnitSpec
import org.jggug.kobo.domainlocking.Util

class UtilSpec extends UnitSpec {

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
}
