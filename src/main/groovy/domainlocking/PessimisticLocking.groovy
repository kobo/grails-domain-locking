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

import groovy.util.logging.Commons

@Commons
class PessimisticLocking {

    static withPessimisticLock(Class lockingDomainClass, Long lockingDomainId, Closure mainClosure) {
        Util.shouldNotNull(lockingDomainClass: lockingDomainClass, lockingDomainId: lockingDomainId, mainClosure: mainClosure)

        // Acquire lock
        def lockedDomain = lockingDomainClass.lock(lockingDomainId)
        if (lockedDomain == null) {
            log.debug "Target not found: domainClass=${lockingDomainClass.name}, id=${lockingDomainId}"
            return new Result(returnValue: null, domainId: lockingDomainId, succeed: false)
        }
        log.debug "Acquired pessimistic lock: domainClass=${lockingDomainClass.name}, id=${lockingDomainId}, version=${lockedDomain.version}"

        // Execute main closure
        def returnValue = mainClosure.call(lockedDomain)
        return new Result(returnValue: returnValue, domainId: lockingDomainId, succeed: true)
    }

    static class Result {
        def returnValue
        Long domainId
        boolean succeed

        Result onNotFound(Closure failureHandler) {
            if (failureHandler && !succeed) {
                return [returnValue: failureHandler(domainId)]
            }
            return [returnValue: returnValue]
        }
    }
}

