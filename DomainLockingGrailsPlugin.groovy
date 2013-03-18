import jp.co.ntts.grails.plugin.domainlocking.OptimisticLockingUtil
import jp.co.ntts.grails.plugin.domainlocking.PessimisticLockingUtil

class DomainLockingGrailsPlugin {

    def version = "0.2"
    def grailsVersion = "2.1 > *"
    def dependsOn = [:]
    def pluginExcludes = [
        "grails-app/views/error.gsp",
        "grails-app/domain/test/**",
        "grails-app/i18n/**",
        "web-app/**",
        "scripts/**",
    ]
    def title = "Grails Domain Locking Plugin"
    def author = "GGAO"
    def license = "APACHE"
    def description = 'This plugin provides a easy way for optimistic/pessimistic locking.'
    def organization = [name: "NTT Software Coporation", url: "http://www.nttsoft.com/"]
    def developers = [
        [name: "Yasuharu NAKANO", email: "nakano.yasuharu@po.ntts.co.jp"],
    ]

    def doWithDynamicMethods = { applicationContext ->
        for (domainClass in application.domainClasses) {
            // OptimisticLockingUtil
            domainClass.metaClass.withOptimisticLock = { Closure dslClosure ->
                OptimisticLockingUtil.withOptimisticLock(delegate, dslClosure)
            }

            // PessimisticLockingUtil
            domainClass.metaClass.static.withPessimisticLock = { long lockingDomainId, Closure dslClosure ->
                PessimisticLockingUtil.withPessimisticLock(delegate, lockingDomainId, dslClosure)
            }
        }
    }
}
