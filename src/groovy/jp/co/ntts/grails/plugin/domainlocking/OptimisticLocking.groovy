package jp.co.ntts.grails.plugin.domainlocking

import groovy.util.logging.Commons
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

    private static Result executeMain(Object domain, Closure mainClosure) {
        try {
            def returnValue = mainClosure.call(domain)

            // To flush session only when mainClosure is succeed.
            // If this were missed, runtime exception couldn't occur here.
            // Instead, it would occur, for example, after an invocation of an action of a controller.
            domain.withSession { it.flush() }

            return new Result(returnValue: returnValue, domain: domain, succeed: true)

        } catch (OptimisticLockingFailureException e) {
            log.debug "Optimistic locking conflicted.", e
            return handleFailure(domain, e)
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

    private static Result handleFailure(domain, Throwable caused = null) {
        bindFieldError(domain)
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

        Result onFailure(Closure failureHandler) {
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

