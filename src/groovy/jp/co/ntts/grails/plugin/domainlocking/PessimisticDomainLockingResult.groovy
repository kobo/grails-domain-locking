package jp.co.ntts.grails.plugin.domainlocking

class PessimisticDomainLockingResult {
    def returnValue
    Closure onNotFound
}