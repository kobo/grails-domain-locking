package jp.co.ntts.grails.plugin.domainlocking

import groovy.util.logging.Commons
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException

@Commons
class OptimisticLockingUtil {

    static withOptimisticLock(domain, modificationBaseVersion = null, Closure mainClosure) {
        shouldNotNull(domain: domain, mainClosure: mainClosure)

        Long persistentVersionAsLong = domain.version
        Long modificationBaseVersionAsLong = convertToLong(modificationBaseVersion)
        if (persistentVersionAsLong != null && modificationBaseVersion != null) {
            if (persistentVersionAsLong > modificationBaseVersionAsLong) {
                log.debug "他のセッションによってバージョンが更新されています。: persistentVersion=${persistentVersionAsLong}, modificationBaseVersion=${modificationBaseVersionAsLong}"
                return handleFailure(domain)
            }
        }
        try {
            def returnValue = mainClosure.call(domain)

            // ここでセッションのフラッシュを強制しないと、実際のSQL発行がこのクロージャ外で行われることになりキャッチできなくなってしまう。
            // To flush session only when mainClosure is succeed.
            // If this were missed, runtime exception couldn't occur here.
            // Instead, it would occur, for example, after an invocation of an action of a controller.
            domain.withSession { it.flush() }

            return [returnValue: returnValue, onFailure: { Closure userFailureHandler -> [returnValue: returnValue] }]

        } catch (DataIntegrityViolationException e) {
            log.warn "制約違反が発生しました。: ${e.message}"
            return handleFailure(domain)
        } catch (OptimisticLockingFailureException e) {
            log.warn "楽観的ロックで競合が発生しました。: ${e.message}"
            return handleFailure(domain)
        }
    }

    private static handleFailure(domain) {
        bindFieldError(domain)
        return [
            returnValue: null,
            onFailure: { Closure userFailureHandler = null ->
                if (userFailureHandler) {
                    return [returnValue: userFailureHandler(domain)]
                }
                return [returnValue: null]
            }
        ]
    }

    private static bindFieldError(domain) {
        def domainClassName = domain.getClass().simpleName
        domain.errors.rejectValue("version", "default.optimistic.locking.failure",
            [domainClassName] as Object[],
            "Another user has updated this ${domainClassName} while you were editing")
        return null
    }

    private static Long convertToLong(number) {
        switch (number) {
            case Number:
                return number as Long
            case String:
                return number.isLong() ? number.toLong() : null
            case null:
            default:
                return null
        }
    }

    private static shouldNotNull(argMaps) {
        argMaps.each { name, value ->
            if (value == null) throw new IllegalArgumentException("${name} should not be null.")
        }
    }
}

