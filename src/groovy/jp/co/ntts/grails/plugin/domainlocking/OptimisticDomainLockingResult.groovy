package jp.co.ntts.grails.plugin.domainlocking

class OptimisticDomainLockingResult {
    def returnValue
    Closure onFailure
}