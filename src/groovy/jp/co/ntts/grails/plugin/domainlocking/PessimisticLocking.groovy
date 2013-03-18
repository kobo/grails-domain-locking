package jp.co.ntts.grails.plugin.domainlocking

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
            return new Result(
                returnValue: null,
                onNotFound: { Closure onNotFoundClosure ->
                    return new Result(returnValue: onNotFoundClosure.call(lockingDomainId))
                }
            )
        }
        log.debug "Acquired pessimistic lock: domainClass=${lockingDomainClass.name}, id=${lockingDomainId}, version=${lockedDomain.version}"

        // execute main closure
        def returnValue = mainClosure.call(lockedDomain)

        return [
            returnValue: returnValue,
            onNotFound: { Closure onNotFoundClosure -> new Result(returnValue: returnValue) }
        ]
    }

    static class Result {
        def returnValue
        Closure onNotFound
    }
}

