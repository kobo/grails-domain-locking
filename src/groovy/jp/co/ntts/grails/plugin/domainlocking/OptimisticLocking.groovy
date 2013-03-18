package jp.co.ntts.grails.plugin.domainlocking

import groovy.util.logging.Commons
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException

@Commons
class OptimisticLocking {

    static withOptimisticLock(domain, modificationBaseVersion = null, Closure mainClosure) {
        Util.shouldNotNull(domain: domain, mainClosure: mainClosure)

        if (isDifferentVersion(domain.version, modificationBaseVersion)) {
            log.debug "Version is already updated by other session: domainClass=${domain.class.name}, id=${domain.id}, persistentVersion=${domain.version}, modificationBaseVersion=${modificationBaseVersion}"
            return handleFailure(domain)
        }

        return executeMain(domain, mainClosure)
    }

    private static OptimisticLockingResult executeMain(Object domain, Closure mainClosure) {
        try {
            def returnValue = mainClosure.call(domain)

            // To flush session only when mainClosure is succeed.
            // If this were missed, runtime exception couldn't occur here.
            // Instead, it would occur, for example, after an invocation of an action of a controller.
            domain.withSession { it.flush() }

            return new OptimisticLockingResult(
                returnValue: returnValue,
                onFailure: { Closure userFailureHandler -> new OptimisticLockingResult(returnValue: returnValue) }
            )

        } catch (DataIntegrityViolationException e) {
            log.debug "Constraint violation occurred.", e
            return handleFailure(domain)
        } catch (OptimisticLockingFailureException e) {
            log.debug "Optimistic locking conflicted.", e
            return handleFailure(domain)
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

    private static handleFailure(domain) {
        bindFieldError(domain)
        return new OptimisticLockingResult(
            returnValue: null,
            onFailure: { Closure userFailureHandler = null ->
                if (userFailureHandler) {
                    return [returnValue: userFailureHandler(domain)]
                }
                return [returnValue: null]
            }
        )
    }

    private static bindFieldError(domain) {
        def domainClassName = domain.getClass().simpleName
        domain.errors.rejectValue("version", "default.optimistic.locking.failure",
            [domainClassName] as Object[],
            "Another user has updated this ${domainClassName} while you were editing")
        return null
    }
}

