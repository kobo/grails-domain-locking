package jp.co.ntts.grails.plugin.domainlocking

class OptimisticLockingResult {
    def returnValue
    Closure onFailure
}