package org.jggug.kobo.domainlocking

import groovy.util.logging.Commons

@Commons
class PessimisticLocking {

    static withPessimisticLock(Class lockingDomainClass, Long lockingDomainId, Closure mainClosure) {
        Util.shouldNotNull(lockingDomainClass: lockingDomainClass, lockingDomainId: lockingDomainId, mainClosure: mainClosure)

        // when there is different between 1st level cache and 2nd level cache (e.g. after other session did update or delete),
        // OptimisticLockingFailureException occurs. To prevent it, session should be flush and clear forcely.
        lockingDomainClass.withSession { session ->
            session.flush()
            session.clear()
        }

        // acquire lock
        def lockedDomain = lockingDomainClass.lock(lockingDomainId)
        if (lockedDomain == null) {
            log.debug "Target not found: domainClass=${lockingDomainClass.name}, id=${lockingDomainId}"
            return new Result(returnValue: null, domainId: lockingDomainId, succeed: false)
        }
        log.debug "Acquired pessimistic lock: domainClass=${lockingDomainClass.name}, id=${lockingDomainId}, version=${lockedDomain.version}"

        // execute main closure
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

