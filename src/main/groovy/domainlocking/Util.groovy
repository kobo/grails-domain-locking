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

package domainlocking

class Util {

    static Long convertToLong(number) {
        switch (number) {
            case Number: return number as Long
            case String: return number.isLong() ? number.toLong() : null
            default: return null
        }
    }

    static void shouldNotNull(Map argMaps) {
        argMaps.each { name, value ->
            if (value == null) throw new IllegalArgumentException("${name} should not be null.")
        }
    }

    static notNullValue(Iterable values) {
        values.find { it != null }
    }

}

