package jp.co.ntts.grails.plugin.domainlocking

class PessimisticLockingResult {
    def returnValue
    Closure onNotFound
}