package jp.co.ntts.grails.plugin.domainlocking

import groovy.util.logging.Commons

@Commons
class PessimisticLockingUtil {

    static withPessimisticLock(Class lockingDomainClass, Long lockingDomainId, Closure mainClosure) {
        shouldNotNull(lockingDomainClass: lockingDomainClass, lockingDomainId: lockingDomainId, mainClosure: mainClosure)

        // 1次キャッシュとDBの状態に差分がある場合(他のセッションによる変更・削除後など)、
        // OptimisticLockingFailureExceptionが発生してしまう。これを防止するために、
        // 強制的にflush & clearする。
        lockingDomainClass.withSession { session ->
            session.flush()
            session.clear()
        }

        // 悲観的ロックを取得する。
        def lockedDomain = lockingDomainClass.lock(lockingDomainId)
        if (lockedDomain == null) {
            log.debug "Target not found: domainClass=${lockingDomainClass.name}, id=${lockingDomainId}"
            return new PessimisticDomainLockingResult(
                returnValue: null,
                onNotFound: { Closure onNotFoundClosure ->
                    return new PessimisticDomainLockingResult(returnValue: onNotFoundClosure.call(lockingDomainId))
                }
            )
        }
        log.debug "Acquired pessimistic lock: domainClass=${lockingDomainClass.name}, id=${lockingDomainId}, version=${lockedDomain.version}"

        // クロージャを実行する。
        def returnValue = mainClosure.call(lockedDomain)

        return [
            returnValue: returnValue,
            onNotFound: { Closure onNotFoundClosure -> new PessimisticDomainLockingResult(returnValue: returnValue) }
        ]
    }

    private static shouldNotNull(argMaps) {
        argMaps.each { name, value ->
            if (value == null) throw new IllegalArgumentException("${name} should not be null.")
        }
    }
}

