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

import grails.util.Holders
import groovy.util.logging.Commons
import org.springframework.dao.OptimisticLockingFailureException

@Commons
class OptimisticLocking {

    static withOptimisticLock(domain, modificationBaseVersion, Map params, Closure mainClosure) {
        Util.shouldNotNull(domain: domain, mainClosure: mainClosure)

        if (isDifferentVersion(domain.version, modificationBaseVersion)) {
            log.debug "Version is already updated by other session: domainClass=${domain.class.name}, id=${domain.id}, persistentVersion=${domain.version}, modificationBaseVersion=${modificationBaseVersion}"
            return handleFailure(domain, params)
        }

        return executeMain(domain, params, mainClosure)
    }

    private static Result executeMain(Object domain, Map params, Closure mainClosure) {
        try {
            def returnValue = mainClosure.call(domain)
            return new Result(returnValue: returnValue, domain: domain, succeed: true)

        } catch (OptimisticLockingFailureException e) {
            log.debug "Optimistic locking conflicted.", e
            return handleFailure(domain, params, e)
        }
    }

    private static boolean isDifferentVersion(persistentVersion, modificationBaseVersion) {
        if (modificationBaseVersion == null) return false

        Long persistentVersionAsLong = Util.convertToLong(persistentVersion)
        Long modificationBaseVersionAsLong = Util.convertToLong(modificationBaseVersion)
        if (persistentVersionAsLong != null && modificationBaseVersion != null) {
            if (persistentVersionAsLong > modificationBaseVersionAsLong) {
                return true
            }
        }
        return false
    }

    private static Result handleFailure(domain, Map params, Throwable caused = null) {
        // NOTICE: If there is no entry in configuration, it returns not null but EMPTY Map.
        // So assignment of "null" is wrong way of clear the configuration.
        def defaultErrorBinding = Holders.config.grails?.plugins?.domainlocking?.defaultErrorBinding
        defaultErrorBinding = (defaultErrorBinding instanceof Map) ? null : defaultErrorBinding

        // Only if error binding is necessary, do it.
        if (Util.notNullValue([params.errorBinding, defaultErrorBinding, true])) {
            bindFieldError(domain)
        }
        return new Result(returnValue: null, domain: domain, succeed: false, caused: caused)
    }

    private static bindFieldError(domain) {
        def domainClassName = domain.getClass().simpleName
        domain.errors.rejectValue("version", "default.optimistic.locking.failure", [domainClassName] as Object[], "Another user has updated this ${domainClassName} while you were editing")
    }

    static class Result {
        def returnValue
        def domain
        boolean succeed
        Throwable caused

        Result onConflict(Closure failureHandler) {
            return new Result(
                returnValue: (succeed || !failureHandler) ? returnValue : evaluateReturnValue(failureHandler),
                domain: domain,
                succeed: succeed,
                caused: caused
            )
        }

        private evaluateReturnValue(Closure failureHandler) {
            switch (failureHandler.parameterTypes.size()) {
                case 0: return failureHandler()
                case 1: return failureHandler(domain)
                case 2: return failureHandler(domain, caused)
                default: throw new IllegalArgumentException("failureHandler must be receive one or two arguments.")
            }
        }
    }
}

